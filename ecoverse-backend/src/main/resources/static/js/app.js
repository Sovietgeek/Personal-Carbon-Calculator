/**
 * ================================================================
 * ECOVERSE — Main Application Entry Point
 * Wires all modules together: Auth, Router, State, Notifications
 * ================================================================
 */

// ============================================================
// GLOBAL APP STATE
// ============================================================

const AppState = {
    user: null,
    weatherCache: null,
    currentTab: 'tab-dashboard'
};

// ============================================================
// APP INITIALIZATION
// ============================================================

document.addEventListener('DOMContentLoaded', () => {
    // Chart.js defaults
    Chart.defaults.color = '#5f8a70';
    Chart.defaults.borderColor = 'rgba(16,185,129,0.08)';
    Chart.defaults.font.family = "'Inter', sans-serif";

    initApp();
    Carbon.setupCalcListeners();
    handleResize();
    window.addEventListener('resize', handleResize);

    // Hide Google social-login button when OAuth is not configured (dummy-id).
    // Prevents a broken accounts.google.com redirect / CSP error.
    fetch(`${window.location.origin}/api/auth/oauth-status`)
        .then(r => r.json())
        .then(d => {
            const googleEnabled = !!(d && d.data && d.data.googleEnabled);
            if (!googleEnabled) {
                const socialBtns = document.querySelectorAll('.social-btn');
                socialBtns.forEach(btn => {
                    if (btn.classList.contains('social-btn--google') ||
                        btn.querySelector('.fa-google')) {
                        btn.style.display = 'none';
                    }
                });
                const divider = document.querySelector('.auth-divider');
                if (divider && socialBtns.length > 0 &&
                    [...socialBtns].every(b => b.style.display === 'none')) {
                    divider.style.display = 'none';
                }
            }
        })
        .catch(() => { /* keep buttons visible on network error — harmless */ });

    // Weather module init — sets up search autocomplete, Enter key, etc.
    AppState.weatherCache = null;
    if (typeof Weather !== 'undefined' && Weather.init) Weather.init();
    // Only auto-detect location AFTER login (weather API requires auth)
    // detectLocation will be called from initApp() after successful login

    // Set auth expired callback
    EcoAPI.setAuthExpiredCallback(() => {
        EcoVerse.showToast('Session expired. Please login again.', 'warning');
        EcoVerse.logout();
    });

    // Initialize theme
    Theme.init();
});

async function initApp() {
    AppState.user = EcoUtils.lsGet('eco_user', null);

    // Check for OAuth2 one-time code in URL (from Google OAuth redirect)
    const urlParams = new URLSearchParams(window.location.search);
    const oauthCode = urlParams.get('code');
    const oauthError = urlParams.get('error');
    const verifyToken = urlParams.get('verify_token');
    const resetToken = urlParams.get('reset_token');

    if (oauthCode) {
        // Exchange the one-time code for tokens
        handleOAuthCodeExchange(oauthCode);
        return; // Don't initialize app yet — wait for exchange to complete
    } else if (oauthError) {
        // OAuth failed (e.g., user denied access, or code expired)
        showToast('Google login failed. Please try again.', 'error');
        // Clean URL
        window.history.replaceState({}, document.title, window.location.pathname);
    } else if (verifyToken) {
        // Email verification: user clicked the link from the email
        handleEmailVerification(verifyToken);
        return;
    } else if (resetToken) {
        // Password reset: user clicked the link from the email
        handlePasswordReset(resetToken);
        return;
    }

    Shop.updateCartUI();

    if (AppState.user) {
        // User has a stored session — try to restore access token via httpOnly cookie
        if (!EcoAPI.isLoggedIn()) {
            const refreshed = await EcoAPI.silentRefresh();
            if (refreshed) {
                enterApp();
            } else {
                // Refresh token expired or invalid — clear stale user data, show login
                AppState.user = null;
                EcoUtils.lsRemove('eco_user');
                document.getElementById('auth-screen').style.display = 'flex';
                document.getElementById('app-screen').style.display = 'none';
                return;
            }
        } else {
            enterApp();
        }
    } else {
        document.getElementById('auth-screen').style.display = 'flex';
        document.getElementById('app-screen').style.display = 'none';
    }
}

