#!/system/bin/sh
# Capture boot diagnostics to /data (this firmware has no logcatd).
{
  echo "=== slot ==="; getprop ro.boot.slot_suffix; getprop ro.boot.slot
  echo "=== fingerprint ==="; getprop ro.build.fingerprint
  echo "=== home packages ==="; pm list packages | grep -iE "honda|chg.launcher"
} > /data/honda-info.log 2>&1
logcat -d -b main -b system -b crash -b events > /data/honda-boot.log 2>&1
logcat -d -b main -b system 2>/dev/null | grep -iE "honda.launcher|PackageManager|PackageParser|Scanning|Deleting|Failed to|INSTALL_|No home" > /data/honda-pm.log 2>&1
dmesg > /data/honda-dmesg.log 2>&1
chmod 666 /data/honda-*.log 2>/dev/null
exec logcat -b main -b system -b crash -b events -f /data/honda-live.log -r 4096 -n 4
