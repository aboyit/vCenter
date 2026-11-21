param(
    [ValidateSet('Build', 'Connect', 'Pair', 'Run', 'Logcat', 'Devices')]
    [string]$Action = 'Build',
    [string]$Serial = $env:ANDROID_SERIAL
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Enter-AndroidEnv.ps1"
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
if ($Action -eq 'Build') {
    Push-Location (Split-Path $PSScriptRoot -Parent)
    try {
        & .\gradlew.bat :app:assembleDebug --no-daemon --console plain
        if ($LASTEXITCODE -ne 0) { throw 'Debug build failed; existing device app is unchanged.' }
    } finally { Pop-Location }
    return
}
if ($Action -eq 'Devices') {
    & $adb devices -l
    if ($LASTEXITCODE -ne 0) { throw 'Unable to list ADB devices.' }
    return
}
if ([string]::IsNullOrWhiteSpace($Serial)) { throw 'Specify -Serial (device serial or IP:port).' }
if ($Action -in @('Connect', 'Pair')) {
    $endpoint = $null
    if ($Serial -notmatch '^([^\s:]+):(\d{1,5})$') { throw 'Expected IP:port, with a TCP port from 1 to 65535.' }
    $endpoint = [int]$Matches[2]
    if ($endpoint -lt 1 -or $endpoint -gt 65535) { throw 'TCP port must be from 1 to 65535.' }
    if ($Action -eq 'Pair') {
        & $adb pair $Serial
        if ($LASTEXITCODE -ne 0) { throw 'Pairing failed. Check pairing port and current code.' }
        return
    }
    & $adb connect $Serial
    if ($LASTEXITCODE -ne 0) { throw 'ADB connection failed.' }
}
$state = & $adb -s $Serial get-state
if ($LASTEXITCODE -ne 0 -or ($state | Out-String).Trim() -ne 'device') {
    throw 'Selected device is offline or unauthorized. Pair it and check the connection port.'
}
if ($Action -eq 'Run') {
    & "$PSScriptRoot\Android-Dev.ps1" -Action Build
    & "$PSScriptRoot\Update-App.ps1" -Serial $Serial -Adb $adb
} elseif ($Action -eq 'Logcat') {
    $processIds = & $adb -s $Serial shell pidof -s vn.webnew.vcenter
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($processIds | Out-String))) {
        throw 'App is not running. Run the app before opening Logcat.'
    }
    $appProcessId = ($processIds | Out-String).Trim()
    if ($appProcessId -notmatch '^\d+$') { throw 'Unexpected app process ID.' }
    Write-Host 'App-only Logcat. Stop with Ctrl+C; restart this task after the app process restarts.'
    & $adb -s $Serial logcat "--pid=$appProcessId" -v threadtime
    if ($LASTEXITCODE -ne 0) { throw 'Logcat disconnected.' }
}
