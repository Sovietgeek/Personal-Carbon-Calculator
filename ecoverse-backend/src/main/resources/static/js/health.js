/**
 * EcoVerse — Health Tracker Module (Phase 3: Production)
 *
 * Key changes from Phase E:
 * - BMI calculated via server API (not client-side) with disclaimer
 * - Client-side pre-validation for UX (server validates authoritatively)
 * - Health logs fetched with pagination support
 * - Loading states on chart render
 */

const Health = (() => {

    function switchTab(tab, btn) {
        document.querySelectorAll('.health-sub-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        document.querySelectorAll('.health-form').forEach(f => f.classList.remove('active'));
        document.getElementById(`health-form-${tab}`)?.classList.add('active');
        if (tab === 'water') renderWaterVisual();
    }

    async function log(type) {
        let data = {};

        if (type === 'steps') {
            data = { steps: parseInt(document.getElementById('steps-input')?.value) || 0, distance: parseFloat(document.getElementById('steps-distance')?.value) || 0 };
            if (!data.steps) return EcoVerse.showToast('Enter steps', 'error');
        } else if (type === 'workout') {
            const dur = parseInt(document.getElementById('workout-duration')?.value) || 0;
            const int = document.getElementById('workout-intensity')?.value || 'moderate';
            data = {
                type: document.getElementById('workout-type')?.value || 'running',
                duration: dur, intensity: int,
                calories: parseInt(document.getElementById('workout-calories')?.value) || Math.round(dur * ({ low: 4, moderate: 7, high: 10, extreme: 14 }[int] || 7))
            };
            if (!dur) return EcoVerse.showToast('Enter duration', 'error');
        } else if (type === 'weight') {
            data = { weight: parseFloat(document.getElementById('weight-input')?.value), height: parseFloat(document.getElementById('height-input')?.value) };
            if (!data.weight) return EcoVerse.showToast('Enter weight', 'error');
        } else if (type === 'sleep') {
            data = { hours: parseFloat(document.getElementById('sleep-hours')?.value), quality: document.getElementById('sleep-quality')?.value, bedtime: document.getElementById('sleep-bedtime')?.value, wake: document.getElementById('sleep-wake')?.value };
            if (!data.hours) return EcoVerse.showToast('Enter hours', 'error');
        } else if (type === 'water') {
            data = { ml: parseInt(document.getElementById('water-input')?.value) || 0 };
            if (!data.ml) return EcoVerse.showToast('Enter ml', 'error');
        }

        // Map to backend fields
        let reqBody = { type };
        if (type === 'steps')     { reqBody.steps = data.steps; reqBody.distance = data.distance; }
        if (type === 'workout')  { reqBody.workoutType = data.type; reqBody.duration = data.duration; reqBody.intensity = data.intensity; reqBody.calories = data.calories; }
        if (type === 'weight')   { reqBody.weight = data.weight; reqBody.height = data.height; }
        if (type === 'sleep')    { reqBody.hours = data.hours; reqBody.quality = data.quality; reqBody.bedtime = data.bedtime; reqBody.wakeTime = data.wake; }
        if (type === 'water')    { reqBody.waterMl = data.ml; }

        let result;
        try {
            result = await EcoAPI.apiPost('/api/health/log', reqBody);
        } catch (err) {
            EcoVerse.showToast(err?.message || `${type} log failed`, 'error');
            return;
        }

        if (result && result.success) {
            EcoVerse.showToast(`${type} logged!`, 'success');
            resetFormInputs(type);
        } else {
            const msg = result?.message || `${type} log failed`;
            EcoVerse.showToast(msg, 'error');
            return;
        }

        // Invalidate dashboard cache so next render fetches fresh data
        Dashboard.invalidateCache();
        render();
        Dashboard.render();
        Achievements.check();
    }

    /** Reset form inputs after successful log */
    function resetFormInputs(type) {
        const resetMap = {
            steps: ['steps-input', 'steps-distance'],
            workout: ['workout-duration'],
            weight: ['weight-input'],
            sleep: ['sleep-hours'],
            water: ['water-input']
        };
        const fields = resetMap[type] || [];
        fields.forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = '';
        });
    }

    function addWater(ml) {
        const el = document.getElementById('water-input');
        if (el) el.value = ml;
        log('water');
    }

    async function renderWaterVisual() {
        // Fetch today's water from server
        try {
            const result = await EcoAPI.apiGet('/api/health/logs?type=water&period=today');
            if (result && result.success && result.data) {
                // Handle paginated response — data may be Page object or array
                const logs = Array.isArray(result.data) ? result.data : (result.data.content || []);
                const total = logs.reduce((s, e) => s + (e.waterMl || 0), 0);
                const glasses = Math.min(10, Math.ceil(total / 300));
                const el = document.getElementById('water-tracker-visual');
                if (!el) return;
                el.innerHTML = Array.from({ length: 10 }, (_, i) =>
                    `<div class="water-glass ${i < glasses ? 'filled' : ''}"></div>`
                ).join('');
            }
        } catch (err) {
            console.warn('Health: water visual failed:', err);
        }
    }

    /**
     * Calculate BMI via server API.
     * Server validates inputs and returns result with disclaimer.
     */
    async function calculateBMI() {
        const w = parseFloat(document.getElementById('bmi-weight')?.value);
        const h = parseFloat(document.getElementById('bmi-height')?.value);
        if (!w || !h) return EcoVerse.showToast('Enter weight & height', 'error');

        // Client-side pre-validation for UX (server validates authoritatively)
        if (w < 2 || w > 300) return EcoVerse.showToast('Weight must be between 2 and 300 kg', 'error');
        if (h < 30 || h > 300) return EcoVerse.showToast('Height must be between 30 and 300 cm', 'error');

        try {
            const result = await EcoAPI.apiPost('/api/health/bmi', { weight: w, height: h });
            if (!result || !result.success || !result.data) {
                const msg = result?.message || 'BMI calculation failed';
                return EcoVerse.showToast(msg, 'error');
            }

            const bmi = result.data;
            const resultEl = document.getElementById('bmi-result');
            if (resultEl) resultEl.style.display = 'block';

            // Display BMI value
            EcoUtils.setText('bmi-value-display', bmi.bmi ? bmi.bmi.toFixed(1) : '—');
            const bmiDisplay = document.getElementById('bmi-value-display');
            if (bmiDisplay) bmiDisplay.style.color = bmi.color || '';

            // Category
            EcoUtils.setText('bmi-category', bmi.category || '—');
            const catEl = document.getElementById('bmi-category');
            if (catEl) catEl.style.color = bmi.color || '';

            // Advice
            EcoUtils.setText('bmi-advice', bmi.advice || '');

            // Disclaimer — show if present
            const disclaimerEl = document.getElementById('bmi-disclaimer');
            if (disclaimerEl && bmi.disclaimer) {
                disclaimerEl.textContent = bmi.disclaimer;
                disclaimerEl.style.display = 'block';
            }

        } catch (err) {
            EcoVerse.showToast('BMI calculation failed', 'error');
            console.error('BMI error:', err);
        }
    }

    async function render() {
        renderWaterVisual();

        // Fetch weekly health logs from server for charts
        try {
            const result = await EcoAPI.apiGet('/api/health/logs?period=week');
            if (result && result.success && result.data) {
                // Handle paginated response — data may be Page object or array
                const logs = Array.isArray(result.data) ? result.data : (result.data.content || []);
                const sD = [], cD = [], wD = [];

                for (let i = 6; i >= 0; i--) {
                    const d = new Date(); d.setDate(d.getDate() - i);
                    const ds = d.toISOString().split('T')[0];
                    sD.push(logs.filter(e => e.type === 'steps' && e.entryDate && e.entryDate.startsWith(ds)).reduce((s, e) => s + (e.steps || 0), 0));
                    cD.push(logs.filter(e => e.type === 'workout' && e.entryDate && e.entryDate.startsWith(ds)).reduce((s, e) => s + (e.calories || 0), 0));
                    const w = logs.filter(e => e.type === 'weight' && e.entryDate && e.entryDate.startsWith(ds)).map(e => e.weight).pop();
                    wD.push(w || null);
                }

                const labels = Array.from({ length: 7 }, (_, i) => {
                    const d = new Date(); d.setDate(d.getDate() - (6 - i));
                    return d.toLocaleDateString('en', { weekday: 'short' });
                });

                EcoUtils.destroyChart('health-steps-chart');
                EcoUtils.chartInstances['health-steps-chart'] = new Chart(
                    document.getElementById('health-steps-chart'), {
                        type: 'bar',
                        data: { labels, datasets: [{ label: 'Steps', data: sD, backgroundColor: 'rgba(99,102,241,0.6)', borderRadius: 6 }] },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, grid: { color: 'rgba(16,185,129,0.05)' } }, x: { grid: { display: false } } } }
                    }
                );

                EcoUtils.destroyChart('health-calories-chart');
                EcoUtils.chartInstances['health-calories-chart'] = new Chart(
                    document.getElementById('health-calories-chart'), {
                        type: 'line',
                        data: { labels, datasets: [{ label: 'Calories', data: cD, borderColor: '#ef4444', backgroundColor: 'rgba(239,68,68,0.1)', fill: true, tension: 0.4 }] },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, grid: { color: 'rgba(16,185,129,0.05)' } }, x: { grid: { display: false } } } }
                    }
                );

                EcoUtils.destroyChart('health-weight-chart');
                EcoUtils.chartInstances['health-weight-chart'] = new Chart(
                    document.getElementById('health-weight-chart'), {
                        type: 'line',
                        data: { labels, datasets: [{ label: 'Weight (kg)', data: wD, borderColor: '#10b981', tension: 0.4, spanGaps: true }] },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { grid: { color: 'rgba(16,185,129,0.05)' } }, x: { grid: { display: false } } } }
                    }
                );
            }
        } catch (err) {
            console.warn('Health: render charts failed:', err);
        }
    }

    return { switchTab, log, addWater, calculateBMI, render, renderWaterVisual };
})();
