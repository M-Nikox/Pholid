# Pangolin

## Distributed GPU-Enabled Render Orchestrator

Pangolin is a Docker-native, stateless render orchestration platform designed for GPU-accelerated Blender workloads. It provides containerized job isolation, distributed worker coordination, and real-time observability with sub-1-minute deployment.

Built for reliability, reproducibility, and operational clarity.

---

## Key Features

- Stateless control plane architecture for resilience
- GPU-enabled containerized rendering (Linux + WSL2 CUDA support)
- Distributed job execution via Flamenco, extended with custom GPU-aware job definitions
- Automatic job isolation per container
- Real-time monitoring with Prometheus metrics exporters and Grafana dashboards
- Sub-60-second full environment deployment
- Designed for LAN/VLAN deployment simplicity

---

## Architecture Overview

Pangolin separates responsibilities across distinct services:

- **Control plane:** Spring Boot backend
- **Job distribution layer:** Flamenco, extended for GPU workloads
- **Containerized GPU-enabled worker nodes**
- **Observability stack:** Prometheus + Grafana, with Python-based custom exporters

Workers are disposable by design. As long as the manager and backend remain operational, job orchestration continues without manual intervention.

---

## Technology Stack

- **Docker** / **Docker Compose** - Containerization & orchestration  
- **Spring Boot** - Backend job control  
- **Python** - Custom Prometheus exporters for metrics  
- **Flamenco** - Distributed render job distribution  
- **Blender** - Rendering engine  
- **Prometheus** - Metrics collection & monitoring  
- **Grafana** - Real-time dashboards & observability

---

### GPU Job Types

- **CUDA (Linux + WSL2):** Adapted from [Flamenco community OptiX job type](https://flamenco.blender.org/third-party-jobs/cycles-optix-gpu/) (GPL v3) for CUDA rendering. Please report any issues at [Pangolin's tracker](https://github.com/M-Nikox/Pangolin/issues).

- **[OptiX (Linux)](https://flamenco.blender.org/third-party-jobs/cycles-optix-gpu/):** Community-made Flamenco job type by [Sybren Stüvel](https://projects.blender.org/dr.sybren) (GPL v3), integrated into Pangolin to provide GPU rendering for specific workflows. Please report any issues at [Flamenco’s tracker](https://projects.blender.org/studio/flamenco/issues).

---

### Have ideas for v2? [Join the discussion](https://github.com/M-Nikox/Pangolin/discussions/23)

---

## Quick Start

1. Copy `.env.example` to `.env` and set `COMPOSE_PROFILES` to your chosen deployment mode:

   | Profile | Description |
   |---|---|
   | `simple` | No auth, no Traefik — direct port access on `localhost:8080` / `localhost:3000` |
   | `local` | Auth + self-signed TLS via Traefik — `https://localhost:8443` / `:3443` / `:9443` |
   | `production` | Auth + TLS via Traefik, hostname-based routing — requires DNS / hosts file |

2. Create the required secret files (see `secrets/README.md`).

3. Run:

```bash
docker compose up -d --build
```

Deployment completes in under one minute on most systems.

Note: Initial Grafana database creation may take a short moment.

> **Switching profiles:** update `COMPOSE_PROFILES` in `.env` and re-run
> `docker compose up -d --build`. You may need to stop the old containers first
> with `docker compose down`.

## Acknowledgments

Special thanks to [yeezygambino](https://github.com/yeezygambino) for consistently testing releases and providing valuable feedback.

---

## Troubleshooting

### Authentik fails to connect to Postgres 18 (SCRAM-SHA-256 auth error)

PostgreSQL 18 uses `scram-sha-256` as its default password authentication method. If
Authentik was set up against an older Postgres version where the user password was
hashed with `md5`, the authentication will fail after an upgrade.

**Symptoms:**

- Authentik server or worker logs show:  
  `django.db.utils.OperationalError: FATAL: password authentication failed for user "..."`
- Or: `fe_sendauth: no password supplied`

**Verify the hash method in use:**

```bash
docker compose exec postgres psql -U postgres -c \
  "SELECT rolname, rolpassword FROM pg_authid WHERE rolname = 'pangolin';"
```

A password starting with `md5` means the old hash is still in use.

**Fix — re-hash the password with SCRAM-SHA-256:**

```bash
docker compose exec postgres psql -U postgres -c \
  "ALTER ROLE pangolin WITH PASSWORD '$(cat secrets/postgres_password.txt)';"
```

> Replace `pangolin` with your actual `POSTGRES_USER` value if you customized it.

After running the command, restart the Authentik services:

```bash
docker compose restart authentik-server authentik-worker        # production
# or
docker compose restart authentik-server-local authentik-worker-local  # local
```

**Verify SCRAM-SHA-256 is now active:**

```bash
docker compose exec postgres psql -U postgres -c \
  "SELECT rolname, rolpassword FROM pg_authid WHERE rolname = 'pangolin';"
```

The password value should now start with `SCRAM-SHA-256$`.

**Prevent the issue on fresh installs:**

Postgres 18 creates new users with SCRAM-SHA-256 by default, so this issue only
affects databases migrated from Postgres 14–16. For new deployments the hash method
is already correct.