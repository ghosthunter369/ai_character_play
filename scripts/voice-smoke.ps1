param([string]$BaseUrl='http://127.0.0.1:5173', [string]$Origin='http://127.0.0.1:5173', [switch]$LongContext)
$ErrorActionPreference='Stop'
$BaseUrl=$BaseUrl.TrimEnd('/')
function Api($Path,$Body) {
    $result=Invoke-RestMethod "$BaseUrl/api/$Path" -Method Post -ContentType 'application/json' -Body ($Body|ConvertTo-Json -Compress) -WebSession $script:session
    if($result.code -ne 0) { throw "API failed: $Path $($result.message)" }; return $result.data
}
function Send-Event($Event) {
    $bytes=[Text.Encoding]::UTF8.GetBytes(($Event|ConvertTo-Json -Compress))
    $null=$script:ws.SendAsync([ArraySegment[byte]]::new($bytes),[Net.WebSockets.WebSocketMessageType]::Text,$true,[Threading.CancellationToken]::None).GetAwaiter().GetResult()
}
function Receive-Event {
    $buffer=New-Object byte[] 65536; $stream=[IO.MemoryStream]::new(); $timeout=[Threading.CancellationTokenSource]::new(60000)
    try {
        do {
            $received=$script:ws.ReceiveAsync([ArraySegment[byte]]::new($buffer),$timeout.Token).GetAwaiter().GetResult()
            if($received.MessageType -eq [Net.WebSockets.WebSocketMessageType]::Close) { throw 'WebSocket closed unexpectedly' }
            $stream.Write($buffer,0,$received.Count)
        } while(-not $received.EndOfMessage)
        return ([Text.Encoding]::UTF8.GetString($stream.ToArray())|ConvertFrom-Json)
    } finally { $stream.Dispose(); $timeout.Dispose() }
}
function Complete-Turn($RequestId) {
    $turn=$null; $text=''; $audio=0; $asr=''; $events=0
    do {
        $event=Receive-Event; $events++
        if($events -gt 5000) { throw 'Too many events' }
        if($event.type -eq 'error') { throw "Pipeline error: $($event.stage) $($event.message)" }
        if($event.type -eq 'turn.started') {
            if($event.requestId -ne $RequestId) { throw 'Wrong request identity' }; $turn=$event.turnId
        }
        if($event.turnId -and $turn -and $event.turnId -ne $turn) { continue }
        if($event.type -eq 'asr.final') { $asr=$event.text }
        if($event.type -eq 'reply.delta') { $text+=$event.text }
        if($event.type -eq 'audio.chunk') {
            $wav=[Convert]::FromBase64String($event.data)
            if([Text.Encoding]::ASCII.GetString($wav,0,4) -ne 'RIFF') { throw 'Expected real WAV synthesis' }; $audio++
        }
    } while($event.type -ne 'turn.done' -or $event.turnId -ne $turn)
    if(-not $turn -or -not $text -or $audio -lt 1) { throw 'Incomplete ASR/LLM/TTS turn' }
    return @{turnId=$turn;text=$text;asr=$asr;audioChunks=$audio}
}
$null=Invoke-RestMethod "$BaseUrl/api/health"
$session=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
$account='voice_'+[guid]::NewGuid().ToString('N').Substring(0,14); $password=[guid]::NewGuid().ToString('N')
$null=Api 'user/register' @{userAccount=$account;userPassword=$password;checkPassword=$password}
$user=Api 'user/login' @{userAccount=$account;userPassword=$password}
$roles=Api 'app/good/list/page/vo' @{pageNum=1;pageSize=20}; $appId=$roles.records[0].appId
$conversation=[guid]::NewGuid().ToString(); $endpoint=$BaseUrl.Replace('https:','wss:').Replace('http:','ws:')+"/api/ws/voice?appId=$appId&conversationId=$conversation&asrProvider=local&ttsProvider=local"
$anonymous=[Net.WebSockets.ClientWebSocket]::new(); $anonymous.Options.SetRequestHeader('Origin',$Origin)
try {
    $rejected=$false
    try { $null=$anonymous.ConnectAsync([Uri]$endpoint,[Threading.CancellationToken]::None).GetAwaiter().GetResult() } catch { $rejected=$true }
    if(-not $rejected) { throw 'Unauthenticated WebSocket must be rejected' }
} finally { $anonymous.Abort(); $anonymous.Dispose() }
$ws=[Net.WebSockets.ClientWebSocket]::new(); $ws.Options.Cookies=$session.Cookies; $ws.Options.SetRequestHeader('Origin',$Origin)
try {
    $null=$ws.ConnectAsync([Uri]$endpoint,[Threading.CancellationToken]::None).GetAwaiter().GetResult()
    if((Receive-Event).type -ne 'session.ready') { throw 'Session not ready' }
    $request=[guid]::NewGuid().ToString(); Send-Event @{type='text.submit';requestId=$request;text='你好，请用一句话介绍你自己。'}
    $first=Complete-Turn $request
    Send-Event @{type='feedback';turnId=$first.turnId;category='audio';elapsedMs=1000}
    $saved=Receive-Event
    if($saved.type -ne 'feedback.saved') { throw 'Feedback was not acknowledged' }
    # Parse RIFF chunks instead of assuming a 44-byte header.
    $fixture=Join-Path $PSScriptRoot '../local-speech/tests/fixtures/jfk-16k.wav'
    $wav=[IO.File]::ReadAllBytes([IO.Path]::GetFullPath($fixture)); $offset=12; $pcm=$null
    while($offset+8 -le $wav.Length) {
        $kind=[Text.Encoding]::ASCII.GetString($wav,$offset,4); $size=[BitConverter]::ToInt32($wav,$offset+4)
        if($size -lt 0 -or $offset+8+$size -gt $wav.Length) { throw 'Invalid fixture' }
        if($kind -eq 'data') { $pcm=New-Object byte[] $size; [Array]::Copy($wav,$offset+8,$pcm,0,$size); break }
        $offset+=8+$size+($size%2)
    }
    if(-not $pcm) { throw 'Missing fixture PCM' }
    for($offset=0;$offset -lt $pcm.Length;$offset+=8192) {
        $count=[Math]::Min(8192,$pcm.Length-$offset)
        $null=$ws.SendAsync([ArraySegment[byte]]::new($pcm,$offset,$count),[Net.WebSockets.WebSocketMessageType]::Binary,$true,[Threading.CancellationToken]::None).GetAwaiter().GetResult()
    }
    $request=[guid]::NewGuid().ToString(); Send-Event @{type='audio.commit';requestId=$request}; $second=Complete-Turn $request
    foreach($phrase in @('fellow Americans','your country','do for you')) { if($second.asr -notlike "*$phrase*") { throw "Real ASR missed official phrase: $phrase" } }
    # Real cancellation then immediate resubmission exercises turn identity and lease release.
    Send-Event @{type='text.submit';requestId=[guid]::NewGuid().ToString();text='请解释哲学的意义。'}
    do { $event=Receive-Event; if($event.type -eq 'error') { throw $event.message } } while($event.type -ne 'reply.delta')
    Send-Event @{type='turn.cancel'}
    $request=[guid]::NewGuid().ToString(); Send-Event @{type='text.submit';requestId=$request;text='请只回答你好。'}
    $third=Complete-Turn $request
    if($LongContext) {
        for($round=1;$round -le 4;$round++) {
            $request=[guid]::NewGuid().ToString()
            Send-Event @{type='text.submit';requestId=$request;text=('这是上下文容量测试。'*180+'请只回答你好。')}
            $null=Complete-Turn $request
        }
        Write-Host 'PASS: four additional long Chinese turns complete without context overflow.'
    }
    Write-Host "PASS: authenticated WS, matching requestId, real text/PCM -> LLM -> WAV, official ASR phrases, feedback ack, cancel/resubmit."
    Write-Host "Evidence: userId=$($user.id) conversationId=$conversation appId=$appId; audio chunks=$($first.audioChunks),$($second.audioChunks),$($third.audioChunks)."
    Write-Host 'This does not verify microphone capture, acoustic playback, cloud credentials, or ASR accuracy on a dataset.'
} finally { $ws.Abort(); $ws.Dispose() }
