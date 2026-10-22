param([string]$BaseUrl = 'http://127.0.0.1:5173')
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
function Assert-Api($Response, [string]$Action) {
    if ($Response.code -ne 0) { throw "$Action failed: $($Response.message)" }
}
$health = Invoke-RestMethod "$BaseUrl/api/health"
$account = 'smoke_' + [guid]::NewGuid().ToString('N').Substring(0, 14)
$password = [guid]::NewGuid().ToString('N')
$register = @{userAccount=$account; userPassword=$password; checkPassword=$password} | ConvertTo-Json
$registered = Invoke-RestMethod "$BaseUrl/api/user/register" -Method Post -ContentType 'application/json' -Body $register
Assert-Api $registered 'register'
$login = @{userAccount=$account; userPassword=$password} | ConvertTo-Json
$loggedIn = Invoke-RestMethod "$BaseUrl/api/user/login" -Method Post -ContentType 'application/json' -Body $login -SessionVariable session
Assert-Api $loggedIn 'login'
$me = Invoke-RestMethod "$BaseUrl/api/user/get/login" -WebSession $session
Assert-Api $me 'session'
if ($me.data.id -ne $loggedIn.data.id) { throw 'Session identity mismatch' }
$roles = Invoke-RestMethod "$BaseUrl/api/app/good/list/page/vo" -Method Post -ContentType 'application/json' -Body '{"pageNum":1,"pageSize":20}' -WebSession $session
Assert-Api $roles 'featured roles'
if ($roles.data.records.Count -lt 2) { throw 'Expected two curated role seeds' }
Write-Host "PASS: health, real registration, login, Redis-backed session, featured roles ($($roles.data.records.Count))."
Write-Host 'Created one isolated smoke user; this script does not test LLM output or cloud speech.'
