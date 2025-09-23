$ErrorActionPreference = 'Stop'
function Write-Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "[ERROR] $msg" -ForegroundColor Red }

# Resolve repo root as two levels up from this script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
$frontendDir = Join-Path $repoRoot 'Chambuavismart\Chambuavismart\frontend'

if (-not (Test-Path $frontendDir)) {
  Write-Err "Frontend directory not found at: $frontendDir"
  exit 1
}

if (-not (Test-Path (Join-Path $frontendDir 'package.json'))) {
  Write-Err "package.json not found in frontend directory. Ensure the frontend root is correct."
  exit 2
}

# Detect Node and npm
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) {
  Write-Warn "Node.js not found in PATH. Install Node 18.13+ or 20.9+ from https://nodejs.org/en/download or https://github.com/coreybutler/nvm-windows"
  exit 3
}

$npm = Get-Command npm -ErrorAction SilentlyContinue
if (-not $npm) {
  Write-Warn "npm not found in PATH. It typically comes with Node.js."
  exit 4
}

Push-Location $frontendDir
try {
  # Use ci if lockfile exists for reproducibility
  if (Test-Path (Join-Path $frontendDir 'package-lock.json')) {
    Write-Info 'Installing dependencies with npm ci...'
    & $npm ci
  } else {
    Write-Info 'Installing dependencies with npm install...'
    & $npm install
  }

  Write-Info 'Starting Angular dev server...'
  & $npm run start
}
finally {
  Pop-Location
}