/**
 * Exchange the one-time OAuth2 authorization code for tokens.
 * This is called when the user is redirected back from Google OAuth
 * with a ?code=ONE_TIME_CODE parameter in the URL.
 *
 * SECURITY: The code is single-use and expires in 30 seconds.
 * Tokens are returned in the response body, never in URL parameters.
 */
async function handleOAuthCodeExchange(code) {
    try {
        const response = await fetch(`${window.location.origin}/api/auth/oauth2/exchange`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ code }),
            credentials: 'include' // Send/receive httpOnly cookies
        });

        const data = await response.json();

        if (response.ok && data && data.success && data.data) {
            const authData = data.data;

            // Store access token in memory (refresh token is set as httpOnly cookie by server)
            if (authData.accessToken) {
                EcoAPI.setToken(authData.accessToken);
            }

            // Set user state
            if (authData.user) {
                AppState.user = authData.user;
                AppState.user.joined = AppState.user.joinedDate || AppState.user.joined || new Date().toISOString();
                AppState.user.carbonBudget = AppState.user.carbonBudget || 4.2;
                // All core features are free — isPremium is not used for gating
                AppState.user.goals = AppState.user.goals || { steps: 10000, sleep: 8, water: 3, calories: 2000 };
                EcoUtils.lsSet('eco_user', AppState.user);
            }

            // Clean the URL (remove ?code=... so it can't be seen/reused)
            window.history.replaceState({}, document.title, window.location.pathname);

            showToast('Welcome to EcoVerse!', 'success');
            enterApp();
        } else {
            // Code was invalid, expired, or already used
            const message = data?.message || 'Authentication failed. Please try again.';
            showToast(message, 'error');
            window.history.replaceState({}, document.title, window.location.pathname);

            // Show login screen
            document.getElementById('auth-screen').style.display = 'flex';
            document.getElementById('app-screen').style.display = 'none';
        }
    } catch (err) {
        console.error('OAuth code exchange failed:', err);
        showToast('Google login failed. Please try again.', 'error');
        window.history.replaceState({}, document.title, window.location.pathname);

        // Show login screen
        document.getElementById('auth-screen').style.display = 'flex';
        document.getElementById('app-screen').style.display = 'none';
    }
}

/**
 * Handle email verification token from the email link.
 * The email link format is: /?verify_token=TOKEN
 * This function calls the backend API to verify the token,
 * then shows the login screen with a success message.
 */
async function handleEmailVerification(token) {
    try {
        const response = await fetch(`${window.location.origin}/api/auth/verify?token=${encodeURIComponent(token)}`, {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
        });

        window.history.replaceState({}, document.title, window.location.pathname);

        if (response.ok) {
            showToast('Email verified successfully! You can now log in.', 'success');
        } else {
            const data = await response.json().catch(() => null);
            const message = data?.message || 'Verification failed. The link may have expired.';
            showToast(message, 'error');
        }
    } catch (err) {
        console.error('Email verification failed:', err);
        window.history.replaceState({}, document.title, window.location.pathname);
        showToast('Verification failed. Please try again.', 'error');
    }

    // Always show the login screen after verification (success or failure)
    document.getElementById('auth-screen').style.display = 'flex';
    document.getElementById('app-screen').style.display = 'none';
}

/**
 * Handle password reset token from the email link.
 * The email link format is: /?reset_token=TOKEN
 * Shows a password reset form for the user to enter a new password.
 */
async function handlePasswordReset(token) {
    // Store the reset token temporarily and show the reset password UI
    window._pendingResetToken = token;
    window.history.replaceState({}, document.title, window.location.pathname);
    showToast('Enter your new password below.', 'info');

    // Show a simple password reset prompt
    const newPassword = prompt('Enter your new password (minimum 8 characters):');
    if (!newPassword || newPassword.length < 8) {
        showToast('Password must be at least 8 characters.', 'error');
        document.getElementById('auth-screen').style.display = 'flex';
        document.getElementById('app-screen').style.display = 'none';
        return;
    }

    try {
        const response = await fetch(`${window.location.origin}/api/auth/reset-password`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token: token, password: newPassword })
        });

        const data = await response.json();

        if (response.ok && data.success) {
            showToast('Password reset successfully! Please log in with your new password.', 'success');
        } else {
            const message = data?.message || 'Password reset failed. The link may have expired.';
            showToast(message, 'error');
        }
    } catch (err) {
        console.error('Password reset failed:', err);
        showToast('Password reset failed. Please try again.', 'error');
    }

    // Show the login screen
    document.getElementById('auth-screen').style.display = 'flex';
    document.getElementById('app-screen').style.display = 'none';
}

