param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$Adb = 'adb'
)
$ErrorActionPreference = 'Stop'
$apk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Build :app:assembleDebug before updating.' }
$state = & $Adb -s $Serial get-state
if ($LASTEXITCODE -ne 0 -or ($state | Out-String).Trim() -ne 'device') {
    throw 'Selected device is unavailable or unauthorized.'
}
& $Adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw 'Update failed. Existing app/data are retained; no uninstall or data clearing attempted. Check signing certificate and versionCode.'
}
& $Adb -s $Serial shell am start -W -n vn.webnew.vcenter/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'Installed update, but app launch failed.' }
