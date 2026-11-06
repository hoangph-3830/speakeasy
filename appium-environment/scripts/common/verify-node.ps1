# verify-node.ps1 - Node.js >= 18 (sungen's own engine floor). Never installs Node: sungen is already
# running on it when this bundle is invoked from `sungen init`; a standalone run gets the guide.
if (-not $script:SungenEnvLibLoaded) { . (Join-Path $PSScriptRoot 'lib.ps1') }

$NodeMin = 18
Invoke-Check -Title "Node.js >= $NodeMin" -Check {
  if (-not (Get-Command node -ErrorAction SilentlyContinue)) { return $null }
  $v = (& node -v) 2>$null
  $major = 0; if ("$v" -match '(\d+)') { $major = [int]$Matches[1] }
  if ($major -lt $NodeMin) { return $null }
  $npm = (& npm -v) 2>$null
  "node $v, npm $npm"
} -Hint 'install Node LTS: https://nodejs.org  (or: winget install OpenJS.NodeJS.LTS), then open a new terminal'
