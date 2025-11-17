Param(
  [string]$FileName
)

$BackupDir = "backups"
$Container = "clube-quinze-postgres"
$Db = "clube_quinze"
$User = "postgres"

if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir | Out-Null }

if (-not $FileName) {
  $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
  $FileName = "backup_$stamp.sql"
}

$BackupPath = Join-Path $BackupDir $FileName

Write-Host "=== Fazendo backup do banco de dados ==="
Write-Host "Container: $Container"
Write-Host "Database: $Db"
Write-Host "Arquivo: $BackupPath"

# Verificar container
$running = docker ps --format '{{.Names}}' | Select-String $Container
if (-not $running) {
  Write-Error "Container $Container não está rodando"
  exit 1
}

# Executar backup
$cmd = "pg_dump -U $User $Db"
docker exec $Container sh -c $cmd | Set-Content -Path $BackupPath -Encoding UTF8

if ($LASTEXITCODE -ne 0) {
  Write-Error "Falha no backup"
  exit 1
}

# Compactar
$GzPath = "$BackupPath.gz"
Compress-Archive -Path $BackupPath -DestinationPath "$BackupPath.zip" -Force
Write-Host "[OK] Backup salvo em: $BackupPath"
Write-Host "[OK] Backup zipado em: $BackupPath.zip"

