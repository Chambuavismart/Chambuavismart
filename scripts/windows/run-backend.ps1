param(
  [switch] $SkipTests
)
$ErrorActionPreference = 'Stop'

function Write-Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "[ERROR] $msg" -ForegroundColor Red }

# Resolve repo root as two levels up from this script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)

$backendDir = Join-Path $repoRoot 'backend'
$rootAggregatorPom = Join-Path $repoRoot 'Chambuavismart\pom.xml'

if (-not (Test-Path $backendDir)) {
  Write-Err "Backend directory not found at: $backendDir"
  exit 1
}

# Choose working directory and pom
$workDir = $backendDir
$pomToUse = Join-Path $backendDir 'pom.xml'
if (Test-Path $rootAggregatorPom) {
  # Prefer running from backend directly to avoid aggregator path quirks
  $workDir = $backendDir
  $pomToUse = Join-Path $backendDir 'pom.xml'
}

# Detect Maven
$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
  Write-Warn "Apache Maven was not found in PATH."
  Write-Warn "Please install Maven: https://maven.apache.org/install.html"
  Write-Warn "Alternatively, add Maven Wrapper (mvnw) to the project to avoid system install."
  exit 2
}

# Java check
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
  Write-Warn "Java runtime not found in PATH. Java 17+ is required for this project."
  Write-Warn "Install from: https://adoptium.net or ensure JAVA_HOME is configured."
  exit 3
}

Push-Location $workDir
try {
  Write-Info "Using pom: $pomToUse"
  $mvnArgs = @('spring-boot:run')
  if ($SkipTests) { $mvnArgs = @('spring-boot:run','-DskipTests') }
  # Ensure clean compile of changes
  & $mvn clean package -DskipTests | Write-Host
  Write-Info "Starting Spring Boot application..."
  & $mvn @mvnArgs
}
finally {
  Pop-Location
}
