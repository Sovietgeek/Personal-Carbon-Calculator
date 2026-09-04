/**
 * EcoVerse — Shared Utilities
 * Formatters, validators, debounce, throttle, date helpers, DOM helpers, chart helpers
 */

const EcoUtils = {
    // Chart instance storage (shared across all modules)
    chartInstances: {},

    // ============================================================
    // DATE HELPERS
    // ============================================================

    today() {
        return new Date().toISOString().split('T')[0];
    },

    fmtDate(d) {
        if (!d) return '';
        return new Date(d).toISOString().split('T')[0];
    },

    // ============================================================
    // NUMBER FORMATTING
    // ============================================================

    formatCarbon(kg) {
        if (kg === null || kg === undefined) return '0.00 kg';
        if (Math.abs(kg) >= 1000) return (kg / 1000).toFixed(2) + ' t';
        return kg.toFixed(2) + ' kg';
    },

    formatCurrency(amount) {
        if (amount === null || amount === undefined) return '₹0';
        return '₹' + amount.toLocaleString('en-IN', { maximumFractionDigits: 0 });
    },

    formatNumber(num) {
        if (num === null || num === undefined) return '0';
        return num.toLocaleString('en-IN');
    },

    formatPercent(value) {
        if (value === null || value === undefined) return '0%';
        return Math.round(value) + '%';
    },

    // ============================================================
    // DATE FORMATTING
    // ============================================================

    formatDate(dateStr) {
        if (!dateStr) return '';
        const date = new Date(dateStr);
        return date.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
    },

    formatTime(dateStr) {
        if (!dateStr) return '';
        const date = new Date(dateStr);
        return date.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
    },

    formatRelative(dateStr) {
        if (!dateStr) return '';
        const date = new Date(dateStr);
        const now = new Date();
        const diff = Math.floor((now - date) / 1000);
        if (diff < 60) return 'just now';
        if (diff < 3600) return Math.floor(diff / 60) + 'm ago';
        if (diff < 86400) return Math.floor(diff / 3600) + 'h ago';
        if (diff < 604800) return Math.floor(diff / 86400) + 'd ago';
        return EcoUtils.formatDate(dateStr);
    },

    getGreeting() {
        const hour = new Date().getHours();
        if (hour < 12) return 'Good Morning';
        if (hour < 17) return 'Good Afternoon';
        return 'Good Evening';
    },

    // ============================================================
    // AQI HELPERS
    // ============================================================

    getAqiLabel(aqi) {
        if (aqi === null || aqi === undefined) return 'Unknown';
        if (aqi <= 20) return 'Good';
        if (aqi <= 40) return 'Fair';
        if (aqi <= 60) return 'Moderate';
        if (aqi <= 80) return 'Poor';
        if (aqi <= 100) return 'Very Poor';
        return 'Hazardous';
    },

    getAqiColor(aqi) {
        if (aqi === null || aqi === undefined) return '#9ca3af';
        if (aqi <= 20) return '#22c55e';
        if (aqi <= 40) return '#84cc16';
        if (aqi <= 60) return '#eab308';
        if (aqi <= 80) return '#f97316';
        if (aqi <= 100) return '#ef4444';
        return '#7f1d1d';
    },

    getUvLabel(uv) {
        if (uv === null || uv === undefined) return 'Unknown';
        if (uv <= 2) return 'Low';
        if (uv <= 5) return 'Moderate';
        if (uv <= 7) return 'High';
        if (uv <= 10) return 'Very High';
        return 'Extreme';
    },

    // ============================================================
    // CARBON COMPARISONS (Fun facts)
    // ============================================================

    getCarbonComparison(kg) {
        if (kg <= 0) return { text: 'Zero emissions! 🎉', icon: '🌟' };
        if (kg <= 1) return { text: `≈ ${Math.round(kg / 0.0088)} phone charges`, icon: '🔋' };
        if (kg <= 5) return { text: `≈ ${Math.round(kg / 0.04)} cups of coffee`, icon: '☕' };
        if (kg <= 20) return { text: `≈ ${Math.round(kg / 0.21)} km by car`, icon: '🚗' };
        if (kg <= 100) return { text: `≈ ${(kg / 70).toFixed(1)} iPhones made`, icon: '📱' };
        return { text: `≈ ${(kg / 150).toFixed(1)} Delhi-Mumbai flights`, icon: '✈️' };
    },

    // ============================================================
    // PERFORMANCE HELPERS
    // ============================================================

    debounce(fn, delay = 300) {
        let timer;
        return function (...args) {
            clearTimeout(timer);
            timer = setTimeout(() => fn.apply(this, args), delay);
        };
    },

    throttle(fn, limit = 1000) {
        let inThrottle;
        return function (...args) {
            if (!inThrottle) {
                fn.apply(this, args);
                inThrottle = true;
                setTimeout(() => inThrottle = false, limit);
            }
        };
    },

    // ============================================================
    // DOM HELPERS
    // ============================================================

    $(selector) {
        return document.querySelector(selector);
    },

    $$(selector) {
        return document.querySelectorAll(selector);
    },

    show(el) {
        if (typeof el === 'string') el = document.getElementById(el);
        if (el) el.style.display = '';
    },

    hide(el) {
        if (typeof el === 'string') el = document.getElementById(el);
        if (el) el.style.display = 'none';
    },

    /** Safely set textContent of an element by ID */
    setText(id, text) {
        const el = document.getElementById(id);
        if (el) el.innerText = text;
    },

    // ============================================================
    // CHART HELPERS
    // ============================================================

    destroyChart(id) {
        if (EcoUtils.chartInstances[id]) {
            EcoUtils.chartInstances[id].destroy();
            delete EcoUtils.chartInstances[id];
        }
    },

    // ============================================================
    // INPUT SANITIZATION (XSS prevention on frontend)
    // ============================================================

    sanitize(input) {
        if (input === null || input === undefined) return input;
        if (typeof input === 'string') {
            return input
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#x27;');
        }
        return input;
    },

    sanitizeObject(obj) {
        if (obj === null || obj === undefined) return obj;
        if (typeof obj === 'string') return EcoUtils.sanitize(obj);
        if (Array.isArray(obj)) return obj.map(EcoUtils.sanitizeObject);
        if (typeof obj === 'object') {
            const clean = {};
            for (const key in obj) {
                if (obj.hasOwnProperty(key)) {
                    clean[key] = EcoUtils.sanitizeObject(obj[key]);
                }
            }
            return clean;
        }
        return obj;
    },

    // ============================================================
    // LOCAL STORAGE (safe, keys are passed with prefix by callers)
    // ============================================================

    lsGet(key, defaultValue = null) {
        try {
            const item = localStorage.getItem(key);
            return item !== null ? JSON.parse(item) : defaultValue;
        } catch (e) {
            return defaultValue;
        }
    },

    lsSet(key, value) {
        try {
            localStorage.setItem(key, JSON.stringify(value));
        } catch (e) { /* quota exceeded */ }
    },

    lsRemove(key) {
        try {
            localStorage.removeItem(key);
        } catch (e) { /* ignore */ }
    },

    // Legacy aliases
    saveLocal(key, value) { EcoUtils.lsSet(key, value); },
    loadLocal(key, defaultValue = null) { return EcoUtils.lsGet(key, defaultValue); }
};

// Make available globally
window.EcoUtils = EcoUtils;
