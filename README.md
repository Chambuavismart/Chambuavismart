# ChambuaViSmart (Non-ML Rebuild)

This repository contains the project scaffolding for the ChambuaViSmart non-ML rebuild:
- backend: Spring Boot 3 (Java 17), MySQL, Flyway
- frontend: Angular (v17) with routing and proxy to backend

Backend
- Port: 8082
- Profiles: dev, test, prod (all use MySQL with separate schemas)
- CORS: http://localhost:4200
- Flyway migration: V1__init.sql creates base tables

Run backend
Option A: Using Docker Compose (recommended for quick start)
- docker compose up -d --build
- Services: mysql (3306), backend (8082), frontend (http://localhost:8080)
- Wait until mysql is healthy (about ~30s). Schemas will be auto-created: chambua_dev, chambua_test, chambua_prod. Backend will run Flyway migrations automatically and seed minimal dev data.

Option B: Local MySQL
1) Create MySQL schemas locally: chambua_dev, chambua_test, chambua_prod
2) Edit backend/src/main/resources/application.yml or set env vars:
   - SPRING_DATASOURCE_URL (default jdbc:mysql://localhost:3306/chambua_dev?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC)
   - SPRING_DATASOURCE_USERNAME (default root)
   - SPRING_DATASOURCE_PASSWORD (default password)
3) Build and run:
   - mvn -f backend/pom.xml clean package
   - java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

Option C: Run without IDE plugins (Maven directly)
- Command: mvn -f backend/pom.xml spring-boot:run
- This avoids relying on any IDE Spring Boot plugin; only Maven is required on PATH

Health check: http://localhost:8082/api/health

Frontend
- Dev server runs at http://localhost:4200
- Proxy (dev): /api -> http://localhost:8082 (see frontend/proxy.conf.json)
- Docker (prod-like): Served via Nginx at http://localhost:8080 and proxies /api to backend container

Run frontend
1) cd frontend
2) npm install
3) npm start

Pages scaffolded
Home, Fixtures, Teams, Matchup Analyzer, League Table, xG History, Advice, Match History, Admin Upload, 404

Verification checklist
- Backend builds: mvn -f backend/pom.xml clean package
- Backend starts on :8082 with profile=dev and connects to MySQL (Docker or local)
- Flyway runs and creates base tables on first start
- Frontend builds: cd frontend && npm install && npm run build
- Dev server runs: npm start (http://localhost:4200) and routes render
- Proxy works: visiting http://localhost:4200 then calling /api/health returns backend health JSON

Git branching (suggested)
- main: stable
- develop: integration branch
- feature/*: per feature, e.g. feature/fixtures, feature/matchup-analyzer

Push to GitHub (example)
- git init
- git remote add origin <your_repo_url>
- git checkout -b develop
- git add .
- git commit -m "chore: scaffold backend and frontend"
- git push -u origin develop

# ChambuaViSmart (Non-ML Rebuild)

This repository contains the project scaffolding for the ChambuaViSmart non-ML rebuild:
- backend: Spring Boot 3 (Java 17), MySQL, Flyway
- frontend: Angular (v17) with routing and proxy to backend

Backend
- Port: 8082
- Profiles: dev, test, prod (all use MySQL with separate schemas)
- CORS: http://localhost:4200
- Flyway migration: V1__init.sql creates base tables

Run backend
Option A: Using Docker Compose (recommended for quick start)
- docker compose up -d --build
- Services: mysql (3306), backend (8082), frontend (http://localhost:8080)
- Wait until mysql is healthy (about ~30s). Schemas will be auto-created: chambua_dev, chambua_test, chambua_prod. Backend will run Flyway migrations automatically and seed minimal dev data.

Option B: Local MySQL
1) Create MySQL schemas locally: chambua_dev, chambua_test, chambua_prod
2) Edit backend/src/main/resources/application.yml or set env vars:
   - SPRING_DATASOURCE_URL (default jdbc:mysql://localhost:3306/chambua_dev?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC)
   - SPRING_DATASOURCE_USERNAME (default root)
   - SPRING_DATASOURCE_PASSWORD (default password)
3) Build and run:
   - mvn -f backend/pom.xml clean package
   - java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

Option C: Run without IDE plugins (Maven directly)
- Command: mvn -f backend/pom.xml spring-boot:run
- This avoids relying on any IDE Spring Boot plugin; only Maven is required on PATH

Health check: http://localhost:8082/api/health

IntelliJ IDEA: Fix "Unknown run configuration type SpringBootApplicationConfigurationType"
1) Check plugins: File -> Settings -> Plugins. Install/enable "Spring" and "Spring Boot" plugins, then restart IDEA.
2) Re-import project: In Project view, right-click project root -> Maven -> Reimport.
3) Delete broken run configuration: Run/Debug dropdown -> Edit Configurations... -> delete the one marked with a red X.
4) Create a new Spring Boot run configuration:
   - + -> Spring Boot
   - Name: ChambuaViSmartBackend
   - Main class: com.chambua.vismart.ChambuaViSmartApplication
   - Use classpath of module: backend
   - Apply and OK
5) Optional (already provided): Use the shared Maven run configuration instead
   - In Run/Debug configurations, you should see "Backend: spring-boot:run" (shared from .run folder).
   - This works even without the Spring plugins, since it just runs Maven goal spring-boot:run in backend.
6) If still broken: File -> Invalidate Caches / Restart -> Invalidate and Restart.

Frontend
- Dev server runs at http://localhost:4200
- Proxy (dev): /api -> http://localhost:8082 (see frontend/proxy.conf.json)
- Docker (prod-like): Served via Nginx at http://localhost:8080 and proxies /api to backend container

Run frontend
1) cd frontend
2) npm install
3) npm start

Pages scaffolded
Home, Fixtures, Teams, Matchup Analyzer, League Table, xG History, Advice, Match History, Admin Upload, 404

Verification checklist
- Backend builds: mvn -f backend/pom.xml clean package
- Backend starts on :8082 with profile=dev and connects to MySQL (Docker or local)
- Flyway runs and creates base tables on first start
- Frontend builds: cd frontend && npm install && npm run build
- Dev server runs: npm start (http://localhost:4200) and routes render
- Proxy works: visiting http://localhost:4200 then calling /api/health returns backend health JSON

Git branching (suggested)
- main: stable
- develop: integration branch
- feature/*: per feature, e.g. feature/fixtures, feature/matchup-analyzer

Push to GitHub (example)
- git init
- git remote add origin <your_repo_url>
- git checkout -b develop
- git add .
- git commit -m "chore: scaffold backend and frontend"
- git push -u origin develop



Windows PowerShell terminal black lines in IntelliJ IDEA (PSReadLine outdated)
If you see black lines/artifacts across the terminal in IntelliJ IDEA on Windows, it is likely due to an outdated PSReadLine module (e.g., 2.0.0). Fix it by updating PSReadLine for the current user and then reopening the terminal.

Option A: Run the provided setup script
- Right-click scripts/windows/Setup-PSReadLine.ps1 -> Run with PowerShell
- Or from a terminal:
  powershell -ExecutionPolicy Bypass -File .\scripts\windows\Setup-PSReadLine.ps1

Option B: Run the manual command in a PowerShell terminal
  Install-Module PSReadLine -MinimumVersion 2.0.3 -Scope CurrentUser -Force

After installation, close and reopen the terminal (or open a new terminal tab in IntelliJ). For more details, see Microsoft’s guidance:
https://learn.microsoft.com/windows/terminal/troubleshooting#black-lines-in-powershell-51-6x-70
