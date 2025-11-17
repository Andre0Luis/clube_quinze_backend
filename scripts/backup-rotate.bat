@echo off
setlocal enabledelayedexpansion

REM Executa backup e depois mantém apenas os N mais recentes (sql, sql.gz, zip)

set KEEP_COUNT=%~1
if "%KEEP_COUNT%"=="" set KEEP_COUNT=3

set SCRIPT_DIR=%~dp0
pushd "%SCRIPT_DIR%.."

call scripts\backup-database.bat

powershell -NoProfile -Command ^
  "$dir = 'backups'; ^
   $keep = %KEEP_COUNT%; ^
   if (-not (Test-Path $dir)) { exit 0 } ^
   $files = Get-ChildItem -Path $dir -Include 'backup_*.sql','backup_*.sql.gz','backup_*.zip' -File -Recurse | Sort-Object LastWriteTime -Descending; ^
   if ($files.Count -gt $keep) { ^
     $files | Select-Object -Skip $keep | Remove-Item -Force -Verbose ^
   }"

popd

echo [OK] Rotação concluída (máximo %KEEP_COUNT% backups mantidos)

