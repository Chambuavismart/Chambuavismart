param(
  [string]$BaseHref = "https://chambuavismart.github.io/Chambuavismart/",
  [switch]$Force
)

$ErrorActionPreference = 'Stop'

function Write-Step($msg) { Write-Host "[DEPLOY] $msg" -ForegroundColor Cyan }
function Write-Warn($msg) { Write-Host "[WARN]  $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "[ERROR] $msg" -ForegroundColor Red }

# Resolve important paths
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $repoRoot  # scripts -> project root
$frontendDir = Join-Path $repoRoot "Chambuavismart\frontend"
$distDir = Join-Path $frontendDir "dist\app"

if (!(Test-Path $frontendDir)) {
  Write-Err "Frontend directory not found: $frontendDir"
  exit 1
}

# Ensure git is available
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
  Write-Err "git is not installed or not in PATH. Install Git and retry."
  exit 1
}

# Ensure Angular CLI is available
if (-not (Get-Command ng -ErrorAction SilentlyContinue)) {
  Write-Warn "Angular CLI (ng) not found in PATH. Attempting to use npx..."
}

Push-Location $frontendDir
try {
  Write-Step "Building Angular app (production) with base-href '$BaseHref'"
  if (Get-Command ng -ErrorAction SilentlyContinue) {
    ng build --configuration production --base-href "$BaseHref"
  } else {
    npx -y @angular/cli build --configuration production --base-href "$BaseHref"
  }

  if (!(Test-Path $distDir)) {
    Write-Err "Build output not found: $distDir"
    exit 1
  }

  # Confirm branch change
  Push-Location $repoRoot
  try {
    $currentBranch = (git rev-parse --abbrev-ref HEAD).Trim()
    Write-Step "Current branch: $currentBranch"

    # Safety pre-checks before switching branches
    $hasUncommitted = $false
    $statusOutput = git status --porcelain
    if ($statusOutput) { $hasUncommitted = $true }

    $hasUpstream = $false
    $upstream = $null
    try {
      $upstream = (git rev-parse --abbrev-ref --symbolic-full-name @{u} 2>$null).Trim()
      if ($upstream) { $hasUpstream = $true }
    } catch { }

    $aheadCount = 0
    if ($hasUpstream) {
      $aheadBehind = (git rev-list --left-right --count "$currentBranch...$upstream" 2>$null)
      if ($aheadBehind) {
        $parts = $aheadBehind -split '\s+'
        if ($parts.Length -ge 1) { [int]::TryParse($parts[0], [ref]$aheadCount) | Out-Null }
      }
    }

    if (-not $Force) {
      if ($hasUncommitted) {
        Write-Warn "You have uncommitted changes on '$currentBranch'. It's recommended to commit/push to main before deploying."
      }
      if ($hasUpstream -and $aheadCount -gt 0) {
        Write-Warn "Your branch '$currentBranch' is ahead of '$upstream' by $aheadCount commit(s). Push to remote first to avoid losing changes."
      }
      Write-Warn "This will switch to an orphan 'gh-pages' branch and overwrite its history."
      $resp = Read-Host "Proceed? (y/N)"
      if ($resp -ne 'y' -and $resp -ne 'Y') {
        Write-Err "Aborted by user."
        exit 2
      }
    }

    Write-Step "Creating/switching to orphan gh-pages branch"
    git checkout --orphan gh-pages 2>$null | Out-Null

    Write-Step "Removing all files from index and working tree"
    git rm -rf . 2>$null | Out-Null

    # Clean untracked leftovers
    Get-ChildItem -Force | Where-Object { $_.Name -notin @('.git') } | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

    Write-Step "Copying built files from $distDir to repo root"
    Copy-Item -Path (Join-Path $distDir '*') -Destination $repoRoot -Recurse -Force

    # Optional: add SPA fallback 404.html if not present
    $indexPath = Join-Path $repoRoot 'index.html'
    $fallback = Join-Path $repoRoot '404.html'
    if ((Test-Path $indexPath) -and -not (Test-Path $fallback)) {
      Copy-Item $indexPath $fallback -Force
    }

    # Optional: prevent Jekyll processing
    New-Item -Path (Join-Path $repoRoot '.nojekyll') -ItemType File -Force | Out-Null

    Write-Step "Committing and pushing to origin/gh-pages"
    git add .
    git commit -m "Deploy Angular app manually"
    git push origin gh-pages --force

    Write-Host "\nDeployment complete. Configure GitHub Pages: Settings → Pages → Branch: gh-pages, Folder: /" -ForegroundColor Green
    Write-Host "Live URL should be: $BaseHref" -ForegroundColor Green
  }
  finally {
    Pop-Location
  }
}
finally {
  Pop-Location
}