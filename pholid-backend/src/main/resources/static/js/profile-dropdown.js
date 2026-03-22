/** Copyright © 2026 Pholid - SPDX-License-Identifier: Apache-2.0
 * Pholid Profile Dropdown
 * Handles avatar dropdown toggle, outside-click close, initials fallback,
 * and avatar upload with cache-bust.
 * 
 * Expects window.PHOLID_USER to be set by the template:
 * window.PHOLID_USER = { username: "...", fullName: "..." };
 */

(function () {

    // ── Initials helper ───────────────────────────────────────────────────────

    function getInitials(name) {
        if (!name || !name.trim()) return '?';
        const parts = name.trim().split(/\s+/);
        if (parts.length >= 2) {
            return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
        }
        return name.substring(0, 2).toUpperCase();
    }

    function applyInitials(imgId, initialsId, name) {
        const img      = document.getElementById(imgId);
        const initials = document.getElementById(initialsId);
        if (!img || !initials) return;

        initials.textContent = getInitials(name);

        // Start hidden, show initials; reveal image only if it loads successfully
        img.style.display      = 'none';
        initials.style.display = 'flex';

        img.onload  = () => { img.style.display = 'block'; initials.style.display = 'none'; };
        img.onerror = () => { img.style.display = 'none';  initials.style.display = 'flex'; };

        // Re-trigger load in case browser already settled on cached 404
        const src = img.getAttribute('data-src');
        if (src) img.src = src;
    }

    // ── Dropdown toggle ───────────────────────────────────────────────────────

    function toggleAvatarDropdown() {
        const dd = document.getElementById('avatarDropdown');
        if (!dd) return;
        dd.classList.toggle('avatar-dropdown--open');
    }

    document.addEventListener('click', function (e) {
        const container = document.getElementById('avatarDropdownContainer');
        const dd        = document.getElementById('avatarDropdown');
        if (!container || !dd) return;
        if (!container.contains(e.target)) {
            dd.classList.remove('avatar-dropdown--open');
        }
    });

    // ── Avatar upload ─────────────────────────────────────────────────────────

    async function uploadAvatar(input) {
        if (!input.files || !input.files[0]) return;

        const formData = new FormData();
        formData.append('avatar', input.files[0]);

        try {
            const res = await fetch('/api/profile/avatar', { method: 'POST', body: formData });
            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                alert(err.error || 'Upload failed. Please try again.');
                return;
            }

            // Cache-bust both avatar images so they reflect the new upload immediately
            const username  = (window.PHOLID_USER || {}).username || '';
            const timestamp = Date.now();
            const newSrc    = `/api/profile/avatar/${encodeURIComponent(username)}?t=${timestamp}`;

            ['avatarImg', 'avatarImgLarge'].forEach(id => {
                const el = document.getElementById(id);
                if (el) {
                    el.setAttribute('data-src', newSrc);
                    el.src = newSrc;
                }
            });
        } catch (e) {
            alert('Upload failed. Please try again.');
        } finally {
            // Reset so the same file can be re-selected after an error
            input.value = '';
        }
    }

    // ── Init on DOM ready ─────────────────────────────────────────────────────

    document.addEventListener('DOMContentLoaded', function () {
        const user = window.PHOLID_USER || {};
        applyInitials('avatarImg',      'avatarInitials',      user.fullName || user.username || '?');
        applyInitials('avatarImgLarge', 'avatarInitialsLarge', user.fullName || user.username || '?');
    });

    // Expose to inline onclick handlers
    window.toggleAvatarDropdown = toggleAvatarDropdown;
    window.uploadAvatar         = uploadAvatar;

}());
