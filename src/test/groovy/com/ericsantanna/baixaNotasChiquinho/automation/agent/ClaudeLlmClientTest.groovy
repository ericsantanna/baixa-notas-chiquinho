package com.ericsantanna.baixaNotasChiquinho.automation.agent

import com.sun.net.httpserver.HttpServer
import groovy.json.JsonSlurper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.util.concurrent.CopyOnWriteArrayList

class ClaudeLlmClientTest {
    HttpServer server
    List<Map> requests = new CopyOnWriteArrayList<>()
    /** status/body returned for the Nth request (last one repeats) */
    List<List> responses = []

    @BeforeEach
    void start() {
        server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        server.createContext('/v1/messages') { exchange ->
            def body = exchange.requestBody.getText('UTF-8')
            requests << [key: exchange.requestHeaders.getFirst('x-api-key'),
                         version: exchange.requestHeaders.getFirst('anthropic-version'), body: body]
            def r = responses[Math.min(requests.size() - 1, responses.size() - 1)]
            byte[] out = r[1].getBytes('UTF-8')
            exchange.responseHeaders.add('content-type', 'application/json')
            exchange.sendResponseHeaders(r[0] as int, out.length)
            exchange.responseBody.write(out)
            exchange.responseBody.close()
        }
        server.start()
    }

    @AfterEach
    void stop() {
        server.stop(0)
    }

    private ClaudeLlmClient client() {
        new ClaudeLlmClient(apiKey: 'sk-test-123', model: 'claude-haiku-5-5',
                url: "http://127.0.0.1:${server.address.port}/v1/messages", retryBaseMillis: 1)
    }

    private static final String OK = '''{"stop_reason":"tool_use","content":[{"type":"text","text":"vou clicar"},{"type":"tool_use","id":"tu_1","name":"click","input":{"id":7}}],"usage":{"input_tokens":120,"cache_read_input_tokens":30,"output_tokens":15}}'''

    @Test
    void sendsToolUseRequestAndParsesTheResponse() {
        responses = [[200, OK]]

        def turn = client().next('sys', [[role: 'user', content: [[type: 'text', text: 'oi']]]], AgentTools.specs())

        assert turn.toolUses.size() == 1
        assert turn.toolUses[0].name == 'click'
        assert turn.toolUses[0].input.id == 7
        assert turn.text == 'vou clicar'
        assert turn.inputTokens == 150
        assert turn.outputTokens == 15

        def req = requests[0]
        assert req.key == 'sk-test-123'
        assert req.version == '2023-06-01'
        def sent = new JsonSlurper().parseText(req.body)
        assert sent.model == 'claude-haiku-5-5'
        assert sent.tool_choice.disable_parallel_tool_use == true
        assert sent.tools*.name.containsAll(['click', 'finish', 'give_up'])
        assert sent.system[0].cache_control.type == 'ephemeral'
        assert !req.body.contains('password')
    }

    @Test
    void doesNotRetryClientErrorsSuchAsSpendLimit() {
        responses = [[400, '{"type":"error","error":{"type":"invalid_request_error","message":"workspace spend limit reached"}}']]

        def e = org.junit.jupiter.api.Assertions.assertThrows(LlmException) {
            client().next('sys', [[role: 'user', content: [[type: 'text', text: 'oi']]]], AgentTools.specs())
        }

        assert e.status == 400
        assert e.message.contains('spend limit')
        assert requests.size() == 1
    }

    @Test
    void retriesOverloadedThenSucceeds() {
        responses = [[529, '{"error":{"message":"overloaded"}}'], [429, '{"error":{"message":"rate"}}'], [200, OK]]

        def turn = client().next('sys', [[role: 'user', content: [[type: 'text', text: 'oi']]]], AgentTools.specs())

        assert turn.toolUses.size() == 1
        assert requests.size() == 3
    }

    @Test
    void givesUpAfterMaxAttempts() {
        responses = [[500, '{"error":{"message":"boom"}}']]

        def e = org.junit.jupiter.api.Assertions.assertThrows(LlmException) {
            client().next('sys', [[role: 'user', content: [[type: 'text', text: 'oi']]]], AgentTools.specs())
        }

        assert e.status == 500
        assert requests.size() == 3
    }
}
