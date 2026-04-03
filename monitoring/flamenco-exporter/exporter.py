#!/usr/bin/env python3
"""
Flamenco Prometheus Exporter
Scrapes Flamenco Manager API and exposes metrics for Prometheus
"""

import os
import time
import logging
import random
from datetime import datetime
import requests
from prometheus_client import start_http_server, Gauge, Counter, Histogram, Info
from prometheus_client.core import GaugeMetricFamily, CounterMetricFamily, REGISTRY
from typing import Dict, List, Optional
from requests import RequestException

FLAMENCO_URL = os.getenv('FLAMENCO_API_URL', 'http://flamenco-manager:8080')
EXPORTER_PORT = int(os.getenv('EXPORTER_PORT', '9090'))
SCRAPE_INTERVAL = int(os.getenv('SCRAPE_INTERVAL', '15'))
WORKER_SCRAPE_INTERVAL = int(os.getenv('WORKER_SCRAPE_INTERVAL', '15'))
TASK_SCRAPE_INTERVAL = int(os.getenv('TASK_SCRAPE_INTERVAL', '60'))
TASK_SAMPLE_SIZE_MIN = int(os.getenv('TASK_SAMPLE_SIZE_MIN', '3'))
TASK_SAMPLE_SIZE_MAX = int(os.getenv('TASK_SAMPLE_SIZE_MAX', '5'))
MAX_JOB_COUNT_THRESHOLD = int(os.getenv('MAX_JOB_COUNT_THRESHOLD', '100'))
REQUEST_TIMEOUT = int(os.getenv('REQUEST_TIMEOUT', '10'))
PROMETHEUS_URL = os.getenv('PROMETHEUS_URL', 'http://prometheus:9090')
LOG_LEVEL = os.getenv('LOG_LEVEL', 'INFO')

