/** Copyright © 2026 Pangolin - SPDX-License-Identifier: Apache-2.0
 *  Pangolin Theme Selector
 *  Supports: light, dark, high-contrast, midnight
 *  Stores preference in localStorage; falls back to system preference
 */

const themeModule = (() => {
    const THEMES = {
        light:          { label: 'Light',         swatch: '#FAF3E1', icon: '☀️'  },
        dark:           { label: 'Dark',           swatch: '#1C1328', icon: '🌙' },
        'high-contrast':{ label: 'High Contrast',  swatch: '#000000', icon: '◑'  },
        midnight:       { label: 'Midnight',        swatch: '#020510', icon: '✦'  },
    };

    let dropdownOpen = false;

    // ── Apply a theme by name ────────────────────────────────────────────────
    function applyTheme(name) {
        if (!THEMES[name]) name = 'light';
        if (name === 'light') {
            document.documentElement.removeAttribute('data-theme');
        } else {
            document.documentElement.setAttribute('data-theme', name);
        }
        localStorage.setItem('theme', name);
        updateUI(name);
    }

    // ── Update button icon + active option highlight ─────────────────────────
    function updateUI(name) {
        const btn = document.getElementById('themeSwitcher');
        if (btn) {
            const iconEl = btn.querySelector('.theme-current-icon');
            if (iconEl) iconEl.textContent = THEMES[name]?.icon ?? '☀️';
        }

        // Mark active option
        document.querySelectorAll('.theme-selector-option').forEach(opt => {
            opt.classList.toggle('active', opt.dataset.theme === name);
        });
    }

    // ── Toggle dropdown open/closed ──────────────────────────────────────────
    function toggleDropdown() {
        const wrapper = document.getElementById('themeSwitcher')?.closest('.theme-selector-dropdown');
        if (!wrapper) return;
        dropdownOpen = !dropdownOpen;
        wrapper.classList.toggle('open', dropdownOpen);
    }

    function closeDropdown() {
        const wrapper = document.getElementById('themeSwitcher')?.closest('.theme-selector-dropdown');
        if (!wrapper) return;
        dropdownOpen = false;
        wrapper.classList.remove('open');
    }

    // ── Build the dropdown list options ──────────────────────────────────────
    function buildDropdown(currentTheme) {
        const list = document.getElementById('themeSelectorList');
        if (!list) return;
        list.innerHTML = '';
        Object.entries(THEMES).forEach(([name, meta]) => {
            const li = document.createElement('li');
            li.className = 'theme-selector-option' + (name === currentTheme ? ' active' : '');
            li.dataset.theme = name;
            li.innerHTML = `<span class="theme-swatch" style="background:${meta.swatch}"></span>${meta.label}`;
            li.addEventListener('click', () => {
                applyTheme(name);
                closeDropdown();
            });
            list.appendChild(li);
        });
    }

    // ── Initialise ────────────────────────────────────────────────────────────
    function init() {
        const btn = document.getElementById('themeSwitcher');
        if (!btn) {
            console.warn('Theme switcher button not found');
            return;
        }

        const saved = localStorage.getItem('theme');
        const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        const currentTheme = saved || (systemDark ? 'dark' : 'light');

        applyTheme(currentTheme);
        buildDropdown(currentTheme);

        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleDropdown();
        });

        // Close on outside click
        document.addEventListener('click', (e) => {
            if (dropdownOpen && !e.target.closest('.theme-selector-dropdown')) {
                closeDropdown();
            }
        });
    }

    return { init, applyTheme };
})();

document.addEventListener('DOMContentLoaded', themeModule.init);
