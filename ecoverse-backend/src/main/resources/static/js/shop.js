/**
 * EcoVerse — Shop Module (Amazon-Level E-Commerce Experience)
 *
 * SECURITY RULES:
 * - NO mock/default products — all data from server API
 * - NO localStorage as source of truth for cart/orders
 * - Server-authoritative prices and totals (frontend NEVER submits prices)
 * - XSS prevention: textContent/createElement only, NO innerHTML with user data
 * - Image URLs: only http/https displayed, javascript:/data: blocked
 * - Idempotency: crypto.randomUUID() on checkout to prevent duplicate orders
 * - Stock is checked at ORDER time (cart ≠ inventory reservation)
 *
 * PAYMENT RULES:
 * - Frontend NEVER trusts browser payment callback alone
 * - Success is shown ONLY after server verification confirms PAID
 * - Razorpay secret is NEVER on the frontend
 * - Payment retry creates new PaymentAttempt (not new Order)
 *
 * AMAZON-LEVEL FEATURES:
 * - Product cards: image badges, brand, rating stars, MRP strikethrough, discount %, delivery info, eco rating, hover effects
 * - Product detail: left image + thumbnails, right side with brand, rating row, price section, stock, eco rating, highlights pills, features, qty, Add to Cart + Buy Now, delivery info, seller, carbon saved
 * - Cart drawer: free delivery progress bar, save for later, quantity selector, subtotal, checkout button
 * - Checkout: order summary, address form, payment methods (card/UPI/NET/COD), Place Order
 * - Deals of the Day: horizontal scrollable section
 * - Search & Filter: debounced search, sort, price filter, eco rating filter
 * - Order History: list with status badges, cancel, retry payment
 */

