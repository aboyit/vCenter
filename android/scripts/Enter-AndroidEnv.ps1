$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$jdk = Get-ChildItem (Join-Path $root '.tools\jdk') -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
    Select-Object -First 1
if (-not $jdk) { throw 'Install JDK 17 in .tools\jdk first.' }
$sdk = Join-Path $root '.tools\android-sdk'
if (-not (Test-Path (Join-Path $sdk 'platform-tools\adb.exe'))) {
    throw 'Install Android SDK platform-tools in .tools\android-sdk first.'
}
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:GRADLE_USER_HOME = Join-Path $root '.gradle-user-home'
$env:PATH = "$env:JAVA_HOME\bin;$sdk\platform-tools;$env:PATH"
