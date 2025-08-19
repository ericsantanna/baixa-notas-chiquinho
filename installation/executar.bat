@echo off
setlocal enabledelayedexpansion

echo Iniciando a aplicacao Baixa Notas Chiquinho...

:: Definindo os caminhos para o JRE e o arquivo JAR
set JAVA_PATH=jre\bin\java.exe
set JAR_FILE=baixa-notas-chiquinho-1.0.jar

:: Verificando se o executável Java existe
if not exist "%JAVA_PATH%" (
    echo ERRO: Java nao encontrado em %JAVA_PATH%
    echo Por favor, verifique se a instalacao esta completa e inclui a pasta JRE.
    pause
    exit /b 1
)

:: Verificando se o arquivo JAR existe
if not exist "%JAR_FILE%" (
    echo ERRO: Arquivo JAR nao encontrado em %JAR_FILE%
    echo Por favor, verifique se a aplicacao esta instalada corretamente.
    pause
    exit /b 1
)

:: Abrindo o navegador em segundo plano após um breve atraso
start "" cmd /c "timeout /t 5 > nul && start http://localhost:8080"

:: Executando a aplicação Java
echo A aplicacao esta iniciando e o navegador sera aberto em 5 segundos...
echo Executando: %JAVA_PATH% -jar "%JAR_FILE%"

:: Usando call para garantir que o batch não encerre imediatamente
call "%JAVA_PATH%" -jar "%JAR_FILE%"

:: Se chegarmos aqui, a aplicação Java foi encerrada
echo A aplicacao foi encerrada.
pause
