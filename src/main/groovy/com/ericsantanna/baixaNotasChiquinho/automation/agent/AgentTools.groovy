package com.ericsantanna.baixaNotasChiquinho.automation.agent

/** Tool definitions exposed to the model (the complete list of what it can do). */
class AgentTools {
    /** Optional: only worth setting after a 'tempo esgotado' error; the system already uses a timeout learned from history. */
    private static final Map TIMEOUT_PARAM = [type: 'integer', minimum: 5, maximum: 120,
                                                description: 'opcional: tempo máximo de espera em segundos; use só depois de um erro de tempo esgotado']

    static List<Map> specs() {
        return [
                [name        : 'click',
                 description : 'Clica no elemento com o id indicado na observação mais recente. Os ids mudam a cada passo.',
                 input_schema: [type      : 'object',
                                properties: [id: [type: 'integer', description: 'id do elemento, o número entre colchetes']],
                                required  : ['id']]],
                [name        : 'scroll',
                 description : 'Rola a página e as listas roláveis ("up"/"down"), ou centraliza o elemento id na tela.',
                 input_schema: [type      : 'object',
                                properties: [direction: [type: 'string', enum: ['up', 'down']],
                                             id       : [type: 'integer', description: 'opcional: elemento a centralizar']],
                                required  : []]],
                [name        : 'wait',
                 description : 'Aguarda de 1 a 10 segundos (use quando houver overlay ou carregamento).',
                 input_schema: [type      : 'object',
                                properties: [seconds: [type: 'integer', minimum: 1, maximum: 10]],
                                required  : ['seconds']]],
                [name        : 'go_home',
                 description : 'Volta para a página inicial do ITFGestor (mantém o login).',
                 input_schema: [type: 'object', properties: [:], required: []]],
                [name        : 'select_franqueadora',
                 description : 'PASSO 1. Seleciona no cabeçalho a franqueadora-alvo do objetivo (abre a lista, rola até o item e confirma que o cabeçalho mudou). Prefira esta ferramenta aos cliques manuais; se der erro, leia a mensagem, corrija o estado (wait, go_home) e tente de novo.',
                 input_schema: [type: 'object', properties: [timeout_seconds: TIMEOUT_PARAM], required: []]],
                [name        : 'open_consulta_pdv2_menu',
                 description : 'PASSO 2. Abre no menu lateral Fiscal > Gerenciamento > Consulta Nota Fiscal Venda PDV2 (expande os submenus sozinho). Só faça depois que o cabeçalho mostrar a franqueadora-alvo.',
                 input_schema: [type: 'object', properties: [timeout_seconds: TIMEOUT_PARAM], required: []]],
                [name        : 'finish',
                 description : 'Declara que o objetivo foi atingido. O sistema verifica de forma independente; se não estiver correto, você continuará.',
                 input_schema: [type: 'object', properties: [:], required: []]],
                [name        : 'give_up',
                 description : 'Desiste quando não há mais o que tentar. Explique o motivo.',
                 input_schema: [type      : 'object',
                                properties: [reason: [type: 'string']],
                                required  : ['reason']]],
        ]
    }
}
