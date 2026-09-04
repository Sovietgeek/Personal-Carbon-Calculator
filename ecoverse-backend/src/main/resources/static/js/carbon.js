/**
 * EcoVerse — Carbon Tracker Module (Phase E: Production Dashboard)
 *
 * Key changes from Phase D:
 * - NO client-side emission factors — server is authoritative
 * - addEntry() sends structured inputs (distance, consumption, etc.), NOT co2
 * - calc*() preview functions call POST /carbon/calculate (server-side)
 * - Removed fake savings: no more "budget - todayCO2" as saved CO2
 * - Trees/averages come from server API responses
 * - calcStreak() REMOVED — streak comes from server (GET /api/dashboard)
 * - Trend chart data from server (GET /api/dashboard/trend), NOT localStorage
 * - Dashboard cache invalidated after carbon mutations
 */

const Carbon = (() => {

    function render() {
        // Fetch server summary for accurate data
        loadSummary();
    }

    async function loadSummary() {
        try {
            const result = await EcoAPI.apiGet('/api/carbon/summary');
            if (result && result.success && result.data) {
                const s = result.data;
                const budget = AppState.user?.carbonBudget || 4.2;

                EcoUtils.setText('carbon-total-emitted', `${Number(s.totalEmitted || 0).toFixed(2)} kg`);
                EcoUtils.setText('carbon-total-saved', `${Number(s.totalSaved || 0).toFixed(2)} kg`);
                const net = Number(s.totalSaved || 0) - Number(s.totalEmitted || 0);
                EcoUtils.setText('carbon-net-impact', `${Math.abs(net).toFixed(2)} kg`);
                EcoUtils.setText('carbon-net-label', net >= 0 ? 'Net Positive 🌱' : 'Net Negative ⚠️');

                const netCard = document.getElementById('carbon-net-impact')?.parentElement;
                if (netCard) {
                    netCard.classList.toggle('carbon-saved-card--emitted', net < 0);
                    netCard.classList.toggle('carbon-saved-card--saved', net >= 0);
                }

                EcoUtils.setText('carbon-today-val', `${Number(s.todayEmissions || 0).toFixed(2)} kg CO₂`);
                EcoUtils.setText('carbon-month-val', `${Number(s.monthEmissions || 0).toFixed(2)} kg`);
                EcoUtils.setText('carbon-year-val', `${(Number(s.yearEmissions || 0) / 1000).toFixed(2)} tonnes`);
                EcoUtils.setText('carbon-trees-needed', (s.treesNeeded || 0).toLocaleString());

                updateRiskBadge(Number(s.todayEmissions || 0), budget);
                updateBudgetUI(Number(s.todayEmissions || 0), budget);
            }
        } catch (err) {
            console.warn('Failed to load carbon summary:', err);
        }

        // Load entries for table
        try {
            const result = await EcoAPI.apiGet('/api/carbon/entries?period=today');
            if (result && result.success && result.data) {
                renderTable(result.data);
            }
        } catch (err) {
            console.warn('Failed to load carbon entries:', err);
        }

        // Load risk assessment
        try {
            const result = await EcoAPI.apiGet('/api/carbon/risk');
            if (result && result.success && result.data) {
                renderRiskFromServer(result.data);
            }
        } catch (err) {}

        // Load breakdown for category chart
        try {
            const result = await EcoAPI.apiGet('/api/carbon/breakdown');
            if (result && result.success && result.data) {
                renderCatChartFromServer(result.data);
            }
        } catch (err) {}
    }

    function updateRiskBadge(todayC, budget) {
        const pct = budget > 0 ? (todayC / budget) * 100 : 0;
        let level, cls, title, desc, color;

        if (pct === 0)         { level='EXCELLENT'; cls='risk-badge--excellent'; title='Zero Emissions!'; desc='Perfect day! You emitted absolutely no carbon today.'; color='#10b981'; }
        else if (pct <= 25)    { level='GOOD'; cls='risk-badge--good'; title='Doing Great!'; desc=`Your emissions are very low (${pct.toFixed(0)}% of budget).`; color='#3b82f6'; }
        else if (pct <= 60)    { level='MODERATE'; cls='risk-badge--moderate'; title='Moderate Impact'; desc=`You've used ${pct.toFixed(0)}% of your daily budget.`; color='#f59e0b'; }
        else if (pct <= 100)  { level='HIGH'; cls='risk-badge--high'; title='High Emissions'; desc=`Warning: ${pct.toFixed(0)}% of daily budget used.`; color='#f97316'; }
        else                  { level='EXTREME'; cls='risk-badge--extreme'; title='Over Budget!'; desc=`You've exceeded your budget by ${((pct-100)/100).toFixed(0)}x.`; color='#ef4444'; }

        const el = document.getElementById('carbon-risk-level-badge');
        if (el) el.innerHTML = `<span class="risk-badge ${cls}">${level}</span>`;
        EcoUtils.setText('carbon-risk-title', title);
        EcoUtils.setText('carbon-risk-desc', desc);

        const dashRC = document.getElementById('dash-carbon-risk-badge');
        if (dashRC) dashRC.innerHTML = `<span class="risk-badge ${cls}">${level}</span>`;

        const rvEl = document.getElementById('carbon-risk-value');
        if (rvEl) rvEl.innerText = todayC.toFixed(2);

        const gf = document.getElementById('risk-gauge-fill');
        if (gf) {
            const maxPct = Math.min(pct, 150);
            const offset = 251 - (maxPct / 150) * 251;
            gf.style.strokeDashoffset = offset;
            gf.style.stroke = color;
        }

        const maxVis = 12;
        const youW = Math.min((todayC / maxVis) * 100, 100);
        const youBar = document.getElementById('risk-bar-you');
        if (youBar) {
            youBar.style.width = `${youW}%`;
            youBar.style.background = pct <= 100 ? 'linear-gradient(90deg,var(--primary),var(--accent))' : 'linear-gradient(90deg,#ef4444,#dc2626)';
        }
        const rv = document.getElementById('risk-val-you');
        if (rv) rv.innerText = `${todayC.toFixed(1)} kg`;
    }

    function renderRiskFromServer(data) {
        if (!data) return;
        const indiaVal = document.getElementById('risk-val-india');
        const globalVal = document.getElementById('risk-val-global');
        if (indiaVal) indiaVal.innerText = `${Number(data.indiaAvgKg || 4.2).toFixed(1)} kg`;
        if (globalVal) globalVal.innerText = `${Number(data.globalAvgKg || 8.5).toFixed(1)} kg`;
    }

    function updateBudgetUI(todayC, budget) {
        if (!budget) budget = AppState.user?.carbonBudget || 4.2;
        const rem = Math.max(0, budget - todayC);
        const pct = budget > 0 ? Math.min(100, (todayC / budget) * 100) : 0;
        EcoUtils.setText('carbon-budget-remaining', `${rem.toFixed(2)} kg left`);
        const fill = document.getElementById('carbon-budget-fill');
        if (fill) {
            fill.style.width = `${pct}%`;
            fill.style.background = pct > 100 ? 'var(--danger)' : pct > 80 ? 'var(--warning)' : 'linear-gradient(90deg,var(--primary),var(--accent))';
        }
    }

    function switchCat(c, btn) {
        document.querySelectorAll('.carbon-cat-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        document.querySelectorAll('.carbon-cat-form').forEach(f => f.classList.remove('active'));
        document.getElementById(`carbon-form-${c}`)?.classList.add('active');
    }

    // ===== Server-side calculation previews =====

    async function calcTransport() {
        const type = document.getElementById('transport-type')?.value;
        const distance = parseFloat(document.getElementById('transport-distance')?.value) || 0;
        const passengers = parseInt(document.getElementById('transport-passengers')?.value) || 1;
        if (!type || !distance) { EcoUtils.setText('transport-est', '0.000 kg CO₂'); return; }
        try {
            const result = await EcoAPI.apiPost('/api/carbon/calculate', { category: 'transport', type, value: distance, unit: 'km', passengers, isSecondhand: false });
            if (result?.success && result.data) EcoUtils.setText('transport-est', `${Number(result.data.co2).toFixed(3)} kg CO₂`);
        } catch (e) { EcoUtils.setText('transport-est', '— kg CO₂'); }
    }

    async function calcEnergy() {
        const type = document.getElementById('energy-type')?.value;
        const consumption = parseFloat(document.getElementById('energy-consumption')?.value) || 0;
        if (!type || !consumption) { EcoUtils.setText('energy-est', '0.000 kg CO₂'); return; }
        try {
            const result = await EcoAPI.apiPost('/api/carbon/calculate', { category: 'energy', type, value: consumption, unit: 'kWh', passengers: 1, isSecondhand: false });
            if (result?.success && result.data) EcoUtils.setText('energy-est', `${Number(result.data.co2).toFixed(3)} kg CO₂`);
        } catch (e) { EcoUtils.setText('energy-est', '— kg CO₂'); }
    }

    async function calcFood() {
        const type = document.getElementById('food-type')?.value;
        const meals = parseInt(document.getElementById('food-meals')?.value) || 1;
        if (!type) { EcoUtils.setText('food-est', '0.000 kg CO₂'); return; }
        try {
            const result = await EcoAPI.apiPost('/api/carbon/calculate', { category: 'food', type, value: meals, unit: 'meal', passengers: 1, isSecondhand: false });
            if (result?.success && result.data) EcoUtils.setText('food-est', `${Number(result.data.co2).toFixed(3)} kg CO₂`);
        } catch (e) { EcoUtils.setText('food-est', '— kg CO₂'); }
    }

    async function calcShopping() {
        const type = document.getElementById('shopping-type')?.value;
        const quantity = parseFloat(document.getElementById('shopping-cost')?.value) || 0;
        const isSecondhand = document.getElementById('shopping-secondhand')?.checked || false;
        if (!type || !quantity) { EcoUtils.setText('shopping-est', '0.000 kg CO₂'); return; }
        try {
            const result = await EcoAPI.apiPost('/api/carbon/calculate', { category: 'shopping', type, value: quantity, unit: 'kg', passengers: 1, isSecondhand });
            if (result?.success && result.data) EcoUtils.setText('shopping-est', `${Number(result.data.co2).toFixed(3)} kg CO₂`);
        } catch (e) { EcoUtils.setText('shopping-est', '— kg CO₂'); }
    }

    async function calcWaste() {
        const type = document.getElementById('waste-type')?.value;
        const amount = parseFloat(document.getElementById('waste-amount')?.value) || 0;
        if (!type || !amount) { EcoUtils.setText('waste-est', '0.000 kg CO₂'); return; }
        try {
            const result = await EcoAPI.apiPost('/api/carbon/calculate', { category: 'waste', type, value: amount, unit: 'kg', passengers: 1, isSecondhand: false });
            if (result?.success && result.data) EcoUtils.setText('waste-est', `${Number(result.data.co2).toFixed(3)} kg CO₂`);
        } catch (e) { EcoUtils.setText('waste-est', '— kg CO₂'); }
    }

    async function calcDigital() {
        const type = document.getElementById('digital-type')?.value;
        const quantity = parseFloat(document.getElementById('digital-quantity')?.value) || 0;
        if (!type || !quantity) { EcoUtils.setText('digital-est', '0.0000 kg CO₂'); return; }
        try {
            const result = await EcoAPI.apiPost('/api/carbon/calculate', { category: 'digital', type, value: quantity, unit: 'hr', passengers: 1, isSecondhand: false });
            if (result?.success && result.data) EcoUtils.setText('digital-est', `${Number(result.data.co2).toFixed(4)} kg CO₂`);
        } catch (e) { EcoUtils.setText('digital-est', '— kg CO₂'); }
    }

    function setupCalcListeners() {
        const map = {
            'transport-distance': calcTransport, 'energy-consumption': calcEnergy,
            'food-meals': calcFood, 'shopping-cost': calcShopping,
            'waste-amount': calcWaste, 'digital-quantity': calcDigital
        };
        Object.keys(map).forEach(id => {
            const el = document.getElementById(id);
            if (el) el.addEventListener('input', map[id]);
        });
        ['transport-type','energy-type','food-type','shopping-type','waste-type','digital-type','shopping-secondhand'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.addEventListener('change', () => {
                const cat = id.split('-')[0];
                const fn = { transport: calcTransport, energy: calcEnergy, food: calcFood, shopping: calcShopping, waste: calcWaste, digital: calcDigital }[cat];
                if (fn) fn();
            });
        });
    }

    // ===== Add Entry — sends structured inputs, NOT co2 =====

    async function addEntry(cat) {
        const typeEl = document.getElementById(`${cat}-type`);
        const type = typeEl?.value || cat;

        const req = { category: cat, type };

        switch (cat) {
            case 'transport':
                req.distance = parseFloat(document.getElementById('transport-distance')?.value) || 0;
                req.distanceUnit = 'km';
                req.passengers = parseInt(document.getElementById('transport-passengers')?.value) || 1;
                if (!req.distance) return EcoVerse.showToast('Enter a distance', 'error');
                break;
            case 'energy':
                req.consumption = parseFloat(document.getElementById('energy-consumption')?.value) || 0;
                req.energyUnit = 'kWh';
                if (!req.consumption) return EcoVerse.showToast('Enter consumption', 'error');
                break;
            case 'food':
                req.meals = parseInt(document.getElementById('food-meals')?.value) || 1;
                break;
            case 'shopping':
                req.quantity = parseFloat(document.getElementById('shopping-cost')?.value) || 0;
                req.quantityUnit = 'kg';
                req.isSecondhand = document.getElementById('shopping-secondhand')?.checked || false;
                if (!req.quantity) return EcoVerse.showToast('Enter a quantity', 'error');
                break;
            case 'waste':
                req.quantity = parseFloat(document.getElementById('waste-amount')?.value) || 0;
                req.quantityUnit = 'kg';
                if (!req.quantity) return EcoVerse.showToast('Enter an amount', 'error');
                break;
            case 'digital':
                req.quantity = parseFloat(document.getElementById('digital-quantity')?.value) || 0;
                req.quantityUnit = 'hr';
                if (!req.quantity) return EcoVerse.showToast('Enter a quantity', 'error');
                break;
        }

        // Send timezone with the request
        try { req.timezone = Intl.DateTimeFormat().resolvedOptions().timeZone; } catch (e) {}

        try {
            const result = await EcoAPI.apiPost('/api/carbon/entries', req);
            if (result && result.success) {
                const co2 = Number(result.data?.co2 || 0);
                const calcType = result.data?.calculationType || 'EMISSION';
                EcoVerse.showToast(`Logged ${co2.toFixed(3)} kg CO₂ (${calcType.replace('_', ' ').toLowerCase()})`, 'success');
            } else {
                EcoVerse.showToast('Failed to log entry', 'error');
            }
        } catch (err) {
            EcoVerse.showToast('Error logging entry: ' + (err.message || 'unknown'), 'error');
        }

        // Invalidate dashboard cache so next render fetches fresh data
        Dashboard.invalidateCache();
        render();
        Dashboard.render();
        Achievements.check();
    }

    // ===== Trend chart — server data =====

    function setTime(t, btn) {
        document.querySelectorAll('.time-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        renderTrendChart(t);
    }

    async function renderTrendChart(period) {
        try {
            const result = await EcoAPI.apiGet(`/api/dashboard/trend?period=${period}`);
            if (result && result.success && result.data && result.data.dataPoints) {
                const points = result.data.dataPoints;
                const labels = points.map(p => {
                    try {
                        const d = new Date(p.date + 'T00:00:00');
                        if (period === 'day' || period === 'week') return d.toLocaleDateString('en', { weekday: 'short' });
                        if (period === 'month') return d.getDate();
                        return d.toLocaleDateString('en', { month: 'short' });
                    } catch (e) { return p.date; }
                });
                const emissionsData = points.map(p => Number(p.emissions || 0));

                EcoUtils.destroyChart('carbon-trend-chart');
                EcoUtils.chartInstances['carbon-trend-chart'] = new Chart(
                    document.getElementById('carbon-trend-chart'), {
                        type: 'bar',
                        data: { labels, datasets: [{ label: 'CO₂ (kg)', data: emissionsData, backgroundColor: 'rgba(16,185,129,0.6)', borderRadius: 6 }] },
                        options: {
                            responsive: true, maintainAspectRatio: false,
                            plugins: { legend: { display: false } },
                            scales: { y: { beginAtZero: true, grid: { color: 'rgba(16,185,129,0.05)' } }, x: { grid: { display: false } } }
                        }
                    }
                );
            }
        } catch (err) {
            console.warn('Carbon: trend chart failed:', err);
        }
    }

    function renderCatChartFromServer(breakdown) {
        if (!breakdown) return;
        const cats = Object.keys(breakdown);
        const colors = ['#6366f1','#f59e0b','#ef4444','#3b82f6','#8b5cf6','#ec4899'];
        const data = cats.map(c => Number(breakdown[c] || 0));

        EcoUtils.destroyChart('carbon-category-chart');
        EcoUtils.chartInstances['carbon-category-chart'] = new Chart(
            document.getElementById('carbon-category-chart'), {
                type: 'bar',
                data: { labels: cats.map(c => c.charAt(0).toUpperCase() + c.slice(1)), datasets: [{ data, backgroundColor: colors.slice(0, cats.length), borderRadius: 6 }] },
                options: {
                    indexAxis: 'y', responsive: true, maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: { x: { beginAtZero: true, grid: { display: false } }, y: { grid: { color: 'rgba(16,185,129,0.05)' } } }
                }
            }
        );
    }

    function renderTable(entries) {
        const tbody = document.getElementById('carbon-entries-tbody');
        const empty = document.getElementById('carbon-entries-empty');
        if (!tbody) return;
        if (!entries.length) { tbody.innerHTML = ''; empty && (empty.style.display = 'flex'); return; }
        if (empty) empty.style.display = 'none';

        const getRisk = co2 => {
            const b = AppState.user?.carbonBudget || 4.2;
            const p = (co2 / b) * 100;
            if (p <= 25) return '<span class="risk-badge risk-badge--good" style="font-size:10px;">GOOD</span>';
            if (p <= 75) return '<span class="risk-badge risk-badge--moderate" style="font-size:10px;">MOD</span>';
            if (p <= 100) return '<span class="risk-badge risk-badge--high" style="font-size:10px;">HIGH</span>';
            return '<span class="risk-badge risk-badge--extreme" style="font-size:10px;">EXTREME</span>';
        };

        tbody.innerHTML = entries.map(e => {
            const co2 = Number(e.co2 || 0);
            const calcType = e.calculationType || 'EMISSION';
            const typeLabel = calcType === 'AVOIDED_EMISSION' ? '🌱' : '';
            return `<tr>
                <td><span style="color:var(--primary);font-weight:600;font-size:12px;text-transform:uppercase;">${e.category}</span></td>
                <td>${e.type} ${typeLabel}</td>
                <td style="font-family:'JetBrains Mono';font-weight:700;">${co2.toFixed(3)} kg</td>
                <td>${getRisk(co2)}</td>
                <td>${e.entryDate ? new Date(e.entryDate).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}</td>
                <td><button class="btn btn-sm btn-danger" style="width:auto;" data-action="deleteCarbonEntry" data-id="${e.id}"><i class="fa-solid fa-trash"></i></button></td>
            </tr>`;
        }).join('');
    }

    async function deleteEntry(id) {
        try { await EcoAPI.apiDelete(`/api/carbon/entries/${id}`); } catch (err) {}
        Dashboard.invalidateCache();
        render();
        Dashboard.render();
    }

    async function clearToday() {
        try { await EcoAPI.apiDelete('/api/carbon/entries/today/clear'); } catch (err) {}
        Dashboard.invalidateCache();
        render();
        Dashboard.render();
        EcoVerse.showToast("Today's log cleared", 'warning');
    }

    function renderSuggestions(entries) {
        // Suggestions are category-based from server; kept for compatibility
        const totals = {};
        (entries || []).forEach(e => { if (!totals[e.category]) totals[e.category] = 0; totals[e.category] += Number(e.co2 || 0); });
        const sorted = Object.entries(totals).sort((a, b) => b[1] - a[1]);
        const top = sorted[0] ? sorted[0][0] : null;

        const tips = {
            transport: [
                { i: 'fa-bicycle', t: 'Cycle Short Distances', d: 'Replace car trips under 5km with cycling.', s: '1 kg/trip' },
                { i: 'fa-train', t: 'Use Public Transit', d: 'A bus emits 80% less per passenger.', s: '0.12 kg/km' }
            ],
            energy: [
                { i: 'fa-solar-panel', t: 'Switch to Solar', d: 'Even partial solar cuts home emissions by 50%.', s: '0.4 kg/kWh' },
                { i: 'fa-lightbulb', t: 'Use LED Lighting', d: 'LEDs use 75% less energy.', s: '0.15 kg/bulb' }
            ],
            food: [
                { i: 'fa-carrot', t: 'Go Plant-Based 3 Days/Week', d: 'Replacing meat with plants lowers food carbon.', s: '2.5 kg/meal' },
                { i: 'fa-store', t: 'Buy Local Produce', d: 'Local food travels less.', s: '0.5 kg/meal' }
            ],
            shopping: [{ i: 'fa-recycle', t: 'Buy Second-Hand', d: 'Pre-owned items save 50% manufacturing emissions.', s: '50% less' }],
            waste: [
                { i: 'fa-compost', t: 'Start Composting', d: 'Composting reduces methane significantly.', s: '2.2 kg/kg' },
                { i: 'fa-recycle', t: 'Recycle Everything', d: 'Sort your waste properly.', s: '0.2 kg/kg' }
            ],
            digital: [{ i: 'fa-download', t: 'Lower Video Quality', d: 'HD to SD cuts streaming emissions by 50%.', s: '0.035 kg/hr' }]
        };

        const el = document.getElementById('carbon-suggestions');
        if (!el) return;
        el.innerHTML = (tips[top] || []).map(t =>
            `<div class="suggestion-item">
                <div class="suggestion-icon" style="background:var(--primary-subtle);color:var(--primary);"><i class="fa-solid ${t.i}"></i></div>
                <div class="suggestion-content"><div class="suggestion-title">${t.t}</div><div class="suggestion-desc">${t.d}</div></div>
                <div class="suggestion-save">Saves ${t.s}</div>
            </div>`
        ).join('') || '<p style="color:var(--text-faint);text-align:center;padding:20px;">Log emissions to get personalized tips!</p>';
    }

    return {
        render, updateRiskBadge, updateBudgetUI,
        switchCat, setupCalcListeners, addEntry, setTime,
        renderTrendChart, renderCatChartFromServer, renderTable,
        deleteEntry, clearToday, renderSuggestions
    };
})();
