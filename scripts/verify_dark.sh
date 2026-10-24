#!/bin/bash
set -u
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
EMU="$HOME/Library/Android/sdk/emulator/emulator"
SHOTS="/Users/zota/.openclaw/workspace/projects/lumen/shots"
APK="/Users/zota/.openclaw/workspace/projects/lumen/lumen-debug.apk"
mkdir -p "$SHOTS"

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

echo "== capture light =="
"$ADB" exec-out screencap -p > "$SHOTS/android-inicio-light.png"

# dump hierarchy and find the theme toggle node (content-desc "Alternar tema")
"$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
"$ADB" shell cat /sdcard/ui.xml 2>/dev/null > /tmp/ui.xml
python3 - <<'PYEOF'
import re
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
# print all clickable nodes with content-desc or text for debugging
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0)
    d = re.search(r'content-desc="([^"]*)"', n)
    t = re.search(r'text="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b: continue
    clickable = 'clickable="true"' in n
    label = d.group(1) if d else (t.group(1) if t else '')
    if label and (clickable or 'Alternar' in label or 'tema' in label.lower()):
        print(f'{label!r} bounds=({b.group(1)},{b.group(2)})-({b.group(3)},{b.group(4)}) clickable={clickable}')
PYEOF

# tap the toggle by content-desc
TOGGLE=$(python3 - <<'PYEOF'
import re
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0)
    if 'Alternar' in n or 'tema' in n.lower():
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x = (int(b.group(1))+int(b.group(3)))//2
            y = (int(b.group(2))+int(b.group(4)))//2
            print(x, y); break
PYEOF
)
echo "toggle coords: '$TOGGLE'"
if [ -n "$TOGGLE" ]; then
  "$ADB" shell input tap $TOGGLE
  sleep 4
fi
"$ADB" exec-out screencap -p > "$SHOTS/android-inicio-dark.png"

echo "== pixel check (top-left background) =="
python3 - "$SHOTS/android-inicio-light.png" "$SHOTS/android-inicio-dark.png" <<'PYEOF'
import sys
from PIL import Image
for p in sys.argv[1:]:
    im = Image.open(p).convert('RGB')
    # sample a few background pixels (near top-left under status bar, between cards)
    pts = [(540, 1300), (30, 1200), (1000, 500)]
    vals = [im.getpixel(pt) for pt in pts]
    print(p.split('/')[-1], vals)
PYEOF

echo "== logcat fatal check =="
"$ADB" logcat -d 2>/dev/null | grep -i "FATAL EXCEPTION" || echo "NO_FATAL"

"$ADB" emu kill 2>/dev/null
pkill -f "qemu-system" 2>/dev/null
echo "DONE"
