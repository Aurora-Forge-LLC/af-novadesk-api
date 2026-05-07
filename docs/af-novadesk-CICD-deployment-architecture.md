# AF NovaDesk API — CI/CD & Deployment Architecture

> **Date:** May 2026  
> **Service:** `af-novadesk-api`  
> **Template basis:** `af-authhub` architecture patterns

---

## Table of Contents

1. [Overview](#overview)
2. [Repository Structure](#repository-structure)
3. [Branch & Release Strategy](#branch--release-strategy)
4. [CI/CD Workflows](#cicd-workflows)
5. [Environment Architecture](#environment-architecture)
6. [Docker & Container Strategy](#docker--container-strategy)
7. [Application Configuration](#application-configuration)
8. [AWS Infrastructure](#aws-infrastructure)
9. [GitHub Secrets & Variables Reference](#github-secrets--variables-reference)
10. [Deployment Checklist](#deployment-checklist)

---

## Overview

`af-novadesk-api` is a Spring Boot microservice built on the Aurora Forge platform. It follows the same CI/CD patterns established by `af-authhub`:

- **Package management**: AWS CodeArtifact (Maven)
- **Container registry**: Amazon ECR
- **Reverse proxy**: Traefik (TLS via Let's Encrypt)
- **Monitoring**: Spring Boot Admin
- **Runners**: Self-hosted EC2 runners in `ap-south-1`
- **Environments**: DIT (dev/integration test) and SIT (system integration test)

```
Developer → Push to develop
                │
                ▼
         GitHub Actions (deploy.yml)
                │
         ┌──────┴──────┐
         │  DIT Deploy  │  (1 replica, SNAPSHOT)
         └──────┬──────┘
                │
         ┌──────▼──────┐
         │  EC2 Runner  │ → CodeArtifact → ECR → Docker (af-core-dit network)
         └─────────────┘

Developer → Create GitHub Release (tag vX.Y.Z)
                │
                ▼
         GitHub Actions (deploy.yml)
                │
         ┌──────┴──────┐
         │  SIT Deploy  │  (2 replicas, RELEASE only)
         └──────┬──────┘
                │
         ┌──────▼──────┐
         │  EC2 Runner  │ → CodeArtifact → ECR → Docker (af-core-sit network)
         └─────────────┘
```

---

## Repository Structure

```
af-novadesk-api/
├── .github/
│   └── workflows/
│       ├── deploy.yml            # Primary deployment: DIT (develop) + SIT (release)
│       ├── maven-build.yml       # CI build on all PRs and branch pushes
│       ├── maven-deploy.yml      # SNAPSHOT publish to CodeArtifact on main
│       ├── release.yml           # Full release lifecycle (tag vX.Y.Z)
│       └── init-db.yml           # Manual DB schema init (placeholder — no DB yet)
├── .mvn/
│   └── settings.xml              # CodeArtifact Maven settings
├── docs/
│   └── af-novadesk-CICD-deployment-architecture.md  ← this file
├── scripts/
│   └── init-db.sh                # DB init script (placeholder)
├── src/
│   └── main/
│       ├── java/com/af/novadesk/api/
│       │   └── NovaDeskapiApplication.java
│       └── resources/
│           ├── application.yml           # Base config (no hardcoded secrets/URLs)
│           ├── application-dit.yml       # DIT profile overrides
│           ├── application-sit.yml       # SIT profile overrides
│           ├── application-prod.yml      # Production profile overrides
│           └── application-local.yml     # Local dev (gitignored — hardcoded values OK here)
├── docker-compose.yml            # Production/manual single-environment compose
├── docker-compose.env.yml        # Multi-environment rolling deploy (used by deploy.yml)
├── Dockerfile                    # eclipse-temurin:25-jre-alpine, non-root user
├── pom.xml                       # Maven build (inherits af-spring-parent BOM)
└── README.md
```

---

## Branch & Release Strategy

| Branch / Event        | Triggers Workflow   | Target Env | Replicas | Version Type   |
|-----------------------|---------------------|------------|----------|----------------|
| Push → `develop`      | `deploy.yml`        | DIT        | 1        | SNAPSHOT ✅    |
| PR → `develop`/`main` | `maven-build.yml`   | —          | —        | Validate only  |
| Push → `main`         | `maven-deploy.yml`  | —          | —        | SNAPSHOT artifact publish |
| Tag `vX.Y.Z`          | `release.yml`       | —          | —        | RELEASE artifact + ECR image |
| GitHub Release        | `deploy.yml`        | SIT        | 2        | RELEASE only ✅ |

### Release Flow (release.yml)

```
1. Push tag vX.Y.Z → triggers release.yml
2. Checkout main at that tag
3. Set pom.xml version to X.Y.Z (remove -SNAPSHOT)
4. Maven deploy → CodeArtifact af-releases
5. Docker build + push → ECR :X.Y.Z and :latest
6. Update GitHub Release notes with artifact coordinates
7. Commit X.Y.Z pom.xml to main
8. Bump pom.xml to X.Y.(Z+1)-SNAPSHOT, commit to main
9. Merge main → develop (keeps branches in sync)
```

---

## CI/CD Workflows

### `deploy.yml` — Primary Deployment Pipeline

**Triggers:** Push to `develop` (→ DIT) | GitHub Release published (→ SIT)

**Steps:**
1. Determine target environment (DIT vs SIT) and replica count
2. Set JAVA_HOME to Amazon Corretto 25
3. Fetch CodeArtifact auth token via EC2 instance IAM role
4. Extract and validate Maven version (RELEASE required for SIT)
5. `mvn clean verify` — full build + tests
6. `mvn deploy` — publish JAR to CodeArtifact (af-snapshots or af-releases)
7. `docker build` + `docker push` to ECR with env-tagged image
8. Write runtime `.env` to `/dev/shm` (tmpfs — never touches disk)
9. `docker compose up` — rolling deploy via `docker-compose.env.yml`
10. Health check loop — waits up to 180s for all replicas to pass liveness
11. Cleanup: remove env file from tmpfs

### `maven-build.yml` — CI Validation

**Triggers:** All pushes and PRs to `develop` / `main`

**Steps:** Checkout → Set JDK → CodeArtifact token → `mvn verify` → Docker build (no push)

### `maven-deploy.yml` — Snapshot Artifact Publish

**Triggers:** Push to `main`

**Steps:** Checkout → Set JDK → CodeArtifact token → `mvn deploy -DskipTests` → Docker push to ECR

### `release.yml` — Full Release Lifecycle

**Triggers:** Push of tag matching `v[0-9]+.[0-9]+.[0-9]+`

See [Release Flow](#release-flow-releaseyml) above.

### `init-db.yml` — Database Initialization (Placeholder)

**Triggers:** Manual (`workflow_dispatch`) — select `dit` or `sit`

**Note:** Currently a placeholder. No database is configured for this service yet. When a database is provisioned, update `scripts/init-db.sh` with the appropriate schema creation SQL.

---

## Environment Architecture

### DIT (Development Integration Test)

- **Network:** `af-core-dit` (external, managed by af-infra-core)
- **Replicas:** 1
- **URL:** `https://novadesk.dit.auroraforge.co` (via Traefik)
- **TLS:** Let's Encrypt staging cert resolver
- **Spring Profile:** `dit`
- **Version:** SNAPSHOT allowed

### SIT (System Integration Test)

- **Network:** `af-core-sit` (external, managed by af-infra-core)
- **Replicas:** 2 (rolling update strategy)
- **URL:** `https://novadesk.sit.auroraforge.co` (via Traefik)
- **TLS:** Let's Encrypt production cert resolver
- **Spring Profile:** `sit`
- **Version:** RELEASE only (no SNAPSHOTs)

---

## Docker & Container Strategy

### Image

```
Base:    eclipse-temurin:25-jre-alpine
User:    appuser (non-root)
Port:    8080
JAR:     target/af-novadesk-api-*.jar → /app/app.jar
```

### Image Tags

| Tag Pattern             | When Created                        |
|-------------------------|-------------------------------------|
| `dit-1.0.0-SNAPSHOT`    | Every push to `develop`             |
| `dit-latest`            | Every push to `develop`             |
| `sit-1.0.0`             | GitHub Release published            |
| `sit-latest`            | GitHub Release published            |
| `1.0.0`                 | Release workflow (tag vX.Y.Z)       |
| `latest`                | Release workflow (tag vX.Y.Z)       |

### Healthcheck

```
Path:       /novadesk/actuator/health/liveness
Strategy:   wget — no curl dependency required in alpine
Interval:   30s | Timeout: 10s | Retries: 5 | Start period: 60s
```

> **Design note:** Liveness only checks JVM + disk space. Infrastructure dependencies (DB, Redis, etc.) belong in the readiness group — they should not kill the container if temporarily unavailable.

---

## Application Configuration

All connection strings, hostnames, ports, and credentials are externalised as environment variable placeholders in `application.yml`. No values are hardcoded (except in the gitignored `application-local.yml`).

### Profile Hierarchy

```
application.yml          ← base config (loaded always)
    + application-{profile}.yml   ← environment overrides
```

| Profile | File                    | Notes                                |
|---------|-------------------------|--------------------------------------|
| `dit`   | `application-dit.yml`   | DEBUG logging, DIT SBA URL           |
| `sit`   | `application-sit.yml`   | INFO logging, SIT SBA URL            |
| `prod`  | `application-prod.yml`  | WARN logging, prod SBA URL           |
| `local` | `application-local.yml` | Hardcoded local values — **gitignored** |

### Runtime Environment Variables (injected by docker-compose.env.yml)

| Variable                        | Description                              |
|---------------------------------|------------------------------------------|
| `SPRING_PROFILES_ACTIVE`        | Active Spring profile (`dit`/`sit`/`prod`) |
| `SPRING_BOOT_ADMIN_URL`         | Spring Boot Admin server URL             |
| `SPRING_BOOT_ADMIN_CLIENT_USERNAME` | SBA authentication username          |
| `SPRING_BOOT_ADMIN_CLIENT_PASSWORD` | SBA authentication password          |

> When infrastructure dependencies are added (DB, Redis, RabbitMQ, etc.), their credentials will be injected here and referenced in `application.yml` via `${ENV_VAR}` placeholders.

---

## AWS Infrastructure

### CodeArtifact

All Maven dependencies are resolved through CodeArtifact. No direct Maven Central access from runners.

| Repository         | Purpose                              |
|--------------------|--------------------------------------|
| `maven-central-store` | Proxy for Maven Central (external deps) |
| `af-snapshots`     | Internal SNAPSHOT artifacts          |
| `af-releases`      | Internal RELEASE artifacts           |

### ECR Repository

Create ECR repository before first deployment:
```bash
aws ecr create-repository \
  --repository-name af-novadesk-api \
  --region ap-south-1
```

### IAM / Authentication

- EC2 self-hosted runners authenticate to CodeArtifact and ECR via **instance IAM role** — no static credentials stored in GitHub.
- Only `GH_PAT` (GitHub Personal Access Token) is needed as a secret for the release workflow to push commits back to the repo.

---

## GitHub Secrets & Variables Reference

Configure these in the GitHub repository settings under **Settings → Secrets and variables → Actions**.

> Secrets marked 🔐 are sensitive and must be stored as **Secrets** (not Variables).  
> Items marked 📋 are non-sensitive and can be stored as **Variables**.

### Repository Variables (Settings → Variables → Actions)

| Variable              | Example Value                     | Description                                      |
|-----------------------|-----------------------------------|--------------------------------------------------|
| `CODEARTIFACT_DOMAIN` | `af-artifacts`                    | 📋 CodeArtifact domain name                      |
| `CODEARTIFACT_OWNER`  | `123456789012`                    | 📋 AWS Account ID owning the CodeArtifact domain |
| `AWS_REGION`          | `ap-south-1`                      | 📋 AWS region for CodeArtifact and ECR           |
| `ECR_REGISTRY`        | `123456789012.dkr.ecr.ap-south-1.amazonaws.com` | 📋 ECR registry base URL    |
| `DOMAIN_SUFFIX`       | `dit.auroraforge.co`              | 📋 Domain suffix for Traefik routing (per env)   |
| `CERT_RESOLVER`       | `letsencrypt` or `letsencrypt-staging` | 📋 Traefik TLS cert resolver name           |

> **Note:** `DOMAIN_SUFFIX`, `CERT_RESOLVER`, `ECR_REGISTRY` should be set at the **environment** level (Settings → Environments → dit / sit) so each environment gets the correct value.

### Environment-Scoped Variables (Settings → Environments → {dit|sit})

| Variable    | Description                         |
|-------------|-------------------------------------|
| `SSH_HOST`  | 📋 EC2 server IP / hostname (for init-db.yml) |
| `SSH_USER`  | 📋 SSH username on EC2 (for init-db.yml)      |

### Repository Secrets (Settings → Secrets → Actions)

| Secret                   | Description                                          |
|--------------------------|------------------------------------------------------|
| `GH_PAT`                 | 🔐 GitHub Personal Access Token — needed by release.yml to push pom.xml commits back to repo. Requires `repo` scope. |
| `ECR_REGISTRY`           | 🔐 ECR registry URL (if preferred as secret over variable) |
| `CODEARTIFACT_DOMAIN`    | 🔐 Can also be stored as secret instead of variable  |
| `CODEARTIFACT_OWNER`     | 🔐 Can also be stored as secret instead of variable  |
| `AWS_REGION`             | 🔐 Can also be stored as secret instead of variable  |

### Environment-Scoped Secrets (Settings → Environments → {dit|sit})

| Secret                        | Description                                           |
|-------------------------------|-------------------------------------------------------|
| `SSH_PRIVATE_KEY`             | 🔐 EC2 SSH private key for init-db.yml                |
| `SBA_CLIENT_USERNAME`         | 🔐 Spring Boot Admin client username                  |
| `SBA_CLIENT_PASSWORD`         | 🔐 Spring Boot Admin client password                  |

> **Future secrets** — Add these when infrastructure dependencies are provisioned:

| Secret                   | Description                                          |
|--------------------------|------------------------------------------------------|
| `POSTGRES_USER`          | 🔐 Application database user                         |
| `POSTGRES_PASSWORD`      | 🔐 Application database password                     |
| `REDIS_PASSWORD`         | 🔐 Redis password (if Redis is added)                |
| `RABBITMQ_USERNAME`      | 🔐 RabbitMQ user (if messaging is added)             |
| `RABBITMQ_PASSWORD`      | 🔐 RabbitMQ password (if messaging is added)         |

---

## Deployment Checklist

Use this checklist before the first production deployment.

### AWS Pre-requisites

- [ ] EC2 self-hosted runner registered in GitHub (labels: `self-hosted`, `linux`, `ap-south-1`)
- [ ] EC2 instance IAM role has permissions for:
  - `codeartifact:GetAuthorizationToken`
  - `codeartifact:GetRepositoryEndpoint`
  - `codeartifact:PublishPackageVersion`
  - `codeartifact:PutPackageMetadata`
  - `ecr:GetAuthorizationToken`
  - `ecr:BatchCheckLayerAvailability`
  - `ecr:PutImage`
  - `ecr:InitiateLayerUpload`
  - `ecr:UploadLayerPart`
  - `ecr:CompleteLayerUpload`
- [ ] ECR repository `af-novadesk-api` created in `ap-south-1`
- [ ] CodeArtifact domain and repositories (`af-snapshots`, `af-releases`, `maven-central-store`) exist
- [ ] `af-spring-parent:1.0.1` artifact published in CodeArtifact

### GitHub Repository Setup

- [ ] Repository created: `Aurora-Forge-LLC/af-novadesk-api`
- [ ] Branches created: `main`, `develop`
- [ ] Branch protection rules configured on `main` and `develop`
- [ ] GitHub Environments created: `dit`, `sit`
- [ ] All Variables configured (see table above)
- [ ] All Secrets configured (see table above)
- [ ] Environment-scoped variables/secrets set per environment

### Infrastructure

- [ ] `af-core-dit` Docker network exists on DIT server (managed by af-infra-core)
- [ ] `af-core-sit` Docker network exists on SIT server (managed by af-infra-core)
- [ ] Traefik running and configured on both DIT and SIT servers
- [ ] DNS records created: `novadesk.dit.auroraforge.co`, `novadesk.sit.auroraforge.co`
- [ ] Spring Boot Admin running at expected URL (`http://af-sba-{env}:9191`)

### First Deployment

1. Push code to `develop` → triggers DIT deployment automatically
2. Verify health: `https://novadesk.dit.auroraforge.co/novadesk/actuator/health`
3. Create GitHub Release (tag `v1.0.0`) → triggers SIT deployment
4. Verify health: `https://novadesk.sit.auroraforge.co/novadesk/actuator/health`

---

*This document should be updated as infrastructure dependencies are added to the service.*

