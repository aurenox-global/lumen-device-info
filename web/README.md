# Lumen — web de presentación

Sitio estático (HTML + CSS + JS, sin dependencias ni build) que explica qué hace la app Android
Lumen y permite descargarla.

> Esta web **no lee el dispositivo**. Un navegador no tiene acceso al hardware como una app nativa,
> así que aquí no se simulan datos: solo se presenta la app.

## Contenido

- Hero con captura real de la app y descarga directa del APK.
- Ocho bloques de características (hardware, batería, eSIM, red, sensores, apps, benchmarks, privacidad).
- Galería de capturas reales de la app.
- Instalación en tres pasos.
- Sección de límites honestos (salud de batería, EID/eSIM, temperaturas, memoria por app).
- FAQ con `<details>` nativos.
- Tema oscuro/claro persistido, animaciones suaves y `prefers-reduced-motion`.

## Archivos

```
index.html            Página completa
styles.css            Diseño (variables para tema claro/oscuro)
app.js                Tema, revelado al hacer scroll, limpieza de SW antiguo
sw.js                 Kill switch: borra cachés de la versión PWA anterior y se desregistra
manifest.webmanifest  Metadatos de instalación
img/                  Capturas reales de la app Android
icons/                Iconos del sitio
favicon.svg
```

## Ver en local

```bash
cd web
python3 -m http.server 8899
# http://localhost:8899
```

## Publicación

GitHub Pages sirve la carpeta `docs/` de la rama `main`. Para actualizar:

```bash
rm -rf docs && mkdir docs && cp -R web/. docs/ && rm -f docs/README.md
git add docs && git commit -m "web: ..." && git push
```

## Nota sobre el service worker

La versión 1.1 de esta web era una PWA que cacheaba una demo. `sw.js` ahora vacía las cachés y se
desregistra al activarse, para que nadie siga viendo la versión antigua.
