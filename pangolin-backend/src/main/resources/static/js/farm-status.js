/**
 * Pangolin Farm Status
 * Polls /api/farm/status and updates the header badge dot and label.
 */

const farmStatus = (() => {
    const POLL_INTERVAL_MS = 10000;
    let pollInterval = null;

    const STATUS_CONFIG = {
        'active':      { color: 'bg-green-500',  pulse: true,  label: 'Farm Online'      },
        'idle':        { color: 'bg-green-500',  pulse: false, label: 'Farm Idle'        },
        'waiting':     { color: 'bg-yellow-400', pulse: true,  label: 'Workers Asleep'   },
        'asleep':      { color: 'bg-yellow-400', pulse: false, label: 'Farm Asleep'      },
        'starting':    { color: 'bg-yellow-400', pulse: true,  label: 'Farm Starting'    },
        'inoperative': { color: 'bg-red-500',    pulse: false, label: 'Farm Offline'     },
        'unknown':     { color: 'bg-red-500',    pulse: false, label: 'Farm Unknown'     },
    };

    const UNREACHABLE = { color: 'bg-red-500', pulse: false, label: 'Farm Unreachable' };

    function applyStatus(config) {
        const dot  = document.getElementById('farm-status-indicator');
        const text = document.getElementById('farm-status-text');
        if (!dot || !text) return;

        // Reset dot classes
        dot.className = 'w-2 h-2 rounded-full';
        dot.classList.add(config.color);
        if (config.pulse) dot.classList.add('animate-pulse');

        text.textContent = config.label;
    }

    async function fetchStatus() {
        try {
            const response = await fetch('/api/farm/status');
            if (!response.ok) {
                applyStatus(UNREACHABLE);
                return;
            }
            const data = await response.json();
            const config = STATUS_CONFIG[data.status] || UNREACHABLE;
            applyStatus(config);
        } catch (e) {
            console.error('Farm status fetch failed:', e);
            applyStatus(UNREACHABLE);
        }
    }

    function init() {
        fetchStatus();
        pollInterval = setInterval(fetchStatus, POLL_INTERVAL_MS);
    }

    return { init };
})();

document.addEventListener('DOMContentLoaded', farmStatus.init);