function handleResize() {
    const b = document.getElementById('mobile-logout');
    if (b) b.style.display = window.innerWidth <= 768 ? 'inline-flex' : 'none';
}

// ============================================================
// TOAST NOTIFICATION
// ============================================================

function showToast(msg, type = 'info') {
    const box = document.getElementById('toast-container');
    if (!box) return;

    const t = document.createElement('div');
    t.className = `toast toast-${type}`;

    const icons = { success: 'fa-circle-check', error: 'fa-circle-xmark', warning: 'fa-triangle-exclamation', info: 'fa-circle-info' };
    const colors = { success: 'var(--success)', error: 'var(--danger)', warning: 'var(--warning)', info: 'var(--info)' };

    t.innerHTML = `<i class="fa-solid ${icons[type]}" style="color:${colors[type]};font-size:18px;flex-shrink:0;"></i><span>${EcoUtils.sanitize(msg)}</span>`;
    box.appendChild(t);
    requestAnimationFrame(() => t.classList.add('show'));

    setTimeout(() => {
        t.classList.remove('show');
        setTimeout(() => t.remove(), 400);
    }, 3500);
}

// Make it globally available
window.showToast = showToast;

// ============================================================
// TAB ROUTER
// ============================================================

function switchTab(tabId, el) {
    const role = (AppState.user?.role || '').toUpperCase();
    const isAdmin = role === 'ADMIN';

    // Guard: non-admins cannot access admin tab at all
    if (tabId === 'tab-admin' && !isAdmin) {
        showToast('Access restricted to administrators only', 'error');
        tabId = 'tab-dashboard';
    }

    // Guard: admins can only access admin panel or their profile
    if (isAdmin && tabId !== 'tab-admin' && tabId !== 'tab-profile') {
        tabId = 'tab-admin';
    }

    document.querySelectorAll('.tab-section').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.nav-item, .mobile-nav-item').forEach(n => n.classList.remove('active'));

    const tabEl = document.getElementById(tabId);
    if (tabEl) tabEl.classList.add('active');

    const s = document.querySelector(`.sidebar .nav-item[data-tab="${tabId}"]`);
    const m = document.querySelector(`.mobile-nav-item[data-tab="${tabId}"]`);
    if (s) s.classList.add('active');
    if (m) m.classList.add('active');

    AppState.currentTab = tabId;

    // Lazy-load tab content
    const actions = {
        'tab-dashboard': () => Dashboard.render(),
        'tab-carbon': () => Carbon.render(),
        'tab-health': () => Health.render(),
        'tab-weather': () => Weather.fetch(),
        'tab-news': () => News.fetch(),
        'tab-tips': () => renderTips(),
        'tab-shop': () => Shop.render(),
        'tab-achievements': () => Achievements.render(),
        'tab-seller': () => Seller.render(),
        'tab-admin': () => Admin.render(),
        'tab-profile': () => Profile.render()
    };

    if (actions[tabId]) actions[tabId]();

    const main = document.querySelector('.main-content');
    if (main) main.scrollTo({ top: 0, behavior: 'smooth' });

    toggleMobileSidebar(true);
}

function getNavEl(id) {
    return document.querySelector(`.sidebar .nav-item[data-tab="${id}"]`) ||
           document.querySelector(`.mobile-nav-item[data-tab="${id}"]`);
}

// ============================================================
// AUTH
// ============================================================

function toggleAuth(mode) {
    document.getElementById('login-box').style.display = mode === 'login' ? 'block' : 'none';
    document.getElementById('register-box').style.display = mode === 'register' ? 'block' : 'none';
}

function togglePasswordVisibility(id, btn) {
    const input = document.getElementById(id);
    const icon = btn.querySelector('i');
    if (!input) return;
    if (input.type === 'password') {
        input.type = 'text';
        icon.className = 'fa-solid fa-eye-slash';
    } else {
        input.type = 'password';
        icon.className = 'fa-solid fa-eye';
    }
}

