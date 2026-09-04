/**
 * EcoVerse — Profile & Settings Module (Phase E: Production Dashboard)
 *
 * Key changes:
 * - updateCarbonBudget() calls server API (not just localStorage)
 * - render() fetches total carbon/saved from server API
 * - No localStorage reads for carbon/health data
 */

const Profile = (() => {

    async function render() {
        const user = AppState.user;
        if (!user) return;

        // Profile card
        EcoUtils.setText('profile-name', user.name || 'User');
        EcoUtils.setText('profile-email', user.email || '--');
        EcoUtils.setText('profile-joined', `Joined: ${new Date(user.joined || Date.now()).toLocaleDateString()}`);

        const avatarEl = document.getElementById('profile-avatar-large');
        if (avatarEl) avatarEl.innerText = (user.name || 'U').charAt(0).toUpperCase();

        // Stats — from server API (not localStorage)
        try {
            const result = await EcoAPI.apiGet('/api/carbon/summary');
            if (result && result.success && result.data) {
                const s = result.data;
                EcoUtils.setText('profile-total-carbon', `${Number(s.totalEmitted || 0).toFixed(1)} kg`);
                EcoUtils.setText('profile-total-saved', `${Number(s.totalSaved || 0).toFixed(1)} kg`);
            }
        } catch (err) {
            EcoUtils.setText('profile-total-carbon', '0.0 kg');
            EcoUtils.setText('profile-total-saved', '0.0 kg');
        }

        // Badges count — from server API
        try {
            const result = await EcoAPI.apiGet('/api/achievements');
            if (result && result.success && result.data) {
                const earned = result.data.filter(a => a.isUnlocked).length;
                EcoUtils.setText('profile-badges-count', earned.toString());
            }
        } catch (err) {
            EcoUtils.setText('profile-badges-count', '0');
        }

        // Settings values
        const nameEl = document.getElementById('settings-name');
        if (nameEl) nameEl.value = user.name || '';

        const budgetEl = document.getElementById('settings-carbon-budget');
        if (budgetEl) budgetEl.value = user.carbonBudget || 4.2;

        const stepsGoalEl = document.getElementById('settings-steps-goal');
        if (stepsGoalEl) stepsGoalEl.value = user.goals?.steps || 10000;

        const sleepGoalEl = document.getElementById('settings-sleep-goal');
        if (sleepGoalEl) sleepGoalEl.value = user.goals?.sleep || 8;

        const waterGoalEl = document.getElementById('settings-water-goal');
        if (waterGoalEl) waterGoalEl.value = user.goals?.water || 3;

        const calGoalEl = document.getElementById('settings-calorie-goal');
        if (calGoalEl) calGoalEl.value = user.goals?.calories || 2000;

        const darkModeEl = document.getElementById('settings-dark-mode');
        if (darkModeEl) darkModeEl.checked = localStorage.getItem('eco_theme') !== 'light';
    }

    async function updateProfile() {
        const name = document.getElementById('settings-name')?.value?.trim();
        if (!name) return EcoVerse.showToast('Name required', 'error');

        // Sanitize
        const cleanName = EcoUtils.sanitize(name);
        AppState.user.name = cleanName;

        // Call server API
        const result = await EcoAPI.apiPut('/api/profile', { name: cleanName });
        if (result && result.success) {
            EcoVerse.showToast('Profile updated!', 'success');
        } else {
            EcoVerse.showToast('Profile update failed', 'error');
        }

        // Update UI
        EcoUtils.lsSet('eco_user', AppState.user);
        EcoUtils.setText('user-display-name', cleanName);
        EcoUtils.setText('sidebar-user-name', cleanName);
        EcoUtils.setText('profile-name', cleanName);
        const sidebarAvatar = document.getElementById('sidebar-avatar');
        if (sidebarAvatar) sidebarAvatar.innerText = cleanName.charAt(0).toUpperCase();
        const profileAvatar = document.getElementById('profile-avatar-large');
        if (profileAvatar) profileAvatar.innerText = cleanName.charAt(0).toUpperCase();
    }

    async function updateCarbonBudget() {
        const b = parseFloat(document.getElementById('settings-carbon-budget')?.value);
        if (!b || b <= 0) return EcoVerse.showToast('Invalid budget', 'error');

        // Call server API to persist
        const result = await EcoAPI.apiPut('/api/profile', { carbonBudget: b });
        if (result && result.success) {
            AppState.user.carbonBudget = b;
            EcoUtils.lsSet('eco_user', AppState.user);
            EcoVerse.showToast('Budget updated!', 'success');
        } else {
            EcoVerse.showToast('Budget update failed', 'error');
            return;
        }

        // Invalidate dashboard cache and re-render
        Dashboard.invalidateCache();
        Dashboard.render();
        Carbon.render();
    }

    async function updateHealthGoals() {
        const steps = parseInt(document.getElementById('settings-steps-goal')?.value) || 10000;
        const sleep = parseFloat(document.getElementById('settings-sleep-goal')?.value) || 8;
        const water = parseFloat(document.getElementById('settings-water-goal')?.value) || 3;
        const calories = parseInt(document.getElementById('settings-calorie-goal')?.value) || 2000;

        // Persist to SERVER (not just localStorage)
        try {
            const result = await EcoAPI.apiPut('/api/profile', {
                goalsSteps: steps,
                goalsSleep: sleep,
                goalsWater: water,
                goalsCalories: calories
            });
            if (result && result.success) {
                AppState.user.goals = { steps, sleep, water, calories };
                EcoUtils.lsSet('eco_user', AppState.user);
                EcoVerse.showToast('Goals updated!', 'success');
            } else {
                EcoVerse.showToast('Failed to update goals', 'error');
            }
        } catch (e) {
            EcoVerse.showToast('Failed to update goals', 'error');
        }
    }

    async function exportData() {
        try {
            const result = await EcoAPI.apiGet('/api/profile/export');
            if (result && result.success && result.data) {
                const blob = new Blob([JSON.stringify(result.data, null, 2)], { type: 'application/json' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `ecoverse-export-${EcoUtils.today()}.json`;
                a.click();
                URL.revokeObjectURL(url);
                EcoVerse.showToast('Data exported!', 'success');
            } else {
                EcoVerse.showToast('Export failed', 'error');
            }
        } catch (e) {
            EcoVerse.showToast('Export failed', 'error');
        }
    }

    function clearAllData() {
        if (!confirm('Clear ALL data? This cannot be undone.')) return;
        localStorage.clear();
        location.reload();
    }

    async function deleteAccount() {
        if (!confirm('Delete account permanently? This cannot be undone.')) return;
        if (!confirm('Are you absolutely sure? All your data will be lost forever.')) return;

        try { await EcoAPI.apiDelete('/api/profile'); } catch (e) {}
        localStorage.clear();
        location.reload();
    }

    return { render, updateProfile, updateCarbonBudget, updateHealthGoals, exportData, clearAllData, deleteAccount };
})();
