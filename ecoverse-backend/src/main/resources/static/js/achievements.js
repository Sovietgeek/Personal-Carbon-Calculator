/**
 * EcoVerse — Achievements Module (Phase E: Production Dashboard)
 *
 * Key changes:
 * - ALL badge data from server API (GET /api/achievements)
 * - Badge checks via server (POST /api/achievements/check)
 * - NO localStorage-based badge checks (removed BADGES[] array)
 * - NO mock leaderboard (replaced with personal stats from server)
 * - NO Carbon.calcStreak() dependency — streak from server
 */

const Achievements = (() => {

    let _achievements = [];

    async function render() {
        // Fetch achievements from server
        try {
            const result = await EcoAPI.apiGet('/api/achievements');
            if (result && result.success && result.data) {
                _achievements = result.data;
            }
        } catch (err) {
            console.warn('Achievements: failed to load from server:', err);
        }

        // Fetch dashboard data for streak and stats
        let dashData = null;
        try {
            const dashResult = await EcoAPI.apiGet('/api/dashboard');
            if (dashResult && dashResult.success && dashResult.data) {
                dashData = dashResult.data;
            }
        } catch (err) {}

        const streak = dashData?.streakDays || 0;
        const best = dashData?.bestStreak || 0;
        const totalDays = _achievements.filter(a => a.isUnlocked).length;

        EcoUtils.setText('streak-count', `${streak} Days`);
        EcoUtils.setText('streak-best', best.toString());
        EcoUtils.setText('streak-total-days', totalDays.toString());

        // Next badge
        const nextBadge = _achievements.find(a => !a.isUnlocked);
        EcoUtils.setText('streak-next-badge', nextBadge ? nextBadge.name : 'All Done!');

        // Milestones
        renderMilestones(streak, totalDays);

        // Badges
        renderBadges();

        // Leaderboard — personal stats (no mock data)
        renderLeaderboard(dashData);
    }

    function renderMilestones(streak, totalDays) {
        const milestones = [
            { name: 'First Entry', icon: 'fa-flag', target: 1, current: totalDays >= 1 ? 1 : 0 },
            { name: '7 Day Streak', icon: 'fa-fire', target: 7, current: streak },
            { name: '30 Day Streak', icon: 'fa-fire-flame-curved', target: 30, current: streak },
            { name: '100 Entries', icon: 'fa-database', target: 100, current: totalDays }
        ];

        const el = document.getElementById('milestone-list');
        if (!el) return;

        el.innerHTML = milestones.map(m => {
            const pct = Math.min(100, (m.current / m.target) * 100);
            const unlocked = m.current >= m.target;
            return `<div class="milestone-item">
                <div class="milestone-icon ${unlocked ? 'milestone-icon--unlocked' : 'milestone-icon--locked'}">
                    <i class="fa-solid ${m.icon}"></i>
                </div>
                <div class="milestone-info">
                    <div class="milestone-title">${m.name}</div>
                    <div class="milestone-desc">${m.current}/${m.target}</div>
                </div>
                <div class="milestone-progress">
                    <div class="progress-bar" style="width:100px;"><div class="progress-fill" style="width:${pct}%;"></div></div>
                </div>
            </div>`;
        }).join('');
    }

    function renderBadges() {
        const el = document.getElementById('badges-grid');
        if (!el) return;

        const earned = _achievements.filter(a => a.isUnlocked);

        el.innerHTML = _achievements.map(b => {
            return `<div class="badge-item ${b.isUnlocked ? 'badge-item--unlocked' : 'badge-item--locked'}" data-action="badgeDetail" data-id="${b.code || b.id}">
                <div class="badge-icon"><i class="fa-solid ${b.icon || 'fa-trophy'}"></i></div>
                <div class="badge-name">${EcoUtils.sanitize(b.name || '')}</div>
            </div>`;
        }).join('');

        EcoUtils.setText('badges-count', `${earned.length}/${_achievements.length}`);
    }

    function showBadgeDetail(idOrCode) {
        const badge = _achievements.find(b => b.code === idOrCode || b.id == idOrCode);
        if (!badge) return;

        EcoUtils.setText('badge-unlock-name', badge.name || '');
        EcoUtils.setText('badge-unlock-desc', badge.description || '');
        document.getElementById('badge-unlock-icon').innerHTML = `<i class="fa-solid ${badge.icon || 'fa-trophy'}"></i>`;

        document.getElementById('badge-unlock-modal')?.classList.add('open');
    }

    function closeBadgeModal() {
        document.getElementById('badge-unlock-modal')?.classList.remove('open');
    }

    function renderLeaderboard(dashData) {
        const user = AppState.user;
        const tbody = document.getElementById('leaderboard-tbody');
        if (!tbody) return;

        // Personal stats only — no mock leaderboard data
        const yearEmissions = Number(dashData?.yearEmissions || 0).toFixed(1);
        const streak = dashData?.streakDays || 0;
        const bestStreak = dashData?.bestStreak || 0;
        const treesNeeded = dashData?.treesNeeded || 0;

        tbody.innerHTML = `
            <tr style="background:var(--primary-subtle);">
                <td>1</td>
                <td>${EcoUtils.sanitize(user?.name || 'You')} (You)</td>
                <td>${yearEmissions} kg/yr</td>
                <td>${streak} days</td>
            </tr>
            <tr>
                <td>—</td>
                <td colspan="3" style="text-align:center;color:var(--text-faint);font-style:italic;">
                    Best Streak: ${bestStreak} days · Trees Needed: ${treesNeeded}
                </td>
            </tr>
        `;
    }

    async function check() {
        try {
            const result = await EcoAPI.apiPost('/api/achievements/check');
            if (result && result.success && result.data && result.data.length > 0) {
                // New badges unlocked — show detail for the first one
                const newBadge = result.data[0];
                if (newBadge) {
                    EcoVerse.showToast(`🏆 Badge unlocked: ${newBadge.name}!`, 'success');
                    setTimeout(() => showBadgeDetail(newBadge.code || newBadge.id), 500);
                }
                // Re-render to update badge status
                render();
            }
        } catch (err) {
            console.warn('Achievements: check failed:', err);
        }
    }

    return { render, check, showBadgeDetail, closeBadgeModal };
})();
