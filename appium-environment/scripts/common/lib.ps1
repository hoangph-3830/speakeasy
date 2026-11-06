# lib.ps1 - shared step runner for the sungen appium-environment bundle (Windows side).
# Dot-sourced by setup.ps1 and by every scripts\**\*.ps1; never run directly.
# Windows PowerShell 5.1 compatible: no ternary, no ??, ASCII only (5.1 reads BOM-less files as ANSI).
#
# Step model (see docs: Mobile -> Appium setup -> "One command"):
#   Check   -> returns a detail string when already satisfied, $null otherwise (side-effect free)
#   Install -> idempotent; output streams to the console; returns 'needs-user' when the user must finish it
#   Verify  -> returns a detail string when satisfied, $null otherwise; -Hint is shown on failure

if ($script:SungenEnvLibLoaded) { return }
$script:SungenEnvLibLoaded = $true
$ErrorActionPreference = 'Continue'

# Flags - set by setup.ps1's parameters, read by every step.
if ($null -eq $script:SungenEnvCheckOnly)   { $script:SungenEnvCheckOnly = $false }
if ($null -eq $script:SungenEnvSkipAndroid) { $script:SungenEnvSkipAndroid = $false }
if ($null -eq $script:SungenEnvNoProfile)   { $script:SungenEnvNoProfile = $false }
# Flipped to $true when the User environment actually changed.
$script:SungenEnvProfileChanged = $false

# Paths: <project>\appium-environment\scripts\common\lib.ps1 -> bundle = ..\.., project = bundle\..
$script:SungenEnvBundleDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $script:ProjectDir) { $script:ProjectDir = (Resolve-Path (Join-Path $script:SungenEnvBundleDir '..')).Path }
# Appium is project-local by design (never `npm i -g appium`) - see install-appium.ps1.
$script:SungenEnvAppiumCmd = Join-Path $script:ProjectDir 'node_modules\.bin\appium.cmd'

$script:CountOk = 0; $script:CountSkip = 0; $script:CountWarn = 0; $script:CountFail = 0

function Write-Section([string]$Title) { Write-Host ''; Write-Host $Title -ForegroundColor White }
function Write-Detail([string]$Text) {
  if (-not $Text) { return }
  foreach ($line in ($Text -split "`r?`n")) { if ($line) { Write-Host "            $line" -ForegroundColor DarkGray } }
}
function Write-Ok([string]$Title, [string]$Detail)   { $script:CountOk++;   Write-Host "[ok]      $Title" -ForegroundColor Green;    Write-Detail $Detail }
function Write-Skip([string]$Title, [string]$Detail) { $script:CountSkip++; Write-Host "[skip]    $Title" -ForegroundColor DarkGray; Write-Detail $Detail }
function Write-Warn([string]$Title, [string]$Detail) { $script:CountWarn++; Write-Host "[warn]    $Title" -ForegroundColor Yellow;   Write-Detail $Detail }
function Write-Fail([string]$Title, [string]$Detail) { $script:CountFail++; Write-Host "[fail]    $Title" -ForegroundColor Red;      Write-Detail $Detail }
function Write-Install([string]$Title) { Write-Host "[install] $Title" -ForegroundColor Cyan }

# Invoke-Native <exe> <args...> - run a native command, throw on a non-zero exit code.
function Invoke-Native([string]$File, [string[]]$Arguments) {
  & $File @Arguments
  if ($LASTEXITCODE -ne 0) { throw "$File exited with code $LASTEXITCODE" }
}

# Invoke-Step - an install-type step.
function Invoke-Step {
  param([string]$Title, [scriptblock]$Check, [scriptblock]$Install, [scriptblock]$Verify, [string]$Hint)
  $found = $null; try { $found = & $Check } catch { $found = $null }
  if ($found) { Write-Skip $Title "$found"; return }
  if ($script:SungenEnvCheckOnly) { Write-Fail $Title 'not installed - run without -Check to install'; return }
  Write-Install $Title
  $needsUser = $false; $err = $null
  try { $r = & $Install; if ("$r" -eq 'needs-user') { $needsUser = $true } } catch { $err = $_.Exception.Message }
  $ok = $null; try { $ok = & $Verify } catch { $ok = $null }
  if ($ok) { Write-Ok $Title "$ok"; return }
  $msg = $Hint; if ($err) { $msg = "$err`n$Hint" }
  if ($needsUser) { Write-Warn $Title $msg } else { Write-Fail $Title $msg }
}

# Invoke-Check - a verification-only step (ok / fail, or -Warn for informational checks).
function Invoke-Check {
  param([string]$Title, [scriptblock]$Check, [string]$Hint, [switch]$Warn)
  $out = $null; try { $out = & $Check } catch { $out = $null }
  if ($out) { Write-Ok $Title "$out"; return }
  if ($Warn) { Write-Warn $Title $Hint } else { Write-Fail $Title $Hint }
}

# Invoke-Guided - GUI installs this bundle never performs itself: skip when present, else warn with the next action.
function Invoke-Guided {
  param([string]$Title, [scriptblock]$Check, [string]$Guidance)
  $found = $null; try { $found = & $Check } catch { $found = $null }
  if ($found) { Write-Skip $Title "$found" } else { Write-Warn $Title $Guidance }
}

# --- user environment (the Windows counterpart of the shell-profile block) ------------------------
# Update-SessionPath - pick up PATH changes made by installers (winget) without a new console.
function Update-SessionPath {
  $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
}
# Set-UserEnv <name> <value> - user-scope variable (what `setx` does, without its 1024-char truncation). $true when changed.
function Set-UserEnv([string]$Name, [string]$Value) {
  if ([Environment]::GetEnvironmentVariable($Name, 'User') -eq $Value) { Set-Item -Path "env:$Name" -Value $Value; return $false }
  [Environment]::SetEnvironmentVariable($Name, $Value, 'User')
  Set-Item -Path "env:$Name" -Value $Value
  $script:SungenEnvProfileChanged = $true
  return $true
}
# Add-UserPath <dir> - append to the user-scope Path once; also to this session. $true when changed.
function Add-UserPath([string]$Dir) {
  $current = [Environment]::GetEnvironmentVariable('Path', 'User')
  $parts = @(); if ($current) { $parts = @($current -split ';' | Where-Object { $_ }) }
  if (-not (($env:Path -split ';') -contains $Dir)) { $env:Path = "$env:Path;$Dir" }
  if ($parts -contains $Dir) { return $false }
  [Environment]::SetEnvironmentVariable('Path', (($parts + $Dir) -join ';'), 'User')
  $script:SungenEnvProfileChanged = $true
  return $true
}

# Write-Summary - tallies + the "new terminal" notice. Returns 1 when anything failed, else 0.
function Write-Summary {
  Write-Host ''
  Write-Host "$($script:CountOk) ok, $($script:CountSkip) skipped, $($script:CountWarn) warned, $($script:CountFail) failed" -ForegroundColor White
  if ($script:SungenEnvProfileChanged) { Write-Host '-> Open a NEW terminal so Path / JAVA_HOME / ANDROID_HOME apply.' -ForegroundColor Yellow }
  if ($script:CountWarn -gt 0) { Write-Host '   warnings need a manual step - do it, then re-run this script.' }
  if ($script:CountFail -gt 0) { return 1 }
  return 0
}
