# Copyright © 2026 Pangolin
# SPDX-License-Identifier: Apache-2.0
#
# GPU metrics exporter — requires NVIDIA container runtime on the host.
# Uses the CUDA runtime image so nvidia-smi is available inside the container.
FROM nvidia/cuda:13.1.1-runtime-ubuntu24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV NVIDIA_VISIBLE_DEVICES=all
ENV NVIDIA_DRIVER_CAPABILITIES=compute,utility

RUN apt-get update && apt-get install -y --no-install-recommends \
      python3 \
      python3-pip \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip3 install --no-cache-dir --break-system-packages -r requirements.txt

COPY exporter.py .

EXPOSE 9091

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD python3 -c "import urllib.request; urllib.request.urlopen('http://localhost:9091').read()"

CMD ["python3", "-u", "exporter.py"]
