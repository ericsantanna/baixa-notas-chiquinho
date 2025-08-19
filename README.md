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
