package com.ericsantanna.baixaNotasChiquinho.configuracoes

class Configuracoes {
    static final String DEFAULT_DRIVER = "Firefox"
    static final int DEFAULT_WAITING_TIMEOUT = 30
    static final int DEFAULT_EXTRACTION_THREADS = 2
    static final File DEFAULT_ROOT_PATH = new File(System.getProperty("user.dir"), "execucao")
    static final File DEFAULT_DOWNLOAD_PATH = new File(DEFAULT_ROOT_PATH, "baixados")
    static final File DEFAULT_EXTRACTION_PATH = new File(DEFAULT_ROOT_PATH, "extraidos")
    static final File DEFAULT_PROCESSADOS_PATH = new File(DEFAULT_ROOT_PATH, "processados.csv")
    static final File DEFAULT_NAO_PROCESSADOS_PATH = new File(DEFAULT_ROOT_PATH, "nao-processados.csv")
    static final File DEFAULT_FRANQUEADORAS_PATH = new File(DEFAULT_ROOT_PATH, "franqueadoras.csv")
    static final File DEFAULT_CONFIG_PATH = new File(DEFAULT_ROOT_PATH, "config.yaml")

    String driver
    int waitingTimeout
    int extractionThreads
    File rootPath
    File downloadPath
    File extractionPath
}