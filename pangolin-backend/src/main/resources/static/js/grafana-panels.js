/**
 * Pangolin Grafana Panels
 * Handles tab switching for system metrics
 * Set GRAFANA_BASE_URL to enable live panels
 */

const grafanaPanels = (() => {
    // Set this to your Grafana URL to enable live panels
    // e.g. 'http://your-grafana:3000'
    const GRAFANA_BASE_URL = '';

    const PANEL_IDS = { cpu: 1, memory: 2, queue: 3, network: 4 };
    const DASHBOARD_ID = 'YOUR_DASHBOARD_ID'; // Replace with your actual dashboard ID

    const tabs = ['cpu', 'memory', 'queue', 'network'];
    let activeTab = 'cpu';

    function switchPanel(panelName) {
        const iframe = document.getElementById('grafana-panel');
        const placeholder = document.getElementById('grafana-placeholder');

        // Update tab button styles
        tabs.forEach(tab => {
            const el = document.getElementById(`tab-${tab}`);
            if (!el) return;
            if (tab === panelName) {
                el.style.background = 'var(--accent-primary)';
                el.style.color = 'white';
                el.classList.remove('glass-input');
            } else {
                el.style.background = '';
                el.style.color = 'var(--text-primary)';
                el.classList.add('glass-input');
            }
        });

        activeTab = panelName;

        // Show iframe only if Grafana is configured
        if (GRAFANA_BASE_URL && iframe && placeholder) {
            const url = `${GRAFANA_BASE_URL}/d-solo/${DASHBOARD_ID}?orgId=1&panelId=${PANEL_IDS[panelName]}&theme=dark`;
            iframe.src = url;
            iframe.style.display = 'block';
            placeholder.style.display = 'none';
        }
    }

    function init() {
        tabs.forEach(tab => {
            const btn = document.getElementById(`tab-${tab}`);
            if (btn) btn.addEventListener('click', () => switchPanel(tab));
        });

        // If Grafana is configured, show iframe immediately
        if (GRAFANA_BASE_URL) {
            switchPanel(activeTab);
        }
    }

    return { init, switchPanel };
})();

document.addEventListener('DOMContentLoaded', grafanaPanels.init);
