/**
 * EcoVerse — Theme Module
 * Dark/light theme toggle, CSS custom property updates
 */

const Theme = (() => {
    const STORAGE_KEY = 'eco_theme';

    function init() {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (saved === 'light') {
            document.body.classList.add('light-theme');
            updateIcon(true);
        }
    }

    function toggle() {
        document.body.classList.toggle('light-theme');
        const isLight = document.body.classList.contains('light-theme');
        localStorage.setItem(STORAGE_KEY, isLight ? 'light' : 'dark');
        updateIcon(isLight);

        // Sync settings checkbox
        const cb = document.getElementById('settings-dark-mode');
        if (cb) cb.checked = !isLight;

        // Re-render charts with new theme colors
        setTimeout(() => {
            Dashboard.render();
        }, 100);
    }

    function updateIcon(isLight) {
        const icon = document.getElementById('theme-icon');
        if (icon) icon.className = isLight ? 'fa-solid fa-sun' : 'fa-solid fa-moon';
    }

    function isDark() {
        return !document.body.classList.contains('light-theme');
    }

    return { init, toggle, updateIcon, isDark };
})();
