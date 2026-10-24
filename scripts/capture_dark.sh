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

echo "== light capture =="
"$ADB" exec-out screencap -p > "$SHOTS/android-inicio-light.png"
"$ADB" exec-out screencap -p > "$SHOTS/android-inicio.png"

# find toggle via uiautomator content-desc
"$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
"$ADB" shell cat /sdcard/ui.xml 2>/dev/null > /tmp/ui.xml
TOGGLE=$(python3 - <<'PYEOF'
import re
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0)
    if 'Alternar tema' in n:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            print((int(b.group(1))+int(b.group(3)))//2, (int(b.group(2))+int(b.group(4)))//2); break
PYEOF
)
echo "toggle: '$TOGGLE'"
if [ -n "$TOGGLE" ]; then
  "$ADB" shell input tap $TOGGLE
  sleep 3
fi
echo "== pref after toggle =="
"$ADB" shell run-as com.lumen.deviceinfo cat shared_prefs/lumen_prefs.xml 2>/dev/null | grep 'name="theme"'
"$ADB" exec-out screencap -p > "$SHOTS/android-inicio-dark.png"

echo "== logcat fatal check =="
"$ADB" logcat -d 2>/dev/null | grep -i "FATAL EXCEPTION" || echo "NO_FATAL"

"$ADB" emu kill 2>/dev/null
pkill -f "qemu-system" 2>/dev/null
echo DONE