logging.basicConfig(
    level=getattr(logging, LOG_LEVEL),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('flamenco-exporter')

# Prometheus Metrics

# Job
job_total = Gauge('flamenco_jobs_total', 'Total number of jobs')
job_status = Gauge('flamenco_job_status', 'Job count by status', ['status'])
job_priority = Gauge('flamenco_job_priority_distribution', 'Jobs by priority level', ['priority'])

# Worker
worker_total = Gauge('flamenco_workers_total', 'Total number of workers')
worker_status = Gauge('flamenco_worker_status', 'Worker count by status', ['status'])
worker_active = Gauge('flamenco_workers_active', 'Number of active workers')
worker_idle = Gauge('flamenco_workers_idle', 'Number of idle workers')
worker_offline = Gauge('flamenco_workers_offline', 'Number of offline workers')
# Worker resource metrics (per worker, sourced from cAdvisor via Prometheus)
worker_cpu_usage = Gauge('flamenco_worker_cpu_usage_percent', 'Worker CPU usage percentage (from cAdvisor)', ['worker_id', 'worker_name', 'worker_num'])
worker_memory_usage = Gauge('flamenco_worker_memory_usage_bytes', 'Worker memory usage in bytes (from cAdvisor)', ['worker_id', 'worker_name', 'worker_num'])
worker_last_seen = Gauge('flamenco_worker_last_seen_timestamp', 'Unix timestamp of when the worker was last seen', ['worker_id', 'worker_name', 'worker_num'])

# Task
task_total = Gauge('flamenco_tasks_total', 'Total number of tasks')
task_status = Gauge('flamenco_task_status', 'Task count by status', ['status'])
task_completion_rate = Gauge('flamenco_task_completion_rate', 'Task completion rate (completed/total)')
task_failure_rate = Gauge('flamenco_task_failure_rate', 'Task failure rate (failed/total)')
task_pipeline_sampled = Gauge('flamenco_task_pipeline_sampled', 'Tasks in pipeline (sampled)', ['status'])

# API
api_request_duration = Histogram(
    'flamenco_api_request_duration_seconds',
    'Flamenco API request duration',
    ['endpoint', 'method']
)
api_request_total = Counter('flamenco_api_requests_total', 'Total Flamenco API requests', ['endpoint', 'status'])
api_errors_total = Counter('flamenco_api_errors_total', 'Total Flamenco API errors', ['endpoint'])

# Exporter
exporter_scrape_duration = Gauge('flamenco_exporter_scrape_duration_seconds', 'Time taken to scrape Flamenco API')
exporter_last_scrape_timestamp = Gauge('flamenco_exporter_last_scrape_timestamp', 'Timestamp of last successful scrape')
task_sampling_active = Gauge('flamenco_task_sampling_active', 'Whether task sampling is currently active (1=yes, 0=no)')
task_sample_size = Gauge('flamenco_task_sample_size', 'Number of tasks sampled in last scrape')
jobs_sampled = Gauge('flamenco_jobs_sampled', 'Number of jobs sampled for task metrics')

# Worker Tag
worker_tag_count = Gauge('flamenco_worker_tag_count', 'Number of workers per tag', ['tag_name'])

# Farm Status
farm_status = Gauge('flamenco_farm_status', 'Overall farm health (1=healthy, 0=degraded)', ['status'])
farm_status_raw = Gauge('flamenco_farm_status_raw', 'Farm status as labelled gauge (always 1, use status label)', ['status'])


class FlamencoAPIClient:
    """Client for interacting with Flamenco Manager API"""
    
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        self.session.timeout = REQUEST_TIMEOUT
        
    def _make_request(self, endpoint: str, method: str = 'GET', **kwargs) -> Optional[dict]:
        """Make HTTP request to Flamenco API with metrics tracking"""
        url = f"{self.base_url}{endpoint}"
        
        try:
            start_time = time.time()
            response = self.session.request(method, url, **kwargs)
            duration = time.time() - start_time
            
            api_request_duration.labels(endpoint=endpoint, method=method).observe(duration)
            api_request_total.labels(endpoint=endpoint, status=response.status_code).inc()
            
            response.raise_for_status()
            return response.json() if response.content else {}
            
        except requests.exceptions.RequestException as e:
            logger.error(f"API request failed: {method} {endpoint} - {e}")
            api_errors_total.labels(endpoint=endpoint).inc()
            api_request_total.labels(endpoint=endpoint, status='error').inc()
            return None
    
    def get_jobs(self) -> Optional[List[dict]]:
        """Get all jobs from Flamenco"""
        data = self._make_request('/api/v3/jobs')
        return data.get('jobs', []) if data else []
    
    def get_job(self, job_id: str) -> Optional[dict]:
        """Get specific job details"""
        return self._make_request(f'/api/v3/jobs/{job_id}')
    
    def get_job_tasks(self, job_id: str) -> Optional[List[dict]]:
        """Get tasks for a specific job"""
        data = self._make_request(f'/api/v3/jobs/{job_id}/tasks')
        return data.get('tasks', []) if data else []
    
    def get_workers(self) -> Optional[List[dict]]:
        """Get all workers from Flamenco"""
        data = self._make_request('/api/v3/worker-mgt/workers')
        return data.get('workers', []) if data else []
    
    def get_worker(self, worker_id: str) -> Optional[dict]:
        """Get specific worker details"""
        return self._make_request(f'/api/v3/worker-mgt/workers/{worker_id}')
    
    def get_version(self) -> Optional[dict]:
        """Get Flamenco version"""
        return self._make_request('/api/v3/version')

    def get_farm_status(self) -> Optional[dict]:
        """Get farm status from /api/v3/status"""
        return self._make_request('/api/v3/status')

    def get_worker_tags(self) -> Optional[List[dict]]:
        """Get all worker tags"""
        data = self._make_request('/api/v3/worker-mgt/tags')
        return data.get('tags', []) if data else []


class FlamencoMetricsCollector:
    """Collects and exposes Flamenco metrics for Prometheus"""
    
    def __init__(self, api_client: FlamencoAPIClient):
        self.api = api_client
        self.last_task_scrape = 0
        self.last_worker_scrape = 0

    def _reset_worker_metrics(self):
        worker_total.set(0)
        worker_status.clear()
        worker_active.set(0)
        worker_idle.set(0)
        worker_offline.set(0)
        worker_last_seen.clear()
        worker_cpu_usage.clear()
        worker_memory_usage.clear()

    def _reset_task_metrics(self):
        task_sampling_active.set(0)
        task_sample_size.set(0)
        jobs_sampled.set(0)
        task_total.set(0)
        task_status.clear()
        task_pipeline_sampled.clear()
        task_completion_rate.set(0)
        task_failure_rate.set(0)

    def _reset_worker_tag_metrics(self):
        worker_tag_count.clear()
        
    def collect_job_metrics(self, jobs: list):
        """Collect job-related metrics from pre-fetched job list"""
        logger.info("Collecting job metrics...")

        if jobs is None:
            logger.warning("No jobs to collect metrics from")
            return
        
        # Reset metrics
        job_total.set(len(jobs))
        
        # Count by status
        status_counts = {}
        priority_counts = {'low': 0, 'normal': 0, 'high': 0, 'urgent': 0, 'requeueing': 0}
        
        for job in jobs:
            status = job.get('status', 'unknown')
            status_counts[status] = status_counts.get(status, 0) + 1
            
            # Categorize priority
            # requeueing is a transient state, counts seperate
            if status == 'requeueing':
                priority_counts['requeueing'] += 1
                continue

            priority = job.get('priority', 50)
            if priority < 25:
                priority_counts['low'] += 1
            elif priority < 60:
                priority_counts['normal'] += 1
            elif priority < 90:
                priority_counts['high'] += 1
            else:
                priority_counts['urgent'] += 1
        
        # Clear stale label series before updating prevents accumulation
        # of old statuses that no longer exist in the current scrape
        job_status.clear()
        job_priority.clear()

        # Update status metrics
        for status, count in status_counts.items():
            job_status.labels(status=status).set(count)
        
        # Update priority metrics
        for priority_level, count in priority_counts.items():
            job_priority.labels(priority=priority_level).set(count)
        
        logger.info(f"Collected metrics for {job_total._value.get()} jobs")
    
    def collect_worker_metrics(self):
        """Collect worker-related metrics"""
        now = time.time()

        # Rate limit worker scraping
        if now - self.last_worker_scrape < WORKER_SCRAPE_INTERVAL:
            return

        self.last_worker_scrape = now
        logger.info("Collecting worker metrics...")

        workers = self.api.get_workers()
        if workers is None:
            logger.warning("Failed to fetch workers")
            self._reset_worker_metrics()
            return

        if not workers:
            logger.info("No workers returned; resetting worker metrics")
            self._reset_worker_metrics()
            return
        worker_total.set(len(workers))

        status_counts = {}
        active_count = 0
        idle_count = 0
        offline_count = 0

        for worker_num, worker in enumerate(workers, start=1):
            status = worker.get('status', 'unknown')
            status_counts[status] = status_counts.get(status, 0) + 1

            # Correct Flamenco WorkerStatus enum:
            # starting, awake, asleep, error, testing, offline, restart
            if status in ('awake', 'starting', 'testing'):
                active_count += 1
            elif status in ('asleep', 'restart'):
                idle_count += 1
            elif status in ('offline', 'error'):
                offline_count += 1

            worker_id = worker.get('id', 'unknown')
            worker_name = worker.get('name', 'unknown')

            # last_seen timestamp (ISO 8601 value returned by Flamenco)
            last_seen_str = worker.get('last_seen')
            if last_seen_str:
                if isinstance(last_seen_str, str):
                    try:
                        # Flamenco returns ISO 8601 with timezone
                        dt = datetime.fromisoformat(last_seen_str.replace('Z', '+00:00'))
                        worker_last_seen.labels(
                            worker_id=worker_id,
                            worker_name=worker_name,
                            worker_num=str(worker_num)
                        ).set(dt.timestamp())
                    except ValueError as e:
                        logger.warning(f"Could not parse last_seen for worker {worker_name}: {e}")
                else:
                    logger.warning(
                        f"Invalid last_seen type for worker {worker_name}: {type(last_seen_str).__name__}"
                    )

            # CPU/memory from cAdvisor via Prometheus
            self._collect_worker_cadvisor_metrics(worker_id, worker_name, worker_num)

        # Clear stale worker status labels before updating
        worker_status.clear()

        for status, count in status_counts.items():
            worker_status.labels(status=status).set(count)

        worker_active.set(active_count)
        worker_idle.set(idle_count)
        worker_offline.set(offline_count)

        logger.info(f"Collected metrics for {len(workers)} workers")

    def _collect_worker_cadvisor_metrics(self, worker_id: str, worker_name: str, worker_num: int):
        """
        Query Prometheus for real CPU/memory via cAdvisor using Docker Compose
        service labels. Works regardless of project name prefix.
        """
        container_num = str(worker_num)
        try:
            cpu_query = (
                f'sum by (container_label_com_docker_compose_container_number) ('
                f'rate(container_cpu_usage_seconds_total{{'
                f'container_label_com_docker_compose_service="flamenco-worker",'
                f'container_label_com_docker_compose_container_number="{container_num}",'
                f'image!=""'
                f'}}[2m])) * 100'
            )
            cpu_resp = requests.get(f"{PROMETHEUS_URL}/api/v1/query", params={'query': cpu_query}, timeout=REQUEST_TIMEOUT)
            cpu_resp.raise_for_status()
            cpu_results = cpu_resp.json().get('data', {}).get('result', [])
            if cpu_results:
                worker_cpu_usage.labels(worker_id=worker_id, worker_name=worker_name, worker_num=container_num).set(float(cpu_results[0]['value'][1]))
            else:
                logger.warning(f"No cAdvisor CPU data for flamenco-worker container_number={container_num}")

            mem_query = (
                f'container_memory_usage_bytes{{'
                f'container_label_com_docker_compose_service="flamenco-worker",'
                f'container_label_com_docker_compose_container_number="{container_num}"'
                f'}}'
            )
            mem_resp = requests.get(f"{PROMETHEUS_URL}/api/v1/query", params={'query': mem_query}, timeout=REQUEST_TIMEOUT)
            mem_resp.raise_for_status()
            mem_results = mem_resp.json().get('data', {}).get('result', [])
            if mem_results:
                worker_memory_usage.labels(worker_id=worker_id, worker_name=worker_name, worker_num=container_num).set(float(mem_results[0]['value'][1]))
            else:
                logger.warning(f"No cAdvisor memory data for flamenco-worker container_number={container_num}")
        except (RequestException, ValueError, KeyError, TypeError) as e:
            logger.warning(f"Failed to fetch cAdvisor metrics for worker {worker_num}: {e}")


    def collect_task_metrics(self, active_jobs: list):
        """Collect task-related metrics with sampling (uses pre-filtered active jobs)"""
        now = time.time()

        # Rate limit task scraping
        if now - self.last_task_scrape < TASK_SCRAPE_INTERVAL:
            return

        self.last_task_scrape = now
        logger.info("Collecting task metrics (with sampling)...")

        if not active_jobs:
            logger.warning("No active jobs available for task sampling")
            self._reset_task_metrics()
            return
        
        jobs = active_jobs
        
        # Limit job count for sampling
        if len(jobs) > MAX_JOB_COUNT_THRESHOLD:
            logger.info(f"Too many jobs ({len(jobs)}), limiting to {MAX_JOB_COUNT_THRESHOLD}")
            jobs = jobs[:MAX_JOB_COUNT_THRESHOLD]
        
        # Sample jobs
        sample_size = random.randint(TASK_SAMPLE_SIZE_MIN, TASK_SAMPLE_SIZE_MAX)
        sample_size = min(sample_size, len(jobs))
        sampled_jobs = random.sample(jobs, sample_size)
        
        task_sampling_active.set(1)
        task_sample_size.set(sample_size)
        jobs_sampled.set(len(sampled_jobs))
        
        # Collect tasks from sampled jobs
        all_tasks = []
        status_counts = {}
        
        for job in sampled_jobs:
            job_id = job.get('id')
            if not job_id:
                continue
            
            tasks = self.api.get_job_tasks(job_id)
            if tasks:
                all_tasks.extend(tasks)
        
        # Count task statuses
        for task in all_tasks:
            status = task.get('status', 'unknown')
            status_counts[status] = status_counts.get(status, 0) + 1
        
        # Update metrics
        task_total.set(len(all_tasks))
        
        # Clear stale task status labels before updating
        task_status.clear()
        task_pipeline_sampled.clear()

        for status, count in status_counts.items():
            task_status.labels(status=status).set(count)
            task_pipeline_sampled.labels(status=status).set(count)
        
        # Calculate rates
        completed = status_counts.get('completed', 0)
        failed = status_counts.get('failed', 0)
        total = len(all_tasks)
        
        if total > 0:
            task_completion_rate.set(completed / total)
            task_failure_rate.set(failed / total)
        else:
            task_completion_rate.set(0)
            task_failure_rate.set(0)
        
        logger.info(f"Collected metrics for {len(all_tasks)} tasks from {len(sampled_jobs)} jobs")
    
    def collect_farm_status(self):
        """Collect real farm status from /api/v3/status"""
        HEALTHY_STATUSES = {'active', 'idle', 'starting'}

        try:
            data = self.api.get_farm_status()
            # Clear all previous label series before setting new state.
            # This prevents stale labels from accumulating in Prometheus.
            farm_status.clear()
            farm_status_raw.clear()
            if data and 'status' in data:
                status = data['status']
                is_healthy = 1 if status in HEALTHY_STATUSES else 0
                farm_status.labels(status=status).set(is_healthy)
                farm_status_raw.labels(status=status).set(1)
                logger.info(f"Farm status: {status} (healthy={bool(is_healthy)})")
            else:
                farm_status.labels(status='unknown').set(0)
                farm_status_raw.labels(status='unknown').set(1)
        except (RequestException, ValueError, KeyError, TypeError) as e:
            logger.error(f"Farm status check failed: {e}")
            farm_status.clear()
            farm_status_raw.clear()
            farm_status.labels(status='unknown').set(0)
            farm_status_raw.labels(status='unknown').set(1)
    
    def collect_worker_tag_metrics(self):
        """Collect worker tag membership counts"""
        logger.info("Collecting worker tag metrics...")

        tags = self.api.get_worker_tags()
        if not tags:
            self._reset_worker_tag_metrics()
            return

        workers = self.api.get_workers()
        if not workers:
            self._reset_worker_tag_metrics()
            return

        # Build tag_id -> tag_name map
        tag_names = {t['id']: t.get('name', 'unknown') for t in tags if 'id' in t}

        # Count workers per tag
        tag_counts = {name: 0 for name in tag_names.values()}
        for worker in workers:
            for tag in worker.get('tags', []):
                tag_id = tag.get('id')
                if tag_id in tag_names:
                    tag_counts[tag_names[tag_id]] += 1

        for tag_name, count in tag_counts.items():
            worker_tag_count.labels(tag_name=tag_name).set(count)

        logger.info(f"Collected metrics for {len(tags)} worker tags")

    def collect_all_metrics(self):
        """Collect all metrics, fetch jobs once, pass to sub-collectors"""
        start_time = time.time()

        try:
            self.collect_farm_status()

            # Fetch all jobs once for job metrics
            all_jobs = self.api.get_jobs()
            if all_jobs is None:
                all_jobs = []

            # Fetch active/queued jobs separately for task sampling
            # This avoids pulling full history for task metrics
            active_jobs = [
                j for j in all_jobs
                if j.get('status') in (
                    'active', 'queued', 'paused',
                    'pause-requested', 'cancel-requested',
                    'under-construction', 'requeueing'
                )
            ]

            self.collect_job_metrics(all_jobs)
            self.collect_worker_metrics()
            self.collect_task_metrics(active_jobs)
            self.collect_worker_tag_metrics()

            duration = time.time() - start_time
            exporter_scrape_duration.set(duration)
            exporter_last_scrape_timestamp.set(time.time())

            logger.info(f"Metrics collection completed in {duration:.2f}s")

        except (RequestException, ValueError, KeyError, TypeError, AttributeError) as e:
            logger.error(f"Error collecting metrics: {e}", exc_info=True)
            self._reset_worker_metrics()
            self._reset_task_metrics()
            self._reset_worker_tag_metrics()
            farm_status.clear()
            farm_status_raw.clear()
            farm_status.labels(status='unknown').set(0)
            farm_status_raw.labels(status='unknown').set(1)
        except Exception as e:
            logger.exception(f"Unexpected error collecting metrics: {e}")
            self._reset_worker_metrics()
            self._reset_task_metrics()
            self._reset_worker_tag_metrics()
            farm_status.clear()
            farm_status_raw.clear()
            farm_status.labels(status='unknown').set(0)
            farm_status_raw.labels(status='unknown').set(1)


def main():
    """Main exporter loop"""
    logger.info(f"Starting Flamenco Prometheus Exporter on port {EXPORTER_PORT}")
    logger.info(f"Flamenco API URL: {FLAMENCO_URL}")
    logger.info(f"Scrape interval: {SCRAPE_INTERVAL}s")
    logger.info(f"Prometheus URL: {PROMETHEUS_URL}")
    logger.info(f"Worker scrape interval: {WORKER_SCRAPE_INTERVAL}s")
    logger.info(f"Task scrape interval: {TASK_SCRAPE_INTERVAL}s")
    logger.info(f"Task sample size: {TASK_SAMPLE_SIZE_MIN}-{TASK_SAMPLE_SIZE_MAX}")
    
    # Start Prometheus HTTP server
    start_http_server(EXPORTER_PORT)
    
    # Initialize API client and collector
    api_client = FlamencoAPIClient(FLAMENCO_URL)
    collector = FlamencoMetricsCollector(api_client)
    
    logger.info("Exporter started, beginning metric collection...")
    
    # Main loop
    while True:
        try:
            collector.collect_all_metrics()
        except Exception as e:
            logger.error(f"Error in main loop: {e}", exc_info=True)
        
        time.sleep(SCRAPE_INTERVAL)


if __name__ == '__main__':
    main()
