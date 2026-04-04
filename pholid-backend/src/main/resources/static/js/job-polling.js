/** Copyright © 2026 Pholid - SPDX-License-Identifier: Apache-2.0
 *  Pholid Job Polling
 *  Polls the output directory status endpoint to detect when rendered files
 *  are ready on disk. Fires pholid:renderComplete for the notification system.
 *  UI progress is handled by active-sessions.js via the Flamenco API.
 */

const jobPolling = (() => {
    let pollInterval;
    let currentJobId;
    let totalFrames;
    let projectName;

    /**
     * Start polling for output file availability
     * @param {string} jobId - Job identifier
     * @param {number} expectedFrames - Total frames expected
     * @param {string} project - Project name for the completion notification
     */
    function startPolling(jobId, expectedFrames, project = 'Your project') {
        currentJobId = jobId;
        totalFrames = expectedFrames;
        projectName = project;

        // Persist state so polling resumes if the page is closed and reopened
        try {
            sessionStorage.setItem('pholid:activeJob', JSON.stringify({ jobId, expectedFrames, projectName: project }));
        } catch (e) {
            console.debug('Session storage unavailable; polling state persistence disabled.', e);
        }

        clearInterval(pollInterval);
        pollInterval = setInterval(checkStatus, 3000);
        console.log(`📡 Polling output status for job ${jobId}`);
    }

    /**
     * Check whether output files are ready on disk
     */
    async function checkStatus() {
        if (!currentJobId) return;

        try {
            const res = await fetch(`/api/render/status/${currentJobId}`);
            const data = await res.json();

            if (data.ready && data.fileCount >= totalFrames && totalFrames > 0) {
                finishJob();
            }
        } catch (e) {
            console.error('Output status check failed:', e);
        }
    }

    /**
     * Files are ready: fire completion event and stop polling
     */
    function finishJob() {
        clearInterval(pollInterval);
        currentJobId = null;

        try {
            sessionStorage.removeItem('pholid:activeJob');
        } catch (e) {
            console.debug('Session storage unavailable; could not clear persisted job state.', e);
        }

        console.log(`✅ Job complete: ${projectName} (${totalFrames} frames)`);

        document.dispatchEvent(new CustomEvent('pholid:renderComplete', {
            detail: {
                projectName: projectName,
                frameCount: totalFrames
            }
        }));
    }

    /**
     * Stop polling (e.g. if user navigates away or resets)
     */
    function stop() {
        clearInterval(pollInterval);
        currentJobId = null;
        try {
            sessionStorage.removeItem('pholid:activeJob');
        } catch (e) {
            console.debug('Session storage unavailable; could not clear persisted job state.', e);
        }
    }

    function init() {
        document.addEventListener('pholid:startPolling', (e) => {
            const { jobId, expectedFrames, projectName } = e.detail;
            startPolling(jobId, expectedFrames, projectName);
        });

        document.addEventListener('pholid:stopPolling', stop);

        // Resume polling if the page was closed while a job was in flight
        try {
            const saved = sessionStorage.getItem('pholid:activeJob');
            if (saved) {
                const { jobId, expectedFrames, projectName } = JSON.parse(saved);
                console.log(`🔄 Resuming poll for in-flight job ${jobId}`);
                startPolling(jobId, expectedFrames, projectName);
            }
        } catch (e) {
            console.debug('Could not restore polling state from session storage.', e);
        }
    }

    return { init };
})();

document.addEventListener('DOMContentLoaded', jobPolling.init);
