#!/bin/sh
# Pangolin Worker Entrypoint
# Detects the runtime environment and configures NVIDIA/OptiX library paths
# accordingly before handing off to the Flamenco worker.
#
# Supported environments:
#   - Windows + Docker Desktop (WSL2 backend)
#   - Native Linux + Docker Engine

WSL_LIB="/usr/lib/wsl/lib"

if [ -f "${WSL_LIB}/libnvoptix.so.1" ]; then
    # WSL2 environment - OptiX lives in the Windows driver directory,
    # exposed into the container via the bind mount in docker-compose.yml.
    # Add it to the dynamic linker search path so Blender can find it.
    echo "[Pangolin] WSL2 detected, adding ${WSL_LIB} to library path"
    export LD_LIBRARY_PATH="${WSL_LIB}:${LD_LIBRARY_PATH}"
else
    # Native Linux - NVIDIA container toolkit injects libraries into the
    # standard system paths automatically. Nothing extra needed.
    echo "[Pangolin] Native Linux detected, using system NVIDIA libraries"
fi

exec "$@"
