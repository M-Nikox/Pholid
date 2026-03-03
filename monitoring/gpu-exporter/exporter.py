#!/usr/bin/env python3
"""
Pangolin GPU Prometheus Exporter
Queries nvidia-smi and exposes GPU metrics for Prometheus.
Supports multiple GPUs via the gpu_index label.
"""

import os
import time
import logging
import subprocess
from prometheus_client import start_http_server, Gauge

EXPORTER_PORT  = int(os.getenv('EXPORTER_PORT', '9091'))
SCRAPE_INTERVAL = int(os.getenv('SCRAPE_INTERVAL', '5'))
LOG_LEVEL       = os.getenv('LOG_LEVEL', 'INFO')

logging.basicConfig(
    level=getattr(logging, LOG_LEVEL),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('gpu-exporter')

# ── Prometheus metrics ──────────────────────────────────────────────────────
LABELS = ['gpu_index', 'gpu_name']

gpu_utilization   = Gauge('pangolin_gpu_utilization_percent',    'GPU core utilisation (%)',       LABELS)
gpu_mem_used      = Gauge('pangolin_gpu_memory_used_bytes',      'GPU VRAM used (bytes)',          LABELS)
gpu_mem_total     = Gauge('pangolin_gpu_memory_total_bytes',     'GPU VRAM total (bytes)',         LABELS)
gpu_mem_pct       = Gauge('pangolin_gpu_memory_used_percent',    'GPU VRAM used (%)',              LABELS)
gpu_temperature   = Gauge('pangolin_gpu_temperature_celsius',    'GPU temperature (°C)',           LABELS)
gpu_power_draw    = Gauge('pangolin_gpu_power_draw_watts',       'GPU power draw (W)',             LABELS)
gpu_power_limit   = Gauge('pangolin_gpu_power_limit_watts',      'GPU power limit (W)',            LABELS)
gpu_fan_speed     = Gauge('pangolin_gpu_fan_speed_percent',      'GPU fan speed (%)',              LABELS)
gpu_sm_clock      = Gauge('pangolin_gpu_sm_clock_mhz',          'GPU SM clock (MHz)',             LABELS)
gpu_mem_clock     = Gauge('pangolin_gpu_mem_clock_mhz',         'GPU memory clock (MHz)',         LABELS)
gpu_scrape_ok     = Gauge('pangolin_gpu_scrape_ok',             '1 if last nvidia-smi scrape succeeded')

# ── nvidia-smi query ────────────────────────────────────────────────────────
QUERY_FIELDS = [
    'index',
    'name',
    'utilization.gpu',
    'memory.used',
    'memory.total',
    'temperature.gpu',
    'power.draw',
    'power.limit',
    'fan.speed',
    'clocks.sm',
    'clocks.mem',
]

def query_nvidia_smi():
    """Run nvidia-smi and return a list of dicts, one per GPU."""
    try:
        result = subprocess.run(
            [
                'nvidia-smi',
                f'--query-gpu={",".join(QUERY_FIELDS)}',
                '--format=csv,noheader,nounits',
            ],
            capture_output=True, text=True, timeout=10
        )
        if result.returncode != 0:
            logger.error('nvidia-smi exited %d: %s', result.returncode, result.stderr.strip())
            return None

        gpus = []
        for line in result.stdout.strip().splitlines():
            values = [v.strip() for v in line.split(',')]
            if len(values) != len(QUERY_FIELDS):
                logger.warning('Unexpected field count: %s', line)
                continue
            gpus.append(dict(zip(QUERY_FIELDS, values)))
        return gpus

    except FileNotFoundError:
        logger.error('nvidia-smi not found, is the NVIDIA runtime available?')
        return None
    except subprocess.TimeoutExpired:
        logger.error('nvidia-smi timed out')
        return None

def safe_float(value, field):
    """Parse a float, returning None for N/A or unparseable values."""
    if value in ('N/A', '[N/A]', '', 'ERR'):
        return None
    try:
        return float(value)
    except ValueError:
        logger.debug('Could not parse %s=%r as float', field, value)
        return None

def collect():
    gpus = query_nvidia_smi()
    if gpus is None:
        gpu_scrape_ok.set(0)
        return

    gpu_scrape_ok.set(1)

    for gpu in gpus:
        idx  = gpu.get('index', '0')
        name = gpu.get('name', 'unknown')
        labels = [idx, name]

        util  = safe_float(gpu.get('utilization.gpu'),  'utilization.gpu')
        m_used = safe_float(gpu.get('memory.used'),     'memory.used')
        m_tot  = safe_float(gpu.get('memory.total'),    'memory.total')
        temp   = safe_float(gpu.get('temperature.gpu'), 'temperature.gpu')
        pwr    = safe_float(gpu.get('power.draw'),      'power.draw')
        plimit = safe_float(gpu.get('power.limit'),     'power.limit')
        fan    = safe_float(gpu.get('fan.speed'),       'fan.speed')
        sm_clk = safe_float(gpu.get('clocks.sm'),       'clocks.sm')
        m_clk  = safe_float(gpu.get('clocks.mem'),      'clocks.mem')

        if util  is not None: gpu_utilization.labels(*labels).set(util)
        if temp  is not None: gpu_temperature.labels(*labels).set(temp)
        if fan   is not None: gpu_fan_speed.labels(*labels).set(fan)
        if sm_clk is not None: gpu_sm_clock.labels(*labels).set(sm_clk)
        if m_clk is not None: gpu_mem_clock.labels(*labels).set(m_clk)

        # Memory - nvidia-smi reports in MiB, convert to bytes for consistency
        if m_used is not None:
            gpu_mem_used.labels(*labels).set(m_used * 1024 * 1024)
        if m_tot is not None:
            gpu_mem_total.labels(*labels).set(m_tot * 1024 * 1024)
        if m_used is not None and m_tot is not None and m_tot > 0:
            gpu_mem_pct.labels(*labels).set(round(m_used / m_tot * 100, 1))

        # Power - reported as "XX.YY W" sometimes; safe_float handles it
        if pwr    is not None: gpu_power_draw.labels(*labels).set(pwr)
        if plimit is not None: gpu_power_limit.labels(*labels).set(plimit)

        logger.debug(
            'GPU %s (%s): util=%s%% mem=%s/%s MiB temp=%s°C power=%sW',
            idx, name, util, m_used, m_tot, temp, pwr
        )

# ── Main ────────────────────────────────────────────────────────────────────
if __name__ == '__main__':
    logger.info('Pangolin GPU Exporter starting on port %d', EXPORTER_PORT)
    start_http_server(EXPORTER_PORT)
    logger.info('Scraping nvidia-smi every %ds', SCRAPE_INTERVAL)

    while True:
        collect()
        time.sleep(SCRAPE_INTERVAL)
