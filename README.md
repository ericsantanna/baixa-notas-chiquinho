# Baixa Notas Chiquinho

Este aplicativo automatiza o download e o processamento de notas fiscais do sistema ITFGestor. Utiliza Geb e Selenium para automação de navegador, e é construído com Spring Boot e Groovy.

## Funcionalidades
- Automatiza login e navegação no ITFGestor
- Seleciona franquia, mês e ano
- Faz download dos arquivos XML das notas fiscais
- Extrai e processa os arquivos baixados
- Toda configuração é feita via `application.yml`

## Configuração
Edite o arquivo `src/main/resources/application.local.yml` para definir suas credenciais, período e diretórios:

```yaml
baixa-notas:
  root-dir: /tmp/notas-fiscais
  download-dir: /tmp/baixa-notas
  usuario: seu-usuario-aqui
  senha: sua-senha-aqui
  mes: seu-mes-aqui
  ano: seu-ano-aqui
```

## Build
Para compilar o aplicativo, execute:

```
./gradlew clean bootJar
```

O JAR executável será gerado em:

```
build/libs/baixa-notas-chiquinho-1.0-SNAPSHOT.jar
```

## Execução
Para iniciar a aplicação Spring Boot:

```
java -jar build/libs/baixa-notas-chiquinho-1.0-SNAPSHOT.jar --spring.config.location=src/main/resources/application.local.yml
```

No seu navegador acesse a URL:

```
GET http://localhost:9001/executar
```

Isso iniciará o processo de automação usando as configurações do `application.yml`.

## Requisitos
- Java 21 ou superior
- Navegador Firefox (para Selenium WebDriver)

## Observações
- Toda a lógica de automação está em `src/main/groovy/com/ericsantanna/baixaNotas/automation/scripts/Script.groovy`.
- Você pode alterar os diretórios de download e saída, credenciais e período no `application.yml` sem modificar o código.
- Saídas e logs são gravados nos diretórios configurados.
