/**
 * EcoVerse — Seller Dashboard Module (Phase 5)
 *
 * SECURITY:
 * - All data from server API — NO mock data
 * - SELLER role required (enforced server-side)
 * - Only shows orders containing seller's products
 * - Seller can only update fulfillment status (PAID→PROCESSING→SHIPPED→DELIVERED)
 */

const Seller = (() => {
    let orders = [];
    let orderPage = 0;
    let hasMoreOrders = true;
    let isLoadingOrders = false;

    async function render() {
        orderPage = 0;
        hasMoreOrders = true;
        orders = [];
        await loadOrders();
    }

    async function loadOrders() {
        if (isLoadingOrders || !hasMoreOrders) return;
        isLoadingOrders = true;

        const container = document.getElementById('seller-orders-list');
        if (container && orderPage === 0) {
            container.innerHTML = '';
            container.appendChild(createLoadingSpinner());
        }

        try {
            const result = await EcoAPI.apiGet(`/api/seller/orders?page=${orderPage}&size=20`);
            const newOrders = result?.data?.content || [];
            orders = orderPage === 0 ? newOrders : [...orders, ...newOrders];
            hasMoreOrders = !result?.data?.last;
            orderPage++;
            renderOrders();
        } catch (e) {
            if (container) {
                container.innerHTML = '';
                const err = document.createElement('div');
                err.style.cssText = 'text-align:center;padding:40px;color:var(--error);';
                err.textContent = 'Failed to load seller orders';
                container.appendChild(err);
            }
        } finally {
            isLoadingOrders = false;
        }
    }

    function renderOrders() {
        const container = document.getElementById('seller-orders-list');
        if (!container) return;
        container.innerHTML = '';

        if (orders.length === 0) {
            const empty = document.createElement('div');
            empty.style.cssText = 'text-align:center;padding:40px;color:var(--text-muted);';
            empty.textContent = 'No orders containing your products yet';
            container.appendChild(empty);
            return;
        }

        orders.forEach(order => container.appendChild(createOrderCard(order)));
    }

    function createOrderCard(order) {
        const card = document.createElement('div');
        card.style.cssText = 'background:var(--card);border-radius:12px;padding:16px;margin-bottom:12px;';

        const header = document.createElement('div');
        header.style.cssText = 'display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;';

        const idSpan = document.createElement('strong');
        idSpan.textContent = `Order #${order.id}`;
        header.appendChild(idSpan);

        const statusBadge = document.createElement('span');
        statusBadge.style.cssText = `padding:4px 10px;border-radius:20px;font-size:12px;font-weight:600;${getStatusStyle(order.status)}`;
        statusBadge.textContent = formatStatus(order.status);
        header.appendChild(statusBadge);

        card.appendChild(header);

        const totalDiv = document.createElement('div');
        totalDiv.style.cssText = 'font-size:16px;font-weight:600;';
        totalDiv.textContent = `₹${(order.totalPrice || 0).toLocaleString()} (your items)`;
        card.appendChild(totalDiv);

        // Items
        if (order.items && order.items.length) {
            const itemsDiv = document.createElement('div');
            itemsDiv.style.cssText = 'font-size:13px;color:var(--text-muted);margin-top:4px;';
            itemsDiv.textContent = order.items.map(i => `${i.productName} x${i.quantity}`).join(', ');
            card.appendChild(itemsDiv);
        }

        // Status update buttons
        if (order.status === 'PAID') {
            const btn = document.createElement('button');
            btn.className = 'btn btn-primary';
            btn.style.cssText = 'margin-top:8px;font-size:12px;';
            btn.textContent = 'Start Processing';
            btn.onclick = () => updateStatus(order.id, 'PROCESSING');
            card.appendChild(btn);
        } else if (order.status === 'PROCESSING') {
            const btn = document.createElement('button');
            btn.className = 'btn btn-primary';
            btn.style.cssText = 'margin-top:8px;font-size:12px;';
            btn.textContent = 'Mark Shipped';
            btn.onclick = () => updateStatus(order.id, 'SHIPPED');
            card.appendChild(btn);
        } else if (order.status === 'SHIPPED') {
            const btn = document.createElement('button');
            btn.className = 'btn btn-primary';
            btn.style.cssText = 'margin-top:8px;font-size:12px;';
            btn.textContent = 'Mark Delivered';
            btn.onclick = () => updateStatus(order.id, 'DELIVERED');
            card.appendChild(btn);
        }

        return card;
    }

    async function updateStatus(orderId, newStatus) {
        try {
            await EcoAPI.apiPatch(`/api/seller/orders/${orderId}/status`, { status: newStatus });
            EcoVerse.showToast(`Order status updated to ${formatStatus(newStatus)}`, 'success');
            render();
        } catch (e) {
            EcoVerse.showToast(e?.message || 'Failed to update order status', 'error');
        }
    }

    function getStatusStyle(status) {
        const styles = {
            'PENDING_PAYMENT': 'background:rgba(234,179,8,0.15);color:#eab308;',
            'PAID': 'background:rgba(59,130,246,0.15);color:#3b82f6;',
            'PROCESSING': 'background:rgba(59,130,246,0.15);color:#3b82f6;',
            'SHIPPED': 'background:rgba(168,85,247,0.15);color:#a855f7;',
            'DELIVERED': 'background:rgba(34,197,94,0.15);color:#22c55e;',
            'CANCELLED': 'background:rgba(239,68,68,0.15);color:#ef4444;',
            'REFUNDED': 'background:rgba(239,68,68,0.15);color:#ef4444;'
        };
        return styles[status] || 'background:var(--border);color:var(--text-muted);';
    }

    function formatStatus(status) {
        if (!status) return 'Unknown';
        return status.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    }

    function createLoadingSpinner() {
        const spinner = document.createElement('div');
        spinner.style.cssText = 'text-align:center;padding:20px;';
        spinner.textContent = 'Loading...';
        return spinner;
    }

    return { render, loadOrders, updateStatus };
})();

window.Seller = Seller;
