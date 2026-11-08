package com.ericsantanna.baixaNotasChiquinho.automation.agent

/**
 * Runs a Geb-backed operation with a timeout and records how long it took, so the next run can use a timeout
 * learned from history (see {@link AgentKnowledgeStore#timeoutFor}). Without a store it just runs the body with
 * the configured timeout.
 */
class OperationTimer {
    final AgentKnowledgeStore store
    final int configuredSeconds

    OperationTimer(AgentKnowledgeStore store, int configuredSeconds) {
        this.store = store
        this.configuredSeconds = configuredSeconds
    }

    /** Timeout to use now: the model's request (bounded) if any, else the learned/configured value. */
    int timeoutFor(String op, Integer requested) {
        if (store == null) {
            return requested != null ? Math.max(AgentKnowledgeStore.ABSOLUTE_MIN_TIMEOUT, Math.min(requested, AgentKnowledgeStore.DEFAULT_TIMEOUT_MAX)) : configuredSeconds
        }
        return requested != null ? store.clampRequested(requested, configuredSeconds) : store.timeoutFor(op, configuredSeconds)
    }

    /**
     * @param body receives the timeout in seconds. Only successes and wait timeouts are recorded: other failures
     *             (blocked click, wrong franchise...) say nothing about how long the operation needs.
     */
    String run(String op, Integer requested, Closure<String> body) {
        int seconds = timeoutFor(op, requested)
        long start = System.nanoTime()
        try {
            String result = body.call(seconds)
            record(op, start, false, seconds)
            return result
        } catch (Throwable e) {
            if (isWaitTimeout(e)) {
                record(op, start, true, seconds)
                throw new AgentToolException("tempo esgotado (${seconds}s) em ${op}: ${e.message?.readLines()?.first()}. " +
                        "Tente de novo (o limite aumenta sozinho) ou passe timeout_seconds maior.")
            }
            throw e
        }
    }

    private void record(String op, long startNanos, boolean timedOut, int seconds) {
        store?.recordOperation(op, (System.nanoTime() - startNanos).intdiv(1_000_000L), timedOut, seconds)
    }

    static boolean isWaitTimeout(Throwable e) {
        Throwable t = e
        while (t != null) {
            if (t.class.simpleName == 'WaitTimeoutException') {
                return true
            }
            t = (t.cause != null && !t.cause.is(t)) ? t.cause : null
        }
        return false
    }
}
