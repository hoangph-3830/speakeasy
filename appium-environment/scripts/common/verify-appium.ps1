# verify-appium.ps1 - the final gate: `appium driver doctor uiautomator2` checks the whole chain the
# driver needs at run time (JAVA_HOME, ANDROID_HOME, adb). Doctor problems are reported as warnings
# with the doctor's own output - it names the missing piece.
if (-not $script:SungenEnvLibLoaded) { . (Join-Path $PSScriptRoot 'lib.ps1') }

$AppiumCmd = $script:SungenEnvAppiumCmd
# Doctor prints a required failure as a heavy-ballot-x line; the character is referenced by code point
# so this file stays ASCII (Windows PowerShell 5.1 reads BOM-less scripts as ANSI).
$RequiredFailMark = [string][char]0x2716

Invoke-Check -Title 'Appium CLI' -Check {
  if (Test-Path $AppiumCmd) { "appium $((& $AppiumCmd -v) 2>$null | Select-Object -First 1)" }
} -Hint 'not installed in this project - sungen capability add mobile'

if ((Test-Path $AppiumCmd) -and -not $script:SungenEnvSkipAndroid) {
  $installed = (& $AppiumCmd driver list --installed --json) 2>$null | Out-String
  if ($installed -match '"uiautomator2"') {
    # Inline (not Invoke-Check) so the failure detail prints AFTER the step title, like every other step.
    $title = 'appium driver doctor uiautomator2'
    $hint = 'fix the required items above (open a NEW terminal first if the environment was just written), then re-run'
    $out = (& $AppiumCmd driver doctor uiautomator2 2>&1) | Out-String
    $rc = $LASTEXITCODE
    # Doctor marks BOTH required and optional problems with the same mark, so read its summary line:
    #   "### Diagnostic completed, N required fixes needed, M optional fixes possible. ###"
    $req = $null; if ($out -match 'Diagnostic completed, (\d+) required fix') { $req = [int]$Matches[1] }
    $opt = 0;     if ($out -match ', (\d+) optional fix') { $opt = [int]$Matches[1] }
    if ($null -eq $req) {
      # No summary line (older driver) - fall back to the exit code.
      if ($rc -eq 0) { Write-Ok $title 'doctor exited 0' } else { Write-Warn $title "$out`n$hint" }
    } elseif ($req -gt 0) {
      $marks = @(($out -split "`r?`n") | Where-Object { $_.Contains($RequiredFailMark) } | ForEach-Object { $_ -replace '^.*Doctor\s+', '' })
      Write-Warn $title ("$req required fix(es) needed:`n" + ($marks -join "`n") + "`nfull report: npx appium driver doctor uiautomator2`n$hint")
    } else {
      Write-Ok $title "0 required fixes, $opt optional (extras like bundletool / gstreamer - see: npx appium driver doctor uiautomator2)"
    }
  } else {
    Write-Warn 'appium driver doctor uiautomator2' 'driver not installed - see the Appium step above'
  }
}
