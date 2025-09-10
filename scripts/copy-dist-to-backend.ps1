param(
  [string]$FrontendDistPath = "..\frontend\dist\app",
  [string]$BackendStaticPath = "..\backend\src\main\resources\static"
)

Write-Host "[copy-dist] FrontendDistPath = $FrontendDistPath"
Write-Host "[copy-dist] BackendStaticPath = $BackendStaticPath"

if (!(Test-Path $FrontendDistPath)) {
  Write-Error "Frontend dist not found at '$FrontendDistPath'. Build the frontend first (npm ci && npm run build:prod)."
  exit 1
}

# Ensure backend static folder exists
if (!(Test-Path $BackendStaticPath)) { New-Item -ItemType Directory -Force -Path $BackendStaticPath | Out-Null }

# Clear existing files in static to avoid stale assets
Get-ChildItem -Path $BackendStaticPath -Recurse -Force -ErrorAction SilentlyContinue | Remove-Item -Force -Recurse -ErrorAction SilentlyContinue

# Copy new dist
Copy-Item -Path (Join-Path $FrontendDistPath '*') -Destination $BackendStaticPath -Recurse -Force

Write-Host "[copy-dist] Copied dist to backend static successfully."