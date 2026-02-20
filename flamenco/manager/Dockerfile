# Copyright © 2026 Pangolin
# SPDX-License-Identifier: Apache-2.0

FROM alpine:3.23.3

RUN wget -q https://flamenco.blender.org/downloads/flamenco-3.8.2-linux-amd64.tar.gz && \
    tar zxf flamenco-3.8.2-linux-amd64.tar.gz && \
    rm flamenco-3.8.2-linux-amd64.tar.gz

WORKDIR /flamenco-3.8.2-linux-amd64

COPY flamenco-manager.yaml .

EXPOSE 8080

CMD ["./flamenco-manager"]