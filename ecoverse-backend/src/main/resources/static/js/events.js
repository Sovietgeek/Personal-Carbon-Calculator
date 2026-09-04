/**
 * ================================================================
 * ECOVERSE — Event Delegation System
 *
 * Replaces ALL inline event handlers (onclick/onchange/oninput/onsubmit)
 * with a single delegated listener. This is required for strict CSP:
 * `script-src 'self'` blocks inline event handlers.
 *
 * Usage in HTML:
 *   <button data-action="login">Sign In</button>
 *   <button data-action="switchTab" data-tab="tab-carbon">Carbon</button>
 *   <select data-action-change="filterNews">...</select>
 *   <input data-action-input="shopFilter">
 *
 * Action handlers read parameters from data-* attributes on the element.
 * ================================================================
 */

// ============================================================
// ACTION HANDLER REGISTRY
// Map of action name -> function(el, event)
// ============================================================

const EcoActions = {

    // ===== AUTH =====
    login: () => login(),
    register: () => register(),
    googleLogin: () => googleLogin(),
    appleLogin: () => appleLogin(),
    logout: () => logout(),
    toggleAuth: (el) => toggleAuth(el.dataset.mode),
    forgotPassword: () => forgotPassword(),
    togglePw: (el) => togglePasswordVisibility(el.dataset.target, el),

    // ===== NAVIGATION =====
    switchTab: (el) => switchTab(el.dataset.tab, el),
    openPremium: () => openPremium(),

    // ===== NOTIFICATIONS =====
    openNotifications: () => openNotifications(),
    closeNotifications: () => closeNotifications(),

    // ===== MOBILE SIDEBAR / THEME =====
    toggleMobileSidebar: () => toggleMobileSidebar(),
    toggleTheme: () => toggleTheme(),

    // ===== DASHBOARD =====
    changeDashChart: (el) => changeDashChart(el.dataset.period, el),

    // ===== CARBON =====
    switchCarbonCat: (el) => switchCarbonCat(el.dataset.cat, el),
    addCarbonEntry: (el) => addCarbonEntry(el.dataset.cat),
    setCarbonTime: (el) => setCarbonTime(el.dataset.period, el),
    clearTodayCarbon: () => clearTodayCarbon(),
    deleteCarbonEntry: (el) => deleteCarbonEntry(el.dataset.id),

    // ===== HEALTH =====
    switchHealthTab: (el) => switchHealthTab(el.dataset.sub, el),
    logHealth: (el) => logHealth(el.dataset.type),
    addWater: (el) => addWater(parseInt(el.dataset.ml, 10) || 0),
    calculateBMI: () => calculateBMI(),

    // ===== WEATHER =====
    searchWeather: () => searchWeather(),
    detectLocation: () => detectLocation(),

    // ===== NEWS =====
    filterNews: () => filterNews(),
    setNewsCategory: (el) => setNewsCategory(el.dataset.cat, el),
    renderNews: () => renderNews(),
    loadMoreNews: () => loadMoreNews(),

    // ===== SHOP =====
    shopFilter: () => Shop.filterProducts(),
    shopSetCategory: (el) => Shop.setCategory(el.dataset.cat, el),
    shopOpenCart: () => Shop.openCart(),
    shopCloseCart: () => Shop.closeCart(),
    shopOpenCheckout: () => Shop.openCheckout(),
    shopCloseCheckout: () => Shop.closeCheckout(),
    shopPlaceOrder: () => Shop.placeOrder(),
    shopCloseOrderSuccess: () => Shop.closeOrderSuccess(),
    shopCloseDetail: () => Shop.closeDetail(),
    shopChangeQty: (el) => Shop.changeQty(parseInt(el.dataset.delta, 10) || 0),
    shopAddToCartDetail: () => Shop.addToCartFromDetail(),
    shopOpenSell: () => Shop.openSellModal(),
    shopCloseSell: () => Shop.closeSellModal(),
    shopSubmitProduct: () => Shop.submitProduct(),
    shopLoadMore: () => Shop.loadMore(),
    shopRenderOrders: () => Shop.renderOrderHistory(),

    // ===== ADMIN =====
    adminSwitchTab: (el) => Admin.switchTab(el.dataset.section || el.dataset.adminTab),
    adminSearchUsers: () => Admin.searchUsers(),
    adminFilterProducts: (el) => Admin.setProductFilter(el.dataset.status),
    adminFilterOrders: (el) => {
        if (el.tagName === 'SELECT') Admin.setOrderFilter(el.value);
    },
    adminFilterReviews: (el) => { Admin.reviewStatusFilter = el.dataset.status; Admin.loadReviews(0); },
    adminAuditFilter: () => {
        const sel = document.getElementById('admin-audit-filter');
        if (sel) { Admin.auditActionFilter = sel.value; Admin.loadAuditLogs(0); }
    },

    // ===== AI CHAT =====
    aiToggle: () => AI.toggle(),
    aiSend: () => {
        const input = document.getElementById('ai-chat-input');
        if (input && input.value.trim()) {
            AI.sendChat(input.value);
            input.value = '';
        }
    },
    aiClearChat: () => AI.clearChat(),
    aiQuickAction: (el) => AI.quickAction(el.dataset.prompt),

    // ===== PROFILE =====
    updateProfile: () => updateProfile(),
    updateCarbonBudget: () => updateCarbonBudget(),
    updateHealthGoals: () => updateHealthGoals(),
    exportData: () => exportData(),
    clearAllData: () => clearAllData(),
    deleteAccount: () => deleteAccount(),

    // ===== BADGES / ACHIEVEMENTS =====
    closeBadgeModal: () => closeBadgeModal(),
    badgeDetail: (el) => Achievements.showBadgeDetail(el.dataset.id),

    // ===== NOTES & TIPS =====
    openNoteEditor: () => openNoteEditor(),
    closeNoteEditor: () => closeNoteEditor(),
    saveNote: () => saveNote(),
    deleteNote: (el) => deleteNote(parseInt(el.dataset.id, 10) || 0),
    shareTip: () => shareTip(),
    bookmarkTip: () => bookmarkTip(),

    // ===== MISC =====
    showToast: (el) => showToast(el.dataset.message || '', el.dataset.type || 'info')
};

// ============================================================
// DELEGATED LISTENERS
// ============================================================

document.addEventListener('click', (e) => {
    const el = e.target.closest('[data-action]');
    if (!el) return;
    const action = EcoActions[el.dataset.action];
    if (typeof action === 'function') {
        e.preventDefault();
        action(el, e);
    }
});

document.addEventListener('change', (e) => {
    const el = e.target.closest('[data-action-change]');
    if (!el) return;
    const action = EcoActions[el.dataset.actionChange];
    if (typeof action === 'function') {
        action(el, e);
    }
});

document.addEventListener('input', (e) => {
    const el = e.target.closest('[data-action-input]');
    if (!el) return;
    const action = EcoActions[el.dataset.actionInput];
    if (typeof action === 'function') {
        action(el, e);
    }
});

// Forms: prevent default submit (buttons use type="button"; Enter key in
// inputs must not trigger a full page reload). Delegated submit handler
// replaces the removed `onsubmit="return false;"` attributes.
document.addEventListener('submit', (e) => {
    const form = e.target.closest('form[data-no-submit]');
    if (form) {
        e.preventDefault();
    }
});
