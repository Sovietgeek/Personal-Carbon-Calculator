/**
 * EcoVerse — AI Chat Popup Module with SSE Streaming
 * Floating popup chatbot, Spring AI + Gemini integration, conversation memory, streaming responses
 *
 * Features:
 * - Floating popup (opens from FAB or sidebar)
 * - SSE streaming for ChatGPT-like token-by-token experience
 * - Conversation memory (server-side, per user)
 * - Quick action chips for common prompts
 * - Fallback to blocking POST when SSE fails
 * - Context built from SERVER APIs (carbon, AQI, city)
 */

const AI = (() => {
    let chatHistory = [];
    let isLoading = false;
    let currentEventSource = null;
    let conversationId = null;
    let popupOpen = false;
    let initialized = false;

    const STREAM_URL = '/api/ai/stream';
    const BLOCKING_URL = '/api/ai/chat';

    async function init() {
        conversationId = 'user-' + (AppState.user?.id || Date.now());

        const input = document.getElementById('ai-chat-input');
        const sendBtn = document.getElementById('ai-send-btn');

        if (input) {
            input.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    sendChat(input.value);
                    input.value = '';
                }
            });
        }

        initialized = true;
    }

    /** Toggle the AI chat popup open/closed */
    function toggle() {
        const popup = document.getElementById('ai-popup');
        const fab = document.getElementById('ai-fab');
        if (!popup || !fab) return;

        if (!initialized) init();

        popupOpen = !popupOpen;

        if (popupOpen) {
            popup.classList.add('ai-popup--open');
            fab.classList.add('ai-fab--hidden');
            // Focus input when opening
            setTimeout(() => {
                const input = document.getElementById('ai-chat-input');
                if (input) input.focus();
            }, 300);
        } else {
            popup.classList.remove('ai-popup--open');
            fab.classList.remove('ai-fab--hidden');
        }
    }

    /** Open the popup (without toggling if already open) */
    function open() {
        if (!popupOpen) toggle();
    }

    /** Close the popup */
    function close() {
        if (popupOpen) toggle();
    }

    function sendChat(message) {
        if (!message?.trim()) return;
        if (isLoading) return;

        chat(message);
    }

    async function chat(message) {
        if (!message?.trim()) return;
        if (isLoading) return;

        // Add user message to UI
        chatHistory.push({ role: 'user', content: message.trim() });
        renderMessages();
        isLoading = true;

        // Hide quick actions after first message
        const quickActions = document.getElementById('ai-quick-actions');
        if (quickActions) quickActions.style.display = 'none';

        try {
            // Build context from server APIs
            const context = await buildContextFromServer();

            // Try SSE streaming first
            await streamChat(message, context);

        } catch (e) {
            // Fallback to blocking POST if SSE fails
            console.warn('SSE failed, falling back to blocking POST:', e.message);
            await blockingChat(message);
        }

        isLoading = false;
        renderMessages();
    }

    /**
     * Stream chat via SSE (Server-Sent Events)
     * Each SSE event contains a token chunk.
     * "[DONE]" signals end of stream.
     */
    async function streamChat(message, context) {
        return new Promise((resolve, reject) => {
            // Build SSE URL with query params
            const params = new URLSearchParams({
                message: message.trim(),
                conversationId: conversationId || ''
            });
            if (context.topic) params.set('context', context.topic);
            if (context.carbonToday) params.set('carbonToday', context.carbonToday);
            if (context.aqi) params.set('aqi', context.aqi);
            if (context.city) params.set('city', context.city);

            const url = `${STREAM_URL}?${params.toString()}`;

            // Add placeholder bot message for streaming
            const botMsg = { role: 'bot', content: '', source: 'openai', streaming: true };
            chatHistory.push(botMsg);

            // Use fetch with ReadableStream for SSE (works with auth cookies)
            const token = localStorage.getItem('eco_access_token')
                || (localStorage.getItem('eco_user')
                    ? JSON.parse(localStorage.getItem('eco_user') || '{}').token
                    : null);

            const headers = {};
            if (token) headers['Authorization'] = 'Bearer ' + token;

            fetch(url, { headers })
                .then(response => {
                    if (!response.ok) throw new Error(`HTTP ${response.status}`);

                    const reader = response.body.getReader();
                    const decoder = new TextDecoder();

                    function read() {
                        reader.read().then(({ done, value }) => {
                            if (done) {
                                botMsg.streaming = false;
                                renderMessages();
                                resolve();
                                return;
                            }

                            const text = decoder.decode(value, { stream: true });
                            // Parse SSE data lines
                            const lines = text.split('\n');
                            for (const line of lines) {
                                if (line.startsWith('data:')) {
                                    const data = line.substring(5).trim();
                                    if (data === '[DONE]') {
                                        botMsg.streaming = false;
                                        renderMessages();
                                        resolve();
                                        return;
                                    }
                                    if (data) {
                                        botMsg.content += data;
                                        renderMessages();
                                    }
                                }
                            }

                            read();
                        }).catch(err => {
                            botMsg.streaming = false;
                            if (!botMsg.content) {
                                chatHistory.pop();
                                reject(err);
                            } else {
                                renderMessages();
                                resolve();
                            }
                        });
                    }

                    read();
                })
                .catch(err => {
                    chatHistory.pop();
                    reject(err);
                });
        });
    }

    /**
     * Fallback: blocking POST chat
     */
    async function blockingChat(message) {
        try {
            const context = await buildContextFromServer();

            const result = await EcoAPI.apiPost(BLOCKING_URL, {
                message: message,
                context: context.topic,
                carbonToday: context.carbonToday,
                aqi: context.aqi,
                city: context.city,
                conversationId: conversationId
            });

            if (result && result.success && result.data?.message) {
                chatHistory.push({
                    role: 'bot',
                    content: result.data.message,
                    source: result.data.source || 'openai'
                });
            } else {
	                chatHistory.push({
	                    role: 'bot',
	                    content: result?.data?.message || 'AI assistant is not configured yet. Please contact the admin.',
	                    source: result?.data?.source || 'system'
	                });
	            }
	        } catch (e) {
	            const isRateLimit = e?.message === 'Rate limited';
	            chatHistory.push({
	                role: 'bot',
	                content: isRateLimit
	                    ? 'Too many requests. Please wait a moment before asking another question.'
	                    : (e?.message || 'AI assistant is temporarily unavailable. Please try again later.'),
	                source: 'system'
	            });
        }
    }

    /**
     * Build AI context from SERVER APIs
     */
    async function buildContextFromServer() {
        const context = { topic: 'general', carbonToday: null, aqi: null, city: null };

        try {
            const dashResult = await EcoAPI.apiGet('/api/dashboard');
            if (dashResult && dashResult.success && dashResult.data) {
                context.carbonToday = Number(dashResult.data.carbonToday || 0);
            }
        } catch (e) { /* non-fatal */ }

        if (AppState.weatherCache) {
            context.aqi = AppState.weatherCache.aqi || null;
            context.city = AppState.weatherCache.city || null;
        }

        return context;
    }

    /**
     * Render chat messages in the DOM
     */
    function renderMessages() {
        const container = document.getElementById('ai-chat-messages');
        if (!container) return;

        // Keep welcome + quick-actions at top
        const welcome = container.querySelector('.ai-welcome');
        const quickActions = container.querySelector('#ai-quick-actions');

        // Remove old message elements only (keep welcome + quick-actions)
        container.querySelectorAll('.ai-message').forEach(el => el.remove());

        // Add messages
        chatHistory.forEach(msg => {
            const div = document.createElement('div');
            div.className = `ai-message ai-message--${msg.role}`;

            if (msg.role === 'user') {
                div.innerHTML = `
                    <div class="ai-message-avatar ai-message-avatar--user"><i class="fa-solid fa-user"></i></div>
                    <div class="ai-message-bubble ai-message-bubble--user">${EcoUtils.sanitize(msg.content)}</div>`;
            } else {
                const sourceLabel = msg.source === 'gemini' ? 'Gemini'
                    : msg.source === 'openai' ? 'OpenAI'
                    : msg.source === 'system' ? 'System'
                    : 'EcoVerse AI';
                const sourceClass = msg.source === 'system' ? 'ai-source-badge--system' : '';
                const content = msg.streaming
                    ? formatResponse(msg.content) + '<span class="ai-cursor">▊</span>'
                    : formatResponse(msg.content);

                div.innerHTML = `
                    <div class="ai-message-avatar ai-message-avatar--bot"><i class="fa-solid fa-robot"></i></div>
                    <div>
                        <div class="ai-message-bubble ai-message-bubble--bot">${content}</div>
                        ${!msg.streaming ? `<div class="ai-source-badge ${sourceClass}"><i class="fa-solid fa-circle"></i> ${sourceLabel}</div>` : ''}
                    </div>`;
            }

            container.appendChild(div);
        });

        // Add typing indicator if loading and last message is from user
        if (isLoading && chatHistory.length > 0 && chatHistory[chatHistory.length - 1].role === 'user') {
            const typing = document.createElement('div');
            typing.className = 'ai-message ai-message--bot';
            typing.innerHTML = `
                <div class="ai-message-avatar ai-message-avatar--bot"><i class="fa-solid fa-robot"></i></div>
                <div class="ai-message-bubble ai-message-bubble--bot"><div class="ai-typing"><span></span><span></span><span></span></div></div>`;
            container.appendChild(typing);
        }

        container.scrollTop = container.scrollHeight;
    }

    function formatResponse(text) {
        if (!text) return '';
        return text
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/\n/g, '<br>');
    }

    function clearChat() {
        chatHistory = [];
        const quickActions = document.getElementById('ai-quick-actions');
        if (quickActions) quickActions.style.display = 'flex';
        renderMessages();
        // Clear server memory
        if (conversationId) {
            EcoAPI.apiDelete(`/api/ai/memory/${encodeURIComponent(conversationId)}`).catch(() => {});
        }
    }

    function quickAction(prompt) {
        const input = document.getElementById('ai-chat-input');
        if (input) input.value = '';
        chat(prompt);
    }

    async function getCarbonSuggestions() {
        const result = await EcoAPI.apiGet('/api/ai/carbon-suggestions');
        if (result && result.success && result.data) return result.data;
        return null;
    }

    async function getHealthTips() {
        const result = await EcoAPI.apiGet('/api/ai/health-tips');
        if (result && result.success && result.data) return result.data;
        return null;
    }

    return { chat, init, sendChat, clearChat, quickAction, getCarbonSuggestions, getHealthTips, renderMessages, toggle, open, close };
})();

window.AI = AI;
