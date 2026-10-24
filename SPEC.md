# LUMEN — Device & System Info (inspirado en DevCheck, UI propia minimalista)

Objetivo: dos productos con la misma identidad visual y la misma información que DevCheck (flar2.devcheck),
pero con diseño UI minimalista, moderno y limpio:

1. App Android nativa (Kotlin + Jetpack Compose, Material 3) — `projects/lumen/android/`
2. Web app estática (HTML/CSS/JS sin build, PWA) — `projects/lumen/web/`

Sin publicidad, sin cuentas, sin telemetría, sin llamadas de red. Todo se lee en el dispositivo.

## Identidad visual (obligatoria, ambos productos)

- Fondo: #0A0B0D (dark) / #F7F8F9 (light)
- Superficie: #121417 / #FFFFFF, superficie 2: #191C20 / #F1F2F4
- Borde: #24282F / #E4E7EB
- Texto: #ECEEF2 / #14161A, secundario #8C93A0 / #697080
- Acento único: #9BE8C0 (menta). Acento suave para fondos: acento al 12% de alfa.
- Semánticos: ok #9BE8C0, warn #F2C879, danger #F28B82
- Tipografía: sistema (SF/Roboto/Inter si existe). Valores numéricos en monospace (`ui-monospace, SFMono-Regular, Menlo`).
- Radios: 16–20px tarjetas, 10–12px chips/botones. Bordes 1px. Sombras casi inexistentes (solo elevación sutil).
- Mucho aire: padding 20px en pantalla, 16px en tarjetas, separación 12px entre tarjetas.
- Iconos: SVG inline (web) / vectores Compose (Android), trazo 1.5–1.75, 24px, geométricos. NUNCA emoji.
- Sin gradientes llamativos: máximo un glow muy sutil del acento en la cabecera.
- Títulos de sección en mayúsculas pequeñas (11px, tracking 0.12em, color secundario).
- Datos: fila etiqueta (secundario) + valor (mono, alineado a la derecha). Separador 1px muy tenue.

## Estructura de información (misma en app y web, adaptada a lo que expone cada plataforma)

1. **Inicio / Dashboard**: nombre del dispositivo, modelo/SoC, versión de SO; grid de 4 métricas rápidas
   (RAM usada, almacenamiento usado, batería %, CPU); gráfica/sparkline en vivo; accesos a secciones.
