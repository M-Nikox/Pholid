/** Copyright © 2026 Pholid - SPDX-License-Identifier: Apache-2.0
 *  Pholid Theme Selector
 *  Supports: light, dark, high-contrast, midnight, custom
 *  Stores preference in localStorage; falls back to system preference
 */

const themeModule = (() => {
    const THEMES = {
        light:           { label: 'Light',         swatch: '#FAF3E1', icon: '☀️' },
        dark:            { label: 'Dark',           swatch: '#1C1328', icon: '🌙' },
        'high-contrast': { label: 'High Contrast',  swatch: '#000000', icon: '◑'  },
        midnight:        { label: 'Midnight',        swatch: '#020510', icon: '✦'  },
        custom:          { label: 'Custom',          swatch: null,      icon: '🎨' },
    };

    const CUSTOM_DEFAULTS = { accent: '#FA8112', bg: '#0F0916', text: '#FAF3E1' };

    let dropdownOpen = false;

    // ── Colour helpers ────────────────────────────────────────────────────────
    function hexToRgb(hex) {
        const h = hex.replace('#', '');
        const n = parseInt(h.length === 3
            ? h.split('').map(c => c + c).join('')
            : h, 16);
        return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
    }

    function rgba(hex, alpha) {
        const { r, g, b } = hexToRgb(hex);
        return `rgba(${r},${g},${b},${alpha})`;
    }

    // Returns '#000000' or '#ffffff' depending on which has better contrast against hex
    function contrastText(hex) {
        const { r, g, b } = hexToRgb(hex);
        // Relative luminance (WCAG formula)
        const lum = ([r, g, b].map(c => {
            c /= 255;
            return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }).reduce((acc, c, i) => acc + c * [0.2126, 0.7152, 0.0722][i], 0));
        return lum > 0.179 ? '#000000' : '#ffffff';
    }

    // ── Inject / update the [data-theme="custom"] CSS block ──────────────────
    function injectCustomStyle(accent, bg, text) {
        let el = document.getElementById('pholid-custom-theme-style');
        if (!el) {
            el = document.createElement('style');
            el.id = 'pholid-custom-theme-style';
            document.head.appendChild(el);
        }
        const { r, g, b: bVal } = hexToRgb(accent);
        el.textContent = `
            [data-theme="custom"] {
                --pholid-orange:     ${accent};
                --pholid-orange-rgb: ${r}, ${g}, ${bVal};
                --accent-primary:      ${accent};
                --accent-text:         ${contrastText(accent)};
                --glass-bg:            ${rgba(bg, 0.85)};
                --glass-border:        ${rgba(accent, 0.35)};
                --text-primary:        ${text};
                --text-secondary:      ${rgba(text, 0.5)};
                --bg-secondary:        ${rgba(text, 0.07)};
                --bg-gradient:         linear-gradient(135deg, ${bg} 0%, ${rgba(bg, 0.85)} 100%);
            }
        `;
    }

    // ── Apply a theme by name ─────────────────────────────────────────────────
    function applyTheme(name) {
        if (!THEMES[name]) name = 'light';

        if (name === 'custom') {
            const saved = loadCustomValues();
            injectCustomStyle(saved.accent, saved.bg, saved.text);
        }

        if (name === 'light') {
            document.documentElement.removeAttribute('data-theme');
        } else {
            document.documentElement.setAttribute('data-theme', name);
        }

        localStorage.setItem('theme', name);
        updateUI(name);
    }

    // ── Persist and load custom colour values ─────────────────────────────────
    function saveCustomValues(accent, bg, text) {
        localStorage.setItem('theme-custom', JSON.stringify({ accent, bg, text }));
    }

    function loadCustomValues() {
        try {
            const saved = JSON.parse(localStorage.getItem('theme-custom'));
            if (saved?.accent && saved?.bg && saved?.text) return saved;
        } catch (e) {
            console.debug('Failed to read custom theme values, using defaults.', e);
        }
        return { ...CUSTOM_DEFAULTS };
    }

    // ── Update button icon + active option highlight ──────────────────────────
    function updateUI(name) {
        const btn = document.getElementById('themeSwitcher');
        if (btn) {
            const iconEl = btn.querySelector('.theme-current-icon');
            if (iconEl) iconEl.textContent = THEMES[name]?.icon ?? '☀️';
        }
        document.querySelectorAll('.theme-selector-option').forEach(opt => {
            opt.classList.toggle('active', opt.dataset.theme === name);
        });
        const customSwatch = document.querySelector('.theme-selector-option[data-theme="custom"] .theme-swatch');
        if (customSwatch) {
            const vals = loadCustomValues();
            customSwatch.style.background = vals.accent;
        }
    }

    // ── Toggle / close dropdown ───────────────────────────────────────────────
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

    // ── Build custom theme panel ──────────────────────────────────────────────
    function buildCustomPanel(list) {
        const vals = loadCustomValues();

        const panel = document.createElement('li');
        panel.className = 'custom-theme-panel';
        panel.innerHTML = `
            <div class="custom-theme-row">
                <label>Accent</label>
                <input type="color" id="ct-accent" value="${vals.accent}">
            </div>
            <div class="custom-theme-row">
                <label>Background</label>
                <input type="color" id="ct-bg" value="${vals.bg}">
            </div>
            <div class="custom-theme-row">
                <label>Text</label>
                <input type="color" id="ct-text" value="${vals.text}">
            </div>
            <button class="custom-theme-apply">Apply</button>
        `;
        list.appendChild(panel);

        // Live preview while dragging pickers
        ['ct-accent', 'ct-bg', 'ct-text'].forEach(id => {
            panel.querySelector(`#${id}`).addEventListener('input', () => {
                const a = panel.querySelector('#ct-accent').value;
                const b = panel.querySelector('#ct-bg').value;
                const t = panel.querySelector('#ct-text').value;
                injectCustomStyle(a, b, t);
                document.documentElement.setAttribute('data-theme', 'custom');
            });
        });

        // Apply — save values, mark custom as active, close dropdown
        panel.querySelector('.custom-theme-apply').addEventListener('click', () => {
            const a = panel.querySelector('#ct-accent').value;
            const b = panel.querySelector('#ct-bg').value;
            const t = panel.querySelector('#ct-text').value;
            saveCustomValues(a, b, t);
            applyTheme('custom');
            closeDropdown();
        });

        return panel;
    }

    // ── Build the dropdown list ───────────────────────────────────────────────
    function buildDropdown(currentTheme) {
        const list = document.getElementById('themeSelectorList');
        if (!list) return;
        list.innerHTML = '';

        const customVals = loadCustomValues();

        Object.entries(THEMES).forEach(([name, meta]) => {
            const li = document.createElement('li');
            li.className = 'theme-selector-option' + (name === currentTheme ? ' active' : '');
            li.dataset.theme = name;

            const swatchColor = name === 'custom' ? customVals.accent : meta.swatch;
            li.innerHTML = `<span class="theme-swatch" style="background:${swatchColor}"></span>${meta.label}`;

            if (name === 'custom') {
                li.addEventListener('click', (e) => {
                    e.stopPropagation();
                    const panel = list.querySelector('.custom-theme-panel');
                    if (panel) panel.classList.toggle('open');
                });
            } else {
                li.addEventListener('click', () => {
                    applyTheme(name);
                    closeDropdown();
                });
            }

            list.appendChild(li);
        });

        buildCustomPanel(list);
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

        // Inject custom CSS before applyTheme sets data-theme to avoid flash
        if (currentTheme === 'custom') {
            const vals = loadCustomValues();
            injectCustomStyle(vals.accent, vals.bg, vals.text);
        }

        applyTheme(currentTheme);
        buildDropdown(currentTheme);

        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleDropdown();
        });

        document.addEventListener('click', (e) => {
            if (dropdownOpen && !e.target.closest('.theme-selector-dropdown')) {
                closeDropdown();
            }
        });
    }

    return { init, applyTheme };
})();

document.addEventListener('DOMContentLoaded', themeModule.init);
