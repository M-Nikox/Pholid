/** Copyright © 2026 Pangolin - SPDX-License-Identifier: Apache-2.0
 * Pangolin Notification Handler
 * Manages browser notifications and audio alerts for render completion
 */

const notifications = (() => {
    let hasRequestedPermission = false;
    let notificationSound = null;
    let audioContext = null;

    /**
     * Generate a subtle double-click notification sound
     * Uses Web Audio API to create a lower-pitched click sound
     */
    function generateNotificationSound() {
        if (!audioContext) audioContext = new (window.AudioContext || window.webkitAudioContext)();
        
        // Create a buffer for our sound
        const sampleRate = audioContext.sampleRate;
        const duration = 0.15; // Total duration including both clicks
        const buffer = audioContext.createBuffer(1, sampleRate * duration, sampleRate);
        const channel = buffer.getChannelData(0);

        // Generate first click (lower pitched, subtle)
        const clickDuration = 0.015; // Very short click
        const clickSamples = sampleRate * clickDuration;
        const frequency = 400; // Lower pitch (was 800+ for typical clicks)
        
        // First click
        for (let i = 0; i < clickSamples; i++) {
            const t = i / sampleRate;
            const envelope = Math.exp(-t * 150); // Quick decay
            channel[i] = Math.sin(2 * Math.PI * frequency * t) * envelope * 0.3;
        }

        // Second click (slightly delayed)
        const delaySeconds = 0.08;
        const delayStart = Math.floor(sampleRate * delaySeconds);
        
        for (let i = 0; i < clickSamples; i++) {
            const t = i / sampleRate;
            const envelope = Math.exp(-t * 150);
            const index = delayStart + i;
            if (index < channel.length) {
                channel[index] = Math.sin(2 * Math.PI * frequency * t) * envelope * 0.3;
            }
        }

        return buffer;
    }

    /**
     * Play the notification sound
     */
    function playSound() {
        try {
            if (!audioContext) audioContext = new (window.AudioContext || window.webkitAudioContext)();
            if (!notificationSound) notificationSound = generateNotificationSound();

            audioContext.resume().then(() => {
                const source = audioContext.createBufferSource();
                source.buffer = notificationSound;
                source.connect(audioContext.destination);
                source.start(0);
            }).catch(e => console.warn('Could not play notification sound:', e));
        } catch (e) {
            console.warn('Could not play notification sound:', e);
        }
    }

    /**
     * Request notification permission on first job submission
     */
    async function requestPermission() {
        if (hasRequestedPermission) return;
        
        hasRequestedPermission = true;

        if (!('Notification' in window)) {
            console.warn('This browser does not support notifications');
            return;
        }

        if (Notification.permission === 'default') {
            try {
                await Notification.requestPermission();
            } catch (e) {
                console.warn('Error requesting notification permission:', e);
            }
        }
    }

    /**
     * Show notification when render completes
     * @param {string} projectName - Name of the project
     * @param {number} frameCount - Number of frames rendered
     */
    function showCompletion(projectName, frameCount) {
        // Only show notification AND play sound if permitted
        if (!('Notification' in window)) return;
        
        if (Notification.permission === 'granted') {
            // Play sound when notification is shown
            playSound();
            try {
                const notification = new Notification('🎬 Render Complete!', {
                    body: `${projectName} finished (${frameCount} frames)`,
                    icon: '/svg/pangolin.svg',
                    badge: '/svg/pangolin.svg',
                    tag: 'pangolin-render-complete', // Prevents notification spam
                    requireInteraction: false
                });

                // Auto-close notification after 8 seconds
                setTimeout(() => notification.close(), 8000);

                // Optional: focus window when notification is clicked
                notification.onclick = () => {
                    window.focus();
                    notification.close();
                };
            } catch (e) {
                console.warn('Could not show notification:', e);
            }
        }
    }

    /**
     * Initialize notification system
     */
    function init() {
        // Listen for job submission to request permission
        document.addEventListener('pangolin:jobSubmitted', requestPermission);

        // Listen for render completion
        document.addEventListener('pangolin:renderComplete', (e) => {
            const { projectName, frameCount } = e.detail;
            showCompletion(projectName, frameCount);
        });
    }

    return { init };
})();

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', notifications.init);