// Password strength indicator
document.addEventListener('input', e => {
    if (e.target.id === 'reg-password') {
        const v = e.target.value;
        let s = 0;
        if (v.length >= 8) s++;
        if (/[A-Z]/.test(v)) s++;
        if (/[0-9]/.test(v)) s++;
        if (/[^A-Za-z0-9]/.test(v)) s++;

        const fill = document.getElementById('pw-strength-fill');
        const text = document.getElementById('pw-strength-text');
        const labels = ['Too short', 'Weak', 'Fair', 'Good', 'Strong'];
        const colors = ['var(--danger)', 'var(--danger)', 'var(--warning)', 'var(--info)', 'var(--success)'];

        if (fill) { fill.style.width = `${(s / 4) * 100}%`; fill.style.background = colors[s]; }
        if (text) { text.innerText = labels[s]; text.style.color = colors[s]; }
    }
});

async function login() {
    const email = document.getElementById('login-email')?.value?.trim();
    const password = document.getElementById('login-password')?.value;

    if (!email || !password) return showToast('Fill all fields', 'error');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return showToast('Please enter a valid email address', 'error');

    try {
        const result = await EcoAPI.login(email, password);
        if (result && result.success && result.data) {
            AppState.user = result.data.user || result.data;
            AppState.user.joined = AppState.user.joinedDate || AppState.user.joined || new Date().toISOString();
            AppState.user.carbonBudget = AppState.user.carbonBudget || 4.2;
            // All core features are free — isPremium is not used for gating
            AppState.user.goals = AppState.user.goals || { steps: 10000, sleep: 8, water: 3, calories: 2000 };
            EcoUtils.lsSet('eco_user', AppState.user);
            enterApp();
            showToast('Welcome back!', 'success');
            return;
        }
        if (result && result.message) {
            showToast(result.message, 'error');
            return;
        }
    } catch (err) {
        // API unavailable
        if (err?.message && err.message !== 'Rate limited') {
            showToast(err.message, 'error');
            return;
        }
    }

    showToast('Login failed. Please check your credentials and try again.', 'error');
}

async function forgotPassword() {
    const emailInput = document.getElementById('login-email');
    const email = emailInput?.value?.trim();
    if (!email) return showToast('Enter your email address first', 'error');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return showToast('Please enter a valid email address', 'error');

    try {
        await EcoAPI.forgotPassword(email);
        showToast('If an account exists, a password reset link has been sent.', 'success');
    } catch (err) {
        // Keep the response generic to prevent account enumeration. Rate-limit
        // feedback is already shown by the API layer.
        if (err?.message !== 'Rate limited') {
            showToast('If an account exists, a password reset link has been sent.', 'success');
        }
    }
}

async function register() {
    const name = document.getElementById('reg-name')?.value?.trim();
    const email = document.getElementById('reg-email')?.value?.trim();
    const country = document.getElementById('reg-country')?.value;
    const password = document.getElementById('reg-password')?.value;
    const agreeTerms = document.getElementById('agree-terms')?.checked;

    if (!name || !email || !country) return showToast('Fill all fields', 'error');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return showToast('Please enter a valid email address', 'error');
    if (password.length < 8) return showToast('Password must be at least 8 characters', 'error');
    if (!agreeTerms) return showToast('Please agree to Terms', 'error');

    try {
        const result = await EcoAPI.register(name, email, password, country);
        if (result && result.success) {
            // Registration creates an active account. The user explicitly
            // signs in next; registration never stores or returns credentials.
            showToast('Account created successfully. Please sign in with your email and password.', 'success');
            const loginEmail = document.getElementById('login-email');
            if (loginEmail) loginEmail.value = email;
            const registerPassword = document.getElementById('reg-password');
            if (registerPassword) registerPassword.value = '';
            toggleAuth('login');
            return;
        }
        if (result && result.message) {
            showToast(result.message, 'error');
            return;
        }
    } catch (err) {
        if (err?.message === 'Rate limited') return;
        if (err?.message) {
            showToast(err.message, 'error');
            return;
        }
    }

    showToast('Registration failed. Please try again.', 'error');
}

function googleLogin() {
    // Redirect to Spring Security's Google OAuth2 authorization endpoint
    // The backend will handle the OAuth flow and redirect back with a one-time code
    window.location.href = '/oauth2/authorization/google';
}

