# install-appium.ps1 - Appium stays PROJECT-LOCAL (a devDependency pulled in by @sungen/driver-mobile),
# never `npm i -g appium`; the platform driver (uiautomator2) lives in ~\.appium and is installed
# through that local CLI. Mirrors what `sungen init --mobile` does in TypeScript, so this script also
# works on a fresh clone where a teammate ran init.
if (-not $script:SungenEnvLibLoaded) { . (Join-Path $PSScriptRoot 'lib.ps1') }

$AppiumCmd = $script:SungenEnvAppiumCmd

function Get-AppiumVersion { if (Test-Path $AppiumCmd) { ((& $AppiumCmd -v) 2>$null | Select-Object -First 1) } }
Invoke-Step -Title 'Appium (project-local)' -Check {
  if (Test-Path $AppiumCmd) { "appium $(Get-AppiumVersion) (project-local: node_modules\.bin\appium)" }
} -Install {
  # Only re-install when the project already declares the mobile driver - otherwise the fix is
  # `sungen capability add mobile`, a consent-gated sungen command, not this script's call.
  $pkg = Join-Path $script:ProjectDir 'package.json'
  if ((Test-Path $pkg) -and ((Get-Content $pkg -Raw) -match '"@sungen/driver-mobile"')) {
    Push-Location $script:ProjectDir; try { Invoke-Native npm @('install') } finally { Pop-Location }
  } else { return 'needs-user' }
} -Verify {
  if (Test-Path $AppiumCmd) { "appium $(Get-AppiumVersion)" }
} -Hint "Appium is project-local (never global). Add the mobile driver + runtime with:`n  sungen capability add mobile"

# --- platform driver -------------------------------------------------------------------------------
function Get-InstalledDrivers {
  $raw = (& $AppiumCmd driver list --installed --json) 2>$null | Out-String
  if (-not $raw.Trim()) { return $null }
  try { return ($raw | ConvertFrom-Json) } catch { return $null }
}
function Get-DriverDetail([string]$Name) {
  $j = Get-InstalledDrivers
  if ($j -and ($j.PSObject.Properties.Name -contains $Name)) { "$Name $($j.$Name.version) in ~\.appium" }
}

if (Test-Path $AppiumCmd) {
  if (-not $script:SungenEnvSkipAndroid) {
    $script:CurrentDriver = 'uiautomator2'
    Invoke-Step -Title "Appium driver: $($script:CurrentDriver)" -Check { Get-DriverDetail $script:CurrentDriver } `
      -Install { Invoke-Native $AppiumCmd @('driver', 'install', $script:CurrentDriver) } `
      -Verify { Get-DriverDetail $script:CurrentDriver } `
      -Hint "retry: npx appium driver install $($script:CurrentDriver)"
  }
} else {
  Write-Warn 'Appium platform driver (uiautomator2)' 'skipped - Appium is not installed in this project yet (see the step above)'
}
