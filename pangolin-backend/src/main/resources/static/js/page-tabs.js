/** Copyright © 2026 Pangolin - SPDX-License-Identifier: Apache-2.0
 *  Pangolin Page Tabs
 *  Handles Render / Metrics main tab navigation
 */

const pageTabs = (() => {
    function init() {
        const tabBtns = document.querySelectorAll('.pangolin-tab-btn[role="tab"]');
        tabBtns.forEach(btn => {
            btn.addEventListener('click', () => switchTab(btn.id));
        });
    }

    function switchTab(tabId) {
        const tabBtns = document.querySelectorAll('.pangolin-tab-btn[role="tab"]');
        const panels  = document.querySelectorAll('.pangolin-tab-panel[role="tabpanel"]');

        tabBtns.forEach(btn => {
            const isActive = btn.id === tabId;
            btn.classList.toggle('active', isActive);
            btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
        });

        panels.forEach(panel => {
            const activeBtn = document.getElementById(tabId);
            const isActive = activeBtn && panel.id === activeBtn.getAttribute('aria-controls');
            panel.classList.toggle('active', isActive);
        });
    }

    return { init, switchTab };
})();

document.addEventListener('DOMContentLoaded', pageTabs.init);
