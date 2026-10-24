# Investigación: qué hace DevCheck y cómo se traduce en Lumen

Referencias: ficha de Google Play (`flar2.devcheck`), [devcheck.app/app](https://devcheck.app/app),
[guía/FAQ oficial](https://devcheck.app/faq) y capturas públicas de la versión 6.x.

## Mapa de funciones de DevCheck

| Área | Qué muestra DevCheck | En Lumen Android | En Lumen Web |
| --- | --- | --- | --- |
| Panel | Resumen del dispositivo y monitor en vivo | Sí (hero + métricas + sparklines) | Sí (hero + métricas + FPS/batería/RAM) |
| CPU | Modelo, núcleos, frecuencias mín/actual/máx, gobernador, carga, ABI | Sí, con frecuencias por núcleo desde sysfs y carga media | Parcial: `hardwareConcurrency`, UA-CH, sin frecuencias |
| GPU | Renderer, vendor, versión GLES, extensiones | Sí vía EGL | Sí vía `WEBGL_debug_renderer_info` y WebGPU |
| Pantalla | Resolución, densidad, refresco, HDR, tamaño | Sí | Sí (limitado por el navegador) |
| Memoria | Total, disponible, uso por app (root) | Total/disponible (sin uso por app) | `deviceMemory` y heap JS |
| Almacenamiento | Volúmenes, total/libre, velocidad | Sí (StatFs) | `storage.estimate` |
| Batería | Nivel, salud, ciclos, corriente, voltaje, potencia, temperatura, historial | Sí (lo que expone el driver) | Nivel y estado de carga; el resto no existe en la API web |
| Cámaras | Número, resoluciones, focal, ISO | Sí (`CameraManager`) | Solo número de dispositivos de vídeo |
| Sensores | Lista completa + valores en vivo | Sí (`SensorManager`) | Acelerómetro/giroscopio con permiso explícito |
| Red | WiFi/celular/ethernet, operador, señal, IPs, DNS | Sí | Parcial (Network Information API) |
| Apps | Lista buscable, permisos, instalador, manifest | Lista con icono, versión, targetSdk y tamaño | No existe en el navegador |
| Tests | Benchmark CPU/RAM/disco/GPU, tests de pantalla y batería | CPU, memoria y disco | No (solo FPS como referencia) |
| Extras | Widgets, monitores flotantes, Shizuku/root | No (requiere privilegios o servicios del sistema) | No |

## Decisiones de producto

1. **Sin permisos agresivos al arrancar.** DevCheck pide acceso root/Shizuku para temperaturas y ciclos de
   batería. Lumen no: muestra «No disponible» y mantiene la app utilizable en cualquier móvil.
2. **Misma estructura en las dos plataformas.** Inicio, Hardware, Batería, Sistema, Red, Sensores (y Apps/Tests
   solo donde tienen sentido). Así la web se entiende como una versión de consulta rápida.
3. **Un solo acento y jerarquía tipográfica.** DevCheck usa una paleta multicolor por categoría; Lumen usa
   menta sobre casi-negro para que los datos manden y la interfaz desaparezca.
4. **Cero red.** No hay catálogo de dispositivos, rankings ni envío de datos: todo se calcula en local.

## Limitaciones conocidas de Android

- Frecuencias y gobernador dependen de `/sys/devices/system/cpu/*`, bloqueado o ausente en muchos equipos.
- Temperaturas, ciclos de carga y salud real de batería suelen requerir root o Shizuku.
- `QUERY_ALL_PACKAGES` es válido para sideload, pero Google Play lo restringe.
- En emuladores, varios valores aparecen a 0 o «No disponible» porque no hay hardware real.
