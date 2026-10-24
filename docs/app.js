/* Lumen — landing page interactions (sin dependencias) */

(function () {
  "use strict";

  var STORAGE_KEY = "lumen-theme";

  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute("content", theme === "light" ? "#F7F8F9" : "#0A0B0D");
  }

  function initialTheme() {
    var saved = null;
    try { saved = localStorage.getItem(STORAGE_KEY); } catch (e) {}
    if (saved === "light" || saved === "dark") return saved;
    return window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
  }

  applyTheme(initialTheme());

  var btn = document.getElementById("theme-btn");
  if (btn) {
    btn.addEventListener("click", function () {
      var next = document.documentElement.getAttribute("data-theme") === "light" ? "dark" : "light";
      applyTheme(next);
      try { localStorage.setItem(STORAGE_KEY, next); } catch (e) {}
    });
  }

  // Desactiva el service worker antiguo (versión que cacheaba la app de pruebas).
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.getRegistration()
      .then(function (registration) {
        if (registration) return registration.update();
      })
      .catch(function () { /* sin soporte o bloqueado: no pasa nada */ });
  }
})();
