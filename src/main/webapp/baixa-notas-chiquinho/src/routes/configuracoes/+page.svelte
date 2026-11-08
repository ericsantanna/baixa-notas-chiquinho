<script>
    import { onMount } from 'svelte';
    import { browser } from '$app/environment';
    
    // Form state using runes
    let driver = $state('Firefox');
    let defaultWaitingTimeout = $state('');
    let extractionThreads = $state('');
    let rootPath = $state('');
    let downloadPath = $state('');
    let extractionPath = $state('');

    // LLM agent
    let agentMode = $state('OFF');
    let llmModel = $state('');
    let llmApiKey = $state('');
    let agentMaxSteps = $state('');
    let agentTimeoutSeconds = $state('');
    let agentMaxTokensPerRun = $state('');

    // Validation errors
    let errors = $state({});
    
    // Form submission state
    let isSubmitting = $state(false);
    let isLoading = $state(true);

    async function loadConfigurations() {
        if (!browser) return;
        
        try {
            isLoading = true;
            const response = await fetch('http://localhost:9001/api/configuracoes');
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const config = await response.json();
            console.log('Configurations loaded:', config);
            
            // Update form fields with loaded data
            driver = config.driver || 'Firefox';
            defaultWaitingTimeout = config.waitingTimeout?.toString() || '';
            extractionThreads = config.extractionThreads?.toString() || '';
            rootPath = config.rootPath?.toString() || '';
            downloadPath = config.downloadPath?.toString() || '';
            extractionPath = config.extractionPath?.toString() || '';

            agentMode = config.agentMode || 'OFF';
            llmModel = config.llmModel || '';
            llmApiKey = config.llmApiKey || ''; // already masked by the backend ("****abcd")
            agentMaxSteps = config.agentMaxSteps?.toString() || '';
            agentTimeoutSeconds = config.agentTimeoutSeconds?.toString() || '';
            agentMaxTokensPerRun = config.agentMaxTokensPerRun?.toString() || '';

        } catch (error) {
            console.error('Error loading configurations:', error);
            // Keep default values on error
        } finally {
            isLoading = false;
        }
    }

    function validateForm() {
        const newErrors = {};
        
        if (!driver) {
            newErrors.driver = 'Seleção de driver é obrigatória';
        }
        
        if (!defaultWaitingTimeout) {
            newErrors.defaultWaitingTimeout = 'Timeout padrão de espera é obrigatório';
        } else if (isNaN(defaultWaitingTimeout) || Number(defaultWaitingTimeout) <= 0) {
            newErrors.defaultWaitingTimeout = 'Deve ser um número positivo';
        }

        if (!extractionThreads) {
            newErrors.extractionThreads = 'Extrações em paralelo é obrigatório';
        } else if (isNaN(extractionThreads) || Number(extractionThreads) <= 0) {
            newErrors.extractionThreads = 'Deve ser um número positivo';
        }
        
        if (!rootPath.trim()) {
            newErrors.rootPath = 'Diretório raiz é obrigatório';
        }
        
        if (!downloadPath.trim()) {
            newErrors.downloadPath = 'Diretório de download é obrigatório';
        }

        if (!extractionPath.trim()) {
            newErrors.extractionPath = 'Diretório de extração é obrigatório';
        }

        if (agentMode !== 'OFF' && !llmApiKey.trim()) {
            newErrors.llmApiKey = 'Informe a chave de API para usar o agente (ou deixe o agente desligado)';
        }
        for (const [field, value] of [['agentMaxSteps', agentMaxSteps], ['agentTimeoutSeconds', agentTimeoutSeconds], ['agentMaxTokensPerRun', agentMaxTokensPerRun]]) {
            if (value !== '' && (isNaN(value) || Number(value) <= 0)) {
                newErrors[field] = 'Deve ser um número positivo';
            }
        }

        errors = newErrors;
        return Object.keys(newErrors).length === 0;
    }

    async function handleSubmit(event) {
        event.preventDefault();
        
        if (!validateForm()) {
            return;
        }
        
        isSubmitting = true;
        
        try {
            const configData = {
                driver,
                defaultWaitingTimeout: Number(defaultWaitingTimeout),
                extractionThreads: Number(extractionThreads),
                rootPath: rootPath.trim(),
                downloadPath: downloadPath.trim(),
                extractionPath: extractionPath.trim(),
                agentMode,
                llmModel: llmModel.trim(),
                llmApiKey: llmApiKey.trim(), // masked value ("****...") means "keep the stored key"
                agentMaxSteps: agentMaxSteps === '' ? null : Number(agentMaxSteps),
                agentTimeoutSeconds: agentTimeoutSeconds === '' ? null : Number(agentTimeoutSeconds),
                agentMaxTokensPerRun: agentMaxTokensPerRun === '' ? null : Number(agentMaxTokensPerRun)
            };
            
            const response = await fetch('http://localhost:9001/api/configuracoes', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(configData)
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const result = await response.text();
            console.log('Configuration saved:', result);
            alert('Configuração salva com sucesso!');
            
        } catch (error) {
            console.error('Error saving configuration:', error);
            alert('Erro ao salvar configuração. Tente novamente.');
        } finally {
            isSubmitting = false;
        }
    }

    // Clear errors reactively
    $effect(() => {
        if (errors.driver && driver) {
            errors = { ...errors, driver: undefined };
        }
    });

    $effect(() => {
        if (errors.defaultWaitingTimeout && defaultWaitingTimeout) {
            errors = { ...errors, defaultWaitingTimeout: undefined };
        }
    });

    $effect(() => {
        if (errors.extractionThreads && extractionThreads) {
            errors = { ...errors, extractionThreads: undefined };
        }
    });

    $effect(() => {
        if (errors.rootPath && rootPath.trim()) {
            errors = { ...errors, rootPath: undefined };
        }
    });

    $effect(() => {
        if (errors.downloadPath && downloadPath.trim()) {
            errors = { ...errors, downloadPath: undefined };
        }
    });

    $effect(() => {
        if (errors.extractionPath && extractionPath.trim()) {
            errors = { ...errors, extractionPath: undefined };
        }
    });

    // Load configurations on mount
    onMount(() => {
        loadConfigurations();
    });
</script>

<svelte:head>
    <title>Configurações - Baixa Notas Chiquinho</title>
</svelte:head>

<div class="page-container">
    <div class="content-header">
        <h1>Configurações</h1>
    </div>
    
    {#if isLoading}
        <div class="loading-container">
            <p>Carregando configurações...</p>
        </div>
    {:else}
        <form onsubmit={handleSubmit} class="config-form">
            <!-- Driver Selection -->
            <div class="form-group">
                <fieldset class="radio-group">
                    <legend class="field-title">Driver</legend>
                    <p class="field-description">Selecione o driver do navegador para automação</p>
                    
                    <div class="radio-options">
                        <label class="radio-label">
                            <input 
                                type="radio" 
                                bind:group={driver} 
                                value="Firefox"
                                class="radio-input"
                            />
                            <span class="radio-text">Firefox</span>
                        </label>
                        
                        <label class="radio-label">
                            <input 
                                type="radio" 
                                bind:group={driver} 
                                value="Chrome"
                                class="radio-input"
                            />
                            <span class="radio-text">Chrome</span>
                        </label>
                    </div>
                    
                    {#if errors.driver}
                        <span class="error-message">{errors.driver}</span>
                    {/if}
                </fieldset>
            </div>

            <!-- Default Waiting Timeout -->
            <div class="form-group">
                <label for="timeout" class="field-title">Timeout padrão de espera</label>
                <p class="field-description">
                    Tempo padrão de carregamento dos elementos.<br/>
                    Quanto maior mais lenta a execução, porém menor a chance de erros por lentidão do sistema.
                </p>
                
                <input 
                    type="number"
                    id="timeout"
                    bind:value={defaultWaitingTimeout}
                    min="1"
                    max="300"
                    placeholder="ex: 10"
                    class="number-input"
                    class:error={errors.defaultWaitingTimeout}
                />
                
                {#if errors.defaultWaitingTimeout}
                    <span class="error-message">{errors.defaultWaitingTimeout}</span>
                {/if}
            </div>

            <!-- Extraction threads -->
            <div class="form-group">
                <label for="timeout" class="field-title">Extrações em paralelo</label>
                <p class="field-description">
                    Quantas extrações do arquivo zip de notas fiscais são executadas ao mesmo tempo.<br/>
                    Quanto maior, mais rápido, mas consome mais do processamento da máquina.<br/>
                    Um valor muito alto não terá efeito, porque o número de arquivos extraídos é limitado pela velocidade que a automação consegue baixar os arquivos.
                </p>

                <input
                    type="number"
                    id="extractionThreads"
                    bind:value={extractionThreads}
                    min="1"
                    max="300"
                    placeholder="ex: 10"
                    class="number-input"
                    class:error={errors.extractionThreads}
                />

                {#if errors.extractionThreads}
                    <span class="error-message">{errors.extractionThreads}</span>
                {/if}
            </div>

            <!-- Root Path -->
            <div class="form-group">
                <label for="rootPath" class="field-title">Diretório raiz</label>
                <p class="field-description">Diretório base onde os arquivos de configuração serão armazenados e onde os arquivos originais serão baixados.</p>
                
                <input
                    type="text"
                    id="rootPath"
                    bind:value={rootPath}
                    placeholder="ex: /home/usuario/projeto"
                    class="directory-input"
                    class:error={errors.rootPath}
                />
                
                {#if errors.rootPath}
                    <span class="error-message">{errors.rootPath}</span>
                {/if}
            </div>

            <!-- Download Path -->
            <div class="form-group">
                <label for="downloadPath" class="field-title">Diretório de download</label>
                <p class="field-description">Diretório onde as notas serão extraídas</p>
                
                <input
                    type="text"
                    id="downloadPath"
                    bind:value={downloadPath}
                    placeholder="ex: /home/usuario/downloads"
                    class="directory-input"
                    class:error={errors.downloadPath}
                />
                
                {#if errors.downloadPath}
                    <span class="error-message">{errors.downloadPath}</span>
                {/if}
            </div>

            <!-- Extraction Path -->
            <div class="form-group">
                <label for="extractionPath" class="field-title">Diretório de extração</label>
                <p class="field-description">Diretório onde as notas fiscais serão extraídas</p>
                
                <input
                    type="text"
                    id="extractionPath"
                    bind:value={extractionPath}
                    placeholder="ex: /home/usuario/notas-fiscais"
                    class="directory-input"
                    class:error={errors.extractionPath}
                />
                
                {#if errors.extractionPath}
                    <span class="error-message">{errors.extractionPath}</span>
                {/if}
            </div>

            <!-- LLM agent -->
            <div class="form-group">
                <fieldset class="radio-group">
                    <legend class="field-title">Agente de IA</legend>
                    <p class="field-description">
                        Uma IA (Claude, da Anthropic) ajuda quando o script automático não consegue chegar na tela de consulta de uma franqueadora.
                    </p>
                    <p class="field-description">
                        <b>Desligado:</b> só o script trabalha, como sempre foi.<br/>
                        <b>Reforço:</b> a IA só entra depois que as 3 tentativas do script falharem. Só gera custo quando algo dá errado.<br/>
                        <b>Completo:</b> a IA navega primeiro; se ela falhar, o script assume.
                    </p>
                    <p class="field-description">
                        O download, a extração e a validação das notas continuam sendo feitos pelo programa. A IA só vê o texto das telas, sem CNPJ, CPF ou valores.
                        As instruções, o que a IA aprendeu e os tempos de espera ficam na página <a href="/agente">Agente de IA</a>.
                    </p>

                    <div class="radio-options">
                        <label class="radio-label">
                            <input type="radio" bind:group={agentMode} value="OFF" class="radio-input" />
                            <span class="radio-text">Desligado</span>
                        </label>
                        <label class="radio-label">
                            <input type="radio" bind:group={agentMode} value="FALLBACK" class="radio-input" />
                            <span class="radio-text">Reforço</span>
                        </label>
                        <label class="radio-label">
                            <input type="radio" bind:group={agentMode} value="FULL" class="radio-input" />
                            <span class="radio-text">Completo</span>
                        </label>
                    </div>
                </fieldset>
            </div>

            {#if agentMode !== 'OFF'}
                <div class="form-group">
                    <label for="llmApiKey" class="field-title">Chave de API (Anthropic)</label>
                    <p class="field-description">
                        Use uma chave exclusiva deste projeto, de um workspace com limite de gasto mensal.
                        Depois de salva, a chave aparece mascarada; deixe como está para mantê-la.
                    </p>
                    <input
                        type="password"
                        id="llmApiKey"
                        bind:value={llmApiKey}
                        placeholder="sk-ant-..."
                        autocomplete="off"
                        class="directory-input"
                        class:error={errors.llmApiKey}
                    />
                    {#if errors.llmApiKey}
                        <span class="error-message">{errors.llmApiKey}</span>
                    {/if}
                </div>

                <div class="form-group">
                    <label for="llmModel" class="field-title">Modelo</label>
                    <input
                        type="text"
                        id="llmModel"
                        bind:value={llmModel}
                        placeholder="claude-haiku-5-5"
                        class="directory-input"
                    />
                </div>

                <div class="form-group">
                    <label for="agentMaxSteps" class="field-title">Máximo de passos por franqueadora</label>
                    <input type="number" id="agentMaxSteps" bind:value={agentMaxSteps} min="1" placeholder="15"
                           class="number-input" class:error={errors.agentMaxSteps} />
                    {#if errors.agentMaxSteps}<span class="error-message">{errors.agentMaxSteps}</span>{/if}
                </div>

                <div class="form-group">
                    <label for="agentTimeoutSeconds" class="field-title">Tempo máximo por franqueadora (segundos)</label>
                    <input type="number" id="agentTimeoutSeconds" bind:value={agentTimeoutSeconds} min="1" placeholder="300"
                           class="number-input" class:error={errors.agentTimeoutSeconds} />
                    {#if errors.agentTimeoutSeconds}<span class="error-message">{errors.agentTimeoutSeconds}</span>{/if}
                </div>

                <div class="form-group">
                    <label for="agentMaxTokensPerRun" class="field-title">Limite de tokens por execução</label>
                    <p class="field-description">Trava de segurança: ao atingir o limite a IA para e as franqueadoras restantes seguem só com o script.</p>
                    <input type="number" id="agentMaxTokensPerRun" bind:value={agentMaxTokensPerRun} min="1" placeholder="400000"
                           class="number-input" class:error={errors.agentMaxTokensPerRun} />
                    {#if errors.agentMaxTokensPerRun}<span class="error-message">{errors.agentMaxTokensPerRun}</span>{/if}
                </div>
            {/if}

            <!-- Submit Button -->
            <div class="form-actions">
                <button 
                    type="submit" 
                    disabled={isSubmitting}
                    class="submit-button"
                >
                    {isSubmitting ? 'Salvando...' : 'Salvar Configuração'}
                </button>
            </div>
        </form>
    {/if}
</div>

<style>
    .page-container {
        background: white;
        color: #333;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        display: grid;
        grid-template-rows: auto 1fr; /* header, form */
        height: calc(100vh - 72px - 30px);
        margin-bottom: 2rem;
    }

    .content-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 1rem;
        padding-bottom: 1rem;
        border-bottom: 1px solid #e1e5e9;
    }

    .content-header h1 {
        margin: 0;
        color: #333;
        font-size: 1.5rem;
        font-weight: 600;
        line-height: 2.3rem;
    }

    .loading-container {
        text-align: center;
        padding: 2rem;
        color: #666;
    }

    .config-form {
        display: flex;
        flex-direction: column;
        gap: 2rem;
        padding-bottom: 2rem;
    }

    .form-group {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
    }

    .field-title {
        font-weight: 600;
        color: #333;
        margin: 0;
        font-size: 1rem;
    }

    .field-description {
        font-size: 0.875rem;
        color: #666;
        margin: 0;
        line-height: 1.4;
    }

    /* Radio Group Styles */
    .radio-group {
        border: none;
        padding: 0;
        margin: 0;
    }

    .radio-group legend {
        padding: 0;
        margin: 0 0 0.5rem 0;
    }

    .radio-options {
        display: flex;
        gap: 1.5rem;
        margin-top: 0.75rem;
    }

    .radio-label {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        cursor: pointer;
        font-size: 0.9rem;
    }

    .radio-input {
        margin: 0;
    }

    .radio-text {
        color: #333;
    }

    /* Input Styles */
    .number-input,
    .directory-input {
        padding: 0.75rem;
        border: 2px solid #e1e5e9;
        border-radius: 6px;
        font-size: 0.9rem;
        transition: border-color 0.2s ease;
    }

    .number-input:focus,
    .directory-input:focus {
        outline: none;
        border-color: #4f46e5;
        box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
    }

    .number-input.error,
    .directory-input.error {
        border-color: #ef4444;
    }

    .number-input.error:focus,
    .directory-input.error:focus {
        box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.1);
    }

    /* Error Message */
    .error-message {
        color: #ef4444;
        font-size: 0.8rem;
        margin-top: 0.25rem;
    }

    /* Submit Button */
    .form-actions {
        display: flex;
        justify-content: center;
        margin-top: 1rem;
    }

    .submit-button {
        background: #4f46e5;
        color: white;
        border: none;
        padding: 0.875rem 2rem;
        border-radius: 6px;
        font-size: 1rem;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s ease;
        min-width: 200px;
    }

    .submit-button:hover:not(:disabled) {
        background: #4338ca;
        transform: translateY(-1px);
    }

    .submit-button:disabled {
        background: #9ca3af;
        cursor: not-allowed;
        transform: none;
    }

    .submit-button:active:not(:disabled) {
        transform: translateY(0);
    }

    /* Responsive Design */
    @media (max-width: 640px) {
        .page-container {
            margin: 1rem;
            padding: 1.5rem;
        }

        .radio-options {
            flex-direction: column;
            gap: 1rem;
        }

        .submit-button {
            width: 100%;
        }
    }
</style>