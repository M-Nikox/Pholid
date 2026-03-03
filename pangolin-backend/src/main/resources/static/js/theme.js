/* Copyright © 2026 Pangolin - SPDX-License-Identifier: Apache-2.0
 * Pangolin Theme Switcher
 * Handles light/dark mode toggling and icon switching
 */

const themeModule = (() => {
    const btn = document.getElementById('themeSwitcher');
    const sunIcon = document.getElementById('theme-icon-sun');
    const moonIcon = document.getElementById('theme-icon-moon');

    /**
     * Updates theme icon visibility
     */
    function updateIcons(theme) {
        if (theme === 'dark') {
            if (sunIcon) sunIcon.style.display = 'none';
            if (moonIcon) moonIcon.style.display = 'block';
        } else {
            if (sunIcon) sunIcon.style.display = 'block';
            if (moonIcon) moonIcon.style.display = 'none';
        }
    }

    /**
     * Initialize theme on page load
     */
    function init() {
        if (!btn) {
            console.warn('Theme switcher button not found');
            return;
        }

        // Check saved preference or system preference
        const currentTheme = localStorage.getItem('theme') ||
            (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');

        if (currentTheme === 'dark') {
            document.documentElement.setAttribute('data-theme', 'dark');
            updateIcons('dark');
        } else {
            updateIcons('light');
        }

        // Add click handler
        btn.addEventListener('click', toggleTheme);
    }

    /**
     * Toggle between light and dark themes
     */
    function toggleTheme() {
        let theme = document.documentElement.getAttribute('data-theme');
        
        if (theme === 'dark') {
            document.documentElement.removeAttribute('data-theme');
            localStorage.setItem('theme', 'light');
            updateIcons('light');
        } else {
            document.documentElement.setAttribute('data-theme', 'dark');
            localStorage.setItem('theme', 'dark');
            updateIcons('dark');
        }
    }

    return { init };
})();

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', themeModule.init);
