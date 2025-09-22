# ChambuaViSmart Backend

Spring Boot 3 (Java 17) backend. Profiles: dev, test, prod. MySQL is used in all environments. Flyway manages schema.

How to run (local):

1. Ensure MySQL is running and create databases (schemas):
   - chambua_dev
   - chambua_test
   - chambua_prod

2. Set environment variables (or edit application.yml):
   - DB_USERNAME (default: root)
   - DB_PASSWORD (default: password)

3. Build and run:

```bash
mvn -f backend/pom.xml clean package
java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Health check: http://localhost:8082/api/health

PDF Generation (Fixture Analysis)
- Preferred: LaTeX via latexmk compiles templates/analysis.tex with Noto fonts and watermark.
- Requirements (for full LaTeX PDF):
  - Install TeX Live (texlive-full) and texlive-fonts-extra (for noto) on Linux.
  - On Windows, install TeX Live and ensure latexmk.exe is on PATH or at a known location (e.g., C:\\texlive\\2025\\bin\\win32\\latexmk.exe).
  - Ensure the latexmk command is callable: `latexmk --version`.
- Fallback: If latexmk is not available, the backend generates a simple, valid PDF containing key sections and a diagonal gray watermark (no external libraries). This keeps the feature usable on minimal environments.

Troubleshooting
- If you see `Cannot run program "latexmk" ... error=2`, install TeX Live or add latexmk to PATH.
- You can also set up TeX Live at one of the default probed paths listed above on Windows.
