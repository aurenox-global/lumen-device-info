'use strict';

/*
 * Lumen Web 1.1.0 — informe local del dispositivo y benchmarks reales.
 * Sin dependencias, sin red, sin telemetría.
 *
 * Estados de cada dato:
 *   real    — medido ahora en este navegador.
 *   limited — el navegador lo estima, redondea u oculta parcialmente.
 *   na      — la API no existe o no hay hardware accesible.
 */
(function () {
  var $ = function (id) { return document.getElementById(id); };
  var root = document.documentElement;

  var APP_VERSION = '1.1.0';
  var LAST_KEY = 'lumen-tests-last-v1';
  var PREV_KEY = 'lumen-tests-prev-v1';

  var state = {
    info: { os: '', osVersion: '', browser: '', browserVersion: '', device: '', model: '', kind: 'desktop', formFactor: '' },
    active: 'inicio',
    webgl2: false,
    hasWebGL: false,
    battery: { supported: false, level: null, charging: null, chargingTime: null, dischargingTime: null, obj: null },
    network: { connection: null },
    live: { fps: null, refresh: null },
    sensors: {
      active: false, motion: false, orientation: false, samples: 0,
      last: { acc: {}, gyro: {}, ori: {}, heading: null },
      offsets: { acc: { x: 0, y: 0, z: 0 }, gyro: { a: 0, b: 0, c: 0 } },
      calibrated: false,
      history: { acc: [[], [], []], gyro: [[], [], []] },
      recent: { acc: [[], [], []], gyro: [[], [], []] }
    },
    memory: { supported: false },
    storage: { used: null, quota: null, pct: null, persisted: null },
    tests: { running: false, lastRunAt: null }
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

  function num(n, digits) {
    if (!Number.isFinite(n)) return '—';
    var d = typeof digits === 'number' ? digits : (n >= 100 ? 0 : n >= 10 ? 1 : 2);
    return numberFmt.format(Number(n.toFixed(d)));
  }

  function setText(id, text) {
    var el = $(id);
    if (el) el.textContent = text;
  }

  function setBar(id, pct) {
    var el = $(id);
    if (!el) return;
    var value = Number.isFinite(pct) ? clamp(pct, 0, 100) : 0;
    el.style.width = value.toFixed(2) + '%';
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

  function delay(ms) {
    return new Promise(function (resolve) { setTimeout(resolve, ms); });
  }

  function dateLabel(iso) {
    try { return dateFmt.format(new Date(iso)); } catch (e) { return iso; }
  }

  /* ------------------------------------------------------------------ */
  /* Sistema de estados por dato                                         */
  /* ------------------------------------------------------------------ */

  var ST_LABEL = { real: 'REAL', limited: 'LIMITADO', na: 'NO DISPONIBLE' };

  function prepareRows() {
    var rows = document.querySelectorAll('#main .row');
    for (var i = 0; i < rows.length; i += 1) {
      var row = rows[i];
      var v = row.querySelector('.v');
      if (!v) continue;
      if (v.parentElement && v.parentElement.classList.contains('vcell')) continue;
      var cell = makeEl('span', 'vcell');
      var line = makeEl('span', 'vline');
      row.insertBefore(cell, v);
      line.appendChild(v);
      var badge = makeEl('span', 'st');
      badge.textContent = '';
      badge.setAttribute('aria-hidden', 'true');
      line.appendChild(badge);
      cell.appendChild(line);
      var note = makeEl('span', 'row-note');
      note.hidden = true;
      cell.appendChild(note);
    }
  }

  function setField(id, value, status, note) {
    var el = $(id);
    if (!el) return;
    var missing = value === null || value === undefined || value === '' || value === '—';
    var st = status || (missing ? 'na' : 'real');
    if (missing && st !== 'limited') st = 'na';
    var text = missing ? 'No disponible' : String(value);

    el.textContent = text;
    if (missing) el.classList.add('na');
    else el.classList.remove('na');

    var cell = el.closest ? el.closest('.vcell') : null;
    if (cell) {
      var badge = cell.querySelector('.st');
      var noteEl = cell.querySelector('.row-note');
      if (badge) {
        badge.className = 'st ' + st;
        badge.textContent = ST_LABEL[st] || st;
        badge.title = st === 'real' ? 'Medido ahora en este navegador.' : (note || '');
      }
      if (noteEl) {
        if (note && st !== 'real') {
          noteEl.textContent = note;
          noteEl.hidden = false;
        } else {
          noteEl.textContent = '';
          noteEl.hidden = true;
        }
      }
      badge && badge.setAttribute('data-st', st);
    }
    el.dataset.status = st;

    if (missing) {
      el.tabIndex = 0;
      el.title = note || 'Este navegador no expone este dato.';
      el.setAttribute('aria-label', 'No disponible. ' + (note || ''));
    } else {
      el.removeAttribute('tabindex');
      if (note) el.title = note;
      else el.removeAttribute('title');
      el.setAttribute('aria-label', text + (note ? '. ' + note : ''));
    }
  }

  function setBadge(id, status, label) {
    var el = $(id);
    if (!el) return;
    el.className = 'st ' + status;
    el.textContent = label || ST_LABEL[status] || status;
  }

  function setMetric(name, value, unit, sub, status, note) {
    var hasValue = value !== null && value !== undefined && value !== '';
    setText('m-' + name, hasValue ? String(value) : '—');
    setText('m-' + name + '-unit', hasValue ? (unit || '') : '');
    var el = $('m-' + name + '-sub');
    if (!el) return;
    el.textContent = sub || '';
    el.dataset.status = status || (hasValue ? 'real' : 'na');
    if (note) el.title = note;
    else el.removeAttribute('title');
  }

  function finalizePending() {
    var vals = document.querySelectorAll('#main .v[data-key]');
    for (var i = 0; i < vals.length; i += 1) {
      var el = vals[i];
      var cell = el.closest('.vcell');
      var badge = cell && cell.querySelector('.st');
      if (badge && badge.textContent) continue;
      var text = el.textContent.trim();
      if (!text || text === '—') {
        setField(el.id, null, 'na', 'Sin lectura de este navegador.');
      } else {
        setField(el.id, text, 'limited', 'El navegador no completó esta consulta a tiempo.');
      }
    }
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
    var sensorGraphs = [accGraph, gyroGraph];
    sensorGraphs.forEach(function (g) { if (g) g.draw(); });
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
    if (/iPhone/.test(ua)) return { name: 'iPhone', kind: 'mobile' };
    if (/iPad/.test(ua)) return { name: 'iPad', kind: 'tablet' };
    if (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1) return { name: 'iPad', kind: 'tablet' };
    if (/Android/.test(ua)) {
      var m = ua.match(/Android[^;)]*;\s*([^;)]+)/);
      var model = '';
      if (m) {
        model = m[1].split(' Build/')[0].trim();
        if (model === 'K' || model === 'wv' || model === 'Mobile') model = '';
      }
      return { name: model || 'Dispositivo Android', kind: /Mobile/.test(ua) ? 'mobile' : 'tablet' };
    }
    if (/Macintosh|Mac OS X/.test(ua)) return { name: 'Equipo de escritorio', kind: 'desktop' };
    if (/CrOS/.test(ua)) return { name: 'Chromebook', kind: 'desktop' };
    if (/Windows/.test(ua)) return { name: 'PC Windows', kind: 'desktop' };
    if (/Linux/.test(ua)) return { name: 'Equipo Linux', kind: 'desktop' };
    return { name: 'Dispositivo', kind: 'desktop' };
  }

  function kindLabel(kind, formFactor) {
    if (formFactor === 'Mobile') return 'Móvil';
    if (formFactor === 'Tablet') return 'Tablet';
    if (formFactor === 'Desktop') return 'Escritorio';
    if (formFactor === 'EInk') return 'E-Ink';
    if (formFactor === 'Watch') return 'Reloj';
    if (kind === 'mobile') return 'Móvil';
    if (kind === 'tablet') return 'Tablet';
    return 'Escritorio';
  }

  function readUA() {
    var ua = navigator.userAgent || '';
    var browser = parseBrowser(ua);
    var os = parseOS(ua);
    var device = guessDevice(ua);
    state.info.os = os[0];
    state.info.osVersion = os[1];
    state.info.browser = browser[0];
    state.info.browserVersion = browser[1] ? browser[1].split('.')[0] : '';
    state.info.device = device.name;
    state.info.kind = device.kind;
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
    if (!box) return;
    box.textContent = '';
    var tags = [
      kindLabel(info.kind, info.formFactor),
      navigator.maxTouchPoints > 0 ? 'Pantalla táctil' : 'Sin pantalla táctil',
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

  /* ------------------------------------------------------------------ */
  /* Hardware                                                            */
  /* ------------------------------------------------------------------ */

  function initHardware() {
    var cores = navigator.hardwareConcurrency;
    setField('hw-cores', cores || null, 'real', cores ? 'navigator.hardwareConcurrency: núcleos lógicos que el navegador declara.' : 'navigator.hardwareConcurrency no está disponible.');
    setMetric('cpu', cores || null, 'núcleos', cores ? 'Núcleos lógicos · declarados por el navegador' : 'No disponible · hardwareConcurrency', cores ? 'real' : 'na');

    setField('hw-arch', null, 'na', 'Requiere User-Agent Client Hints de alta entropía (Chromium).');
    setField('hw-platform', navigator.platform || null, 'limited', 'navigator.platform es un valor obsoleto y puede estar congelado por privacidad.');
    setField('hw-devmem', navigator.deviceMemory ? num(navigator.deviceMemory) + ' GB aprox.' : null,
      navigator.deviceMemory ? 'limited' : 'na',
      navigator.deviceMemory ? 'El navegador redondea la RAM a una potencia de 2 y la limita por privacidad.' : 'navigator.deviceMemory no está disponible en este navegador.');

    var mem = safe(function () { return performance.memory; });
    state.memory.supported = !!(mem && Number.isFinite(mem.jsHeapSizeLimit));
    if (mem && Number.isFinite(mem.usedJSHeapSize) && Number.isFinite(mem.jsHeapSizeLimit)) {
      var used = mem.usedJSHeapSize;
      var limit = mem.jsHeapSizeLimit;
      var usedPct = clamp((used / limit) * 100, 0, 100);
      setField('hw-heap', bytes(used), 'real', 'performance.memory: heap de esta pestaña.');
      setField('mem-used', bytes(used), 'real');
      setField('mem-limit', bytes(limit), 'real');
      setField('mem-free', bytes(Math.max(0, limit - used)), 'real');
      setMetric('ram', num(used / 1048576), 'MB', 'Heap JS · límite ' + bytes(limit), 'real', 'Solo mide el heap de esta pestaña, no la RAM del sistema.');
      setBar('memBarFill', usedPct);
    } else {
      setField('hw-heap', null, 'na', 'performance.memory no existe en este navegador.');
      setField('mem-used', null, 'na', 'performance.memory no existe en este navegador.');
      setField('mem-limit', null, 'na', 'performance.memory no existe en este navegador.');
      setField('mem-free', null, 'na', 'performance.memory no existe en este navegador.');
      setMetric('ram', null, '', 'No disponible · heap JS no expuesto', 'na');
      setText('live-ram', 'No disponible');
    }

    setField('mem-device', navigator.deviceMemory ? num(navigator.deviceMemory) + ' GB aprox.' : null,
      navigator.deviceMemory ? 'limited' : 'na',
      navigator.deviceMemory ? 'Valor redondeado por el navegador; no es la RAM instalada exacta.' : 'navigator.deviceMemory no está disponible.');

    initGPU();
    initDisplay();
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
        setField(id, null, 'na', 'WebGL no está disponible o está bloqueado en este navegador.');
      });
      initWebGPU();
      return;
    }

    var vendor = null;
    var renderer = null;
    var unmasked = false;
    try {
      var debug = gl.getExtension('WEBGL_debug_renderer_info');
      if (debug) {
        vendor = gl.getParameter(debug.UNMASKED_VENDOR_WEBGL);
        renderer = gl.getParameter(debug.UNMASKED_RENDERER_WEBGL);
        unmasked = !!(vendor || renderer);
      }
    } catch (e) { /* extensión bloqueada */ }

    if (!vendor) vendor = gl.getParameter(gl.VENDOR);
    if (!renderer) renderer = gl.getParameter(gl.RENDERER);

    setField('gpu-vendor', vendor, unmasked ? 'real' : 'limited',
      unmasked ? null : 'El navegador oculta el fabricante real y devuelve un valor genérico.');
    setField('gpu-renderer', renderer, unmasked ? 'real' : 'limited',
      unmasked ? null : 'Renderer enmascarado por privacidad.');
    var version = gl.getParameter(gl.VERSION) || '';
    setField('gpu-api', version.indexOf('WebGL 2') === 0 ? 'WebGL 2.0' : 'WebGL 1.0', 'real');
    var maxTex = gl.getParameter(gl.MAX_TEXTURE_SIZE);
    setField('gpu-maxtex', Number.isFinite(maxTex) ? intFmt.format(maxTex) + ' px' : null,
      Number.isFinite(maxTex) ? 'real' : 'na', Number.isFinite(maxTex) ? null : 'WebGL no expone MAX_TEXTURE_SIZE.');

    initWebGPU();
  }

  function initWebGPU() {
    if (!('gpu' in navigator) || !navigator.gpu) {
      setField('gpu-webgpu', null, 'na', 'WebGPU no está disponible en este navegador.');
      setField('gpu-buffer', null, 'na', 'WebGPU no está disponible en este navegador.');
      return;
    }
    setField('gpu-webgpu', 'Solicitando adaptador…', 'limited', 'Consultando navigator.gpu.requestAdapter().');
    navigator.gpu.requestAdapter().then(function (adapter) {
      if (!adapter) {
        setField('gpu-webgpu', null, 'na', 'No se encontró un adaptador WebGPU compatible.');
        setField('gpu-buffer', null, 'na', 'No hay adaptador WebGPU.');
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
      setField('gpu-webgpu', null, 'na', 'La solicitud de adaptador WebGPU falló.');
      setField('gpu-buffer', null, 'na', 'La solicitud de adaptador WebGPU falló.');
    });
  }

  function renderAdapter(adapter, info) {
    var parts = [];
    if (info) {
      [info.vendor, info.architecture, info.device, info.description].forEach(function (part) {
        if (part && parts.indexOf(part) === -1) parts.push(String(part));
      });
    }
    setField('gpu-webgpu', parts.length ? parts.join(' · ') : 'Adaptador disponible', 'real',
      'Información del adaptador WebGPU expuesta por el navegador.');
    var maxBuffer = adapter.limits && adapter.limits.maxBufferSize;
    setField('gpu-buffer', Number.isFinite(maxBuffer) ? bytes(maxBuffer) : null,
      Number.isFinite(maxBuffer) ? 'real' : 'na',
      Number.isFinite(maxBuffer) ? null : 'El adaptador no expone maxBufferSize.');
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
    setField('scr-css', sw + ' × ' + sh + ' px', 'real', 'screen.width × screen.height en píxeles CSS.');
    setField('scr-phys', Math.round(sw * dpr) + ' × ' + Math.round(sh * dpr) + ' px', 'real', 'Resolución CSS × devicePixelRatio.');
    setField('scr-dpr', (dpr % 1 === 0 ? dpr : Number(dpr.toFixed(2))).toString().replace('.', ',') + '×', 'real', 'window.devicePixelRatio.');
    setField('scr-depth', screen.colorDepth ? screen.colorDepth + ' bits' : null,
      screen.colorDepth ? 'real' : 'na', screen.colorDepth ? null : 'screen.colorDepth no está disponible.');

    var gamut = null;
    if (window.matchMedia('(color-gamut: rec2020)').matches) gamut = 'Rec. 2020';
    else if (window.matchMedia('(color-gamut: p3)').matches) gamut = 'Display P3';
    else if (window.matchMedia('(color-gamut: srgb)').matches) gamut = 'sRGB';
    setField('scr-gamut', gamut, gamut ? 'real' : 'na',
      gamut ? 'Detectado con la media query color-gamut.' : 'Este navegador no expone la gama de color.');

    var hdr = null;
    if (window.matchMedia('(dynamic-range: high)').matches) hdr = 'Alto rango dinámico';
    else if (window.matchMedia('(dynamic-range: standard)').matches) hdr = 'Rango estándar';
    setField('scr-hdr', hdr, hdr ? 'real' : 'na',
      hdr ? 'Media query dynamic-range.' : 'Este navegador no expone el rango dinámico.');

    setField('scr-orient', orientationLabel(), orientationLabel() ? 'real' : 'na',
      orientationLabel() ? null : 'screen.orientation no está disponible.');
    setField('scr-touch', navigator.maxTouchPoints > 0 ? intFmt.format(navigator.maxTouchPoints) + ' puntos' : 'Ninguno',
      'real', 'navigator.maxTouchPoints.');

    setField('scr-refresh', 'Midiendo…', 'limited', 'Contando fotogramas reales durante 1,2 s.');
    measureRefresh().then(function (result) {
      if (!result) {
        setField('scr-refresh', null, 'na', 'No se pudo medir el refresco (pestaña oculta o sin requestAnimationFrame).');
        return;
      }
      state.live.refresh = Math.round(result.hz);
      setField('scr-refresh', '≈ ' + Math.round(result.hz) + ' Hz', 'real',
        'Medido con ' + result.frames + ' fotogramas; mediana ' + result.median.toFixed(2) + ' ms.');
    });

    if (screen.orientation && typeof screen.orientation.addEventListener === 'function') {
      screen.orientation.addEventListener('change', function () {
        setField('scr-orient', orientationLabel(), orientationLabel() ? 'real' : 'na', orientationLabel() ? null : 'screen.orientation no disponible.');
      });
    }
  }

  function measureRefresh() {
    return new Promise(function (resolve) {
      if (typeof window.requestAnimationFrame !== 'function') { resolve(null); return; }
      var deltas = [];
      var start = performance.now();
      var last = start;
      var done = false;
      var timer = setTimeout(function () {
        if (done) return;
        done = true;
        finish();
      }, 2600);

      function finish() {
        clearTimeout(timer);
        if (deltas.length < 8) { resolve(null); return; }
        var sorted = deltas.slice().sort(function (a, b) { return a - b; });
        var median = sorted[Math.floor(sorted.length / 2)];
        var hz = 1000 / median;
        var common = [60, 75, 90, 100, 120, 144, 165, 240, 360];
        var snapped = null;
        common.forEach(function (c) {
          if (Math.abs(hz - c) / c < 0.07) snapped = c;
        });
        resolve({ hz: snapped || hz, frames: deltas.length, median: median });
      }

      function frame(now) {
        if (done) return;
        var d = now - last;
        last = now;
        if (now - start > 1200) {
          done = true;
          finish();
          return;
        }
        if (d > 0 && d < 100) deltas.push(d);
        requestAnimationFrame(frame);
      }
      requestAnimationFrame(frame);
    });
  }

  /* ------------------------------------------------------------------ */
  /* Almacenamiento                                                      */
  /* ------------------------------------------------------------------ */

  function updatePersist(granted, note) {
    state.storage.persisted = !!granted;
    setField('st-persist', granted ? 'Sí' : 'No', 'real',
      note || (granted ? 'navigator.storage.persisted() confirma persistencia.' : 'El navegador no la concede; puede borrar los datos bajo presión de espacio.'));
    var btn = $('persistBtn');
    if (btn) {
      btn.textContent = granted ? 'Almacenamiento persistente concedido' : 'Solicitar almacenamiento persistente';
      btn.disabled = !!granted;
    }
    setText('persistNote', granted
      ? 'Concedido: el navegador no borrará estos datos por falta de espacio sin intervención.'
      : (note || 'El navegador decide si concede persistencia; no siempre depende de ti.'));
  }

  function initStorage() {
    var persistBtn = $('persistBtn');
    if (!navigator.storage || typeof navigator.storage.estimate !== 'function') {
      var reason = 'La Storage API no está disponible en este navegador.';
      ['st-used', 'st-quota', 'st-free', 'st-persist', 'st-details'].forEach(function (id) { setField(id, null, 'na', reason); });
      setText('st-pct', '—');
      setText('st-caption', 'No disponible');
      setMetric('store', null, '', 'No disponible · Storage API', 'na');
      if (persistBtn) {
        persistBtn.disabled = true;
        persistBtn.textContent = 'Persistencia no disponible';
      }
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
        setField('st-used', bytes(used), 'real', 'Uso del origen según storage.estimate().');
        setField('st-quota', bytes(quota), 'real', 'Cuota del origen, no del disco.');
        setField('st-free', bytes(Math.max(0, quota - used)), 'real');
        setText('st-pct', pct < 1 ? num(pct, 1) : intFmt.format(pct));
        setText('st-caption', bytes(used) + ' de ' + bytes(quota));
        setBar('stBarFill', pct);
        setMetric('store', pct < 1 ? num(pct, 1) : intFmt.format(pct), '%', bytes(used) + ' de ' + bytes(quota), 'real', 'Solo el almacenamiento de este sitio web.');
      } else if (Number.isFinite(used)) {
        setField('st-used', bytes(used), 'real');
        setField('st-quota', null, 'na', 'El navegador no revela la cuota de almacenamiento.');
        setMetric('store', null, '', 'Usado: ' + bytes(used), 'limited');
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
        setField('st-details', parts.join(' · '), 'real', 'Desglose declarado por storage.estimate().');
      } else {
        setField('st-details', null, 'na', 'El navegador no expone el desglose por tipo de almacenamiento.');
      }

      if (typeof navigator.storage.persisted === 'function') {
        return navigator.storage.persisted().then(function (persisted) {
          updatePersist(persisted);
        }).catch(function () {
          setField('st-persist', null, 'na', 'No se pudo consultar el estado persistente.');
        });
      }
      setField('st-persist', null, 'na', 'navigator.storage.persisted no está disponible.');
      return null;
    }).catch(function () {
      var reason = 'El navegador bloqueó la estimación de almacenamiento.';
      ['st-used', 'st-quota', 'st-free', 'st-persist', 'st-details'].forEach(function (id) { setField(id, null, 'na', reason); });
      setText('st-caption', 'No disponible');
      setMetric('store', null, '', 'No disponible · Storage API', 'na');
    });

    if (persistBtn) {
      persistBtn.addEventListener('click', function () {
        if (!navigator.storage || typeof navigator.storage.persist !== 'function') {
          toast('Este navegador no permite solicitar persistencia');
          return;
        }
        persistBtn.disabled = true;
        persistBtn.textContent = 'Solicitando…';
        navigator.storage.persist().then(function (granted) {
          return (typeof navigator.storage.persisted === 'function' ? navigator.storage.persisted() : Promise.resolve(granted))
            .then(function (actual) {
              updatePersist(actual, actual ? null : 'El navegador denegó la persistencia: puede borrar estos datos si necesita espacio.');
              return actual;
            });
        }).then(function (granted) {
          toast(granted ? 'Almacenamiento persistente concedido' : 'El navegador no concedió persistencia');
        }).catch(function () {
          updatePersist(false, 'La solicitud falló: este navegador no permite persistencia en este contexto.');
          toast('No se pudo solicitar persistencia');
        });
      });
    }
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
    setField('bat-state', null, 'na', reason);
    setField('bat-charge-time', null, 'na', reason);
    setField('bat-discharge-time', null, 'na', reason);
    setField('bat-health', null, 'na', 'La salud de la batería requiere una API nativa; el navegador no la expone.');
    setField('bat-temp', null, 'na', 'Temperatura y voltaje no se exponen al navegador.');
    setMetric('bat', null, '', 'No disponible · Battery API', 'na');
    setText('live-battery', 'No disponible');
    setField('bat-res', null, 'na', 'La API no expone resolución ni precisión del nivel.');
  }

  function initBattery() {
    if (typeof navigator.getBattery !== 'function') {
      batteryUnavailable('La Battery Status API no está disponible en este navegador (Safari y Firefox no la incluyen).');
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
    // Muchos equipos de escritorio sin batería exponen un valor fijo 100 % + cargando.
    var fixedDesktopValue = charging && pct === 100 && (!Number.isFinite(battery.chargingTime) || battery.chargingTime === 0);
    state.battery.level = pct;
    state.battery.charging = charging;

    setText('bat-big', String(pct));
    setText('bat-badge', charging ? 'Cargando' : pct >= 100 ? 'Completa' : 'En uso');
    var badge = $('bat-badge');
    if (badge) badge.className = 'state-pill ' + (charging ? 'ok' : pct <= 20 ? 'warn' : '');

    setBar('batBarFill', pct);
    setField('bat-state', charging ? 'Cargando' : 'Descargando', fixedDesktopValue ? 'limited' : 'real',
      fixedDesktopValue
        ? 'El navegador declara 100 % y «cargando»; en equipos sin batería suele ser un valor fijo, no una medición.'
        : 'battery.charging.');
    setField('bat-charge-time', charging ? (duration(battery.chargingTime) || 'No estimado') : 'No aplica (sin cargar)',
      charging ? 'limited' : 'real',
      charging ? 'El navegador puede devolver Infinity mientras calcula.' : null);
    setField('bat-discharge-time', !charging ? (duration(battery.dischargingTime) || 'No estimado') : 'No aplica (cargando)',
      !charging ? 'limited' : 'real',
      !charging ? 'El navegador puede devolver Infinity mientras calcula.' : null);
    setField('bat-health', null, 'na', 'No existe una API web de salud de batería; requiere app nativa.');
    setField('bat-temp', null, 'na', 'Temperatura y voltaje no se exponen al navegador.');
    setField('bat-res', '1 % · redondeado', 'limited', 'La API redondea el nivel; no declara la resolución real.');

    setMetric('bat', pct, '%', charging ? 'Cargando · Battery Status API' : 'En uso · Battery Status API', fixedDesktopValue ? 'limited' : 'real',
      fixedDesktopValue ? 'Valor fijo habitual en equipos de escritorio sin batería.' : null);
    setText('live-battery', pct + '%');
    setText('batNote', fixedDesktopValue
      ? 'Este equipo parece no tener batería: el navegador devuelve 100 % y «cargando» como valor fijo. No es una medición del estado real.'
      : 'La API puede redondear el nivel y devolver «Infinity» mientras calcula los tiempos. No existe una API web para salud, temperatura o voltaje.');
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
    setField('net-status', online ? 'En línea' : 'Sin conexión', 'real', 'navigator.onLine: declaración del navegador.');
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
        setField(id, null, 'na', reason);
      });
      setText('qualityValue', '—');
      setText('qualityLabel', online ? 'Sin estimación de calidad' : 'Sin conexión');
      setBar('netBarFill', 0);
      return;
    }

    setField('net-type', networkTypeLabel(connection.type), connection.type ? 'limited' : 'na',
      connection.type ? 'El navegador puede ocultar el tipo físico de conexión.' : 'El navegador no revela el tipo físico de conexión.');
    var effective = connection.effectiveType;
    setField('net-effective', effective ? effective.toUpperCase() : null, effective ? 'limited' : 'na',
      effective ? 'Estimación del navegador, no una medición de velocidad.' : 'No expuesto por este navegador.');
    setField('net-downlink', Number.isFinite(connection.downlink) ? num(connection.downlink) + ' Mb/s aprox.' : null,
      Number.isFinite(connection.downlink) ? 'limited' : 'na',
      Number.isFinite(connection.downlink) ? 'Valor declarado por el navegador; suele estar limitado a 10 Mb/s o redondeado.' : 'El navegador no expone el ancho de banda estimado.');
    setField('net-rtt', Number.isFinite(connection.rtt) ? connection.rtt + ' ms' : null,
      Number.isFinite(connection.rtt) ? 'limited' : 'na',
      Number.isFinite(connection.rtt) ? 'Latencia estimada y redondeada por el navegador.' : 'El navegador no expone la latencia estimada.');
    setField('net-savedata', typeof connection.saveData === 'boolean' ? (connection.saveData ? 'Activado' : 'Desactivado') : null,
      typeof connection.saveData === 'boolean' ? 'real' : 'na',
      typeof connection.saveData === 'boolean' ? null : 'El navegador no expone el ahorro de datos.');
    setField('net-live', 'Escuchando', 'real', 'Se actualiza con el evento change.');
    setField('net-origin', 'Network Information API', 'real');

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
    setText('qualityLabel', label + ' · estimación del navegador');
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
  /* Navegador: UA, plataforma, entorno, WebAssembly                     */
  /* ------------------------------------------------------------------ */

  function browserRows() {
    var ua = navigator.userAgent;
    setField('br-ua', ua, 'real', 'navigator.userAgent.');
    setField('br-legacy', navigator.platform || null, navigator.platform ? 'limited' : 'na',
      navigator.platform ? 'Valor obsoleto y potencialmente congelado por privacidad.' : 'navigator.platform no está disponible.');

    var languages = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language];
    setField('br-lang', languages.filter(Boolean).join(', '), 'real', 'navigator.languages.');

    var timezone = safe(function () { return Intl.DateTimeFormat().resolvedOptions().timeZone; });
    var offset = -new Date().getTimezoneOffset();
    var sign = offset >= 0 ? '+' : '−';
    var abs = Math.abs(offset);
    var hh = String(Math.floor(abs / 60)).padStart(2, '0');
    var mm = String(abs % 60).padStart(2, '0');
    setField('br-tz', timezone ? timezone + ' (UTC' + sign + hh + ':' + mm + ')' : null,
      timezone ? 'real' : 'na', timezone ? 'Intl.DateTimeFormat().resolvedOptions().' : 'El navegador no expone la zona horaria.');

    setField('br-cookies', navigator.cookieEnabled ? 'Activadas' : 'Bloqueadas', 'real');
    var dnt = navigator.doNotTrack || window.doNotTrack || navigator.msDoNotTrack;
    setField('br-dnt', dnt === '1' || dnt === 'yes' ? 'Activado' : dnt === '0' ? 'Desactivado' : null,
      dnt === '1' || dnt === 'yes' || dnt === '0' ? 'real' : 'na',
      dnt === '1' || dnt === 'yes' || dnt === '0' ? null : 'El navegador ya no expone Do Not Track.');
    setField('br-secure', window.isSecureContext ? 'Sí' : 'No', 'real', 'window.isSecureContext: las APIs PWA requieren HTTPS o localhost.');

    setField('br-platform', null, 'na', 'Requiere User-Agent Client Hints (Chromium).');
    setField('br-platver', null, 'na', 'Requiere datos de alta entropía de User-Agent Client Hints.');
    setField('br-model', null, 'na', 'El navegador no revela el modelo del dispositivo.');
    setField('br-arch', null, 'na', 'Requiere datos de alta entropía de User-Agent Client Hints.');
    setField('br-bits', null, 'na', 'Requiere datos de alta entropía de User-Agent Client Hints.');
    setField('br-form', null, 'na', 'Requiere User-Agent Client Hints (formFactors).');
    setField('br-brands', null, 'na', 'User-Agent Client Hints solo está en navegadores Chromium.');
    setField('br-fullver', null, 'na', 'User-Agent Client Hints solo está en navegadores Chromium.');

    readWasm();
  }

  function readWasm() {
    if (typeof WebAssembly !== 'object' || typeof WebAssembly.validate !== 'function') {
      setField('br-wasm', null, 'na', 'WebAssembly no está disponible en este navegador.');
      return;
    }
    var bytes = new Uint8Array([0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00]);
    var t0 = performance.now();
    var ok = false;
    try { ok = WebAssembly.validate(bytes); } catch (e) { ok = false; }
    var ms = performance.now() - t0;
    var text = ok ? 'Sí · validado en ' + (ms < 1 ? ms.toFixed(2) : ms.toFixed(1)) + ' ms' : 'No válido';
    setField('br-wasm', text, ok ? 'real' : 'limited',
      ok ? 'Módulo mínimo validado con WebAssembly.validate().' : 'El motor responde pero rechaza un módulo mínimo.');
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

    setField('br-platform', uaData.platform || null, uaData.platform ? 'real' : 'na',
      uaData.platform ? 'navigator.userAgentData.platform.' : 'El navegador no expone la plataforma.');
    if (Array.isArray(uaData.brands) && uaData.brands.length) {
      setField('br-brands', uaData.brands.map(function (b) { return b.brand + ' ' + b.version; }).join(' · '), 'real');
    }

    var highEntropy = ['architecture', 'bitness', 'model', 'platformVersion', 'uaFullVersion', 'fullVersionList', 'wow64', 'formFactors'];
    if (typeof uaData.getHighEntropyValues !== 'function') return;

    uaData.getHighEntropyValues(highEntropy).then(function (high) {
      setField('br-platver', high.platformVersion || null, high.platformVersion ? 'real' : 'na',
        high.platformVersion ? null : 'El navegador no revela la versión del sistema.');
      var model = high.model && high.model !== 'K' ? high.model : null;
      setField('br-model', model, model ? 'real' : 'na',
        model ? 'Modelo declarado por User-Agent Client Hints.' : 'El navegador no revela el modelo del dispositivo.');
      if (model) {
        state.info.model = model;
        renderHero();
      }

      var arch = high.architecture || null;
      if (arch) {
        var archText = arch + (high.bitness ? ' · ' + high.bitness + ' bits' : '') + (high.wow64 ? ' · WOW64' : '');
        setField('br-arch', high.architecture, 'real', 'Arquitectura del binario del navegador, no del SoC.');
        setField('br-bits', high.bitness ? high.bitness + ' bits' : null,
          high.bitness ? 'real' : 'na', high.bitness ? null : 'El navegador no revela el ancho de palabra.');
        setField('hw-arch', archText, 'real', 'User-Agent Client Hints: arquitectura del navegador, no del SoC.');
      } else {
        setField('br-bits', null, 'na', 'El navegador no revela el ancho de palabra.');
      }

      if (Array.isArray(high.formFactors) && high.formFactors.length) {
        var map = { Desktop: 'Escritorio', Mobile: 'Móvil', Tablet: 'Tablet', XR: 'XR', EInk: 'E-Ink', Watch: 'Reloj' };
        var ff = high.formFactors[0];
        state.info.formFactor = ff;
        setField('br-form', map[ff] || ff, 'real', 'formFactors de User-Agent Client Hints.');
        if (!state.info.model) {
          if (ff === 'Mobile') state.info.kind = 'mobile';
          else if (ff === 'Tablet') state.info.kind = 'tablet';
          else if (ff === 'Desktop') state.info.kind = 'desktop';
        }
        renderHero();
      } else {
        setField('br-form', null, 'na', 'El navegador no expone formFactors.');
      }

      if (Array.isArray(high.fullVersionList) && high.fullVersionList.length) {
        setField('br-brands', high.fullVersionList.map(function (b) { return b.brand + ' ' + b.version; }).join(' · '), 'real');
        var preferred = high.fullVersionList.filter(function (b) {
          return !/Not.?A.?Brand|Chromium/i.test(b.brand);
        })[0];
        if (preferred) {
          state.info.browser = simplifyBrand(preferred.brand);
          state.info.browserVersion = String(preferred.version).split('.')[0];
          renderHero();
        }
      }
      setField('br-fullver', high.uaFullVersion || null, high.uaFullVersion ? 'real' : 'na',
        high.uaFullVersion ? null : 'El navegador no revela la versión completa.');

      if (uaData.platform === 'macOS' && high.platformVersion) {
        state.info.os = 'macOS';
        state.info.osVersion = high.platformVersion;
        renderHero();
      } else if (uaData.platform === 'Windows' && high.platformVersion) {
        state.info.osVersion = high.platformVersion;
        renderHero();
      }
    }).catch(function () {
      ['br-platver', 'br-model', 'br-arch', 'br-bits', 'br-form', 'br-fullver'].forEach(function (id) {
        setField(id, null, 'na', 'El navegador denegó los datos de alta entropía.');
      });
    });
  }

  /* ------------------------------------------------------------------ */
  /* Códecs                                                              */
  /* ------------------------------------------------------------------ */

  var CODECS = [
    { name: 'H.264', kind: 'video', type: 'video/mp4; codecs="avc1.42E01E"' },
    { name: 'HEVC', kind: 'video', type: 'video/mp4; codecs="hvc1.1.6.L93.B0"' },
    { name: 'VP9', kind: 'video', type: 'video/webm; codecs="vp9"' },
    { name: 'AV1', kind: 'video', type: 'video/mp4; codecs="av01.0.05M.08"' },
    { name: 'Opus', kind: 'audio', type: 'audio/webm; codecs="opus"' },
    { name: 'AAC', kind: 'audio', type: 'audio/mp4; codecs="mp4a.40.2"' }
  ];

  function codecConfig(codec) {
    if (codec.kind === 'video') {
      return {
        type: 'file',
        video: { contentType: codec.type, width: 1920, height: 1080, bitrate: 4000000, framerate: 30 }
      };
    }
    return {
      type: 'file',
      audio: { contentType: codec.type, channels: 2, bitrate: 128000, samplerate: 48000 }
    };
  }

  function canPlayType(codec) {
    var media = document.createElement(codec.kind === 'video' ? 'video' : 'audio');
    return media.canPlayType(codec.type);
  }

  function initCodecs() {
    var grid = $('codecGrid');
    if (!grid) return;
    grid.textContent = '';

    var entries = CODECS.map(function (codec) {
      var chip = makeEl('div', 'codec');
      chip.appendChild(makeEl('span', 'codec-dot'));
      chip.appendChild(makeEl('span', 'codec-name', codec.name));
      var stateEl = makeEl('span', 'codec-state', 'consultando…');
      chip.appendChild(stateEl);
      grid.appendChild(chip);
      return { codec: codec, chip: chip, stateEl: stateEl, status: 'limited' };
    });

    function paint() {
      var supported = 0;
      entries.forEach(function (entry) {
        var status = entry.status;
        if (status === 'real') supported += 1;
        entry.chip.className = 'codec ' + (status === 'real' ? 'ok' : status === 'limited' ? 'maybe' : 'no');
        entry.stateEl.textContent = status === 'real' ? 'soportado' : status === 'limited' ? 'quizá' : 'no';
        entry.chip.title = entry.note || '';
      });
      setText('codecCount', supported + ' / ' + entries.length + ' soportados');
    }

    if (navigator.mediaCapabilities && typeof navigator.mediaCapabilities.decodingInfo === 'function') {
      Promise.all(entries.map(function (entry) {
        return navigator.mediaCapabilities.decodingInfo(codecConfig(entry.codec)).then(function (info) {
          entry.status = info.supported ? 'real' : 'na';
          entry.note = info.supported
            ? 'MediaCapabilities declara decodificación soportada (smooth: ' + info.smooth + ', powerEfficient: ' + info.powerEfficient + ').'
            : 'MediaCapabilities declara que este códec no se puede decodificar.';
          return null;
        }).catch(function () {
          var fallback = canPlayType(entry.codec);
          entry.status = fallback === 'probably' ? 'limited' : fallback === 'maybe' ? 'limited' : 'na';
          entry.note = 'MediaCapabilities falló; canPlayType respondió «' + (fallback || 'nada') + '».';
          return null;
        });
      })).then(paint).catch(paint);
    } else {
      entries.forEach(function (entry) {
        var result = canPlayType(entry.codec);
        entry.status = result === 'probably' || result === 'maybe' ? 'limited' : 'na';
        entry.note = 'Sin MediaCapabilities: canPlayType respondió «' + (result || 'nada') + '», que no confirma decodificación real.';
      });
      paint();
    }
  }

  /* ------------------------------------------------------------------ */
  /* Cámara y micrófono bajo demanda                                     */
  /* ------------------------------------------------------------------ */

  function mediaErrorText(error) {
    if (!error) return 'Error desconocido.';
    switch (error.name) {
      case 'NotAllowedError': return 'Permiso denegado o bloqueado por el navegador.';
      case 'NotFoundError': return 'No hay ningún dispositivo de ese tipo conectado.';
      case 'NotReadableError': return 'El dispositivo está en uso por otra aplicación.';
      case 'SecurityError': return 'Requiere HTTPS o localhost.';
      case 'OverconstrainedError': return 'Ningún dispositivo cumple las restricciones.';
      default: return (error.name || 'Error') + ': ' + (error.message || 'sin detalle');
    }
  }

  function renderDevices(devices, granted) {
    var cams = devices.filter(function (d) { return d.kind === 'videoinput'; });
    var mics = devices.filter(function (d) { return d.kind === 'audioinput'; });
    var outs = devices.filter(function (d) { return d.kind === 'audiooutput'; });
    var labels = devices.filter(function (d) { return d.label; });

    var hiddenNote = 'El navegador puede ocultar dispositivos y nombres hasta conceder permiso.';
    if (!granted) {
      setField('br-cams', 'Sin permiso', 'limited', hiddenNote);
      setField('br-mics', 'Sin permiso', 'limited', hiddenNote);
      setField('br-outs', 'Sin permiso', 'limited', hiddenNote);
      setField('br-media', 'Oculto hasta permiso', 'limited', hiddenNote);
      setText('mediaStatus', 'Sin permiso: el navegador oculta nombres y puede ocultar dispositivos. Activa cámara o micrófono para obtener el conteo real; la captura se cierra al terminar.');
      return;
    }

    setField('br-cams', String(cams.length), 'real', 'Conteo real tras conceder permiso.');
    setField('br-mics', String(mics.length), 'real', 'Conteo real tras conceder permiso.');
    setField('br-outs', String(outs.length), outs.length ? 'real' : 'na',
      outs.length ? 'Salidas de audio enumeradas.' : 'El navegador no expone salidas de audio.');

    if (labels.length) {
      var list = labels.slice(0, 6).map(function (d) { return d.label; }).join(' · ');
      setField('br-media', list, 'real', null);
      setText('mediaStatus', 'Permiso concedido. ' + devices.length + ' dispositivos enumerados con nombre; la captura ya está cerrada.');
    } else {
      setField('br-media', devices.length + ' dispositivos · etiquetas ocultas', 'limited', hiddenNote);
      setText('mediaStatus', 'Permiso concedido para un tipo de dispositivo; el resto puede seguir con etiquetas ocultas.');
    }
  }

  function initMedia() {
    var camBtn = $('camBtn');
    var micBtn = $('micBtn');

    if (!navigator.mediaDevices || typeof navigator.mediaDevices.enumerateDevices !== 'function') {
      setField('br-media', null, 'na', 'La MediaDevices API no está disponible en este navegador.');
      setField('br-cams', null, 'na', 'La MediaDevices API no está disponible.');
      setField('br-mics', null, 'na', 'La MediaDevices API no está disponible.');
      setField('br-outs', null, 'na', 'La MediaDevices API no está disponible.');
      setBadge('mediaBadge', 'na', 'NO DISPONIBLE');
      if (camBtn) camBtn.disabled = true;
      if (micBtn) micBtn.disabled = true;
      setText('mediaStatus', 'Este navegador no expone la lista de dispositivos de media.');
      return;
    }

    setBadge('mediaBadge', 'limited', 'SIN PERMISO');
    navigator.mediaDevices.enumerateDevices().then(function (devices) {
      renderDevices(devices, false);
    }).catch(function () {
      setField('br-media', null, 'na', 'El navegador bloqueó la enumeración de dispositivos.');
      setText('mediaStatus', 'El navegador bloqueó la enumeración de dispositivos.');
    });

    function request(kind) {
      var btn = kind === 'video' ? camBtn : micBtn;
      if (btn) btn.disabled = true;
      setText('mediaStatus', 'Solicitando permiso de ' + (kind === 'video' ? 'cámara' : 'micrófono') + '…');
      var constraints = kind === 'video' ? { video: true } : { audio: true };

      navigator.mediaDevices.getUserMedia(constraints).then(function (stream) {
        stream.getTracks().forEach(function (track) { track.stop(); });
        setBadge('mediaBadge', 'real', kind === 'video' ? 'CÁMARA' : 'MICRÓFONO');
        return navigator.mediaDevices.enumerateDevices().then(function (devices) {
          renderDevices(devices, true);
          initPermissions();
        });
      }).catch(function (error) {
        setBadge('mediaBadge', 'na', 'DENEGADO');
        setText('mediaStatus', mediaErrorText(error) + ' Sin permiso no se puede contar el hardware real; lo mostrado arriba es lo que el navegador declara.');
      }).then(function () {
        if (btn) btn.disabled = false;
      });
    }

    if (camBtn) camBtn.addEventListener('click', function () { request('video'); });
    if (micBtn) micBtn.addEventListener('click', function () { request('audio'); });
  }

  /* ------------------------------------------------------------------ */
  /* Permisos y APIs                                                     */
  /* ------------------------------------------------------------------ */

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
      grid.textContent = '';
      grid.appendChild(makeEl('p', 'card-note', 'La Permissions API no está disponible en este navegador; los permisos no se pueden consultar sin pedirlos.'));
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
        item.title = result.label + ': ' + labelText + (result.state === 'unsupported' ? ' (este navegador no permite consultarlo)' : '');
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
      ['MediaCapabilities', function () { return !!(navigator.mediaCapabilities && navigator.mediaCapabilities.decodingInfo); }],
      ['WebRTC', function () { return 'RTCPeerConnection' in window; }],
      ['File System Access', function () { return 'showOpenFilePicker' in window; }],
      ['WebXR', function () { return 'xr' in navigator; }],
      ['Speech Synthesis', function () { return 'speechSynthesis' in window; }],
      ['Payment Request', function () { return 'PaymentRequest' in window; }],
      ['ResizeObserver', function () { return 'ResizeObserver' in window; }],
      ['IntersectionObserver', function () { return 'IntersectionObserver' in window; }],
      ['Sensor API', function () { return 'Accelerometer' in window; }],
      ['Battery Status', function () { return typeof navigator.getBattery === 'function'; }],
      ['Network Information', function () { return !!connectionObject(); }],
      ['Storage Manager', function () { return !!(navigator.storage && navigator.storage.estimate); }]
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
  var accGraph = null;
  var gyroGraph = null;

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

  function accelValue(v) {
    return Number.isFinite(v) ? num(v, 2) : '—';
  }

  function pushRecent(list, value) {
    list.push(value);
    while (list.length > 30) list.shift();
  }

  function updateSampleCount() {
    setText('sensorSamples', intFmt.format(state.sensors.samples) + ' muestras');
  }

  function markSensorData(kind) {
    state.sensors[kind] = true;
    state.sensors.samples += 1;
    updateSampleCount();
    setBadge('sensorBadge', 'real', 'ACTIVO');
    if (!state.sensors.motion || !state.sensors.orientation) {
      sensorStatus('Sensores activos · recibiendo ' + (state.sensors.motion ? 'acelerómetro/giroscopio' : 'orientación') + '. El otro bloque puede no existir en este equipo.', 'on');
    } else {
      sensorStatus('Sensores activos · actualizando en vivo.', 'on');
    }
    var cal = $('calibrateBtn');
    if (cal) cal.disabled = false;
  }

  function onMotion(event) {
    var acc = event.accelerationIncludingGravity || event.acceleration;
    var rotation = event.rotationRate;
    var got = false;
    var off = state.sensors.offsets;

    if (acc && (Number.isFinite(acc.x) || Number.isFinite(acc.y) || Number.isFinite(acc.z))) {
      var axes = ['x', 'y', 'z'];
      var slots = [0, 1, 2];
      axes.forEach(function (axis, i) {
        var raw = acc[axis];
        if (!Number.isFinite(raw)) return;
        got = true;
        pushRecent(state.sensors.recent.acc[i], raw);
        var value = raw - off.acc[axis];
        state.sensors.last.acc[axis] = value;
        setText('acc-' + axis, accelValue(value));
        setMeter('acc' + axis + 'Bar', value, 20);
        state.sensors.history.acc[i].push(value);
        if (state.sensors.history.acc[i].length > 150) state.sensors.history.acc[i].shift();
        if (accGraph) accGraph.push(slots[i], value);
      });
    }

    if (rotation && (Number.isFinite(rotation.alpha) || Number.isFinite(rotation.beta) || Number.isFinite(rotation.gamma))) {
      var pairs = [['alpha', 'a', 0], ['beta', 'b', 1], ['gamma', 'c', 2]];
      pairs.forEach(function (pair) {
        var raw = rotation[pair[0]];
        if (!Number.isFinite(raw)) return;
        got = true;
        pushRecent(state.sensors.recent.gyro[pair[2]], raw);
        var value = raw - off.gyro[pair[1]];
        state.sensors.last.gyro[pair[1]] = value;
        setText('gyro-' + pair[1], accelValue(value));
        setMeter('gyro' + pair[1] + 'Bar', value, 360);
        var slot = pair[2];
        state.sensors.history.gyro[slot].push(value);
        if (state.sensors.history.gyro[slot].length > 150) state.sensors.history.gyro[slot].shift();
        if (gyroGraph) gyroGraph.push(slot, value);
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
      setMeter('oriaBar', alpha > 180 ? alpha - 360 : alpha, 180);
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
      setBadge('compassStatus', 'real', 'REAL');
      var compass = $('compass');
      if (compass) compass.title = 'Rumbo ' + Math.round(heading) + '°';
    } else {
      setBadge('compassStatus', 'na', 'SIN RUMBO');
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
      setBadge('sensorBadge', 'na', 'NO DISPONIBLE');
      setText('sensorNote', 'No hay DeviceMotion ni DeviceOrientation disponibles en este dispositivo.');
      button.textContent = 'No disponible';
      button.disabled = true;
      return;
    }

    button.disabled = true;
    setBadge('sensorBadge', 'limited', 'PIDIENDO PERMISO');
    sensorStatus('Solicitando permiso…');

    var motionAllowed = hasMotion;
    var orientationAllowed = hasOrientation;

    var askMotion = function () {
      if (!motionAllowed || typeof DeviceMotionEvent === 'undefined' || typeof DeviceMotionEvent.requestPermission !== 'function') {
        return Promise.resolve();
      }
      return DeviceMotionEvent.requestPermission().then(function (result) {
        motionAllowed = result === 'granted';
      }).catch(function () { motionAllowed = false; });
    };

    var askOrientation = function () {
      if (!orientationAllowed || typeof DeviceOrientationEvent === 'undefined' || typeof DeviceOrientationEvent.requestPermission !== 'function') {
        return Promise.resolve();
      }
      return DeviceOrientationEvent.requestPermission().then(function (result) {
        orientationAllowed = result === 'granted';
      }).catch(function () { orientationAllowed = false; });
    };

    askMotion().then(askOrientation).then(function () {
      if (!motionAllowed && !orientationAllowed) {
        sensorStatus('Permiso denegado o no concedido. En un equipo de escritorio sin sensores es normal; en móvil, revisa los ajustes del navegador para este sitio.', 'err');
        setBadge('sensorBadge', 'na', 'DENEGADO');
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
      state.sensors.samples = 0;
      updateSampleCount();
      button.disabled = false;
      button.textContent = 'Detener sensores';
      setBadge('sensorBadge', 'limited', 'ESPERANDO');
      sensorStatus('Esperando datos de los sensores…');

      clearTimeout(sensorTimeout);
      sensorTimeout = setTimeout(function () {
        if (!state.sensors.motion && !state.sensors.orientation) {
          setBadge('sensorBadge', 'limited', 'SIN DATOS');
          sensorStatus('La API existe pero no llega ningún dato. Es lo normal en un equipo de escritorio sin sensores o con el navegador bloqueándolos.', 'err');
        } else if (!state.sensors.motion || !state.sensors.orientation) {
          setBadge('sensorBadge', 'limited', 'PARCIAL');
          sensorStatus('Solo llegan datos de un bloque: este equipo no tiene el otro sensor o el navegador no lo expone.', 'err');
        }
      }, 2500);
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
    var cal = $('calibrateBtn');
    if (cal) cal.disabled = true;
    setBadge('sensorBadge', 'limited', 'DETENIDO');
    sensorStatus('Sensores detenidos.');
  }

  function calibrateSensors() {
    var recentAcc = state.sensors.recent.acc;
    var recentGyro = state.sensors.recent.gyro;
    var axes = ['x', 'y', 'z'];
    var keys = ['a', 'b', 'c'];
    var moved = false;

    axes.forEach(function (axis, i) {
      var arr = recentAcc[i];
      if (!arr.length) return;
      var avg = arr.reduce(function (a, b) { return a + b; }, 0) / arr.length;
      state.sensors.offsets.acc[axis] = avg;
      moved = true;
    });
    keys.forEach(function (key, i) {
      var arr = recentGyro[i];
      if (!arr.length) return;
      var avg = arr.reduce(function (a, b) { return a + b; }, 0) / arr.length;
      state.sensors.offsets.gyro[key] = avg;
      moved = true;
    });

    if (!moved) {
      sensorStatus('Todavía no hay muestras que promediar. Mueve el dispositivo o espera un segundo.', 'err');
      return;
    }
    state.sensors.calibrated = true;
    sensorStatus('Puesto a cero: se resta la media de las últimas muestras de acelerómetro y giroscopio. La orientación no se altera.', 'on');
    toast('Sensores puestos a cero');
  }

  function initSensors() {
    accGraph = new MultiSpark('accCanvas', { min: -20, max: 20 });
    gyroGraph = new MultiSpark('gyroCanvas', { min: -360, max: 360 });

    var button = $('sensorBtn');
    var cal = $('calibrateBtn');
    if (cal) cal.addEventListener('click', calibrateSensors);
    if (!button) return;

    if (typeof window.DeviceMotionEvent === 'undefined' && typeof window.DeviceOrientationEvent === 'undefined') {
      button.disabled = true;
      button.textContent = 'No disponible';
      setBadge('sensorBadge', 'na', 'NO DISPONIBLE');
      sensorStatus('Sin APIs de sensores en este navegador.', 'err');
      return;
    }
    setBadge('sensorBadge', 'limited', 'SIN ACTIVAR');
    button.addEventListener('click', function () {
      if (state.sensors.active) disableSensors();
      else enableSensors();
    });
  }

  /* ------------------------------------------------------------------ */
  /* Gráficas                                                            */
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
    var self = this;

    function pointX(index) { return index * stepX; }
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

  function MultiSpark(canvasId, options) {
    this.canvas = $(canvasId);
    this.ctx = this.canvas ? this.canvas.getContext('2d') : null;
    this.min = options.min;
    this.max = options.max;
    this.series = [[], [], []];
    this.maxPoints = 150;
    this.w = 0;
    this.h = 0;
    this.raf = null;
    if (this.canvas && this.ctx) {
      this.resize();
      if (typeof ResizeObserver === 'function') {
        var self = this;
        new ResizeObserver(function () { self.resize(); self.draw(); }).observe(this.canvas);
      }
    }
  }

  MultiSpark.prototype.resize = function () {
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

  MultiSpark.prototype.push = function (slot, value) {
    if (!Number.isFinite(value)) return;
    var arr = this.series[slot];
    if (!arr) return;
    arr.push(value);
    while (arr.length > this.maxPoints) arr.shift();
    this.schedule();
  };

  MultiSpark.prototype.schedule = function () {
    if (this.raf) return;
    var self = this;
    this.raf = requestAnimationFrame(function () {
      self.raf = null;
      self.draw();
    });
  };

  MultiSpark.prototype.draw = function () {
    if (!this.ctx || !this.w || !this.h) return;
    var ctx = this.ctx;
    var w = this.w;
    var h = this.h;
    var styles = getComputedStyle(root);
    var colors = [
      styles.getPropertyValue('--spark').trim() || '#9BE8C0',
      styles.getPropertyValue('--warn').trim() || '#F2C879',
      styles.getPropertyValue('--muted').trim() || '#8C93A0'
    ];
    var border = styles.getPropertyValue('--border').trim() || '#24282F';

    ctx.clearRect(0, 0, w, h);
    ctx.strokeStyle = border;
    ctx.globalAlpha = 0.5;
    ctx.lineWidth = 1;
    [0.25, 0.5, 0.75].forEach(function (ratio) {
      var y = Math.round(h * ratio) + 0.5;
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(w, y);
      ctx.stroke();
    });
    ctx.globalAlpha = 1;

    var range = this.max - this.min;
    var minValue = this.min;
    var maxValue = this.max;
    function yFor(value) {
      return h - 7 - ((clamp(value, minValue, maxValue) - minValue) / range) * (h - 14);
    }

    var self = this;
    this.series.forEach(function (points, index) {
      if (!points.length) return;
      var stepX = points.length > 1 ? w / (self.maxPoints - 1) : 0;
      var offset = self.maxPoints - points.length;
      ctx.beginPath();
      points.forEach(function (value, i) {
        var x = (offset + i) * stepX;
        var y = yFor(value);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.strokeStyle = colors[index];
      ctx.lineWidth = 1.5;
      ctx.lineJoin = 'round';
      ctx.lineCap = 'round';
      ctx.stroke();
    });
  };

  /* ------------------------------------------------------------------ */
  /* Pruebas / benchmarks                                                */
  /* ------------------------------------------------------------------ */

  var CPU_WORKER_SRC = [
    'self.onmessage = function (e) {',
    '  var duration = e.data.duration || 1500;',
    '  var end = performance.now() + duration;',
    '  var ops = 0, reps = 0;',
    '  var x = 123456789, y = 362436069, z = 521288629, w = 88675123, t;',
    '  while (performance.now() < end) {',
    '    for (var i = 0; i < 50000; i++) {',
    '      t = x ^ (x << 11);',
    '      x = y; y = z; z = w;',
    '      w = (w ^ (w >>> 19) ^ (t ^ (t >>> 8))) >>> 0;',
    '      ops += 6;',
    '    }',
    '    reps++;',
    '  }',
    '  self.postMessage({ ops: ops, reps: reps, ms: duration });',
    '};'
  ].join('\n');

  // Las barras usan escala logarítmica: el número mostrado siempre es la medida real.
  var TEST_SCALES = { cpu1: 10000, cpum: 100000, mem: 100000, stg: 100000, gpu: 10000000 };

  function cpuWorker(duration) {
    return new Promise(function (resolve, reject) {
      if (typeof Worker !== 'function') { reject(new Error('Web Workers no disponible')); return; }
      var blob = new Blob([CPU_WORKER_SRC], { type: 'application/javascript' });
      var url = URL.createObjectURL(blob);
      var worker = new Worker(url);
      var finished = false;
      var timer = setTimeout(function () {
        if (finished) return;
        finished = true;
        worker.terminate();
        URL.revokeObjectURL(url);
        reject(new Error('El worker no respondió a tiempo'));
      }, duration + 5000);

      function cleanup() {
        clearTimeout(timer);
        worker.terminate();
        URL.revokeObjectURL(url);
      }

      worker.onmessage = function (event) {
        if (finished) return;
        finished = true;
        cleanup();
        resolve(event.data);
      };
      worker.onerror = function (event) {
        if (finished) return;
        finished = true;
        cleanup();
        reject(new Error(event.message || 'Error en el worker'));
      };
      worker.postMessage({ duration: duration });
    });
  }

  function cpuPhase(threads) {
    var duration = 1500;
    var jobs = [];
    for (var i = 0; i < threads; i += 1) jobs.push(cpuWorker(duration));
    return Promise.all(jobs).then(function (results) {
      var ops = 0;
      var reps = 0;
      results.forEach(function (r) { ops += r.ops; reps += r.reps; });
      return {
        threads: threads,
        seconds: duration / 1000,
        opsPerSec: ops / (duration / 1000),
        reps: reps,
        totalOps: ops
      };
    });
  }

  function memoryTest() {
    return new Promise(function (resolve, reject) {
      var MB = 64;
      var elements = (MB * 1024 * 1024) / 8;
      var arr;

      try {
        // Calentamiento de 8 MB para no medir los fallos de página iniciales.
        var warm = new Float64Array((8 * 1024 * 1024) / 8);
        for (var w = 0; w < warm.length; w += 1) warm[w] = w * 0.25;
        var warmRead = 0;
        for (var wr = 0; wr < warm.length; wr += 1) warmRead += warm[wr];
        void warmRead;
        warm = null;
      } catch (e) { /* sin calentamiento */ }

      try {
        arr = new Float64Array(elements);
      } catch (e) {
        reject(new Error('No se pudo reservar 64 MB: ' + (e.message || e.name)));
        return;
      }

      setTimeout(function () {
        try {
          var t0 = performance.now();
          for (var i = 0; i < elements; i += 1) arr[i] = i * 0.5;
          var t1 = performance.now();
          var sum = 0;
          for (var j = 0; j < elements; j += 1) sum += arr[j];
          var t2 = performance.now();
          arr = null;
          resolve({
            sizeMB: MB,
            write: MB / ((t1 - t0) / 1000),
            read: MB / ((t2 - t1) / 1000),
            checksum: sum
          });
        } catch (e) {
          arr = null;
          reject(new Error('La prueba de memoria falló: ' + (e.message || e.name)));
        }
      }, 30);
    });
  }

  function storageTest() {
    return new Promise(function (resolve, reject) {
      if (!window.indexedDB) { reject(new Error('IndexedDB no está disponible')); return; }
      var MB = 8;
      var totalBytes = MB * 1024 * 1024;
      var open;

      try {
        open = indexedDB.open('lumen-bench', 1);
      } catch (e) {
        reject(new Error('No se pudo abrir IndexedDB: ' + (e.message || e.name)));
        return;
      }

      open.onupgradeneeded = function () {
        if (!open.result.objectStoreNames.contains('blobs')) open.result.createObjectStore('blobs');
      };
      open.onerror = function () {
        reject(open.error || new Error('No se pudo abrir IndexedDB'));
      };
      open.onsuccess = function () {
        var db = open.result;
        var data;
        try {
          data = new Uint8Array(totalBytes);
          for (var i = 0; i < data.length; i += 4096) data[i] = i & 255;
        } catch (e) {
          db.close();
          reject(new Error('No se pudo crear el bloque de 8 MB: ' + (e.message || e.name)));
          return;
        }

        var tx = null;
        try {
          tx = db.transaction('blobs', 'readwrite');
        } catch (e) {
          db.close();
          reject(new Error('No se pudo iniciar la transacción: ' + (e.message || e.name)));
          return;
        }

        var t0 = performance.now();
        tx.objectStore('blobs').put(data, 'payload');
        tx.oncomplete = function () {
          var t1 = performance.now();
          var tx2 = db.transaction('blobs', 'readonly');
          var req = tx2.objectStore('blobs').get('payload');
          req.onsuccess = function () {
            var t2 = performance.now();
            var result = req.result;
            var verified = !!(result && result.byteLength === totalBytes);
            var tx3 = db.transaction('blobs', 'readwrite');
            tx3.objectStore('blobs').delete('payload');
            tx3.oncomplete = function () { db.close(); };
            resolve({
              sizeMB: MB,
              write: MB / ((t1 - t0) / 1000),
              read: MB / ((t2 - t1) / 1000),
              verified: verified
            });
          };
          req.onerror = function () {
            db.close();
            reject(req.error || new Error('La lectura de IndexedDB falló'));
          };
        };
        tx.onerror = function () {
          db.close();
          reject(tx.error || new Error('La escritura en IndexedDB falló'));
        };
      };
    });
  }

  function gpuTest() {
    return new Promise(function (resolve, reject) {
      var size = 512;
      var canvas = document.createElement('canvas');
      canvas.width = size;
      canvas.height = size;
      var gl = null;
      try {
        gl = canvas.getContext('webgl2', { antialias: false, alpha: false, depth: false, powerPreference: 'high-performance' })
          || canvas.getContext('webgl', { antialias: false, alpha: false, depth: false, powerPreference: 'high-performance' });
      } catch (e) { gl = null; }
      if (!gl) { reject(new Error('WebGL no disponible o bloqueado')); return; }

      function compile(type, source) {
        var shader = gl.createShader(type);
        gl.shaderSource(shader, source);
        gl.compileShader(shader);
        if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
          var log = gl.getShaderInfoLog(shader);
          gl.deleteShader(shader);
          throw new Error('Shader: ' + log);
        }
        return shader;
      }

      var program = null;
      try {
        var vs = compile(gl.VERTEX_SHADER, 'attribute vec2 p; void main(){ gl_Position = vec4(p, 0.0, 1.0); }');
        var fs = compile(gl.FRAGMENT_SHADER, 'precision mediump float; uniform float u; void main(){ gl_FragColor = vec4(fract(u + gl_FragCoord.x * 0.001), gl_FragCoord.y / 512.0, 0.6, 1.0); }');
        program = gl.createProgram();
        gl.attachShader(program, vs);
        gl.attachShader(program, fs);
        gl.linkProgram(program);
        if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
          throw new Error('Programa: ' + gl.getProgramInfoLog(program));
        }
        gl.useProgram(program);
      } catch (e) {
        reject(new Error('No se pudo preparar WebGL: ' + (e.message || e.name)));
        return;
      }

      var buffer = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([
        -1, -1, 1, -1, -1, 1,
        -1, 1, 1, -1, 1, 1
      ]), gl.STATIC_DRAW);
      var location = gl.getAttribLocation(program, 'p');
      gl.enableVertexAttribArray(location);
      gl.vertexAttribPointer(location, 2, gl.FLOAT, false, 0, 0);
      var uLoc = gl.getUniformLocation(program, 'u');
      gl.viewport(0, 0, size, size);
      gl.clearColor(0.05, 0.06, 0.07, 1);
      gl.clear(gl.COLOR_BUFFER_BIT);

      try {
        for (var warm = 0; warm < 20; warm += 1) {
          gl.uniform1f(uLoc, warm * 0.01);
          gl.drawArrays(gl.TRIANGLES, 0, 6);
        }
        gl.finish();
      } catch (e) {
        reject(new Error('WebGL no pudo dibujar: ' + (e.message || e.name)));
        return;
      }

      var calls = 0;
      var start = performance.now();
      var duration = 700;
      try {
        while (performance.now() - start < duration) {
          for (var i = 0; i < 25; i += 1) {
            gl.uniform1f(uLoc, calls * 0.001);
            gl.drawArrays(gl.TRIANGLES, 0, 6);
            calls += 1;
          }
          gl.finish();
        }
      } catch (e) {
        reject(new Error('La prueba WebGL se interrumpió: ' + (e.message || e.name)));
        return;
      }
      var elapsed = performance.now() - start;
      var renderer = null;
      try {
        var debug = gl.getExtension('WEBGL_debug_renderer_info');
        renderer = debug ? gl.getParameter(debug.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER);
      } catch (e) { renderer = null; }

      var lose = gl.getExtension('WEBGL_lose_context');
      if (lose) { try { lose.loseContext(); } catch (e) { /* nada */ } }

      resolve({
        calls: calls,
        seconds: elapsed / 1000,
        callsPerSec: calls / (elapsed / 1000),
        mpixPerSec: (calls * size * size) / (elapsed / 1000) / 1000000,
        renderer: renderer
      });
    });
  }

  function loadResult(key) {
    try {
      var raw = localStorage.getItem(key);
      if (!raw) return null;
      var parsed = JSON.parse(raw);
      return parsed && parsed.at ? parsed : null;
    } catch (e) { return null; }
  }

  function saveResult(key, value) {
    try {
      if (value) localStorage.setItem(key, JSON.stringify(value));
      else localStorage.removeItem(key);
    } catch (e) { /* cuota o modo privado */ }
  }

  function deltaValue(cur, prev) {
    if (!Number.isFinite(cur) || !Number.isFinite(prev) || prev === 0) return null;
    return ((cur - prev) / prev) * 100;
  }

  function paintDelta(id, cur, prev) {
    var el = $(id);
    if (!el) return;
    var d = deltaValue(cur, prev);
    if (d === null) {
      el.hidden = true;
      el.textContent = '';
      return;
    }
    var abs = Math.abs(d);
    el.hidden = false;
    el.className = 'test-delta ' + (d > 0.05 ? 'up' : d < -0.05 ? 'down' : 'flat');
    el.textContent = 'vs anterior: ' + (d > 0 ? '+' : d < 0 ? '−' : '±') + num(abs, abs < 10 ? 1 : 0) + ' %';
  }

  function setTestValue(key, value, status, meta, deltaCur, deltaPrev) {
    var valEl = $(key + '-val');
    var barEl = $(key + '-bar');
    var metaEl = $(key + '-meta');
    var stEl = $(key + '-st');

    if (value === null || value === undefined) {
      if (valEl) valEl.textContent = '—';
      if (barEl) barEl.style.width = '0%';
    } else {
      if (valEl) valEl.textContent = num(value, value >= 100 ? 0 : 1);
      if (barEl) {
        var pct = value > 0 ? (Math.log10(value) / Math.log10(TEST_SCALES[key])) * 100 : 0;
        barEl.style.width = clamp(pct, 0, 100).toFixed(1) + '%';
      }
    }
    if (metaEl) metaEl.textContent = meta || '';
    if (stEl) {
      stEl.className = 'st ' + status;
      stEl.textContent = status === 'real' ? 'REAL' : status === 'limited' ? 'LIMITADO' : 'NO DISPONIBLE';
    }
    paintDelta(key + '-delta', deltaCur, deltaPrev);
  }

  function renderResults(result, previous) {
    if (!result || !result.tests) return;
    var t = result.tests;
    var prev = previous && previous.tests ? previous.tests : {};

    setText('testStamp', 'Última ejecución: ' + dateLabel(result.at) + ' · guardada en este navegador');

    if (t.cpu1 && !t.cpu1.error) {
      setTestValue('cpu1', t.cpu1.score, 'real',
        intFmt.format(t.cpu1.opsPerSec) + ' ops/s · ' + intFmt.format(t.cpu1.reps) + ' repeticiones · ' + num(t.cpu1.seconds, 1) + ' s',
        t.cpu1.score, prev.cpu1 && !prev.cpu1.error ? prev.cpu1.score : null);
    } else {
      setTestValue('cpu1', null, 'na', t.cpu1 && t.cpu1.error ? t.cpu1.error : 'No se ejecutó.');
    }

    if (t.cpuMulti && !t.cpuMulti.error) {
      setTestValue('cpum', t.cpuMulti.score, 'real',
        intFmt.format(t.cpuMulti.threads) + ' hilos · ' + intFmt.format(t.cpuMulti.opsPerSec) + ' ops/s · eficiencia ' + num(t.cpuMulti.efficiency, 0) + ' %',
        t.cpuMulti.score, prev.cpuMulti && !prev.cpuMulti.error ? prev.cpuMulti.score : null);
    } else {
      setTestValue('cpum', null, 'na', t.cpuMulti && t.cpuMulti.error ? t.cpuMulti.error : 'No se ejecutó.');
    }

    if (t.memory && !t.memory.error) {
      setTestValue('mem', t.memory.write, 'real',
        'Escritura ' + num(t.memory.write, 0) + ' MB/s · lectura ' + num(t.memory.read, 0) + ' MB/s · ' + t.memory.sizeMB + ' MB',
        t.memory.write, prev.memory && !prev.memory.error ? prev.memory.write : null);
    } else {
      setTestValue('mem', null, 'na', t.memory && t.memory.error ? t.memory.error : 'No se ejecutó.');
    }

    if (t.storage && !t.storage.error) {
      setTestValue('stg', t.storage.write, 'real',
        'Escritura ' + num(t.storage.write, 0) + ' MB/s · lectura ' + num(t.storage.read, 0) + ' MB/s · ' + t.storage.sizeMB + ' MB IndexedDB' + (t.storage.verified ? ' · verificado' : ''),
        t.storage.write, prev.storage && !prev.storage.error ? prev.storage.write : null);
    } else {
      setTestValue('stg', null, 'na', t.storage && t.storage.error ? t.storage.error : 'No se ejecutó.');
    }

    if (t.gpu && !t.gpu.error) {
      var renderer = t.gpu.renderer ? String(t.gpu.renderer) : 'renderer no expuesto';
      if (renderer.length > 70) renderer = renderer.slice(0, 68) + '…';
      setTestValue('gpu', t.gpu.callsPerSec, 'real',
        intFmt.format(t.gpu.callsPerSec) + ' draws/s · ' + num(t.gpu.mpixPerSec, 1) + ' MPix/s · ' + renderer,
        t.gpu.callsPerSec, prev.gpu && !prev.gpu.error ? prev.gpu.callsPerSec : null);
    } else {
      setTestValue('gpu', null, 'na', t.gpu && t.gpu.error ? t.gpu.error : 'No se ejecutó.');
    }

    var summary = [];
    [['cpu1', 'CPU 1 hilo'], ['cpuMulti', 'CPU multihilo'], ['memory', 'Memoria'], ['storage', 'Almacenamiento'], ['gpu', 'GPU']].forEach(function (pair) {
      var cur = t[pair[0]];
      var before = prev[pair[0]];
      if (!cur || cur.error || !before || before.error) return;
      var field = pair[0] === 'cpu1' ? 'score' : pair[0] === 'cpuMulti' ? 'score' : pair[0] === 'memory' ? 'write' : pair[0] === 'storage' ? 'write' : 'callsPerSec';
      var d = deltaValue(cur[field], before[field]);
      if (d === null) return;
      summary.push(pair[1] + ' ' + (d > 0 ? '+' : d < 0 ? '−' : '±') + num(Math.abs(d), Math.abs(d) < 10 ? 1 : 0) + ' %');
    });
    setText('testDelta', summary.length
      ? 'Comparación con la ejecución anterior: ' + summary.join(' · ')
      : (previous ? 'La ejecución anterior no tiene valores comparables.' : 'Primera ejecución guardada. La próxima vez se comparará automáticamente.'));

    state.tests.lastRunAt = result.at;
    setBadge('testsStatusBadge', 'real', 'COMPLETADO');
  }

  function setProgress(pct, label) {
    var fill = $('testProgressFill');
    var bar = $('testProgress');
    if (fill) fill.style.width = clamp(pct, 0, 100).toFixed(1) + '%';
    if (bar) bar.setAttribute('aria-valuenow', String(Math.round(pct)));
    if (label) setText('testStatus', label);
  }

  function setTestButtons(running) {
    var run = $('runTests');
    var rerun = $('rerunTests');
    if (run) {
      run.disabled = running;
      run.textContent = running ? 'Ejecutando…' : 'Ejecutar pruebas';
    }
    if (rerun) rerun.disabled = running;
  }

  function runAllTests() {
    if (state.tests.running) return Promise.resolve();
    state.tests.running = true;
    setTestButtons(true);
    setBadge('testsStatusBadge', 'limited', 'EJECUTANDO');
    setProgress(0, 'Preparando las pruebas…');

    var previous = loadResult(LAST_KEY);
    var result = { at: new Date().toISOString(), app: 'Lumen Web', version: APP_VERSION, tests: {} };
    var single = null;
    var t0 = performance.now();
    var threads = Math.max(1, Math.min(navigator.hardwareConcurrency || 1, 8));

    // Cada fase captura su propio fallo: si la GPU no existe, las demás pruebas siguen.
    function step(run, key, map) {
      return run().then(function (value) {
        result.tests[key] = map ? map(value) : value;
      }).catch(function (error) {
        result.tests[key] = { error: error && error.message ? error.message : String(error) };
      });
    }

    return delay(80).then(function () {
      setProgress(6, 'CPU · 1 hilo: bucle entero de 1,5 s…');
      return step(function () { return cpuPhase(1); }, 'cpu1', function (r) {
        single = r;
        return {
          score: r.opsPerSec / 1000000,
          opsPerSec: r.opsPerSec,
          reps: r.reps,
          seconds: r.seconds
        };
      });
    }).then(function () {
      setProgress(28, 'CPU · ' + threads + ' hilos en paralelo (1,5 s)…');
      return step(function () { return cpuPhase(threads); }, 'cpuMulti', function (r) {
        return {
          score: r.opsPerSec / 1000000,
          opsPerSec: r.opsPerSec,
          reps: r.reps,
          seconds: r.seconds,
          threads: r.threads,
          efficiency: single ? (r.opsPerSec / (single.opsPerSec * threads)) * 100 : null
        };
      });
    }).then(function () {
      setProgress(52, 'Memoria: escritura y lectura de 64 MB…');
      return delay(40).then(function () { return step(memoryTest, 'memory'); });
    }).then(function () {
      setProgress(70, 'Almacenamiento: 8 MB en IndexedDB…');
      return delay(40).then(function () { return step(storageTest, 'storage'); });
    }).then(function () {
      setProgress(84, 'GPU: dibujando 512 × 512 durante 0,7 s…');
      return delay(40).then(function () { return step(gpuTest, 'gpu'); });
    }).then(function () {
      var elapsed = performance.now() - t0;
      saveResult(PREV_KEY, previous);
      saveResult(LAST_KEY, result);
      renderResults(result, previous);
      setProgress(100, 'Completado en ' + num(elapsed / 1000, 1) + ' s. Puedes repetir la prueba cuando quieras.');
      setBadge('testsStatusBadge', 'real', 'COMPLETADO');
      state.tests.running = false;
      setTestButtons(false);
      var rerun = $('rerunTests');
      if (rerun) rerun.hidden = false;
      return result;
    }).catch(function (error) {
      state.tests.running = false;
      setTestButtons(false);
      setBadge('testsStatusBadge', 'na', 'ERROR');
      setProgress(0, 'No se pudieron ejecutar las pruebas: ' + (error && error.message ? error.message : 'error desconocido'));
    });
  }

  function initTests() {
    var run = $('runTests');
    var rerun = $('rerunTests');
    if (!run) return;

    var last = loadResult(LAST_KEY);
    var previous = loadResult(PREV_KEY);
    if (last) {
      renderResults(last, previous);
      setProgress(100, 'Resultado guardado. Pulsa «Repetir prueba» para medir de nuevo y comparar.');
      if (rerun) rerun.hidden = false;
    } else {
      setProgress(0, 'Pulsa «Ejecutar pruebas». Duración aproximada: 4–6 segundos. No se bloquea la interfaz: la CPU se mide en Web Workers.');
    }

    run.addEventListener('click', function () { runAllTests(); });
    if (rerun) rerun.addEventListener('click', function () { runAllTests(); });
  }

  /* ------------------------------------------------------------------ */
  /* Gráficas en vivo                                                    */
  /* ------------------------------------------------------------------ */

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

    setMetric('ram', num(usedMb, usedMb < 10 ? 1 : 0), 'MB', 'Heap JS · límite ' + bytes(limit), 'real', 'Solo mide el heap de esta pestaña.');
    setText('live-ram', bytes(used) + ' · ' + num(percentage, 1) + '%');
    setField('hw-heap', bytes(used), 'real');
    setField('mem-used', bytes(used), 'real');
    setField('mem-limit', bytes(limit), 'real');
    setField('mem-free', bytes(Math.max(0, limit - used)), 'real');
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
      if (accGraph) { accGraph.resize(); accGraph.draw(); }
      if (gyroGraph) { gyroGraph.resize(); gyroGraph.draw(); }
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
      var value = row.querySelector('.v');
      if (!value) return;
      var key = value.dataset.key || row.dataset.key;
      if (!key) return;
      var cell = value.closest('.vcell');
      var noteEl = cell ? cell.querySelector('.row-note') : null;
      rows[key] = {
        value: value.textContent.trim(),
        status: value.dataset.status || 'na',
        note: noteEl && !noteEl.hidden ? noteEl.textContent.trim() : ''
      };
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
      version: APP_VERSION,
      generatedAt: new Date().toISOString(),
      url: location.href,
      theme: root.dataset.theme,
      device: {
        name: $('deviceName') ? $('deviceName').textContent.trim() : null,
        os: state.info.os,
        osVersion: state.info.osVersion,
        browser: state.info.browser,
        browserVersion: state.info.browserVersion,
        kind: state.info.kind,
        formFactor: state.info.formFactor,
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
        samples: state.sensors.samples,
        calibrated: state.sensors.calibrated,
        last: state.sensors.last
      },
      tests: loadResult(LAST_KEY),
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
    prepareRows();
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
    initCodecs();
    initMedia();
    initPermissions();
    initApis();
    initSensors();
    initTests();
    initLive();
    initNav();
    initReportActions();
    registerServiceWorker();

    finalizePending();
    setTimeout(finalizePending, 4000);

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
