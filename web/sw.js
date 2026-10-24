/*
 * Kill switch del service worker antiguo.
 * La versión 1.1 de la web era una PWA que cacheaba la app de pruebas.
 * Esta página es solo informativa: borra las cachés, se desregistra y recarga.
 */
self.addEventListener("install", function () {
  self.skipWaiting();
});

self.addEventListener("activate", function (event) {
  event.waitUntil(
    (async function () {
      try {
        var keys = await caches.keys();
        await Promise.all(keys.map(function (key) { return caches.delete(key); }));
      } catch (e) {}

      try {
        await self.registration.unregister();
      } catch (e) {}

      try {
        var clients = await self.clients.matchAll({ type: "window" });
        clients.forEach(function (client) { client.navigate(client.url); });
      } catch (e) {}
    })()
  );
});
