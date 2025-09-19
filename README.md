# ChambuaViSmart (Non-ML Rebuild)

A world-class football analysis app — this repo is the active Non-ML rebuild.

Tech stack
- Backend: Spring Boot 3 (Java 17), MySQL, Flyway (prod), H2 for tests
- Frontend: Angular v17 with routing and a dev proxy to backend

Backend
- Port: 8082
- Profiles: dev (MySQL), test (H2), prod (MySQL + Flyway)
- CORS (dev): http://localhost:4200
- Health check: http://localhost:8082/api/health

Quick start (Docker Compose)
- docker compose up -d --build
- Services: mysql (3306), backend (8082), frontend (http://localhost:8080)
- Wait ~30s for MySQL to become healthy. Dev DB schema used: chambua_dev.

Local development (without Docker)
1) MySQL running locally.
2) backend/src/main/resources/application.yml dev profile defaults to:
   - URL: jdbc:mysql://localhost:3306/chambua (createDatabaseIfNotExist=true)
   - Username: DB_USERNAME env (default root)
   - Password: DB_PASSWORD env (default root)
   - Flyway: disabled in dev (DDL is update)
3) Build and run backend:
   - mvn -f backend/pom.xml clean package
   - java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
   - Or: mvn -f backend/pom.xml spring-boot:run
4) Frontend (dev server):
   - cd frontend
   - npm install
   - npm start (http://localhost:4200)
   - Dev proxy: /api -> http://localhost:8082 (see frontend/proxy.conf.json)

Verification checklist
- Backend builds: mvn -f backend/pom.xml clean package
- Backend starts on :8082 with profile=dev and connects to MySQL
- Frontend builds: cd frontend && npm install && npm run build
- Dev server runs: npm start and routes render at http://localhost:4200
- Proxy works: visiting http://localhost:4200 then calling /api/health returns backend health JSON

Fixtures Analysis and Match Analysis harmonization (2025-09-19)
- Both frontend tabs now call the backend endpoint POST /api/match-analysis/analyze.
- Fixtures Analysis rewired: client-side PoissonService is no longer used for predictions; it calls MatchAnalysisService via match-analysis.service.ts.
- analysisType: Request adds analysisType to differentiate logic:
  - "fixtures": simpler Poisson-style using overall form and shallow H2H averages (no split-weight weighting), scaled with higher draw share. No persistent caching (avoids cross-mixing).
  - "match": weighted PPG (home/away) and blended form/H2H metrics with split-based BTTS/Over; persisted caching enabled (modelVariant=v2).
- Request supports either team IDs or names; seasonId is optional (backend falls back to current season).
- Response DTO remains unchanged (MatchAnalysisResponse with winProbabilities, bttsProbability, over25Probability, expectedGoals, advice, etc.).
- Frontend caching: match-analysis.service.ts caches identical requests in-memory via shareReplay for responsiveness (cache key includes analysisType).
- Backend caching: persisted cache entries carry modelVariant="v2" inside JSON; fixtures-mode responses are not persisted.
- Performance: backend analysis target <20ms; Fixtures Analysis end-to-end target <500ms with caching.
- Monitoring: logs include analysisType, leagueId, team names, and total ms at INFO level.

Rollback plan
- To temporarily revert Fixtures Analysis to local computation, re-enable PoissonService.calculatePredictions usage inside played-matches-summary.component.ts (previous implementation preserved in git history).

IntelliJ IDEA tip (if Spring Boot run config error)
- Install/enable Spring + Spring Boot plugins and restart.
- Reimport Maven project.
- Delete broken run config and create a new Spring Boot config with main class:
  com.chambua.vismart.ChambuaViSmartApplication (module: backend)
- Alternatively, use the shared Maven run config "Backend: spring-boot:run".

Windows PowerShell rendering artifacts in IDE terminal (PSReadLine)
- Option A: scripts\windows\Setup-PSReadLine.ps1 (Run with PowerShell) or:
  powershell -ExecutionPolicy Bypass -File .\scripts\windows\Setup-PSReadLine.ps1
- Option B: Install-Module PSReadLine -MinimumVersion 2.0.3 -Scope CurrentUser -Force
- Then restart the terminal. More: https://learn.microsoft.com/windows/terminal/troubleshooting#black-lines-in-powershell-51-6x-70

Git branching (suggested)
- main: stable
- develop: integration branch
- feature/*: feature branches (e.g., feature/fixtures, feature/matchup-analyzer)
