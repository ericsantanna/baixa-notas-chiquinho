# Baixa Notas Chiquinho

Este aplicativo automatiza o download e o processamento de notas fiscais do sistema ITFGestor. Utiliza Geb e Selenium para automação de navegador, e é construído com Spring Boot e Groovy.

## Funcionalidades
- Automatiza login e navegação no ITFGestor
- Seleciona franquia, mês e ano
- Faz download dos arquivos XML das notas fiscais
- Extrai e processa os arquivos baixados
- Interface web para configuração e execução

## Instalação

1. Execute o arquivo `installation/instalar.bat` para criar um ícone na área de trabalho.
2. Ao clicar no ícone, o script `installation/executar.bat` será executado, abrindo o navegador padrão e um terminal com o servidor.

## Configurações

Na página de Configurações do aplicativo, você pode:

- Escolher o navegador (Firefox ou Chrome) - é necessário ter o navegador já instalado em seu sistema
- Alterar os diretórios de trabalho, download e extração
- Definir tempos de espera e número de threads para extração
- Todas as configurações são salvas automaticamente
- Ligar o **Agente de IA** (desligado por padrão) - veja a seção abaixo

## Agente de IA

Quando o script automático não consegue chegar na tela "Consulta Cupom Fiscal PDV" de uma franqueadora, uma IA (Claude, da Anthropic) pode assumir a navegação. Três modos, na página Configurações:

- **Desligado** (padrão): comportamento original, 3 tentativas do script por franqueadora.
- **Reforço**: depois das 3 tentativas do script falharem, a IA tenta navegar. Só gasta tokens se algo falhar.
- **Completo**: a IA navega primeiro (o primeiro caminho bem-sucedido é gravado e reaproveitado nas próximas franqueadoras sem chamar a IA); se falhar, o script (3 tentativas) assume.

Garantias:
- A IA **só navega** (clicar, rolar, esperar, voltar ao início, abrir o menu de consulta). Download, extração e validação das notas continuam sendo do programa.
- Quem decide se a navegação deu certo é uma verificação do programa (franqueadora correta no cabeçalho + tela de consulta aberta), nunca a IA.
- A IA recebe apenas o texto da tela, com CNPJ, CPF, chaves de acesso e valores removidos. Não recebe senha nem imagens. Não digita, não executa scripts, não sai do ITFGestor e não clica em "Sair"/"Excluir"/"Cancelar".
- Limites: passos e tempo por franqueadora, e total de tokens por execução.
- Ao fim de cada execução, toda franqueadora precisa estar em `processados.csv` ou em `nao-processados.csv`; qualquer uma sem resultado é registrada como não processada.
- Cada passo da IA fica registrado em `execucao/diagnostico/agent/<FRANQUEADORA>.jsonl`.

### Página "Métricas"

Cada execução (concluída, interrompida ou com falha) é registrada automaticamente em `execucao/metricas/` e aparece na página Métricas:

- **Resumo geral**: número de execuções, duração média por execução, tempo médio por franqueadora (navegação + download; a extração roda em paralelo e não entra), sucesso técnico, retentativas por execução, tokens gastos pela IA, custo estimado, quanto o agente foi acionado e total de XMLs baixados.
- **Evolução** e **histórico**: duração e resultado de cada execução. Em "Ver" aparece cada franqueadora com situação, tentativas, como chegou na tela (script, agente ou caminho aprendido), tempo, tokens, custo, quantidade de XMLs e o motivo quando não foi processada.
- **Mais lentas** e **mais problemáticas** entre todas as execuções, e **por que** as franqueadoras não foram processadas.
- **"Sem notas no período" não é falha**: a franqueadora simplesmente não tinha notas naquele mês. Só erros técnicos entram na taxa de sucesso técnico.
- **Custo**: é uma estimativa (tokens de entrada e de saída vezes o preço por milhão de tokens). Informe os preços do modelo, em dólares, no fim da página; sem eles o custo aparece como indisponível. O consumo real está no Console da Anthropic.
- O histórico guarda as últimas 120 execuções e nunca inclui usuário ou senha. Uma execução em que o programa for encerrado à força (queda de energia, por exemplo) não chega a ser registrada.

