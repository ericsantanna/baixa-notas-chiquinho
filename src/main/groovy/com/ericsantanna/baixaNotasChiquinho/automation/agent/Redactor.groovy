package com.ericsantanna.baixaNotasChiquinho.automation.agent

import java.util.regex.Pattern

/** Strips fiscal identifiers and amounts before anything is sent to the LLM or written to traces. */
class Redactor {
    // order matters: longest/most specific first
    private static final List<List> RULES = [
            [Pattern.compile('\\b\\d{44}\\b'), '[CHAVE]'],
            [Pattern.compile('\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b'), '[CNPJ]'],
            [Pattern.compile('\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b'), '[CPF]'],
            [Pattern.compile('R\\$\\s*\\d[\\d.]*(?:,\\d{1,2})?'), '[VALOR]'],
            [Pattern.compile('\\b\\d{9,}\\b'), '[NUM]'],
    ]

    static String redact(String text) {
        if (!text) {
            return text
        }
        String out = text
        RULES.each { rule ->
            out = ((Pattern) rule[0]).matcher(out).replaceAll((String) rule[1])
        }
        return out
    }
}
