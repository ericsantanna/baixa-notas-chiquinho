@echo off
setlocal enabledelayedexpansion

echo Instalando Baixa Notas Chiquinho...
echo.

:: Verificando se a pasta JRE existe
if not exist "jre" (
    echo ERRO: Pasta JRE nao encontrada.
    echo Por favor, verifique se voce extraiu todos os arquivos corretamente.
    pause
    exit /b 1
)

:: Verificando se o arquivo JAR existe
if not exist "baixa-notas-chiquinho-1.0.jar" (
    echo ERRO: Arquivo JAR nao encontrado.
    echo Por favor, verifique se voce extraiu todos os arquivos corretamente.
    pause
    exit /b 1
)

:: Obtendo o diretório atual
set "CURRENT_DIR=%CD%"
echo Diretorio da aplicacao: %CURRENT_DIR%

:: Criando o arquivo executar.bat se não existir
if not exist "executar.bat" (
    echo Criando arquivo de execucao...
    (
    echo @echo off
    echo setlocal enabledelayedexpansion
    echo.
    echo echo Iniciando a aplicacao Baixa Notas Chiquinho...
    echo.
    echo :: Definindo os caminhos para o JRE e o arquivo JAR
    echo set JAVA_PATH=jre\bin\java.exe
    echo set JAR_FILE=baixa-notas-chiquinho-1.0.jar
    echo.
    echo :: Verificando se o executavel Java existe
    echo if not exist "%%JAVA_PATH%%" (
    echo     echo ERRO: Java nao encontrado em %%JAVA_PATH%%
    echo     echo Por favor, verifique se a instalacao esta completa e inclui a pasta JRE.
    echo     pause
    echo     exit /b 1
    echo ^)
    echo.
    echo :: Verificando se o arquivo JAR existe
    echo if not exist "%%JAR_FILE%%" (
    echo     echo ERRO: Arquivo JAR nao encontrado em %%JAR_FILE%%
    echo     echo Por favor, verifique se a aplicacao esta instalada corretamente.
    echo     pause
    echo     exit /b 1
    echo ^)
    echo.
    echo :: Abrindo o navegador em segundo plano apos um breve atraso
    echo start "" cmd /c "timeout /t 5 ^> nul ^&^& start http://localhost:8080"
    echo.
    echo :: Executando a aplicacao Java
    echo echo A aplicacao esta iniciando e o navegador sera aberto em 5 segundos...
    echo echo Executando: %%JAVA_PATH%% -jar "%%JAR_FILE%%"
    echo.
    echo :: Usando call para garantir que o batch nao encerre imediatamente
    echo call "%%JAVA_PATH%%" -jar "%%JAR_FILE%%"
    echo.
    echo :: Se chegarmos aqui, a aplicacao Java foi encerrada
    echo echo A aplicacao foi encerrada.
    echo pause
    ) > executar.bat
)

:: Usando PowerShell para obter o caminho da Área de Trabalho e criar o atalho
echo Criando atalho na area de trabalho...

:: Comando PowerShell para criar o atalho
powershell -Command "& {try { $desktop = [Environment]::GetFolderPath('Desktop'); $iconPath = '%CURRENT_DIR%\app.ico'; $WshShell = New-Object -ComObject WScript.Shell; $Shortcut = $WshShell.CreateShortcut(\"$desktop\Baixa Notas Chiquinho.lnk\"); $Shortcut.TargetPath = '%CURRENT_DIR%\executar.bat'; $Shortcut.WorkingDirectory = '%CURRENT_DIR%'; $Shortcut.Description = 'Baixa Notas Chiquinho'; if (Test-Path '%CURRENT_DIR%\app.ico') { $Shortcut.IconLocation = '%CURRENT_DIR%\app.ico' }; $Shortcut.Save(); Write-Output \"Atalho criado com sucesso em $desktop\"; exit 0; } catch { Write-Output \"Erro ao criar atalho: $_\"; exit 1; }}"

:: Verificando o resultado do PowerShell
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Instalacao concluida com sucesso!
    echo Um atalho "Baixa Notas Chiquinho" foi criado na sua area de trabalho.
    echo.
    echo Para iniciar a aplicacao, clique duas vezes no atalho criado.
) else (
    echo.
    echo AVISO: Nao foi possivel criar automaticamente o atalho na area de trabalho.
    echo.
    echo Opcoes para iniciar a aplicacao:
    echo 1. Execute o arquivo "executar.bat" diretamente nesta pasta.
    echo 2. Crie manualmente um atalho para o arquivo "executar.bat":
    echo    - Clique com o botao direito na area de trabalho
    echo    - Selecione "Novo" e depois "Atalho"
    echo    - No campo "Localizacao", digite: %CURRENT_DIR%\executar.bat
    echo    - Clique em "Avancar" e digite "Baixa Notas Chiquinho" como nome
    echo    - Clique em "Concluir"
    echo.
)

echo.
echo Pressione qualquer tecla para finalizar a instalacao...
pause > nul