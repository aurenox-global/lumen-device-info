/* Lumen — landing page interactions (sin dependencias) */

(function () {
  "use strict";

  var STORAGE_KEY = "lumen-theme";
  var button = document.getElementById("theme-btn");

  function applyTheme(theme) {
    var root = document.documentElement;
    root.setAttribute("data-theme", theme);

    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute("content", theme === "dark" ? "#070C10" : "#F4F8FA");

    if (button) {
      var isDark = theme === "dark";
      button.setAttribute("aria-pressed", isDark ? "true" : "false");
      button.setAttribute("aria-label", isDark ? "Activar tema claro" : "Activar tema oscuro");
    }
  }

  function initialTheme() {
    // Permite ?theme=dark|light para capturas y enlaces directos.
    try {
      var forced = new URLSearchParams(window.location.search).get("theme");
      if (forced === "light" || forced === "dark") return forced;
    } catch (e) {}

    var saved = null;
    try { saved = localStorage.getItem(STORAGE_KEY); } catch (e) {}
    if (saved === "light" || saved === "dark") return saved;

    // ColorOS usa tema claro por defecto.
    return "light";
  }

  applyTheme(initialTheme());

  if (button) {
    button.addEventListener("click", function () {
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
