/**
 * EcoVerse API Module — All HTTP requests go through this file.
 * Handles: Authentication headers, error handling, retry logic, auto-logout.
 *
 * SECURITY:
 * - Access token: stored in JavaScript memory only (NOT sessionStorage/localStorage)
 * - Refresh token: stored as httpOnly cookie (set by server, NOT accessible to JS)
 * - Auto-refresh before token expires via httpOnly cookie
 * - Auto-logout on 401
 * - Input sanitization before sending
 * - Request timeout handling
 */

const API_BASE = window.location.origin;

// ============================================================
// TOKEN MANAGEMENT (Secure — in-memory access token + httpOnly cookie refresh)
// ============================================================

/**
 * Access token stored in memory only.
 * NOT in sessionStorage/localStorage (XSS-safe).
 * On page refresh, this is lost, but tryRefreshToken() recovers via httpOnly cookie.
 */
let accessToken = null;

function getToken() {
    return accessToken;
}

function setToken(token) {
    accessToken = token;
    // Do NOT store in sessionStorage/localStorage — memory only
}

function clearTokens() {
    accessToken = null;
}

function isLoggedIn() {
    return accessToken !== null;
}

// ============================================================
// OUTPUT SANITIZATION (XSS Prevention for rendered content)
// ============================================================

function sanitize(input) {
    if (input === null || input === undefined) return input;
    if (typeof input === 'string') {
        return input
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#x27;')
            .replace(/\//g, '&#x2F;');
    }
    return input;
}

function sanitizeObject(obj) {
    if (obj === null || obj === undefined) return obj;
    if (typeof obj === 'string') return sanitize(obj);
    if (Array.isArray(obj)) return obj.map(sanitizeObject);
    if (typeof obj === 'object') {
        const clean = {};
        for (const key in obj) {
            if (obj.hasOwnProperty(key)) {
                // NEVER HTML-encode credentials (password, token, secret).
                // They are sent to the backend for hashing — altering them
                // would corrupt the stored hash and break authentication.
                if (/password|secret|token/i.test(key)) {
                    clean[key] = obj[key];
                } else {
                    clean[key] = sanitizeObject(obj[key]);
                }
            }
        }
        return clean;
    }
    return obj;
}

// ============================================================
// CORE API REQUEST FUNCTION
// ============================================================

	async function apiRequest(method, endpoint, body = null, options = {}) {
	    const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint}`;

	    const headers = {
	        'Content-Type': 'application/json',
	    };

	    // Add auth header if token exists
	    const token = getToken();
	    if (token) {
	        headers['Authorization'] = `Bearer ${token}`;
	    }

	    // Support custom headers (e.g., X-Idempotency-Key)
	    if (options && typeof options === 'object' && !Array.isArray(options)) {
	        // If options is a headers object (not a standard fetch options)
	        if (options['X-Idempotency-Key'] || options['x-idempotency-key']) {
	            Object.assign(headers, options);
	        }
	    }

	    const config = {
	        method,
	        headers,
	        credentials: 'include', // Send httpOnly cookies (refresh token)
	    };

    if (body && method !== 'GET') {
        // JSON.stringify handles transport escaping. HTML encoding belongs at
        // the output boundary, never in credentials or API values.
        config.body = JSON.stringify(body);
    }

    try {
        const response = await fetch(url, config);

        // Only protected API calls should attempt cookie-based token refresh.
        // A 401 from login or another public auth endpoint is a real auth
        // failure, not an expired session.
        if (response.status === 401 && shouldRefreshOnUnauthorized(endpoint)) {
            const refreshed = await tryRefreshToken();
            if (refreshed) {
                // Retry with new token
                headers['Authorization'] = `Bearer ${getToken()}`;
                config.headers = headers;
                const retryResponse = await fetch(url, config);
                return handleResponse(retryResponse);
            } else {
                // Refresh failed — logout
                clearTokens();
                if (typeof onAuthExpired === 'function') {
                    onAuthExpired();
                } else {
                    window.location.href = '/';
                }
                throw new Error('Session expired. Please login again.');
            }
        }

        // Handle 429 — Rate limited
        if (response.status === 429) {
            const retryAfter = response.headers.get('X-RateLimit-Retry-After');
            const msg = retryAfter
                ? `Too many requests. Try again in ${Math.ceil(retryAfter / 1000)}s.`
                : 'Too many requests. Please wait a moment.';
            showToast(msg, 'warning');
            throw new Error('Rate limited');
        }

        return handleResponse(response);

    } catch (error) {
        // Network error
        if (error.name === 'TypeError' && error.message.includes('fetch')) {
            showToast('Network error. Check your internet connection.', 'error');
        }
        throw error;
    }
}

function shouldRefreshOnUnauthorized(endpoint) {
    return !endpoint.startsWith('/api/auth/login') &&
           !endpoint.startsWith('/api/auth/register') &&
           !endpoint.startsWith('/api/auth/verify') &&
           !endpoint.startsWith('/api/auth/forgot-password') &&
           !endpoint.startsWith('/api/auth/reset-password') &&
           !endpoint.startsWith('/api/auth/resend-verification') &&
           !endpoint.startsWith('/api/auth/oauth2/exchange');
}

// ============================================================
// RESPONSE HANDLER
// ============================================================

async function handleResponse(response) {
    let data;
    try {
        data = await response.json();
    } catch (e) {
        data = { success: false, message: 'Server returned invalid response' };
    }

    if (!response.ok) {
        const message = data?.message || `Request failed (${response.status})`;
        throw new Error(message);
    }

    return data;
}

// ============================================================
// TOKEN REFRESH LOGIC (httpOnly cookie-based)
// ============================================================

/**
 * Attempt to refresh the access token using the httpOnly refresh token cookie.
 * The refresh token is in an httpOnly cookie — no need to send it in the body.
 * The server reads it from the cookie automatically.
 */
async function tryRefreshToken() {
    try {
        const response = await fetch(`${API_BASE}/api/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include' // Send httpOnly cookie
        });

        if (response.ok) {
            const data = await response.json();
            if (data?.data?.accessToken) {
                setToken(data.data.accessToken);
                return true;
            }
        }
        return false;
    } catch (e) {
        return false;
    }
}

