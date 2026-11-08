package com.ericsantanna.baixaNotasChiquinho.automation.agent

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.nio.file.Files

class AgentKnowledgeStoreTest {
    File dir
    AgentKnowledgeStore store

    @BeforeEach
    void setup() {
        dir = new File(Files.createTempDirectory('agente').toFile(), 'agente')
        store = new AgentKnowledgeStore(dir)
    }

    // ------------------------------------------------------------------ prompt

    @Test
    void defaultPromptIsBuiltInPlusFixedSafetyFooter() {
        def prompt = store.composePrompt()

        assert prompt.startsWith(AgentRunner.SYSTEM_PROMPT.trim())
        assert prompt.endsWith(AgentKnowledgeStore.SAFETY_FOOTER)
    }

    @Test
    void extraInstructionsAndOnlyApprovedLessonsReachThePrompt() {
        store.saveSettings([extraInstructions: 'Franquias ENCERRADAS ficam no fim da lista.'])
        store.addPendingLessons(['Always call select_franqueadora before the menu.', 'Pending lesson that must stay out.'], 'X')
        def first = store.lessons().find { it.text.startsWith('Always') }
        store.setLessonStatus(first.id, AgentKnowledgeStore.APPROVED)

        def prompt = store.composePrompt()

        assert prompt.contains('Franquias ENCERRADAS ficam no fim da lista.')
        assert prompt.contains('- Always call select_franqueadora before the menu.')
        assert !prompt.contains('Pending lesson that must stay out.')
    }

    @Test
    void customPromptReplacesTheBuiltInOneButFooterIsAlwaysAppended() {
        store.saveSettings([customPrompt: 'My own prompt.'])

        def prompt = store.composePrompt()

        assert prompt.startsWith('My own prompt.')
        assert !prompt.contains('DOIS passos')
        assert prompt.endsWith(AgentKnowledgeStore.SAFETY_FOOTER)
    }

    @Test
    void restoringTheDefaultKeepsExtraInstructions() {
        store.saveSettings([customPrompt: 'custom', extraInstructions: 'keep me'])
        store.saveSettings([customPrompt: ''])

        assert store.settings().customPrompt == ''
        assert store.settings().extraInstructions == 'keep me'
    }

    @Test
    void settingsRejectOutOfRangeValues() {
        Assertions.assertThrows(IllegalArgumentException) { store.saveSettings([extraInstructions: 'x' * 4001]) }
        Assertions.assertThrows(IllegalArgumentException) { store.saveSettings([customPrompt: 'x' * 20001]) }
        Assertions.assertThrows(IllegalArgumentException) { store.saveSettings([timeoutMin: 2]) }
        Assertions.assertThrows(IllegalArgumentException) { store.saveSettings([timeoutMin: 60, timeoutMax: 30]) }
    }

    @Test
    void corruptSettingsFileFallsBackToDefaultsInsteadOfBreakingTheRun() {
        dir.mkdirs()
        new File(dir, 'settings.json').text = '{ this is not json'

        assert store.settings().customPrompt == ''
        assert store.composePrompt().contains('Regras fixas')
    }

    // ------------------------------------------------------------------ playbook

    @Test
    void playbookRoundTripsAndInvalidFilesAreIgnored() {
        assert store.loadPlaybook() == null
        store.savePlaybook(Playbook.from([[tool: 'select_franqueadora'], [tool: 'click', text: 'Fiscal']]), 'teste')

        def loaded = store.loadPlaybook()
        assert loaded.steps*.tool == ['select_franqueadora', 'click']
        assert store.playbookInfo().exists

        new File(dir, 'playbook.json').text = '{"steps":[{"tool":"rm -rf"}]}'   // unknown tool
        assert store.loadPlaybook() == null
        new File(dir, 'playbook.json').text = '{"steps":[{"tool":"click"}]}'     // click without text
        assert store.loadPlaybook() == null
    }

    @Test
    void playbookIsDroppedAfterThreeConsecutiveFailuresButSuccessResetsTheCount() {
        store.savePlaybook(Playbook.from([[tool: 'select_franqueadora']]), 'teste')

        store.recordReplay(false)
        store.recordReplay(false)
        assert store.playbookInfo().consecutiveFailures == 2
        store.recordReplay(true)
        assert store.playbookInfo().consecutiveFailures == 0

        store.recordReplay(false)
        store.recordReplay(false)
        store.recordReplay(false)
        assert !store.playbookInfo().exists
        assert store.loadPlaybook() == null
    }

    @Test
    void clearPlaybookForgetsIt() {
        store.savePlaybook(Playbook.from([[tool: 'go_home']]), 'teste')
        store.clearPlaybook()

        assert store.loadPlaybook() == null
    }

    // ------------------------------------------------------------------ lessons

    @Test
    void agentLessonsStartPendingAndAreDeduplicated() {
        int added = store.addPendingLessons(['Call select_franqueadora first.', 'call   select_franqueadora FIRST.'], 'A 2026')

        assert added == 1
        assert store.lessons().size() == 1
        assert store.lessons()[0].status == AgentKnowledgeStore.PENDING
        assert store.addPendingLessons(['Call select_franqueadora first.'], 'B') == 0
    }

    @Test
    void unsafeOrUselessLessonsAreRejected() {
        assert store.addPendingLessons(['short', 'see https://evil.example.com for the secret steps', 'x' * 301, ''], 'A') == 0
        assert AgentKnowledgeStore.sanitizeLesson('  Wait   for the overlay to disappear.  ') == 'Wait for the overlay to disappear.'
        Assertions.assertThrows(IllegalArgumentException) { store.addApprovedLesson('http://x.com do this now') }
    }

