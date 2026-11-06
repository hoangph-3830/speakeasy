# install-android.ps1 - Windows: JDK >= 17, Android SDK root, platform-tools + emulator, User environment.
# Headless via winget (decision D2): Microsoft OpenJDK 21 + Google's command-line tools zip + sdkmanager.
# Android Studio is optional (only for an on-screen emulator) and is never installed here.
# Dot-sourced by setup.ps1 (shares the runner + counters); runnable standalone for debugging.
if (-not $script:SungenEnvLibLoaded) { . (Join-Path $PSScriptRoot '..\common\lib.ps1') }

$JdkMin = 17
# Google rotates the command-line tools zip name; bump this ONE value when the download 404s.
# Current builds: https://developer.android.com/studio#command-line-tools-only
$CmdlineToolsBuild = '13114758'
$DefaultSdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'

# --- JDK ------------------------------------------------------------------------------------------
# Any working JDK >= 17 is accepted (Microsoft, Temurin, Oracle, Corretto...) - we never replace a good one.
function Get-JavaMajor([string]$JavaExe) {
  try { $out = (& $JavaExe -version 2>&1) | Out-String; if ($out -match '"(\d+)(\.\d+)*') { return [int]$Matches[1] } } catch {}
  return 0
}
function Get-JavaHomeFromExe([string]$JavaExe) {
  $out = (& $JavaExe -XshowSettings:properties -version 2>&1) | Out-String
  if ($out -match 'java\.home = (.+)') { return $Matches[1].Trim() }
  return $null
}
function Test-JdkHome([string]$JdkHome) {
  if (-not $JdkHome) { return $false }
  $exe = Join-Path $JdkHome 'bin\java.exe'
  return ((Test-Path $exe) -and ((Get-JavaMajor $exe) -ge $JdkMin))
}
# Resolve-JavaHome - sets $env:JAVA_HOME when a usable JDK is found (env -> PATH -> vendor default folders).
function Resolve-JavaHome {
  if (Test-JdkHome $env:JAVA_HOME) { return $true }
  $java = Get-Command java -ErrorAction SilentlyContinue
  if ($java -and ((Get-JavaMajor $java.Source) -ge $JdkMin)) {
    $home = Get-JavaHomeFromExe $java.Source
    if ($home -and (Test-Path $home)) { $env:JAVA_HOME = $home; return $true }
  }
  foreach ($root in @((Join-Path $env:ProgramFiles 'Microsoft'), (Join-Path $env:ProgramFiles 'Eclipse Adoptium'), (Join-Path $env:ProgramFiles 'Java'))) {
    if (-not (Test-Path $root)) { continue }
    $cand = Get-ChildItem $root -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending |
      Where-Object { Test-JdkHome $_.FullName } | Select-Object -First 1
    if ($cand) { $env:JAVA_HOME = $cand.FullName; return $true }
  }
  return $false
}
function Get-JdkDetail { if (Resolve-JavaHome) { "java $(Get-JavaMajor (Join-Path $env:JAVA_HOME 'bin\java.exe')), JAVA_HOME=$env:JAVA_HOME" } }

Invoke-Step -Title "JDK >= $JdkMin" -Check { Get-JdkDetail } -Install {
  Invoke-Native winget @('install', '--id', 'Microsoft.OpenJDK.21', '-e', '--silent', '--accept-source-agreements', '--accept-package-agreements')
  Update-SessionPath
} -Verify { Get-JdkDetail } -Hint 'winget install --id Microsoft.OpenJDK.21 -e   (then open a NEW terminal and re-run)'

# --- Android SDK root -----------------------------------------------------------------------------
function Find-SdkManager([string]$Root) {
  if (-not $Root) { return $null }
  foreach ($c in @((Join-Path $Root 'cmdline-tools\latest\bin\sdkmanager.bat'), (Join-Path $Root 'tools\bin\sdkmanager.bat'))) { if (Test-Path $c) { return $c } }
  $any = Get-ChildItem (Join-Path $Root 'cmdline-tools') -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Join-Path $_.FullName 'bin\sdkmanager.bat' } | Where-Object { Test-Path $_ } | Select-Object -First 1
  return $any
}
function Test-SdkRoot([string]$Root) {
  if (-not $Root -or -not (Test-Path $Root)) { return $false }
  return ((Test-Path (Join-Path $Root 'platform-tools\adb.exe')) -or [bool](Find-SdkManager $Root))
}
# Precedence: what the user already set -> Android Studio's default (%LOCALAPPDATA%\Android\Sdk).
function Resolve-AndroidHome {
  foreach ($c in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, $DefaultSdkRoot)) {
    if (Test-SdkRoot $c) { $env:ANDROID_HOME = $c; $env:ANDROID_SDK_ROOT = $c; return $true }
  }
  return $false
}
function Install-CmdlineTools {
  # Cmdlet errors (download 404 when Google rotates the zip, unpack failures) must become terminating so
  # Invoke-Step's try/catch reports the real cause instead of only the generic hint.
  $ErrorActionPreference = 'Stop'
  $root = $DefaultSdkRoot
  $url = "https://dl.google.com/android/repository/commandlinetools-win-${CmdlineToolsBuild}_latest.zip"
  $zip = Join-Path $env:TEMP "commandlinetools-win-$CmdlineToolsBuild.zip"
  $staging = Join-Path $env:TEMP "sungen-cmdline-tools-$CmdlineToolsBuild"
  New-Item -ItemType Directory -Force -Path (Join-Path $root 'cmdline-tools') | Out-Null
  Write-Host "            downloading $url" -ForegroundColor DarkGray
  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
  Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
  if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
  Expand-Archive -Path $zip -DestinationPath $staging -Force
  # The zip unpacks as cmdline-tools\ but sdkmanager only works from cmdline-tools\latest\.
  $latest = Join-Path $root 'cmdline-tools\latest'
  if (Test-Path $latest) { Remove-Item $latest -Recurse -Force }
  Move-Item (Join-Path $staging 'cmdline-tools') $latest
  Remove-Item $zip -Force -ErrorAction SilentlyContinue
  Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue
  $env:ANDROID_HOME = $root; $env:ANDROID_SDK_ROOT = $root
}
[void](Resolve-AndroidHome)
Invoke-Step -Title 'Android SDK root (cmdline-tools)' -Check {
  if (Test-SdkRoot $env:ANDROID_HOME) { "ANDROID_HOME=$env:ANDROID_HOME" }
} -Install { Install-CmdlineTools } -Verify {
  if (Find-SdkManager $env:ANDROID_HOME) { "ANDROID_HOME=$env:ANDROID_HOME" }
} -Hint "download the command-line tools zip from https://developer.android.com/studio#command-line-tools-only and unpack it to $DefaultSdkRoot\cmdline-tools\latest"

