package com.ericsantanna.baixaNotasChiquinho.metricas

/**
 * History of executions, one JSON document per execution inside {@code <root>/metricas/execucoes.json}, plus the
 * token prices used to estimate the agent cost. Credentials are never stored here.
 * Keeps the newest {@link #MAX_EXECUCOES}; older ones are dropped (monthly runs: about ten years).
 */
class ExecutionHistoryStore {
    private static final Object LOCK = new Object()
    static final int MAX_EXECUCOES = 120

    final File dir

    ExecutionHistoryStore(File dir) {
        this.dir = dir
    }

    private File historyFile() { new File(dir, 'execucoes.json') }

    private File pricesFile() { new File(dir, 'precos.json') }

    /** Oldest first, as stored. */
    List<Map> list() {
        synchronized (LOCK) {
            return (JsonFiles.read(historyFile(), []) as List).findAll { it instanceof Map }.collect { it as Map }
        }
    }

    Map find(String id) {
        return list().find { it.id == id }
    }

    void append(Map execution) {
        synchronized (LOCK) {
            List<Map> all = list()
            all.removeAll { it.id == execution.id }
            all << execution
            if (all.size() > MAX_EXECUCOES) {
                all = all.drop(all.size() - MAX_EXECUCOES)
            }
            JsonFiles.write(historyFile(), all)
        }
    }

    boolean delete(String id) {
        synchronized (LOCK) {
            List<Map> all = list()
            boolean removed = all.removeAll { it.id == id }
            if (removed) {
                JsonFiles.write(historyFile(), all)
            }
            return removed
        }
    }

    /** Prices in USD per million tokens; null = not informed (the cost is then shown as unavailable). */
    Map prices() {
        Map p = JsonFiles.read(pricesFile(), [:]) as Map
        return [entradaPorMilhao: toDouble(p.entradaPorMilhao), saidaPorMilhao: toDouble(p.saidaPorMilhao)]
    }

    /** @throws IllegalArgumentException with a user-facing message for negative/invalid values */
    void savePrices(Double entrada, Double saida) {
        if ((entrada != null && (entrada < 0 || entrada.isNaN())) || (saida != null && (saida < 0 || saida.isNaN()))) {
            throw new IllegalArgumentException("Os preços devem ser números maiores ou iguais a zero")
        }
        if ((entrada == null) != (saida == null)) {
            throw new IllegalArgumentException("Informe os dois preços (entrada e saída) ou deixe os dois vazios")
        }
        synchronized (LOCK) {
            JsonFiles.write(pricesFile(), [entradaPorMilhao: entrada, saidaPorMilhao: saida])
        }
    }

    private static Double toDouble(Object v) {
        v == null ? null : (v as Double)
    }
}
