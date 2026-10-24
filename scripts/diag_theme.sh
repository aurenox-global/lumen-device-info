#!/bin/bash
set -u
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
EMU="$HOME/Library/Android/sdk/emulator/emulator"
SHOTS="/Users/zota/.openclaw/workspace/projects/lumen/shots"
APK="/Users/zota/.openclaw/workspace/projects/lumen/lumen-debug.apk"

"$ADB" emu kill 2>/dev/null
pkill -f "qemu-system" 2>/dev/null
sleep 2
"$EMU" -avd RV_API34 -no-window -no-audio -gpu swiftshader_indirect -no-snapshot >/tmp/emu.log 2>&1 &
for i in $(seq 1 180); do
  state=$("$ADB" get-state 2>/dev/null)
  boot=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$state" = "device" ] && [ "$boot" = "1" ] && break
  sleep 1
done
"$ADB" shell input keyevent 82 >/dev/null 2>&1
sleep 2
"$ADB" install -r "$APK" >/dev/null 2>&1
"$ADB" shell am start -n com.lumen.deviceinfo/.MainActivity >/dev/null 2>&1
sleep 8

pref() { "$ADB" shell run-as com.lumen.deviceinfo cat shared_prefs/lumen_prefs.xml 2>/dev/null | tr -d '\r'; }

echo "== pref before =="
pref

# dump and find all nodes with text or content-desc + their clickable state, to find the toggle accurately
"$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
"$ADB" shell cat /sdcard/ui.xml 2>/dev/null > /tmp/ui.xml
echo "== all clickable/desc nodes =="
python3 - <<'PYEOF'
import re
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0)
    d = re.search(r'content-desc="([^"]*)"', n)
    t = re.search(r'text="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    c = 'clickable="true"' in n
    label = (d.group(1) if d else '') or (t.group(1) if t else '')
    if label and ('tema' in label.lower() or 'Alternar' in label or 'oscuro' in label.lower() or 'Inicio' == label or 'Más' == label):
        print(f'label={label!r} clickable={c} bounds={b.groups() if b else None}')
PYEOF

echo "== tap header toggle =="
"$ADB" shell input tap 966 227
sleep 3
echo "== pref after header toggle =="
pref

echo "== navigate to Settings via Más -> Ajustes, toggle Modo oscuro =="
# tap Más (bottom bar) then Ajustes
MCOORD=$(python3 - <<'PYEOF'
import re
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0)
    t = re.search(r'text="([^"]*)"', n)
    if t and t.group(1) == 'Más':
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            print((int(b.group(1))+int(b.group(3)))//2, (int(b.group(2))+int(b.group(4)))//2); break
PYEOF
)
if [ -n "$MCOORD" ]; then
  "$ADB" shell input tap $MCOORD; sleep 3
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  "$ADB" shell cat /sdcard/ui.xml 2>/dev/null > /tmp/ui.xml
  ACOORD=$(python3 - <<'PYEOF'
import re
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0)
    t = re.search(r'text="([^"]*)"', n)
    if t and t.group(1) == 'Ajustes':
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            print((int(b.group(1))+int(b.group(3)))//2, (int(b.group(2))+int(b.group(4)))//2); break
PYEOF
)
  echo "Ajustes coords: '$ACOORD'"
  if [ -n "$ACOORD" ]; then
    "$ADB" shell input tap $ACOORD; sleep 3
    "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    "$ADB" shell cat /sdcard/ui.xml 2>/dev/null > /tmp/ui.xml
    # find "Modo oscuro" switch (checkable node) or the text; tap the row
    python3 - <<'PYEOF'
import re
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0)
    t = re.search(r'text="([^"]*)"', n)
    if t and 'oscuro' in t.group(1).lower():
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b: print(f"OSCURO node text={t.group(1)!r} bounds={b.groups()} checkable={'checkable=\"true\"' in n}")
PYEOF
  fi
fi

echo "== pref final =="
pref
"$ADB" emu kill 2>/dev/null
pkill -f "qemu-system" 2>/dev/null
echo DONE
