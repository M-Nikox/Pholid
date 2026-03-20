<p align="center">
  <img src="pangolin-backend/src/main/resources/static/svg/pangolin-readme.svg" width="180" alt="Pangolin Logo">
</p>

<h1 align="center">Pangolin</h1>
<h3 align="center">Distributed GPU-Accelerated Render Orchestrator</h3>

<p align="center">
  <a href="https://github.com/M-Nikox/Pangolin/releases"><img src="https://img.shields.io/github/v/release/M-Nikox/Pangolin?label=stable&color=4c9a2a" alt="Stable Release"></a>
  <a href="https://github.com/M-Nikox/Pangolin/tree/v2"><img src="https://img.shields.io/badge/v2--dev-in%20development-orange" alt="v2 Dev"></a>
  <a href="https://github.com/M-Nikox/Pangolin/actions"><img src="https://github.com/M-Nikox/Pangolin/actions/workflows/<your-workflow>.yml/badge.svg?branch=v2" alt="Tests"></a>
  <img src="https://img.shields.io/badge/license-Apache%202.0-blue" alt="License">
  <img src="https://img.shields.io/badge/blender-GPU%20rendering-e87d0d" alt="Blender">
  <img src="https://img.shields.io/badge/docker-compose-2496ED?logo=docker&logoColor=white" alt="Docker">
</p>

---

Pangolin is a Docker-native render orchestration platform for GPU-accelerated Blender workloads. It handles multi-user job submission, GPU worker coordination, and real-time observability in a single deployable stack.

Free and open source, forever.

---

## Stable Release — v1.x

The v1.x release is a fully hand-written, production-ready single-user render farm manager. No authentication, no dependencies beyond Docker, just deploy and render.

```bash
git checkout master
cp .env.example .env   # fill in your values
docker compose up -d --build
```

Deployment completes in under one minute. Pangolin will be available at `http://localhost:8080`.

> Initial Grafana database creation may take a short moment on first boot.

---

## v2 — Multi-User Auth (In Development)

v2 introduces full multi-user support via Keycloak SSO, per-user job isolation, quota enforcement, avatar profiles, and a complete Prometheus + Grafana observability stack.

**No v2 release will be made until the implementation is fully tested and stable.**

### Current State

| Mode | Status | Notes |
|------|--------|-------|
| HTTP dev mode | ✅ Working | Full auth stack functional, see quick start below |
| HTTPS prod mode | 🚧 In testing | Traefik + TLS, not yet validated end-to-end |

### What's working in v2 dev mode

- Keycloak login and logout with session management
- Group-based admin detection (`pangolin-admins`)
- User profiles - avatar upload, full name and email from OIDC token
- Notification preferences
- Job submission with per-user filtering and quota enforcement
- Paginated job history
- Admin panel with audit log and farm status
- Full Prometheus + Grafana observability stack

### v2 Quick Start (HTTP dev mode)

```bash
git checkout v2
cp .env.example .env   # fill in your values
docker compose -f docker-compose.dev.yml up -d postgresql keycloak
```

Wait ~90 seconds for Keycloak to be healthy, then complete the Keycloak setup:

📖 **[KEYCLOAK_SETUP.md](KEYCLOAK_SETUP.md)**

Once setup is complete:

```bash
docker compose -f docker-compose.dev.yml up -d
```

| Service | URL |
|---------|-----|
| Pangolin | `http://localhost:8080` |
| Keycloak | `http://localhost:8180` |
| Grafana | `http://localhost:3000` |
| Flamenco | `http://localhost:8280` |
| Prometheus | `http://localhost:9090` |

---

## Architecture

Pangolin separates responsibilities cleanly across distinct services:

- **Control plane** - Spring Boot backend handles job submission, quota enforcement, user context, and the UI
- **Job distribution** - Flamenco, extended with custom GPU-aware job types for CUDA and OptiX workflows
- **Worker nodes** - Disposable containerised Blender workers. As long as the manager stays up, job orchestration continues without intervention.
- **Identity** - Keycloak handles SSO, group membership, and token issuance (v2+)
- **Observability** - Prometheus + Grafana with custom Python exporters for Flamenco job metrics and NVIDIA GPU metrics

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Containerisation | Docker, Docker Compose |
| Backend | Spring Boot 4, Spring Security OAuth2, Thymeleaf, JPA, Flyway |
| Database | PostgreSQL |
| Identity | Keycloak 26 |
| Rendering | Blender, Flamenco |
| Reverse proxy | Traefik v3 |
| Observability | Prometheus, Grafana, cAdvisor, custom Python exporters |
| GPU | NVIDIA CUDA / OptiX (Linux + WSL2) |

---

## GPU Job Types

- **CUDA (Linux + WSL2):** Adapted from the [Flamenco community OptiX job type](https://flamenco.blender.org/third-party-jobs/cycles-optix-gpu/) (GPL v3) for CUDA rendering. Issues → [Pangolin tracker](https://github.com/M-Nikox/Pangolin/issues).

- **OptiX (Linux):** Original community contribution by [Sybren Stüvel](https://projects.blender.org/dr.sybren) (GPL v3), integrated into Pangolin. Issues → [Flamenco tracker](https://projects.blender.org/studio/flamenco/issues).

---

## Prerequisites

- Docker Desktop (Windows/macOS) or Docker Engine (Linux)
- NVIDIA GPU with drivers installed
- NVIDIA Container Toolkit (`nvidia-docker2`) for GPU workers
- WSL2 with GPU passthrough enabled (Windows only)

---

## Project Status

| Version | Branch | Status | Notes |
|---------|--------|--------|-------|
| v1.x | `master` | ✅ Stable | Production-ready, no-auth single-user |
| v2 | `v2` | 🚧 In development | Multi-user auth, HTTPS testing in progress |

---

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.  
Have ideas for v2? [Join the discussion](https://github.com/M-Nikox/Pangolin/discussions/23).

---

## License

Licensed under the [Apache License 2.0](LICENSE).  
See [THIRD_PARTY_SOFTWARE.md](THIRD_PARTY_SOFTWARE.md) for third-party licenses.

---

## Acknowledgments

Special thanks to [yeezygambino](https://github.com/yeezygambino) for consistently testing releases and providing valuable feedback.