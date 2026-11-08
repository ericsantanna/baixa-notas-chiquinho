package com.ericsantanna.baixaNotasChiquinho.automation.agent

/**
 * Provider-agnostic LLM port. Messages/tools use the Anthropic Messages API shape (plain maps) because it is
 * the first (and currently only) implementation; another provider can adapt to/from this shape.
 */
interface LlmClient {
    /**
     * @param system   system prompt
     * @param messages conversation so far ([role:, content:[blocks]])
     * @param tools    tool specs ([name:, description:, input_schema:])
     */
    AgentTurn next(String system, List<Map> messages, List<Map> tools)
}

/** One model response. */
class AgentTurn {
    /** Raw assistant content blocks, to be appended to the conversation as-is. */
    List<Map> content = []
    /** The tool_use blocks inside {@link #content}. */
    List<Map> toolUses = []
    String text = ''
    int inputTokens
    int outputTokens
    String stopReason
}

class LlmException extends RuntimeException {
    final int status

    LlmException(String message, int status = 0) {
        super(message)
        this.status = status
    }
}
