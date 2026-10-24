# Lumen — Android

App Android nativa de información de dispositivo, inspirada en DevCheck con una UI
minimalista propia (tema oscuro por defecto, acento menta `#9BE8C0`, tipografía de
sistema con valores en monospace).

## Stack

- Kotlin 2.2.0 · Jetpack Compose (Material 3) · Compose BOM 2025.04.01
- AGP 8.9.2 · Gradle wrapper 8.11.1
- compileSdk 35 · targetSdk 35 · minSdk 26 · Java/Kotlin target 17
- Sin Hilt / Room / Retrofit. Solo `androidx.*` y Material Icons Extended.

## Estructura

- `data/` — lectores del sistema (`DeviceReader`, `SensorReader`, `Benchmark`),
  sin filtrar el `Context`.
- `ui/` — tema, componentes y pantallas (Inicio, Hardware, Batería, Sistema,
  Más → Red, Sensores, Apps, Tests, Ajustes).
- `LumenViewModel` — `StateFlow` + refresco en vivo cada 2 s solo con la app
  visible; lecturas pesadas en `Dispatchers.IO`.

## Compilar

```bash
cd projects/lumen/android
./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
```

Requisitos: JDK 17 y `local.properties` con `sdk.dir=/Users/zota/Library/Android/sdk`.

## Pantallas

1. **Inicio** — hero del dispositivo, 4 métricas rápidas, sparkline CPU/RAM en vivo,
   accesos a secciones.
2. **Hardware** — CPU (frecuencias por núcleo), GPU (EGL), pantalla, memoria,
   almacenamiento, cámaras, Bluetooth.
3. **Batería** — nivel, estado, salud, temperatura, voltaje, corriente, potencia,
   tecnología, capacidad.
4. **Sistema** — Android/SDK, parche, kernel, build, fingerprint, uptime, ABIs, locale.
5. **Red** — transporte, operador, SIM, IPs, DNS, MTU, señal.
6. **Sensores** — lista con valores en vivo.
7. **Apps** — lista buscable con iconos, filtro usuario/sistema, versión, targetSdk, tamaño.
8. **Tests** — benchmark CPU (1 hilo/multihilo), memoria, storage con puntuación e
   historial en `SharedPreferences`.
9. **Ajustes** — tema claro/oscuro/sistema, exportar informe (`ACTION_SEND`), acerca de.

## Permisos

- `QUERY_ALL_PACKAGES` (lista de apps), `ACCESS_NETWORK_STATE`, `BLUETOOTH_CONNECT`
  (solo lectura, con try/catch, sin pedir al arrancar).

## Limitaciones

- Lecturas de frecuencia de CPU dependen de `/sys/devices/system/cpu/...`, no
  disponibles en todos los dispositivos (se degrada a "No disponible").
- Frecuencia de CPU por núcleo y gobernador requieren acceso al sistema de archivos
  de `/sys` (emulador/sin root puede no exponerlos).
- GPU vía EGL reporta vendor/versión GLES; el renderer físico puede no estar
  disponible en todos los dispositivos.
- El historial de batería es una nota de lugar en la UI; el benchmark guarda
  historial real en `SharedPreferences`.
