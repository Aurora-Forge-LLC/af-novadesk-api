# AF NovaDesk API
AF NovaDesk API is a Spring Boot microservice within the Aurora Forge platform.
## Technology Stack
- **Java 25** (Amazon Corretto)
- **Spring Boot** (via `af-spring-parent` BOM)
- **Maven** with AWS CodeArtifact as package registry
- **Docker** + **Amazon ECR** for container registry
- **Traefik** for reverse proxy / TLS termination
- **Spring Boot Admin** for service monitoring
## Project Structure
```
af-novadesk-api/
+-- .github/workflows/        # CI/CD pipelines
¦   +-- deploy.yml            # Auto-deploy: develop?DIT, release?SIT
¦   +-- maven-build.yml       # Build + validate on PRs and pushes
¦   +-- maven-deploy.yml      # SNAPSHOT deploy to CodeArtifact on main
¦   +-- release.yml           # Full release flow triggered by vX.Y.Z tag
¦   +-- init-db.yml           # Manual DB schema init (placeholder)
+-- .mvn/
¦   +-- settings.xml          # Maven settings for CodeArtifact
+-- scripts/
¦   +-- init-db.sh            # DB initialization script (placeholder)
+-- src/
¦   +-- main/
¦       +-- java/com/af/novadesk/api/
¦       ¦   +-- NovaDeskapiApplication.java
¦       +-- resources/
¦           +-- application.yml           # Base configuration
¦           +-- application-dit.yml       # DIT overrides
¦           +-- application-sit.yml       # SIT overrides
¦           +-- application-prod.yml      # Production overrides
¦           +-- application-local.yml     # Local dev (gitignored)
+-- docker-compose.yml        # Production service definition
+-- docker-compose.env.yml    # Multi-environment deploy compose file
+-- Dockerfile                # Container build
+-- pom.xml                   # Maven build descriptor
```
## Branch Strategy
| Branch    | Deployment Target | Version Type |
|-----------|-------------------|--------------|
| `develop` | DIT (1 replica)   | SNAPSHOT     |
| `main`    | —                 | Artifact publish only |
| `vX.Y.Z` tag | SIT (2 replicas) | RELEASE    |
## Local Development
1. Copy `src/main/resources/application-local.yml` and fill in your values
2. Run with profile: `SPRING_PROFILES_ACTIVE=local mvn spring-boot:run`
## Endpoints
| Path | Description |
|------|-------------|
| `GET /novadesk/actuator/health` | Full health status |
| `GET /novadesk/actuator/health/liveness` | Liveness probe |
| `GET /novadesk/actuator/health/readiness` | Readiness probe |
| `GET /novadesk/actuator/prometheus` | Prometheus metrics |
## See Also
- [CI/CD Deployment Architecture](docs/af-novadesk-CICD-deployment-architecture.md)
