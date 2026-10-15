#!/bin/bash
# Auto-build ProfilePilot v1.7.0 when VM memory frees up.
# Called by cron job 'profilepilot-v17-autobuild'.

MEM_KB=$(grep MemAvailable /proc/meminfo | awk '{print $2}')
MEM_MB=$((MEM_KB / 1024))
echo "MemAvailable: ${MEM_MB}MB"

if [ "$MEM_MB" -lt 2000 ]; then
  echo "Memory still low (${MEM_MB}MB < 2000MB). Skipping build."
  exit 0
fi

echo "Memory OK (${MEM_MB}MB). Starting build..."
cd /home/hatch/workspace/profilepilot-v17 || exit 1

export ANDROID_HOME=/home/hatch/android-sdk
export JAVA_HOME=/home/hatch/android-toolchain/jdk-17.0.20.1+1
export PATH="$JAVA_HOME/bin:$PATH"
# Persistent keystore password (same cert as installed app = update works, no data loss)
export PROFILEPILOT_STOREPASS=$(cat /home/hatch/workspace/android-keystore/.storepass)

/home/hatch/android-toolchain/gradle-8.7/bin/gradle assembleDebug --no-daemon --console=plain 2>&1 | tail -30

APK=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -1)
if [ -n "$APK" ]; then
  cp "$APK" /home/hatch/workspace/your_files/ProfilePilot-v1.7.0-debug.apk
  echo "BUILD SUCCESS: $APK -> your_files/ProfilePilot-v1.7.0-debug.apk"
  ls -lh /home/hatch/workspace/your_files/ProfilePilot-v1.7.0-debug.apk
else
  echo "BUILD FAILED: no APK produced"
  exit 1
fi
