<p align="center">
  <img src="assets\pholid-readme.svg" width="300" alt="Pholid Logo">
</p>

<h1 align="center">Pholid</h1>
<h3 align="center">Distributed GPU-Accelerated Render Orchestrator</h3>

<p align="center">
  <a href="https://github.com/M-Nikox/Pholid/releases"><img src="https://img.shields.io/github/v/release/M-Nikox/Pholid?label=stable&color=4c9a2a" alt="Stable Release"></a>
  <a href="https://github.com/M-Nikox/Pholid/actions"><img src="https://github.com/M-Nikox/Pholid/actions/workflows/ci.yml/badge.svg?branch=master" alt="Tests"></a>
  <img src="https://img.shields.io/badge/license-Apache%202.0-blue" alt="License">
  <img src="https://img.shields.io/badge/blender-GPU%20rendering-e87d0d" alt="Blender">
  <img src="https://img.shields.io/badge/docker-compose-2496ED?logo=docker&logoColor=white" alt="Docker">
</p>

---

Pholid is a Docker-native render orchestration platform for GPU-accelerated Blender workloads. It handles job submission, GPU worker coordination, and real-time observability in a single deployable stack.

Free and open source, forever.

---

## Quick Start

```bash
cp .env.example .env   # fill in your values
docker compose up -d --build
```

Deployment completes in under one minute.

| Service | URL |
|---------|-----|
| Pholid | `http://localhost` |
| Flamenco manager | `http://localhost:88` |
| Grafana | `http://localhost:3000` |
| Prometheus | `http://localhost:9090` |

> Ports are configurable in `.env`. Initial Grafana database creation may take a short moment on first boot.

---

## Architecture

Pholid separates responsibilities cleanly across distinct services:

- **Control plane** - Spring Boot backend handles job submission and the UI
- **Job distribution** - Flamenco, extended with custom GPU-aware job types for CUDA and OptiX workflows
- **Worker nodes** - Disposable containerised Blender workers. As long as the manager stays up, job orchestration continues without intervention.
- **Observability** - Prometheus + Grafana with custom Python exporters for Flamenco job metrics and NVIDIA GPU metrics

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Containerisation | Docker, Docker Compose |
| Backend | Spring Boot |
| Rendering | Blender, Flamenco |
| Observability | Prometheus, Grafana, cAdvisor, custom Python exporters |
| GPU | NVIDIA CUDA / OptiX (Linux + WSL2) |

---

## GPU Job Types

- **CUDA (Linux + WSL2):** Adapted from the [Flamenco community OptiX job type](https://flamenco.blender.org/third-party-jobs/cycles-optix-gpu/) (GPL v3) for CUDA rendering. Issues → [Pholid tracker](https://github.com/M-Nikox/Pholid/issues).

- **OptiX (Linux):** Original community contribution by [Sybren Stüvel](https://projects.blender.org/dr.sybren) (GPL v3), integrated into Pholid. Issues → [Flamenco tracker](https://projects.blender.org/studio/flamenco/issues).

---

## Prerequisites

- Docker Desktop (Windows/macOS) or Docker Engine (Linux)
- NVIDIA GPU with drivers installed
- NVIDIA Container Toolkit (`nvidia-docker2`) for GPU workers
- WSL2 with GPU passthrough enabled (Windows only)

---

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.  
Have ideas for v2? [Join the discussion](https://github.com/M-Nikox/Pholid/discussions/23).

---

## License

Licensed under the [Apache License 2.0](LICENSE).  
See [THIRD_PARTY_SOFTWARE.md](THIRD_PARTY_SOFTWARE.md) for third-party licenses.

---

## Acknowledgments

Special thanks to [yeezygambino](https://github.com/yeezygambino) for consistently testing releases and providing valuable feedback.

---

> Looking for multi-user auth and HTTPS support? See the [v2 branch](https://github.com/M-Nikox/Pholid/tree/v2-dev).