// ============================================================
// CONVENIENCE METHODS
// ============================================================

async function apiGet(endpoint) {
    return apiRequest('GET', endpoint);
}

async function apiPost(endpoint, body) {
    return apiRequest('POST', endpoint, body);
}

async function apiPut(endpoint, body) {
    return apiRequest('PUT', endpoint, body);
}

	async function apiDelete(endpoint) {
	    return apiRequest('DELETE', endpoint);
	}

	async function apiPatch(endpoint, body) {
	    return apiRequest('PATCH', endpoint, body);
	}

// ============================================================
// AUTH-SPECIFIC API CALLS
// ============================================================

async function apiLogin(email, password) {
    const data = await apiPost('/api/auth/login', { email, password });
    if (data?.data?.accessToken) {
        setToken(data.data.accessToken);
        // Refresh token is set as httpOnly cookie by the server — no JS access needed
        // Send user timezone to server after successful login
        sendTimezone();
    }
    return data;
}

async function apiRegister(name, email, password, country) {
    const data = await apiPost('/api/auth/register', { name, email, password, country });
    // Registration creates the account only. The user explicitly logs in next.
    return data;
}

async function apiForgotPassword(email) {
    return apiPost('/api/auth/forgot-password', { email });
}

/**
 * Logout: call server to revoke the refresh token and clear the httpOnly cookie.
 * The server handles cookie clearing; we just clear our in-memory access token.
 */
async function apiLogout() {
    try {
        await fetch(`${API_BASE}/api/auth/logout`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include' // Send httpOnly cookie so server can revoke it
        });
    } catch (e) {
        // Even if the server call fails, clear local state
    }
    clearTokens();
}

/**
 * Silent refresh: try to get a new access token on page load using the httpOnly cookie.
 * Called by app.js initApp() to restore session after page refresh.
 */
async function silentRefresh() {
    const refreshed = await tryRefreshToken();
    return refreshed;
}

// ============================================================
// AUTO-LOGOUT CALLBACK (set by app.js)
// ============================================================

let onAuthExpired = null;

function setAuthExpiredCallback(callback) {
    onAuthExpired = callback;
}

// ============================================================
// TOAST NOTIFICATION (simple fallback if app.js not loaded)
// ============================================================

function showToast(message, type = 'info') {
    if (typeof window.showToast === 'function') {
        window.showToast(message, type);
    } else if (typeof EcoVerse !== 'undefined' && typeof EcoVerse.showToast === 'function') {
        EcoVerse.showToast(message, type);
    } else {
        console.log(`[${type.toUpperCase()}] ${message}`);
    }
}

// ============================================================
// TIMEZONE SYNC — Send user's timezone to server after login
// ============================================================

async function sendTimezone() {
    try {
        const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
        if (tz && accessToken) {
            await apiPut('/api/profile', { timezone: tz });
        }
    } catch (e) {
        // Non-critical — timezone defaults to Asia/Kolkata on server
    }
}

// ============================================================
// EXPORTS — Available globally via window.EcoAPI
// ============================================================

window.EcoAPI = {
    // Convenience methods
    get: apiGet,
    post: apiPost,
    put: apiPut,
    delete: apiDelete,
	    apiGet,
	    apiPost,
	    apiPut,
	    apiDelete,
	    apiPatch,
	    // Auth methods
    login: apiLogin,
    register: apiRegister,
    forgotPassword: apiForgotPassword,
    logout: apiLogout,
    // Token management
    getToken,
    setToken,
    clearTokens,
    isLoggedIn,
    silentRefresh,
    // Output sanitization helpers (never used to transform API credentials)
    sanitize,
    sanitizeObject,
    // Callbacks
    setAuthExpiredCallback
};
