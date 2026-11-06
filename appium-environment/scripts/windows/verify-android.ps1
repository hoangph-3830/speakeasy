# verify-android.ps1 - Windows: is the Android side usable right now? (adb, env vars, connected devices)
if (-not $script:SungenEnvLibLoaded) { . (Join-Path $PSScriptRoot '..\common\lib.ps1') }

# Prefer the adb under ANDROID_HOME (this run may have just installed it) over a stale one on PATH.
function Get-AndroidAdb {
  if ($env:ANDROID_HOME) { $p = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'; if (Test-Path $p) { return $p } }
  $cmd = Get-Command adb -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  return $null
}

Invoke-Check -Title 'adb' -Check {
  $adb = Get-AndroidAdb; if (-not $adb) { return $null }
  (& $adb version) 2>$null | Select-Object -First 1
} -Hint 'adb not found - re-run without -SkipAndroid, or open a NEW terminal if the environment was just written'

Invoke-Check -Title 'ANDROID_HOME / JAVA_HOME' -Check {
  if ($env:ANDROID_HOME -and $env:JAVA_HOME) { "ANDROID_HOME=$env:ANDROID_HOME, JAVA_HOME=$env:JAVA_HOME" }
} -Hint 'not set in this console - open a NEW terminal (User environment) or set them'

Invoke-Check -Title 'Android devices' -Check {
  $adb = Get-AndroidAdb; if (-not $adb) { return $null }
  $lines = @((& $adb devices) 2>$null | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' })
  if ($lines.Count -gt 0) { "$($lines.Count) device(s) connected" }
  else { 'no device connected - plug in a phone (USB debugging on) or start an emulator before /sungen:run-test' }
} -Hint 'adb is not usable yet' -Warn