function appleLogin() {
    showToast('Apple Sign-In requires backend setup. Please use email login.', 'info');
}

async function logout() {
    AppState.user = null;
    await EcoAPI.logout();
    EcoUtils.lsRemove('eco_user');
    localStorage.removeItem('eco_user');

    // Clear admin panel content and restore sidebar
    const adminContent = document.getElementById('admin-content');
    if (adminContent) adminContent.innerHTML = '';
    const sidebar = document.getElementById('sidebar');
    if (sidebar) sidebar.classList.remove('admin-mode');

    document.getElementById('app-screen').style.display = 'none';
    document.getElementById('auth-screen').style.display = 'flex';

    ['login-email', 'login-password', 'reg-name', 'reg-email', 'reg-password'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });

    showToast('Logged out', 'info');
}

function enterApp() {
    document.getElementById('auth-screen').style.display = 'none';
    document.getElementById('app-screen').style.display = 'flex';

    const user = AppState.user;
    if (!user) return;

    EcoUtils.setText('user-display-name', user.name || 'User');
    EcoUtils.setText('sidebar-user-name', user.name || 'User');
    EcoUtils.setText('profile-name', user.name || 'User');
    EcoUtils.setText('profile-email', user.email || '--');
    EcoUtils.setText('profile-joined', `Joined: ${new Date(user.joined || Date.now()).toLocaleDateString()}`);
    const settingsName = document.getElementById('settings-name');
    if (settingsName) settingsName.value = user.name || '';

    const sidebarAvatar = document.getElementById('sidebar-avatar');
    if (sidebarAvatar) sidebarAvatar.innerText = (user.name || 'U').charAt(0).toUpperCase();

    const sidebarPlan = document.getElementById('sidebar-user-plan');
    if (sidebarPlan) sidebarPlan.innerHTML = '<i class="fa-solid fa-seedling" style="color:var(--success);margin-right:4px;"></i>Free';

    const role = (user.role || '').toUpperCase();
    const isAdmin = role === 'ADMIN';
    const sidebar = document.getElementById('sidebar');

    if (isAdmin) {
        // ADMIN MODE: hide all user nav, show only admin nav + settings
        if (sidebar) sidebar.classList.add('admin-mode');
        const navBusiness = document.getElementById('nav-section-business');
        const navSeller = document.getElementById('nav-seller');
        if (navBusiness) navBusiness.style.display = 'none';
        if (navSeller) navSeller.style.display = 'none';

        // Admin goes directly to admin panel — full screen admin experience
        switchTab('tab-admin');
    } else {
        // USER MODE: normal app, admin hidden completely
        if (sidebar) sidebar.classList.remove('admin-mode');
        const navBusiness = document.getElementById('nav-section-business');
        const navSeller = document.getElementById('nav-seller');
        const navAdmin = document.getElementById('nav-admin');
        const isSeller = role === 'SELLER';
        if (navBusiness) navBusiness.style.display = isSeller ? '' : 'none';
        if (navSeller) navSeller.style.display = isSeller ? '' : 'none';
        if (navAdmin) navAdmin.style.display = 'none';

        Dashboard.render();

        // Initialize news with user's location
        if (typeof News !== 'undefined' && News.initFromProfile) {
            News.initFromProfile();
        }

        // Auto-detect or restore location for weather + news
        if (typeof Weather !== 'undefined') {
            const u = AppState.user;
            if (u && u.latitude && u.longitude) {
                AppState.weatherCache = {
                    lat: u.latitude,
                    lon: u.longitude,
                    city: u.city || null,
                    state: u.state || null
                };
                Weather.fetch();
            } else if (Weather.detectLocation) {
                Weather.detectLocation();
            }
        }
    }
}

function openPremium() {
    switchTab('tab-premium', getNavEl('tab-premium'));
}

// ============================================================
// NOTIFICATIONS
// ============================================================

function openNotifications() {
    document.getElementById('notification-panel')?.classList.add('open');
    document.getElementById('notification-overlay')?.classList.add('open');
}

function closeNotifications() {
    document.getElementById('notification-panel')?.classList.remove('open');
    document.getElementById('notification-overlay')?.classList.remove('open');
}

// ============================================================
// MOBILE SIDEBAR
// ============================================================

