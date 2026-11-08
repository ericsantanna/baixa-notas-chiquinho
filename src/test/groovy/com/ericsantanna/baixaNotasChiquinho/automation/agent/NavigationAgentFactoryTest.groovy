package com.ericsantanna.baixaNotasChiquinho.automation.agent

import com.ericsantanna.baixaNotasChiquinho.configuracoes.Configuracoes
import org.junit.jupiter.api.Test

class NavigationAgentFactoryTest {

    @Test
    void defaultConfigKeepsAgentOff() {
        assert new Configuracoes().agentMode == 'OFF'
        assert NavigationAgent.create(new Configuracoes(), null) == null
    }

    @Test
    void modeWithoutKeyDisablesTheAgentInsteadOfFailingTheRun() {
        // only meaningful when the machine has no ANTHROPIC_API_KEY exported
        if (System.getenv('ANTHROPIC_API_KEY')) {
            return
        }
        assert NavigationAgent.create(new Configuracoes(agentMode: 'FULL'), null) == null
        assert NavigationAgent.create(new Configuracoes(agentMode: 'FALLBACK', llmApiKey: ''), null) == null
    }

    @Test
    void unknownModeOrProviderDisablesTheAgent() {
        assert NavigationAgent.create(new Configuracoes(agentMode: 'TURBO', llmApiKey: 'k'), null) == null
        assert NavigationAgent.create(new Configuracoes(agentMode: 'FULL', llmProvider: 'other', llmApiKey: 'k'), null) == null
    }

    @Test
    void createsAgentWithConfiguredLimits() {
        def agent = NavigationAgent.create(new Configuracoes(agentMode: 'fallback', llmApiKey: 'sk-x', agentMaxSteps: 7,
                agentTimeoutSeconds: 42, agentMaxTokensPerRun: 1234), null)

        assert agent != null
        assert agent.mode == 'FALLBACK'
        assert !agent.full
        assert agent.maxSteps == 7
        assert agent.timeoutMillis == 42_000L
        assert agent.budget.maxTokens == 1234
        assert ((ClaudeLlmClient) agent.llm).model == Configuracoes.DEFAULT_LLM_MODEL
    }

    @Test
    void maskedConfigNeverExposesTheApiKey() {
        def masked = new Configuracoes(llmApiKey: 'sk-ant-abcdef123456').masked()

        assert masked.llmApiKey == '****3456'
        assert !masked.llmApiKey.contains('sk-ant')
        assert new Configuracoes().masked().llmApiKey == null
    }
}
