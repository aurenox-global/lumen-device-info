#!/bin/bash
# Verificación visual de Lumen 1.2.0 en emulador. Todo en un solo flujo:
# el emulador muere al terminar la sesión de shell.
set -u
cd /Users/zota/.openclaw/workspace/projects/lumen

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
EMU="$HOME/Library/Android/sdk/emulator/emulator"

pkill -f qemu-system 2>/dev/null
"$ADB" kill-server >/dev/null 2>&1
sleep 2

nohup "$EMU" -avd RV_API34 -no-window -no-audio -gpu host -no-snapshot > /tmp/lumen-emu-verify.log 2>&1 &
EMUPID=$!

echo "== esperando emulador =="
for i in $(seq 1 45); do
  sleep 4
  st=$("$ADB" get-state 2>/dev/null)
  [ "$st" = "device" ] && break
done
echo "estado: $("$ADB" get-state 2>/dev/null)"

for i in $(seq 1 40); do
  b=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$b" = "1" ] && break
  sleep 3
done
echo "boot: $("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"

"$ADB" install -r lumen-debug.apk 2>&1 | tail -1
"$ADB" shell pm clear com.lumen.deviceinfo >/dev/null 2>&1
"$ADB" shell am force-stop com.lumen.deviceinfo
"$ADB" shell am start -n com.lumen.deviceinfo/.MainActivity 2>&1 | tail -1
sleep 10
"$ADB" exec-out screencap -p > shots/android-inicio.png

tap_text() {
  local name="$1" out="$2"
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  "$ADB" pull /sdcard/ui.xml /tmp/lumen-ui.xml >/dev/null 2>&1
  local coords
  coords=$(python3 - "$name" <<'PY'
import sys, re
name = sys.argv[1]
try:
    xml = open('/tmp/lumen-ui.xml', encoding='utf-8').read()
except Exception:
    sys.exit(0)
for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    if m.group(1).strip().lower() == name.lower():
        x = (int(m.group(2)) + int(m.group(4))) // 2
        y = (int(m.group(3)) + int(m.group(5))) // 2
        print(f"{x} {y}")
        break
PY
)
  echo "tap '$name' -> ${coords:-fallback}"
  if [ -n "$coords" ]; then
    # shellcheck disable=SC2086
    "$ADB" shell input tap $coords
  else
    "$ADB" shell input tap 324 2260
  fi
  sleep 4
  "$ADB" exec-out screencap -p > "$out"
}

tap_text "Hardware" shots/android-hardware.png
tap_text "Batería" shots/android-bateria.png

echo "== tamaño de capturas =="
ls -lh shots/android-inicio.png shots/android-hardware.png shots/android-bateria.png shots/android-mas.png
echo "== md5 =="
md5 shots/android-inicio.png shots/android-hardware.png shots/android-bateria.png shots/android-mas.png
echo "== crashes =="
"$ADB" logcat -d -t 400 | grep -i "FATAL EXCEPTION" | tail -5

kill "$EMUPID" 2>/dev/null
echo "== fin =="