function toggleMobileSidebar(forceClose = false) {
    const s = document.getElementById('sidebar');
    const o = document.getElementById('mobile-sidebar-overlay');
    if (!s || !o) return;
    if (forceClose || s.classList.contains('sidebar-open')) {
        s.classList.remove('sidebar-open');
        o.classList.remove('open');
    } else {
        s.classList.add('sidebar-open');
        o.classList.add('open');
    }
}

// ============================================================
// TIPS & NOTES (kept in app.js since it's a smaller feature)
// ============================================================

// Tips come from the server API (GET /api/notes/tip and GET /api/notes/tips/history).
// No client-side TIPS array — server is authoritative.

function renderTips() {
    // Fetch daily tip from server
    loadDailyTip();
    // Fetch notes from server
    loadNotes();
    // Fetch tip history from server
    loadTipHistory();
}

async function loadDailyTip() {
    try {
        const result = await EcoAPI.apiGet('/api/notes/tip');
        if (result && result.success && result.data) {
            EcoUtils.setText('tip-today-date', new Date().toLocaleDateString('en', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' }));
            EcoUtils.setText('tip-today-title', 'Daily Sustainability Insight');
            EcoUtils.setText('tip-today-text', result.data);
        }
    } catch (err) {
        EcoUtils.setText('tip-today-text', 'Track your carbon footprint to make a difference!');
    }
}

async function loadNotes() {
    const list = document.getElementById('notes-list');
    const empty = document.getElementById('notes-empty');
    if (!list) return;

    try {
        const result = await EcoAPI.apiGet('/api/notes');
        if (result && result.success && result.data) {
            const notes = result.data;
            if (!notes.length) {
                list.innerHTML = '';
                if (empty) empty.style.display = 'flex';
                return;
            }
            if (empty) empty.style.display = 'none';
            list.innerHTML = notes.map(n =>
                `<div class="note-item">
                    <div class="note-item-header">
                        <div class="note-item-title">${EcoUtils.sanitize(n.title)}</div>
                        <div class="note-item-tag">${n.tag || 'note'}</div>
                    </div>
                    <div class="note-item-body">${EcoUtils.sanitize(n.body)}</div>
                    <div class="note-item-date">${n.createdAt ? new Date(n.createdAt).toLocaleString() : ''} <button class="auth-link" style="margin-left:auto;" data-action="deleteNote" data-id="${n.id}">Delete</button></div>
                </div>`
            ).join('');
        }
    } catch (err) {
        if (empty) empty.style.display = 'flex';
    }
}

async function loadTipHistory() {
    const calendar = document.getElementById('tips-calendar');
    if (!calendar) return;

    try {
        const result = await EcoAPI.apiGet('/api/notes/tips/history?days=7');
        if (result && result.success && result.data) {
            calendar.innerHTML = result.data.map(entry =>
                `<div class="tip-history-item">
                    <div class="tip-history-date">${EcoUtils.sanitize(entry.date || '')}</div>
                    <div class="tip-history-text">${EcoUtils.sanitize(entry.tip || '')}</div>
                </div>`
            ).join('');
        }
    } catch (err) {
        // Tip history is non-critical
    }
}

function openNoteEditor() {
    document.getElementById('note-editor').style.display = 'block';
    document.getElementById('note-title')?.focus();
}

function closeNoteEditor() {
    document.getElementById('note-editor').style.display = 'none';
}

async function saveNote() {
    const title = document.getElementById('note-title')?.value?.trim();
    const body = document.getElementById('note-body')?.value?.trim();
    const tag = document.getElementById('note-tag')?.value || 'observation';
    if (!title || !body) return showToast('Fill title & body', 'error');

    try {
        const result = await EcoAPI.apiPost('/api/notes', { title, body, tag });
        if (result && result.success) {
            document.getElementById('note-title').value = '';
            document.getElementById('note-body').value = '';
            closeNoteEditor();
            renderTips();
            showToast('Note saved!', 'success');
        } else {
            showToast('Failed to save note', 'error');
        }
    } catch (e) {
        showToast('Failed to save note', 'error');
    }
}

async function deleteNote(id) {
    try {
        await EcoAPI.apiDelete(`/api/notes/${id}`);
        renderTips();
    } catch (e) {
        showToast('Failed to delete note', 'error');
    }
}

function shareTip() {
    const text = document.getElementById('tip-today-text')?.innerText;
    if (navigator.clipboard && text) {
        navigator.clipboard.writeText(text);
        showToast('Tip copied!', 'success');
    }
}

function bookmarkTip() {
    showToast('Saved to bookmarks!', 'success');
}

// ============================================================
// GLOBAL EXPORTS (used by the external event delegation system)
// ============================================================

const EcoVerse = {
    showToast, switchTab, getNavEl, toggleAuth, togglePasswordVisibility,
    login, register, forgotPassword, googleLogin, appleLogin, logout, openPremium,
    openNotifications, closeNotifications, toggleMobileSidebar,
    toggleTheme: () => Theme.toggle(),
    renderTips, openNoteEditor, closeNoteEditor, saveNote, deleteNote, shareTip, bookmarkTip
};
window.EcoVerse = EcoVerse;

// Expose legacy-compatible globals for the external event delegation system
window.showToast = showToast;
window.switchTab = switchTab;
window.getNavEl = getNavEl;
window.toggleAuth = toggleAuth;
window.togglePasswordVisibility = togglePasswordVisibility;
window.login = login;
window.register = register;
window.forgotPassword = forgotPassword;
window.googleLogin = googleLogin;
window.appleLogin = appleLogin;
window.logout = logout;
window.openPremium = openPremium;
window.openNotifications = openNotifications;
window.closeNotifications = closeNotifications;
window.toggleMobileSidebar = toggleMobileSidebar;
window.toggleTheme = () => Theme.toggle();
window.changeDashChart = (p, btn) => Dashboard.changeChart(p, btn);
window.switchCarbonCat = (c, btn) => Carbon.switchCat(c, btn);
window.addCarbonEntry = (cat) => Carbon.addEntry(cat);
window.setCarbonTime = (t, btn) => Carbon.setTime(t, btn);
window.clearTodayCarbon = () => Carbon.clearToday();
window.deleteCarbonEntry = (id) => Carbon.deleteEntry(id);
window.switchHealthTab = (tab, btn) => Health.switchTab(tab, btn);
window.logHealth = (type) => Health.log(type);
window.addWater = (ml) => Health.addWater(ml);
window.calculateBMI = () => Health.calculateBMI();
window.searchWeather = () => Weather.search();
window.detectLocation = () => Weather.detectLocation();
window.renderNews = () => News.loadNews();
window.filterNews = () => News.filter();
window.setNewsCategory = (c, btn) => News.setCategory(c, btn);
window.loadMoreNews = () => News.loadMore();
window.filterShopProducts = () => Shop.filterProducts();
window.setShopCategory = (c, btn) => Shop.setCategory(c, btn);
window.loadMoreProducts = () => Shop.loadMore();
window.openCart = () => Shop.openCart();
window.closeCart = () => Shop.closeCart();
window.openCheckout = () => Shop.openCheckout();
window.closeCheckout = () => Shop.closeCheckout();
window.placeOrder = () => Shop.placeOrder();
window.closeOrderSuccess = () => Shop.closeOrderSuccess();
window.openProductDetail = (id) => Shop.openDetail(id);
window.closeProductDetail = () => Shop.closeDetail();
window.changeQty = (d) => Shop.changeQty(d);
window.addToCartFromDetail = () => Shop.addToCartFromDetail();
window.openSellProductModal = () => Shop.openSellModal();
window.closeSellProductModal = () => Shop.closeSellModal();
window.submitProduct = () => Shop.submitProduct();
window.updateProfile = () => Profile.updateProfile();
window.updateCarbonBudget = () => Profile.updateCarbonBudget();
window.updateHealthGoals = () => Profile.updateHealthGoals();
window.exportData = () => Profile.exportData();
window.clearAllData = () => Profile.clearAllData();
window.deleteAccount = () => Profile.deleteAccount();
window.closeBadgeModal = () => Achievements.closeBadgeModal();
window.renderTips = renderTips;
window.openNoteEditor = openNoteEditor;
window.closeNoteEditor = closeNoteEditor;
window.saveNote = saveNote;
window.deleteNote = deleteNote;
window.shareTip = shareTip;
window.bookmarkTip = bookmarkTip;
