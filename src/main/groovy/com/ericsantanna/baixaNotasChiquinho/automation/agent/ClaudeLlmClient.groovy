package com.ericsantanna.baixaNotasChiquinho.automation.agent

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.slf4j.LoggerFactory

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Anthropic Messages API client (tool use). Never logs the API key. */
class ClaudeLlmClient implements LlmClient {
    private static final log = LoggerFactory.getLogger(ClaudeLlmClient)

    static final String DEFAULT_URL = 'https://api.anthropic.com/v1/messages'
    static final String API_VERSION = '2023-06-01'
    private static final List<Integer> RETRYABLE = [429, 500, 502, 503, 529]

    String apiKey
    String model
    String url = DEFAULT_URL
    int maxTokens = 1024
    int maxAttempts = 3
    long retryBaseMillis = 2000

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()

    @Override
    AgentTurn next(String system, List<Map> messages, List<Map> tools) {
        // cache_control on the last tool + system: stable prefix is re-read cheaply on every step
        List<Map> toolDefs = tools.collect { new LinkedHashMap(it) }
        if (toolDefs) {
            toolDefs.last().put('cache_control', [type: 'ephemeral'])
        }
        Map payload = [
                model     : model,
                max_tokens: maxTokens,
                system    : [[type: 'text', text: system, cache_control: [type: 'ephemeral']]],
                messages  : messages
        ]
        if (toolDefs) { // plain-text calls (e.g. lesson proposals) send no tools at all
            payload.tools = toolDefs
            payload.tool_choice = [type: 'auto', disable_parallel_tool_use: true]
        }
        String body = JsonOutput.toJson(payload)

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header('x-api-key', apiKey)
                .header('anthropic-version', API_VERSION)
                .header('content-type', 'application/json')
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

        LlmException last = null
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString())
                int status = response.statusCode()
                if (status == 200) {
                    return parse(response.body())
                }
                last = new LlmException("HTTP ${status}: ${errorMessage(response.body())}", status)
                if (!(status in RETRYABLE)) {
                    throw last // 400 (incl. spend limit reached), 401, 403, 404...: retrying cannot help
                }
            } catch (LlmException e) {
                if (!(e.status in RETRYABLE)) {
                    throw e
                }
                last = e
            } catch (IOException e) {
                last = new LlmException("falha de rede: ${e.class.simpleName}: ${e.message}")
            }
            if (attempt < maxAttempts) {
                log.warn "LLM indisponível (${last.message}); nova tentativa ${attempt + 1}/${maxAttempts}"
                Thread.sleep(retryBaseMillis * attempt)
            }
        }
        throw last
    }

    private static AgentTurn parse(String json) {
        def root = new JsonSlurper().parseText(json)
        def turn = new AgentTurn()
        turn.content = (root.content ?: []) as List<Map>
        turn.toolUses = turn.content.findAll { it.type == 'tool_use' }
        turn.text = turn.content.findAll { it.type == 'text' }.collect { it.text }.join('\n')
        turn.stopReason = root.stop_reason
        def usage = root.usage ?: [:]
        turn.inputTokens = ((usage.input_tokens ?: 0) + (usage.cache_creation_input_tokens ?: 0) +
                (usage.cache_read_input_tokens ?: 0)) as int
        turn.outputTokens = (usage.output_tokens ?: 0) as int
        return turn
    }

    private static String errorMessage(String body) {
        try {
            def root = new JsonSlurper().parseText(body)
            return root?.error?.message ?: body?.take(200)
        } catch (Exception ignored) {
            return body?.take(200)
        }
    }
}
