# Lumen Web

Informe local del dispositivo desde el navegador. Interfaz minimalista propia inspirada en la información de DevCheck, sin publicidad, sin cuentas, sin cookies y sin telemetría. Todo se calcula en la página; no hay llamadas a servicios externos.

## Características

- **Inicio** — dispositivo y sistema detectados, cuatro métricas rápidas (heap JS, almacenamiento, batería, núcleos) y tres gráficas en vivo (FPS, RAM del heap y batería) que se pausan al ocultar la pestaña.
- **Hardware** — CPU y arquitectura, GPU con renderer WebGL sin enmascarar, adaptador WebGPU cuando existe, pantalla (CSS/física, DPR, refresco estimado, gama, HDR, orientación, táctil) y memoria.
- **Batería** — nivel, estado, tiempos de carga/descarga e historial en vivo mediante Battery Status API.
- **Red** — estado, tipo, `effectiveType`, `downlink`, `rtt` y ahorro de datos mediante Network Information API.
- **Almacenamiento** — uso y cuota del origen con `storage.estimate`, estado persistente y desglose cuando el navegador lo expone.
- **Sensores** — acelerómetro, giroscopio, orientación y brújula con botón «Activar sensores» (permiso explícito en iOS/iPadOS) y medidores en vivo.
- **Navegador** — user agent, User-Agent Client Hints de alta entropía, plataforma, idiomas, zona horaria, cookies, DNT, dispositivos de media, permisos consultados sin avisos y compatibilidad de 26 Web APIs.
- Tema oscuro/claro persistido y sincronizado con `prefers-color-scheme`; «Copiar informe» en JSON; «Compartir» cuando `navigator.share` existe.
- PWA instalable con manifest, iconos 192/512/maskable y service worker cache-first versionado.

Cada dato no disponible se muestra como «No disponible» con el motivo en el `title`/`aria-label` del valor (enfocable con teclado), nunca como un error de consola.

## Estructura

```
web/
├── index.html
├── styles.css
├── app.js
├── manifest.webmanifest
├── sw.js
├── favicon.svg
├── icons/
│   ├── icon.svg
│   ├── icon-192.png
│   ├── icon-512.png
│   └── icon-maskable-512.png
└── README.md
```

HTML + CSS + JavaScript vanilla. Sin dependencias, sin build y sin rutas absolutas: funciona en la raíz de un dominio, en una subruta de GitHub Pages y al abrir `index.html` con doble clic.

## Servir en local

```bash
cd web
python3 -m http.server 8899
```

Abre <http://localhost:8899>. El service worker y el manifest solo se activan en `https` o `localhost`/`127.0.0.1`. Abrir el archivo con `file://` también funciona para consultar todos los datos; el navegador no permite instalar la PWA ni registrar el service worker desde `file://`.

## Publicar en GitHub Pages

Sube el contenido de `web/` (no la carpeta padre) a la rama de Pages o a `/docs`. No hace falta compilar ni configurar nada: todas las rutas son relativas (`./`). Para actualizar una versión ya cacheada, cambia el nombre de `CACHE` en `sw.js` (por ejemplo `lumen-v1.0.1`) para invalidar la caché anterior.

## Limitaciones reales del navegador

- No se puede leer el modelo de CPU, el SoC, la salud/temperatura/voltaje de la batería ni el almacenamiento físico total. La RAM es `performance.memory` (heap de la pestaña, solo Chromium) y `navigator.deviceMemory` (valor aproximado y redondeado).
- La Battery Status API no existe en Safari/Firefox; el nivel puede redondearse y los tiempos devolver `Infinity`.
- Network Information API solo está en navegadores Chromium y algunos derivados la desactivan (Brave Shields, por ejemplo); no es una prueba de velocidad.
- La cuota de almacenamiento es la del origen web, no la del disco. El desglose por tipo no está disponible en todos los navegadores.
- Los sensores dependen del hardware y del permiso. En escritorio es normal que no lleguen datos; en iOS/iPadOS el permiso exige un toque y un contexto seguro.
- Los permisos consultados y las APIs presentes cambian según navegador y versión; «Preguntar» no concede nada por sí solo.
- Chrome en macOS congela parte del user agent (macOS 10.15.7) y los Client Hints solo están en Chromium.

## Accesibilidad

Contraste AA en ambos temas, foco visible, navegación por teclado, `aria-label` en controles de icono, `aria-current` en la navegación, regiones etiquetadas y respeto por `prefers-reduced-motion`.

## Verificación

Probado con Brave 155 (Chromium) servido en `http://127.0.0.1:8899` y controlado por CDP:

- `index.html`, `styles.css`, `app.js`, `manifest.webmanifest`, `sw.js` e iconos: HTTP 200.
- Capturas a 390×844 y 1440×900 en `../shots/web-mobile.png` y `../shots/web-desktop.png`; también 360×800 (`web-360.png`) y tema claro (`web-mobile-light.png`, `web-desktop-light.png`).
- Sin overflow horizontal (`document.scrollWidth === innerWidth`) a 360, 390 y 1440 px.
- Consola sin errores; solo un aviso del navegador al abrir con `file://` sobre cookies, que no afecta a la app.
- Informe copiado verificado como JSON válido (2.001 caracteres) en el portapapeles.
- Service worker activo y recarga sin conexión funcionando.
- `file://` carga correctamente estilos, datos y secciones; el manifest se inyecta solo por HTTP para evitar el error CORS del protocolo local.

## Créditos

Inspirado en la información que muestra DevCheck (flar2). Diseño, código e iconografía propios. Lumen Web 1.0.0.
