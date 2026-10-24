#!/bin/bash
set -u
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
EMU="$HOME/Library/Android/sdk/emulator/emulator"
SHOTS="/Users/zota/.openclaw/workspace/projects/lumen/shots"
APK="/Users/zota/.openclaw/workspace/projects/lumen/lumen-debug.apk"
mkdir -p "$SHOTS"

echo "== stop any existing emulator =="
"$ADB" emu kill 2>/dev/null
pkill -f "qemu-system" 2>/dev/null
sleep 2

echo "== starting emulator =="
"$EMU" -avd RV_API34 -no-window -no-audio -gpu swiftshader_indirect -no-snapshot >/tmp/emu.log 2>&1 &
echo "emulator pid $!"

echo "== waiting for boot (up to 180s) =="
booted=0
for i in $(seq 1 180); do
  state=$("$ADB" get-state 2>/dev/null)
  boot=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  if [ "$state" = "device" ] && [ "$boot" = "1" ]; then booted=1; break; fi
  sleep 1
done
echo "booted=$booted after ~${i}s"
if [ "$booted" != "1" ]; then echo "EMULATOR DID NOT BOOT"; "$ADB" emu kill 2>/dev/null; exit 1; fi

"$ADB" shell input keyevent 82 >/dev/null 2>&1
sleep 2

echo "== device name sources =="
echo "device_name(global): $("$ADB" shell settings get global device_name 2>/dev/null | tr -d '\r')"
echo "marketname: $("$ADB" shell getprop ro.product.marketname 2>/dev/null | tr -d '\r')"
echo "odm.marketname: $("$ADB" shell getprop ro.product.odm.marketname 2>/dev/null | tr -d '\r')"
echo "vendor.marketname: $("$ADB" shell getprop ro.vendor.product.marketname 2>/dev/null | tr -d '\r')"
echo "system.marketname: $("$ADB" shell getprop ro.system.product.marketname 2>/dev/null | tr -d '\r')"
echo "ro.product.model: $("$ADB" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
echo "ro.product.manufacturer: $("$ADB" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r')"

echo "== install =="
"$ADB" install -r "$APK" 2>&1 | tail -3

SIZE=$("$ADB" shell wm size | tr -d '\r')
W=$(echo "$SIZE" | sed -E 's/.*: *([0-9]+)x([0-9]+).*/\1/')
H=$(echo "$SIZE" | sed -E 's/.*: *([0-9]+)x([0-9]+).*/\2/')
echo "W=$W H=$H"

"$ADB" shell am start -n com.lumen.deviceinfo/.MainActivity >/dev/null 2>&1
sleep 8
"$ADB" logcat -c 2>/dev/null

echo "== capture inicio (light) =="
"$ADB" exec-out screencap -p > "$SHOTS/android-inicio.png"
"$ADB" exec-out screencap -p > "$SHOTS/android-inicio-light.png"

# Dump UI hierarchy for exact bottom-bar bounds.
tap_node() { # text
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  "$ADB" shell cat /sdcard/ui.xml 2>/dev/null > /tmp/ui.xml
  # find node with text=... and parse bounds="[x1,y1][x2,y2]"
  python3 - "$1" <<'PYEOF'
import re, sys
want = sys.argv[1]
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
# match nodes that have the desired text and a clickable bounds
for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    t, x1, y1, x2, y2 = m.group(1), *map(int, m.groups()[1:])
    if want.lower() in t.lower():
        print((x1+x2)//2, (y1+y2)//2)
        break
PYEOF
}

tap_text() {
  local coords
  coords=$(tap_node "$1")
  if [ -n "$coords" ]; then
    "$ADB" shell input tap $coords
    echo "tapped '$1' at $coords"
  else
    echo "WARN: node '$1' not found"
  fi
}

echo "== capture hardware =="
tap_text "Hardware"
sleep 3
"$ADB" exec-out screencap -p > "$SHOTS/android-hardware.png"

echo "== capture bateria =="
tap_text "Batería"
sleep 3
"$ADB" exec-out screencap -p > "$SHOTS/android-bateria.png"

echo "== capture mas =="
tap_text "Más"
sleep 3
"$ADB" exec-out screencap -p > "$SHOTS/android-mas.png"

echo "== back to inicio =="
tap_text "Inicio"
sleep 3

echo "== toggle theme (header button) -> dark =="
# theme toggle is top-right of the Inicio header; use uiautomator if it exposes one,
# else fall back to fixed coords.
TG=$(python3 - "$W" "$H" <<'PYEOF'
import sys
w, h = int(sys.argv[1]), int(sys.argv[2])
print(w - 40*2.75, 150)
PYEOF
)
"$ADB" shell input tap $TG
sleep 3
"$ADB" exec-out screencap -p > "$SHOTS/android-inicio-dark.png"

echo "== logcat fatal check =="
"$ADB" logcat -d 2>/dev/null | grep -i "FATAL EXCEPTION" || echo "NO_FATAL"

echo "== kill emulator =="
"$ADB" emu kill 2>/dev/null
pkill -f "qemu-system" 2>/dev/null
echo "DONE"
