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
