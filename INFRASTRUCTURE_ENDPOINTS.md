# AF NovaDesk API — Infrastructure API Endpoints

This document covers **non-functional, infrastructure-related** API endpoints only (Swagger UI, OpenAPI docs, and Actuator health/observability).  
For service-layer (business logic) endpoints see the live Swagger UI or OpenAPI JSON listed below.

---

## Base Paths

| Setting | Value |
|---------|-------|
| Servlet context path | `/novadesk-api` |
| Actuator base path | `/actuator` (mounted under context path) |
| Server port | `8080` (internal container port) |

---

## Environments & Base URLs

| Environment | External Base URL |
|-------------|-------------------|
| Local | `http://localhost:8080` |
| DIT | `https://novadesk-api.dit.auroraforge.co` |
| SIT | `https://novadesk-api.sit.auroraforge.co` |
| Production | `https://novadesk-api.auroraforge.co` |

---

## Swagger UI

Interactive API explorer (SpringDoc OpenAPI).

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080/novadesk-api/swagger-ui/index.html` |
| DIT | `https://novadesk-api.dit.auroraforge.co/novadesk-api/swagger-ui/index.html` |
| SIT | `https://novadesk-api.sit.auroraforge.co/novadesk-api/swagger-ui/index.html` |
| Production | `https://novadesk-api.auroraforge.co/novadesk-api/swagger-ui/index.html` |

> SpringDoc also registers a redirect from `/swagger-ui.html` → `/swagger-ui/index.html` automatically.

---

## OpenAPI Docs (JSON)

Machine-readable OpenAPI 3 specification.

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080/novadesk-api/v3/api-docs` |
| DIT | `https://novadesk-api.dit.auroraforge.co/novadesk-api/v3/api-docs` |
| SIT | `https://novadesk-api.sit.auroraforge.co/novadesk-api/v3/api-docs` |
| Production | `https://novadesk-api.auroraforge.co/novadesk-api/v3/api-docs` |

YAML variant (append `.yaml`):

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080/novadesk-api/v3/api-docs.yaml` |
| DIT | `https://novadesk-api.dit.auroraforge.co/novadesk-api/v3/api-docs.yaml` |
| SIT | `https://novadesk-api.sit.auroraforge.co/novadesk-api/v3/api-docs.yaml` |

---

## Actuator Endpoints

Exposed endpoints: `health`, `info`, `prometheus`, `metrics`

### Health (aggregate)

Returns overall application health status.

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080/novadesk-api/actuator/health` |
| DIT | `https://novadesk-api.dit.auroraforge.co/novadesk-api/actuator/health` |
| SIT | `https://novadesk-api.sit.auroraforge.co/novadesk-api/actuator/health` |

---

### Health — Liveness Probe

Used by Docker healthcheck and Kubernetes liveness probe.  
Checks: `livenessState`, `ping`, `diskSpace`

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080/novadesk-api/actuator/health/liveness` |
| DIT | `https://novadesk-api.dit.auroraforge.co/novadesk-api/actuator/health/liveness` |
| SIT | `https://novadesk-api.sit.auroraforge.co/novadesk-api/actuator/health/liveness` |

---

### Health — Readiness Probe

Used by Traefik load balancer healthcheck.  
Checks: `readinessState`, `db`

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080/novadesk-api/actuator/health/readiness` |
| DIT | `https://novadesk-api.dit.auroraforge.co/novadesk-api/actuator/health/readiness` |
| SIT | `https://novadesk-api.sit.auroraforge.co/novadesk-api/actuator/health/readiness` |

---

### Info

Returns build metadata, git commit info, Java version, and OS details.

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080/novadesk-api/actuator/info` |
| DIT | `https://novadesk-api.dit.auroraforge.co/novadesk-api/actuator/info` |
| SIT | `https://novadesk-api.sit.auroraforge.co/novadesk-api/actuator/info` |

---

### Prometheus Metrics

Prometheus scrape endpoint — consumed by the Prometheus instance in `af-infra-devops`.

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080/novadesk-api/actuator/prometheus` |
| DIT | `https://novadesk-api.dit.auroraforge.co/novadesk-api/actuator/prometheus` |
| SIT | `https://novadesk-api.sit.auroraforge.co/novadesk-api/actuator/prometheus` |

---

### Metrics

Spring Boot metrics in JSON format.

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080/novadesk-api/actuator/metrics` |
| DIT | `https://novadesk-api.dit.auroraforge.co/novadesk-api/actuator/metrics` |
| SIT | `https://novadesk-api.sit.auroraforge.co/novadesk-api/actuator/metrics` |

Individual metric (example):

```
GET /novadesk-api/actuator/metrics/jvm.memory.used
```

---

## Summary Table

| Endpoint | Path | Purpose |
|----------|------|---------|
| Swagger UI | `/novadesk-api/swagger-ui/index.html` | Interactive API explorer |
| OpenAPI JSON | `/novadesk-api/v3/api-docs` | OpenAPI 3 spec (JSON) |
| OpenAPI YAML | `/novadesk-api/v3/api-docs.yaml` | OpenAPI 3 spec (YAML) |
| Health (all) | `/novadesk-api/actuator/health` | Aggregate health status |
| Health Liveness | `/novadesk-api/actuator/health/liveness` | Docker / K8s liveness probe |
| Health Readiness | `/novadesk-api/actuator/health/readiness` | Traefik / K8s readiness probe |
| Info | `/novadesk-api/actuator/info` | Build & git metadata |
| Prometheus | `/novadesk-api/actuator/prometheus` | Prometheus scrape target |
| Metrics | `/novadesk-api/actuator/metrics` | Spring Boot metrics (JSON) |

