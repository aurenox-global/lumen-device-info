# Lumen — web de presentación

Sitio estático (HTML + CSS + JS, sin dependencias ni build) que presenta la app Android
Lumen y permite descargarla.

> Esta web **no lee el dispositivo**. Un navegador no tiene acceso al hardware como una app nativa,
> así que aquí no se simulan datos: solo se presenta la app.

## Identidad visual — ColorOS 17 «Living Flow»

La landing sigue el sistema de diseño de ColorOS 17 descrito en `../DESIGN-COLOROS17.md`:

- **Tema claro por defecto**, como ColorOS, con toggle a oscuro persistido en `localStorage`
  (`lumen-theme`). También se puede forzar con `?theme=light` o `?theme=dark`.
- Fondo con tres blobs difusos (aqua `#B9F5E4`, cielo `#C9DDFF`, lavanda `#E8DBFF`) y tarjetas
  glass con `backdrop-filter`, borde translúcido y sombra difusa. El tema oscuro usa los mismos
  acentos a baja opacidad sobre `#070C10`.
- Superficies de 22/28/32 px y botones, chips y etiquetas en píldora (999 px).
- Tipografía del sistema, títulos 700 con tracking `-0.03em`, cuerpo en `#5C6B76` y números con
  `tabular-nums`. Sin fuentes externas, sin monoespaciada y sin emoji.
- Movimiento de 180–260 ms con `cubic-bezier(0.22, 1, 0.36, 1)`, respetando
  `prefers-reduced-motion`.
- Foco visible, objetivos táctiles de 44 px y contraste AA en textos.

## Contenido

- Hero con captura real de la app, chips de metadatos y descarga directa del APK 1.2.0.
- Ocho bloques de características (hardware, batería, eSIM, red, sensores, apps, benchmarks, privacidad).
- Galería de capturas reales de la app en marcos redondeados.
- Instalación en tres pasos.
- Límites honestos (salud de batería, EID/eSIM, temperaturas, memoria por app) como tarjeta destacada.
- FAQ con `<details>` nativos estilizados.
- Cierre con degradado ambiental aqua/cielo/lavanda.

## Archivos

```
index.html            Página completa
styles.css            Sistema visual (variables para tema claro/oscuro)
app.js                Tema persistido, metadatos de color y limpieza de SW antiguo
sw.js                 Kill switch: borra cachés de la versión PWA anterior y se desregistra
manifest.webmanifest  Metadatos de instalación (tema claro)
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

## Capturas de verificación

El script `../scripts/preview.mjs` mide overflow horizontal, consola e imágenes, y guarda una
captura de página completa. Requiere el servidor vivo en el mismo comando:

```bash
cd ..
(cd web && python3 -m http.server 8899 &) && sleep 1
node scripts/preview.mjs 390 844 shots/web-mobile.png
node scripts/preview.mjs 1440 900 shots/web-desktop.png
node scripts/preview.mjs 1440 900 shots/web-desktop-light.png
```

## Publicación

GitHub Pages sirve la carpeta `docs/` de la rama `main`. Para actualizar:

```bash
rm -rf docs && mkdir docs && cp -R web/. docs/ && rm -f docs/README.md
git add docs && git commit -m "web: ..." && git push
```

La descarga apunta a la release `v1.2.0`:

`https://github.com/aurenox-global/lumen-device-info/releases/download/v1.2.0/lumen-debug.apk`

## Nota sobre el service worker

La versión 1.1 de esta web era una PWA que cacheaba una demo. `sw.js` ahora vacía las cachés y se
desregistra al activarse, para que nadie siga viendo la versión antigua.
