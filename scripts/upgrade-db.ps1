param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$sql = Join-Path $project 'docker/mysql/upgrade-voice.sql'
if (!$Apply) {
    Get-Content -LiteralPath $sql
    Write-Host 'Back up the database first, then run scripts/upgrade-db.ps1 -Apply. Existing columns and indexes will be skipped.'
    exit 0
}
function Invoke-Database([string]$Query) {
    $Query | docker compose -f (Join-Path $project 'compose.yaml') exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -u"$MYSQL_USER" "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) { throw 'Migration failed; inspect schema before trying again. Never reset an existing volume.' }
}
$columns = @(Invoke-Database "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_history';")
if ($columns -notcontains 'conversation_id') {
    Invoke-Database "ALTER TABLE chat_history ADD COLUMN conversation_id varchar(64) NOT NULL DEFAULT 'legacy';"
}
if ($columns -notcontains 'turn_id') {
    Invoke-Database 'ALTER TABLE chat_history ADD COLUMN turn_id varchar(64) DEFAULT NULL;'
}
$indexes = @(Invoke-Database "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_history';")
if ($indexes -notcontains 'idx_user_app_conversation') {
    Invoke-Database 'ALTER TABLE chat_history ADD INDEX idx_user_app_conversation (user_id, app_id, conversation_id, create_time);'
}
$migration = Get-Content -Raw -LiteralPath $sql
Invoke-Database $migration.Substring($migration.IndexOf('CREATE TABLE'))
Write-Host 'Voice schema ready. Existing data preserved.'