### Página "Agente de IA"

Tudo que o agente guarda entre execuções é gerenciado por essa página, sem mexer em arquivos (ficam em `execucao/agente/`):

- **Instruções**: texto adicional que a IA recebe junto com as instruções padrão (vale na próxima execução). Em "Avançado" dá para substituir o prompt completo (as regras de segurança fixas continuam sendo acrescentadas) e ver exatamente o que a IA recebe.
- **Aprendizado**: o caminho que funcionou (repetido sem gastar tokens nas próximas franqueadoras e execuções; descartado se falhar 3 vezes seguidas) e as **lições** que a IA sugere depois de uma recuperação difícil. Lições sugeridas ficam *pendentes* e não têm efeito nenhum até você aprovar; só as aprovadas (no máximo 20) entram nas instruções. Você também pode escrever lições próprias.
- **Tempos**: o programa mede quanto cada operação demora (selecionar a franqueadora, abrir o menu) e passa a usar 95% do histórico recente + 50% de folga como tempo máximo de espera nas ferramentas da IA. Por padrão nunca fica abaixo do timeout das Configurações; é possível definir mínimo e máximo ou desligar o ajuste automático. A IA também pode pedir um tempo maior (dentro dos limites) depois de um erro de tempo esgotado.

Chave de API: crie um *workspace* exclusivo para este projeto no Console da Anthropic, com limite de gasto mensal (ex.: US$ 5-10) e créditos pré-pagos com recarga automática desligada, e gere uma chave nele. Informe a chave na página Configurações (fica salva em `execucao/config.yaml`, com permissão restrita ao usuário quando o sistema operacional permite) ou defina a variável de ambiente `ANTHROPIC_API_KEY`. Uma assinatura do Claude Code **não** serve como chave de API.

## Utilização

1. Acesse o aplicativo através do ícone criado na área de trabalho
2. Configure os parâmetros necessários na página Configurações
3. Na página principal, preencha suas credenciais e selecione o período
4. Inicie o processo de download e processamento

## Arquivos de Controle

O arquivo `execucao/processados.csv` contém a lista de franqueadoras que já foram processadas. Se você deseja reprocessar alguma franqueadora:

- Apague o arquivo `processados.csv` inteiro para reprocessar todas as franqueadoras
- OU remova apenas as linhas específicas das franqueadoras que deseja reprocessar

## Para Desenvolvedores

### Pré-requisitos
- Java 21
- Node.js e npm
- Firefox ou Chrome

### Como construir o projeto

1. Navegue até a pasta do frontend:
   ```
   cd src/main/webapp/baixa-notas-chiquinho
   ```

2. Instale as dependências e construa o frontend:
   ```
   npm install
   npm run build
   ```

3. Volte para a raiz do projeto e construa o arquivo JAR:
   ```
   cd ../../../../
   ./gradlew clean bootJar
   ```

4. Copie o arquivo JAR gerado para a pasta de instalação:
   ```
   cp build/libs/baixa-notas-chiquinho-1.0-SNAPSHOT.jar installation/
   ```

### Estrutura do Projeto

- A lógica de automação está em `src/main/groovy/com/ericsantanna/baixaNotasChiquinho/automation/scripts/BaixaNotasChiquinhoAutomation.groovy`
- A interface web está em `src/main/webapp/baixa-notas-chiquinho`
- As configurações padrão são definidas em `com.ericsantanna.baixaNotasChiquinho.configuracoes.Configuracoes`

### Observações

- As configurações são armazenadas no arquivo `execucao/config.yaml`
- Os logs e saídas são gravados nos diretórios configurados
- O aplicativo utiliza o Selenium WebDriver para automatizar as ações no navegador