# --- platform-tools (adb) + emulator --------------------------------------------------------------
function Get-SdkPackagesDetail {
  if (-not $env:ANDROID_HOME) { return $null }
  $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'; $emu = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
  if ((Test-Path $adb) -and (Test-Path $emu)) { $v = (& $adb version) 2>$null | Select-Object -First 1; "platform-tools + emulator, $v" }
}
Invoke-Step -Title 'Android platform-tools (adb) + emulator' -Check { Get-SdkPackagesDetail } -Install {
  $sm = Find-SdkManager $env:ANDROID_HOME
  if (-not $sm) { throw 'no sdkmanager - the "Android SDK root" step must succeed first' }
  # sdkmanager needs JAVA_HOME (set above). Licenses are Google's SDK terms - the same prompt Android
  # Studio shows - accepted non-interactively here.
  $yes = ('y' + "`n") * 40
  $yes | & $sm "--sdk_root=$env:ANDROID_HOME" --licenses 2>&1 | Out-Null
  Invoke-Native $sm @("--sdk_root=$env:ANDROID_HOME", 'platform-tools', 'emulator')
} -Verify { Get-SdkPackagesDetail } -Hint "sdkmanager --sdk_root=$env:ANDROID_HOME platform-tools emulator"

# Make the tools reachable for the rest of THIS run (the User environment covers new consoles).
if ($env:ANDROID_HOME) {
  foreach ($d in @('platform-tools', 'emulator', 'cmdline-tools\latest\bin')) {
    $p = Join-Path $env:ANDROID_HOME $d
    if (-not (($env:Path -split ';') -contains $p)) { $env:Path = "$env:Path;$p" }
  }
}

# --- User environment (decision D3) ---------------------------------------------------------------
$EnvTitle = 'User environment (ANDROID_HOME, ANDROID_SDK_ROOT, JAVA_HOME, Path)'
$PathDirs = @('platform-tools', 'emulator', 'cmdline-tools\latest\bin')
if (-not $env:ANDROID_HOME -or -not $env:JAVA_HOME) {
  Write-Warn $EnvTitle 'skipped - fix the JDK / SDK steps above first, then re-run'
} elseif ($script:SungenEnvNoProfile) {
  Write-Skip $EnvTitle '-NoProfile'
} elseif ($script:SungenEnvCheckOnly) {
  $missing = @()
  foreach ($pair in @(@('ANDROID_HOME', $env:ANDROID_HOME), @('ANDROID_SDK_ROOT', $env:ANDROID_HOME), @('JAVA_HOME', $env:JAVA_HOME))) {
    if ([Environment]::GetEnvironmentVariable($pair[0], 'User') -ne $pair[1]) { $missing += $pair[0] }
  }
  $userPath = @(([Environment]::GetEnvironmentVariable('Path', 'User')) -split ';')
  foreach ($d in $PathDirs) { if (-not ($userPath -contains (Join-Path $env:ANDROID_HOME $d))) { $missing += "Path+=$d" } }
  if ($missing.Count -gt 0) { Write-Fail $EnvTitle "not set for your user: $($missing -join ', ') - run without -Check to write them" }
  else { Write-Skip $EnvTitle 'already set for your user' }
} else {
  $changed = @()
  if (Set-UserEnv 'ANDROID_HOME' $env:ANDROID_HOME)     { $changed += 'ANDROID_HOME' }
  if (Set-UserEnv 'ANDROID_SDK_ROOT' $env:ANDROID_HOME) { $changed += 'ANDROID_SDK_ROOT' }
  if (Set-UserEnv 'JAVA_HOME' $env:JAVA_HOME)           { $changed += 'JAVA_HOME' }
  foreach ($d in $PathDirs) { if (Add-UserPath (Join-Path $env:ANDROID_HOME $d)) { $changed += "Path+=$d" } }
  if ($changed.Count -gt 0) { Write-Ok $EnvTitle "set for your user: $($changed -join ', ') - open a NEW terminal to apply" }
  else { Write-Skip $EnvTitle 'already set for your user' }
}

# --- Android Studio - optional, GUI, never installed here ------------------------------------------
$studio = @((Join-Path $env:LOCALAPPDATA 'Programs\Android Studio'), (Join-Path $env:ProgramFiles 'Android\Android Studio')) | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($studio) { Write-Skip 'Android Studio (optional)' $studio }
else { Write-Skip 'Android Studio (optional)' 'not installed - only needed for the AVD manager / an on-screen emulator: https://developer.android.com/studio' }
