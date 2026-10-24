'use strict';

/*
 * Lumen Web — informe local del dispositivo.
 * Sin dependencias, sin red, sin telemetría.
 */
(function () {
  var $ = function (id) { return document.getElementById(id); };
  var root = document.documentElement;
  var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

  var state = {
    info: { os: '', osVersion: '', browser: '', browserVersion: '', device: '', model: '' },
    active: 'inicio',
    webgl2: false,
    hasWebGL: false,
    battery: { supported: false, level: null, charging: null, chargingTime: null, dischargingTime: null, obj: null },
    network: { connection: null },
    live: { fps: null, refresh: null },
    sensors: { active: false, motion: false, orientation: false, last: { acc: {}, gyro: {}, ori: {}, heading: null } },
    memory: { supported: false },
    storage: { used: null, quota: null, pct: null, persisted: null }
  };

  var numberFmt = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 1 });
  var intFmt = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 0 });
  var dateFmt = new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'short' });

  /* ------------------------------------------------------------------ */
  /* Utilidades                                                          */
  /* ------------------------------------------------------------------ */

  function clamp(n, min, max) { return Math.min(max, Math.max(min, n)); }

  function bytes(n) {
    if (!Number.isFinite(n) || n < 0) return null;
    var units = ['B', 'KB', 'MB', 'GB', 'TB'];
    var i = 0;
    var v = n;
    while (v >= 1024 && i < units.length - 1) { v /= 1024; i += 1; }
    var digits = v >= 100 ? 0 : v >= 10 ? 1 : 2;
    return numberFmt.format(Number(v.toFixed(digits))) + ' ' + units[i];
  }

  function pctText(n, digits) {
    if (!Number.isFinite(n)) return null;
    var d = typeof digits === 'number' ? digits : 0;
    return numberFmt.format(Number(n.toFixed(d)));
  }

  function setText(id, text) {
    var el = $(id);
    if (el) el.textContent = text;
  }

  function setVal(id, value, reason) {
    var el = $(id);
    if (!el) return;
    var missing = value === null || value === undefined || value === '';
    var text = missing ? 'No disponible' : String(value);
    el.textContent = text;
    if (missing) {
      el.classList.add('na');
      el.tabIndex = 0;
      el.title = reason || 'Este navegador no expone este dato.';
      el.setAttribute('aria-label', 'No disponible. ' + (reason || 'Este navegador no expone este dato.'));
    } else {
      el.classList.remove('na');
      el.removeAttribute('tabindex');
      if (reason) el.title = reason;
      else el.removeAttribute('title');
    }
  }

  function setMetric(valueId, unitId, subId, value, unit, sub) {
    setText(valueId, value === null || value === undefined ? '—' : String(value));
    setText(unitId, value === null || value === undefined ? '' : (unit || ''));
    setText(subId, sub || '');
  }

  function setBar(id, pct) {
    var el = $(id);
    if (!el) return;
    var v = Number.isFinite(pct) ? clamp(pct, 0, 100) : 0;
    el.style.width = v.toFixed(2) + '%';
  }

  function makeEl(tag, className, text) {
    var el = document.createElement(tag);
    if (className) el.className = className;
    if (text !== undefined) el.textContent = text;
    return el;
  }

  function toast(message) {
    var el = $('toast');
    if (!el) return;
    el.textContent = message;
    el.classList.add('show');
    clearTimeout(toast._timer);
    toast._timer = setTimeout(function () { el.classList.remove('show'); }, 2800);
  }

  function safe(fn) {
    try { return fn(); } catch (e) { return null; }
  }

  /* ------------------------------------------------------------------ */
  /* Tema                                                                */
  /* ------------------------------------------------------------------ */

  var THEME_KEY = 'lumen-theme';
  var sparks = [];
  var systemTheme = window.matchMedia('(prefers-color-scheme: light)');

  function storedTheme() {
    try { return localStorage.getItem(THEME_KEY); } catch (e) { return null; }
  }

  function applyTheme(theme, persist) {
    var next = theme === 'light' ? 'light' : 'dark';
    root.dataset.theme = next;
    if (persist) {
      try { localStorage.setItem(THEME_KEY, next); } catch (e) { /* modo privado */ }
    }
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', next === 'light' ? '#F7F8F9' : '#0A0B0D');
    var btn = $('themeToggle');
    if (btn) {
      var label = next === 'dark' ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro';
      btn.setAttribute('aria-label', label);
      btn.title = label;
    }
    sparks.forEach(function (spark) { spark.draw(); });
  }

  function initTheme() {
    applyTheme(root.dataset.theme === 'light' ? 'light' : 'dark', false);
    var btn = $('themeToggle');
    if (btn) {
      btn.addEventListener('click', function () {
        applyTheme(root.dataset.theme === 'dark' ? 'light' : 'dark', true);
      });
    }
    var onSystemChange = function (event) {
      if (!storedTheme()) applyTheme(event.matches ? 'light' : 'dark', false);
    };
    if (typeof systemTheme.addEventListener === 'function') systemTheme.addEventListener('change', onSystemChange);
    else if (typeof systemTheme.addListener === 'function') systemTheme.addListener(onSystemChange);
  }

  /* ------------------------------------------------------------------ */
  /* Detección de dispositivo                                            */
  /* ------------------------------------------------------------------ */

  function parseBrowser(ua) {
    var m;
    if ((m = ua.match(/Edg\/([\d.]+)/))) return ['Edge', m[1]];
    if ((m = ua.match(/OPR\/([\d.]+)/))) return ['Opera', m[1]];
    if ((m = ua.match(/SamsungBrowser\/([\d.]+)/))) return ['Samsung Internet', m[1]];
    if ((m = ua.match(/Firefox\/([\d.]+)/))) return ['Firefox', m[1]];
    if ((m = ua.match(/FxiOS\/([\d.]+)/))) return ['Firefox', m[1]];
    if ((m = ua.match(/CriOS\/([\d.]+)/))) return ['Chrome', m[1]];
    if ((m = ua.match(/Chrome\/([\d.]+)/))) return ['Chrome', m[1]];
    if ((m = ua.match(/Version\/([\d.]+).*Safari/))) return ['Safari', m[1]];
    if (/Safari/.test(ua)) return ['Safari', ''];
    return ['Navegador', ''];
  }

  function parseOS(ua) {
    var m;
    if (/iPhone|iPad|iPod/.test(ua)) {
      m = ua.match(/OS (\d+)[._](\d+)(?:[._](\d+))?/);
      return ['iOS', m ? [m[1], m[2], m[3]].filter(Boolean).join('.') : ''];
    }
    if (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1) return ['iPadOS', ''];
    if (/Android/.test(ua)) {
      m = ua.match(/Android\s+([\d.]+)/);
      return ['Android', m ? m[1] : ''];
    }
    if (/Mac OS X/.test(ua)) {
      m = ua.match(/Mac OS X\s+([\d_.]+)/);
      return ['macOS', m ? m[1].replace(/_/g, '.') : ''];
    }
    if (/Windows NT 10/.test(ua)) return ['Windows', '10/11'];
    if (/Windows NT 6\.3/.test(ua)) return ['Windows', '8.1'];
    if (/Windows NT 6\.1/.test(ua)) return ['Windows', '7'];
    if (/CrOS/.test(ua)) return ['ChromeOS', ''];
    if (/Linux/.test(ua)) return ['Linux', ''];
    return ['Sistema', ''];
  }

  function guessDevice(ua) {
    if (/iPhone/.test(ua)) return 'iPhone';
    if (/iPad/.test(ua)) return 'iPad';
    if (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1) return 'iPad';
    if (/Android/.test(ua)) {
      var m = ua.match(/Android[^;)]*;\s*([^;)]+)/);
      if (m) {
        var model = m[1].split(' Build/')[0].trim();
        if (model && model !== 'K' && model !== 'wv' && model !== 'Mobile') return model;
      }
      return 'Android';
    }
    if (/Macintosh|Mac OS X/.test(ua)) return 'Mac';
    if (/CrOS/.test(ua)) return 'Chromebook';
    if (/Windows/.test(ua)) return 'PC Windows';
    if (/Linux/.test(ua)) return 'Equipo Linux';
    return 'Dispositivo';
  }

  function readUA() {
    var ua = navigator.userAgent || '';
    var browser = parseBrowser(ua);
    var os = parseOS(ua);
    state.info.os = os[0];
    state.info.osVersion = os[1];
    state.info.browser = browser[0];
    state.info.browserVersion = browser[1] ? browser[1].split('.')[0] : '';
    state.info.device = guessDevice(ua);
    state.info.ua = ua;
  }

  function renderHero() {
    var info = state.info;
    var name = info.model || info.device || 'Dispositivo';
    setText('deviceName', name);
    setText('deviceSub', [
      info.os ? (info.osVersion ? info.os + ' ' + info.osVersion : info.os) : '',
      info.browser ? (info.browserVersion ? info.browser + ' ' + info.browserVersion : info.browser) : '',
      window.innerWidth + ' × ' + window.innerHeight + ' CSS'
    ].filter(Boolean).join(' · '));
    document.title = name + ' — Lumen';

    var box = $('heroTags');
    if (box) {
      box.textContent = '';
      var tags = [
        navigator.maxTouchPoints > 0 ? 'Pantalla táctil' : 'Ratón y teclado',
        window.matchMedia('(display-mode: standalone)').matches ? 'Instalada' : 'En el navegador',
        navigator.onLine ? 'En línea' : 'Sin conexión'
      ];
      tags.forEach(function (text) {
        var tag = makeEl('span', 'tag');
        tag.appendChild(makeEl('span', 'dot'));
        tag.appendChild(document.createTextNode(text));
        box.appendChild(tag);
      });
    }
  }

  /* ------------------------------------------------------------------ */
  /* Hardware                                                            */
  /* ------------------------------------------------------------------ */

  function initHardware() {
    var cores = navigator.hardwareConcurrency;
    setVal('hw-cores', cores || null, 'navigator.hardwareConcurrency no está disponible.');
    setMetric('m-cpu', 'm-cpu-unit', 'm-cpu-sub', cores || null, 'núcleos', 'Núcleos lógicos expuestos por el navegador');

    setVal('hw-arch', null, 'Requiere User-Agent Client Hints de alta entropía (Chromium).');
    setVal('hw-platform', navigator.platform || null, 'navigator.platform no está disponible.');
    setVal('hw-devmem', navigator.deviceMemory ? numberFmt.format(navigator.deviceMemory) + ' GB aprox.' : null,
      'navigator.deviceMemory no está disponible en este navegador.');

    var mem = safe(function () { return performance.memory; });
    state.memory.supported = !!(mem && Number.isFinite(mem.jsHeapSizeLimit));
    if (mem && Number.isFinite(mem.usedJSHeapSize) && Number.isFinite(mem.jsHeapSizeLimit)) {
      var used = mem.usedJSHeapSize;
      var limit = mem.jsHeapSizeLimit;
      var usedPct = clamp((used / limit) * 100, 0, 100);
      setVal('hw-heap', bytes(used));
      setVal('mem-used', bytes(used));
      setVal('mem-limit', bytes(limit));
      setVal('mem-free', bytes(Math.max(0, limit - used)));
      setText('m-ram', numberFmt.format(Number((used / 1048576).toFixed(used < 10485760 ? 1 : 0))));
      setText('m-ram-unit', 'MB');
      setText('m-ram-sub', 'Heap JS · límite ' + bytes(limit));
      setBar('memBarFill', usedPct);
    } else {
      setVal('hw-heap', null, 'performance.memory no existe en este navegador.');
      setVal('mem-used', null, 'performance.memory no existe en este navegador.');
      setVal('mem-limit', null, 'performance.memory no existe en este navegador.');
      setVal('mem-free', null, 'performance.memory no existe en este navegador.');
      setMetric('m-ram', 'm-ram-unit', 'm-ram-sub', null, '', 'No disponible · heap JS no expuesto');
      setText('live-ram', 'No disponible');
    }

    setVal('mem-device', navigator.deviceMemory ? numberFmt.format(navigator.deviceMemory) + ' GB aprox.' : null,
      'navigator.deviceMemory no está disponible en este navegador.');

    initGPU();
    initDisplay();
    estimateRefreshRate();
  }

  function initGPU() {
    var gl = null;
    try {
      var canvas = document.createElement('canvas');
      gl = canvas.getContext('webgl2') || canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
      state.webgl2 = !!(gl && typeof WebGL2RenderingContext !== 'undefined' && gl instanceof WebGL2RenderingContext);
      state.hasWebGL = !!gl;
    } catch (e) { gl = null; }

    if (!gl) {
      ['gpu-vendor', 'gpu-renderer', 'gpu-api', 'gpu-maxtex'].forEach(function (id) {
        setVal(id, null, 'WebGL no está disponible o está bloqueado en este navegador.');
      });
      return;
    }

    var vendor = null;
    var renderer = null;
    try {
      var debug = gl.getExtension('WEBGL_debug_renderer_info');
      if (debug) {
        vendor = gl.getParameter(debug.UNMASKED_VENDOR_WEBGL);
        renderer = gl.getParameter(debug.UNMASKED_RENDERER_WEBGL);
      }
    } catch (e) { /* extensión bloqueada */ }

    if (!vendor) vendor = gl.getParameter(gl.VENDOR);
    if (!renderer) renderer = gl.getParameter(gl.RENDERER);

    setVal('gpu-vendor', vendor, 'El renderer sin enmascarar está bloqueado; se muestra el valor genérico.');
    setVal('gpu-renderer', renderer, 'Algunos navegadores ocultan el renderer real por privacidad.');
    var version = gl.getParameter(gl.VERSION) || '';
    setVal('gpu-api', version.indexOf('WebGL 2') === 0 ? 'WebGL 2.0' : 'WebGL 1.0');
    var maxTex = gl.getParameter(gl.MAX_TEXTURE_SIZE);
    setVal('gpu-maxtex', Number.isFinite(maxTex) ? intFmt.format(maxTex) + ' px' : null);

    initWebGPU();
  }

  function initWebGPU() {
    if (!('gpu' in navigator) || !navigator.gpu) {
      setVal('gpu-webgpu', null, 'WebGPU no está disponible en este navegador.');
      setVal('gpu-buffer', null, 'WebGPU no está disponible en este navegador.');
      return;
    }
    setText('gpu-webgpu', 'Solicitando adaptador…');
    navigator.gpu.requestAdapter().then(function (adapter) {
      if (!adapter) {
        setVal('gpu-webgpu', null, 'No se encontró un adaptador WebGPU compatible.');
        setVal('gpu-buffer', null, 'No se encontró un adaptador WebGPU compatible.');
        return;
      }
      var info = adapter.info || null;
      if (!info && typeof adapter.requestAdapterInfo === 'function') {
        return adapter.requestAdapterInfo().then(function (legacyInfo) {
          renderAdapter(adapter, legacyInfo);
        }).catch(function () { renderAdapter(adapter, null); });
      }
      renderAdapter(adapter, info);
      return null;
    }).catch(function () {
      setVal('gpu-webgpu', null, 'La solicitud de adaptador WebGPU falló.');
      setVal('gpu-buffer', null, 'La solicitud de adaptador WebGPU falló.');
    });
  }

  function renderAdapter(adapter, info) {
    var parts = [];
    if (info) {
      [info.vendor, info.architecture, info.device, info.description].forEach(function (part) {
        if (part && parts.indexOf(part) === -1) parts.push(String(part));
      });
    }
    setVal('gpu-webgpu', parts.length ? parts.join(' · ') : 'Adaptador disponible');
    var maxBuffer = adapter.limits && adapter.limits.maxBufferSize;
    setVal('gpu-buffer', bytes(maxBuffer));
  }

  function orientationLabel() {
    var type = safe(function () { return screen.orientation && screen.orientation.type; });
    var map = {
      'portrait-primary': 'Vertical',
      'portrait-secondary': 'Vertical invertida',
      'landscape-primary': 'Horizontal',
      'landscape-secondary': 'Horizontal invertida'
    };
    if (type && map[type]) return map[type];
    var legacy = window.orientation;
    if (typeof legacy === 'number') return Math.abs(legacy) === 90 ? 'Horizontal' : 'Vertical';
    return null;
  }

  function initDisplay() {
    var sw = screen.width || window.innerWidth;
    var sh = screen.height || window.innerHeight;
    var dpr = window.devicePixelRatio || 1;
    setVal('scr-css', sw + ' × ' + sh + ' px');
    setVal('scr-phys', Math.round(sw * dpr) + ' × ' + Math.round(sh * dpr) + ' px');
    setVal('scr-dpr', (dpr % 1 === 0 ? dpr : Number(dpr.toFixed(2))).toString().replace('.', ',') + '×');
    setVal('scr-depth', screen.colorDepth ? screen.colorDepth + ' bits' : null, 'screen.colorDepth no está disponible.');

    var gamut = null;
    if (window.matchMedia('(color-gamut: rec2020)').matches) gamut = 'Rec. 2020';
    else if (window.matchMedia('(color-gamut: p3)').matches) gamut = 'Display P3';
    else if (window.matchMedia('(color-gamut: srgb)').matches) gamut = 'sRGB';
    setVal('scr-gamut', gamut, 'Este navegador no expone la gama de color.');

    var hdr = null;
    if (window.matchMedia('(dynamic-range: high)').matches) hdr = 'Alto rango dinámico';
    else if (window.matchMedia('(dynamic-range: standard)').matches) hdr = 'Rango estándar';
    setVal('scr-hdr', hdr, 'Este navegador no expone el rango dinámico.');

    setVal('scr-orient', orientationLabel(), 'screen.orientation no está disponible.');
    setVal('scr-touch', navigator.maxTouchPoints > 0 ? intFmt.format(navigator.maxTouchPoints) + ' puntos' : 'Ninguno');
    setVal('scr-refresh', 'Midiendo…');

    if (screen.orientation && typeof screen.orientation.addEventListener === 'function') {
      screen.orientation.addEventListener('change', function () { setVal('scr-orient', orientationLabel()); });
    }
  }

  function estimateRefreshRate() {
    if (typeof window.requestAnimationFrame !== 'function') {
      setVal('scr-refresh', null, 'requestAnimationFrame no está disponible.');
      return;
    }
    var deltas = [];
    var start = performance.now();
    var last = start;
    function frame(now) {
      var d = now - last;
      last = now;
      if (d > 0 && d < 100) deltas.push(d);
      if (now - start < 700) {
        requestAnimationFrame(frame);
        return;
      }
      if (deltas.length < 5) {
        setVal('scr-refresh', null, 'No se pudo medir el refresco de pantalla.');
        return;
      }
      deltas.sort(function (a, b) { return a - b; });
      var median = deltas[Math.floor(deltas.length / 2)];
      var hz = 1000 / median;
      var common = [60, 75, 90, 100, 120, 144, 165, 240];
      var snapped = null;
      common.forEach(function (c) {
        if (Math.abs(hz - c) / c < 0.08) snapped = c;
      });
      hz = snapped || hz;
      state.live.refresh = Math.round(hz);
      setVal('scr-refresh', '≈ ' + Math.round(hz) + ' Hz');
    }
    requestAnimationFrame(frame);
  }

  /* ------------------------------------------------------------------ */
  /* Almacenamiento                                                      */
  /* ------------------------------------------------------------------ */

  function initStorage() {
    if (!navigator.storage || typeof navigator.storage.estimate !== 'function') {
      var reason = 'La Storage API no está disponible en este navegador.';
      ['st-used', 'st-quota', 'st-free', 'st-persist', 'st-details'].forEach(function (id) { setVal(id, null, reason); });
      setText('st-pct', '—');
      setText('st-caption', 'No disponible');
      setMetric('m-store', 'm-store-unit', 'm-store-sub', null, '', 'No disponible · Storage API');
      return;
    }

    navigator.storage.estimate().then(function (estimate) {
      var used = estimate.usage;
      var quota = estimate.quota;
      if (Number.isFinite(used) && Number.isFinite(quota) && quota > 0) {
        var pct = clamp((used / quota) * 100, 0, 100);
        state.storage.used = used;
        state.storage.quota = quota;
        state.storage.pct = pct;
        setVal('st-used', bytes(used));
        setVal('st-quota', bytes(quota));
        setVal('st-free', bytes(Math.max(0, quota - used)));
        setText('st-pct', pctText(pct, pct < 1 ? 1 : 0));
        setText('st-caption', bytes(used) + ' de ' + bytes(quota));
        setBar('stBarFill', pct);
        setMetric('m-store', 'm-store-unit', 'm-store-sub', pctText(pct, pct < 1 ? 1 : 0), '%', bytes(used) + ' de ' + bytes(quota));
      } else if (Number.isFinite(used)) {
        setVal('st-used', bytes(used));
        setVal('st-quota', null, 'El navegador no revela la cuota de almacenamiento.');
        setMetric('m-store', 'm-store-unit', 'm-store-sub', null, '', 'Usado: ' + bytes(used));
      } else {
        throw new Error('Sin datos');
      }

      var details = estimate.usageDetails;
      if (details && Object.keys(details).length) {
        var labels = {
          indexedDB: 'IndexedDB',
          caches: 'Caché',
          serviceWorkerRegistrations: 'Service Worker',
          fileSystem: 'Archivos'
        };
        var parts = Object.keys(details).map(function (key) {
          return (labels[key] || key) + ': ' + (bytes(details[key]) || '0 B');
        });
        setVal('st-details', parts.join(' · '));
      } else {
        setVal('st-details', null, 'El navegador no expone el desglose por tipo de almacenamiento.');
      }

      if (typeof navigator.storage.persisted === 'function') {
        return navigator.storage.persisted().then(function (persisted) {
          state.storage.persisted = persisted;
          setVal('st-persist', persisted ? 'Sí' : 'No');
        }).catch(function () {
          setVal('st-persist', null, 'No se pudo consultar el estado persistente.');
        });
      }
      setVal('st-persist', null, 'navigator.storage.persisted no está disponible.');
      return null;
    }).catch(function () {
      var reason = 'El navegador bloqueó la estimación de almacenamiento.';
      ['st-used', 'st-quota', 'st-free', 'st-persist', 'st-details'].forEach(function (id) { setVal(id, null, reason); });
      setText('st-caption', 'No disponible');
      setMetric('m-store', 'm-store-unit', 'm-store-sub', null, '', 'No disponible · Storage API');
    });
  }

  /* ------------------------------------------------------------------ */
  /* Batería                                                             */
  /* ------------------------------------------------------------------ */

  function duration(seconds) {
    if (!Number.isFinite(seconds) || seconds <= 0) return null;
    var minutes = Math.round(seconds / 60);
    if (minutes < 1) return 'Menos de 1 min';
    if (minutes < 60) return minutes + ' min';
    var hours = Math.floor(minutes / 60);
    var rest = minutes % 60;
    return hours + ' h' + (rest ? ' ' + rest + ' min' : '');
  }

  function batteryUnavailable(reason) {
    setText('bat-big', '—');
    setText('bat-badge', 'No disponible');
    var badge = $('bat-badge');
    if (badge) badge.className = 'state-pill';
    setBar('batBarFill', 0);
    setVal('bat-state', null, reason);
    setVal('bat-charge-time', null, reason);
    setVal('bat-discharge-time', null, reason);
    setVal('bat-health', null, 'La salud de la batería requiere una API nativa; el navegador no la expone.');
    setVal('bat-temp', null, 'Temperatura y voltaje no se exponen al navegador.');
    setMetric('m-bat', 'm-bat-unit', 'm-bat-sub', null, '', 'No disponible · Battery API');
    setText('live-battery', 'No disponible');
  }

  function initBattery() {
    if (typeof navigator.getBattery !== 'function') {
      batteryUnavailable('La Battery Status API no está disponible en este navegador.');
      return;
    }
    navigator.getBattery().then(function (battery) {
      state.battery.supported = true;
      state.battery.obj = battery;
      updateBattery();
      ['levelchange', 'chargingchange', 'chargingtimechange', 'dischargingtimechange'].forEach(function (event) {
        battery.addEventListener(event, updateBattery);
      });
    }).catch(function () {
      batteryUnavailable('El navegador denegó el acceso al estado de la batería.');
    });
  }

  function updateBattery() {
    var battery = state.battery.obj;
    if (!battery) return;
    var pct = clamp(Math.round((battery.level || 0) * 100), 0, 100);
    var charging = !!battery.charging;
    state.battery.level = pct;
    state.battery.charging = charging;

    setText('bat-big', String(pct));
    setText('bat-badge', charging ? 'Cargando' : pct >= 100 ? 'Completa' : 'En uso');
    var badge = $('bat-badge');
    if (badge) badge.className = 'state-pill ' + (charging ? 'ok' : pct <= 20 ? 'warn' : '');

    setBar('batBarFill', pct);
    setVal('bat-state', charging ? 'Cargando' : 'Descargando');
    setVal('bat-charge-time', charging ? (duration(battery.chargingTime) || 'No estimado') : 'No aplica (sin cargar)');
    setVal('bat-discharge-time', !charging ? (duration(battery.dischargingTime) || 'No estimado') : 'No aplica (cargando)');
    setVal('bat-health', null, 'La salud de la batería requiere una API nativa; el navegador no la expone.');
    setVal('bat-temp', null, 'Temperatura y voltaje no se exponen al navegador.');

    setMetric('m-bat', 'm-bat-unit', 'm-bat-sub', pct, '%', charging ? 'Cargando' : 'En uso');
    setText('live-battery', pct + '%');
  }

  /* ------------------------------------------------------------------ */
  /* Red                                                                 */
  /* ------------------------------------------------------------------ */

  function connectionObject() {
    return navigator.connection || navigator.mozConnection || navigator.webkitConnection || null;
  }

  function networkTypeLabel(type) {
    var map = {
      wifi: 'Wi-Fi',
      cellular: 'Móvil',
      ethernet: 'Ethernet',
      bluetooth: 'Bluetooth',
      wimax: 'WiMAX',
      none: 'Sin conexión',
      other: 'Otra',
      unknown: 'Desconocida'
    };
    return type ? (map[type] || type) : null;
  }

  function updateNetwork() {
    var online = navigator.onLine;
    setVal('net-status', online ? 'En línea' : 'Sin conexión');
    var pill = $('net-pill');
    if (pill) {
      pill.textContent = online ? 'Conectado' : 'Desconectado';
      pill.className = 'state-pill ' + (online ? 'ok' : 'danger');
    }

    var connection = connectionObject();
    state.network.connection = connection;

    if (!connection) {
      var reason = 'La Network Information API no está disponible aquí; no la exponen Safari, Firefox ni algunos derivados de Chromium.';
      ['net-type', 'net-effective', 'net-downlink', 'net-rtt', 'net-savedata', 'net-live', 'net-origin'].forEach(function (id) {
        setVal(id, null, reason);
      });
      setText('qualityValue', '—');
      setText('qualityLabel', online ? 'Sin estimación de calidad' : 'Sin conexión');
      setBar('netBarFill', 0);
      return;
    }

    setVal('net-type', networkTypeLabel(connection.type), 'El navegador no revela el tipo físico de conexión.');
    var effective = connection.effectiveType;
    setVal('net-effective', effective ? effective.toUpperCase() : null, 'No expuesto por este navegador.');
    setVal('net-downlink', Number.isFinite(connection.downlink) ? numberFmt.format(connection.downlink) + ' Mb/s aprox.' : null,
      'El navegador no expone el ancho de banda estimado.');
    setVal('net-rtt', Number.isFinite(connection.rtt) ? connection.rtt + ' ms' : null,
      'El navegador no expone la latencia estimada.');
    setVal('net-savedata', typeof connection.saveData === 'boolean' ? (connection.saveData ? 'Activado' : 'Desactivado') : null,
      'El navegador no expone el ahorro de datos.');
    setVal('net-live', 'Escuchando');
    setVal('net-origin', 'Network Information API');

    var widths = { 'slow-2g': 12, '2g': 28, '3g': 55, '4g': 86 };
    var pct = widths[effective];
    if (pct === undefined) {
      setText('qualityValue', '—');
      setText('qualityLabel', 'Sin estimación');
      setBar('netBarFill', 0);
      return;
    }
    if (Number.isFinite(connection.downlink)) {
      pct = clamp(30 + Math.min(connection.downlink, 40) * 1.6, 10, 100);
    }
    var label = pct >= 80 ? 'Excelente' : pct >= 55 ? 'Buena' : pct >= 30 ? 'Limitada' : 'Mala';
    if (!online) { pct = 0; label = 'Sin conexión'; }
    setText('qualityValue', effective.toUpperCase());
    setText('qualityLabel', label);
    setBar('netBarFill', pct);
  }

  function initNetwork() {
    updateNetwork();
    window.addEventListener('online', function () { updateNetwork(); renderHero(); });
    window.addEventListener('offline', function () { updateNetwork(); renderHero(); });
    var connection = connectionObject();
    if (connection && typeof connection.addEventListener === 'function') {
      connection.addEventListener('change', updateNetwork);
    }
  }

  /* ------------------------------------------------------------------ */
  /* Navegador                                                           */
  /* ------------------------------------------------------------------ */

  function browserRows() {
    var ua = navigator.userAgent;
    setVal('br-ua', ua, 'El navegador no expone un user agent.');
    setVal('br-legacy', navigator.platform || null, 'navigator.platform no está disponible.');

    var languages = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language];
    setVal('br-lang', languages.filter(Boolean).join(', '));

    var timezone = safe(function () { return Intl.DateTimeFormat().resolvedOptions().timeZone; });
    var offset = -new Date().getTimezoneOffset();
    var sign = offset >= 0 ? '+' : '−';
    var abs = Math.abs(offset);
    var hh = String(Math.floor(abs / 60)).padStart(2, '0');
    var mm = String(abs % 60).padStart(2, '0');
    setVal('br-tz', timezone ? timezone + ' (UTC' + sign + hh + ':' + mm + ')' : null,
      'El navegador no expone la zona horaria.');

    setVal('br-cookies', navigator.cookieEnabled ? 'Activadas' : 'Bloqueadas');
    var dnt = navigator.doNotTrack || window.doNotTrack || navigator.msDoNotTrack;
    setVal('br-dnt', dnt === '1' || dnt === 'yes' ? 'Activado' : dnt === '0' ? 'Desactivado' : 'No especificado');
    setVal('br-secure', window.isSecureContext ? 'Sí' : 'No', 'Las APIs PWA requieren HTTPS o localhost.');

    setVal('br-platform', null, 'Requiere User-Agent Client Hints (Chromium).');
    setVal('br-platver', null, 'Requiere datos de alta entropía de User-Agent Client Hints.');
    setVal('br-model', null, 'El navegador no revela el modelo del dispositivo.');
    setVal('br-arch', null, 'Requiere datos de alta entropía de User-Agent Client Hints.');
    setVal('br-bits', null, 'Requiere datos de alta entropía de User-Agent Client Hints.');
    setVal('br-brands', null, 'User-Agent Client Hints solo está en navegadores Chromium.');
    setVal('br-fullver', null, 'User-Agent Client Hints solo está en navegadores Chromium.');
  }

  function simplifyBrand(brand) {
    return String(brand)
      .replace('Google ', '')
      .replace('Microsoft ', '')
      .replace(' Software', '')
      .replace('Internet Explorer', 'Edge')
      .trim();
  }

  function initUAData() {
    var uaData = navigator.userAgentData;
    if (!uaData) return;

    setVal('br-platform', uaData.platform || null, 'El navegador no expone la plataforma.');
    if (Array.isArray(uaData.brands) && uaData.brands.length) {
      setVal('br-brands', uaData.brands.map(function (b) { return b.brand + ' ' + b.version; }).join(' · '));
    }

    var highEntropy = ['architecture', 'bitness', 'model', 'platformVersion', 'uaFullVersion', 'fullVersionList', 'wow64'];
    if (typeof uaData.getHighEntropyValues !== 'function') return;

    uaData.getHighEntropyValues(highEntropy).then(function (high) {
      setVal('br-platver', high.platformVersion || null, 'El navegador no revela la versión del sistema.');
      var model = high.model && high.model !== 'K' ? high.model : null;
      setVal('br-model', model, 'El navegador no revela el modelo del dispositivo.');
      if (model) {
        state.info.model = model;
        renderHero();
      }

      var arch = high.architecture || null;
      if (arch) {
        var archText = arch + (high.bitness ? ' · ' + high.bitness + ' bits' : '') + (high.wow64 ? ' · WOW64' : '');
        setVal('br-arch', arch);
        setVal('br-bits', high.bitness ? high.bitness + ' bits' : null, 'El navegador no revela el ancho de palabra.');
        setVal('hw-arch', archText);
      } else {
        setVal('br-bits', null, 'El navegador no revela el ancho de palabra.');
      }

      if (Array.isArray(high.fullVersionList) && high.fullVersionList.length) {
        setVal('br-brands', high.fullVersionList.map(function (b) { return b.brand + ' ' + b.version; }).join(' · '));
        var preferred = high.fullVersionList.filter(function (b) {
          return !/Not.?A.?Brand|Chromium/i.test(b.brand);
        })[0];
        if (preferred) {
          state.info.browser = simplifyBrand(preferred.brand);
          state.info.browserVersion = String(preferred.version).split('.')[0];
          renderHero();
        }
      }
      setVal('br-fullver', high.uaFullVersion || null, 'El navegador no revela la versión completa.');

      if (uaData.platform === 'macOS' && high.platformVersion) {
        state.info.os = 'macOS';
        state.info.osVersion = high.platformVersion;
        renderHero();
      } else if (uaData.platform === 'Windows' && high.platformVersion) {
        state.info.osVersion = high.platformVersion;
        renderHero();
      }
    }).catch(function () {
      setVal('br-platver', null, 'El navegador denegó los datos de alta entropía.');
      setVal('br-model', null, 'El navegador denegó los datos de alta entropía.');
      setVal('br-arch', null, 'El navegador denegó los datos de alta entropía.');
      setVal('br-bits', null, 'El navegador denegó los datos de alta entropía.');
      setVal('br-fullver', null, 'El navegador denegó los datos de alta entropía.');
    });
  }

  function initMedia() {
    if (!navigator.mediaDevices || typeof navigator.mediaDevices.enumerateDevices !== 'function') {
      setVal('br-media', null, 'La MediaDevices API no está disponible en este navegador.');
      return;
    }
    navigator.mediaDevices.enumerateDevices().then(function (devices) {
      var audioIn = devices.filter(function (d) { return d.kind === 'audioinput'; }).length;
      var audioOut = devices.filter(function (d) { return d.kind === 'audiooutput'; }).length;
      var videoIn = devices.filter(function (d) { return d.kind === 'videoinput'; }).length;
      setVal('br-media', devices.length + ' · ' + audioIn + ' audio · ' + audioOut + ' salida · ' + videoIn + ' vídeo');
    }).catch(function () {
      setVal('br-media', null, 'El navegador bloqueó la enumeración de dispositivos.');
    });
  }

  var PERMISSIONS = [
    ['geolocation', 'Ubicación'],
    ['notifications', 'Notificaciones'],
    ['camera', 'Cámara'],
    ['microphone', 'Micrófono'],
    ['clipboard-read', 'Portapapeles (lectura)'],
    ['clipboard-write', 'Portapapeles (escritura)'],
    ['persistent-storage', 'Almacenamiento persistente'],
    ['push', 'Push'],
    ['midi', 'MIDI'],
    ['background-sync', 'Sincronización en segundo plano'],
    ['accelerometer', 'Acelerómetro'],
    ['gyroscope', 'Giroscopio'],
    ['magnetometer', 'Magnetómetro'],
    ['screen-wake-lock', 'Mantener pantalla activa']
  ];

  function initPermissions() {
    var grid = $('permGrid');
    var count = $('permCount');
    if (!grid) return;

    if (!navigator.permissions || typeof navigator.permissions.query !== 'function') {
      if (count) count.textContent = 'No consultable';
      var note = makeEl('p', 'card-note', 'La Permissions API no está disponible en este navegador; los permisos no se pueden consultar sin pedirlos.');
      grid.parentNode.appendChild(note);
      return;
    }

    var queries = PERMISSIONS.map(function (entry) {
      var name = entry[0];
      var label = entry[1];
      return navigator.permissions.query({ name: name }).then(function (status) {
        return { name: name, label: label, state: status.state };
      }).catch(function () {
        return { name: name, label: label, state: 'unsupported' };
      });
    });

    Promise.all(queries).then(function (results) {
      var granted = 0;
      grid.textContent = '';
      results.forEach(function (result) {
        var stateClass = result.state === 'granted' ? 'granted'
          : result.state === 'denied' ? 'denied'
            : result.state === 'prompt' ? 'prompt' : '';
        if (result.state === 'granted') granted += 1;

        var item = makeEl('div', 'perm ' + stateClass);
        item.dataset.perm = result.name;
        item.dataset.state = result.state;
        var labelText = {
          granted: 'Concedido',
          denied: 'Bloqueado',
          prompt: 'Preguntar',
          unsupported: 'No consultable'
        }[result.state] || result.state;
        item.title = result.label + ': ' + labelText;
        item.appendChild(makeEl('span', 'perm-dot'));
        item.appendChild(makeEl('span', 'perm-name', result.label));
        item.appendChild(makeEl('span', 'perm-state', labelText));
        grid.appendChild(item);
      });
      if (count) count.textContent = granted + ' / ' + results.length + ' concedidos';
    }).catch(function () {
      if (count) count.textContent = 'No disponible';
    });
  }

  function hasWebGL2() {
    if (state.hasWebGL) return state.webgl2;
    try {
      var context = document.createElement('canvas').getContext('webgl2');
      state.webgl2 = !!context;
      state.hasWebGL = true;
      return state.webgl2;
    } catch (e) {
      return false;
    }
  }

  function initApis() {
    var grid = $('apiGrid');
    var count = $('apiCount');
    if (!grid) return;

    var apis = [
      ['WebGL 2', hasWebGL2],
      ['WebGPU', function () { return 'gpu' in navigator; }],
      ['WebAssembly', function () { return typeof WebAssembly === 'object'; }],
      ['Service Worker', function () { return 'serviceWorker' in navigator; }],
      ['Web Share', function () { return typeof navigator.share === 'function'; }],
      ['Portapapeles', function () { return !!(navigator.clipboard && navigator.clipboard.writeText); }],
      ['Bluetooth', function () { return 'bluetooth' in navigator; }],
      ['USB', function () { return 'usb' in navigator; }],
      ['Serial', function () { return 'serial' in navigator; }],
      ['NFC', function () { return 'NDEFReader' in window; }],
      ['Gamepad', function () { return typeof navigator.getGamepads === 'function'; }],
      ['Vibración', function () { return typeof navigator.vibrate === 'function'; }],
      ['Wake Lock', function () { return 'wakeLock' in navigator; }],
      ['Notificaciones', function () { return 'Notification' in window; }],
      ['Geolocalización', function () { return 'geolocation' in navigator; }],
      ['MediaDevices', function () { return !!(navigator.mediaDevices && navigator.mediaDevices.enumerateDevices); }],
      ['WebRTC', function () { return 'RTCPeerConnection' in window; }],
      ['File System Access', function () { return 'showOpenFilePicker' in window; }],
      ['WebXR', function () { return 'xr' in navigator; }],
      ['Speech Synthesis', function () { return 'speechSynthesis' in window; }],
      ['Payment Request', function () { return 'PaymentRequest' in window; }],
      ['ResizeObserver', function () { return 'ResizeObserver' in window; }],
      ['IntersectionObserver', function () { return 'IntersectionObserver' in window; }],
      ['Sensor API', function () { return 'Accelerometer' in window; }],
      ['Battery Status', function () { return typeof navigator.getBattery === 'function'; }],
      ['Network Information', function () { return !!connectionObject(); }]
    ];

    var available = 0;
    grid.textContent = '';
    apis.forEach(function (entry) {
      var ok = false;
      try { ok = !!entry[1](); } catch (e) { ok = false; }
      if (ok) available += 1;
      var item = makeEl('div', 'api ' + (ok ? 'ok' : 'no'));
      item.dataset.api = entry[0];
      item.dataset.state = ok ? 'available' : 'unavailable';
      item.title = entry[0] + (ok ? ': disponible' : ': no disponible');
      var icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      icon.setAttribute('class', 'ico');
      icon.setAttribute('aria-hidden', 'true');
      var use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
      use.setAttribute('href', ok ? '#i-check' : '#i-x');
      icon.appendChild(use);
      item.appendChild(icon);
      item.appendChild(makeEl('span', null, entry[0]));
      grid.appendChild(item);
    });
    if (count) count.textContent = available + ' / ' + apis.length + ' disponibles';
  }

  /* ------------------------------------------------------------------ */
  /* Sensores                                                            */
  /* ------------------------------------------------------------------ */

  var sensorHandlers = { motion: null, orientation: null };
  var sensorTimeout = null;

  function sensorStatus(message, kind) {
    var el = $('sensorStatus');
    if (!el) return;
    el.textContent = message;
    el.className = 'sensor-status' + (kind ? ' ' + kind : '');
  }

  function setMeter(id, value, maxAbs) {
    var el = $(id);
    if (!el) return;
    var pct = clamp((value / maxAbs) * 100, -100, 100);
    el.style.left = (pct >= 0 ? 50 : 50 + pct / 2) + '%';
    el.style.width = Math.abs(pct) / 2 + '%';
  }

  function markSensorData(kind) {
    state.sensors[kind] = true;
    clearTimeout(sensorTimeout);
    if (!state.sensors.motion || !state.sensors.orientation) {
      sensorStatus('Sensores activos · esperando más ejes…', 'on');
    } else {
      sensorStatus('Sensores activos · actualizando en vivo', 'on');
    }
    if (state.sensors.motion && state.sensors.orientation) {
      sensorStatus('Sensores activos · actualizando en vivo', 'on');
    }
  }

  function accelValue(v) {
    return Number.isFinite(v) ? numberFmt.format(Number(v.toFixed(2))) : '—';
  }

  function onMotion(event) {
    var acc = event.acceleration || event.accelerationIncludingGravity;
    var rotation = event.rotationRate;
    var got = false;

    if (acc) {
      ['x', 'y', 'z'].forEach(function (axis) {
        var value = acc[axis];
        if (Number.isFinite(value)) {
          got = true;
          state.sensors.last.acc[axis] = value;
          setText('acc-' + axis, accelValue(value));
          setMeter('acc' + axis + 'Bar', value, 20);
        }
      });
    }

    if (rotation) {
      [['alpha', 'a'], ['beta', 'b'], ['gamma', 'c']].forEach(function (pair) {
        var value = rotation[pair[0]];
        if (Number.isFinite(value)) {
          got = true;
          state.sensors.last.gyro[pair[1]] = value;
          setText('gyro-' + pair[1], accelValue(value));
          setMeter('gyro' + pair[1] + 'Bar', value, 360);
        }
      });
    }

    if (got) markSensorData('motion');
  }

  var DIRECTIONS = ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'];

  function onOrientation(event) {
    var alpha = event.alpha;
    var beta = event.beta;
    var gamma = event.gamma;
    var got = false;

    if (Number.isFinite(alpha)) {
      got = true;
      state.sensors.last.ori.alpha = alpha;
      setText('ori-a', accelValue(alpha) + '°');
      var signed = alpha > 180 ? alpha - 360 : alpha;
      setMeter('oriaBar', signed, 180);
    }
    if (Number.isFinite(beta)) {
      got = true;
      state.sensors.last.ori.beta = beta;
      setText('ori-b', accelValue(beta) + '°');
      setMeter('oribBar', beta, 180);
    }
    if (Number.isFinite(gamma)) {
      got = true;
      state.sensors.last.ori.gamma = gamma;
      setText('ori-c', accelValue(gamma) + '°');
      setMeter('oricBar', gamma, 180);
    }

    var heading = Number.isFinite(event.webkitCompassHeading) ? event.webkitCompassHeading : (Number.isFinite(alpha) ? alpha : null);
    if (heading !== null) {
      state.sensors.last.heading = heading;
      var needle = $('compassNeedle');
      if (needle) needle.style.transform = 'rotate(' + heading + 'deg)';
      setText('compassValue', Math.round(heading) + '°');
      setText('compassLabel', DIRECTIONS[Math.round(heading / 45) % 8]);
    }

    if (got) markSensorData('orientation');
  }

  function enableSensors() {
    var button = $('sensorBtn');
    if (!button) return;

    var hasMotion = typeof window.DeviceMotionEvent !== 'undefined';
    var hasOrientation = typeof window.DeviceOrientationEvent !== 'undefined';
    if (!hasMotion && !hasOrientation) {
      sensorStatus('Este navegador no expone sensores de movimiento.', 'err');
      setText('sensorNote', 'No hay DeviceMotion ni DeviceOrientation disponibles en este dispositivo.');
      button.textContent = 'No disponible';
      button.disabled = true;
      return;
    }

    button.disabled = true;
    sensorStatus('Solicitando permiso…');

    var motionAllowed = hasMotion;
    var orientationAllowed = hasOrientation;

    var askMotion = function () {
      if (!motionAllowed || typeof DeviceMotionEvent === 'undefined' || typeof DeviceMotionEvent.requestPermission !== 'function') {
        return Promise.resolve();
      }
      return DeviceMotionEvent.requestPermission().then(function (result) {
        motionAllowed = result === 'granted';
      }).catch(function () {
        motionAllowed = false;
      });
    };

    var askOrientation = function () {
      if (!orientationAllowed || typeof DeviceOrientationEvent === 'undefined' || typeof DeviceOrientationEvent.requestPermission !== 'function') {
        return Promise.resolve();
      }
      return DeviceOrientationEvent.requestPermission().then(function (result) {
        orientationAllowed = result === 'granted';
      }).catch(function () {
        orientationAllowed = false;
      });
    };

    askMotion().then(askOrientation).then(function () {
      if (!motionAllowed && !orientationAllowed) {
        sensorStatus('Permiso denegado. Actívalo en los ajustes del navegador para este sitio.', 'err');
        button.disabled = false;
        button.textContent = 'Activar sensores';
        return;
      }

      if (motionAllowed) {
        sensorHandlers.motion = onMotion;
        window.addEventListener('devicemotion', sensorHandlers.motion);
      }
      if (orientationAllowed) {
        sensorHandlers.orientation = onOrientation;
        window.addEventListener('deviceorientation', sensorHandlers.orientation);
        window.addEventListener('deviceorientationabsolute', sensorHandlers.orientation);
      }

      state.sensors.active = true;
      state.sensors.motion = false;
      state.sensors.orientation = false;
      button.disabled = false;
      button.textContent = 'Detener sensores';
      sensorStatus('Esperando datos de los sensores…');

      clearTimeout(sensorTimeout);
      sensorTimeout = setTimeout(function () {
        if (!state.sensors.motion && !state.sensors.orientation) {
          sensorStatus('El navegador no entrega datos (es habitual en escritorio).', 'err');
        } else if (!state.sensors.motion || !state.sensors.orientation) {
          sensorStatus('Solo algunos ejes están disponibles en este dispositivo.', 'err');
        }
      }, 1800);
    });
  }

  function disableSensors() {
    if (sensorHandlers.motion) window.removeEventListener('devicemotion', sensorHandlers.motion);
    if (sensorHandlers.orientation) {
      window.removeEventListener('deviceorientation', sensorHandlers.orientation);
      window.removeEventListener('deviceorientationabsolute', sensorHandlers.orientation);
    }
    sensorHandlers.motion = null;
    sensorHandlers.orientation = null;
    clearTimeout(sensorTimeout);
    state.sensors.active = false;
    var button = $('sensorBtn');
    if (button) {
      button.textContent = 'Activar sensores';
      button.disabled = false;
    }
    sensorStatus('Sensores detenidos.');
  }

  function initSensors() {
    var button = $('sensorBtn');
    if (!button) return;
    if (typeof window.DeviceMotionEvent === 'undefined' && typeof window.DeviceOrientationEvent === 'undefined') {
      button.disabled = true;
      button.textContent = 'No disponible';
      sensorStatus('Sin APIs de sensores en este navegador.', 'err');
      return;
    }
    button.addEventListener('click', function () {
      if (state.sensors.active) disableSensors();
      else enableSensors();
    });
  }

  /* ------------------------------------------------------------------ */
  /* Gráficas en vivo                                                    */
  /* ------------------------------------------------------------------ */

  function Spark(canvasId, options) {
    this.canvas = $(canvasId);
    this.ctx = this.canvas ? this.canvas.getContext('2d') : null;
    this.min = options && typeof options.min === 'number' ? options.min : 0;
    this.max = options && typeof options.max === 'number' ? options.max : 100;
    this.auto = !!(options && options.auto);
    this.maxPoints = 60;
    this.points = [];
    this.w = 0;
    this.h = 0;
    if (this.canvas && this.ctx) {
      this.resize();
      if (typeof ResizeObserver === 'function') {
        var self = this;
        new ResizeObserver(function () { self.resize(); self.draw(); }).observe(this.canvas);
      }
    }
  }

  Spark.prototype.resize = function () {
    if (!this.canvas || !this.ctx) return;
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    var width = this.canvas.clientWidth;
    var height = this.canvas.clientHeight;
    if (!width || !height) return;
    var pixelWidth = Math.round(width * dpr);
    var pixelHeight = Math.round(height * dpr);
    if (this.canvas.width !== pixelWidth || this.canvas.height !== pixelHeight) {
      this.canvas.width = pixelWidth;
      this.canvas.height = pixelHeight;
      this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    this.w = width;
    this.h = height;
  };

  Spark.prototype.push = function (value) {
    if (!Number.isFinite(value)) return;
    this.points.push(value);
    if (this.points.length > this.maxPoints) this.points.shift();
    this.draw();
  };

  Spark.prototype.draw = function () {
    if (!this.ctx || !this.w || !this.h) return;
    var ctx = this.ctx;
    var w = this.w;
    var h = this.h;
    var styles = getComputedStyle(root);
    var accent = styles.getPropertyValue('--spark').trim() || styles.getPropertyValue('--accent').trim() || '#9BE8C0';
    var border = styles.getPropertyValue('--border').trim() || '#24282F';

    ctx.clearRect(0, 0, w, h);

    ctx.save();
    ctx.globalAlpha = 0.55;
    ctx.strokeStyle = border;
    ctx.lineWidth = 1;
    [0.25, 0.5, 0.75].forEach(function (ratio) {
      var y = Math.round(h * ratio) + 0.5;
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(w, y);
      ctx.stroke();
    });
    ctx.restore();

    if (!this.points.length) return;

    var max = this.max;
    if (this.auto) {
      max = Math.max.apply(null, this.points) * 1.2;
      max = Math.max(max, this.max * 0.3);
    }
    var min = this.min;
    if (max <= min) max = min + 1;

    var stepX = this.points.length > 1 ? w / (this.points.length - 1) : 0;
    var start = 0;
    var self = this;

    function pointX(index) { return (start + index) * stepX; }
    function pointY(value) {
      var ratio = clamp((value - min) / (max - min), 0, 1);
      return h - 7 - ratio * (h - 14);
    }

    ctx.beginPath();
    this.points.forEach(function (value, index) {
      var x = pointX(index);
      var y = pointY(value);
      if (index === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });

    var gradient = ctx.createLinearGradient(0, 0, 0, h);
    gradient.addColorStop(0, accent + '14');
    gradient.addColorStop(1, accent + '00');
    ctx.lineTo(pointX(self.points.length - 1), h);
    ctx.lineTo(pointX(0), h);
    ctx.closePath();
    ctx.fillStyle = gradient;
    ctx.fill();

    ctx.beginPath();
    this.points.forEach(function (value, index) {
      var x = pointX(index);
      var y = pointY(value);
      if (index === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.strokeStyle = accent;
    ctx.lineWidth = 1.8;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.stroke();

    var lastX = pointX(this.points.length - 1);
    var lastY = pointY(this.points[this.points.length - 1]);
    ctx.beginPath();
    ctx.arc(lastX, lastY, 2.2, 0, Math.PI * 2);
    ctx.fillStyle = accent;
    ctx.fill();
  };

  var fpsSpark;
  var ramSpark;
  var batterySpark;
  var batteryDetailSpark;
  var fpsFrame = null;
  var liveTimer = null;
  var frames = 0;
  var lastFpsAt = 0;

  function fpsTick(now) {
    if (document.hidden) {
      fpsFrame = null;
      return;
    }
    frames += 1;
    if (!lastFpsAt) lastFpsAt = now;
    var elapsed = now - lastFpsAt;
    if (elapsed >= 500) {
      var fps = Math.round((frames * 1000) / elapsed);
      frames = 0;
      lastFpsAt = now;
      if (fps > 0 && fps < 500) {
        state.live.fps = fps;
        setText('live-fps', String(fps));
        if (fpsSpark) fpsSpark.push(fps);
      }
    }
    fpsFrame = requestAnimationFrame(fpsTick);
  }

  function sampleMemory() {
    var memory = safe(function () { return performance.memory; });
    if (!memory || !Number.isFinite(memory.usedJSHeapSize) || !Number.isFinite(memory.jsHeapSizeLimit) || !memory.jsHeapSizeLimit) {
      return;
    }
    var used = memory.usedJSHeapSize;
    var limit = memory.jsHeapSizeLimit;
    var percentage = clamp((used / limit) * 100, 0, 100);
    var usedMb = used / 1048576;

    setText('m-ram', numberFmt.format(Number(usedMb.toFixed(usedMb < 10 ? 1 : 0))));
    setText('m-ram-unit', 'MB');
    setText('m-ram-sub', 'Heap JS · límite ' + bytes(limit));
    setText('live-ram', bytes(used) + ' · ' + pctText(percentage, 1) + '%');
    setVal('hw-heap', bytes(used));
    setVal('mem-used', bytes(used));
    setVal('mem-limit', bytes(limit));
    setVal('mem-free', bytes(Math.max(0, limit - used)));
    setBar('memBarFill', percentage);
    if (ramSpark) ramSpark.push(usedMb);
  }

  function sampleBattery() {
    if (state.battery.level === null) return;
    if (batterySpark) batterySpark.push(state.battery.level);
    if (batteryDetailSpark) batteryDetailSpark.push(state.battery.level);
  }

  function slowSample() {
    sampleMemory();
    sampleBattery();
  }

  function startLive() {
    if (fpsFrame === null) {
      frames = 0;
      lastFpsAt = 0;
      fpsFrame = requestAnimationFrame(fpsTick);
    }
    if (liveTimer === null) {
      liveTimer = setInterval(slowSample, 1000);
      slowSample();
    }
    var pill = $('livePill');
    if (pill) {
      pill.classList.remove('is-paused');
      pill.setAttribute('aria-label', 'Muestreo en vivo activo');
    }
  }

  function stopLive() {
    if (fpsFrame !== null) {
      cancelAnimationFrame(fpsFrame);
      fpsFrame = null;
    }
    if (liveTimer !== null) {
      clearInterval(liveTimer);
      liveTimer = null;
    }
    var pill = $('livePill');
    if (pill) {
      pill.classList.add('is-paused');
      pill.setAttribute('aria-label', 'Muestreo en pausa porque la pestaña está oculta');
    }
  }

  function initLive() {
    fpsSpark = new Spark('fpsCanvas', { min: 0, max: 144 });
    ramSpark = new Spark('ramCanvas', { min: 0, max: 100, auto: true });
    batterySpark = new Spark('batCanvas', { min: 0, max: 100 });
    batteryDetailSpark = new Spark('batDetailCanvas', { min: 0, max: 100 });
    sparks.push(fpsSpark, ramSpark, batterySpark, batteryDetailSpark);

    startLive();

    document.addEventListener('visibilitychange', function () {
      if (document.hidden) stopLive();
      else startLive();
    });

    window.addEventListener('resize', function () {
      sparks.forEach(function (spark) { spark.resize(); spark.draw(); });
    });
  }

  /* ------------------------------------------------------------------ */
  /* Navegación                                                          */
  /* ------------------------------------------------------------------ */

  function initNav() {
    var links = Array.prototype.slice.call(document.querySelectorAll('[data-nav]'));
    var sections = Array.prototype.slice.call(document.querySelectorAll('section[data-section]'));

    function setActive(id, scrollChip) {
      if (!id) return;
      state.active = id;
      links.forEach(function (link) {
        if (link.dataset.nav === id) link.setAttribute('aria-current', 'true');
        else link.removeAttribute('aria-current');
      });
      if (scrollChip && window.matchMedia('(max-width: 900px)').matches) {
        var chip = document.querySelector('.chips [data-nav="' + id + '"]');
        if (chip) chip.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
      }
    }

    links.forEach(function (link) {
      link.addEventListener('click', function () {
        setActive(link.dataset.nav, true);
      });
    });

    var ticking = false;
    function onScroll() {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(function () {
        ticking = false;
        var y = window.scrollY + Math.min(window.innerHeight * 0.22, 160);
        var current = sections.length ? sections[0].id : null;
        sections.forEach(function (section) {
          var top = section.getBoundingClientRect().top + window.scrollY;
          if (top <= y) current = section.id;
        });
        setActive(current, false);
      });
    }

    window.addEventListener('scroll', onScroll, { passive: true });
    if (location.hash) {
      var hashId = location.hash.replace('#', '');
      if (document.getElementById(hashId)) setActive(hashId, false);
    } else {
      setActive('inicio', false);
    }
  }

  /* ------------------------------------------------------------------ */
  /* Informe, copiar y compartir                                         */
  /* ------------------------------------------------------------------ */

  function collectRows(section) {
    var rows = {};
    section.querySelectorAll('.row').forEach(function (row) {
      var key = row.dataset.key;
      var value = row.querySelector('.v');
      if (key && value) rows[key] = value.textContent.trim();
    });
    return rows;
  }

  function buildReport() {
    var sections = {};
    document.querySelectorAll('section[data-section]').forEach(function (section) {
      var rows = collectRows(section);
      if (Object.keys(rows).length) sections[section.dataset.section] = rows;
    });

    var apis = {};
    document.querySelectorAll('#apiGrid .api').forEach(function (item) {
      apis[item.dataset.api] = item.dataset.state === 'available';
    });

    var permissions = {};
    document.querySelectorAll('#permGrid .perm').forEach(function (item) {
      permissions[item.dataset.perm] = item.dataset.state;
    });

    var metrics = {};
    [['ram', 'm-ram', 'm-ram-unit'], ['storage', 'm-store', 'm-store-unit'], ['battery', 'm-bat', 'm-bat-unit'], ['cpu', 'm-cpu', 'm-cpu-unit']].forEach(function (entry) {
      var value = $(entry[1]);
      var unit = $(entry[2]);
      metrics[entry[0]] = value ? value.textContent.trim() + (unit && unit.textContent.trim() ? ' ' + unit.textContent.trim() : '') : null;
    });

    return {
      app: 'Lumen Web',
      version: '1.0.0',
      generatedAt: new Date().toISOString(),
      url: location.href,
      theme: root.dataset.theme,
      device: {
        name: $('deviceName') ? $('deviceName').textContent.trim() : null,
        os: state.info.os,
        osVersion: state.info.osVersion,
        browser: state.info.browser,
        browserVersion: state.info.browserVersion,
        touchPoints: navigator.maxTouchPoints || 0,
        standalone: window.matchMedia('(display-mode: standalone)').matches
      },
      metrics: metrics,
      live: {
        fps: state.live.fps,
        refreshHz: state.live.refresh
      },
      battery: {
        supported: state.battery.supported,
        level: state.battery.level,
        charging: state.battery.charging
      },
      storage: {
        usedBytes: state.storage.used,
        quotaBytes: state.storage.quota,
        percent: state.storage.pct,
        persisted: state.storage.persisted
      },
      sensors: {
        active: state.sensors.active,
        motion: state.sensors.motion,
        orientation: state.sensors.orientation,
        last: state.sensors.last
      },
      apis: apis,
      permissions: permissions,
      sections: sections
    };
  }

  function legacyCopy(text) {
    try {
      var area = document.createElement('textarea');
      area.value = text;
      area.setAttribute('readonly', '');
      area.style.position = 'fixed';
      area.style.top = '-1000px';
      area.style.left = '-1000px';
      area.style.opacity = '0';
      document.body.appendChild(area);
      area.select();
      area.setSelectionRange(0, area.value.length);
      var ok = document.execCommand('copy');
      area.remove();
      return ok;
    } catch (e) {
      return false;
    }
  }

  function openReportDialog(text) {
    var dialog = $('reportDialog');
    var area = $('reportText');
    if (!dialog || !area) return;
    area.value = text;
    if (typeof dialog.showModal === 'function') {
      if (!dialog.open) dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
    setTimeout(function () {
      area.focus();
      area.select();
    }, 50);
  }

  function initReportActions() {
    var copyButton = $('copyBtn');
    if (copyButton) {
      copyButton.addEventListener('click', function () {
        var text = JSON.stringify(buildReport(), null, 2);
        function fallback() {
          if (legacyCopy(text)) {
            toast('Informe copiado al portapapeles');
          } else {
            openReportDialog(text);
            toast('Abre el informe y cópialo manualmente');
          }
        }

        if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
          navigator.clipboard.writeText(text).then(function () {
            toast('Informe copiado al portapapeles');
          }).catch(fallback);
        } else {
          fallback();
        }
      });
    }

    var shareButton = $('shareBtn');
    if (shareButton) {
      if (typeof navigator.share === 'function') {
        shareButton.hidden = false;
        shareButton.addEventListener('click', function () {
          var report = buildReport();
          navigator.share({
            title: 'Lumen — ' + (report.device.name || 'Informe del dispositivo'),
            text: 'Informe local de hardware, batería, red y navegador. Sin cuentas ni telemetría.',
            url: location.href
          }).catch(function (error) {
            if (error && error.name === 'AbortError') return;
            toast('No se pudo compartir');
          });
        });
      } else {
        shareButton.hidden = true;
      }
    }

    var dialog = $('reportDialog');
    var closeButton = $('closeDialog');
    if (dialog && closeButton) {
      closeButton.addEventListener('click', function () { dialog.close(); });
      dialog.addEventListener('click', function (event) {
        if (event.target === dialog) dialog.close();
      });
    }
    var selectButton = $('selectReport');
    if (selectButton) {
      selectButton.addEventListener('click', function () {
        var area = $('reportText');
        if (!area) return;
        area.focus();
        area.select();
        if (legacyCopy(area.value)) toast('Informe copiado');
      });
    }
  }

  /* ------------------------------------------------------------------ */
  /* Service worker                                                      */
  /* ------------------------------------------------------------------ */

  function registerServiceWorker() {
    if (!('serviceWorker' in navigator)) return;
    var secure = location.protocol === 'https:' || location.hostname === 'localhost' || location.hostname === '127.0.0.1';
    if (!secure) return;
    window.addEventListener('load', function () {
      navigator.serviceWorker.register('./sw.js').catch(function () { /* sin conexión offline */ });
    });
  }

  /* ------------------------------------------------------------------ */
  /* Arranque                                                            */
  /* ------------------------------------------------------------------ */

  function init() {
    initTheme();
    readUA();
    renderHero();

    var stamp = dateFmt.format(new Date());
    setText('stampTime', stamp);
    setText('footStamp', stamp);
    setText('footOrigin', location.host || 'archivo local');

    initHardware();
    initStorage();
    initBattery();
    initNetwork();
    browserRows();
    initUAData();
    initMedia();
    initPermissions();
    initApis();
    initSensors();
    initLive();
    initNav();
    initReportActions();
    registerServiceWorker();

    var resizeTimer = null;
    window.addEventListener('resize', function () {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(renderHero, 140);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