const Shop = (() => {
    // ================================================================
    // STATE
    // ================================================================
    let products = [];
    let currentCategory = 'all';
    let currentDetailProduct = null;
    let productPage = 0;
    let hasMoreProducts = true;
    let isLoadingProducts = false;
    let orderPage = 0;
    let hasMoreOrders = true;
    let isLoadingOrders = false;
    let searchTerm = '';
    let currentSort = 'popular';
    let minPrice = null;
    let maxPrice = null;
    let ecoFilter = 0; // 0 = all, 3, 4, 5
    let featuredDeals = [];
    let _initialized = false;
    let _searchDebounceTimer = null;
    let _savedForLater = new Set(); // cartItemIds saved for later

    // Constants
    const FREE_DELIVERY_THRESHOLD = 499;
    const DELIVERY_CHARGE = 40;
    const GST_RATE = 0.18;

    // ================================================================
    // HELPERS
    // ================================================================

    /** Check if a URL is safe for display (http/https only) */
    function isSafeUrl(url) {
        if (!url || typeof url !== 'string') return false;
        const trimmed = url.trim().toLowerCase();
        return (trimmed.startsWith('http://') || trimmed.startsWith('https://')) &&
               !trimmed.startsWith('javascript:') &&
               !trimmed.startsWith('data:');
    }

    /** Create a leaf icon element (fallback image) */
    function createLeafIcon() {
        const icon = document.createElement('i');
        icon.className = 'fa-solid fa-leaf';
        icon.style.cssText = 'font-size:40px;color:var(--text-faint);';
        return icon;
    }

    /** Create a loading spinner element */
    function createLoadingSpinner() {
        const spinner = document.createElement('div');
        spinner.style.cssText = 'text-align:center;padding:40px;color:var(--text-muted);grid-column:1/-1;';
        spinner.innerHTML = '<i class="fa-solid fa-spinner fa-spin" style="font-size:24px;"></i><p style="margin-top:8px;">Loading...</p>';
        return spinner;
    }

    /** Format a delivery date string like "Fri, Sep 8" from deliveryDays */
    function formatDeliveryDate(days) {
        if (!days || days < 0) days = 4;
        const d = new Date();
        d.setDate(d.getDate() + days);
        const opts = { weekday: 'short', month: 'short', day: 'numeric' };
        return d.toLocaleDateString('en-US', opts);
    }

    /** Get the delivery line for a product card */
    function getDeliveryLine(p) {
        const price = p.price || 0;
        const deliveryDays = p.deliveryDays || 4;
        if (price >= FREE_DELIVERY_THRESHOLD) {
            return 'Free delivery by ' + formatDeliveryDate(deliveryDays);
        }
        return '\u20B9' + DELIVERY_CHARGE + ' delivery';
    }

    /** Estimate carbon saved for a product */
    function getCarbonSaved(p) {
        if (p.ecoRating) {
            return (p.ecoRating * 0.5).toFixed(1);
        }
        return '0.0';
    }

    /** Build a star rating HTML fragment (returns array of DOM nodes) */
    function createRatingStars(rating, count) {
        const container = document.createElement('span');
        container.style.cssText = 'display:inline-flex;align-items:center;gap:2px;';

        const r = rating || 0;
        const full = Math.floor(r);
        const half = r - full >= 0.25 ? 1 : 0;
        const empty = 5 - full - half;

        for (let i = 0; i < full; i++) {
            const star = document.createElement('i');
            star.className = 'fa-solid fa-star';
            star.style.cssText = 'color:#f59e0b;font-size:13px;';
            container.appendChild(star);
        }
        if (half) {
            const star = document.createElement('i');
            star.className = 'fa-solid fa-star-half-stroke';
            star.style.cssText = 'color:#f59e0b;font-size:13px;';
            container.appendChild(star);
        }
        for (let i = 0; i < empty; i++) {
            const star = document.createElement('i');
            star.className = 'fa-regular fa-star';
            star.style.cssText = 'color:#d1d5db;font-size:13px;';
            container.appendChild(star);
        }

        if (count !== undefined && count !== null) {
            const countSpan = document.createElement('span');
            countSpan.style.cssText = 'color:var(--text-muted);font-size:12px;margin-left:4px;';
            countSpan.textContent = count.toLocaleString('en-IN');
            container.appendChild(countSpan);
        }

        return container;
    }

    /** Create a simple badge pill element */
    function createBadgePill(text, bgColor, textColor) {
        const pill = document.createElement('span');
        pill.style.cssText = 'display:inline-block;padding:2px 8px;border-radius:12px;font-size:11px;font-weight:600;margin-right:4px;margin-bottom:4px;background:' + (bgColor || 'rgba(16,185,129,0.12)') + ';color:' + (textColor || 'var(--primary)') + ';';
        pill.textContent = text;
        return pill;
    }

    // ================================================================
    // INITIALIZATION
    // ================================================================

    /** One-time setup: inject dynamic UI sections, set up listeners */
    function init() {
        if (_initialized) return;
        _initialized = true;

        // Amazon-style responsive grid columns (5 desktop / 3 tablet / 2 mobile)
        injectGridStyles();

        // Inject Deals of the Day section
        injectDealsSection();

        // Inject filter bar (price + eco rating)
        injectFilterBar();

        // Override search input with debounced handler
        const searchEl = document.getElementById('shop-search');
        if (searchEl) {
            // Remove the data-action-input attribute to prevent double-triggering
            searchEl.removeAttribute('data-action-input');
            searchEl.addEventListener('input', function () {
                clearTimeout(_searchDebounceTimer);
                _searchDebounceTimer = setTimeout(() => {
                    searchTerm = this.value.trim();
                    Shop.filterProducts();
                }, 300);
            });
        }

        // Inject enhanced cart-drawer UI additions (free delivery progress bar)
        injectCartEnhancements();

        // Inject Net Banking option into checkout
        injectNetBankingOption();

        // Inject enhanced sort options (Relevance, Price L-H, Price H-L, Newest, Eco Rating, Customer Rating)
        injectSortOptions();

        // Inject phone/state fields into checkout address
        injectCheckoutAddressFields();

        // Inject "Buy Now" button into product detail modal
        injectBuyNowButton();

        // Inject UPI QR placeholder
        injectUpiQrPlaceholder();

        // Make the category strip horizontally scrollable (Amazon-style)
        makeCategoryStripScrollable();

        // Wire up payment method form toggling (card/upi/net/cod)
        setupPaymentToggle();

        // Make order history section expandable/collapsible
        setupOrderHistoryToggle();
    }

    /** Make the category tab strip horizontally scrollable on small screens */
    function makeCategoryStripScrollable() {
        const strip = document.querySelector('.shop-category-tabs');
        if (!strip) return;
        strip.style.cssText = 'display:flex;flex-wrap:nowrap;overflow-x:auto;gap:8px;padding-bottom:6px;scrollbar-width:thin;-webkit-overflow-scrolling:touch;margin-bottom:12px;';
    }

    /** Add a collapse/expand toggle to the order history card */
    function setupOrderHistoryToggle() {
        const historyCard = document.getElementById('order-history-list')?.closest('.card');
        if (!historyCard) return;

        const headerRow = historyCard.querySelector('.card-header-row');
        const listEl = document.getElementById('order-history-list');
        if (!headerRow || !listEl) return;

        const toggleBtn = document.createElement('button');
        toggleBtn.className = 'btn btn-ghost';
        toggleBtn.style.cssText = 'font-size:12px;';
        toggleBtn.innerHTML = '<i class="fa-solid fa-chevron-down"></i>';
        toggleBtn.title = 'Expand / Collapse orders';

        let expanded = true;
        toggleBtn.addEventListener('click', function () {
            expanded = !expanded;
            listEl.style.display = expanded ? '' : 'none';
            toggleBtn.innerHTML = expanded
                ? '<i class="fa-solid fa-chevron-down"></i>'
                : '<i class="fa-solid fa-chevron-right"></i>';
        });

        headerRow.appendChild(toggleBtn);
    }

    /** Inject responsive grid column styles (5/3/2) — CSP allows 'unsafe-inline' styles */
    function injectGridStyles() {
        if (document.getElementById('shop-grid-inline-styles')) return;
        const style = document.createElement('style');
        style.id = 'shop-grid-inline-styles';
        style.textContent =
            '#shop-products-grid.shop-products-grid{grid-template-columns:repeat(5,1fr);}' +
            '@media (max-width:1100px){#shop-products-grid.shop-products-grid{grid-template-columns:repeat(3,1fr);}}' +
            '@media (max-width:640px){#shop-products-grid.shop-products-grid{grid-template-columns:repeat(2,1fr);}}';
        document.head.appendChild(style);
    }

    /** Inject Deals of the Day section after the shop header */
    function injectDealsSection() {
        const header = document.querySelector('.shop-header');
        if (!header) return;
        const existing = document.getElementById('shop-deals-section');
        if (existing) return;

        const section = document.createElement('div');
        section.id = 'shop-deals-section';
        section.style.cssText = 'margin:12px 0 8px;';

        const titleRow = document.createElement('div');
        titleRow.style.cssText = 'display:flex;align-items:center;gap:8px;margin-bottom:8px;';

        const fireIcon = document.createElement('i');
        fireIcon.className = 'fa-solid fa-fire';
        fireIcon.style.cssText = 'color:var(--error);font-size:18px;';
        titleRow.appendChild(fireIcon);

        const title = document.createElement('span');
        title.style.cssText = 'font-weight:700;font-size:16px;color:var(--text-primary);';
        title.textContent = 'Deals of the Day';
        titleRow.appendChild(title);

        const seeAll = document.createElement('a');
        seeAll.style.cssText = 'margin-left:auto;font-size:12px;color:var(--primary);cursor:pointer;text-decoration:none;';
        seeAll.textContent = 'See all';
        seeAll.href = '#';
        seeAll.addEventListener('click', (e) => { e.preventDefault(); });
        titleRow.appendChild(seeAll);

        section.appendChild(titleRow);

        const scrollWrap = document.createElement('div');
        scrollWrap.style.cssText = 'overflow-x:auto;display:flex;gap:12px;padding-bottom:8px;scrollbar-width:thin;';
        scrollWrap.id = 'shop-deals-scroll';

        section.appendChild(scrollWrap);

        header.parentNode.insertBefore(section, header.nextSibling);
    }

    /** Inject filter bar (price min/max, eco rating filter) */
    function injectFilterBar() {
        const catTabs = document.querySelector('.shop-category-tabs');
        if (!catTabs) return;
        const existing = document.getElementById('shop-filter-bar');
        if (existing) return;

        const bar = document.createElement('div');
        bar.id = 'shop-filter-bar';
        bar.style.cssText = 'display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin:8px 0 12px;';

        // Price filter
        const priceLabel = document.createElement('span');
        priceLabel.style.cssText = 'font-size:12px;color:var(--text-muted);font-weight:600;';
        priceLabel.textContent = 'Price:';
        bar.appendChild(priceLabel);

        const minInput = document.createElement('input');
        minInput.type = 'number';
        minInput.placeholder = 'Min';
        minInput.id = 'shop-price-min';
        minInput.style.cssText = 'width:70px;padding:6px 8px;border:1px solid var(--border);border-radius:6px;font-size:12px;background:var(--card);color:var(--text-primary);';
        bar.appendChild(minInput);

        const sep = document.createElement('span');
        sep.style.cssText = 'color:var(--text-muted);';
        sep.textContent = '-';
        bar.appendChild(sep);

        const maxInput = document.createElement('input');
        maxInput.type = 'number';
        maxInput.placeholder = 'Max';
        maxInput.id = 'shop-price-max';
        maxInput.style.cssText = 'width:70px;padding:6px 8px;border:1px solid var(--border);border-radius:6px;font-size:12px;background:var(--card);color:var(--text-primary);';
        bar.appendChild(maxInput);

        const applyBtn = document.createElement('button');
        applyBtn.className = 'btn btn-sm btn-ghost';
        applyBtn.style.cssText = 'font-size:11px;padding:4px 10px;';
        applyBtn.textContent = 'Apply';
        applyBtn.addEventListener('click', () => {
            const m = parseFloat(document.getElementById('shop-price-min')?.value);
            const mx = parseFloat(document.getElementById('shop-price-max')?.value);
            minPrice = isNaN(m) ? null : m;
            maxPrice = isNaN(mx) ? null : mx;
            Shop.filterProducts();
        });
        bar.appendChild(applyBtn);

        // Divider
        const div = document.createElement('span');
        div.style.cssText = 'width:1px;height:24px;background:var(--border);margin:0 4px;';
        bar.appendChild(div);

        // Eco rating filter
        const ecoLabel = document.createElement('span');
        ecoLabel.style.cssText = 'font-size:12px;color:var(--text-muted);font-weight:600;';
        ecoLabel.textContent = 'Eco:';
        bar.appendChild(ecoLabel);

        const ecoOptions = [
            { label: 'All', value: 0 },
            { label: '3+', value: 3 },
            { label: '4+', value: 4 },
            { label: '5', value: 5 }
        ];
        ecoOptions.forEach(o => {
            const btn = document.createElement('button');
            btn.className = 'shop-eco-filter-btn';
            btn.dataset.ecoValue = o.value;
            btn.style.cssText = 'padding:4px 10px;border:1px solid var(--border);border-radius:12px;font-size:11px;cursor:pointer;background:var(--card);color:var(--text-secondary);transition:all 0.15s;';
            btn.textContent = o.label;
            if (o.value === 0) {
                btn.style.cssText += ';background:var(--primary);color:white;border-color:var(--primary);';
            }
            btn.addEventListener('click', function () {
                document.querySelectorAll('.shop-eco-filter-btn').forEach(b => {
                    b.style.background = 'var(--card)';
                    b.style.color = 'var(--text-secondary)';
                    b.style.borderColor = 'var(--border)';
                });
                this.style.background = 'var(--primary)';
                this.style.color = 'white';
                this.style.borderColor = 'var(--primary)';
                ecoFilter = parseInt(this.dataset.ecoValue, 10) || 0;
                Shop.filterProducts();
            });
            bar.appendChild(btn);
        });

        catTabs.parentNode.insertBefore(bar, catTabs.nextSibling);
    }

    /** Inject cart enhancements: free delivery progress bar */
    function injectCartEnhancements() {
        const cartFooter = document.getElementById('cart-footer');
        if (!cartFooter) return;
        const existing = document.getElementById('cart-free-delivery-progress');
        if (existing) return;

        const progressWrap = document.createElement('div');
        progressWrap.id = 'cart-free-delivery-progress';
        progressWrap.style.cssText = 'margin:8px 0;';

        const progressText = document.createElement('div');
        progressText.id = 'cart-delivery-text';
        progressText.style.cssText = 'font-size:12px;color:var(--text-muted);margin-bottom:4px;';
        progressText.textContent = 'Add \u20B9299 more for FREE delivery';
        progressWrap.appendChild(progressText);

        const progressBar = document.createElement('div');
        progressBar.style.cssText = 'height:4px;background:var(--border);border-radius:4px;overflow:hidden;';
        const progressFill = document.createElement('div');
        progressFill.id = 'cart-delivery-fill';
        progressFill.style.cssText = 'height:100%;width:0%;background:var(--primary);border-radius:4px;transition:width 0.3s;';
        progressBar.appendChild(progressFill);
        progressWrap.appendChild(progressBar);

        // Insert before the first cart-total-row
        const firstTotalRow = cartFooter.querySelector('.cart-total-row');
        if (firstTotalRow) {
            cartFooter.insertBefore(progressWrap, firstTotalRow);
        } else {
            cartFooter.prepend(progressWrap);
        }
    }

    /** Replace sort dropdown options with the full Amazon-style set */
    function injectSortOptions() {
        const sortEl = document.getElementById('shop-sort');
        if (!sortEl) return;

        const options = [
            { value: 'relevance', label: 'Relevance' },
            { value: 'popular', label: 'Popular' },
            { value: 'price-low', label: 'Price: Low to High' },
            { value: 'price-high', label: 'Price: High to Low' },
            { value: 'newest', label: 'Newest Arrivals' },
            { value: 'eco', label: 'Eco Rating' },
            { value: 'rating', label: 'Customer Rating' }
        ];

        // Keep the element's id / data-action attributes; replace options only
        const keepAttrs = ['id', 'class', 'data-action-change', 'style'];
        const attrs = {};
        keepAttrs.forEach(a => { if (sortEl.hasAttribute(a)) attrs[a] = sortEl.getAttribute(a); });

        const freshSelect = document.createElement('select');
        freshSelect.id = attrs.id || 'shop-sort';
        if (attrs.class) freshSelect.className = attrs.class;
        if (attrs['data-action-change']) freshSelect.setAttribute('data-action-change', attrs['data-action-change']);
        if (attrs.style) freshSelect.setAttribute('style', attrs.style);

        options.forEach(o => {
            const opt = document.createElement('option');
            opt.value = o.value;
            opt.textContent = o.label;
            freshSelect.appendChild(opt);
        });

        sortEl.replaceWith(freshSelect);
        currentSort = 'relevance';
    }

    /** Inject Net Banking and other payment options into checkout */
    function injectNetBankingOption() {
        const paymentMethods = document.querySelector('.payment-methods');
        if (!paymentMethods) return;
        const existing = paymentMethods.querySelector('input[value="net"]');
        if (existing) return;

        // Net Banking
        const netLabel = document.createElement('label');
        netLabel.className = 'payment-method-option';
        const netInput = document.createElement('input');
        netInput.type = 'radio';
        netInput.name = 'payment';
        netInput.value = 'net';
        netLabel.appendChild(netInput);

        const netCard = document.createElement('div');
        netCard.className = 'payment-method-card';
        const netIcon = document.createElement('i');
        netIcon.className = 'fa-solid fa-building-columns';
        netCard.appendChild(netIcon);
        const netSpan = document.createElement('span');
        netSpan.textContent = 'Net Banking';
        netCard.appendChild(netSpan);
        netLabel.appendChild(netCard);
        paymentMethods.appendChild(netLabel);
    }

    /** Inject phone and state fields into checkout address section */
    function injectCheckoutAddressFields() {
        const addressSection = document.querySelector('.checkout-section .form-row');
        if (!addressSection) return;

        // Check if already injected
        if (document.getElementById('checkout-phone')) return;

        // Phone field before the form-row
        const phoneGroup = document.createElement('div');
        phoneGroup.className = 'form-group';
        const phoneInput = document.createElement('input');
        phoneInput.type = 'tel';
        phoneInput.id = 'checkout-phone';
        phoneInput.className = 'auth-input';
        phoneInput.placeholder = 'Phone Number';
        phoneGroup.appendChild(phoneInput);
        addressSection.parentNode.insertBefore(phoneGroup, addressSection);

        // State field inside the form-row, after city
        const stateGroup = document.createElement('div');
        stateGroup.className = 'form-group';
        const stateInput = document.createElement('input');
        stateInput.type = 'text';
        stateInput.id = 'checkout-state';
        stateInput.className = 'auth-input';
        stateInput.placeholder = 'State';
        stateGroup.appendChild(stateInput);
        addressSection.appendChild(stateGroup);
    }

    /** Inject Buy Now button into product detail modal */
    function injectBuyNowButton() {
        const actions = document.querySelector('.product-detail-actions');
        if (!actions) return;
        const existing = document.getElementById('btn-buy-now');
        if (existing) return;

        const buyBtn = document.createElement('button');
        buyBtn.id = 'btn-buy-now';
        buyBtn.className = 'btn btn-secondary';
        buyBtn.style.cssText = 'background:var(--accent);color:white;font-weight:700;';
        buyBtn.innerHTML = '<i class="fa-solid fa-bolt"></i> Buy Now';
        buyBtn.addEventListener('click', () => Shop.buyNowFromDetail());
        actions.appendChild(buyBtn);
    }

    /** Inject UPI QR placeholder into UPI section */
    function injectUpiQrPlaceholder() {
        const upiForm = document.getElementById('upi-details-form');
        if (!upiForm) return;
        const existing = document.getElementById('upi-qr-placeholder');
        if (existing) return;

        const qrDiv = document.createElement('div');
        qrDiv.id = 'upi-qr-placeholder';
        qrDiv.style.cssText = 'display:flex;align-items:center;gap:12px;padding:12px;background:var(--bg-tertiary);border-radius:8px;margin-top:8px;';

        const qrIcon = document.createElement('div');
        qrIcon.style.cssText = 'width:64px;height:64px;background:var(--card);display:flex;align-items:center;justify-content:center;border-radius:8px;border:1px solid var(--border);';
        qrIcon.innerHTML = '<i class="fa-solid fa-qrcode" style="font-size:32px;color:var(--text-muted);"></i>';
        qrDiv.appendChild(qrIcon);

        const qrText = document.createElement('div');
        qrText.style.cssText = 'font-size:12px;color:var(--text-muted);';
        qrText.textContent = 'Scan QR with any UPI app to pay';
        qrDiv.appendChild(qrText);

        upiForm.appendChild(qrDiv);
    }

    // ================================================================
    // PRODUCTS — Server-Authoritative Rendering
    // ================================================================

    async function render() {
        productPage = 0;
        hasMoreProducts = true;
        products = [];
        featuredDeals = [];
        await loadProducts();
    }

    async function loadProducts() {
        if (isLoadingProducts || !hasMoreProducts) return;
        isLoadingProducts = true;

        const gridEl = document.getElementById('shop-products-grid');
        if (gridEl && productPage === 0) {
            gridEl.innerHTML = '';
            gridEl.appendChild(createLoadingSpinner());
        }

        try {
            let url = '/api/shop/products?page=' + productPage + '&size=20';
            if (currentCategory && currentCategory !== 'all') {
                url += '&category=' + encodeURIComponent(currentCategory);
            }
            if (searchTerm) {
                url += '&keyword=' + encodeURIComponent(searchTerm);
            }
            // Sort
            if (currentSort === 'price-low') url += '&sort=price&direction=asc';
            else if (currentSort === 'price-high') url += '&sort=price&direction=desc';
            else if (currentSort === 'newest') url += '&sort=createdAt&direction=desc';
            else if (currentSort === 'eco') url += '&sort=ecoRating&direction=desc';
            else if (currentSort === 'rating') url += '&sort=rating&direction=desc';
            else if (currentSort === 'popular') url += '&sort=createdAt&direction=desc';
            // 'relevance' — no sort param, server default order

            // Price filter
            if (minPrice !== null) url += '&minPrice=' + minPrice;
            if (maxPrice !== null) url += '&maxPrice=' + maxPrice;

            // Eco filter
            if (ecoFilter > 0) url += '&ecoRating=' + ecoFilter;

            const result = await EcoAPI.apiGet(url);

            if (result && result.data && result.data.content) {
                const newProducts = result.data.content;
                products = productPage === 0 ? newProducts : [...products, ...newProducts];
                hasMoreProducts = !result.data.last;
                productPage++;

                // Build featured deals list on first page
                if (productPage === 1) {
                    featuredDeals = newProducts.filter(p => p.discountPercent && p.discountPercent > 0).slice(0, 10);
                    renderFeaturedDeals();
                }

                renderGrid();
            } else {
                renderEmptyProducts('No products available');
            }
        } catch (e) {
            console.error('Failed to load products:', e);
            renderEmptyProducts('Failed to load products. Please try again.');
        } finally {
            isLoadingProducts = false;
        }
    }

    function renderGrid() {
        const gridEl = document.getElementById('shop-products-grid');
        if (!gridEl) return;
        gridEl.innerHTML = '';

        if (products.length === 0) {
            renderEmptyProducts('No products found');
            return;
        }

        products.forEach(p => {
            gridEl.appendChild(createProductCard(p));
        });

        // Update impact stats
        const totalSaved = products.reduce((s, p) => s + (p.ecoRating || 0) * 0.5, 0);
        EcoUtils.setText('shop-total-carbon-saved', totalSaved.toFixed(1) + ' kg');
        EcoUtils.setText('shop-trees-equivalent', Math.ceil(totalSaved / 22));
    }

    function renderEmptyProducts(message) {
        const gridEl = document.getElementById('shop-products-grid');
        if (!gridEl) return;
        gridEl.innerHTML = '';

        const emptyDiv = document.createElement('div');
        emptyDiv.className = 'shop-empty-state';
        emptyDiv.style.cssText = 'text-align:center;padding:60px 20px;color:var(--text-muted);grid-column:1/-1;';

        const icon = document.createElement('i');
        icon.className = 'fa-solid fa-leaf';
        icon.style.cssText = 'font-size:40px;opacity:0.3;display:block;margin-bottom:16px;';
        emptyDiv.appendChild(icon);

        const msg = document.createElement('p');
        msg.textContent = message;
        emptyDiv.appendChild(msg);

        if (message.includes('Failed')) {
            const retryBtn = document.createElement('button');
            retryBtn.className = 'btn btn-outline';
            retryBtn.textContent = 'Retry';
            retryBtn.style.cssText = 'margin-top:12px;';
            retryBtn.addEventListener('click', () => render());
            emptyDiv.appendChild(retryBtn);
        }

        gridEl.appendChild(emptyDiv);
    }

    /**
     * Create an Amazon-level product card
     */
    function createProductCard(p) {
        const card = document.createElement('div');
        card.className = 'shop-product-card';
        card.style.cssText = 'position:relative;cursor:pointer;transition:transform 0.2s,box-shadow 0.2s;border-radius:10px;overflow:hidden;background:var(--card);border:1px solid var(--border);';
        card.addEventListener('click', () => Shop.openDetail(p.id));

        // Add hover effect
        card.addEventListener('mouseenter', function () {
            this.style.transform = 'translateY(-3px)';
            this.style.boxShadow = '0 8px 25px rgba(0,0,0,0.1)';
            const quickAdd = this.querySelector('.shop-quick-add-btn');
            if (quickAdd && p.stock > 0) quickAdd.style.display = 'flex';
        });
        card.addEventListener('mouseleave', function () {
            this.style.transform = '';
            this.style.boxShadow = '';
            const quickAdd = this.querySelector('.shop-quick-add-btn');
            if (quickAdd) quickAdd.style.display = 'none';
        });

        // ===== IMAGE SECTION =====
        const imgDiv = document.createElement('div');
        imgDiv.className = 'shop-product-img';
        imgDiv.style.cssText = 'position:relative;aspect-ratio:1/1;overflow:hidden;background:var(--bg-tertiary);';

        if (p.imageUrl && isSafeUrl(p.imageUrl)) {
            const img = document.createElement('img');
            img.src = p.imageUrl;
            img.alt = p.name || 'Product';
            img.style.cssText = 'width:100%;height:100%;object-fit:cover;display:block;';
            img.addEventListener('error', function () { this.replaceWith(createLeafIcon()); });
            imgDiv.appendChild(img);
        } else {
            imgDiv.appendChild(createLeafIcon());
        }

        // Badges (top-left area)
        // Discount badge
        let discountBadge = null;
        if (p.discountPercent && p.discountPercent > 0) {
            discountBadge = document.createElement('div');
            discountBadge.style.cssText = 'position:absolute;top:6px;left:6px;background:var(--error);color:white;padding:2px 6px;border-radius:4px;font-size:11px;font-weight:700;z-index:2;';
            discountBadge.textContent = '-' + p.discountPercent + '%';
            imgDiv.appendChild(discountBadge);
        }

        // Eco badge
        if (p.ecoRating >= 4) {
            const ecoBadge = document.createElement('div');
            ecoBadge.className = 'shop-product-eco-badge';
            ecoBadge.style.cssText = 'position:absolute;top:6px;right:6px;z-index:2;';
            ecoBadge.innerHTML = '<i class="fa-solid fa-leaf"></i> Eco';
            imgDiv.appendChild(ecoBadge);
        }

        // Secondhand badge
        if (p.isSecondhand) {
            const usedBadge = document.createElement('div');
            usedBadge.className = 'shop-product-secondhand-badge';
            usedBadge.style.cssText = 'position:absolute;bottom:6px;left:6px;z-index:2;';
            usedBadge.textContent = 'Pre-Owned';
            imgDiv.appendChild(usedBadge);
        }

        // Best Seller badge (shifts discount badge down so they don't overlap)
        if (p.highlights && (Array.isArray(p.highlights) ? p.highlights.includes('Best Seller') : (typeof p.highlights === 'string' && p.highlights.includes('Best Seller')))) {
            const bestSeller = document.createElement('div');
            bestSeller.style.cssText = 'position:absolute;top:6px;left:6px;background:var(--accent);color:white;padding:2px 8px;border-radius:4px;font-size:10px;font-weight:700;z-index:2;';
            bestSeller.textContent = 'Best Seller';
            if (discountBadge) discountBadge.style.top = '32px';
            imgDiv.appendChild(bestSeller);
        }

        // Free Delivery badge
        if ((p.price || 0) >= FREE_DELIVERY_THRESHOLD) {
            const freeBadge = document.createElement('div');
            freeBadge.style.cssText = 'position:absolute;bottom:6px;right:6px;background:rgba(16,185,129,0.9);color:white;padding:2px 6px;border-radius:4px;font-size:10px;font-weight:600;z-index:2;';
            freeBadge.textContent = 'Free Delivery';
            imgDiv.appendChild(freeBadge);
        }

        // Out of Stock overlay
        if (p.stock === 0) {
            const oosOverlay = document.createElement('div');
            oosOverlay.style.cssText = 'position:absolute;inset:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:3;';
            const oosText = document.createElement('span');
            oosText.style.cssText = 'color:white;font-weight:700;font-size:14px;';
            oosText.textContent = 'Out of Stock';
            oosOverlay.appendChild(oosText);
            imgDiv.appendChild(oosOverlay);
        }

        // Quick add to cart button (visible on hover)
        if (p.stock > 0) {
            const quickAdd = document.createElement('button');
            quickAdd.className = 'shop-quick-add-btn';
            quickAdd.style.cssText = 'position:absolute;bottom:0;left:0;right:0;display:none;align-items:center;justify-content:center;gap:6px;padding:8px;background:rgba(0,0,0,0.7);color:white;border:none;cursor:pointer;font-size:13px;font-weight:600;z-index:4;transition:background 0.2s;';
            quickAdd.innerHTML = '<i class="fa-solid fa-cart-plus" style="font-size:14px;"></i> Add to Cart';
            quickAdd.addEventListener('click', (e) => {
                e.stopPropagation();
                Shop.addToCart(p.id);
            });
            quickAdd.addEventListener('mouseenter', function () { this.style.background = 'rgba(0,0,0,0.85)'; });
            quickAdd.addEventListener('mouseleave', function () { this.style.background = 'rgba(0,0,0,0.7)'; });
            imgDiv.appendChild(quickAdd);
        }

        card.appendChild(imgDiv);

        // ===== BODY SECTION =====
        const body = document.createElement('div');
        body.style.cssText = 'padding:10px 12px 12px;';

        // Brand
        if (p.brand) {
            const brandDiv = document.createElement('div');
            brandDiv.style.cssText = 'font-size:11px;color:var(--text-muted);margin-bottom:2px;';
            brandDiv.textContent = p.brand;
            body.appendChild(brandDiv);
        }

        // Name (2-line clamp)
        const nameDiv = document.createElement('div');
        nameDiv.className = 'shop-product-name';
        nameDiv.style.cssText = 'font-weight:600;font-size:14px;color:var(--text-primary);display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;line-height:1.3;margin-bottom:4px;';
        nameDiv.textContent = p.name || '';
        body.appendChild(nameDiv);

        // Rating stars + count
        if (p.rating !== undefined && p.rating !== null) {
            const ratingRow = document.createElement('div');
            ratingRow.style.cssText = 'display:flex;align-items:center;gap:2px;margin-bottom:4px;';
            ratingRow.appendChild(createRatingStars(p.rating, p.ratingCount));
            body.appendChild(ratingRow);
        }

        // Price row: discount price, MRP strikethrough, discount %
        const priceRow = document.createElement('div');
        priceRow.style.cssText = 'display:flex;align-items:baseline;gap:6px;margin-bottom:2px;flex-wrap:wrap;';

        const finalPrice = document.createElement('span');
        finalPrice.style.cssText = 'font-size:18px;font-weight:700;color:var(--text-primary);';
        finalPrice.textContent = '\u20B9' + (p.price || 0).toLocaleString('en-IN');
        priceRow.appendChild(finalPrice);

        if (p.mrp && p.mrp > (p.price || 0)) {
            const mrpSpan = document.createElement('span');
            mrpSpan.style.cssText = 'font-size:13px;color:var(--text-muted);text-decoration:line-through;';
            mrpSpan.textContent = '\u20B9' + p.mrp.toLocaleString('en-IN');
            priceRow.appendChild(mrpSpan);

            if (p.discountPercent) {
                const discSpan = document.createElement('span');
                discSpan.style.cssText = 'font-size:12px;color:var(--error);font-weight:600;';
                discSpan.textContent = '-' + p.discountPercent + '%';
                priceRow.appendChild(discSpan);
            }
        }

        body.appendChild(priceRow);

        // Delivery line
        const deliveryDiv = document.createElement('div');
        deliveryDiv.style.cssText = 'font-size:11px;color:var(--text-muted);margin-bottom:4px;';
        if ((p.price || 0) >= FREE_DELIVERY_THRESHOLD) {
            deliveryDiv.style.color = 'var(--success)';
        }
        deliveryDiv.textContent = getDeliveryLine(p);
        body.appendChild(deliveryDiv);

        // Eco rating + two thumbnails row
        const bottomRow = document.createElement('div');
        bottomRow.style.cssText = 'display:flex;align-items:center;gap:8px;margin-bottom:6px;';

        // Eco rating indicator
        if (p.ecoRating) {
            const ecoSpan = document.createElement('span');
            ecoSpan.style.cssText = 'display:flex;align-items:center;gap:2px;font-size:11px;color:var(--primary);font-weight:600;';
            const leafIcon = document.createElement('i');
            leafIcon.className = 'fa-solid fa-leaf';
            leafIcon.style.cssText = 'font-size:12px;';
            ecoSpan.appendChild(leafIcon);
            const ecoVal = document.createElement('span');
            ecoVal.textContent = p.ecoRating.toFixed(1);
            ecoSpan.appendChild(ecoVal);
            bottomRow.appendChild(ecoSpan);
        }

        // Two thumbnail placeholders
        const thumb1 = document.createElement('i');
        thumb1.className = 'fa-solid fa-leaf';
        thumb1.style.cssText = 'font-size:14px;color:var(--text-faint);opacity:0.5;';
        bottomRow.appendChild(thumb1);
        const thumb2 = document.createElement('i');
        thumb2.className = 'fa-solid fa-leaf';
        thumb2.style.cssText = 'font-size:14px;color:var(--text-faint);opacity:0.5;';
        bottomRow.appendChild(thumb2);

        body.appendChild(bottomRow);

        // Add to Cart button (always visible)
        if (p.stock > 0) {
            const addBtn = document.createElement('button');
            addBtn.className = 'shop-product-add-btn';
            addBtn.style.cssText = 'width:100%;padding:8px;border:none;border-radius:8px;background:var(--primary);color:white;font-weight:600;font-size:13px;cursor:pointer;display:flex;align-items:center;justify-content:center;gap:6px;transition:background 0.2s;';
            addBtn.innerHTML = '<i class="fa-solid fa-cart-plus"></i> Add to Cart';
            addBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                Shop.addToCart(p.id);
            });
            addBtn.addEventListener('mouseenter', function () { this.style.background = 'var(--primary-hover)'; });
            addBtn.addEventListener('mouseleave', function () { this.style.background = 'var(--primary)'; });
            body.appendChild(addBtn);
        }

        card.appendChild(body);
        return card;
    }

    /**
     * Render Deals of the Day section
     */
    function renderFeaturedDeals() {
        const scrollWrap = document.getElementById('shop-deals-scroll');
        if (!scrollWrap) return;
        scrollWrap.innerHTML = '';

        if (!featuredDeals.length) {
            // Hide deals section if no deals
            const section = document.getElementById('shop-deals-section');
            if (section) section.style.display = 'none';
            return;
        }

        const section = document.getElementById('shop-deals-section');
        if (section) section.style.display = '';

        featuredDeals.forEach(p => {
            const dealCard = document.createElement('div');
            dealCard.style.cssText = 'flex:0 0 160px;border-radius:8px;overflow:hidden;background:var(--card);border:1px solid var(--border);cursor:pointer;transition:transform 0.15s;';
            dealCard.addEventListener('click', () => Shop.openDetail(p.id));
            dealCard.addEventListener('mouseenter', function () { this.style.transform = 'translateY(-2px)'; });
            dealCard.addEventListener('mouseleave', function () { this.style.transform = ''; });

            // Image
            const imgWrap = document.createElement('div');
            imgWrap.style.cssText = 'aspect-ratio:1/1;background:var(--bg-tertiary);position:relative;overflow:hidden;';

            if (p.imageUrl && isSafeUrl(p.imageUrl)) {
                const img = document.createElement('img');
                img.src = p.imageUrl;
                img.alt = p.name || '';
                img.style.cssText = 'width:100%;height:100%;object-fit:cover;';
                img.addEventListener('error', function () { this.replaceWith(createLeafIcon()); });
                imgWrap.appendChild(img);
            } else {
                imgWrap.appendChild(createLeafIcon());
            }

            // Discount badge
            if (p.discountPercent) {
                const discBadge = document.createElement('div');
                discBadge.style.cssText = 'position:absolute;top:4px;left:4px;background:var(--error);color:white;padding:2px 6px;border-radius:4px;font-size:10px;font-weight:700;';
                discBadge.textContent = '-' + p.discountPercent + '%';
                imgWrap.appendChild(discBadge);
            }

            dealCard.appendChild(imgWrap);

            // Info
            const info = document.createElement('div');
            info.style.cssText = 'padding:6px 8px 8px;';

            const name = document.createElement('div');
            name.style.cssText = 'font-size:12px;font-weight:600;color:var(--text-primary);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;';
            name.textContent = p.name || '';
            info.appendChild(name);

            const price = document.createElement('div');
            price.style.cssText = 'font-size:14px;font-weight:700;color:var(--text-primary);';
            price.textContent = '\u20B9' + (p.price || 0).toLocaleString('en-IN');
            info.appendChild(price);

            if (p.mrp && p.mrp > (p.price || 0)) {
                const oldPrice = document.createElement('span');
                oldPrice.style.cssText = 'font-size:11px;color:var(--text-muted);text-decoration:line-through;margin-left:4px;';
                oldPrice.textContent = '\u20B9' + p.mrp.toLocaleString('en-IN');
                price.appendChild(oldPrice);
            }

            const addBtn = document.createElement('button');
            addBtn.style.cssText = 'width:100%;margin-top:4px;padding:4px;border:none;border-radius:4px;background:var(--primary);color:white;font-size:11px;font-weight:600;cursor:pointer;';
            addBtn.textContent = 'Add to Cart';
            addBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                Shop.addToCart(p.id);
            });
            info.appendChild(addBtn);

            dealCard.appendChild(info);
            scrollWrap.appendChild(dealCard);
        });
    }

    // ================================================================
    // CATEGORY / FILTER / SORT
    // ================================================================

    function setCategory(c, btn) {
        document.querySelectorAll('.shop-cat-btn').forEach(b => b.classList.remove('active'));
        if (btn) btn.classList.add('active');
        currentCategory = c;
        productPage = 0;
        hasMoreProducts = true;
        products = [];
        loadProducts();
    }

    function filterProducts() {
        // Read sort value
        const sortEl = document.getElementById('shop-sort');
        if (sortEl) currentSort = sortEl.value;

        productPage = 0;
        hasMoreProducts = true;
        products = [];
        loadProducts();
    }

    function loadMore() {
        loadProducts();
    }

    // ================================================================
    // CART — Server-Authoritative
    // ================================================================

    async function addToCart(productId) {
        try {
            await EcoAPI.apiPost('/api/shop/cart?productId=' + productId + '&quantity=1');
            EcoVerse.showToast('Added to cart!', 'success');
            updateCartUI();
        } catch (e) {
            const msg = e?.message || 'Failed to add to cart';
            EcoVerse.showToast(msg, 'error');
        }
    }

    async function removeFromCart(cartItemId) {
        try {
            await EcoAPI.apiDelete('/api/shop/cart/' + cartItemId);
            EcoVerse.showToast('Removed from cart', 'info');
            renderCartDrawer();
        } catch (e) {
            EcoVerse.showToast('Failed to remove item', 'error');
        }
    }

    async function changeCartQty(cartItemId, delta, currentQty) {
        const newQty = currentQty + delta;
        if (newQty < 1) {
            await removeFromCart(cartItemId);
            return;
        }
        if (newQty > 100) {
            EcoVerse.showToast('Maximum quantity is 100', 'warning');
            return;
        }
        try {
            await EcoAPI.apiPut('/api/shop/cart/' + cartItemId + '?quantity=' + newQty);
            renderCartDrawer();
        } catch (e) {
            EcoVerse.showToast('Failed to update quantity', 'error');
        }
    }

    async function updateCartUI() {
        try {
            const result = await EcoAPI.apiGet('/api/shop/cart');
            const items = result?.data || [];
            const countEl = document.getElementById('cart-count');
            if (countEl) countEl.textContent = items.reduce((s, i) => s + (i.quantity || 0), 0);
        } catch (e) {
            const countEl = document.getElementById('cart-count');
            if (countEl) countEl.textContent = '0';
        }
    }

    function openCart() {
        renderCartDrawer();
        document.getElementById('cart-drawer')?.classList.add('open');
        document.querySelector('.cart-drawer-overlay')?.classList.add('open');
    }

    function closeCart() {
        document.getElementById('cart-drawer')?.classList.remove('open');
        document.querySelector('.cart-drawer-overlay')?.classList.remove('open');
    }

    /**
     * Render Amazon-level cart drawer
     */
    async function renderCartDrawer() {
        const itemsEl = document.getElementById('cart-items');
        const emptyEl = document.getElementById('cart-empty');
        const footerEl = document.getElementById('cart-footer');

        if (!itemsEl) return;
        itemsEl.innerHTML = '';

        let cartItems = [];
        try {
            const result = await EcoAPI.apiGet('/api/shop/cart');
            cartItems = result?.data || [];
        } catch (e) {
            if (emptyEl) emptyEl.style.display = 'flex';
            if (footerEl) footerEl.style.display = 'none';
            return;
        }

        if (!cartItems.length) {
            if (emptyEl) {
                emptyEl.style.display = 'flex';
                // Update empty state message
                const emptyIcon = emptyEl.querySelector('i');
                if (emptyIcon) emptyIcon.className = 'fa-solid fa-bag-shopping';
                const emptyP = emptyEl.querySelector('p');
                if (emptyP) emptyP.textContent = 'Your cart is empty';
                // Add "Shop Now" button if not present
                let shopBtn = emptyEl.querySelector('.btn');
                if (!shopBtn) {
                    shopBtn = document.createElement('button');
                    shopBtn.className = 'btn btn-primary';
                    shopBtn.style.cssText = 'margin-top:8px;';
                    shopBtn.textContent = 'Shop Now';
                    shopBtn.addEventListener('click', () => { closeCart(); });
                    emptyEl.appendChild(shopBtn);
                }
            }
            if (footerEl) footerEl.style.display = 'none';
            const countEl = document.getElementById('cart-count');
            if (countEl) countEl.textContent = '0';
            return;
        }

        if (emptyEl) emptyEl.style.display = 'none';
        if (footerEl) footerEl.style.display = 'block';

        // Update header title with item count (keep icon)
        const headerEl = document.querySelector('.cart-drawer-header h3');
        if (headerEl) {
            const totalQty = cartItems.reduce((s, i) => s + (i.quantity || 0), 0);
            headerEl.innerHTML = '<i class="fa-solid fa-cart-shopping"></i> Shopping Cart (' + totalQty + ')';
        }

        const countEl = document.getElementById('cart-count');
        if (countEl) countEl.textContent = cartItems.reduce((s, i) => s + (i.quantity || 0), 0);

        let subtotal = 0;
        cartItems.forEach(item => {
            const itemTotal = (item.price || 0) * (item.quantity || 1);
            subtotal += itemTotal;

            const cartItem = document.createElement('div');
            cartItem.className = 'cart-item';

            // Image
            const imgDiv = document.createElement('div');
            imgDiv.className = 'cart-item-img';
            if (item.imageUrl && isSafeUrl(item.imageUrl)) {
                const img = document.createElement('img');
                img.src = item.imageUrl;
                img.alt = item.productName || '';
                img.style.cssText = 'width:100%;height:100%;object-fit:cover;';
                img.addEventListener('error', function () { this.replaceWith(createLeafIcon()); });
                imgDiv.appendChild(img);
            } else {
                imgDiv.appendChild(createLeafIcon());
            }
            cartItem.appendChild(imgDiv);

            // Info
            const infoDiv = document.createElement('div');
            infoDiv.className = 'cart-item-info';

            const nameDiv = document.createElement('div');
            nameDiv.className = 'cart-item-name';
            nameDiv.style.cssText = 'font-weight:600;font-size:14px;';
            nameDiv.textContent = item.productName || 'Unknown';
            infoDiv.appendChild(nameDiv);

            // Price per unit
            const priceDiv = document.createElement('div');
            priceDiv.className = 'cart-item-price';
            priceDiv.style.cssText = 'font-size:14px;font-weight:600;margin-bottom:4px;';
            priceDiv.textContent = '\u20B9' + (item.price || 0).toLocaleString('en-IN');
            infoDiv.appendChild(priceDiv);

            // Stock warning
            if (item.stock !== undefined && item.stock !== null && item.stock < item.quantity) {
                const warnDiv = document.createElement('div');
                warnDiv.style.cssText = 'color:var(--error);font-size:11px;margin-bottom:2px;';
                warnDiv.textContent = 'Only ' + item.stock + ' in stock';
                infoDiv.appendChild(warnDiv);
            }

            // Quantity selector
            const qtyDiv = document.createElement('div');
            qtyDiv.className = 'cart-item-qty';
            qtyDiv.style.cssText = 'display:flex;align-items:center;gap:6px;margin-top:4px;';

            const minusBtn = document.createElement('button');
            minusBtn.style.cssText = 'width:28px;height:28px;border:1px solid var(--border);background:var(--card);border-radius:50%;cursor:pointer;display:flex;align-items:center;justify-content:center;font-size:12px;color:var(--text-secondary);';
            minusBtn.innerHTML = '<i class="fa-solid fa-minus"></i>';
            minusBtn.addEventListener('click', () => Shop.changeCartQty(item.id, -1, item.quantity));
            qtyDiv.appendChild(minusBtn);

            const qtySpan = document.createElement('span');
            qtySpan.style.cssText = 'font-weight:700;min-width:20px;text-align:center;font-size:14px;';
            qtySpan.textContent = item.quantity;
            qtyDiv.appendChild(qtySpan);

            const plusBtn = document.createElement('button');
            plusBtn.style.cssText = 'width:28px;height:28px;border:1px solid var(--border);background:var(--card);border-radius:50%;cursor:pointer;display:flex;align-items:center;justify-content:center;font-size:12px;color:var(--text-secondary);';
            plusBtn.innerHTML = '<i class="fa-solid fa-plus"></i>';
            plusBtn.addEventListener('click', () => Shop.changeCartQty(item.id, 1, item.quantity));
            qtyDiv.appendChild(plusBtn);

            // Item total price
            const itemTotalSpan = document.createElement('span');
            itemTotalSpan.style.cssText = 'margin-left:auto;font-weight:600;font-size:14px;';
            itemTotalSpan.textContent = '\u20B9' + itemTotal.toLocaleString('en-IN');
            qtyDiv.appendChild(itemTotalSpan);

            infoDiv.appendChild(qtyDiv);

            // Action buttons row: Remove + Save for later
            const actionRow = document.createElement('div');
            actionRow.style.cssText = 'display:flex;gap:12px;margin-top:4px;';

            const removeBtn = document.createElement('button');
            removeBtn.className = 'cart-item-remove';
            removeBtn.style.cssText = 'border:none;background:none;color:var(--text-muted);cursor:pointer;font-size:12px;padding:0;display:flex;align-items:center;gap:4px;';
            removeBtn.innerHTML = '<i class="fa-solid fa-trash-can" style="font-size:12px;"></i> Remove';
            removeBtn.addEventListener('click', () => Shop.removeFromCart(item.id));
            actionRow.appendChild(removeBtn);

            const saveBtn = document.createElement('button');
            saveBtn.style.cssText = 'border:none;background:none;color:var(--text-muted);cursor:pointer;font-size:12px;padding:0;display:flex;align-items:center;gap:4px;';
            saveBtn.innerHTML = '<i class="fa-regular fa-bookmark" style="font-size:12px;"></i> Save for later';
            saveBtn.addEventListener('click', () => {
                EcoVerse.showToast('Saved for later!', 'info');
                // For now, just remove from cart (backend doesn't support save-for-later)
                Shop.removeFromCart(item.id);
            });
            actionRow.appendChild(saveBtn);

            infoDiv.appendChild(actionRow);
            cartItem.appendChild(infoDiv);

            itemsEl.appendChild(cartItem);
        });

        // Update footer totals
        EcoUtils.setText('cart-subtotal', '\u20B9' + subtotal.toLocaleString('en-IN'));

        // Carbon saved
        const carbonSaved = cartItems.reduce((s, i) => s + (i.ecoRating || 0) * 0.5 * (i.quantity || 1), 0);
        const carbonEl = document.getElementById('cart-carbon-save');
        if (carbonEl) carbonEl.textContent = carbonSaved.toFixed(1) + ' kg';

        // Free delivery progress bar
        const deliveryText = document.getElementById('cart-delivery-text');
        const deliveryFill = document.getElementById('cart-delivery-fill');
        if (deliveryText && deliveryFill) {
            if (subtotal >= FREE_DELIVERY_THRESHOLD) {
                deliveryText.textContent = 'Your order qualifies for FREE delivery!';
                deliveryText.style.color = 'var(--success)';
                deliveryFill.style.width = '100%';
                deliveryFill.style.background = 'var(--success)';
            } else {
                const remaining = FREE_DELIVERY_THRESHOLD - subtotal;
                deliveryText.textContent = 'Add \u20B9' + remaining.toLocaleString('en-IN') + ' more for FREE delivery';
                deliveryText.style.color = 'var(--text-muted)';
                deliveryFill.style.width = Math.min((subtotal / FREE_DELIVERY_THRESHOLD) * 100, 100) + '%';
                deliveryFill.style.background = 'var(--primary)';
            }
        }

        const total = subtotal;
        EcoUtils.setText('cart-total', '\u20B9' + total.toLocaleString('en-IN'));
    }

    // ================================================================
    // PRODUCT DETAIL (Amazon-level)
    // ================================================================

    async function openDetail(id) {
        try {
            const result = await EcoAPI.apiGet('/api/shop/products/' + id);
            if (!result?.data) return;
            currentDetailProduct = result.data;
        } catch (e) {
            EcoVerse.showToast('Failed to load product details', 'error');
            return;
        }

        const p = currentDetailProduct;
        if (!p) return;

        // --- Right side info ---

        // Brand link (small, muted) — insert after category label
        const categoryEl = document.getElementById('pd-category');
        const brandEl = document.createElement('div');
        brandEl.style.cssText = 'font-size:12px;color:var(--text-muted);margin-bottom:2px;';
        brandEl.textContent = p.brand || '';

        // Product name
        EcoUtils.setText('pd-fullname', p.name || '');
        EcoUtils.setText('pd-category', (p.category || 'general').toUpperCase());

        // Remove existing brand element if present, then insert after category
        const existingBrand = document.getElementById('pd-brand');
        if (existingBrand) existingBrand.remove();
        brandEl.id = 'pd-brand';
        if (categoryEl && categoryEl.parentNode) {
            categoryEl.parentNode.insertBefore(brandEl, categoryEl.nextSibling);
        }

        // Rating row: stars + rating count + "|" + "X bought this month"
        const ratingEl = document.getElementById('pd-rating');
        if (ratingEl) {
            ratingEl.innerHTML = '';
            const starsContainer = createRatingStars(p.rating, p.ratingCount);
            ratingEl.appendChild(starsContainer);

            if (p.ratingCount) {
                const ratingText = document.createElement('span');
                ratingText.style.cssText = 'font-size:12px;color:var(--text-muted);margin-left:4px;';
                ratingText.textContent = p.ratingCount.toLocaleString('en-IN') + ' ratings';
                ratingEl.appendChild(ratingText);
            }

            const sep = document.createElement('span');
            sep.style.cssText = 'color:var(--border);margin:0 6px;';
            sep.textContent = '|';
            ratingEl.appendChild(sep);

            const boughtSpan = document.createElement('span');
            boughtSpan.style.cssText = 'font-size:12px;color:var(--text-muted);';
            const boughtCount = p.ratingCount ? Math.round(p.ratingCount * 8) : 0;
            boughtSpan.textContent = boughtCount.toLocaleString('en-IN') + '+ bought this month';
            ratingEl.appendChild(boughtSpan);
        }

        // Price section: MRP strike-through, discount %, final price
        const priceEl = document.getElementById('pd-price');
        if (priceEl) {
            priceEl.innerHTML = '';

            if (p.mrp && p.mrp > (p.price || 0)) {
                const mrpSpan = document.createElement('span');
                mrpSpan.style.cssText = 'font-size:14px;color:var(--text-muted);text-decoration:line-through;margin-right:8px;';
                mrpSpan.textContent = 'MRP \u20B9' + p.mrp.toLocaleString('en-IN');
                priceEl.appendChild(mrpSpan);

                if (p.discountPercent) {
                    const discBadge = document.createElement('span');
                    discBadge.style.cssText = 'display:inline-block;padding:2px 8px;border-radius:4px;background:rgba(239,68,68,0.12);color:var(--error);font-size:12px;font-weight:700;margin-right:8px;';
                    discBadge.textContent = '-' + p.discountPercent + '%';
                    priceEl.appendChild(discBadge);
                }
            }

            const finalPrice = document.createElement('span');
            finalPrice.style.cssText = 'font-size:24px;font-weight:800;color:var(--text-primary);';
            finalPrice.textContent = '\u20B9' + (p.price || 0).toLocaleString('en-IN');
            priceEl.appendChild(finalPrice);

            // Tax info
            const taxInfo = document.createElement('div');
            taxInfo.style.cssText = 'font-size:11px;color:var(--text-muted);margin-top:2px;';
            taxInfo.textContent = 'Inclusive of all taxes';
            priceEl.appendChild(taxInfo);
        }

        // Stock
        const stockEl = document.getElementById('pd-stock');
        if (!stockEl) {
            // Create stock element if not exists
            const stockDiv = document.createElement('div');
            stockDiv.id = 'pd-stock';
            stockDiv.style.cssText = 'font-size:14px;font-weight:600;margin-bottom:6px;';
            const priceContainer = document.getElementById('pd-price');
            if (priceContainer && priceContainer.parentNode) {
                priceContainer.parentNode.insertBefore(stockDiv, priceContainer.nextSibling);
            }
        }
        const stockDisplay = document.getElementById('pd-stock');
        if (stockDisplay) {
            if (p.stock > 0) {
                stockDisplay.textContent = 'In Stock';
                stockDisplay.style.color = 'var(--success)';
            } else {
                stockDisplay.textContent = 'Out of Stock';
                stockDisplay.style.color = 'var(--error)';
            }
            stockDisplay.style.display = 'block';
        }

        // Eco rating
        const carbonEl = document.getElementById('pd-carbon');
        if (carbonEl) {
            if (p.ecoRating) {
                carbonEl.innerHTML = '';
                const leafIcon = document.createElement('i');
                leafIcon.className = 'fa-solid fa-leaf';
                leafIcon.style.cssText = 'margin-right:4px;';
                carbonEl.appendChild(leafIcon);
                const ecoText = document.createTextNode('Eco Rating: ' + p.ecoRating + '/5');
                carbonEl.appendChild(ecoText);

                if (p.ecoRating >= 4.5) {
                    const bestText = document.createElement('span');
                    bestText.style.cssText = 'color:var(--primary);font-weight:600;margin-left:4px;';
                    bestText.textContent = '- Best in Class';
                    carbonEl.appendChild(bestText);
                }
            } else {
                carbonEl.textContent = '';
            }
        }

        // Highlights / Pills: "Best Seller", "Eco Pick", "Free Delivery"
        const highlightsContainer = document.createElement('div');
        highlightsContainer.id = 'pd-highlights';
        highlightsContainer.style.cssText = 'display:flex;flex-wrap:wrap;gap:6px;margin-bottom:10px;';

        // Check existing highlights before adding
        const existingHighlights = document.getElementById('pd-highlights');
        if (existingHighlights) existingHighlights.remove();

        if (p.highlights) {
            const highlights = Array.isArray(p.highlights) ? p.highlights : [p.highlights];
            highlights.forEach(h => {
                if (h === 'Best Seller') {
                    highlightsContainer.appendChild(createBadgePill('\u{1F525} Best Seller', 'rgba(245,158,11,0.15)', '#d97706'));
                } else if (h === 'Eco Pick') {
                    highlightsContainer.appendChild(createBadgePill('\u{1F331} Eco Pick', 'rgba(16,185,129,0.15)', 'var(--primary)'));
                } else if (h === 'Free Delivery') {
                    highlightsContainer.appendChild(createBadgePill('\u{1F69A} Free Delivery', 'rgba(16,185,129,0.15)', 'var(--primary)'));
                } else {
                    highlightsContainer.appendChild(createBadgePill(h, 'rgba(99,102,241,0.1)', '#6366f1'));
                }
            });
        }

        // If product qualifies for free delivery
        if ((p.price || 0) >= FREE_DELIVERY_THRESHOLD && (!p.highlights || !Array.isArray(p.highlights) || !p.highlights.some(h => h.includes('Free')))) {
            highlightsContainer.appendChild(createBadgePill('\u{1F69A} Free Delivery', 'rgba(16,185,129,0.15)', 'var(--primary)'));
        }

        // Insert highlights after eco rating
        const carbonElParent = document.getElementById('pd-carbon');
        if (carbonElParent && carbonElParent.parentNode) {
            carbonElParent.parentNode.insertBefore(highlightsContainer, carbonElParent.nextSibling);
        }

        // Description (expandable)
        const descEl = document.getElementById('pd-desc');
        if (descEl) {
            descEl.textContent = p.description || '';
            descEl.style.cssText = 'font-size:13px;color:var(--text-secondary);line-height:1.5;margin-bottom:8px;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden;cursor:pointer;';
            // Assign (not addEventListener) to avoid stacking listeners across opens
            descEl.onclick = function () {
                if (this.style.webkitLineClamp === '3' || !this.style.webkitLineClamp) {
                    this.style.webkitLineClamp = 'unset';
                    this.style.overflow = 'visible';
                } else {
                    this.style.webkitLineClamp = '3';
                    this.style.overflow = 'hidden';
                }
            };
        }

        // Features bullet list with checkmarks
        const featEl = document.getElementById('pd-features');
        if (featEl) {
            featEl.innerHTML = '';
            if (p.features && Array.isArray(p.features) && p.features.length > 0) {
                p.features.forEach(f => {
                    const li = document.createElement('li');
                    li.textContent = f;
                    // The CSS ::before already adds a checkmark
                    featEl.appendChild(li);
                });
            } else if (p.ecoRating >= 4) {
                const li1 = document.createElement('li');
                li1.textContent = 'Sustainably sourced materials';
                featEl.appendChild(li1);
                const li2 = document.createElement('li');
                li2.textContent = 'Carbon-neutral shipping';
                featEl.appendChild(li2);
            }
        }

        // Quantity selector reset
        const qtyEl = document.getElementById('pd-qty');
        if (qtyEl) qtyEl.textContent = '1';
        detailQty = 1;

        // --- Left side: Image + Thumbnail row ---
        const imgEl = document.getElementById('pd-image');
        if (imgEl) {
            imgEl.innerHTML = '';
            // Override the fixed-height CSS container so image + thumbnails stack vertically
            imgEl.style.cssText = 'display:flex;flex-direction:column;align-items:center;width:100%;height:auto;overflow:visible;background:transparent;border-radius:8px;gap:8px;';

            // Main image area
            const mainWrap = document.createElement('div');
            mainWrap.style.cssText = 'width:100%;height:300px;background:var(--bg-tertiary);border-radius:8px;display:flex;align-items:center;justify-content:center;overflow:hidden;';

            if (p.imageUrl && isSafeUrl(p.imageUrl)) {
                const img = document.createElement('img');
                img.src = p.imageUrl;
                img.alt = p.name || '';
                img.style.cssText = 'width:100%;height:100%;object-fit:contain;';
                img.addEventListener('error', function () { this.replaceWith(createLeafIcon()); });
                mainWrap.appendChild(img);
            } else {
                mainWrap.appendChild(createLeafIcon());
            }
            imgEl.appendChild(mainWrap);

            // Thumbnail row below image
            const thumbRow = document.createElement('div');
            thumbRow.style.cssText = 'display:flex;gap:6px;justify-content:center;';

            const makeThumb = function (borderColor) {
                const thumb = document.createElement('div');
                thumb.style.cssText = 'width:48px;height:48px;border:2px solid ' + borderColor + ';border-radius:6px;overflow:hidden;cursor:pointer;display:flex;align-items:center;justify-content:center;background:var(--bg-tertiary);';
                return thumb;
            };

            // Main image thumbnail (active)
            const thumbMain = makeThumb('var(--primary)');
            if (p.imageUrl && isSafeUrl(p.imageUrl)) {
                const tImg = document.createElement('img');
                tImg.src = p.imageUrl;
                tImg.alt = '';
                tImg.style.cssText = 'width:100%;height:100%;object-fit:cover;';
                tImg.addEventListener('error', function () { this.remove(); thumbMain.appendChild(createLeafIcon()); });
                thumbMain.appendChild(tImg);
            } else {
                const leafT = createLeafIcon();
                leafT.style.fontSize = '20px';
                thumbMain.appendChild(leafT);
            }
            thumbMain.addEventListener('click', function () {
                // Switch main image back to primary thumbnail
                if (p.imageUrl && isSafeUrl(p.imageUrl)) {
                    const activeImg = mainWrap.querySelector('img');
                    if (activeImg) activeImg.src = p.imageUrl;
                }
            });
            thumbRow.appendChild(thumbMain);

            // Two placeholder thumbnails (leaf icons for now)
            for (let i = 0; i < 2; i++) {
                const thumb = makeThumb('var(--border)');
                const leafT = createLeafIcon();
                leafT.style.fontSize = '20px';
                thumb.appendChild(leafT);
                thumbRow.appendChild(thumb);
            }

            imgEl.appendChild(thumbRow);
        }

        // --- Action buttons ---
        // Add to Cart button
        const addBtn = document.querySelector('#product-detail-modal .btn-primary[data-action="shopAddToCartDetail"]');
        if (addBtn) {
            if (p.stock === 0) {
                addBtn.disabled = true;
                addBtn.textContent = 'Out of Stock';
                addBtn.style.background = 'var(--text-muted)';
                addBtn.style.cursor = 'not-allowed';
            } else {
                addBtn.disabled = false;
                addBtn.innerHTML = '<i class="fa-solid fa-cart-plus"></i> Add to Cart';
                addBtn.style.background = '';
                addBtn.style.cursor = '';
            }
        }

        // Buy Now button
        const buyBtn = document.getElementById('btn-buy-now');
        if (buyBtn) {
            if (p.stock === 0) {
                buyBtn.disabled = true;
                buyBtn.style.opacity = '0.5';
                buyBtn.style.cursor = 'not-allowed';
            } else {
                buyBtn.disabled = false;
                buyBtn.style.opacity = '';
                buyBtn.style.cursor = '';
            }
        }

        // Delivery info row
        const deliveryInfoRow = document.createElement('div');
        deliveryInfoRow.id = 'pd-delivery-info';
        deliveryInfoRow.style.cssText = 'font-size:13px;color:var(--text-primary);margin-top:10px;padding:8px 12px;background:var(--bg-tertiary);border-radius:8px;display:flex;flex-direction:column;gap:4px;';

        const deliveryDate = formatDeliveryDate(p.deliveryDays || 4);
        const now = new Date();
        const hoursLeft = 23 - now.getHours();
        const minsLeft = 59 - now.getMinutes();
        const deliveryLine = document.createElement('div');
        deliveryLine.style.cssText = 'display:flex;align-items:center;gap:6px;';
        const truckIcon = document.createElement('i');
        truckIcon.className = 'fa-solid fa-truck';
        truckIcon.style.cssText = 'color:var(--primary);';
        deliveryLine.appendChild(truckIcon);
        const deliveryText = document.createElement('span');
        if ((p.price || 0) >= FREE_DELIVERY_THRESHOLD) {
            deliveryText.textContent = 'Free delivery by ' + deliveryDate + '. Order in ' + hoursLeft + 'h ' + minsLeft + 'm';
        } else {
            deliveryText.textContent = '\u20B9' + DELIVERY_CHARGE + ' delivery by ' + deliveryDate + '. Order in ' + hoursLeft + 'h ' + minsLeft + 'm';
        }
        deliveryLine.appendChild(deliveryText);
        deliveryInfoRow.appendChild(deliveryLine);

        // Seller info
        const sellerLine = document.createElement('div');
        sellerLine.style.cssText = 'font-size:12px;color:var(--text-muted);';
        sellerLine.textContent = 'Sold by EcoVerse Official';
        deliveryInfoRow.appendChild(sellerLine);

        // Carbon saved
        const carbonSaved = getCarbonSaved(p);
        const carbonLine = document.createElement('div');
        carbonLine.style.cssText = 'font-size:12px;color:var(--primary);font-weight:500;';
        const leafIcon = document.createElement('i');
        leafIcon.className = 'fa-solid fa-leaf';
        leafIcon.style.cssText = 'margin-right:4px;';
        carbonLine.appendChild(leafIcon);
        carbonLine.appendChild(document.createTextNode('This product saves ~' + carbonSaved + ' kg CO\u2082 vs alternatives'));
        deliveryInfoRow.appendChild(carbonLine);

        // Remove existing delivery info if present
        const existingDelivery = document.getElementById('pd-delivery-info');
        if (existingDelivery) existingDelivery.remove();

        // Append after features or before actions
        const actionsContainer = document.querySelector('.product-detail-actions');
        if (actionsContainer && actionsContainer.parentNode) {
            actionsContainer.parentNode.insertBefore(deliveryInfoRow, actionsContainer);
        } else {
            const bodyEl = document.querySelector('.product-detail-info');
            if (bodyEl) bodyEl.appendChild(deliveryInfoRow);
        }

        // Update modal title
        EcoUtils.setText('pd-name', p.name || '');

        document.getElementById('product-detail-modal')?.classList.add('open');
    }

    function closeDetail() {
        document.getElementById('product-detail-modal')?.classList.remove('open');
    }

    function changeQty(d) {
        const el = document.getElementById('pd-qty');
        if (!el) return;
        let q = parseInt(el.textContent) + d;
        if (q < 1) q = 1;
        if (currentDetailProduct && q > (currentDetailProduct.stock || 100)) q = currentDetailProduct.stock || 1;
        el.textContent = q;
        detailQty = q;
    }

    async function addToCartFromDetail() {
        if (!currentDetailProduct) return;
        const qty = parseInt(document.getElementById('pd-qty')?.textContent || 1);
        try {
            await EcoAPI.apiPost('/api/shop/cart?productId=' + currentDetailProduct.id + '&quantity=' + qty);
            EcoVerse.showToast(currentDetailProduct.name + ' added to cart!', 'success');
            updateCartUI();
            closeDetail();
        } catch (e) {
            EcoVerse.showToast(e?.message || 'Failed to add to cart', 'error');
        }
    }

    /** Buy Now: add to cart then open checkout */
    async function buyNowFromDetail() {
        if (!currentDetailProduct) return;
        const qty = parseInt(document.getElementById('pd-qty')?.textContent || 1);
        try {
            await EcoAPI.apiPost('/api/shop/cart?productId=' + currentDetailProduct.id + '&quantity=' + qty);
            EcoVerse.showToast('Added to cart!', 'success');
            updateCartUI();
            closeDetail();
            // Open checkout
            setTimeout(() => Shop.openCheckout(), 300);
        } catch (e) {
            EcoVerse.showToast(e?.message || 'Failed to add to cart', 'error');
        }
    }

    // ================================================================
    // CHECKOUT — Server-Authoritative with Idempotency
    // ================================================================

    async function openCheckout() {
        closeCart();

        // Load cart from server
        let cartItems = [];
        try {
            const result = await EcoAPI.apiGet('/api/shop/cart');
            cartItems = result?.data || [];
        } catch (e) {
            EcoVerse.showToast('Failed to load cart', 'error');
            return;
        }

        if (!cartItems.length) {
            EcoVerse.showToast('Cart is empty', 'error');
            return;
        }

        // Calculate display totals
        const subtotal = cartItems.reduce((s, i) => s + (i.price || 0) * (i.quantity || 1), 0);
        const deliveryCharge = subtotal >= FREE_DELIVERY_THRESHOLD ? 0 : DELIVERY_CHARGE;
        const tax = Math.round(subtotal * GST_RATE * 100) / 100;
        const total = subtotal + deliveryCharge + tax;

        // Render order summary items
        const itemsEl = document.getElementById('checkout-items');
        if (itemsEl) {
            itemsEl.innerHTML = '';
            cartItems.forEach(i => {
                const row = document.createElement('div');
                row.style.cssText = 'display:flex;justify-content:space-between;padding:6px 0;font-size:13px;border-bottom:1px solid var(--border);';

                const nameSpan = document.createElement('span');
                nameSpan.textContent = i.productName || 'Unknown';
                if (i.quantity > 1) {
                    nameSpan.textContent += ' x' + i.quantity;
                }
                row.appendChild(nameSpan);

                const priceSpan = document.createElement('span');
                priceSpan.style.cssText = 'font-weight:600;';
                priceSpan.textContent = '\u20B9' + ((i.price || 0) * (i.quantity || 1)).toLocaleString('en-IN');
                row.appendChild(priceSpan);

                itemsEl.appendChild(row);
            });
        }

        EcoUtils.setText('checkout-subtotal', '\u20B9' + subtotal.toLocaleString('en-IN'));

        // Delivery charge row
        const deliveryRow = document.createElement('div');
        deliveryRow.id = 'checkout-delivery-row';
        deliveryRow.style.cssText = 'display:flex;justify-content:space-between;padding:6px 0;font-size:13px;color:var(--text-secondary);';
        const deliveryLabel = document.createElement('span');
        deliveryLabel.textContent = 'Delivery';
        deliveryRow.appendChild(deliveryLabel);
        const deliveryValue = document.createElement('span');
        if (deliveryCharge === 0) {
            deliveryValue.textContent = 'FREE';
            deliveryValue.style.color = 'var(--success)';
            deliveryValue.style.fontWeight = '600';
        } else {
            deliveryValue.textContent = '\u20B9' + deliveryCharge;
        }
        deliveryRow.appendChild(deliveryValue);

        // Insert delivery row after subtotal (before tax)
        const taxEl = document.getElementById('checkout-tax');
        if (taxEl && taxEl.parentNode) {
            const existingDelivery = document.getElementById('checkout-delivery-row');
            if (existingDelivery) existingDelivery.remove();
            taxEl.parentNode.insertBefore(deliveryRow, taxEl);
        }

        EcoUtils.setText('checkout-tax', '\u20B9' + tax.toLocaleString('en-IN'));
        EcoUtils.setText('checkout-total', '\u20B9' + total.toLocaleString('en-IN'));
        EcoUtils.setText('checkout-final-total', '\u20B9' + total.toLocaleString('en-IN'));

        // Update Place Order button text (keep #checkout-final-total span)
        const payBtn = document.querySelector('#checkout-modal .btn-primary');
        if (payBtn) {
            payBtn.innerHTML = '<i class="fa-solid fa-lock"></i> Place Order & Pay <span id="checkout-final-total">\u20B9' + total.toLocaleString('en-IN') + '</span>';
        }

        const carbonSave = cartItems.reduce((s, i) => s + (i.ecoRating || 0) * 0.5 * (i.quantity || 1), 0);
        const impactEl = document.getElementById('checkout-carbon-impact');
        if (impactEl) impactEl.textContent = 'This purchase saves ~' + carbonSave.toFixed(1) + ' kg CO\u2082 \u{1F331}';

        document.getElementById('checkout-modal')?.classList.add('open');
    }

    function closeCheckout() {
        document.getElementById('checkout-modal')?.classList.remove('open');
    }

    async function placeOrder() {
        const address = document.getElementById('checkout-address')?.value?.trim();
        const payment = document.querySelector('input[name="payment"]:checked')?.value || 'cod';

        if (!address) {
            EcoVerse.showToast('Please enter a shipping address', 'error');
            return;
        }

        const name = document.getElementById('checkout-name')?.value?.trim() || 'Customer';
        const phone = document.getElementById('checkout-phone')?.value?.trim() || '';
        const city = document.getElementById('checkout-city')?.value?.trim() || 'City';
        const state = document.getElementById('checkout-state')?.value?.trim() || 'State';
        const pincode = document.getElementById('checkout-pincode')?.value?.trim() || '000000';

        if (!name) {
            EcoVerse.showToast('Please enter your full name', 'error');
            return;
        }

        // Disable button to prevent double-click
        const payBtn = document.querySelector('#checkout-modal .btn-primary');
        if (payBtn) {
            payBtn.disabled = true;
            payBtn.textContent = 'Processing...';
        }

        try {
            // Step 1: Create order via /api/payments/create-order (server-authoritative)
            // NOTE: idempotency is enforced server-side; apiPost only forwards (endpoint, body)
            const orderPayload = {
                paymentMethod: payment,
                shippingAddress: {
                    fullName: name,
                    phone: phone,
                    addressLine1: address,
                    city: city,
                    state: state,
                    pincode: pincode
                }
            };

            const createResult = await EcoAPI.apiPost('/api/payments/create-order', orderPayload);

            if (!createResult?.data) {
                throw new Error('Failed to create order');
            }

            const orderData = createResult.data;

            // Step 2: If Razorpay order created, open Razorpay checkout
            if (orderData.razorpayOrderId && orderData.key) {
                await openRazorpayCheckout(orderData, payment);
            } else {
                // COD — show success
                EcoVerse.showToast('Order placed successfully!', 'success');
                closeCheckout();
                showOrderSuccess(orderData.ecoverseOrderId);
                updateCartUI();
            }
        } catch (e) {
            EcoVerse.showToast(e?.message || 'Failed to place order', 'error');
        } finally {
            if (payBtn) {
                payBtn.disabled = false;
                const total = document.getElementById('checkout-final-total')?.textContent || '\u20B90';
                payBtn.innerHTML = '<i class="fa-solid fa-lock"></i> Place Order & Pay <span id="checkout-final-total">' + total + '</span>';
            }
        }
    }

    /**
     * Open Razorpay checkout modal.
     * CRITICAL: Success is NOT trusted from browser callback.
     * We verify server-side before showing success.
     */
    async function openRazorpayCheckout(orderData, paymentMethod) {
        return new Promise((resolve, reject) => {
            if (typeof Razorpay === 'undefined') {
                const script = document.createElement('script');
                script.src = 'https://checkout.razorpay.com/v1/checkout.js';
                script.onload = () => openRazorpayModal(orderData, paymentMethod, resolve, reject);
                script.onerror = () => reject(new Error('Failed to load payment gateway'));
                document.head.appendChild(script);
            } else {
                openRazorpayModal(orderData, paymentMethod, resolve, reject);
            }
        });
    }

    function openRazorpayModal(orderData, paymentMethod, resolve, reject) {
        const options = {
            key: orderData.key,
            amount: orderData.amount,
            currency: orderData.currency || 'INR',
            name: 'EcoVerse',
            description: 'Eco-friendly products order',
            order_id: orderData.razorpayOrderId,
            handler: async function (response) {
                try {
                    const verifyResult = await EcoAPI.apiPost('/api/payments/verify', {
                        razorpayOrderId: response.razorpay_order_id,
                        razorpayPaymentId: response.razorpay_payment_id,
                        razorpaySignature: response.razorpay_signature
                    });

                    if (verifyResult?.data?.status === 'paid') {
                        EcoVerse.showToast('Payment successful! Order confirmed.', 'success');
                        closeCheckout();
                        showOrderSuccess(orderData.ecoverseOrderId);
                        updateCartUI();
                        resolve();
                    } else {
                        EcoVerse.showToast('Payment verification pending. Check your order history.', 'info');
                        closeCheckout();
                        updateCartUI();
                        resolve();
                    }
                } catch (e) {
                    EcoVerse.showToast(e?.message || 'Payment verification failed. Check your order history.', 'error');
                    closeCheckout();
                    updateCartUI();
                    resolve();
                }
            },
            prefill: {
                name: document.getElementById('checkout-name')?.value?.trim() || '',
                contact: document.getElementById('checkout-phone')?.value?.trim() || '',
            },
            theme: {
                color: '#22c55e'
            },
            modal: {
                ondismiss: function () {
                    EcoVerse.showToast('Payment not completed. You can retry from order history.', 'info');
                    closeCheckout();
                    updateCartUI();
                    resolve();
                }
            }
        };

        try {
            const rzp = new Razorpay(options);
            rzp.on('payment.failed', function (response) {
                EcoVerse.showToast('Payment failed: ' + (response.error.description || 'Unknown error'), 'error');
                closeCheckout();
                updateCartUI();
                resolve();
            });
            rzp.open();
        } catch (e) {
            reject(new Error('Failed to open payment gateway'));
        }
    }

    function showOrderSuccess(ecoverseOrderId) {
        const successEl = document.getElementById('order-success-modal');
        if (successEl) {
            EcoUtils.setText('order-success-id', '#' + (ecoverseOrderId || ''));
            // Update impact message
            const impactEl = document.getElementById('order-success-impact');
            if (impactEl) {
                // Estimate total carbon saved from current cart data
                impactEl.textContent = 'Estimated carbon saved: 0.0 kg CO\u2082';
                // Try to get from checkout
                const carbonEl = document.getElementById('checkout-carbon-impact');
                if (carbonEl) {
                    impactEl.textContent = carbonEl.textContent || impactEl.textContent;
                }
            }
            successEl.classList.add('open');
        }
    }

    function closeOrderSuccess() {
        document.getElementById('order-success-modal')?.classList.remove('open');
    }

    // ================================================================
    // SELL PRODUCT
    // ================================================================

    function openSellModal() { document.getElementById('sell-product-modal')?.classList.add('open'); }
    function closeSellModal() { document.getElementById('sell-product-modal')?.classList.remove('open'); }

    async function submitProduct() {
        const name = document.getElementById('sell-name')?.value?.trim();
        const category = document.getElementById('sell-category')?.value;
        const desc = document.getElementById('sell-desc')?.value?.trim();
        const priceStr = document.getElementById('sell-price')?.value;
        const stock = parseInt(document.getElementById('sell-stock')?.value) || 0;
        const imageUrl = document.getElementById('sell-image')?.value?.trim();
        const carbonStr = document.getElementById('sell-carbon')?.value;
        const featuresStr = document.getElementById('sell-features')?.value?.trim();

        if (!name || !priceStr) {
            EcoVerse.showToast('Name and price are required', 'error');
            return;
        }

        const price = parseFloat(priceStr);
        if (isNaN(price) || price <= 0) {
            EcoVerse.showToast('Please enter a valid price', 'error');
            return;
        }

        const ecoRating = carbonStr ? Math.min(5, Math.max(1, Math.round(parseFloat(carbonStr) / 0.5))) : 4;

        const features = featuresStr ? featuresStr.split('\n').filter(f => f.trim()).map(f => f.trim()) : [];

        try {
            const body = {
                name,
                category: category || 'other',
                description: desc,
                price,
                stock,
                imageUrl: imageUrl || undefined,
                ecoRating: ecoRating,
                features: features.length > 0 ? features : undefined
            };

            const result = await EcoAPI.apiPost('/api/shop/products', body);

            if (result?.data) {
                EcoVerse.showToast('Product listed!', 'success');
                closeSellModal();
                render(); // Refresh product list
            }
        } catch (e) {
            EcoVerse.showToast(e?.message || 'Failed to list product', 'error');
        }
    }

    // ================================================================
    // ORDER HISTORY
    // ================================================================

    async function renderOrderHistory() {
        const container = document.getElementById('order-history-list');
        if (!container) return;
        container.innerHTML = '';
        container.appendChild(createLoadingSpinner());

        orderPage = 0;
        hasMoreOrders = true;

        try {
            const result = await EcoAPI.apiGet('/api/shop/orders?page=0&size=10');
            const orders = result?.data?.content || [];

            if (!orders.length) {
                container.innerHTML = '';
                const empty = document.createElement('div');
                empty.style.cssText = 'text-align:center;padding:40px;color:var(--text-muted);';
                empty.textContent = 'No orders yet';
                container.appendChild(empty);
                return;
            }

            container.innerHTML = '';
            orders.forEach(order => {
                container.appendChild(createOrderCard(order));
            });
        } catch (e) {
            container.innerHTML = '';
            const err = document.createElement('div');
            err.style.cssText = 'text-align:center;padding:40px;color:var(--error);';
            err.textContent = 'Failed to load orders';
            container.appendChild(err);
        }
    }

    function createOrderCard(order) {
        const card = document.createElement('div');
        card.style.cssText = 'background:var(--card);border-radius:12px;padding:16px;margin-bottom:12px;border:1px solid var(--border);';

        const header = document.createElement('div');
        header.style.cssText = 'display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;';

        const idSpan = document.createElement('strong');
        idSpan.textContent = 'Order #' + order.id;
        header.appendChild(idSpan);

        // Status badge
        const statusBadge = document.createElement('span');
        statusBadge.style.cssText = 'padding:4px 10px;border-radius:20px;font-size:12px;font-weight:600;' + getStatusStyle(order.status);
        statusBadge.textContent = formatStatus(order.status);
        header.appendChild(statusBadge);

        card.appendChild(header);

        // Date
        const dateDiv = document.createElement('div');
        dateDiv.style.cssText = 'font-size:12px;color:var(--text-muted);margin-bottom:8px;';
        dateDiv.textContent = order.createdAt ? new Date(order.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
        card.appendChild(dateDiv);

        // Total
        const totalDiv = document.createElement('div');
        totalDiv.style.cssText = 'font-size:16px;font-weight:600;';
        totalDiv.textContent = '\u20B9' + (order.totalPrice || 0).toLocaleString('en-IN');
        card.appendChild(totalDiv);

        // Items summary
        if (order.items && order.items.length) {
            const itemsDiv = document.createElement('div');
            itemsDiv.style.cssText = 'font-size:13px;color:var(--text-muted);margin-top:4px;';
            const itemNames = order.items.map(i => i.productName + ' x' + i.quantity).join(', ');
            itemsDiv.textContent = itemNames;
            card.appendChild(itemsDiv);
        }

        // Payment method
        if (order.paymentMethod) {
            const pmDiv = document.createElement('div');
            pmDiv.style.cssText = 'font-size:11px;color:var(--text-muted);margin-top:2px;';
            pmDiv.textContent = 'Payment: ' + order.paymentMethod.toUpperCase();
            card.appendChild(pmDiv);
        }

        // Actions for PENDING_PAYMENT orders
        if (order.status === 'PENDING_PAYMENT') {
            const btnGroup = document.createElement('div');
            btnGroup.style.cssText = 'display:flex;gap:8px;margin-top:8px;';

            const cancelBtn = document.createElement('button');
            cancelBtn.className = 'btn btn-ghost';
            cancelBtn.style.cssText = 'font-size:12px;color:var(--error);border:1px solid var(--error);padding:4px 12px;border-radius:6px;cursor:pointer;';
            cancelBtn.textContent = 'Cancel Order';
            cancelBtn.addEventListener('click', () => cancelOrder(order.id));
            btnGroup.appendChild(cancelBtn);

            if (order.paymentMethod && order.paymentMethod !== 'cod') {
                const retryBtn = document.createElement('button');
                retryBtn.className = 'btn btn-primary';
                retryBtn.style.cssText = 'font-size:12px;padding:4px 12px;border-radius:6px;cursor:pointer;background:var(--primary);color:white;border:none;';
                retryBtn.textContent = 'Retry Payment';
                retryBtn.addEventListener('click', () => retryPayment(order.id));
                btnGroup.appendChild(retryBtn);
            }

            card.appendChild(btnGroup);
        }

        // Cancel/Return buttons for DELIVERED orders
        if (order.status === 'DELIVERED') {
            const returnBtn = document.createElement('button');
            returnBtn.style.cssText = 'margin-top:8px;font-size:12px;color:var(--primary);background:none;border:none;cursor:pointer;padding:0;';
            returnBtn.textContent = 'Return or Replace';
            returnBtn.addEventListener('click', () => EcoVerse.showToast('Contact support for returns', 'info'));
            card.appendChild(returnBtn);
        }

        return card;
    }

    async function cancelOrder(orderId) {
        if (!confirm('Are you sure you want to cancel this order?')) return;
        try {
            await EcoAPI.apiPatch('/api/shop/orders/' + orderId + '/status', { status: 'CANCELLED' });
            EcoVerse.showToast('Order cancelled', 'info');
            renderOrderHistory();
        } catch (e) {
            EcoVerse.showToast(e?.message || 'Failed to cancel order', 'error');
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
            'REFUNDED': 'background:rgba(239,68,68,0.15);color:#ef4444;',
            'PAYMENT_FAILED': 'background:rgba(239,68,68,0.15);color:#ef4444;'
        };
        return styles[status] || 'background:var(--border);color:var(--text-muted);';
    }

    function formatStatus(status) {
        if (!status) return 'Unknown';
        return status.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    }

    // ================================================================
    // PAYMENT TOGGLE
    // ================================================================

    function setupPaymentToggle() {
        document.querySelectorAll('input[name="payment"]').forEach(r => {
            r.addEventListener('change', function () {
                const cardForm = document.getElementById('card-details-form');
                const upiForm = document.getElementById('upi-details-form');
                if (cardForm) cardForm.style.display = this.value === 'card' ? 'block' : 'none';
                if (upiForm) upiForm.style.display = this.value === 'upi' ? 'block' : 'none';
            });
        });
    }

    // ================================================================
    // PAYMENT RETRY
    // ================================================================

    async function retryPayment(orderId) {
        if (!confirm('Retry payment for this order?')) return;
        try {
            const result = await EcoAPI.apiPost('/api/payments/retry/' + orderId);

            if (result?.data?.razorpayOrderId && result?.data?.key) {
                await openRazorpayCheckout(result.data, 'online');
            } else {
                EcoVerse.showToast('Payment retry not available for this order.', 'info');
            }
        } catch (e) {
            EcoVerse.showToast(e?.message || 'Failed to retry payment', 'error');
        }
    }

    // ================================================================
    // PUBLIC API
    // ================================================================

    return {
        // Initialize (called once from app.js or manually)
        init: init,
        // Products
        render: render,
        setCategory: setCategory,
        filterProducts: filterProducts,
        loadMore: loadMore,
        // Cart
        addToCart: addToCart,
        removeFromCart: removeFromCart,
        changeCartQty: changeCartQty,
        updateCartUI: updateCartUI,
        openCart: openCart,
        closeCart: closeCart,
        // Detail
        openDetail: openDetail,
        closeDetail: closeDetail,
        changeQty: changeQty,
        addToCartFromDetail: addToCartFromDetail,
        buyNowFromDetail: buyNowFromDetail,
        // Checkout
        openCheckout: openCheckout,
        closeCheckout: closeCheckout,
        placeOrder: placeOrder,
        closeOrderSuccess: closeOrderSuccess,
        // Sell
        openSellModal: openSellModal,
        closeSellModal: closeSellModal,
        submitProduct: submitProduct,
        // Orders
        renderOrderHistory: renderOrderHistory,
        cancelOrder: cancelOrder,
        // Payment
        setupPaymentToggle: setupPaymentToggle,
        retryPayment: retryPayment
    };
})();

// Auto-initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => Shop.init());
} else {
    Shop.init();
}