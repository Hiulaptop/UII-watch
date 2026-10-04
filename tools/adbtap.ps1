# Helpers for driving the Wear OS emulator from PowerShell (development only).
#   . .\tools\adbtap.ps1 ; Tap "Ghép nối" ; Shot "name"
$script:adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$script:shots = $env:UII_SHOTS_DIR

function Dump {
    & $script:adb shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
    $tmp = Join-Path $env:TEMP "ui.xml"
    & $script:adb pull /sdcard/ui.xml $tmp 2>&1 | Out-Null
    [xml](Get-Content $tmp -Encoding UTF8)
}

# Taps the centre of the first element whose text equals $text (exact match).
function Tap([string]$text) {
    $xml = Dump
    $n = $xml.SelectNodes("//node") | Where-Object { $_.text -ceq $text -or $_.'content-desc' -ceq $text } | Select-Object -First 1
    if (-not $n) { Write-Output "no element with text '$text'"; return $false }
    if ($n.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { return $false }
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2); $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    & $script:adb shell input tap $x $y
    return $true
}

function Texts { (Dump).SelectNodes("//node") | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object { if ($_.text) { $_.text } else { "[" + $_.'content-desc' + "]" } } }

function Shot([string]$name) {
    & $script:adb shell screencap -p /sdcard/s.png
    & $script:adb pull /sdcard/s.png (Join-Path $script:shots "$name.png") 2>&1 | Out-Null
}
