package com.ericsantanna.baixaNotasChiquinho.automation.agent

/**
 * Token/call counter shared by every agent run of one execution; the hard stop against runaway spend. Input and
 * output are kept apart because they are priced differently (see the "Métricas" page).
 */
class AgentBudget {
    final long maxTokens
    private long inputUsed
    private long outputUsed
    private int calls

    AgentBudget(long maxTokens) {
        this.maxTokens = maxTokens
    }

    synchronized void add(int inputTokens, int outputTokens) {
        inputUsed += inputTokens
        outputUsed += outputTokens
        calls++
    }

    synchronized long getUsed() { inputUsed + outputUsed }

    synchronized long getInputUsed() { inputUsed }

    synchronized long getOutputUsed() { outputUsed }

    synchronized int getCalls() { calls }

    synchronized boolean exceeded() { inputUsed + outputUsed >= maxTokens }
}
