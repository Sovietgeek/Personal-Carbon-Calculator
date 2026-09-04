/**
 * EcoVerse — Dashboard Module (Phase E: Production Dashboard)
 *
 * Key changes from Phase D:
 * - ALL data from server API (GET /api/dashboard), NEVER from localStorage
 * - NO mock leaderboard, NO fake data, NO hardcoded metrics
 * - Client-side cache with 30-second TTL to avoid excessive API calls
 * - Trend chart data from GET /api/dashboard/trend (server aggregates)
 * - Category breakdown from server response (no client-side computation)
 * - Health score from server (no client-side calcHealthScore)
 * - Streak from server (no client-side calcStreak)
 * - Eco tip from server (no TIPS[] array)
 * - Every metric traces: DATABASE → Repository → Service → Controller → DTO → Frontend → UI
 */

const Dashboard = (() => {

    // ===== Client-side cache (30-second TTL, no Redis) =====
    const CACHE_TTL_MS = 30_000;
    let _cache = { data: null, timestamp: 0 };

    function invalidateCache() {
        _cache = { data: null, timestamp: 0 };
    }

    function isCacheValid() {
        return _cache.data !== null && (Date.now() - _cache.timestamp) < CACHE_TTL_MS;
    }

    // ===== Main render — server-first =====

    async function render() {
        // Check cache first
        if (isCacheValid()) {
            populateUI(_cache.data);
            return;
        }

        try {
            const result = await EcoAPI.apiGet('/api/dashboard');
            if (result && result.success && result.data) {
                _cache = { data: result.data, timestamp: Date.now() };
                populateUI(result.data);
            } else {
                renderEmptyState();
            }
        } catch (err) {
            console.warn('Dashboard: failed to load from server:', err);
            // Show last cached data if available, otherwise empty state
            if (_cache.data) {
                populateUI(_cache.data);
            } else {
                renderEmptyState();
            }
        }
    }

    // ===== Populate UI from server response =====

    function populateUI(d) {
        if (!d) return;

        // Carbon today + risk
        const carbonToday = Number(d.carbonToday || 0);
        EcoUtils.setText('dash-carbon-today', `${carbonToday.toFixed(2)} kg`);

        const riskBadge = document.getElementById('dash-carbon-risk-badge');
        if (riskBadge) {
            const cls = getRiskBadgeClass(d.riskLevel);
            riskBadge.innerHTML = `<span class="risk-badge ${cls}">${d.riskLevel || 'N/A'}</span>`;
        }

        // Streak
        EcoUtils.setText('dash-streak', `${d.streakDays || 0} days`);
        EcoUtils.setText('dash-streak-best', `Best: ${d.bestStreak || 0}`);

        // Health score
        EcoUtils.setText('dash-health-score', `${d.healthScore || 0}/100`);

        // Health snapshot
        EcoUtils.setText('dash-steps', (d.steps || 0).toLocaleString());
        EcoUtils.setText('dash-calories', (d.calories || 0).toLocaleString());
        EcoUtils.setText('dash-sleep', `${d.sleep || 0}h`);
        EcoUtils.setText('dash-water', `${(d.water || 0).toFixed(1)}L`);
        EcoUtils.setText('dash-weight', d.weight ? `${d.weight}kg` : '--');

        const dashHealthSteps = document.getElementById('dash-health-steps');
        if (dashHealthSteps) {
            dashHealthSteps.innerHTML = `<i class="fa-solid fa-shoe-prints"></i> ${(d.steps || 0).toLocaleString()}`;
        }

        // Eco tip (from server, not client-side array)
        EcoUtils.setText('dash-tip-text', d.ecoTip || 'Track your carbon footprint to make a difference!');

        // Charts
        renderChart('week');
        renderBreakdown(d.categoryBreakdown);

        // Recent activity
        renderRecentActivity(d.recentActivity);

        // Personal impact (replaces mock leaderboard)
        renderPersonalImpact(d);

        // Weather cache (still from localStorage — not server data)
        if (AppState.weatherCache && AppState.weatherCache.temp) {
            EcoUtils.setText('dash-temp', `${AppState.weatherCache.temp}°`);
            EcoUtils.setText('dash-weather-city', AppState.weatherCache.city || 'Your Area');
        }
    }

    function renderEmptyState() {
        EcoUtils.setText('dash-carbon-today', '0.00 kg');
        EcoUtils.setText('dash-streak', '0 days');
        EcoUtils.setText('dash-streak-best', 'Best: 0');
        EcoUtils.setText('dash-health-score', '0/100');
        EcoUtils.setText('dash-steps', '0');
        EcoUtils.setText('dash-calories', '0');
        EcoUtils.setText('dash-sleep', '0h');
        EcoUtils.setText('dash-water', '0.0L');
        EcoUtils.setText('dash-weight', '--');
        EcoUtils.setText('dash-tip-text', 'Start tracking your carbon footprint!');
    }

    // ===== Trend chart — server data =====

    async function renderChart(period) {
        try {
            const result = await EcoAPI.apiGet(`/api/dashboard/trend?period=${period}`);
            if (result && result.success && result.data && result.data.dataPoints) {
                const points = result.data.dataPoints;
                const labels = points.map(p => formatDateLabel(p.date, period));
                const emissionsData = points.map(p => Number(p.emissions || 0));

                EcoUtils.destroyChart('dash-carbon-chart');
                EcoUtils.chartInstances['dash-carbon-chart'] = new Chart(
                    document.getElementById('dash-carbon-chart'), {
                        type: 'line',
                        data: {
                            labels,
                            datasets: [{
                                label: 'CO₂ (kg)', data: emissionsData,
                                borderColor: '#10b981',
                                backgroundColor: 'rgba(16,185,129,0.1)',
                                fill: true, tension: 0.4,
                                pointBackgroundColor: '#10b981', pointRadius: 4
                            }]
                        },
                        options: {
                            responsive: true, maintainAspectRatio: false,
                            plugins: { legend: { display: false } },
                            scales: {
                                y: { beginAtZero: true, grid: { color: 'rgba(16,185,129,0.05)' } },
                                x: { grid: { display: false } }
                            }
                        }
                    }
                );
            }
        } catch (err) {
            console.warn('Dashboard: trend chart failed:', err);
        }
    }

    function formatDateLabel(dateStr, period) {
        try {
            const d = new Date(dateStr + 'T00:00:00');
            if (period === 'week') return d.toLocaleDateString('en', { weekday: 'short' });
            if (period === 'month') return d.getDate();
            return d.toLocaleDateString('en', { month: 'short' });
        } catch (e) {
            return dateStr;
        }
    }

    function changeChart(p, btn) {
        document.querySelectorAll('.period-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        renderChart(p);
    }

    // ===== Category breakdown — server data =====

    function renderBreakdown(breakdown) {
        if (!breakdown || typeof breakdown !== 'object') return;

        const cats = Object.keys(breakdown);
        const colors = { transport: '#6366f1', energy: '#f59e0b', food: '#ef4444', shopping: '#3b82f6', waste: '#8b5cf6', digital: '#ec4899' };
        const data = cats.map(c => Number(breakdown[c] || 0));

        if (data.every(d => d === 0)) return;

        EcoUtils.destroyChart('dash-breakdown-chart');
        EcoUtils.chartInstances['dash-breakdown-chart'] = new Chart(
            document.getElementById('dash-breakdown-chart'), {
                type: 'doughnut',
                data: {
                    labels: cats.map(c => c.charAt(0).toUpperCase() + c.slice(1)),
                    datasets: [{ data, backgroundColor: cats.map(c => colors[c] || '#6b7280'), borderWidth: 0 }]
                },
                options: {
                    responsive: true, maintainAspectRatio: false, cutout: '65%',
                    plugins: { legend: { display: false } }
                }
            }
        );

        const legend = document.getElementById('dash-breakdown-legend');
        if (legend) {
            legend.innerHTML = cats.map((c, i) =>
                data[i] > 0 ? `<div class="breakdown-legend-item"><div class="breakdown-legend-dot" style="background:${colors[c] || '#6b7280'}"></div>${c}: ${data[i].toFixed(2)}kg</div>` : ''
            ).join('');
        }
    }

    // ===== Recent activity — server data =====

    function renderRecentActivity(activities) {
        const el = document.getElementById('dash-news-mini');
        if (!el) return;

        if (!activities || !activities.length) {
            el.innerHTML = '<div class="news-mini-item"><div class="news-mini-img"><i class="fa-solid fa-clock-rotate-left" style="font-size:24px;color:var(--text-faint);"></i></div><div class="news-mini-content"><div class="news-mini-title">No activity today</div><div class="news-mini-date">Log a carbon entry or health activity</div></div></div>';
            return;
        }

        el.innerHTML = activities.map(a => {
            const icon = a.type === 'carbon' ? 'fa-leaf' : 'fa-heart-pulse';
            const color = a.type === 'carbon' ? 'var(--primary)' : 'var(--accent)';
            const valStr = a.type === 'carbon'
                ? `${Number(a.value || 0).toFixed(3)} kg CO₂`
                : formatHealthValue(a);
            const timeStr = a.timestamp ? EcoUtils.formatRelative(a.timestamp) : '';
            return `<div class="news-mini-item">
                <div class="news-mini-img"><i class="fa-solid ${icon}" style="font-size:20px;color:${color};"></i></div>
                <div class="news-mini-content">
                    <div class="news-mini-title">${EcoUtils.sanitize(a.description || a.category || '')}</div>
                    <div class="news-mini-date">${valStr} · ${timeStr}</div>
                </div>
            </div>`;
        }).join('');
    }

    function formatHealthValue(a) {
        const v = Number(a.value || 0);
        if (a.category === 'steps') return `${v.toLocaleString()} steps`;
        if (a.category === 'workout') return `${v} kcal`;
        if (a.category === 'sleep') return `${v.toFixed(1)}h`;
        if (a.category === 'water') return `${(v / 1000).toFixed(1)}L`;
        if (a.category === 'weight') return `${v} kg`;
        return v.toString();
    }

    // ===== Personal impact (replaces mock leaderboard) =====

    function renderPersonalImpact(d) {
        const el = document.getElementById('dash-leaderboard');
        if (!el) return;

        const totalEmitted = Number(d.yearEmissions || 0);
        const budget = Number(d.carbonToday || 0) > 0 ? 'Under Budget' : 'No emissions today';
        const streak = d.streakDays || 0;
        const best = d.bestStreak || 0;

        el.innerHTML = `
            <div class="leaderboard-item" style="border-color:var(--primary);background:var(--primary-subtle);">
                <div class="leaderboard-rank leaderboard-rank--1"><i class="fa-solid fa-user" style="font-size:12px;"></i></div>
                <div class="leaderboard-name">You</div>
                <div class="leaderboard-score">${totalEmitted.toFixed(1)} kg/yr</div>
            </div>
            <div class="leaderboard-item">
                <div class="leaderboard-rank"><i class="fa-solid fa-fire" style="font-size:12px;color:var(--warning);"></i></div>
                <div class="leaderboard-name">Current Streak</div>
                <div class="leaderboard-score">${streak} days</div>
            </div>
            <div class="leaderboard-item">
                <div class="leaderboard-rank"><i class="fa-solid fa-trophy" style="font-size:12px;color:var(--accent);"></i></div>
                <div class="leaderboard-name">Best Streak</div>
                <div class="leaderboard-score">${best} days</div>
            </div>
            <div class="leaderboard-item">
                <div class="leaderboard-rank"><i class="fa-solid fa-tree" style="font-size:12px;color:var(--success);"></i></div>
                <div class="leaderboard-name">Trees Needed</div>
                <div class="leaderboard-score">${d.treesNeeded || 0}</div>
            </div>
        `;
    }

    // ===== Utility =====

    function getRiskBadgeClass(level) {
        if (!level) return '';
        const map = {
            'EXCELLENT': 'risk-badge--excellent',
            'GOOD': 'risk-badge--good',
            'MODERATE': 'risk-badge--moderate',
            'HIGH': 'risk-badge--high',
            'EXTREME': 'risk-badge--extreme'
        };
        return map[level.toUpperCase()] || '';
    }

    return { render, renderChart, changeChart, renderBreakdown, invalidateCache, getRiskBadgeClass };
})();
