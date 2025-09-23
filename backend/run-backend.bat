@echo off
REM Starts the Spring Boot backend without relying on any IDE plugins.
REM Requires Maven to be installed and available on PATH.

pushd %~dp0
mvn spring-boot:run
set EXIT_CODE=%ERRORLEVEL%
popd
exit /B %EXIT_CODE%