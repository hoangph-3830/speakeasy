<#
.SYNOPSIS
  setup.ps1 - Windows entry point of the sungen Appium environment bundle.
.DESCRIPTION
  .\appium-environment\setup.ps1 [-Check] [-SkipAndroid] [-NoProfile]

  Emitted by `sungen init --mobile` / `sungen capability add mobile`, which also run it once.
  Re-run it any time: every step is check -> install -> verify and a satisfied step is skipped.
    -Check        report what is missing, install nothing (side-effect free)
    -SkipAndroid  skip JDK / Android SDK / uiautomator2
    -NoProfile    do not write ANDROID_HOME / JAVA_HOME / Path to your User environment
  iOS (xcuitest) needs Xcode and therefore a Mac - there is no iOS step on Windows.
  Exit: 0 ready (or only warnings) - 1 something failed - 2 unsupported host
  Run as:  powershell -NoProfile -ExecutionPolicy Bypass -File .\appium-environment\setup.ps1
#>
[CmdletBinding()]
param([switch]$Check, [switch]$SkipAndroid, [switch]$NoProfile)

$ErrorActionPreference = 'Continue'
if ($PSVersionTable.PSVersion.Major -lt 5) { Write-Host 'Windows PowerShell 5.1 or newer is required.' -ForegroundColor Red; exit 2 }
if ($env:OS -ne 'Windows_NT') { Write-Host 'setup.ps1 is for Windows - on macOS run ./appium-environment/setup.sh' -ForegroundColor Red; exit 2 }

$script:SungenEnvCheckOnly   = [bool]$Check
$script:SungenEnvSkipAndroid = [bool]$SkipAndroid
$script:SungenEnvNoProfile   = [bool]$NoProfile
$script:ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'scripts\common\lib.ps1')

Set-Location $script:ProjectDir
if ($script:SungenEnvCheckOnly) { Write-Section 'sungen - Appium environment - check only (Windows)' }
else { Write-Section 'sungen - Appium environment setup (Windows)' }
Write-Host "project: $($script:ProjectDir)" -ForegroundColor DarkGray

# --- 1/4 prerequisites -----------------------------------------------------------------------------
Write-Section '1/4  Prerequisites'
. (Join-Path $PSScriptRoot 'scripts\common\verify-node.ps1')
Invoke-Check -Title 'winget (Windows Package Manager)' -Check {
  if (Get-Command winget -ErrorAction SilentlyContinue) { $v = (& winget --version) 2>$null | Select-Object -First 1; "winget $v" }
} -Hint 'winget missing - install "App Installer" from the Microsoft Store (Windows 10 1809+ / 11), then re-run'

# --- 2/4 Android -----------------------------------------------------------------------------------
if (-not $script:SungenEnvSkipAndroid) {
  Write-Section '2/4  Android (JDK, SDK, adb, emulator)'
  . (Join-Path $PSScriptRoot 'scripts\windows\install-android.ps1')
} else {
  Write-Section '2/4  Android'; Write-Skip 'Android toolchain' '-SkipAndroid'
}

# --- 3/4 Appium ------------------------------------------------------------------------------------
Write-Section '3/4  Appium (project-local) + platform driver'
. (Join-Path $PSScriptRoot 'scripts\common\install-appium.ps1')

# --- 4/4 health ------------------------------------------------------------------------------------
Write-Section '4/4  Health check'
if (-not $script:SungenEnvSkipAndroid) { . (Join-Path $PSScriptRoot 'scripts\windows\verify-android.ps1') }
. (Join-Path $PSScriptRoot 'scripts\common\verify-appium.ps1')

exit (Write-Summary)
