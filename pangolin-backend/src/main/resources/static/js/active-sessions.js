/** Copyright © 2026 Pangolin - SPDX-License-Identifier: Apache-2.0
 *  Pangolin Render Manager - Active Sessions Module
 */

const activeSessions = (() => {
    let pollInterval = null;
    let activeJobs = [];
    let _lastActiveIds = new Set(); // tracks IDs from previous poll to detect completions
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

            // Detect jobs that just left the active list: they completed/failed/canceled
            // Refresh the Previous panel immediately so new entries appear without page reload
            const currentIds = new Set(activeJobs.map(j => j.id));
            const jobsJustFinished = [..._lastActiveIds].some(id => !currentIds.has(id));
            if (jobsJustFinished) {
                console.log('✅ Job(s) completed, refreshing Previous panel');
                previousJobsOffset = 0;
                fetchAndDisplayPreviousJobs();
            }
            _lastActiveIds = currentIds;

            // Pause polling when nothing is active, then resume on next submission
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

            // Determine if delete button should appear
            const showDelete = ['completed', 'failed', 'canceled'].includes(job.status);

            // Build action buttons
            const actions = [];
            if (job.status === 'completed') {
                actions.push(`<a href="/api/render/download/${pangolinId}" class="text-xs font-semibold px-3 py-1.5 rounded-lg border transition-all" style="background:rgba(34,197,94,0.1);color:#22c55e;border-color:rgba(34,197,94,0.3);">Download</a>`);
            }
            if (showDelete) {
                actions.push(`<button class="delete-job-btn text-xs font-semibold px-3 py-1.5 rounded-lg border transition-all" style="background:rgba(239,68,68,0.1);color:#ef4444;border-color:rgba(239,68,68,0.3);" data-job-id="${job.id}" data-confirm="false">Delete</button>`);
            }

            const actionsHtml = actions.length
                ? `<div class="flex items-center gap-2 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-all ml-3">${actions.join('')}</div>`
                : '';

            // Full card HTML – left side + actions (if any)
            return '<div class="history-item history-item-enter px-4 py-3 glass-input border-none flex items-center justify-between group rounded-xl" style="animation-delay:' + staggerDelay + '" data-flamenco-id="' + job.id + '">'
                + '<div class="flex items-center gap-3 min-w-0">'
                +   '<div class="w-2 h-2 rounded-full flex-shrink-0" style="background:' + statusColor + ';"></div>'
                +   '<div class="min-w-0">'
                +     '<p class="text-sm font-semibold truncate" title="' + displayName + '">' + displayName + '</p>'
                +     '<p class="text-xs opacity-50 font-mono mt-0.5">' + pangolinId + '</p>'
                +     '<p class="text-xs mt-0.5" style="color:' + statusColor + ';opacity:0.8;">' + statusLabel + '</p>'
                +   '</div>'
                + '</div>'
                + actionsHtml
                + '</div>';
        }).join('');

        // Re-attach event listeners (same as before)
        container.querySelectorAll('.delete-job-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                handleDeleteClick(btn);
            });
        });

        container.querySelectorAll('.history-item[data-flamenco-id]').forEach(row => {
            row.style.cursor = 'pointer';
            row.addEventListener('click', (e) => {
                if (e.target.closest('button, a')) return;
                const flamencoId = row.dataset.flamencoId;
                const displayName = row.querySelector('p.font-semibold')?.textContent?.trim() || flamencoId;
                document.dispatchEvent(new CustomEvent('pangolin:openLogModal', {
                    detail: { flamencoId, displayName }
                }));
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
        
        if (jobs.length === 0) {
            container.innerHTML = `
                <div class="flex flex-col items-center justify-center py-4 px-4 text-center empty-state">
                    <div class="text-4xl mb-2 opacity-50">💤</div>
                    <p class="text-sm font-semibold mb-1" style="color: var(--text-primary);">No active renders</p>
                    <p class="text-xs" style="color: var(--text-secondary);">Submit a job to get started!</p>
                </div>
            `;
            return;
        }

        // Track which job IDs are in the new list
        const incomingIds = new Set(jobs.map(j => j.id));

        // Clear empty state if present
        const emptyState = container.querySelector('.empty-state');
        if (emptyState) emptyState.remove();

        // Remove cards for jobs no longer in the list
        container.querySelectorAll('.job-card[data-job-id]').forEach(el => {
            if (!incomingIds.has(el.dataset.jobId)) el.remove();
        });

        // Update existing cards in-place or insert new ones (new ones get the enter animation)
        jobs.forEach((job, index) => {
            const existing = container.querySelector('.job-card[data-job-id="' + job.id + '"]');
            if (existing) {
                // In-place update, patch only the parts that change to avoid jitter
                updateJobCard(existing, job);
            } else {
                // New card, insert at correct position with enter animation
                const card = createJobCard(job);
                const allCards = container.querySelectorAll('.job-card[data-job-id]');
                if (index < allCards.length) {
                    container.insertBefore(card, allCards[index]);
                } else {
                    container.appendChild(card);
                }
            }
        });
    }

    function updateJobCard(card, job) {
        const progress = job.steps_total > 0
            ? Math.round((job.steps_completed / job.steps_total) * 100)
            : 0;
        const statusInfo = getStatusInfo(job.status);
        const eta = calculateETA(job);

        // Update status class
        card.className = card.className.replace(/status-\S+/, 'status-' + job.status);

        // Patch activity text
        const activityEl = card.querySelector('.job-activity-text');
        if (activityEl) activityEl.textContent = job.activity || statusInfo.text;

        // Patch progress bar width
        const bar = card.querySelector('.job-progress-bar');
        if (bar) bar.style.width = progress + '%';

        // Patch frames text
        const framesEl = card.querySelector('.job-frames-text');
        if (framesEl) framesEl.textContent = job.steps_completed + ' / ' + job.steps_total + ' frames';

        // Patch percent text
        const pctEl = card.querySelector('.job-percent-text');
        if (pctEl) pctEl.textContent = progress + '%';

        // Patch ETA
        const etaEl = card.querySelector('.job-eta-text');
        if (etaEl) {
            if (eta) {
                etaEl.textContent = '⏱️ ' + eta;
                etaEl.style.display = '';
            } else {
                etaEl.style.display = 'none';
            }
        }

        // Patch action buttons if status changed
        const actionsEl = card.querySelector('.job-actions');
        if (actionsEl) {
            actionsEl.innerHTML = createActionButtons(job);
            actionsEl.querySelectorAll('.cancel-job-btn').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.preventDefault();
                    handleCancelClick(btn);
                });
            });
        }
    }

    function createJobCard(job) {
        const card = document.createElement('div');
        card.className = `glass-card job-card job-card-enter status-${job.status} rounded-2xl p-5 mb-3 overflow-hidden`;
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
                    <p class="job-activity-text text-xs italic" style="color: var(--text-secondary);">
                        ${job.activity || statusInfo.text}
                    </p>
                </div>
            </div>

            <!-- Progress Bar -->
            <div class="mb-3">
                <div class="h-2 rounded-full overflow-hidden" style="background: var(--bg-secondary);">
                    <div class="job-progress-bar h-full bg-gradient-to-r from-pangolin-orange to-orange-400 transition-all duration-500 relative"
                        style="width: ${progress}%">
                        <div class="absolute inset-0 bg-gradient-to-r from-transparent via-white/30 to-transparent animate-shimmer"></div>
                    </div>
                </div>
                <div class="flex justify-between items-center mt-1">
                    <span class="job-frames-text text-xs" style="color: var(--text-secondary);">
                        ${job.steps_completed} / ${job.steps_total} frames
                    </span>
                    <span class="job-percent-text text-xs font-semibold text-pangolin-orange">
                        ${progress}%
                    </span>
                </div>
            </div>

            <!-- ETA -->
            <div class="job-eta-text text-sm mb-3 text-pangolin-orange font-medium" ${eta ? '' : 'style="display:none"'}>
                ⏱️ ${eta || ''}
            </div>

            <!-- Actions -->
            <div class="job-actions flex gap-2">
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
            'under-construction': { icon: '🔨', text: 'Setting up...' },
            'requeueing': { icon: '🔄', text: 'Requeueing...' },
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
                        onclick="window.location.href='/api/render/download/${pangolinId}'"
                        title="${isPartial ? 'Job is still rendering, download will contain completed frames only' : 'Download all rendered frames'}">
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
            // First click - enter confirm state
            btn.dataset.confirm = 'true';
            btn.textContent = 'Confirm?';
            btn.style.background = 'rgba(239,68,68,0.25)';
            btn.style.borderColor = 'rgba(239,68,68,0.7)';

            // Auto-revert after 3s
            btn._revertTimer = setTimeout(() => {
                btn.dataset.confirm = 'false';
                btn.textContent = 'Delete';
                btn.style.background = 'rgba(239,68,68,0.1)';
                btn.style.borderColor = 'rgba(239,68,68,0.3)';
            }, 3000);
        } else {
            // Second click - execute delete
            clearTimeout(btn._revertTimer);
            btn.disabled = true;
            btn.textContent = '...';
            deleteJob(jobId, btn);
        }
    }

    // deleteJob with full/partial/disabled/failure response handling
    async function deleteJob(jobId, btn) {
        try {
            const response = await fetch(`/api/render/job/${jobId}`, {
                method: 'DELETE'
            });

            const data = await response.json();

            if (response.ok) {
                console.log(`✅ Job ${jobId} deleted successfully`);
                const card = document.querySelector(`[data-flamenco-id="${jobId}"]`);
                if (card) {
                    card.style.transition = 'opacity 200ms ease-out, transform 200ms ease-out';
                    card.style.opacity = '0';
                    card.style.transform = 'translateX(8px)';
                    setTimeout(() => card.remove(), 200);
                }
                fetchAndDisplayPreviousJobs();

            } else if (response.status === 403) {
                showDeleteError(btn, 'Delete not enabled. Set ENABLE_DELETE=true in .env');

            } else if (response.status === 207) {
                // Partial failure - Flamenco deleted but output files remain
                const card = document.querySelector(`[data-flamenco-id="${jobId}"]`);
                if (card) {
                    showCardWarning(card, 'Job removed from Flamenco but output files could not be deleted. Remove them manually.');
                    setTimeout(() => card.remove(), 5000);
                }
                fetchAndDisplayPreviousJobs();

            } else {
                console.error('Delete failed:', data);
                showDeleteError(btn, data.error || 'Delete failed');
            }

        } catch (e) {
            console.error('Delete request error:', e);
            showDeleteError(btn, 'Network error. Please try again.');
        }
    }

    function resetDeleteBtn(btn) {
        if (!btn) return;
        btn.disabled = false;
        btn.dataset.confirm = 'false';
        btn.textContent = 'Delete';
        btn.style.background = 'rgba(239,68,68,0.1)';
        btn.style.borderColor = 'rgba(239,68,68,0.3)';
    }

    // Flashes the delete button red briefly, then shows a small error line below the card content
    function showDeleteError(btn, message) {
        if (!btn) return;

        // Flash the button border red without changing its text or size
        btn.style.borderColor = 'rgba(239,68,68,0.8)';
        btn.style.background  = 'rgba(239,68,68,0.2)';
        setTimeout(() => resetDeleteBtn(btn), 4000);

        const card = btn.closest('[data-flamenco-id]');
        if (!card) return;

        // Remove any existing error line before adding a new one
        const existing = card.querySelector('.delete-error-msg');
        if (existing) existing.remove();

        // Make the card wrap so the error renders on its own line below
        card.style.flexWrap = 'wrap';

        const err = document.createElement('p');
        err.className = 'delete-error-msg';
        err.textContent = '⚠ ' + message;
        err.style.cssText = 'font-size:10px;color:#ef4444;opacity:0.85;width:100%;padding:0 4px 2px;margin:0;';
        card.appendChild(err);

        setTimeout(() => {
            err.remove();
            card.style.flexWrap = '';
        }, 4000);
    }

    // Appends an orange warning line to a card (used for 207 partial failure)
    function showCardWarning(card, message) {
        const warn = document.createElement('div');
        warn.style.cssText = 'font-size:10px;color:#f97316;padding:4px 8px;opacity:0.85;';
        warn.textContent = '⚠ ' + message;
        card.appendChild(warn);
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

            const showDelete = ['completed', 'failed', 'canceled'].includes(job.status);

            const actions = [];
            if (job.status === 'completed') {
                actions.push(`<a href="/api/render/download/${pangolinId}" class="text-xs font-semibold px-3 py-1.5 rounded-lg border transition-all" style="background:rgba(34,197,94,0.1);color:#22c55e;border-color:rgba(34,197,94,0.3);">Download</a>`);
            }
            if (showDelete) {
                actions.push(`<button class="delete-job-btn text-xs font-semibold px-3 py-1.5 rounded-lg border transition-all" style="background:rgba(239,68,68,0.1);color:#ef4444;border-color:rgba(239,68,68,0.3);" data-job-id="${job.id}" data-confirm="false">Delete</button>`);
            }

            const actionsHtml = actions.length
                ? `<div class="flex items-center gap-2 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-all ml-3">${actions.join('')}</div>`
                : '';

            const div = document.createElement('div');
            div.className = 'history-item history-item-enter px-4 py-3 glass-input border-none flex items-center justify-between group rounded-xl';
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
                + actionsHtml;

            // Delete button listeners
            div.querySelectorAll('.delete-job-btn').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    handleDeleteClick(btn);
                });
            });

            // Row click → log modal
            div.style.cursor = 'pointer';
            div.addEventListener('click', (e) => {
                if (e.target.closest('button, a')) return;
                const flamencoId = div.dataset.flamencoId;
                const displayName = div.querySelector('p.font-semibold')?.textContent?.trim() || flamencoId;
                document.dispatchEvent(new CustomEvent('pangolin:openLogModal', {
                    detail: { flamencoId, displayName }
                }));
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
    return {
        init,
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
