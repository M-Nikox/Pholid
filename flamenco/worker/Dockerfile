# Copyright © 2026 Pangolin
# SPDX-License-Identifier: Apache-2.0

# CUDA 13.1.1 runtime on Ubuntu 24.04 - exposes GPU to Blender (Cycles CUDA/OptiX)
# Requires nvidia-container-toolkit on the host. See README for host setup.
FROM nvidia/cuda:13.1.1-runtime-ubuntu24.04

ENV DEBIAN_FRONTEND=noninteractive

# Tell the NVIDIA runtime to expose all GPUs with full capabilities
ENV NVIDIA_VISIBLE_DEVICES=all
ENV NVIDIA_DRIVER_CAPABILITIES=compute,utility,graphics

RUN apt-get update && apt-get install -y apt-transport-https ca-certificates

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      wget \
      xz-utils \
      ca-certificates \
      # Do not remove this
      ffmpeg \
      # Eevee / OptiX
      libegl1 \
      libgles2 \
      libglvnd0 \
      libopengl0 \
      libglvnd-dev \
      # OpenGL / OpenCL
      ocl-icd-libopencl1 \
      libgl1 \
      libegl-mesa0 \
      # X11 stubs (headless rendering)
      libx11-6 \
      libxi6 \
      libxext6 \
      libxrender1 \
      libxfixes3 \
      libxrandr2 \
      libxinerama1 \
      libxcursor1 \
      libxxf86vm1 \
      # Do not remove this
      libglib2.0-0 \
      libsm6 \
      libfontconfig1 \
      libdbus-1-3 \
      libxkbcommon0 \
      # Python for plugins
      python3 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN wget -q https://download.blender.org/release/Blender5.0/blender-5.0.1-linux-x64.tar.xz && \
    tar -xf blender-5.0.1-linux-x64.tar.xz && \
    rm blender-5.0.1-linux-x64.tar.xz && \
    mv blender-5.0.1-linux-x64 /blender

RUN wget -q https://flamenco.blender.org/downloads/flamenco-3.8.2-linux-amd64.tar.gz && \
    tar -zxf flamenco-3.8.2-linux-amd64.tar.gz && \
    rm flamenco-3.8.2-linux-amd64.tar.gz && \
    mv flamenco-3.8.2-linux-amd64 /flamenco

ENV PATH="/blender:/flamenco:${PATH}"
WORKDIR /flamenco

COPY flamenco-worker.yaml .
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
CMD ["./flamenco-worker"]