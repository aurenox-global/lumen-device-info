# Lumen Web

Informe local del dispositivo desde el navegador, con **benchmarks reales** ejecutados en el equipo. Interfaz minimalista propia inspirada en la información de DevCheck, sin publicidad, sin cuentas, sin cookies y sin telemetría. Todo se calcula en la página; no hay llamadas a servicios externos.

**Versión 1.1.0.** Esta versión sustituye la sensación de demo por mediciones y estados explícitos: cada dato se marca como `REAL`, `LIMITADO` o `NO DISPONIBLE`, con una nota que explica el motivo cuando el navegador recorta, estima u oculta la información.

## Características

- **Inicio** — equipo y sistema detectados (modelo UA-CH si existe; si no, «Equipo de escritorio», «Móvil» o «Tablet», nunca un «Mac» sin contexto), cuatro métricas rápidas con su estado y tres gráficas en vivo (FPS, heap JS y batería) que se pausan al ocultar la pestaña.
- **Hardware** — CPU, arquitectura y plataforma UA-CH, GPU con renderer WebGL sin enmascarar y adaptador WebGPU, pantalla (CSS/física, DPR, refresco **medido con `requestAnimationFrame`**, gama, HDR, orientación, puntos táctiles) y memoria.
- **Batería** — nivel, estado y tiempos. Si el navegador devuelve el valor fijo `100 % + cargando` típico de equipos sin batería, se marca como limitado y se explica.
- **Red** — estado, tipo, `effectiveType`, `downlink`, `rtt` y ahorro de datos, siempre como estimación del navegador.
- **Almacenamiento** — uso y cuota del origen, desglose y botón para solicitar `navigator.storage.persist()` con explicación del resultado.
- **Sensores** — acelerómetro y giroscopio con gráficas en vivo de tres ejes, valores numéricos, brújula y botón **«Poner a cero»** que resta la media de las últimas muestras. Permiso explícito en iOS/iPadOS. Si la API existe pero no llegan datos (habitual en escritorio), se dice claramente.
- **Pruebas** — benchmarks ejecutables con un clic real:
  - CPU 1 hilo y multihilo con Web Workers (hasta 8), bucle entero de 1,5 s por fase, puntuación en Mops/s, repeticiones y eficiencia de escalado.
  - Memoria: escritura y lectura de un `Float64Array` de 64 MB, en MB/s.
  - Almacenamiento: escritura y lectura de 8 MB en IndexedDB, con verificación del bloque, en MB/s.
  - GPU: WebGL 2/1 con 512 × 512 píxeles, `gl.finish()` por lotes, draws/s y MPix/s.
  - Barra de progreso, botones bloqueados mientras corre, resultado guardado en `localStorage` y comparación porcentual con la ejecución anterior.
- **Navegador** — UA completo, UA-CH de alta entropía, plataforma, zona horaria, idiomas, WebAssembly validado, códecs con `MediaCapabilities` (o `canPlayType` marcado como limitado), permisos y 28 Web APIs.
- **Cámara y micrófono** — petición de permiso bajo demanda con `getUserMedia`, conteo real de dispositivos y cierre inmediato de la captura. Si se deniega, se explica el error.
- Tema oscuro/claro persistido, «Copiar informe» en JSON (incluye el estado de cada dato y el último resultado de las pruebas), «Compartir» si existe `navigator.share`.
- PWA instalable con manifest, iconos 192/512/maskable y service worker cache-first versionado (`lumen-v1.1.0`).

## Verificación real

Servido con `python3 -m http.server 8899` y controlado por CDP con Brave 155 (Chrome 155) en un MacBook Pro M1 Pro, macOS 26.5.1:

