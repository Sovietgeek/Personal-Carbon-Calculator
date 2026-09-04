/**
 * EcoVerse — News Module (Phase 4: Real News Site)
 *
 * Features:
 * - GNews API + RSS hybrid via server proxy
 * - Country filter (🇮🇳 India, 🇺🇸 US, 🇬🇧 UK, 🌍 World)
 * - Category tabs (All, Climate, Pollution, Wildlife, Renewable, Green Tech)
 * - Live search with debounce
 * - Article images from API
 * - Sentiment badges (Good / Bad / Neutral)
 * - Eco tips per article
 * - Breaking badge for today's news
 * - Pagination with Load More
 * - XSS-safe DOM construction
 */

const News = (() => {
    let currentPage = 0;
    let currentCategory = 'all';
    let currentCountry = 'in';
    let currentCity = '';
    let currentState = '';
    let currentQuery = '';
    let hasMore = false;
    let searchTimer = null;
    let autoRefreshInterval = null;

    /** Initialize news from user profile (called by app.js after login) */
    function initFromProfile() {
        const user = AppState.user;
        if (!user) return;

        // Auto-set country from user profile
        if (user.country) {
            currentCountry = user.country.toLowerCase();
            const countrySelect = document.getElementById('news-country-filter');
            if (countrySelect) {
                // Find the matching option
                const opts = countrySelect.options;
                for (let i = 0; i < opts.length; i++) {
                    if (opts[i].value === currentCountry) {
                        countrySelect.selectedIndex = i;
                        break;
                    }
                }
            }
        }

        // Store city/state for location-aware news
        if (user.city) currentCity = user.city;
        if (user.state) currentState = user.state;

        // Update location badge
        updateLocationBadge();

        // Auto-refresh news every 15 minutes
        if (!autoRefreshInterval) {
            autoRefreshInterval = setInterval(() => {
                if (document.getElementById('tab-news')?.style.display !== 'none') {
                    loadNews();
                }
            }, 15 * 60 * 1000);
        }
    }

    /** Show/hide the location badge in news header */
    function updateLocationBadge() {
        const badge = document.getElementById('news-location-badge');
        if (!badge) return;
        const city = currentCity || AppState.weatherCache?.city;
        if (city) {
            badge.style.display = 'inline-flex';
            badge.textContent = '📍 ' + city;
        } else {
            badge.style.display = 'none';
        }
    }

    /** Load news from server API */
    async function loadNews() {
        const loading  = document.getElementById('news-loading');
        const errorEl  = document.getElementById('news-error');
        const gridEl   = document.getElementById('news-grid');
        const featEl   = document.getElementById('news-featured');
        const moreWrap = document.getElementById('news-load-more-wrap');

        if (loading) loading.style.display = 'block';
        if (errorEl) errorEl.style.display = 'none';

        try {
            const params = new URLSearchParams({
                source: 'all',
                category: currentCategory,
                country: currentCountry,
                city: currentCity || '',
                state: currentState || '',
                q: currentQuery,
                page: currentPage,
                size: 20
            });

            const result = await EcoAPI.apiGet(`/api/news?${params}`);
            if (!result || !result.success || !result.data) {
                throw new Error(result?.message || 'News API failed');
            }

            const data = result.data;
            const articles = data.articles || [];

            if (loading) loading.style.display = 'none';

            // Featured article (page 0 only)
            if (currentPage === 0) {
                if (articles.length > 0 && featEl) {
                    featEl.style.display = 'block';
                    renderFeatured(articles[0]);
                }
                if (gridEl) gridEl.textContent = '';
                renderGrid(articles.slice(1));
            } else {
                renderGrid(articles);
            }

            // Pagination
            hasMore = data.page < (data.totalPages || 1) - 1;
            if (moreWrap) moreWrap.style.display = hasMore ? 'block' : 'none';

            // Empty state
            if (articles.length === 0 && currentPage === 0) {
                if (gridEl) {
                    gridEl.style.display = 'grid';
                    gridEl.textContent = '';
                    const emptyMsg = document.createElement('div');
                    emptyMsg.className = 'news-empty-state';
                    emptyMsg.textContent = currentQuery
                        ? `No articles found for "${currentQuery}". Try a different search.`
                        : 'No articles available at this time. Try again later.';
                    gridEl.appendChild(emptyMsg);
                }
            }

            // Update dashboard mini news
            if (typeof Dashboard !== 'undefined' && Dashboard.renderDashNewsMini) {
                Dashboard.renderDashNewsMini();
            }

        } catch (err) {
            if (loading) loading.style.display = 'none';
            if (errorEl) errorEl.style.display = 'flex';
            console.error('News API error:', err);
        }
    }

    /** Render featured article */
    function renderFeatured(feat) {
        const featEl = document.getElementById('news-featured');
        if (!featEl) return;

        featEl.textContent = '';

        // Background image if available
        if (feat.image) {
            featEl.classList.add('news-featured--img');
            featEl.style.backgroundImage = `linear-gradient(to top, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.4) 50%, rgba(0,0,0,0.1) 100%), url(${feat.image})`;
            featEl.style.backgroundSize = 'cover';
            featEl.style.backgroundPosition = 'center';
        } else {
            featEl.classList.remove('news-featured--img');
            featEl.style.backgroundImage = 'none';
        }

        const content = document.createElement('div');
        content.className = 'news-featured-content';
        content.style.cursor = 'pointer';
        content.addEventListener('click', () => {
            if (feat.link) window.open(feat.link, '_blank', 'noopener,noreferrer');
        });

        // Badge row
        const badgeRow = document.createElement('div');
        badgeRow.style.display = 'flex';
        badgeRow.style.gap = '8px';
        badgeRow.style.alignItems = 'center';
        badgeRow.style.marginBottom = '12px';
        badgeRow.style.flexWrap = 'wrap';

        const liveBadge = document.createElement('div');
        liveBadge.className = 'news-featured-badge';
        liveBadge.innerHTML = '<i class="fa-solid fa-bolt"></i> LIVE NEWS';
        badgeRow.appendChild(liveBadge);

        // Breaking badge for today's articles
        if (isToday(feat.date)) {
            const breakBadge = document.createElement('div');
            breakBadge.className = 'news-breaking-badge';
            breakBadge.textContent = '🔥 BREAKING';
            badgeRow.appendChild(breakBadge);
        }

        // Sentiment badge on featured
        if (feat.sentiment && feat.sentiment !== 'neutral') {
            const sentBadge = document.createElement('div');
            sentBadge.className = `news-sentiment-badge news-sentiment-${feat.sentiment}`;
            sentBadge.textContent = feat.sentiment === 'positive' ? '🟢 Good' : '🔴 Bad';
            badgeRow.appendChild(sentBadge);
        }

        content.appendChild(badgeRow);

        const title = document.createElement('div');
        title.className = 'news-featured-title';
        title.textContent = feat.title || 'Untitled';

        const summary = document.createElement('div');
        summary.className = 'news-featured-summary';
        summary.textContent = feat.summary || '';

        const meta = document.createElement('div');
        meta.className = 'news-featured-meta';

        const catSpan = document.createElement('span');
        catSpan.innerHTML = `<i class="fa-solid fa-tag"></i> `;
        catSpan.appendChild(document.createTextNode((feat.category || 'General').toUpperCase()));

        const srcSpan = document.createElement('span');
        srcSpan.innerHTML = `<i class="fa-regular fa-newspaper"></i> `;
        srcSpan.appendChild(document.createTextNode(feat.source || ''));

        const dateSpan = document.createElement('span');
        dateSpan.innerHTML = `<i class="fa-regular fa-calendar"></i> `;
        dateSpan.appendChild(document.createTextNode(formatDate(feat.date)));

        meta.appendChild(catSpan);
        meta.appendChild(srcSpan);
        meta.appendChild(dateSpan);

        content.appendChild(title);
        content.appendChild(summary);

        // Eco tip on featured
        if (feat.ecoTip) {
            const tipDiv = document.createElement('div');
            tipDiv.className = 'news-eco-tip';
            tipDiv.innerHTML = '<i class="fa-solid fa-leaf"></i> ';
            tipDiv.appendChild(document.createTextNode(feat.ecoTip));
            content.appendChild(tipDiv);
        }

        content.appendChild(meta);
        featEl.appendChild(content);
    }

    /** Render article grid */
    function renderGrid(articles) {
        const gridEl = document.getElementById('news-grid');
        if (!gridEl) return;

        gridEl.style.display = 'grid';

        articles.forEach(n => {
            const card = document.createElement('div');
            card.className = 'news-card';
            card.style.cursor = 'pointer';
            card.addEventListener('click', () => {
                if (n.link) window.open(n.link, '_blank', 'noopener,noreferrer');
            });

            // Image
            const imgDiv = document.createElement('div');
            imgDiv.className = 'news-card-img';
            if (n.image) {
                const img = document.createElement('img');
                img.src = n.image;
                img.alt = n.title || 'News';
                img.loading = 'lazy';
                img.style.width = '100%';
                img.style.height = '100%';
                img.style.objectFit = 'cover';
                img.onerror = function() {
                    this.parentElement.innerHTML = '<i class="fa-solid fa-newspaper" style="font-size:32px;color:var(--text-faint);"></i>';
                };
                imgDiv.appendChild(img);
            } else {
                imgDiv.innerHTML = '<i class="fa-solid fa-newspaper" style="font-size:32px;color:var(--text-faint);"></i>';
            }

            const body = document.createElement('div');
            body.className = 'news-card-body';

            // Badge row: category + breaking + sentiment
            const badgeRow = document.createElement('div');
            badgeRow.className = 'news-card-badge-row';

            const catBadge = document.createElement('span');
            catBadge.className = 'news-card-badge';
            catBadge.textContent = (n.category || 'General').toUpperCase();
            badgeRow.appendChild(catBadge);

            if (isToday(n.date)) {
                const breakBadge = document.createElement('span');
                breakBadge.className = 'news-breaking-badge';
                breakBadge.textContent = '🔥 NEW';
                badgeRow.appendChild(breakBadge);
            }

            if (n.sentiment && n.sentiment !== 'neutral') {
                const sentBadge = document.createElement('span');
                sentBadge.className = `news-sentiment-badge news-sentiment-${n.sentiment}`;
                sentBadge.textContent = n.sentiment === 'positive' ? '🟢 Good' : '🔴 Bad';
                badgeRow.appendChild(sentBadge);
            }

            body.appendChild(badgeRow);

            const title = document.createElement('div');
            title.className = 'news-card-title';
            title.textContent = n.title || 'Untitled';

            const summary = document.createElement('div');
            summary.className = 'news-card-summary';
            summary.textContent = n.summary || '';

            const meta = document.createElement('div');
            meta.className = 'news-card-meta';

            const srcSpan = document.createElement('span');
            srcSpan.textContent = n.source || '';

            const dateSpan = document.createElement('span');
            dateSpan.textContent = formatDate(n.date);

            meta.appendChild(srcSpan);
            meta.appendChild(dateSpan);

            body.appendChild(title);
            body.appendChild(summary);

            // Eco tip
            if (n.ecoTip) {
                const tipDiv = document.createElement('div');
                tipDiv.className = 'news-eco-tip';
                tipDiv.innerHTML = '<i class="fa-solid fa-leaf"></i> ';
                tipDiv.appendChild(document.createTextNode(n.ecoTip));
                body.appendChild(tipDiv);
            }

            body.appendChild(meta);

            card.appendChild(imgDiv);
            card.appendChild(body);
            gridEl.appendChild(card);
        });
    }

    /** Check if date is today */
    function isToday(dateStr) {
        if (!dateStr) return false;
        try {
            const d = new Date(dateStr);
            const now = new Date();
            return d.getFullYear() === now.getFullYear() &&
                   d.getMonth() === now.getMonth() &&
                   d.getDate() === now.getDate();
        } catch {
            return false;
        }
    }

    /** Format a date string for display */
    function formatDate(dateStr) {
        if (!dateStr) return '—';
        try {
            const d = new Date(dateStr);
            if (isNaN(d.getTime())) return dateStr.split('T')[0] || dateStr;
            const now = new Date();
            const diffMs = now - d;
            const diffHrs = Math.floor(diffMs / 3600000);

            // Relative time for recent articles
            if (diffHrs < 1) return 'Just now';
            if (diffHrs < 24) return `${diffHrs}h ago`;
            if (diffHrs < 48) return 'Yesterday';

            return d.toLocaleDateString('en', { month: 'short', day: 'numeric', year: 'numeric' });
        } catch {
            return dateStr;
        }
    }

    /** Search/filter — debounced, resets to page 0 */
    function filter() {
        const searchInput = document.getElementById('news-search');
        const countrySelect = document.getElementById('news-country-filter');

        currentQuery = searchInput ? searchInput.value.trim() : '';
        currentCountry = countrySelect ? countrySelect.value : 'in';

        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => {
            currentPage = 0;
            loadNews();
        }, 400);
    }

    /** Set category filter */
    function setCategory(c, btn) {
        document.querySelectorAll('.news-cat-btn').forEach(b => b.classList.remove('active'));
        if (btn) btn.classList.add('active');
        currentCategory = c === 'all' ? 'all' : c;
        currentPage = 0;
        loadNews();
    }

    /** Load more articles (next page) */
    function loadMore() {
        if (!hasMore) return;
        currentPage++;
        loadNews();
    }

    /** Reset and reload */
    function refresh() {
        currentPage = 0;
        loadNews();
    }

    return { fetch: loadNews, loadNews, filter, setCategory, loadMore, refresh, initFromProfile };
})();
