param(
  [int]$Start = 0,
  [int]$End = 1199,
  [string]$Out = "$PSScriptRoot\..\video\frames",
  [string]$Url = "http://127.0.0.1:49696/index.html",
  [int]$Step = 30
)
$ErrorActionPreference = "Stop"
$edge = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
$profile = Join-Path $PSScriptRoot "..\video\.cdpprof2"
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$ready = $false
try { $null = Invoke-RestMethod "http://127.0.0.1:9222/json/version" -TimeoutSec 2; $ready = $true } catch {}
if (-not $ready) {
  Start-Process -FilePath $edge -ArgumentList @(
    "--headless=new","--disable-gpu","--hide-scrollbars","--no-sandbox","--no-first-run",
    "--no-default-browser-check","--disable-sync","--disable-extensions",
    "--remote-debugging-port=9222","--user-data-dir=$profile",
    "--force-device-scale-factor=1","--window-size=1080,1620","about:blank"
  ) | Out-Null
  for ($k = 0; $k -lt 40; $k++) {
    Start-Sleep -Milliseconds 300
    try { $null = Invoke-RestMethod "http://127.0.0.1:9222/json/version" -TimeoutSec 2; $ready = $true; break } catch {}
  }
}
if (-not $ready) { Write-Output "EDGE-NOT-READY"; exit 1 }

$targets = Invoke-RestMethod "http://127.0.0.1:9222/json"
$page = $targets | Where-Object { $_.type -eq "page" } | Select-Object -First 1
if (-not $page) { $page = Invoke-RestMethod "http://127.0.0.1:9222/json/new?about:blank" -Method Put }
$wsUrl = $page.webSocketDebuggerUrl
if (-not $wsUrl) { Write-Output "NO-WS-URL"; exit 1 }

$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ws.ConnectAsync([Uri]$wsUrl, [Threading.CancellationToken]::None).Wait(10000) | Out-Null
if ($ws.State -ne "Open") { Write-Output "WS-CONNECT-FAIL"; exit 1 }

function Send-Json([string]$json) {
  $bytes = [Text.Encoding]::UTF8.GetBytes($json)
  $seg = New-Object "System.ArraySegment[byte]" -ArgumentList @(,$bytes)
  $ws.SendAsync($seg, [Net.WebSockets.WebSocketMessageType]::Text, $true, [Threading.CancellationToken]::None).Wait()
}

function Receive-Json([int]$timeoutMs = 8000) {
  $buf = New-Object byte[] 8388608
  $seg = New-Object "System.ArraySegment[byte]" -ArgumentList @(,$buf)
  $sb = New-Object Text.StringBuilder
  $cts = New-Object Threading.CancellationTokenSource
  $cts.CancelAfter($timeoutMs)
  try {
    do {
      $r = $ws.ReceiveAsync($seg, $cts.Token).Result
      if ($r.MessageType -eq [Net.WebSockets.WebSocketMessageType]::Close) { return $null }
      [void]$sb.Append([Text.Encoding]::UTF8.GetString($buf, 0, $r.Count))
    } while (-not $r.EndOfMessage)
  } catch { return $null }
  return $sb.ToString()
}

function Call-Method([int]$id, [string]$method, [string]$params) {
  Send-Json ('{"id":' + $id + ',"method":"' + $method + '","params":' + $params + '}')
  for ($t = 0; $t -lt 80; $t++) {
    $msg = Receive-Json 5000
    if ($null -eq $msg) { continue }
    if (($msg -match '"id":\s*' + $id + '\b') -and ($msg -match '"result"')) { return $msg }
  }
  return $null
}

[void](Call-Method 1 "Page.enable" "{}")
# Pin the viewport so every screenshot is exactly 1080x1620 regardless of window chrome.
[void](Call-Method 3 "Emulation.setDeviceMetricsOverride" '{"width":1080,"height":1620,"deviceScaleFactor":1,"mobile":false}')
[void](Call-Method 2 "Page.navigate" ('{"url":"' + $Url + '"}'))
Start-Sleep -Milliseconds 1200

$made = 0
$id = 100
for ($i = $Start; $i -le $End; $i++) {
  $name = "f-{0:d4}.png" -f $i
  $path = Join-Path $Out $name
  if ((Test-Path $path) -and ((Get-Item $path).Length -gt 2000)) { continue }
  $ms = [int][math]::Round($i * 1000.0 / $Step)
  $id++
  [void](Call-Method $id "Runtime.evaluate" ('{"expression":"window.freezeAt(' + $ms + ')","returnByValue":true}'))
  Start-Sleep -Milliseconds 90
  $id++
  $resp = Call-Method $id "Page.captureScreenshot" '{"format":"png","fromSurface":true}'
  if (-not $resp) { Write-Output ("FAIL " + $i); continue }
  $m = [regex]::Match($resp, '"data":"([^"]+)"')
  if (-not $m.Success) { Write-Output ("NODATA " + $i); continue }
  [IO.File]::WriteAllBytes($path, [Convert]::FromBase64String($m.Groups[1].Value))
  $made++
}
$ws.CloseAsync([Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "done", [Threading.CancellationToken]::None).Wait(3000) | Out-Null
$count = (Get-ChildItem $Out -Filter "f-*.png" | Measure-Object).Count
Write-Output ("made=" + $made + " total=" + $count)
