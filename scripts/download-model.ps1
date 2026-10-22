param([string]$Proxy = '')
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$directory = Join-Path $project 'docker/models'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$file = Join-Path $directory 'qwen2.5-0.5b-instruct-q4_k_m.gguf'
$expected = '74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db'
if ((Test-Path -LiteralPath $file) -and ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -eq $expected)) {
    Write-Host 'Official Qwen GGUF already present; SHA256 verified.'
    exit 0
}
$uri = 'https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf'
Write-Host 'Downloading official Qwen2.5 0.5B Instruct Q4_K_M (491400032 bytes)...'
$ProgressPreference = 'SilentlyContinue'
if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
    # Small HTTP ranges tolerate proxy interruptions. Keep parts for a retry.
    $parts = "$file.parts"
    New-Item -ItemType Directory -Path $parts -Force | Out-Null
    $arguments = @('--parallel', '--parallel-immediate', '--parallel-max', '8')
    $total = 491400032L
    $chunk = 8MB
    $paths = @()
    for ($start = 0L; $start -lt $total; $start += $chunk) {
        $end = [Math]::Min($start + $chunk - 1, $total - 1)
        $part = Join-Path $parts ([string]$start)
        $paths += $part
        if ((Test-Path -LiteralPath $part) -and ((Get-Item -LiteralPath $part).Length -eq $end-$start+1)) { continue }
        if ($arguments.Count -gt 5) { $arguments += '--next' }
        $arguments += @('--fail', '--location', '--silent', '--show-error', '--retry', '8', '--retry-all-errors', '--connect-timeout', '20', '--max-time', '300', '--range', "$start-$end", '--output', $part)
        if ($Proxy) { $arguments += @('--proxy', $Proxy) }
        $arguments += $uri
    }
    if ($arguments.Count -gt 5) {
        & curl.exe @arguments
        if ($LASTEXITCODE -ne 0) { throw 'Model download interrupted. Run this script again to reuse completed parts.' }
    }
    $output = [IO.File]::Create("$file.partial")
    try {
        foreach ($part in $paths) {
            $inputFile = [IO.File]::OpenRead($part)
            try { $inputFile.CopyTo($output) } finally { $inputFile.Dispose() }
        }
    } finally { $output.Dispose() }
} else {
    $parameters = @{Uri=$uri; OutFile="$file.partial"}
    if ($Proxy) { $parameters.Proxy = $Proxy }
    Invoke-WebRequest @parameters
}
if ((Get-FileHash -LiteralPath "$file.partial" -Algorithm SHA256).Hash -ne $expected) {
    throw 'Downloaded model SHA256 mismatch; partial file kept for inspection.'
}
Move-Item -LiteralPath "$file.partial" -Destination $file -Force
if ($paths) {
    foreach ($part in $paths) { Remove-Item -LiteralPath $part }
    Remove-Item -LiteralPath $parts
}
Write-Host 'Model downloaded and SHA256 verified. Qwen2.5 0.5B is a small demonstration baseline.'