- `index.html`, `styles.css`, `app.js`, `sw.js`, `manifest.webmanifest`, `favicon.svg` e iconos: **HTTP 200**.
- Carga a **390 × 844** y **1440 × 900**; consola **sin errores**.
- **360 px**: `document.documentElement.scrollWidth === innerWidth` (360 = 360), sin scroll horizontal.
- Pruebas ejecutadas con clic real en el botón (CDP `Input.dispatchMouseEvent`), dos veces seguidas:
  - CPU 1 hilo: **≈ 1380 Mops/s** · 6902 repeticiones · 1,5 s.
  - CPU multihilo: **≈ 10 405 Mops/s** con 8 hilos · eficiencia 94 %.
  - Memoria: **≈ 6154 MB/s escritura**, 6214 MB/s lectura · 64 MB.
  - Almacenamiento: **≈ 1176 MB/s escritura**, 20 000 MB/s lectura · 8 MB IndexedDB verificado.
  - GPU: **≈ 692 758 draws/s**, 181 602 MPix/s · ANGLE Metal Renderer Apple M1 Pro.
  - Repetición: comparación mostrada (`CPU 1 hilo +1,2 %`, `GPU −1 %`, etc.) y `lumen-tests-prev-v1` guardado.
- Capturas actualizadas: `../shots/web-mobile.png`, `../shots/web-desktop.png`, `../shots/web-tests.png` y `../shots/web-360.png`.

Los números son de esa máquina y esa sesión; sirven para comparar el equipo consigo mismo, no como ranking entre dispositivos.

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

Abre <http://localhost:8899>. El service worker y el manifest solo se activan en `https` o `localhost`/`127.0.0.1`. Abrir el archivo con `file://` también funciona para consultar datos; el navegador no permite instalar la PWA ni registrar el service worker desde `file://`.

## Publicar en GitHub Pages

Sube el contenido de `web/` (no la carpeta padre) a la rama de Pages o a `/docs`. No hace falta compilar ni configurar nada: todas las rutas son relativas (`./`). Para invalidar una versión cacheada, cambia el nombre de `CACHE` en `sw.js`.

## Limitaciones reales del navegador

- No se puede leer el modelo de CPU/SoC, la salud, temperatura o voltaje de la batería ni el almacenamiento físico total. `navigator.deviceMemory` es un valor redondeado y `performance.memory` mide solo el heap de la pestaña (Chromium).
- La Battery Status API no existe en Safari/Firefox. En equipos de escritorio sin batería algunos navegadores devuelven un valor fijo; Lumen lo marca como limitado.
- Network Information API solo está en navegadores Chromium y algunos derivados la desactivan (Brave Shields). No es una prueba de velocidad.
- La cuota de almacenamiento es la del origen web, no la del disco.
- Los sensores dependen del hardware y del permiso. En escritorio es normal que la API exista pero no entregue muestras; en iOS/iPadOS el permiso exige un toque y un contexto seguro. La brújula requiere magnetómetro.
- La lectura de IndexedDB puede servirse desde la caché en memoria del navegador; la cifra de escritura es la más representativa del almacenamiento. En GPUs con ejecución diferida, los draws/s miden comandos aceptados por el driver, no píxeles sombreados.
- Los valores de los benchmarks varían con el calor, el ahorro de energía y la carga del sistema. El navegador headless usa ANGLE/Metal o SwiftShader, no necesariamente el mismo camino que una ventana normal.
- `MediaCapabilities` no está en todos los navegadores; cuando falta se usa `canPlayType`, que solo responde «quizá» y se marca como limitado.
- Chrome en macOS congela parte del user agent (macOS 10.15.7); los Client Hints de alta entropía dan la versión real cuando están disponibles.
- Permisos y APIs presentes cambian según navegador y versión; «Preguntar» no concede nada por sí solo.

## Accesibilidad

Contraste AA en ambos temas, foco visible, navegación por teclado, `aria-label` en controles de icono, `aria-current` en la navegación, estados con texto (no solo color), regiones etiquetadas y respeto por `prefers-reduced-motion`.

## Créditos

Inspirado en la información que muestra DevCheck (flar2). Diseño, código e iconografía propios. Lumen Web 1.1.0.
