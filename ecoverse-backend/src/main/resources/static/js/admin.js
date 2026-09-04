/**
 * EcoVerse — Admin Control Center (Complete Rewrite)
 *
 * SECURITY: ADMIN role required (enforced server-side via @PreAuthorize)
 * All data from server API — NO mock data, NO hardcoded stats
 * Every admin action is audited server-side
 */

const Admin = (() => {
    let currentSection = 'dashboard';
    let currentPage = 0;
    let totalPages = 0;
    let userSearchQuery = '';
    let productStatusFilter = '';
    let orderStatusFilter = '';
    let reviewStatusFilter = '';
    let auditActionFilter = '';
    let selectedUserId = null;

    // ================================================================
    // ACCESS GUARD — Non-admins see NOTHING, not even the layout
    // ================================================================

    function isAdmin() {
        try {
            const user = JSON.parse(localStorage.getItem('eco_user') || 'null');
            return user && user.role === 'ADMIN';
        } catch (_) { return false; }
    }

    function showAccessDenied(container) {
        if (!container) return;
        container.innerHTML = `
            <div class="admin-access-denied">
                <div class="admin-access-denied-icon"><i class="fa-solid fa-lock"></i></div>
                <h2>Access Restricted</h2>
                <p>This area is reserved for administrators only.</p>
                <button class="admin-back-btn" onclick="document.querySelector('[data-tab=dashboard]')?.click()">
                    <i class="fa-solid fa-arrow-left"></i> Back to Dashboard
                </button>
            </div>`;
    }

    function guard() {
        if (!isAdmin()) {
            const container = document.getElementById('admin-content');
            if (container) showAccessDenied(container);
            const sidebar = document.querySelector('.admin-sidebar');
            if (sidebar) sidebar.style.display = 'none';
            return false;
        }
        const sidebar = document.querySelector('.admin-sidebar');
        if (sidebar) sidebar.style.display = '';
        return true;
    }

    // ================================================================
    // MAIN RENDER
    // ================================================================

    async function render() {
        const container = document.getElementById('admin-content');
        if (!container) return;
        if (!guard()) return;
        await loadSection('dashboard');
    }

    function switchTab(tab) {
        if (!guard()) return;
        loadSection(tab);
    }

    async function loadSection(section) {
        if (!guard()) return;
        currentSection = section;
        currentPage = 0;
        selectedUserId = null;

        // Update sidebar active state
        document.querySelectorAll('.admin-nav-item').forEach(item => {
            item.classList.toggle('active', item.dataset.section === section);
        });

        const container = document.getElementById('admin-content');
        if (!container) return;
        container.innerHTML = '<div class="admin-loading">Loading...</div>';

        switch (section) {
            case 'dashboard': await loadDashboard(); break;
            case 'users': await loadUsers(); break;
            case 'audit': await loadAuditLogs(); break;
            case 'products': await loadProducts(); break;
            case 'orders': await loadOrders(); break;
            case 'reviews': await loadReviews(); break;
            case 'ai-usage': await loadAiUsage(); break;
            case 'health': await loadSystemHealth(); break;
            case 'analytics': await loadAnalyticsPage(); break;
            default: await loadDashboard();
        }
    }

    // ================================================================
    // DASHBOARD — 16 stat cards + charts
    // ================================================================

    async function loadDashboard() {
        const container = document.getElementById('admin-content');
        if (!container) return;

        try {
            const result = await EcoAPI.apiGet('/api/admin/analytics');
            const d = result?.data || {};

            container.innerHTML = '';

            // Stat cards grid
            const grid = document.createElement('div');
            grid.className = 'admin-stats-grid';

            const stats = [
                { label: 'Total Users', value: d.totalUsers || 0, icon: 'fa-users', color: '#6366f1' },
                { label: 'Active Users', value: d.activeUsers || 0, icon: 'fa-user-check', color: '#10b981' },
                { label: 'Blocked Users', value: d.blockedUsers || 0, icon: 'fa-user-xmark', color: '#ef4444' },
                { label: 'New Users (30d)', value: d.newUsers || 0, icon: 'fa-user-plus', color: '#8b5cf6' },
                { label: 'Carbon Entries', value: d.totalCarbonEntries || 0, icon: 'fa-leaf', color: '#22c55e' },
                { label: 'Total CO₂ (kg)', value: parseFloat(d.totalCo2 || 0).toFixed(1), icon: 'fa-cloud', color: '#06b6d4' },
                { label: 'Health Records', value: d.totalHealthRecords || 0, icon: 'fa-heart-pulse', color: '#f43f5e' },
                { label: 'Products', value: d.totalProducts || 0, icon: 'fa-box', color: '#f59e0b' },
                { label: 'Active Products', value: d.activeProducts || 0, icon: 'fa-box-open', color: '#84cc16' },
                { label: 'Out of Stock', value: d.outOfStockProducts || 0, icon: 'fa-triangle-exclamation', color: '#f97316' },
                { label: 'Total Orders', value: d.totalOrders || 0, icon: 'fa-shopping-cart', color: '#3b82f6' },
                { label: 'Pending Orders', value: d.pendingOrders || 0, icon: 'fa-clock', color: '#eab308' },
                { label: 'Revenue', value: `₹${(d.totalRevenue || 0).toLocaleString()}`, icon: 'fa-indian-rupee-sign', color: '#10b981' },
                { label: 'AI Requests', value: d.totalAiRequests || 0, icon: 'fa-robot', color: '#8b5cf6' },
                { label: 'Failed AI', value: d.failedAiRequests || 0, icon: 'fa-bug', color: '#ef4444' },
                { label: 'Pending Reviews', value: d.pendingReviews || 0, icon: 'fa-star', color: '#f59e0b' }
            ];

            stats.forEach(stat => {
                const card = document.createElement('div');
                card.className = 'admin-stat-card';
                card.innerHTML = `<div class="admin-stat-icon" style="color:${stat.color}"><i class="fa-solid ${stat.icon}"></i></div>` +
                    `<div class="admin-stat-value">${stat.value}</div>` +
                    `<div class="admin-stat-label">${stat.label}</div>`;
                grid.appendChild(card);
            });

            container.appendChild(grid);

            // Load chart data
            try {
                const chartResult = await EcoAPI.apiGet('/api/admin/analytics/charts');
                const cd = chartResult?.data || {};

                // Carbon trend chart
                if (cd.carbonTrend && cd.carbonTrend.length > 0) {
                    const chartSection = document.createElement('div');
                    chartSection.className = 'admin-chart-section';
                    chartSection.innerHTML = '<h3 class="admin-section-title">Carbon Emissions Trend (30 days)</h3>';
                    const canvas = document.createElement('canvas');
                    canvas.id = 'admin-carbon-trend';
                    canvas.height = 200;
                    chartSection.appendChild(canvas);
                    container.appendChild(chartSection);

                    EcoUtils.destroyChart('admin-carbon-trend');
                    new Chart(canvas, {
                        type: 'line',
                        data: {
                            labels: cd.carbonTrend.map(r => r[0]),
                            datasets: [{
                                label: 'CO₂ (kg)',
                                data: cd.carbonTrend.map(r => parseFloat(r[1] || 0)),
                                borderColor: '#10b981',
                                backgroundColor: 'rgba(16,185,129,0.1)',
                                fill: true,
                                tension: 0.3
                            }]
                        },
                        options: { responsive: true, plugins: { legend: { display: false } } }
                    });
                }

                // Order status distribution
                if (cd.orderStatusDistribution && cd.orderStatusDistribution.length > 0) {
                    const chartSection = document.createElement('div');
                    chartSection.className = 'admin-chart-section';
                    chartSection.innerHTML = '<h3 class="admin-section-title">Order Status Distribution</h3>';
                    const canvas = document.createElement('canvas');
                    canvas.id = 'admin-order-dist';
                    canvas.height = 200;
                    chartSection.appendChild(canvas);
                    container.appendChild(chartSection);

                    const colors = ['#f59e0b', '#3b82f6', '#8b5cf6', '#10b981', '#ef4444', '#6366f1', '#f97316'];
                    EcoUtils.destroyChart('admin-order-dist');
                    new Chart(canvas, {
                        type: 'doughnut',
                        data: {
                            labels: cd.orderStatusDistribution.map(r => r[0]),
                            datasets: [{
                                data: cd.orderStatusDistribution.map(r => r[1]),
                                backgroundColor: colors
                            }]
                        },
                        options: { responsive: true }
                    });
                }
            } catch (e) { /* Charts optional, don't fail the whole page */ }

        } catch (e) {
            container.innerHTML = '<div class="admin-error">Failed to load dashboard</div>';
        }
    }

    // ================================================================
    // USERS — Table with search/filters/pagination
    // ================================================================

    async function loadUsers(page = 0) {
        currentPage = page;
        const container = document.getElementById('admin-content');
        if (!container) return;
        container.innerHTML = '<div class="admin-loading">Loading users...</div>';

        try {
            let url = `/api/admin/users?page=${page}&size=20`;
            if (userSearchQuery) url += `&search=${encodeURIComponent(userSearchQuery)}`;

            const result = await EcoAPI.apiGet(url);
            const users = result?.data?.content || [];
            totalPages = result?.data?.totalPages || 0;

            container.innerHTML = '';

            // Search bar
            const searchBar = document.createElement('div');
            searchBar.className = 'admin-search-bar';
            searchBar.innerHTML = `
                <input type="text" id="admin-user-search" class="admin-search-input" placeholder="Search by name or email..." value="${userSearchQuery}" />
                <button class="admin-search-btn" data-action="adminSearchUsers"><i class="fa-solid fa-magnifying-glass"></i> Search</button>
            `;
            container.appendChild(searchBar);

            if (users.length === 0) {
                showEmpty(container, userSearchQuery ? `No users matching "${userSearchQuery}"` : 'No users found');
                appendPagination(container, page, loadUsers);
                return;
            }

            const table = createTable(['ID', 'Name', 'Email', 'Role', 'Status', 'Joined', 'Actions']);
            users.forEach(user => {
                const row = document.createElement('tr');
                row.className = 'admin-clickable-row';
                row.onclick = () => loadUserDetail(user.id);

                const cells = [
                    String(user.id || ''),
                    escHtml(user.name || ''),
                    escHtml(user.email || ''),
                    `<span class="admin-badge admin-badge-${(user.role || 'USER').toLowerCase()}">${user.role || 'USER'}</span>`,
                    user.enabled ? '<span class="admin-badge admin-badge-active">Active</span>' : '<span class="admin-badge admin-badge-blocked">Blocked</span>',
                    user.createdAt ? new Date(user.createdAt).toLocaleDateString() : ''
                ];

                cells.forEach(html => {
                    const td = document.createElement('td');
                    td.className = 'admin-td';
                    td.innerHTML = html;
                    row.appendChild(td);
                });

                // Actions
                const actionTd = document.createElement('td');
                actionTd.className = 'admin-td';

                const toggleBtn = document.createElement('button');
                toggleBtn.className = 'admin-action-btn';
                toggleBtn.textContent = user.enabled ? 'Block' : 'Unblock';
                toggleBtn.style.color = user.enabled ? 'var(--error, #ef4444)' : 'var(--success, #10b981)';
                toggleBtn.onclick = (e) => { e.stopPropagation(); toggleUserStatus(user.id, !user.enabled, user.name); };
                actionTd.appendChild(toggleBtn);

                if (user.role !== 'ADMIN') {
                    const promoteBtn = document.createElement('button');
                    promoteBtn.className = 'admin-action-btn';
                    promoteBtn.textContent = user.role === 'SELLER' ? '→ User' : '→ Seller';
                    promoteBtn.onclick = (e) => { e.stopPropagation(); changeUserRole(user.id, user.role === 'SELLER' ? 'USER' : 'SELLER', user.name); };
                    actionTd.appendChild(promoteBtn);
                }

                row.appendChild(actionTd);
                table.querySelector('tbody').appendChild(row);
            });

            container.appendChild(table);
            appendPagination(container, page, loadUsers);

        } catch (e) {
            container.innerHTML = '<div class="admin-error">Failed to load users</div>';
        }
    }

    async function searchUsers() {
        const input = document.getElementById('admin-user-search');
        userSearchQuery = input ? input.value.trim() : '';
        await loadUsers(0);
    }

    async function toggleUserStatus(userId, enable, userName) {
        if (!confirm(`Are you sure you want to ${enable ? 'unblock' : 'block'} ${userName || 'this user'}? ${enable ? '' : 'They will be immediately logged out.'}`)) return;
        try {
            await EcoAPI.apiPatch(`/api/admin/users/${userId}/status`, { enabled: enable });
            EcoVerse.showToast(`User ${enable ? 'unblocked' : 'blocked'}`, 'success');
            if (selectedUserId) loadUserDetail(selectedUserId);
            else loadUsers(currentPage);
        } catch (e) {
            EcoVerse.showToast(e?.message || 'Failed to update user', 'error');
        }
    }

    async function changeUserRole(userId, newRole, userName) {
        if (!confirm(`Change ${userName || 'user'}'s role to ${newRole}?`)) return;
        try {
            await EcoAPI.apiPatch(`/api/admin/users/${userId}/role`, { role: newRole });
            EcoVerse.showToast(`Role updated to ${newRole}`, 'success');
            if (selectedUserId) loadUserDetail(selectedUserId);
            else loadUsers(currentPage);
        } catch (e) {
            EcoVerse.showToast(e?.message || 'Failed to update role', 'error');
        }
    }

    // ================================================================
    // USER 360° PROFILE
    // ================================================================

    async function loadUserDetail(userId) {
        selectedUserId = userId;
        const container = document.getElementById('admin-content');
        if (!container) return;
        container.innerHTML = '<div class="admin-loading">Loading user profile...</div>';

        try {
            const result = await EcoAPI.apiGet(`/api/admin/users/${userId}/detail`);
            const d = result?.data || {};

            container.innerHTML = '';

            // Back button
            const backBtn = document.createElement('button');
            backBtn.className = 'admin-back-btn';
            backBtn.innerHTML = '<i class="fa-solid fa-arrow-left"></i> Back to Users';
            backBtn.onclick = () => { selectedUserId = null; loadUsers(currentPage); };
            container.appendChild(backBtn);

            // Profile header
            const header = document.createElement('div');
            header.className = 'admin-detail-header';
            header.innerHTML = `
                <div class="admin-detail-avatar"><i class="fa-solid fa-user"></i></div>
                <div class="admin-detail-info">
                    <h2>${escHtml(d.name || 'Unknown')}</h2>
                    <p>${escHtml(d.email || '')}</p>
                    <div class="admin-detail-badges">
                        <span class="admin-badge admin-badge-${(d.role || 'user').toLowerCase()}">${d.role || 'USER'}</span>
                        ${d.enabled ? '<span class="admin-badge admin-badge-active">Active</span>' : '<span class="admin-badge admin-badge-blocked">Blocked</span>'}
                        ${d.isPremium ? '<span class="admin-badge" style="background:#f59e0b;color:#fff">Premium</span>' : ''}
                    </div>
                </div>
                <div class="admin-detail-actions">
                    <button class="admin-action-btn" style="color:${d.enabled ? '#ef4444' : '#10b981'}" onclick="Admin.toggleUserStatus(${userId}, ${!d.enabled}, '${escHtml(d.name || '')}')">
                        ${d.enabled ? 'Block User' : 'Unblock User'}
                    </button>
                    ${d.role !== 'ADMIN' ? `<button class="admin-action-btn" onclick="Admin.changeUserRole(${userId}, '${d.role === 'SELLER' ? 'USER' : 'SELLER'}', '${escHtml(d.name || '')}')">Make ${d.role === 'SELLER' ? 'User' : 'Seller'}</button>` : ''}
                </div>
            `;
            container.appendChild(header);

            // Summary cards
            const cs = d.carbonSummary || {};
            const hs = d.healthSummary || {};
            const ss = d.shopSummary || {};
            const ai = d.aiSummary || {};

            const summaryGrid = document.createElement('div');
            summaryGrid.className = 'admin-stats-grid';
            summaryGrid.innerHTML = `
                <div class="admin-stat-card"><div class="admin-stat-value">${cs.entryCount || 0}</div><div class="admin-stat-label">Carbon Entries</div></div>
                <div class="admin-stat-card"><div class="admin-stat-value">${parseFloat(cs.totalEmissions || 0).toFixed(1)} kg</div><div class="admin-stat-label">Total CO₂</div></div>
                <div class="admin-stat-card"><div class="admin-stat-value">${hs.entryCount || 0}</div><div class="admin-stat-label">Health Logs</div></div>
                <div class="admin-stat-card"><div class="admin-stat-value">${ss.orderCount || 0}</div><div class="admin-stat-label">Orders</div></div>
                <div class="admin-stat-card"><div class="admin-stat-value">₹${(ss.totalSpending || 0).toLocaleString()}</div><div class="admin-stat-label">Total Spending</div></div>
                <div class="admin-stat-card"><div class="admin-stat-value">${ai.requestCount || 0}</div><div class="admin-stat-label">AI Requests</div></div>
                <div class="admin-stat-card"><div class="admin-stat-value">${d.achievementCount || 0}</div><div class="admin-stat-label">Achievements</div></div>
                <div class="admin-stat-card"><div class="admin-stat-value">${d.notesCount || 0}</div><div class="admin-stat-label">Notes</div></div>
            `;
            container.appendChild(summaryGrid);

            // Carbon category breakdown
            if (cs.categoryBreakdown && cs.categoryBreakdown.length > 0) {
                const section = document.createElement('div');
                section.className = 'admin-chart-section';
                section.innerHTML = '<h3 class="admin-section-title">Carbon Category Breakdown</h3>';
                const canvas = document.createElement('canvas');
                canvas.id = 'admin-user-carbon-breakdown';
                canvas.height = 180;
                section.appendChild(canvas);
                container.appendChild(section);

                EcoUtils.destroyChart('admin-user-carbon-breakdown');
                new Chart(canvas, {
                    type: 'doughnut',
                    data: {
                        labels: cs.categoryBreakdown.map(r => r[0]),
                        datasets: [{ data: cs.categoryBreakdown.map(r => parseFloat(r[2] || 0)), backgroundColor: ['#10b981','#3b82f6','#f59e0b','#ef4444','#8b5cf6','#06b6d4','#f97316','#22c55e'] }]
                    },
                    options: { responsive: true }
                });
            }

            // Basic info table
            const infoSection = document.createElement('div');
            infoSection.className = 'admin-info-section';
            infoSection.innerHTML = `
                <h3 class="admin-section-title">Account Details</h3>
                <table class="admin-table">
                    <tr><td class="admin-td" style="font-weight:600">User ID</td><td class="admin-td">${d.id}</td></tr>
                    <tr><td class="admin-td" style="font-weight:600">Provider</td><td class="admin-td">${d.provider || 'LOCAL'}</td></tr>
                    <tr><td class="admin-td" style="font-weight:600">Country</td><td class="admin-td">${d.country || '—'}</td></tr>
                    <tr><td class="admin-td" style="font-weight:600">City</td><td class="admin-td">${d.city || '—'}</td></tr>
                    <tr><td class="admin-td" style="font-weight:600">Joined</td><td class="admin-td">${d.createdAt ? new Date(d.createdAt).toLocaleString() : '—'}</td></tr>
                    <tr><td class="admin-td" style="font-weight:600">Last AI Request</td><td class="admin-td">${ai.lastRequest ? new Date(ai.lastRequest).toLocaleString() : 'Never'}</td></tr>
                </table>
            `;
            container.appendChild(infoSection);

        } catch (e) {
            container.innerHTML = '<div class="admin-error">Failed to load user details</div>';
        }
    }

    // ================================================================
    // AUDIT LOGS
    // ================================================================

    async function loadAuditLogs(page = 0) {
        currentPage = page;
        const container = document.getElementById('admin-content');
        if (!container) return;
        container.innerHTML = '<div class="admin-loading">Loading audit logs...</div>';

        try {
            let url = `/api/admin/audit-logs?page=${page}&size=25`;
            if (auditActionFilter) url += `&action=${auditActionFilter}`;

            const result = await EcoAPI.apiGet(url);
            const logs = result?.data?.content || [];
            totalPages = result?.data?.totalPages || 0;

            container.innerHTML = '';

            // Filter bar
            const filterBar = document.createElement('div');
            filterBar.className = 'admin-filter-bar';
            filterBar.innerHTML = `
                <select id="admin-audit-filter" class="admin-filter-select" data-action-change="adminAuditFilter">
                    <option value="">All Actions</option>
                    <option value="REGISTER" ${auditActionFilter === 'REGISTER' ? 'selected' : ''}>Register</option>
                    <option value="LOGIN" ${auditActionFilter === 'LOGIN' ? 'selected' : ''}>Login</option>
                    <option value="LOGIN_FAILED" ${auditActionFilter === 'LOGIN_FAILED' ? 'selected' : ''}>Login Failed</option>
                    <option value="ACCOUNT_DISABLE" ${auditActionFilter === 'ACCOUNT_DISABLE' ? 'selected' : ''}>Account Disabled</option>
                    <option value="ACCOUNT_ENABLE" ${auditActionFilter === 'ACCOUNT_ENABLE' ? 'selected' : ''}>Account Enabled</option>
                    <option value="ROLE_CHANGE" ${auditActionFilter === 'ROLE_CHANGE' ? 'selected' : ''}>Role Change</option>
                    <option value="PRODUCT_STATUS_CHANGE" ${auditActionFilter === 'PRODUCT_STATUS_CHANGE' ? 'selected' : ''}>Product Change</option>
                    <option value="ORDER_STATE_OVERRIDE" ${auditActionFilter === 'ORDER_STATE_OVERRIDE' ? 'selected' : ''}>Order Override</option>
                    <option value="REVIEW_STATUS_CHANGE" ${auditActionFilter === 'REVIEW_STATUS_CHANGE' ? 'selected' : ''}>Review Change</option>
                    <option value="CARBON_ENTRY_CREATE" ${auditActionFilter === 'CARBON_ENTRY_CREATE' ? 'selected' : ''}>Carbon Create</option>
                    <option value="CARBON_ENTRY_DELETE" ${auditActionFilter === 'CARBON_ENTRY_DELETE' ? 'selected' : ''}>Carbon Delete</option>
                    <option value="HEALTH_LOG_CREATE" ${auditActionFilter === 'HEALTH_LOG_CREATE' ? 'selected' : ''}>Health Create</option>
                    <option value="PASSWORD_RESET" ${auditActionFilter === 'PASSWORD_RESET' ? 'selected' : ''}>Password Reset</option>
                </select>
            `;
            container.appendChild(filterBar);

            if (logs.length === 0) {
                showEmpty(container, 'No audit logs found');
                return;
            }

            const table = createTable(['Time', 'User ID', 'Action', 'Resource', 'IP', 'Details']);
            logs.forEach(log => {
                const row = document.createElement('tr');
                const cells = [
                    log.createdAt ? new Date(log.createdAt).toLocaleString() : '',
                    String(log.userId || ''),
                    `<span class="admin-badge admin-badge-action">${escHtml(log.action || '')}</span>`,
                    escHtml((log.resource || '').substring(0, 40)),
                    escHtml(log.ipAddress || ''),
                    escHtml((log.details || '').substring(0, 60))
                ];
                cells.forEach(html => {
                    const td = document.createElement('td');
                    td.className = 'admin-td';
                    td.innerHTML = html;
                    row.appendChild(td);
                });
                table.querySelector('tbody').appendChild(row);
            });

            container.appendChild(table);
            appendPagination(container, page, loadAuditLogs);

        } catch (e) {
            container.innerHTML = '<div class="admin-error">Failed to load audit logs</div>';
        }
    }

    // ================================================================
    // PRODUCTS
    // ================================================================

    async function loadProducts(page = 0) {
        currentPage = page;
        const container = document.getElementById('admin-content');
        if (!container) return;
        container.innerHTML = '<div class="admin-loading">Loading products...</div>';

        try {
            let url = `/api/admin/products?page=${page}&size=20`;
            if (productStatusFilter) url += `&status=${productStatusFilter}`;

            const result = await EcoAPI.apiGet(url);
            const products = result?.data?.content || [];
            totalPages = result?.data?.totalPages || 0;

            container.innerHTML = '';

            // Filter bar
            const filterBar = document.createElement('div');
            filterBar.className = 'admin-filter-bar';
            filterBar.innerHTML = `
                <button class="admin-product-filter-btn ${productStatusFilter === '' ? 'active' : ''}" data-action="adminFilterProducts" data-status="">All</button>
                <button class="admin-product-filter-btn ${productStatusFilter === 'ACTIVE' ? 'active' : ''}" data-action="adminFilterProducts" data-status="ACTIVE">☀️ Active</button>
                <button class="admin-product-filter-btn ${productStatusFilter === 'ARCHIVED' ? 'active' : ''}" data-action="adminFilterProducts" data-status="ARCHIVED">📦 Archived</button>
                <button class="admin-product-filter-btn ${productStatusFilter === 'OUT_OF_STOCK' ? 'active' : ''}" data-action="adminFilterProducts" data-status="OUT_OF_STOCK">⚠️ Out of Stock</button>
                <button class="admin-product-filter-btn ${productStatusFilter === 'INACTIVE' ? 'active' : ''}" data-action="adminFilterProducts" data-status="INACTIVE">💤 Inactive</button>
            `;
            container.appendChild(filterBar);

            if (products.length === 0) {
                showEmpty(container, 'No products found');
                appendPagination(container, page, loadProducts);
                return;
            }

            const table = createTable(['ID', 'Image', 'Name', 'Category', 'Price', 'Stock', 'Rating', 'Status', 'Actions']);
            products.forEach(p => {
                const row = document.createElement('tr');
                const cells = [
                    String(p.id || ''),
                    p.imageUrl ? `<img src="${escHtml(p.imageUrl)}" style="width:40px;height:40px;object-fit:cover;border-radius:4px" onerror="this.outerHTML='<i class=fa-solid fa-leaf style=color:#999></i>'" />` : '<i class="fa-solid fa-leaf" style="color:#999"></i>',
                    escHtml((p.name || '').substring(0, 30)),
                    String(p.category || ''),
                    `₹${(p.price || 0).toLocaleString()}`,
                    String(p.stock ?? 0),
                    String(p.rating || '—'),
                    String(p.status || 'ACTIVE')
                ];
                cells.forEach(html => {
                    const td = document.createElement('td');
                    td.className = 'admin-td';
                    td.innerHTML = html;
                    row.appendChild(td);
                });

                const actionTd = document.createElement('td');
                actionTd.className = 'admin-td';

                if (p.status === 'ACTIVE') {
                    const btn = document.createElement('button');
                    btn.className = 'admin-action-btn';
                    btn.textContent = 'Archive';
                    btn.style.color = '#ef4444';
                    btn.onclick = () => updateProductStatus(p.id, 'ARCHIVED');
                    actionTd.appendChild(btn);
                } else if (p.status === 'ARCHIVED' || p.status === 'INACTIVE') {
                    const btn = document.createElement('button');
                    btn.className = 'admin-action-btn';
                    btn.textContent = 'Activate';
                    btn.style.color = '#10b981';
                    btn.onclick = () => updateProductStatus(p.id, 'ACTIVE');
                    actionTd.appendChild(btn);
                }

                row.appendChild(actionTd);
                table.querySelector('tbody').appendChild(row);
            });

            container.appendChild(table);
            appendPagination(container, page, loadProducts);

        } catch (e) {
            container.innerHTML = '<div class="admin-error">Failed to load products</div>';
        }
    }

    async function updateProductStatus(productId, status) {
        if (!confirm(`Are you sure you want to set this product to ${status}?`)) return;
        try {
            await EcoAPI.apiPatch(`/api/admin/products/${productId}/status`, { status });
            EcoVerse.showToast('Product status updated', 'success');
            loadProducts(currentPage);
        } catch (e) {
            EcoVerse.showToast(e?.message || 'Failed to update product', 'error');
        }
    }

    function setProductFilter(status) {
        productStatusFilter = status;
        document.querySelectorAll('.admin-product-filter-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.status === status);
        });
        loadProducts(0);
    }

    // ================================================================
    // ORDERS
    // ================================================================

    async function loadOrders(page = 0) {
        currentPage = page;
        const container = document.getElementById('admin-content');
        if (!container) return;
        container.innerHTML = '<div class="admin-loading">Loading orders...</div>';

        try {
            let url = `/api/admin/orders?page=${page}&size=20`;
            if (orderStatusFilter) url += `&status=${orderStatusFilter}`;

            const result = await EcoAPI.apiGet(url);
            const orders = result?.data?.content || [];
            totalPages = result?.data?.totalPages || 0;

            container.innerHTML = '';

            // Filter bar
            const filterBar = document.createElement('div');
            filterBar.className = 'admin-filter-bar';
            filterBar.innerHTML = `<select id="admin-order-status-filter" class="admin-filter-select" data-action-change="adminFilterOrders">
                <option value="">All Statuses</option>
                <option value="PENDING_PAYMENT" ${orderStatusFilter === 'PENDING_PAYMENT' ? 'selected' : ''}>Pending Payment</option>
                <option value="PAID" ${orderStatusFilter === 'PAID' ? 'selected' : ''}>Paid</option>
                <option value="PROCESSING" ${orderStatusFilter === 'PROCESSING' ? 'selected' : ''}>Processing</option>
                <option value="SHIPPED" ${orderStatusFilter === 'SHIPPED' ? 'selected' : ''}>Shipped</option>
                <option value="DELIVERED" ${orderStatusFilter === 'DELIVERED' ? 'selected' : ''}>Delivered</option>
                <option value="CANCELLED" ${orderStatusFilter === 'CANCELLED' ? 'selected' : ''}>Cancelled</option>
                <option value="REFUNDED" ${orderStatusFilter === 'REFUNDED' ? 'selected' : ''}>Refunded</option>
            </select>`;
            container.appendChild(filterBar);

            if (orders.length === 0) {
                showEmpty(container, 'No orders found');
                appendPagination(container, page, loadOrders);
                return;
            }

            const table = createTable(['ID', 'User', 'Items', 'Total', 'Status', 'Payment', 'Date', 'Actions']);
            orders.forEach(o => {
                const row = document.createElement('tr');
                const statusColor = { DELIVERED: '#10b981', CANCELLED: '#ef4444', REFUNDED: '#f97316', PENDING_PAYMENT: '#eab308', PAID: '#3b82f6' };
                const cells = [
                    String(o.id || ''),
                    String(o.userId || ''),
                    String(o.itemCount || (o.items ? o.items.length : '—')),
                    `₹${(o.totalPrice || 0).toLocaleString()}`,
                    `<span class="admin-badge" style="background:${statusColor[o.status] || '#6b7280'};color:#fff">${o.status || ''}</span>`,
                    String(o.paymentStatus || ''),
                    o.createdAt ? new Date(o.createdAt).toLocaleDateString() : ''
                ];
                cells.forEach(html => {
                    const td = document.createElement('td');
                    td.className = 'admin-td';
                    td.innerHTML = html;
                    row.appendChild(td);
                });

                const actionTd = document.createElement('td');
                actionTd.className = 'admin-td';
                const select = document.createElement('select');
                select.className = 'admin-status-select';
                const statuses = ['PENDING_PAYMENT', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
                statuses.forEach(s => {
                    const opt = document.createElement('option');
                    opt.value = s;
                    opt.textContent = s;
                    if (s === o.status) opt.selected = true;
                    select.appendChild(opt);
                });
                select.onchange = () => updateOrderStatus(o.id, select.value);
                actionTd.appendChild(select);

                row.appendChild(actionTd);
                table.querySelector('tbody').appendChild(row);
            });

            container.appendChild(table);
            appendPagination(container, page, loadOrders);

        } catch (e) {
            container.innerHTML = '<div class="admin-error">Failed to load orders</div>';
        }
    }

    async function updateOrderStatus(orderId, status) {
        if (!confirm(`Change order #${orderId} status to ${status}?`)) return;
        try {
            await EcoAPI.apiPatch(`/api/admin/orders/${orderId}/status`, { status });
            EcoVerse.showToast('Order status updated', 'success');
            loadOrders(currentPage);
        } catch (e) {
            EcoVerse.showToast(e?.message || 'Failed to update order', 'error');
            loadOrders(currentPage);
        }
    }

    function setOrderFilter(status) {
        orderStatusFilter = status;
        loadOrders(0);
    }

    // ================================================================
    // REVIEWS
    // ================================================================

    async function loadReviews(page = 0) {
        currentPage = page;
        const container = document.getElementById('admin-content');
        if (!container) return;
        container.innerHTML = '<div class="admin-loading">Loading reviews...</div>';

        try {
            let url = `/api/admin/reviews?page=${page}&size=20`;
            if (reviewStatusFilter) url += `&status=${reviewStatusFilter}`;

            const result = await EcoAPI.apiGet(url);
            const reviews = result?.data?.content || [];
            totalPages = result?.data?.totalPages || 0;

            container.innerHTML = '';

            // Filter bar
            const filterBar = document.createElement('div');
            filterBar.className = 'admin-filter-bar';
            filterBar.innerHTML = `
                <button class="admin-product-filter-btn ${reviewStatusFilter === '' ? 'active' : ''}" data-action="adminFilterReviews" data-status="">All</button>
                <button class="admin-product-filter-btn ${reviewStatusFilter === 'PENDING' ? 'active' : ''}" data-action="adminFilterReviews" data-status="PENDING">⏳ Pending</button>
                <button class="admin-product-filter-btn ${reviewStatusFilter === 'APPROVED' ? 'active' : ''}" data-action="adminFilterReviews" data-status="APPROVED">✅ Approved</button>
                <button class="admin-product-filter-btn ${reviewStatusFilter === 'HIDDEN' ? 'active' : ''}" data-action="adminFilterReviews" data-status="HIDDEN">🚫 Hidden</button>
                <button class="admin-product-filter-btn ${reviewStatusFilter === 'FLAGGED' ? 'active' : ''}" data-action="adminFilterReviews" data-status="FLAGGED">🚩 Flagged</button>
            `;
            container.appendChild(filterBar);

            if (reviews.length === 0) {
                showEmpty(container, 'No reviews found');
                appendPagination(container, page, loadReviews);
                return;
            }

            const table = createTable(['ID', 'Product', 'User', 'Rating', 'Title', 'Status', 'Date', 'Actions']);
            reviews.forEach(r => {
                const row = document.createElement('tr');
                const stars = '★'.repeat(r.rating || 0) + '☆'.repeat(5 - (r.rating || 0));
                const statusColors = { PENDING: '#eab308', APPROVED: '#10b981', HIDDEN: '#6b7280', FLAGGED: '#ef4444' };
                const cells = [
                    String(r.id || ''),
                    String(r.productId || ''),
                    String(r.userId || ''),
                    `<span style="color:#f59e0b">${stars}</span>`,
                    escHtml((r.title || '').substring(0, 25)),
                    `<span class="admin-badge" style="background:${statusColors[r.status] || '#6b7280'};color:#fff">${r.status || 'PENDING'}</span>`,
                    r.createdAt ? new Date(r.createdAt).toLocaleDateString() : ''
                ];
                cells.forEach(html => {
                    const td = document.createElement('td');
                    td.className = 'admin-td';
                    td.innerHTML = html;
                    row.appendChild(td);
                });

                const actionTd = document.createElement('td');
                actionTd.className = 'admin-td';

                if (r.status === 'PENDING') {
                    const btn = document.createElement('button');
                    btn.className = 'admin-action-btn';
                    btn.textContent = 'Approve';
                    btn.style.color = '#10b981';
                    btn.onclick = () => updateReviewStatus(r.id, 'APPROVED');
                    actionTd.appendChild(btn);
                }
                if (r.status !== 'HIDDEN') {
                    const btn = document.createElement('button');
                    btn.className = 'admin-action-btn';
                    btn.textContent = 'Hide';
                    btn.style.color = '#6b7280';
                    btn.onclick = () => { if (confirm('Hide this review? It will no longer affect the product rating.')) updateReviewStatus(r.id, 'HIDDEN'); };
                    actionTd.appendChild(btn);
                }
                if (r.status === 'APPROVED') {
                    const btn = document.createElement('button');
                    btn.className = 'admin-action-btn';
                    btn.textContent = 'Flag';
                    btn.style.color = '#ef4444';
                    btn.onclick = () => updateReviewStatus(r.id, 'FLAGGED');
                    actionTd.appendChild(btn);
                }
                if (r.status === 'HIDDEN' || r.status === 'FLAGGED') {
                    const btn = document.createElement('button');
                    btn.className = 'admin-action-btn';
                    btn.textContent = 'Approve';
                    btn.style.color = '#10b981';
                    btn.onclick = () => updateReviewStatus(r.id, 'APPROVED');
                    actionTd.appendChild(btn);
                }

                row.appendChild(actionTd);
                table.querySelector('tbody').appendChild(row);
            });

            container.appendChild(table);
            appendPagination(container, page, loadReviews);

        } catch (e) {
            container.innerHTML = '<div class="admin-error">Failed to load reviews</div>';
        }
    }

    async function updateReviewStatus(reviewId, status) {
        try {
            await EcoAPI.apiPatch(`/api/admin/reviews/${reviewId}/status`, { status });
            EcoVerse.showToast('Review status updated', 'success');
            loadReviews(currentPage);
        } catch (e) {
            EcoVerse.showToast(e?.message || 'Failed to update review', 'error');
        }
    }

    // ================================================================
    // AI USAGE
    // ================================================================

    async function loadAiUsage(page = 0) {
        currentPage = page;
        const container = document.getElementById('admin-content');
        if (!container) return;
        container.innerHTML = '<div class="admin-loading">Loading AI usage...</div>';

        try {
            const result = await EcoAPI.apiGet(`/api/admin/ai-usage?page=${page}&size=20`);
            const data = result?.data || {};
            const logs = data.logs?.content || [];
            const stats = data.stats || {};
            totalPages = data.logs?.totalPages || 0;

            container.innerHTML = '';

            // Stats cards
            const grid = document.createElement('div');
            grid.className = 'admin-stats-grid';
            grid.innerHTML = `
                <div class="admin-stat-card"><div class="admin-stat-value">${stats.totalRequests || 0}</div><div class="admin-stat-label">Total Requests</div></div>
                <div class="admin-stat-card"><div class="admin-stat-value" style="color:#ef4444">${stats.failedRequests || 0}</div><div class="admin-stat-label">Failed</div></div>
            `;
            container.appendChild(grid);

            if (logs.length === 0) {
                showEmpty(container, 'No AI usage logs found');
                return;
            }

            const table = createTable(['ID', 'User', 'Provider', 'Model', 'Tokens In', 'Tokens Out', 'Success', 'Latency', 'Date']);
            logs.forEach(l => {
                const row = document.createElement('tr');
                const cells = [
                    String(l.id || ''),
                    String(l.userId || ''),
                    String(l.provider || ''),
                    String(l.model || ''),
                    String(l.inputTokens || '—'),
                    String(l.outputTokens || '—'),
                    l.success ? '<span style="color:#10b981">✓</span>' : '<span style="color:#ef4444">✗</span>',
                    l.latencyMs ? `${l.latencyMs}ms` : '—',
                    l.createdAt ? new Date(l.createdAt).toLocaleString() : ''
                ];
                cells.forEach(html => {
                    const td = document.createElement('td');
                    td.className = 'admin-td';
                    td.innerHTML = html;
                    row.appendChild(td);
                });
                table.querySelector('tbody').appendChild(row);
            });

            container.appendChild(table);
            appendPagination(container, page, loadAiUsage);

        } catch (e) {
            container.innerHTML = '<div class="admin-error">Failed to load AI usage</div>';
        }
    }

    // ================================================================
    // SYSTEM HEALTH
    // ================================================================

    async function loadSystemHealth() {
        const container = document.getElementById('admin-content');
        if (!container) return;
        container.innerHTML = '<div class="admin-loading">Checking system health...</div>';

        try {
            const result = await EcoAPI.apiGet('/api/admin/system/health');
            const health = result?.data || {};

            container.innerHTML = '';

            const grid = document.createElement('div');
            grid.className = 'admin-health-grid';

            const serviceNames = { database: 'Database', ai: 'AI Provider', email: 'Email/SMTP', payment: 'Payment', weather: 'Weather API', news: 'News API' };
            const statusIcons = { HEALTHY: '✅', CONFIGURED: '🔧', NOT_CONFIGURED: '⚠️', DEGRADED: '⚠️', FAILED: '❌' };
            const statusColors = { HEALTHY: '#10b981', CONFIGURED: '#3b82f6', NOT_CONFIGURED: '#6b7280', DEGRADED: '#f59e0b', FAILED: '#ef4444' };

            Object.entries(serviceNames).forEach(([key, name]) => {
                const service = health[key] || {};
                const status = service.status || 'UNKNOWN';
                const card = document.createElement('div');
                card.className = 'admin-health-card';
                card.style.borderLeftColor = statusColors[status] || '#6b7280';
                card.innerHTML = `
                    <div class="admin-health-status">${statusIcons[status] || '❓'} ${status}</div>
                    <div class="admin-health-name">${name}</div>
                    ${service.provider ? `<div class="admin-health-detail">Provider: ${escHtml(service.provider)}</div>` : ''}
                    ${service.host ? `<div class="admin-health-detail">Host: ${escHtml(service.host)}</div>` : ''}
                    ${service.details ? `<div class="admin-health-detail">${escHtml(service.details)}</div>` : ''}
                `;
                grid.appendChild(card);
            });

            container.appendChild(grid);

        } catch (e) {
            container.innerHTML = '<div class="admin-error">Failed to check system health</div>';
        }
    }

    // ================================================================
    // ANALYTICS PAGE — Charts
    // ================================================================

    async function loadAnalyticsPage() {
        const container = document.getElementById('admin-content');
        if (!container) return;
        container.innerHTML = '<div class="admin-loading">Loading analytics...</div>';

        try {
            const [analyticsResult, chartResult] = await Promise.all([
                EcoAPI.apiGet('/api/admin/analytics'),
                EcoAPI.apiGet('/api/admin/analytics/charts')
            ]);
            const d = analyticsResult?.data || {};
            const cd = chartResult?.data || {};

            container.innerHTML = '';

            // Carbon breakdown
            if (cd.carbonCategoryBreakdown && cd.carbonCategoryBreakdown.length > 0) {
                const section = document.createElement('div');
                section.className = 'admin-chart-section';
                section.innerHTML = '<h3 class="admin-section-title">Carbon Emissions by Category (Global)</h3>';
                const canvas = document.createElement('canvas');
                canvas.id = 'admin-analytics-carbon';
                canvas.height = 250;
                section.appendChild(canvas);
                container.appendChild(section);

                EcoUtils.destroyChart('admin-analytics-carbon');
                new Chart(canvas, {
                    type: 'bar',
                    data: {
                        labels: cd.carbonCategoryBreakdown.map(r => r[0]),
                        datasets: [{ label: 'CO₂ (kg)', data: cd.carbonCategoryBreakdown.map(r => parseFloat(r[1] || 0)), backgroundColor: '#10b981' }]
                    },
                    options: { responsive: true, plugins: { legend: { display: false } } }
                });
            }

            // Review status distribution
            if (cd.reviewStatusDistribution) {
                const section = document.createElement('div');
                section.className = 'admin-chart-section';
                section.innerHTML = '<h3 class="admin-section-title">Review Status Distribution</h3>';
                const canvas = document.createElement('canvas');
                canvas.id = 'admin-analytics-reviews';
                canvas.height = 200;
                section.appendChild(canvas);
                container.appendChild(section);

                const labels = Object.keys(cd.reviewStatusDistribution);
                const values = Object.values(cd.reviewStatusDistribution);
                const colors = { PENDING: '#eab308', APPROVED: '#10b981', HIDDEN: '#6b7280', FLAGGED: '#ef4444' };

                EcoUtils.destroyChart('admin-analytics-reviews');
                new Chart(canvas, {
                    type: 'doughnut',
                    data: {
                        labels,
                        datasets: [{ data: values, backgroundColor: labels.map(l => colors[l] || '#3b82f6') }]
                    },
                    options: { responsive: true }
                });
            }

            // Key metrics summary
            const summary = document.createElement('div');
            summary.className = 'admin-stats-grid';
            summary.innerHTML = `
                <div class="admin-stat-card"><div class="admin-stat-value">${d.totalUsers || 0}</div><div class="admin-stat-label">Total Users</div></div>
                <div class="admin-stat-card"><div class="admin-stat-value">${parseFloat(d.totalCo2 || 0).toFixed(1)} kg</div><div class="admin-stat-label">Total CO₂</div></div>
                <div class="admin-stat-card"><div class="admin-stat-value">₹${(d.totalRevenue || 0).toLocaleString()}</div><div class="admin-stat-label">Total Revenue</div></div>
                <div class="admin-stat-card"><div class="admin-stat-value">${d.totalAiRequests || 0}</div><div class="admin-stat-label">AI Requests</div></div>
            `;
            container.appendChild(summary);

        } catch (e) {
            container.innerHTML = '<div class="admin-error">Failed to load analytics</div>';
        }
    }

    // ================================================================
    // HELPERS
    // ================================================================

    function createTable(headers) {
        const table = document.createElement('table');
        table.className = 'admin-table';
        const thead = document.createElement('thead');
        const headerRow = document.createElement('tr');
        headers.forEach(h => {
            const th = document.createElement('th');
            th.textContent = h;
            th.className = 'admin-th';
            headerRow.appendChild(th);
        });
        thead.appendChild(headerRow);
        table.appendChild(thead);
        const tbody = document.createElement('tbody');
        table.appendChild(tbody);
        return table;
    }

    function appendPagination(container, currentPageNum, loadFn) {
        if (totalPages <= 1) return;
        const paginationDiv = document.createElement('div');
        paginationDiv.className = 'admin-pagination';

        const prevBtn = document.createElement('button');
        prevBtn.className = 'admin-page-btn';
        prevBtn.textContent = '← Previous';
        prevBtn.disabled = currentPageNum === 0;
        prevBtn.onclick = () => loadFn(currentPageNum - 1);
        paginationDiv.appendChild(prevBtn);

        const info = document.createElement('span');
        info.className = 'admin-page-info';
        info.textContent = `Page ${currentPageNum + 1} of ${totalPages}`;
        paginationDiv.appendChild(info);

        const nextBtn = document.createElement('button');
        nextBtn.className = 'admin-page-btn';
        nextBtn.textContent = 'Next →';
        nextBtn.disabled = currentPageNum >= totalPages - 1;
        nextBtn.onclick = () => loadFn(currentPageNum + 1);
        paginationDiv.appendChild(nextBtn);

        container.appendChild(paginationDiv);
    }

    function showEmpty(container, message) {
        const empty = document.createElement('div');
        empty.className = 'admin-empty';
        empty.textContent = message;
        container.appendChild(empty);
    }

    function escHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    return {
        render, switchTab, loadUsers, loadProducts, loadOrders, loadPaymentEvents: loadAiUsage,
        toggleUserStatus, changeUserRole, updateProductStatus, updateOrderStatus,
        searchUsers, setProductFilter, setOrderFilter, updateReviewStatus,
        loadUserDetail, loadSection, loadAuditLogs, loadReviews,
        get auditActionFilter() { return auditActionFilter; },
        set auditActionFilter(v) { auditActionFilter = v; },
        get reviewStatusFilter() { return reviewStatusFilter; },
        set reviewStatusFilter(v) { reviewStatusFilter = v; }
    };
})();

window.Admin = Admin;
