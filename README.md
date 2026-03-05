# Pangolin

## Distributed GPU-Enabled Render Orchestrator

Pangolin is a Docker-native, stateless render orchestration platform designed for GPU-accelerated Blender workloads. It provides containerized job isolation, distributed worker coordination, and real-time observability with sub-1-minute deployment.

Built for reliability, reproducibility, and operational clarity.

---

## Stable Release (v1.0.0)

The **v1.0.0** release represents the fully hand-written, production-ready implementation of Pangolin.  
All architecture, code, and functionality in v1 were developed manually.  

This release is considered the stable baseline for the project and can be used in production environments with confidence.

---

## Experimental Prototype (v2-dev)

The **v2-dev** branch is a separate experimental prototype used to explore potential improvements to the architecture.  
It is **not production-ready** and exists solely to evaluate design ideas before any manual development begins.  

No merging into the stable master is guaranteed at this stage.

---

## Project Status

| Version | Branch      | Status      | Notes                                      |
|---------|------------|------------|--------------------------------------------|
| v1.x    | master     | Stable     | Production-ready baseline                  |
| v2      | v2-dev     | Prototype  | Architecture experimentation              |

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
