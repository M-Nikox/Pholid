/**
 * Pangolin Render Manager - Active Sessions Module (CORRECT VERSION)
 * For the NEW orange-themed Pangolin design with Tailwind
 */

const activeSessions = (() => {
    let pollInterval = null;
    let activeJobs = [];
    const POLL_INTERVAL_MS = 3000;
    const PAGE_SIZE = 50;
    let previousJobsOffset = 0;
    let previousJobsHasMore = false;

    function init() {
        console.log('Active Sessions module initializing...');
        fetchAndDisplayActiveJobs();
        fetchAndDisplayPreviousJobs();
        startPolling();
        
        // Listen for new job submissions
        document.addEventListener('pangolin:jobSubmitted', handleNewJobSubmitted);

        // When a job completes (notification fired), refresh history
        document.addEventListener('pangolin:renderComplete', () => {
            fetchAndDisplayActiveJobs();
            fetchAndDisplayPreviousJobs();
        });
    }

    function startPolling() {
        if (pollInterval) clearInterval(pollInterval);
        pollInterval = setInterval(() => {
            fetchAndDisplayActiveJobs();
        }, POLL_INTERVAL_MS);
        console.log('✅ Active sessions polling started');
    }

    function stopPolling() {
        if (pollInterval) {
            clearInterval(pollInterval);
            pollInterval = null;
            console.log('⏹️ Active sessions polling stopped');
        }
    }

    async function fetchAndDisplayActiveJobs() {
        try {
            const response = await fetch('/api/jobs/active');
            
            if (!response.ok) {
                console.error('Failed to fetch active jobs:', response.status);
                showPanelError('active-sessions-list', 'Could not reach the farm. Retrying...');
                return;
            }
            
            const data = await response.json();
            const allJobs = data.jobs || [];
            const activeStatuses = new Set(['active','queued','paused','pause-requested','cancel-requested','under-construction','requeueing']);
            activeJobs = allJobs.filter(j => activeStatuses.has(j.status));

            if (allJobs.length !== activeJobs.length) {
                console.log(`📊 Filtered ${allJobs.length - activeJobs.length} terminal job(s) from active panel`);
            }
            console.log(`📊 Fetched ${activeJobs.length} active job(s)`);
            
            renderActiveJobs(activeJobs);

            // Pause polling when nothing is active — resume on next submission
            if (activeJobs.length === 0 && pollInterval) {
                stopPolling();
            }
            
        } catch (error) {
            console.error('❌ Error fetching active jobs:', error);
            showPanelError('active-sessions-list', 'Could not reach the farm. Retrying...');
        }
    }

    async function fetchAndDisplayPreviousJobs() {
        // Reset pagination state on fresh load
        previousJobsOffset = 0;
        previousJobsHasMore = false;
        updateLoadMoreButton();

        try {
            const response = await fetch(`/api/jobs/previous?limit=${PAGE_SIZE}&offset=0`);

            if (!response.ok) {
                console.error('Failed to fetch previous jobs:', response.status);
                showPanelError('history-sessions-list', 'Could not load history. Retrying...');
                return;
            }

            const data = await response.json();
            const jobs = data.jobs || [];

            console.log(`📦 Fetched ${jobs.length} previous job(s)`);

            previousJobsHasMore = jobs.length === PAGE_SIZE;
            previousJobsOffset = jobs.length;
            updateLoadMoreButton();

            renderPreviousJobs(jobs);

        } catch (error) {
            console.error('❌ Error fetching previous jobs:', error);
            showPanelError('history-sessions-list', 'Could not load history. Retrying...');
        }
    }

    function showPanelError(containerId, message) {
        const container = document.getElementById(containerId);
        if (!container) return;
        container.innerHTML = `
            <div class="py-10 text-center text-[10px] font-bold uppercase tracking-widest opacity-40 text-red-400">
                ⚠ ${message}
            </div>
        `;
    }

    function renderPreviousJobs(jobs) {
        const container = document.getElementById('history-sessions-list');
        if (!container) return;

        if (jobs.length === 0) {
            container.innerHTML = `
                <div class="py-10 text-center opacity-20 text-[10px] font-bold uppercase tracking-widest">
                    Empty Archive
                </div>
            `;
            return;
        }

        container.innerHTML = jobs.map((job, index) => {
            const displayName = (job.name || 'Untitled').replace(/^Pangolin_/, '');
            const statusColor = job.status === 'completed' ? '#22c55e' : job.status === 'failed' ? '#ef4444' : job.status === 'canceled' ? '#f97316' : '#6b7280';
            const statusLabel = job.status === 'completed' ? 'Completed' : job.status === 'failed' ? 'Failed' : job.status === 'canceled' ? 'Canceled' : job.status;
            const pangolinId = job.metadata ? (job.metadata['pangolin.job_id'] || job.id) : job.id;
            const staggerDelay = (index * 18) + 'ms';
            const downloadBtn = job.status === 'completed'
                ? '<a href="/download/' + pangolinId + '" class="text-xs font-semibold px-3 py-1.5 rounded-lg border transition-all" style="background:rgba(34,197,94,0.1);color:#22c55e;border-color:rgba(34,197,94,0.3);" onclick="event.stopPropagation()">Download</a>'
                : '';
            return '<div class="history-item history-item-enter px-4 py-3 glass-input border-none flex items-center justify-between group rounded-xl cursor-pointer" style="animation-delay:' + staggerDelay + '" data-flamenco-id="' + job.id + '" data-job-name="' + displayName + '" onclick="activeSessions.openLogModal(this, event)">'
                + '<div class="flex items-center gap-3 min-w-0">'
                +   '<div class="w-2 h-2 rounded-full flex-shrink-0" style="background:' + statusColor + ';"></div>'
                +   '<div class="min-w-0">'
                +     '<p class="text-sm font-semibold truncate" title="' + displayName + '">' + displayName + '</p>'
                +     '<p class="text-xs opacity-50 font-mono mt-0.5">' + pangolinId + '</p>'
                +     '<p class="text-xs mt-0.5" style="color:' + statusColor + ';opacity:0.8;">' + statusLabel + '</p>'
                +   '</div>'
                + '</div>'
                + '<div class="flex items-center gap-2 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-all ml-3">'
                +   downloadBtn
                +   '<button class="delete-job-btn text-xs font-semibold px-3 py-1.5 rounded-lg border transition-all" style="background:rgba(239,68,68,0.1);color:#ef4444;border-color:rgba(239,68,68,0.3);" data-job-id="' + job.id + '" data-confirm="false" onclick="event.stopPropagation()">Delete</button>'
                + '</div>'
                + '</div>';
        }).join('');
        // Wire up delete buttons via event delegation
        container.querySelectorAll('.delete-job-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                handleDeleteClick(btn);
            });
        });
    }

    function renderActiveJobs(jobs) {
        const container = document.getElementById('active-sessions-list');
        const countBadge = document.getElementById('active-count');
        
        if (!container) {
            console.error('Container #active-sessions-list not found!');
            return;
        }

        if (countBadge) countBadge.textContent = jobs.length;
        
        container.innerHTML = '';
        
        if (jobs.length === 0) {
            container.innerHTML = `
                <div class="flex flex-col items-center justify-center py-12 px-4 text-center">
                    <div class="text-6xl mb-4 opacity-50">💤</div>
                    <p class="text-lg font-semibold mb-2" style="color: var(--text-primary);">No active renders</p>
                    <p class="text-sm" style="color: var(--text-secondary);">Submit a job to get started!</p>
                </div>
            `;
            return;
        }
        
        jobs.forEach(job => {
            const card = createJobCard(job);
            container.appendChild(card);
        });
    }

    function createJobCard(job) {
        const card = document.createElement('div');
        card.className = `glass-card job-card job-card-enter status-${job.status} rounded-2xl p-5 mb-3`;
        card.dataset.jobId = job.id;

        const progress = job.steps_total > 0 
            ? Math.round((job.steps_completed / job.steps_total) * 100) 
            : 0;

        const statusInfo = getStatusInfo(job.status);
        const displayName = job.name.replace(/^Pangolin_/, '');
        const eta = calculateETA(job);

        card.innerHTML = `
            <!-- Header: Icon + Name -->
            <div class="flex items-center gap-3 mb-3">
                <div class="text-2xl">${statusInfo.icon}</div>
                <div class="flex-1 min-w-0">
                    <h3 class="font-bold text-base truncate" style="color: var(--text-primary);" title="${displayName}">
                        ${displayName}
                    </h3>
                    <p class="text-xs italic" style="color: var(--text-secondary);">
                        ${job.activity || statusInfo.text}
                    </p>
                </div>
            </div>

            <!-- Progress Bar -->
            <div class="mb-3">
                <div class="h-2 rounded-full overflow-hidden" style="background: var(--bg-secondary);">
                    <div class="h-full bg-gradient-to-r from-pangolin-orange to-orange-400 transition-all duration-500 relative"
                        style="width: ${progress}%">
                        <div class="absolute inset-0 bg-gradient-to-r from-transparent via-white/30 to-transparent animate-shimmer"></div>
                    </div>
                </div>
                <div class="flex justify-between items-center mt-1">
                    <span class="text-xs" style="color: var(--text-secondary);">
                        ${job.steps_completed} / ${job.steps_total} frames
                    </span>
                    <span class="text-xs font-semibold text-pangolin-orange">
                        ${progress}%
                    </span>
                </div>
            </div>

            <!-- ETA -->
            ${eta ? `
                <div class="text-sm mb-3 text-pangolin-orange font-medium">
                    ⏱️ ${eta}
                </div>
            ` : ''}

            <!-- Actions -->
            <div class="flex gap-2">
                ${createActionButtons(job)}
            </div>
        `;

        // Wire cancel buttons with 2-click inline confirmation
        card.querySelectorAll('.cancel-job-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                handleCancelClick(btn);
            });
        });

        return card;
    }

    function getStatusInfo(status) {
        const statusMap = {
            'active': { icon: '🟢', text: 'Rendering' },
            'queued': { icon: '🕐', text: 'Queued' },
            'paused': { icon: '⏸️', text: 'Paused' },
            'pause-requested': { icon: '⏸️', text: 'Pausing...' },
            'cancel-requested': { icon: '⏹️', text: 'Cancelling...' },
            'under-construction': { icon: '🔨', text: 'Setting up...' }
        };
        return statusMap[status] || { icon: '❓', text: status };
    }

    function calculateETA(job) {
        if (job.status !== 'active') return null;
        if (job.steps_completed === 0) return 'Calculating...';
        if (job.steps_completed >= job.steps_total) return null;
        
        const created = new Date(job.created);
        const now = new Date();
        const elapsed = now - created;
        const timePerFrame = elapsed / job.steps_completed;
        const remainingFrames = job.steps_total - job.steps_completed;
        const remainingMs = timePerFrame * remainingFrames;
        
        return formatDuration(remainingMs);
    }

    function formatDuration(ms) {
        const seconds = Math.floor(ms / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);
        
        if (hours > 0) {
            const m = minutes % 60;
            return `~${hours}h ${m}m`;
        } else if (minutes > 0) {
            const s = seconds % 60;
            return `~${minutes}m ${s}s`;
        } else {
            return `~${seconds}s`;
        }
    }

    function createActionButtons(job) {
        const buttons = [];
        
        // Download button
        if (job.status === 'active' || job.steps_completed > 0) {
            const pangolinId = job.metadata?.['pangolin.job_id'];
            const isPartial = job.status === 'active';
            buttons.push(`
                <button class="flex-1 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold py-2 px-4 rounded-lg text-sm transition-all hover:-translate-y-0.5"
                        onclick="window.location.href='/download/${pangolinId}'"
                        title="${isPartial ? 'Job is still rendering — download will contain completed frames only' : 'Download all rendered frames'}">
                    📦 ${isPartial ? 'Download (partial)' : 'Download'}
                </button>
            `);
        }
        
        // Cancel button
        if (!['cancel-requested', 'canceled', 'completed', 'failed'].includes(job.status)) {
            buttons.push(`
                <button class="cancel-job-btn px-4 py-2 rounded-lg text-sm font-semibold transition-all border"
                        style="background: rgba(239, 68, 68, 0.1); color: #ef4444; border-color: rgba(239, 68, 68, 0.3);"
                        data-job-id="${job.id}"
                        data-confirm="false">
                    ✕ Cancel
                </button>
            `);
        }
        
        return buttons.join('');
    }

    function handleCancelClick(btn) {
        const jobId = btn.dataset.jobId;
        const isConfirming = btn.dataset.confirm === 'true';

        if (!isConfirming) {
            btn.dataset.confirm = 'true';
            btn.textContent = 'Sure?';
            btn.style.background = 'rgba(239,68,68,0.25)';
            btn.style.borderColor = 'rgba(239,68,68,0.7)';
            btn._revertTimer = setTimeout(() => resetCancelBtn(btn), 3000);
        } else {
            clearTimeout(btn._revertTimer);
            btn.disabled = true;
            btn.textContent = '...';
            cancelJob(jobId, btn);
        }
    }

    function resetCancelBtn(btn) {
        if (!btn) return;
        btn.disabled = false;
        btn.dataset.confirm = 'false';
        btn.textContent = '✕ Cancel';
        btn.style.background = 'rgba(239,68,68,0.1)';
        btn.style.borderColor = 'rgba(239,68,68,0.3)';
    }

    async function cancelJob(jobId, btn) {
        try {
            const response = await fetch(`/api/jobs/${jobId}/cancel`, {
                method: 'POST'
            });

            const data = await response.json();

            if (response.ok) {
                console.log(`✅ Cancel requested for job ${jobId}`);
                fetchAndDisplayActiveJobs();
            } else if (response.status === 422) {
                if (btn) {
                    btn.textContent = "Can't cancel";
                    setTimeout(() => resetCancelBtn(btn), 2000);
                }
            } else {
                console.error('Cancel failed:', data);
                if (btn) {
                    btn.textContent = 'Failed';
                    setTimeout(() => resetCancelBtn(btn), 2000);
                }
            }
        } catch (e) {
            console.error('Cancel request error:', e);
            if (btn) {
                btn.textContent = 'Error';
                setTimeout(() => resetCancelBtn(btn), 2000);
            }
        }
    }

    function handleNewJobSubmitted() {
        console.log('🎬 New job submitted, refreshing sessions');
        fetchAndDisplayActiveJobs();
        fetchAndDisplayPreviousJobs();
        
        if (!pollInterval) {
            startPolling();
        }
    }

    // Change 12: Two-click inline confirmation for delete
    function handleDeleteClick(btn) {
        const jobId = btn.dataset.jobId;
        const isConfirming = btn.dataset.confirm === 'true';

        if (!isConfirming) {
            // First click — enter confirm state
            btn.dataset.confirm = 'true';
            btn.textContent = 'Confirm?';
            btn.style.background = 'rgba(239,68,68,0.25)';
            btn.style.borderColor = 'rgba(239,68,68,0.7)';

            // Auto-revert after 3s
            btn._revertTimer = setTimeout(() => {
                btn.dataset.confirm = 'false';
                btn.textContent = '🗑';
                btn.style.background = 'rgba(239,68,68,0.1)';
                btn.style.borderColor = 'rgba(239,68,68,0.3)';
            }, 3000);
        } else {
            // Second click — execute delete
            clearTimeout(btn._revertTimer);
            btn.disabled = true;
            btn.textContent = '...';
            deleteJob(jobId, btn);
        }
    }

    // Change 13: deleteJob with full/partial/disabled/failure response handling
    async function deleteJob(jobId, btn) {
        try {
            const response = await fetch(`/api/render/job/${jobId}`, {
                method: 'DELETE'
            });

            const data = await response.json();

            if (response.ok) {
                // Success — remove card from the list
                console.log(`✅ Job ${jobId} deleted successfully`);
                const card = document.querySelector(`[data-flamenco-id="${jobId}"]`);
                if (card) {
                    card.style.transition = 'opacity 200ms ease-out, transform 200ms ease-out';
                    card.style.opacity = '0';
                    card.style.transform = 'translateX(8px)';
                    setTimeout(() => card.remove(), 200);
                }
                // Refresh previous sessions to sync count
                fetchAndDisplayPreviousJobs();

            } else if (response.status === 403) {
                // Feature disabled
                alert('Delete is not enabled on this instance. Set ENABLE_DELETE=true in your .env to enable it.');
                resetDeleteBtn(btn);

            } else if (response.status === 207) {
                // Partial failure — Flamenco deleted but files remain
                const path = data.path || 'unknown path';
                alert(`⚠️ Job was removed from Flamenco but output files could not be deleted.

Path: ${path}

You may need to remove these files manually.`);
                // Still remove the card since Flamenco deletion succeeded
                const card = document.querySelector(`[data-flamenco-id="${jobId}"]`);
                if (card) card.remove();
                fetchAndDisplayPreviousJobs();

            } else {
                console.error('Delete failed:', data);
                alert(`Failed to delete job: ${data.error || 'Unknown error'}`);
                resetDeleteBtn(btn);
            }

        } catch (e) {
            console.error('Delete request error:', e);
            alert('Failed to delete job. Please try again.');
            resetDeleteBtn(btn);
        }
    }

    function resetDeleteBtn(btn) {
        if (!btn) return;
        btn.disabled = false;
        btn.dataset.confirm = 'false';
        btn.textContent = '🗑';
        btn.style.background = 'rgba(239,68,68,0.1)';
        btn.style.borderColor = 'rgba(239,68,68,0.3)';
    }

    // Change 5: Load more previous jobs (pagination)
    async function loadMorePreviousJobs() {
        const btn = document.getElementById('load-more-btn');
        if (btn) {
            btn.disabled = true;
            btn.textContent = 'Loading...';
        }

        try {
            const response = await fetch(`/api/jobs/previous?limit=${PAGE_SIZE}&offset=${previousJobsOffset}`);

            if (!response.ok) {
                console.error('Failed to load more previous jobs:', response.status);
                if (btn) { btn.disabled = false; btn.textContent = 'Load more'; }
                return;
            }

            const data = await response.json();
            const jobs = data.jobs || [];

            console.log(`📦 Loaded ${jobs.length} more previous job(s)`);

            previousJobsHasMore = jobs.length === PAGE_SIZE;
            previousJobsOffset += jobs.length;
            updateLoadMoreButton();

            // Append to existing list rather than replace
            appendPreviousJobs(jobs);

        } catch (error) {
            console.error('❌ Error loading more previous jobs:', error);
            if (btn) { btn.disabled = false; btn.textContent = 'Load more'; }
        }
    }

    function appendPreviousJobs(jobs) {
        const container = document.getElementById('history-sessions-list');
        if (!container) return;

        jobs.forEach((job, index) => {
            const displayName = (job.name || 'Untitled').replace(/^Pangolin_/, '');
            const statusColor = job.status === 'completed' ? '#22c55e' : job.status === 'failed' ? '#ef4444' : job.status === 'canceled' ? '#f97316' : '#6b7280';
            const statusLabel = job.status === 'completed' ? 'Completed' : job.status === 'failed' ? 'Failed' : job.status === 'canceled' ? 'Canceled' : job.status;
            const pangolinId = job.metadata ? (job.metadata['pangolin.job_id'] || job.id) : job.id;
            const staggerDelay = (index * 18) + 'ms';
            const downloadBtn = job.status === 'completed'
                ? '<a href="/download/' + pangolinId + '" class="text-xs font-semibold px-3 py-1.5 rounded-lg border transition-all" style="background:rgba(34,197,94,0.1);color:#22c55e;border-color:rgba(34,197,94,0.3);" onclick="event.stopPropagation()">Download</a>'
                : '';
            const div = document.createElement('div');
            div.className = 'history-item history-item-enter px-4 py-3 glass-input border-none flex items-center justify-between group rounded-xl cursor-pointer';
            div.dataset.jobName = displayName;
            div.setAttribute('onclick', 'activeSessions.openLogModal(this, event)');
            div.style.animationDelay = staggerDelay;
            div.dataset.flamencoId = job.id;
            div.innerHTML = '<div class="flex items-center gap-3 min-w-0">'
                + '<div class="w-2 h-2 rounded-full flex-shrink-0" style="background:' + statusColor + ';"></div>'
                + '<div class="min-w-0">'
                +   '<p class="text-sm font-semibold truncate" title="' + displayName + '">' + displayName + '</p>'
                +   '<p class="text-xs opacity-50 font-mono mt-0.5">' + pangolinId + '</p>'
                +   '<p class="text-xs mt-0.5" style="color:' + statusColor + ';opacity:0.8;">' + statusLabel + '</p>'
                + '</div>'
                + '</div>'
                + '<div class="flex items-center gap-2 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-all ml-3">'
                +   downloadBtn
                +   '<button class="delete-job-btn text-xs font-semibold px-3 py-1.5 rounded-lg border transition-all" style="background:rgba(239,68,68,0.1);color:#ef4444;border-color:rgba(239,68,68,0.3);" data-job-id="' + job.id + '" data-confirm="false" onclick="event.stopPropagation()">Delete</button>'
                + '</div>';
            div.querySelectorAll('.delete-job-btn').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    handleDeleteClick(btn);
                });
            });
            container.appendChild(div);
        });
    }
    function updateLoadMoreButton() {
        const btn = document.getElementById('load-more-btn');
        if (!btn) return;
        btn.style.display = previousJobsHasMore ? 'block' : 'none';
        btn.disabled = false;
        btn.textContent = 'Load more';
    }

    // Public API
    // ── Log Modal ─────────────────────────────────────────────────────────────

    async function openLogModal(row, event) {
        // Don't open if clicking a button or link
        if (event && (event.target.tagName === 'BUTTON' || event.target.tagName === 'A')) return;

        const jobId = row.dataset.flamencoId;
        const jobName = row.dataset.jobName || 'Job';

        const modal = document.getElementById('logModal');
        const jobNameEl = document.getElementById('logModalJobName');
        const taskList = document.getElementById('logModalTaskList');
        const logContent = document.getElementById('logModalContent');

        if (!modal) return;

        // Reset and open
        jobNameEl.textContent = jobName;
        taskList.innerHTML = '<p class="text-[10px] font-bold uppercase tracking-widest opacity-30 mb-1">Tasks</p><p class="text-xs opacity-40 italic">Loading...</p>';
        logContent.innerHTML = '<p class="text-xs opacity-30 italic">Select a task to view its log.</p>';
        modal.style.display = 'flex';

        // Fetch task list
        try {
            const res = await fetch('/api/jobs/' + jobId + '/tasks');
            const data = await res.json();
            const tasks = data.tasks || [];

            if (tasks.length === 0) {
                taskList.innerHTML = '<p class="text-[10px] font-bold uppercase tracking-widest opacity-30 mb-1">Tasks</p><p class="text-xs opacity-40 italic">No tasks found.</p>';
                return;
            }

            const statusColor = (s) => s === 'completed' ? '#22c55e' : s === 'failed' ? '#ef4444' : s === 'active' ? '#f97316' : '#6b7280';

            taskList.innerHTML = '<p class="text-[10px] font-bold uppercase tracking-widest opacity-30 mb-1">Tasks</p>'
                + tasks.map(t =>
                    '<button class="log-task-btn w-full text-left px-3 py-2 rounded-lg text-xs transition-all hover:bg-white/5 flex items-center gap-2"'
                    + ' data-task-id="' + t.id + '" data-job-id="' + jobId + '">'
                    + '<div class="w-1.5 h-1.5 rounded-full flex-shrink-0" style="background:' + statusColor(t.status) + '"></div>'
                    + '<span class="truncate">' + (t.name || t.id) + '</span>'
                    + '</button>'
                ).join('');

            // Wire task buttons
            taskList.querySelectorAll('.log-task-btn').forEach(btn => {
                btn.addEventListener('click', () => loadTaskLog(btn, jobId, btn.dataset.taskId));
            });

            // Auto-load first task
            const firstBtn = taskList.querySelector('.log-task-btn');
            if (firstBtn) loadTaskLog(firstBtn, jobId, firstBtn.dataset.taskId);

        } catch (e) {
            taskList.innerHTML = '<p class="text-[10px] font-bold uppercase tracking-widest opacity-30 mb-1">Tasks</p><p class="text-xs" style="color:#ef4444">Failed to load tasks.</p>';
            console.error('Error fetching tasks:', e);
        }
    }

    async function loadTaskLog(btn, jobId, taskId) {
        const logContent = document.getElementById('logModalContent');
        const taskList = document.getElementById('logModalTaskList');

        // Highlight active task button
        taskList.querySelectorAll('.log-task-btn').forEach(b => b.style.background = '');
        btn.style.background = 'rgba(250,129,18,0.12)';

        logContent.innerHTML = '<p class="text-xs opacity-40 italic">Loading log...</p>';

        try {
            const res = await fetch('/api/jobs/' + jobId + '/tasks/' + taskId + '/log');
            const text = await res.text();

            if (!res.ok) {
                logContent.innerHTML = '<p class="text-xs" style="color:#ef4444">' + text + '</p>';
                return;
            }

            // Render log as monospace with line coloring
            const lines = text.split('\n').map(line => {
                let color = '';
                if (line.includes('ERROR') || line.includes('error') || line.includes('EGL Error')) color = 'color:#ef4444';
                else if (line.includes('WARNING') || line.includes('warning')) color = 'color:#f97316';
                else if (line.includes('completed') || line.includes('Saved:')) color = 'color:#22c55e';
                const escaped = line.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
                return '<div style="' + color + '">' + escaped + '</div>';
            }).join('');

            logContent.innerHTML = '<pre class="text-xs font-mono leading-relaxed whitespace-pre-wrap" style="opacity:0.8">' + lines + '</pre>';
            // Scroll to bottom
            logContent.scrollTop = logContent.scrollHeight;

        } catch (e) {
            logContent.innerHTML = '<p class="text-xs" style="color:#ef4444">Failed to fetch log.</p>';
            console.error('Error fetching task log:', e);
        }
    }

    return {
        init,
        openLogModal,
        fetchAndDisplayActiveJobs,
        fetchAndDisplayPreviousJobs,
        loadMorePreviousJobs,
        cancelJob
    };
})();

// Auto-initialize
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', activeSessions.init);
} else {
    activeSessions.init();
}
