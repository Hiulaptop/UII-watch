<#
  Measures how long a fall alert takes to reach the server after the cancel window, using the debug-only trigger
  (a debug build must be installed; a release build has no trigger). Nobody has to fall.

    $env:UII_REST_URL = "https://<project>.supabase.co/rest/v1"   # or the local PostgREST base
    $env:UII_REST_KEY = "<service role key>"                       # never commit this; read-only use here
    .\tools\fall-latency.ps1 -Runs 5 -CancelWindowMs 20000

  Per run it broadcasts the debug fall, waits for the alert row to appear, and prints
    delay_after_window = server created_at - (trigger time + cancel window)
  The spec asks for <= 10 s. The trigger time is read from this PC's clock, so keep the PC and the server clock in sync
  (a few seconds of skew would show up directly in the result).
  Cancelling (the wearer taps "Tôi ổn" on the watch notification) is checked by hand; see docs/validation.
#>
param(
    [int]$Runs = 5,
    [int]$CancelWindowMs = 20000,
    [string]$Serial = "",
    [string]$Package = "net.studev.uii"
)

$ErrorActionPreference = "Stop"
if (-not $env:UII_REST_URL -or -not $env:UII_REST_KEY) { throw "Set UII_REST_URL and UII_REST_KEY (see the header of this script)." }
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$adbArgs = @(); if ($Serial) { $adbArgs = @("-s", $Serial) }
function Adb { & $adb @adbArgs @args }

$hdr = @{ apikey = $env:UII_REST_KEY; Authorization = "Bearer $($env:UII_REST_KEY)" }
function Latest-AlertAfter([datetime]$since) {
    $iso = [uri]::EscapeDataString($since.ToUniversalTime().ToString("o"))
    $url = "$($env:UII_REST_URL)/alerts?select=id,detected_at,created_at,trigger,type&type=eq.fall&created_at=gt.$iso&order=created_at.asc&limit=1"
    $r = Invoke-RestMethod -Uri $url -Headers $hdr
    if ($r.Count -gt 0) { $r[0] } else { $null }
}

$results = @()
for ($i = 1; $i -le $Runs; $i++) {
    $t0 = [datetime]::UtcNow
    $out = (Adb shell am broadcast -n "$Package/com.example.watchtest.uii.DebugFallReceiver" -a net.studev.uii.DEBUG_FALL --ez force true) -join " "
    if ($out -notmatch "queued") { throw "The watch did not queue the fall: $out" }

    $deadline = $t0.AddMilliseconds($CancelWindowMs + 60000)
    $alert = $null
    while ([datetime]::UtcNow -lt $deadline) {
        $alert = Latest-AlertAfter $t0
        if ($alert) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $alert) { Write-Host "run $i : NO alert within window + 60 s" -ForegroundColor Red; $results += $null; continue }

    $created = ([datetimeoffset]$alert.created_at).UtcDateTime
    $afterWindow = ($created - $t0).TotalSeconds - ($CancelWindowMs / 1000.0)
    $results += $afterWindow
    Write-Host ("run {0}: alert stored {1:N1} s after trigger = {2:N1} s after the cancel window" -f $i, ($created - $t0).TotalSeconds, $afterWindow)
    Start-Sleep -Seconds 3
}

$ok = $results | Where-Object { $_ -ne $null }
if ($ok.Count) {
    $max = ($ok | Measure-Object -Maximum).Maximum
    $avg = ($ok | Measure-Object -Average).Average
    Write-Host ("{0}/{1} alerts arrived. After the cancel window: average {2:N1} s, worst {3:N1} s (limit 10 s) -> {4}" -f $ok.Count, $Runs, $avg, $max, $(if ($max -le 10 -and $ok.Count -eq $Runs) { "PASS" } else { "FAIL" }))
} else {
    Write-Host "No alerts arrived: FAIL"
}
