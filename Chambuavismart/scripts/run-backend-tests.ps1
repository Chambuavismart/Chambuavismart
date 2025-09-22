# Runs backend tests using Maven, ensuring all dependencies and plugins are downloaded first
Param(
  [switch]$CI,
  [string]$Settings,
  [int]$Retries = 3
)

$ErrorActionPreference = 'Stop'

# Move to backend directory
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path | Split-Path -Parent
$backendDir = Join-Path $repoRoot 'backend'
Set-Location $backendDir

# Prefer Maven Wrapper if available (support Windows and Linux):
# - Check for mvnw(.cmd) in backend first, then repo root; fallback to 'mvn'
$mvnCmd = $null
$mvnwBackendWin = Join-Path $backendDir 'mvnw.cmd'
$mvnwRootWin    = Join-Path $repoRoot  'mvnw.cmd'
$mvnwBackendNix = Join-Path $backendDir 'mvnw'
$mvnwRootNix    = Join-Path $repoRoot  'mvnw'
if     (Test-Path $mvnwBackendWin) { $mvnCmd = $mvnwBackendWin }
elseif (Test-Path $mvnwRootWin)    { $mvnCmd = $mvnwRootWin }
elseif (Test-Path $mvnwBackendNix) { $mvnCmd = $mvnwBackendNix }
elseif (Test-Path $mvnwRootNix)    { $mvnCmd = $mvnwRootNix }
else                                { $mvnCmd = 'mvn' }

# Memory for Maven
$env.MAVEN_OPTS = "-Xmx1024m"

# Build common args
$baseArgs = @()
if ($CI) { $baseArgs += '-B' }
if ($Settings) { $baseArgs += @('-s', $Settings) }

function Invoke-Maven {
  param(
    [string[]]$Args
  )
  Write-Host "> mvn $($Args -join ' ')" -ForegroundColor Cyan
  & $mvnCmd @Args
  return $LASTEXITCODE
}

function Invoke-With-Retry {
  param(
    [string[]]$Args,
    [int]$RetriesLocal
  )
  $attempt = 0
  $exit = 0
  while ($attempt -lt $RetriesLocal) {
    $attempt++
    $exit = Invoke-Maven -Args $Args
    if ($exit -eq 0) { break }
    Write-Host "Command failed with exit code $exit. Retrying ($attempt/$RetriesLocal)..." -ForegroundColor Yellow
    Start-Sleep -Seconds ([Math]::Min(5 * $attempt, 20))
  }
  return $exit
}

# 1) Resolve project dependencies
Write-Host "Resolving Maven dependencies (dependency:resolve)..." -ForegroundColor Yellow
$resolveArgs = $baseArgs + @('-U', '-q', '-DskipTests=true', 'dependency:resolve')
$resolveExit = Invoke-With-Retry -Args $resolveArgs -RetriesLocal $Retries
if ($resolveExit -ne 0) {
  Write-Error "Failed to resolve Maven dependencies after $Retries attempt(s). Check your network, proxy, or pom.xml. You can pass -Settings path\\to\\settings.xml for custom mirrors."
  exit $resolveExit
}

# 2) Resolve plugin artifacts
Write-Host "Resolving Maven plugin dependencies (dependency:resolve-plugins)..." -ForegroundColor Yellow
$resolvePluginsArgs = $baseArgs + @('-U', '-q', '-DskipTests=true', 'dependency:resolve-plugins')
$resolvePluginsExit = Invoke-With-Retry -Args $resolvePluginsArgs -RetriesLocal $Retries
if ($resolvePluginsExit -ne 0) {
  Write-Error "Failed to resolve Maven plugin dependencies after $Retries attempt(s). Check your network, proxy, or pom.xml plugin sections."
  exit $resolvePluginsExit
}

Write-Host "All Maven dependencies and plugins resolved successfully." -ForegroundColor Green

# 3) Run backend tests (clean test)
$testArgs = $baseArgs + @('clean', 'test', '-DskipTests=false', '-q')
$testExit = Invoke-Maven -Args $testArgs

if ($testExit -ne 0) {
  Write-Error "Backend tests failed with exit code $testExit"
  exit $testExit
} else {
  Write-Host "Backend tests completed successfully" -ForegroundColor Green
}
