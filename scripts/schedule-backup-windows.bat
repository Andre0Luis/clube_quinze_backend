@echo off
setlocal

REM Cria/atualiza uma tarefa agendada no Windows para rodar o backup periodicamente.
REM Uso: schedule-backup-windows.bat [intervalo_em_horas] [quantidade_para_manter]

set INTERVAL_HOURS=%~1
if "%INTERVAL_HOURS%"=="" set INTERVAL_HOURS=6

set KEEP_COUNT=%~2
if "%KEEP_COUNT%"=="" set KEEP_COUNT=3

set SCRIPT_DIR=%~dp0
for %%I in ("%SCRIPT_DIR%..") do set PROJ_DIR=%%~fI

set TASK_NAME=ClubeQuinzeDbBackup
set "CMD=cmd.exe /c ""cd /d ""%PROJ_DIR%"" && if not exist backups mkdir backups && scripts\backup-rotate.bat %KEEP_COUNT% >> backups\backup-task.log 2^>^&1"""

echo Criando/atualizando tarefa "%TASK_NAME%" (a cada %INTERVAL_HOURS%h, mantendo %KEEP_COUNT% backups)...

SCHTASKS /Create /TN "%TASK_NAME%" /SC HOURLY /MO %INTERVAL_HOURS% /TR "%CMD%" /RU SYSTEM /RL HIGHEST /F >nul 2>&1
if errorlevel 1 (
	echo [WARN] Falha ao criar como SYSTEM. Tentando no contexto do usuario atual...
	SCHTASKS /Create /TN "%TASK_NAME%" /SC HOURLY /MO %INTERVAL_HOURS% /TR "%CMD%" /RL HIGHEST /F
	if errorlevel 1 (
		echo [ERRO] Nao foi possivel criar a tarefa agendada.
		exit /b 1
	)
)

echo [OK] Tarefa criada/atualizada. Para conferir use: schtasks /Query /TN "%TASK_NAME%" /V /FO LIST
