/**
 * EcoVerse — Weather Module (Phase 4: Real Weather Site)
 *
 * Features:
 * - Auto geolocation detection with city name via reverse geocoding
 * - Search with autocomplete suggestions (Open-Meteo geocoding)
 * - Enter key support in search bar
 * - "Detect My Location" button
 * - Server-first weather data from /api/weather
 * - 7-day forecast, AQI, rain chance, UV, visibility
 * - Outdoor advisory panel
 * - Weather-carbon insight
 * - Full error/retry handling
 */

const Weather = (() => {

    const weatherIcons = {
        0:'fa-sun',1:'fa-cloud-sun',2:'fa-cloud',3:'fa-cloud',
        45:'fa-cloud-fog',48:'fa-smog',51:'fa-smog',53:'fa-smog',55:'fa-cloud-moon-rain',
        56:'fa-cloud-rain',57:'fa-cloud-rain',61:'fa-cloud-showers-heavy',
        63:'fa-cloud-showers-heavy',65:'fa-snowflake',66:'fa-snowflake',67:'fa-snowflake',
        71:'fa-snowflake',73:'fa-snowflake',75:'fa-snowflake',77:'fa-bolt',
        80:'fa-cloud-rain',81:'fa-cloud-rain',82:'fa-cloud-showers-heavy',
        85:'fa-snowflake',86:'fa-snowflake',95:'fa-bolt',96:'fa-bolt',99:'fa-bolt'
    };

    const weatherDescs = {
        0:'Clear Sky',1:'Mainly Clear',2:'Partly Cloudy',3:'Overcast',
        45:'Foggy',48:'Rime Fog',51:'Light Drizzle',53:'Drizzle',55:'Dense Drizzle',
        56:'Freezing Drizzle',57:'Heavy Freezing Drizzle',
        61:'Light Rain',63:'Moderate Rain',65:'Heavy Rain',
        66:'Light Freezing Rain',67:'Heavy Freezing Rain',
        71:'Light Snow',73:'Moderate Snow',75:'Heavy Snow',
        77:'Snow Grains',80:'Light Showers',81:'Moderate Showers',
        82:'Violent Showers',85:'Light Snow Showers',86:'Heavy Snow Showers',
        95:'Thunderstorm',96:'Thunderstorm with Hail',99:'Severe Thunderstorm'
    };

    let suggestionTimer = null;

    /** Safely display a value — shows "—" for null/undefined */
    function safeVal(val, suffix) {
        if (val === null || val === undefined) return '—';
        return suffix ? `${val}${suffix}` : String(val);
    }

    /** Show loading state */
    function showLoading() {
        const el = document.getElementById('weather-location');
        if (el) {
            el.textContent = '';
            const icon = document.createElement('i');
            icon.className = 'fa-solid fa-spinner fa-spin';
            el.appendChild(icon);
            el.appendChild(document.createTextNode(' Detecting location...'));
        }
    }

    /** Show error state */
    function showError(msg) {
        const el = document.getElementById('weather-location');
        if (el) {
            el.textContent = '';
            const icon = document.createElement('i');
            icon.className = 'fa-solid fa-triangle-exclamation';
            icon.style.color = 'var(--danger)';
            el.appendChild(icon);
            el.appendChild(document.createTextNode(' Weather unavailable — try searching below'));
        }
        const descEl = document.getElementById('weather-desc');
        if (descEl) descEl.textContent = 'Could not load weather data';
        console.error('Weather error:', msg);
    }

    /** Show prompt to search for a city */
    function showLocationPrompt() {
        const locEl = document.getElementById('weather-location');
        if (locEl) {
            locEl.textContent = '';
            const icon = document.createElement('i');
            icon.className = 'fa-solid fa-map-pin';
            icon.style.color = 'var(--warning)';
            locEl.appendChild(icon);
            locEl.appendChild(document.createTextNode(' Search for a city or tap the location button below'));
        }
        const tempEl = document.getElementById('weather-temp-big');
        if (tempEl) tempEl.textContent = '—';
        const descEl = document.getElementById('weather-desc');
        if (descEl) descEl.textContent = 'Search for a city to see live weather data';
    }

    // ============================================================
    // GEOLOCATION + REVERSE GEOCODING
    // ============================================================

    /** Detect user's location via browser Geolocation API, then reverse-geocode to city name */
    function detectLocation() {
        if (!navigator.geolocation) {
            showToast('Geolocation is not supported by your browser.', 'error');
            showLocationPrompt();
            return;
        }

        showLoading();

        navigator.geolocation.getCurrentPosition(
            async (pos) => {
                const lat = pos.coords.latitude;
                const lon = pos.coords.longitude;

                AppState.weatherCache = { lat, lon, city: null };

                // Reverse geocode to get city name
                try {
                    const res = await window.fetch(
                        `https://geocoding-api.open-meteo.com/v1/search?name=&count=1&latitude=${lat}&longitude=${lon}`
                    );
                    // Open-Meteo geocoding doesn't support reverse lookup directly.
                    // Use the Nominatim (OpenStreetMap) free reverse geocoding API instead.
                    const revRes = await window.fetch(
                        `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&accept-language=en`
                    );
                    const revData = await revRes.json();
                    const cityName = revData?.address?.city
                        || revData?.address?.town
                        || revData?.address?.village
                        || revData?.address?.county
                        || revData?.address?.state
                        || null;
                    const stateName = revData?.address?.state || null;
                    const countryCode = revData?.address?.country_code || null;
                    AppState.weatherCache.city = cityName;
                    AppState.weatherCache.state = stateName;

                    // Save detected location to user profile (persists across sessions)
                    if (cityName || stateName) {
                        try {
                            await EcoAPI.apiPut('/api/profile/location', {
                                city: cityName || '',
                                state: stateName || '',
                                country: countryCode || '',
                                latitude: lat,
                                longitude: lon
                            });
                            // Also update AppState.user so news module can use it immediately
                            if (AppState.user) {
                                AppState.user.city = cityName;
                                AppState.user.state = stateName;
                                if (countryCode) AppState.user.country = countryCode.toUpperCase();
                            }
                        } catch (saveErr) {
                            console.warn('Failed to save location to profile:', saveErr);
                        }
                    }
                } catch (e) {
                    // Reverse geocoding failed — keep lat/lon, city will fall back to server data
                    console.warn('Reverse geocoding failed, will use server location name:', e);
                }

                await loadWeather();
            },
            (err) => {
                console.warn('Geolocation denied or unavailable:', err.message);
                AppState.weatherCache = null;
                showToast('Location access denied. Search for your city below.', 'warning');
                showLocationPrompt();
            },
            { enableHighAccuracy: false, timeout: 8000, maximumAge: 300000 }
        );
    }

    // ============================================================
    // SEARCH WITH AUTOCOMPLETE SUGGESTIONS
    // ============================================================

    /** Search for a city using Open-Meteo geocoding API */
    async function search() {
        const input = document.getElementById('weather-search');
        const c = input?.value?.trim();
        if (!c) return;

        hideSuggestions();
        showLoading();

        try {
            const geoRes = await window.fetch(
                `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(c)}&count=5&language=en`
            );
            const data = await geoRes.json();

            if (data.results?.length) {
                const r = data.results[0];
                AppState.weatherCache = {
                    lat: r.latitude,
                    lon: r.longitude,
                    city: r.name + (r.admin1 ? `, ${r.admin1}` : '') + (r.country ? `, ${r.country}` : '')
                };
                await loadWeather();
            } else {
                AppState.weatherCache = null;
                showToast(`City "${c}" not found. Try a different spelling.`, 'error');
                showLocationPrompt();
            }
        } catch (err) {
            AppState.weatherCache = null;
            showToast('City search failed. Check your internet connection.', 'error');
            showError(err);
        }
    }

    /** Search for a specific suggestion (when user clicks a suggestion) */
    async function searchBySuggestion(result) {
        hideSuggestions();
        const input = document.getElementById('weather-search');
        if (input) input.value = result.name;
        showLoading();

        AppState.weatherCache = {
            lat: result.latitude,
            lon: result.longitude,
            city: result.name + (result.admin1 ? `, ${result.admin1}` : '') + (result.country ? `, ${result.country}` : '')
        };

        try {
            await loadWeather();
        } catch (err) {
            showError(err);
        }
    }

    /** Fetch autocomplete suggestions as user types */
    function fetchSuggestions() {
        const input = document.getElementById('weather-search');
        const query = input?.value?.trim();

        if (!query || query.length < 2) {
            hideSuggestions();
            return;
        }

        clearTimeout(suggestionTimer);
        suggestionTimer = setTimeout(async () => {
            try {
                const res = await window.fetch(
                    `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(query)}&count=6&language=en`
                );
                const data = await res.json();
                if (data.results?.length) {
                    showSuggestions(data.results);
                } else {
                    hideSuggestions();
                }
            } catch (e) {
                hideSuggestions();
            }
        }, 350);
    }

    /** Render suggestion dropdown */
    function showSuggestions(results) {
        let box = document.getElementById('weather-suggestions');
        if (!box) return;

        box.textContent = '';
        results.forEach(r => {
            const div = document.createElement('div');
            div.style.cssText = 'padding:10px 14px;cursor:pointer;display:flex;align-items:center;gap:8px;border-bottom:1px solid var(--border);font-size:0.9rem;transition:background 0.15s;';
            div.onmouseenter = () => div.style.background = 'var(--primary-alpha, rgba(46,139,87,0.1))';
            div.onmouseleave = () => div.style.background = 'transparent';

            const icon = document.createElement('i');
            icon.className = 'fa-solid fa-location-dot';
            icon.style.color = 'var(--primary)';

            const text = document.createElement('span');
            const sub = [r.admin1, r.country].filter(Boolean).join(', ');
            text.textContent = r.name + (sub ? ` — ${sub}` : '');

            div.appendChild(icon);
            div.appendChild(text);
            div.addEventListener('click', () => searchBySuggestion(r));
            box.appendChild(div);
        });
        box.style.display = 'block';
    }

    /** Hide suggestion dropdown */
    function hideSuggestions() {
        const box = document.getElementById('weather-suggestions');
        if (box) box.style.display = 'none';
    }

    // ============================================================
    // LOAD WEATHER FROM SERVER
    // ============================================================

    /** Load weather data from server API */
    async function loadWeather() {
        if (!AppState.weatherCache) {
            showLocationPrompt();
            return;
        }

        const { lat, lon } = AppState.weatherCache;

        const locEl = document.getElementById('weather-location');
        if (locEl) {
            locEl.textContent = '';
            const sp = document.createElement('i');
            sp.className = 'fa-solid fa-spinner fa-spin';
            locEl.appendChild(sp);
            locEl.appendChild(document.createTextNode(' Loading weather...'));
        }

        try {
            let result = await EcoAPI.apiGet(`/api/weather?lat=${lat}&lon=${lon}`);
            if (!result || !result.success || !result.data) {
                // Retry once
                result = await EcoAPI.apiGet(`/api/weather?lat=${lat}&lon=${lon}`);
            }
            if (!result || !result.success || !result.data) {
                throw new Error(result?.message || 'Weather data unavailable');
            }
            renderWeather(result.data);
        } catch (err) {
            showError(err);
        }
    }

    // ============================================================
    // RENDER WEATHER DATA
    // ============================================================

    function renderWeather(data) {
        // Location — prefer cached city name from geocoding/reverse-geocoding
        const locEl = document.getElementById('weather-location');
        if (locEl) {
            locEl.textContent = '';
            const icon = document.createElement('i');
            icon.className = 'fa-solid fa-location-dot';
            icon.style.color = 'var(--danger)';
            locEl.appendChild(icon);
            const displayCity = AppState.weatherCache?.city || data.location || 'Unknown';
            locEl.appendChild(document.createTextNode(' ' + displayCity));
        }

        // Main stats
        EcoUtils.setText('weather-temp-big', safeVal(data.temperature, '°C'));
        EcoUtils.setText('weather-desc', data.description || '—');
        EcoUtils.setText('weather-date', new Date().toLocaleDateString('en', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' }));
        EcoUtils.setText('weather-feels', safeVal(data.feelsLike, '°C'));
        EcoUtils.setText('weather-humidity', safeVal(data.humidity, '%'));
        EcoUtils.setText('weather-wind', safeVal(data.windSpeed, ' km/h'));
        EcoUtils.setText('weather-pressure', safeVal(data.pressure, ' hPa'));
        EcoUtils.setText('weather-uv', safeVal(data.uvIndex));

        // Visibility — from server if available
        // Visibility from server is in meters — convert to km
        const visKm = data.visibility ? (data.visibility / 1000).toFixed(1) : null;
        EcoUtils.setText('weather-visibility', visKm ? `${visKm} km` : '—');

        // Weather icon
        const iconEl = document.getElementById('weather-icon-big');
        if (iconEl) {
            iconEl.innerHTML = `<i class="fa-solid ${data.icon || 'fa-cloud-sun'}"></i>`;
        }

        // AQI
        if (data.aqi !== null && data.aqi !== undefined) {
            EcoUtils.setText('weather-aqi', Math.round(data.aqi));
            const aqiLabel = data.aqiLabel || 'Unknown';
            const pmText = data.pm25 ? ` (PM2.5: ${data.pm25}µg)` : '';
            EcoUtils.setText('weather-aqi-label', `${aqiLabel}${pmText}`);
        } else {
            EcoUtils.setText('weather-aqi', '—');
            EcoUtils.setText('weather-aqi-label', 'Unavailable');
        }

        // Rain chance
        EcoUtils.setText('weather-rain', safeVal(data.rainChance, '%'));

        // Dashboard mini-weather
        if (data.temperature !== null && data.temperature !== undefined) {
            EcoUtils.setText('dash-temp', `${data.temperature}°`);
        }
        const city = AppState.weatherCache?.city || (data.location || 'Unknown').split(',')[0].trim();
        EcoUtils.setText('dash-weather-city', city);

        // 7-day Forecast
        const fEl = document.getElementById('forecast-row');
        if (fEl && data.forecast && data.forecast.length) {
            fEl.textContent = '';
            data.forecast.forEach(f => {
                const div = document.createElement('div');
                div.className = 'forecast-day';

                const nameDiv = document.createElement('div');
                nameDiv.className = 'forecast-day-name';
                nameDiv.textContent = f.date ? new Date(f.date + 'T00:00:00').toLocaleDateString('en', { weekday: 'short', month: 'short', day: 'numeric' }) : '—';

                const iconDiv = document.createElement('div');
                iconDiv.className = 'forecast-day-icon';
                const iconInner = document.createElement('i');
                iconInner.className = `fa-solid ${f.icon || 'fa-cloud'}`;
                iconDiv.appendChild(iconInner);

                const highDiv = document.createElement('div');
                highDiv.className = 'forecast-day-temp';
                highDiv.textContent = f.highTemp !== null && f.highTemp !== undefined ? `${Math.round(f.highTemp)}°` : '—';

                const lowDiv = document.createElement('div');
                lowDiv.className = 'forecast-day-low';
                lowDiv.textContent = f.lowTemp !== null && f.lowTemp !== undefined ? `${Math.round(f.lowTemp)}°` : '—';

                div.appendChild(nameDiv);
                div.appendChild(iconDiv);
                div.appendChild(highDiv);
                div.appendChild(lowDiv);
                fEl.appendChild(div);
            });
        }

        // Carbon Insight
        const insightEl = document.getElementById('weather-carbon-insight');
        if (insightEl) {
            let insight = '';
            if (data.aqi === null || data.aqi === undefined || data.aqi <= 50) {
                insight = 'Air quality is excellent! Perfect day for walking or cycling — low carbon footprint and great for health.';
            } else if (data.aqi <= 100) {
                insight = 'Air quality is moderate. Walking or cycling is still greener than driving — just limit prolonged outdoor exertion.';
            } else if (data.aqi <= 150) {
                insight = 'Air quality is unhealthy for sensitive groups. Consider public transport instead of driving — lower emissions and less exposure.';
            } else {
                insight = 'Air quality is hazardous! Avoid outdoor activities. Use public transport if you must travel — carpooling reduces per-person emissions.';
            }
            if (data.rainChance && data.rainChance > 70) {
                insight += ' High rain chance — carry an umbrella and consider indoor transit options.';
            }
            insightEl.innerText = insight;
        }

        // Update Outdoor Advisory panel
        updateAdvisory(data.aqi, data.pm25, data.uvIndex);

        // Save to cache for dashboard/AI use
        if (AppState.weatherCache) {
            AppState.weatherCache.temp = data.temperature;
            AppState.weatherCache.aqi = data.aqi;
        }
    }

    function getIcon(code) { return weatherIcons[code] || 'fa-cloud-sun'; }
    function getDesc(code) { return weatherDescs[code] || 'Cloudy'; }

    function updateAdvisory(aqi, pm25, uv) {
        const panel = document.getElementById('outdoor-advisory');
        const levelEl = document.getElementById('advisory-level');
        const msgEl = document.getElementById('advisory-message');
        const precEl = document.getElementById('advisory-precautions');
        const flagsEl = document.getElementById('advisory-flags');
        if (!panel || !levelEl) return;

        let level, cls, title, message, precautions, children, elderly, exercise;

        if (aqi === null || aqi === undefined) {
            level = 'SAFE'; cls = 'outdoor-advisory--safe'; title = 'SAFE'; message = 'Air quality data unavailable. Check local advisories before extended outdoor activity.';
            precautions = ['Monitor local air quality reports', 'Stay hydrated during outdoor activities'];
            children = 'warn'; elderly = 'warn'; exercise = 'safe';
        } else if (aqi <= 50) {
            level = 'SAFE'; cls = 'outdoor-advisory--safe'; title = 'SAFE'; message = 'Air quality is excellent! Perfect conditions for all outdoor activities.';
            precautions = ['Great day for walking, cycling, or jogging outdoors', 'No mask required — air is clean', 'Open windows for fresh air ventilation', 'Ideal day for children to play outside'];
            children = 'safe'; elderly = 'safe'; exercise = 'safe';
        } else if (aqi <= 100) {
            level = 'CAUTION'; cls = 'outdoor-advisory--caution'; title = 'CAUTION'; message = 'Air quality is moderate. Sensitive individuals should limit prolonged outdoor exertion.';
            precautions = ['Reduce prolonged outdoor exercise', 'Sensitive groups should consider a mask', 'Keep windows partially closed during peak traffic', 'Stay hydrated and take breaks indoors'];
            children = 'warn'; elderly = 'warn'; exercise = 'safe';
        } else if (aqi <= 150) {
            level = 'UNHEALTHY'; cls = 'outdoor-advisory--unhealthy'; title = 'UNHEALTHY'; message = 'Air quality is unhealthy for sensitive groups. Limit outdoor activities.';
            precautions = ['Avoid jogging or cycling outdoors', 'Wear N95 mask if going outside', 'Keep windows and doors closed', 'Use air purifier indoors if available', 'Children and elderly should stay indoors'];
            children = 'danger'; elderly = 'danger'; exercise = 'warn';
        } else {
            level = 'HAZARDOUS'; cls = 'outdoor-advisory--hazardous'; title = 'HAZARDOUS'; message = 'Air quality is dangerous! Avoid ALL outdoor activities. Stay indoors with air purification.';
            precautions = ['Stay indoors — do NOT go outside for exercise', 'Seal windows and doors', 'Run air purifier on highest setting', 'Wear N95 mask if you must go out', 'Seek medical help if experiencing breathing difficulty'];
            children = 'danger'; elderly = 'danger'; exercise = 'danger';
        }

        panel.className = `outdoor-advisory ${cls}`;
        levelEl.className = `advisory-level advisory-level--${level.toLowerCase()}`;
        levelEl.textContent = '';
        const shieldIcon = document.createElement('i');
        shieldIcon.className = 'fa-solid fa-shield-halved';
        levelEl.appendChild(shieldIcon);
        levelEl.appendChild(document.createTextNode(` ${title} — ${level === 'SAFE' ? 'Safe for Outdoor Activities' : level === 'CAUTION' ? 'Moderate Air Quality' : level === 'UNHEALTHY' ? 'Limit Outdoor Activities' : 'Stay Indoors!'}`));

        if (msgEl) msgEl.innerText = message;
        if (precEl) {
            precEl.textContent = '';
            precautions.forEach(p => {
                const li = document.createElement('li');
                li.textContent = p;
                precEl.appendChild(li);
            });
        }

        const flagClass = { safe: 'advisory-flag--safe', warn: 'advisory-flag--warn', danger: 'advisory-flag--danger' };
        const flagLabel = { children: 'Children', elderly: 'Elderly', exercise: 'Exercise' };

        if (flagsEl) {
            flagsEl.textContent = '';
            [
                { id: 'children', val: children },
                { id: 'elderly', val: elderly },
                { id: 'exercise', val: exercise }
            ].forEach(f => {
                const span = document.createElement('span');
                span.className = `advisory-flag ${flagClass[f.val]}`;
                span.id = `flag-${f.id}`;

                const icon = document.createElement('i');
                icon.className = `fa-solid ${f.val === 'safe' ? 'fa-child' : f.val === 'warn' ? 'fa-exclamation' : 'fa-xmark'}`;
                span.appendChild(icon);

                const label = f.val === 'safe' ? 'Safe for' : f.val === 'warn' ? 'Caution for' : 'Avoid for';
                span.appendChild(document.createTextNode(` ${label} ${flagLabel[f.id]}`));
                flagsEl.appendChild(span);
            });
        }
    }

    // ============================================================
    // INIT — Wire up search events
    // ============================================================

    function init() {
        const searchInput = document.getElementById('weather-search');
        if (searchInput) {
            // Enter key triggers search
            searchInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    search();
                }
            });
            // Typing triggers autocomplete suggestions
            searchInput.addEventListener('input', () => fetchSuggestions());
            // Focus out hides suggestions
            searchInput.addEventListener('blur', () => {
                setTimeout(hideSuggestions, 200);
            });
            // Focus shows suggestions if there's text
            searchInput.addEventListener('focus', () => {
                if (searchInput.value.trim().length >= 2) fetchSuggestions();
            });
        }
    }

    return { fetch: loadWeather, search, detectLocation, getIcon, getDesc, updateAdvisory, loadWeather, init };
})();
