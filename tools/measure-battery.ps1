<#
  Records the watch battery over time so the cost of monitoring can be compared between configurations.

    .\tools\measure-battery.ps1 -Label current-50hz -Minutes 360
    .\tools\measure-battery.ps1 -Label baseline -Minutes 360 -Serial 192.168.1.50:5555

  Writes docs/validation/battery-<label>-<timestamp>.csv (one row per sample) and
  docs/validation/battery-<label>-<timestamp>-stats.txt (the system's own per-app estimate, from batterystats).
  Run with the watch unplugged: a charging watch gives meaningless numbers. The script says so and stops if it is.
#>
param(
    [Parameter(Mandatory = $true)][string]$Label,
    [int]$Minutes = 360,
    [int]$IntervalSeconds = 300,
    [string]$Serial = "",
    [string]$Package = "net.studev.uii"
)

$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$adbArgs = @(); if ($Serial) { $adbArgs = @("-s", $Serial) }
function Adb { & $adb @adbArgs @args }

function Read-Battery {
    $t = (Adb shell dumpsys battery) -join "`n"
    $get = { param($k) if ($t -match "(?m)^\s*${k}:\s*(\S+)") { $Matches[1] } else { "" } }
    [pscustomobject]@{
        level    = & $get "level"
        charging = if ((& $get "AC powered") -eq "true" -or (& $get "USB powered") -eq "true" -or (& $get "Wireless powered") -eq "true") { "true" } else { "false" }
        status   = & $get "status"
        voltage  = & $get "voltage"
        temp     = & $get "temperature"
    }
}

$first = Read-Battery
if ($first.charging -eq "true") { throw "The watch is charging. Unplug it and rerun, or the numbers mean nothing." }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$dir = Join-Path $PSScriptRoot "..\docs\validation"
New-Item -ItemType Directory -Force $dir | Out-Null
$csv = Join-Path $dir "battery-$Label-$stamp.csv"
$stats = Join-Path $dir "battery-$Label-$stamp-stats.txt"

Adb shell dumpsys batterystats --reset | Out-Null
"time,elapsed_min,level_pct,charging,voltage_mv,temp_tenths_c" | Set-Content $csv
$start = Get-Date
$end = $start.AddMinutes($Minutes)
Write-Host "Recording '$Label' for $Minutes min every $IntervalSeconds s -> $csv"

while ($true) {
    $now = Get-Date
    $b = Read-Battery
    $elapsed = [math]::Round(($now - $start).TotalMinutes, 1)
    "$($now.ToString('s')),$elapsed,$($b.level),$($b.charging),$($b.voltage),$($b.temp)" | Add-Content $csv
    Write-Host ("{0,6} min  battery {1}%{2}" -f $elapsed, $b.level, $(if ($b.charging -eq "true") { "  (CHARGING - discard this run)" } else { "" }))
    if ($now -ge $end) { break }
    Start-Sleep -Seconds $IntervalSeconds
}

Adb shell dumpsys batterystats $Package | Set-Content $stats

$rows = Import-Csv $csv
$drop = [double]$rows[0].level_pct - [double]$rows[-1].level_pct
$hours = [double]$rows[-1].elapsed_min / 60
if ($hours -gt 0 -and $drop -gt 0) {
    $perHour = $drop / $hours
    Write-Host ("Drained {0}% in {1:N1} h = {2:N2} %/h; projected full-charge runtime about {3:N1} h" -f $drop, $hours, $perHour, (100 / $perHour))
} else {
    Write-Host "No measurable drain in this window; run longer."
}
Write-Host "Saved $csv and $stats"