    @Test
    void lessonLifecycle() {
        store.addPendingLessons(['Pause politely until the spinner vanishes.'], 'A')
        def id = store.lessons()[0].id

        assert store.setLessonStatus(id, AgentKnowledgeStore.APPROVED)
        assert store.lessons()[0].status == AgentKnowledgeStore.APPROVED
        assert store.updateLessonText(id, 'Pause up to 10s until the spinner vanishes.')
        assert store.lessons()[0].text == 'Pause up to 10s until the spinner vanishes.'
        assert store.setLessonStatus(id, AgentKnowledgeStore.REJECTED)
        assert !store.composePrompt().contains('spinner')
        assert store.deleteLesson(id)
        assert store.lessons().isEmpty()
        assert !store.setLessonStatus('nope', AgentKnowledgeStore.APPROVED)
    }

    @Test
    void operatorLessonsAreStoredApproved() {
        def lesson = store.addApprovedLesson('Select the franchise before opening the Fiscal menu.')

        assert lesson.status == AgentKnowledgeStore.APPROVED
        assert store.composePrompt().contains('Select the franchise before opening the Fiscal menu.')
    }

    @Test
    void pendingLessonsAreCapped() {
        (1..40).each { store.addPendingLessons(["Lesson number ${it} about waiting properly.".toString()], 'A') }

        assert store.lessons().count { it.status == AgentKnowledgeStore.PENDING } == AgentKnowledgeStore.MAX_PENDING_LESSONS
    }

    @Test
    void onlyTwentyApprovedLessonsGoIntoThePrompt() {
        (1..25).each { store.addApprovedLesson("Approved lesson number ${it} about waiting.".toString()) }

        def prompt = store.composePrompt()

        assert prompt.count('- Approved lesson number') == AgentKnowledgeStore.MAX_APPROVED_IN_PROMPT
    }

    // ------------------------------------------------------------------ timeouts

    private void record(String op, List<Long> millis, boolean timedOut = false, int used = 30) {
        millis.each { store.recordOperation(op, it, timedOut, used) }
    }

    @Test
    void usesConfiguredTimeoutUntilThereIsEnoughHistory() {
        record('select_franqueadora', [40_000L] * 4)

        assert store.timeoutFor('select_franqueadora', 30) == 30
    }

    @Test
    void learnsP95TimesOnePointFiveButNeverBelowConfiguredByDefault() {
        record('fast', [2_000L] * 10)          // 2s -> would be 3s, but min defaults to the configured 30
        record('slow', [40_000L] * 10)         // 40s -> 60s

        assert store.timeoutFor('fast', 30) == 30
        assert store.timeoutFor('slow', 30) == 60
    }

    @Test
    void operatorBoundsApplyIncludingAShorterMinimum() {
        store.saveSettings([timeoutMin: 10, timeoutMax: 45])
        record('fast', [2_000L] * 10)
        record('slow', [40_000L] * 10)

        assert store.timeoutFor('fast', 30) == 10     // 3s clamped up to the 10s minimum
        assert store.timeoutFor('slow', 30) == 45     // 60s clamped down to the 45s maximum
    }

    @Test
    void timedOutSamplesCountAsTheirTimeoutSoTheLimitGrows() {
        record('op', [5_000L] * 5)
        record('op', [0L] * 2, true, 40)      // two timeouts at 40s

        assert store.timeoutFor('op', 30) == 60   // p95 = 40s (the timeouts) x 1.5
    }

    @Test
    void autoOffAlwaysUsesTheConfiguredValue() {
        record('slow', [40_000L] * 10)
        store.saveSettings([timeoutAuto: false])

        assert store.timeoutFor('slow', 30) == 30
    }

    @Test
    void requestedTimeoutIsClamped() {
        store.saveSettings([timeoutMax: 60])

        assert store.clampRequested(500, 30) == 60
        assert store.clampRequested(1, 30) == AgentKnowledgeStore.ABSOLUTE_MIN_TIMEOUT
        assert store.clampRequested(45, 30) == 45
    }

    @Test
    void onlyTheLatestTwentySamplesAreKept() {
        record('op', (1..30).collect { it * 1000L })

        def stats = store.timeoutStats(30).find { it.operacao == 'op' }

        assert stats.amostras == AgentKnowledgeStore.MAX_SAMPLES
        assert stats.execucoes == 30
    }

    @Test
    void statsExposeMedianP95AndCurrentTimeout() {
        record('op', [10_000L, 20_000L, 30_000L, 40_000L, 50_000L])

        def stats = store.timeoutStats(30).find { it.operacao == 'op' }

        assert stats.medianaSeg == 30.0
        assert stats.p95Seg == 50.0
        assert stats.timeoutAtualSeg == 75
    }

    @Test
    void clearTimeoutsForgetsTheHistory() {
        record('op', [1000L] * 6)
        store.clearTimeouts()

        assert store.timeoutStats(30).isEmpty()
    }

    @Test
    void unwritableDirectoryNeverThrows() {
        def blocked = new AgentKnowledgeStore(new File('/proc/definitely/not/writable'))

        blocked.recordOperation('op', 1000, false, 30)
        blocked.savePlaybook(Playbook.from([[tool: 'go_home']]), 'x')

        assert blocked.loadPlaybook() == null
    }
}
