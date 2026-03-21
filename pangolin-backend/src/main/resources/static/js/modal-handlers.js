/** Copyright © 2026 Pangolin - SPDX-License-Identifier: Apache-2.0
 *  Pangolin Modal Handlers
 *  Manages the first-time guide modal.
 */

const modalHandlers = (() => {
    const STORAGE_KEY = 'pangolin_guide_seen';

    function init() {
        initGuideModal();
        initLogModal();
    }

    function ensureModalAttachedToBody(modal) {
        if (!modal || modal.parentElement === document.body) return;
        document.body.appendChild(modal);
    }

    function setModalOpenState(isOpen) {
        document.documentElement.classList.toggle('modal-open', isOpen);
        document.body.classList.toggle('modal-open', isOpen);
    }

    function initGuideModal() {
        const modal = document.getElementById('guideModal');
        const closeBtn = document.getElementById('closeModal');
        const gotItBtn = document.getElementById('gotItBtn');
        const helpBtn = document.getElementById('helpBtn');

        if (!modal) return;
        ensureModalAttachedToBody(modal);

        function openModal() {
            modal.style.display = 'flex';
            setModalOpenState(true);
        }

        function closeModal() {
            modal.style.display = 'none';
            setModalOpenState(false);
            localStorage.setItem(STORAGE_KEY, 'true');
        }

        const hasSeenGuide = localStorage.getItem(STORAGE_KEY);
        if (!hasSeenGuide) {
            setTimeout(openModal, 500);
        }

        if (closeBtn) closeBtn.addEventListener('click', closeModal);
        if (gotItBtn) gotItBtn.addEventListener('click', closeModal);
        if (helpBtn)  helpBtn.addEventListener('click', openModal);

        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeModal();
        });
    }

    function initLogModal() {
        const modal = document.getElementById('logModal');
        const closeBtn = document.getElementById('closeLogModal');
        if (!modal) return;
        ensureModalAttachedToBody(modal);

        function close() {
            modal.style.display = 'none';
            setModalOpenState(false);
            document.getElementById('logModalTaskList').innerHTML = '<p class="text-[10px] font-bold uppercase tracking-widest opacity-30 mb-1">Tasks</p>';
            document.getElementById('logModalContent').innerHTML = '<p class="text-xs opacity-30 italic">Select a task to view its log.</p>';
            document.getElementById('logModalJobName').textContent = '';
        }

        if (closeBtn) closeBtn.addEventListener('click', close);
        modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
        document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && modal.style.display !== 'none') close(); });
        document.addEventListener('pangolin:openLogModal', (e) => {
            openLogModal(e.detail.flamencoId, e.detail.displayName);
        });
    }

    async function openLogModal(flamencoJobId, jobDisplayName) {
        const modal = document.getElementById('logModal');
        const taskList = document.getElementById('logModalTaskList');
        const logContent = document.getElementById('logModalContent');
        const jobNameEl = document.getElementById('logModalJobName');
        if (!modal) return;

        // Reset and open
        jobNameEl.textContent = jobDisplayName;
        taskList.innerHTML = '<p class="text-[10px] font-bold uppercase tracking-widest opacity-30 mb-1">Tasks</p><p class="text-xs opacity-30 italic mt-2">Loading...</p>';
        logContent.innerHTML = '<p class="text-xs opacity-30 italic">Select a task to view its log.</p>';
        modal.style.display = 'flex';
        setModalOpenState(true);

        // Fetch tasks
        let tasks = [];
        try {
            const res = await fetch(`/api/jobs/${flamencoJobId}/tasks`);
            if (!res.ok) throw new Error(`${res.status}`);
            const data = await res.json();
            tasks = data.tasks || [];
        } catch (e) {
            taskList.innerHTML = '<p class="text-[10px] font-bold uppercase tracking-widest opacity-30 mb-1">Tasks</p><p class="text-xs text-red-400 mt-2">Failed to load tasks.</p>';
            return;
        }

        if (tasks.length === 0) {
            taskList.innerHTML = '<p class="text-[10px] font-bold uppercase tracking-widest opacity-30 mb-1">Tasks</p><p class="text-xs opacity-30 italic mt-2">No tasks found.</p>';
            return;
        }

        // Render task list
        const statusColors = { completed: '#22c55e', failed: '#ef4444', canceled: '#f97316', active: '#FA8112', queued: '#6b7280' };
        taskList.innerHTML = '<p class="text-[10px] font-bold uppercase tracking-widest opacity-30 mb-1">Tasks</p>'
            + tasks.map(t => {
                const color = statusColors[t.status] || '#6b7280';
                const label = (t.name || t.id).replace(/^render-/, '');
                return `<button class="task-log-btn w-full text-left px-2 py-1.5 rounded-lg text-xs font-mono hover:bg-white/10 transition-all flex items-center gap-2"
                                data-task-id="${t.id}" data-job-id="${flamencoJobId}">
                            <span class="w-2 h-2 rounded-full flex-shrink-0" style="background:${color}"></span>
                            <span class="truncate">${label}</span>
                        </button>`;
            }).join('');

        // Wire task click → load log
        taskList.querySelectorAll('.task-log-btn').forEach(btn => {
            btn.addEventListener('click', () => loadTaskLog(btn, flamencoJobId));
        });

        // Auto-load the first task's log
        const firstBtn = taskList.querySelector('.task-log-btn');
        if (firstBtn) firstBtn.click();
    }

    async function loadTaskLog(btn, flamencoJobId) {
        const taskId = btn.dataset.taskId;
        const logContent = document.getElementById('logModalContent');

        // Highlight active task
        document.querySelectorAll('.task-log-btn').forEach(b => b.classList.remove('bg-white/10'));
        btn.classList.add('bg-white/10');

        logContent.innerHTML = '<p class="text-xs opacity-30 italic">Loading log...</p>';

        try {
            const res = await fetch(`/api/jobs/${flamencoJobId}/tasks/${taskId}/log`);
            const text = await res.text();
            if (!res.ok || !text.trim()) {
                logContent.innerHTML = '<p class="text-xs opacity-30 italic">No log output for this task yet.</p>';
                return;
            }
            // Render as preformatted log with Flamenco/Blender-aware line coloring
            const lines = text.split('\n').map(line => {
                let color = 'inherit';

                // 🔴 Errors - EGL/GL errors, explicit failures, exceptions
                if (/error|failed|exception|traceback|EGL_BAD|fatal/i.test(line)) {
                    color = '#ef4444';

                // 🟡 Warnings
                } else if (/warn/i.test(line)) {
                    color = '#f97316';

                // 🟢 Success - saves, completions, status transitions to good states
                } else if (/saved|-> completed|-> active|success|done|quit/i.test(line)) {
                    color = '#22c55e';

                // 🔵 Flamenco system lines - timestamps like 2026-02-19T...
                } else if (/^\d{4}-\d{2}-\d{2}T/.test(line)) {
                    color = '#60a5fa';
                }
                // Default (inherit): normal Blender render progress: pid=20 > render | ...

                const escaped = line.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
                return `<span style="color:${color}">${escaped}</span>`;
            }).join('\n');
            logContent.innerHTML = `<pre class="text-xs font-mono whitespace-pre-wrap break-words leading-relaxed">${lines}</pre>`;
            // Scroll to bottom
            logContent.scrollTop = logContent.scrollHeight;
        } catch (e) {
            logContent.innerHTML = `<p class="text-xs text-red-400">Failed to load log: ${e.message}</p>`;
        }
    }

    return { init, openLogModal };

})();

document.addEventListener('DOMContentLoaded', modalHandlers.init);
