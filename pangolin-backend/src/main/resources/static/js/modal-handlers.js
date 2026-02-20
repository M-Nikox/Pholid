/**
 * Pangolin Modal Handlers
 * Manages the first-time guide modal.
 */

const modalHandlers = (() => {
    const STORAGE_KEY = 'pangolin_guide_seen';

    function init() {
        initGuideModal();
        initLogModal();
    }

    function initGuideModal() {
        const modal = document.getElementById('guideModal');
        const closeBtn = document.getElementById('closeModal');
        const gotItBtn = document.getElementById('gotItBtn');
        const helpBtn = document.getElementById('helpBtn');

        if (!modal) return;

        function openModal() {
            modal.style.display = 'flex';
        }

        function closeModal() {
            modal.style.display = 'none';
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

        function close() { modal.style.display = 'none'; }

        if (closeBtn) closeBtn.addEventListener('click', close);
        modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
        document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && modal.style.display !== 'none') close(); });
    }

    return { init };
})();

document.addEventListener('DOMContentLoaded', modalHandlers.init);