2. **Hardware**: CPU (núcleos, min/max/actual por núcleo, ABI, gobernador), GPU (renderer/vendor/GLES),
   Pantalla (resolución, densidad, refresco, HDR, tamaño"), Memoria (total/disponible), Almacenamiento
   (volúmenes total/libre), Cámaras (número, resoluciones, focal), Bluetooth básico.
3. **Batería**: nivel, estado, salud, enchufado, temperatura, voltaje, tecnología, corriente, potencia,
   capacidad estimada; historial/sparkline de nivel y temperatura.
4. **Sistema**: Android/SDK, parche de seguridad, kernel, build ID, fingerprint, uptime, bootloader,
   board/hardware, ABIs, locale, zona horaria, desarrollador/OEM.
5. **Red**: transporte (wifi/celular/ethernet), operador, tipo de red, roaming, SIM, IPv4/IPv6, DNS,
   MTU, interfaz, ancho de banda, intensidad de señal.
6. **Sensores**: lista agrupada con nombre, fabricante, tipo, rango/resolución y valores en vivo
   (acelerómetro, giroscopio, magnetómetro, luz, proximidad).
7. **Apps** (solo Android): lista buscable, filtro usuario/sistema, paquete, versión, targetSdk, tamaño APK, icono.
8. **Tests** (solo Android): benchmark CPU 1 hilo / multihilo, memoria, escritura/lectura de almacenamiento,
   puntuación y historial local.
9. **Ajustes**: tema (sistema/claro/oscuro), unidades, exportar informe (compartir texto/JSON), acerca de.

## App Android — requisitos técnicos

- Directorio: `/Users/zota/.openclaw/workspace/projects/lumen/android`
- Kotlin 2.2.0, AGP 8.9.2, Gradle wrapper 8.11.1, compileSdk 35, targetSdk 35, minSdk 26.
- SDK: `/Users/zota/Library/Android/sdk` (escribe `local.properties`, gitignored).
- Compose BOM 2025.04.01, Material3, Navigation-compose (o navegación propia con estado; preferible sin nav-compose
  si añade riesgo), sin Hilt/Room/Retrofit. Dependencias mínimas.
- Arquitectura simple: `data/` (lectores del sistema, sin Context filtrado), `ui/` (pantallas + tema + componentes),
  `MainActivity`, `LumenApp`. ViewModel con StateFlow + actualización periódica cada 2 s solo en pantalla visible.
- Debe compilar: `./gradlew assembleDebug` sin errores. APK final copiado a `projects/lumen/lumen-debug.apk`.
- Icono adaptativo vectorial propio (fondo oscuro, marca "pulso" menta). Nombre visible: "Lumen".
- Permisos: solo los imprescindibles y sin pedirlos al arrancar:
  `QUERY_ALL_PACKAGES` (lista de apps), `ACCESS_NETWORK_STATE`, `BLUETOOTH_CONNECT` (opcional, con try/catch).
  Nada de ubicación ni teléfono. Todo lo que falle debe degradar a "No disponible", nunca crashear.
- UI: bottom bar con 5 destinos (Inicio, Hardware, Batería, Sistema, Más) y "Más" agrupa Red/Sensores/Apps/Tests/Ajustes.
  Alternativa: barra superior con chips horizontales scrollables. Prioriza que se vea limpio y que no se corten textos.
- Modo oscuro por defecto, con toggle claro/oscuro.
- Rendimiento: sin recomposiciones locas; listas con keys; lecturas pesadas fuera del hilo principal.
- Verifica en emulador si hay espacio (`RV_API34`): instala, abre, captura pantallas de Inicio/Hardware/Batería y
  revisa que no haya crash en logcat. Guarda capturas en `projects/lumen/shots/`.

## Web — requisitos técnicos

- Directorio: `/Users/zota/.openclaw/workspace/projects/lumen/web`
- `index.html`, `styles.css`, `app.js`, `manifest.webmanifest`, `sw.js`, `favicon.svg`, `icons/` (SVG + PNG 192/512 generados).
- Cero dependencias, cero build, funciona en GitHub Pages y también abriendo el HTML en local.
- APIs reales del navegador: userAgentData (high entropy), screen/DPR/colorDepth, hardwareConcurrency,
  deviceMemory, performance.memory (si existe), storage.estimate, getBattery, Network Information,
  WebGL/WebGPU renderer, mediaDevices (solo conteo, sin pedir permiso), permissions.query,
  DeviceOrientation/Motion (con botón "Activar sensores" en iOS), Intl/timezone, FPS con rAF.
- Lo no soportado se muestra como "No disponible" con un tooltip corto que explica por qué.
- Misma estructura de secciones, navegación lateral en desktop y tabs/chips en móvil, tema claro/oscuro persistido,
  botón "Copiar informe" (JSON) y "Compartir" (navigator.share si existe).
- PWA instalable, responsive real a 360px, sin overflow horizontal, sin emoji, iconos SVG.
- Accesibilidad: contraste AA, foco visible, aria-labels.
- Comprueba con un servidor local y, si es posible, con captura de pantalla (Playwright/Chrome headless) a 390x844
  y 1440x900; guarda capturas en `projects/lumen/shots/web-*.png`.

## Entregables

- Android: proyecto completo + APK debug funcionando.
- Web: carpeta estática lista para GitHub Pages.
- README en la raíz del repo con capturas, características, limitaciones y cómo compilar/publicar.
- El padre (Tini) se encarga de git/GitHub Pages y de la entrega.
