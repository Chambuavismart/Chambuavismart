<#
.SYNOPSIS
  Ensures PSReadLine is installed/updated to a minimum version to fix black lines in Windows PowerShell terminals (e.g., in IntelliJ IDEA).

.NOTES
  - Safe for non-admin use (Scope CurrentUser)
  - Requires internet access
  - Works on Windows PowerShell 5.1+ and PowerShell 7+

.USAGE
  Right-click file -> Run with PowerShell (or):
  powershell -ExecutionPolicy Bypass -File .\scripts\windows\Setup-PSReadLine.ps1
#>

param(
  [Version] $MinimumVersion = '2.0.3'
)

function Write-Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Ok($msg) { Write-Host "[OK]  $msg" -ForegroundColor Green }
function Write-Err($msg) { Write-Host "[ERR] $msg" -ForegroundColor Red }

$ErrorActionPreference = 'Stop'

try {
  Write-Info "Checking PowerShell version..."
  Write-Info ("PSVersion: {0}" -f $PSVersionTable.PSVersion)

  Write-Info "Ensuring 'PSGallery' repository exists and is trusted..."
  $psGallery = Get-PSRepository -Name PSGallery -ErrorAction SilentlyContinue
  if (-not $psGallery) {
    Write-Warn "'PSGallery' not found. Registering..."
    Register-PSRepository -Default
    $psGallery = Get-PSRepository -Name PSGallery
  }
  if ($psGallery.InstallationPolicy -ne 'Trusted') {
    Set-PSRepository -Name PSGallery -InstallationPolicy Trusted
    Write-Ok "'PSGallery' set to Trusted."
  } else {
    Write-Ok "'PSGallery' already Trusted."
  }

  Write-Info "Checking PSReadLine installation..."
  $existing = Get-Module -ListAvailable -Name PSReadLine | Sort-Object Version -Descending | Select-Object -First 1
  if ($existing) {
    Write-Info ("Found PSReadLine {0} in {1}" -f $existing.Version, $existing.ModuleBase)
  } else {
    Write-Warn "PSReadLine not found. It will be installed."
  }

  $needsInstall = $true
  if ($existing) {
    if ([Version]$existing.Version -ge $MinimumVersion) {
      $needsInstall = $false
      Write-Ok ("PSReadLine already meets minimum version {0}." -f $MinimumVersion)
    }
  }

  if ($needsInstall) {
    Write-Info ("Installing/Updating PSReadLine >= {0} for CurrentUser..." -f $MinimumVersion)
    # Ensure NuGet provider is available
    $nuget = Get-PackageProvider -Name NuGet -ErrorAction SilentlyContinue
    if (-not $nuget) {
      Write-Info "Installing NuGet provider..."
      Install-PackageProvider -Name NuGet -Force -Scope CurrentUser | Out-Null
    }

    Install-Module PSReadLine -MinimumVersion $MinimumVersion -Scope CurrentUser -Force -AllowClobber
    Write-Ok "PSReadLine installed/updated successfully."
  }

  Write-Info "Verifying installation..."
  $ver = (Get-Module -ListAvailable -Name PSReadLine | Sort-Object Version -Descending | Select-Object -First 1).Version
  if (-not $ver) { throw "PSReadLine verification failed." }
  if ([Version]$ver -lt $MinimumVersion) { throw "PSReadLine version $ver < $MinimumVersion after install." }
  Write-Ok ("PSReadLine version {0} is ready." -f $ver)

  Write-Host "\nNext steps:" -ForegroundColor Magenta
  Write-Host " - Close and reopen your terminal (or open a new tab in IntelliJ IDEA Terminal)." -ForegroundColor Gray
  Write-Host " - If you still see black lines, ensure you're using Windows PowerShell >= 5.1 or PowerShell 7+." -ForegroundColor Gray
  Write-Host " - Microsoft guidance: https://learn.microsoft.com/windows/terminal/troubleshooting#black-lines-in-powershell-51-6x-70" -ForegroundColor Gray

  exit 0
}
catch {
  Write-Err $_
  Write-Err "If running behind a proxy, configure proxy for PowerShellGet and try again."
  Write-Err "Manual command: Install-Module PSReadLine -MinimumVersion $MinimumVersion -Scope CurrentUser -Force"
  exit 1
}
