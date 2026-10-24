# LUMEN — Sistema de diseño «ColorOS 17 / Living Flow»

Objetivo: aplicar la identidad visual de ColorOS 17 (Android 17, 2026) a la app Android y a la web,
sin copiar iconos propietarios. Rasgos: capas translúcidas, luz suave, esquinas muy redondeadas,
paleta aqua/azul con toques lavanda, tipografía grande y clara, movimiento elástico sutil.

## Paleta (usar exactamente estos valores)

### Tema claro (por defecto, como ColorOS)
- Fondo base: `#F4F8FA`
- Degradados ambientales (blobs difusos): `#B9F5E4` (aqua), `#C9DDFF` (cielo), `#E8DBFF` (lavanda)
- Superficie sólida: `#FFFFFF`
- Superficie glass: `rgba(255,255,255,0.72)` + desenfoque
- Borde glass: `rgba(255,255,255,0.85)`; sombra: `0 18px 50px -24px rgba(9,45,60,0.22)`
- Texto: `#0C1B22`; secundario: `#5C6B76`; terciario: `#8A99A3`
- Primario aqua: `#00BFA6`; primario profundo: `#008F7D`
- Secundario azul: `#3B82F6`; lavanda: `#8B5CF6`
- Éxito `#22C55E` · aviso `#F59E0B` · error `#EF4444`

### Tema oscuro
- Fondo base: `#070C10`
- Blobs: aqua `#0E5F55`, azul `#12325F`, lavanda `#3A2A66` (baja opacidad, 18–24 %)
- Superficie glass: `rgba(255,255,255,0.06)`; borde `rgba(255,255,255,0.10)`
- Texto: `#ECF6F8`; secundario: `#93A4AE`; terciario: `#6B7A84`
- Primario aqua: `#4DE0C8`; azul `#6AA1FF`; lavanda `#B69CFF`

## Forma y profundidad
- Tarjetas: radio 28 px (grandes) / 22 px (medianas) / 999 px (chips y botones).
- Bordes de 1 px translúcidos; sombras muy difusas; nada de bordes duros negros.
- Capas: fondo con 2–3 blobs de color desenfocados + superficies glass encima.
- Iconos: contenedor circular de 44 px con tinte del color al 12–16 % y trazo del color pleno.

## Tipografía
- Familia del sistema (SF Pro / Roboto), sin fuentes externas.
- Títulos: peso 700, tracking -0.03em, tamaño grande (hero 40–56, secciones 26–32).
- Cuerpo: 14–16 px, line-height 1.55, color secundario.
- Datos numéricos: peso 600, `font-variant-numeric: tabular-nums` (web) / `FontFeatureSettings "tnum"` (Compose). Sin monoespaciada.
- Etiquetas de sección: 11 px, mayúsculas, tracking 0.14em, color terciario.

## Componentes clave
- **Hero de dispositivo**: tarjeta grande con degradado aqua→azul muy suave, nombre real del dispositivo en grande,
  modelo/código y versión de Android debajo, y un anillo o brillo luminoso decorativo.
- **Stat tiles**: 4 tarjetas glass con icono circular tintado, valor grande y etiqueta corta.
- **Botones**: píldora, primario con degradado `#00BFA6 → #2DD4BF` y texto oscuro; secundario glass.
- **Bottom bar (app)**: píldora flotante glass con separación del borde, ítem activo con fondo tintado aqua.
- **Chips**: píldora con borde translúcido y fondo tintado; estado activo aqua.
- **Filas de datos**: etiqueta a la izquierda, valor a la derecha en peso 600; separador 1 px al 40 %.
- **Avisos**: tarjeta con icono circular, título corto y texto; nunca bloques de color chillones.

## Movimiento
- Transiciones de 180–260 ms, curva `cubic-bezier(0.22, 1, 0.36, 1)`.
- Aparición suave (fade + 8 px) al cargar; hover con elevación de 2 px.
- Respetar `prefers-reduced-motion` (web) y animaciones reducidas (Android).

## Accesibilidad
- Contraste AA en texto principal y secundario.
- Objetivos táctiles ≥ 44 px.
- No usar emoji; solo SVG/vectores geométricos.

## Aplicación
- **Android**: Material 3 recoloreado (light/dark), tarjetas `Surface` con blur nativo si API 31+ (o fondo glass simulado),
  radios 26–28 dp, bottom bar flotante, hero con Brush vertical/horizontal, tipografía sin mono.
- **Web**: variables CSS para ambos temas, blobs con `filter: blur()`, tarjetas `backdrop-filter`,
  hero y secciones con la misma jerarquía; conservar el contenido actual de la landing.
