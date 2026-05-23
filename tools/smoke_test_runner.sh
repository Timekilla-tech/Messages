#!/bin/bash

###############################################################################
# SMOKE TEST RUNNER — Fossify Messages (Final)
#
# Purpose: Automate pre-defense smoke test verification
# Usage: ./tools/smoke_test_runner.sh [device-id]
# Example: ./tools/smoke_test_runner.sh emulator-5554
#
# This script:
# 1. Builds the APK
# 2. Installs on device/emulator
# 3. Grants permissions
# 4. Launches the app
# 5. Monitors logcat (filtered by app PID)
# 6. Reports PASS if no crash within 10 seconds, otherwise FAIL
###############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DEVICE_ID="${1:-100.124.101.75:5555}"
PACKAGE_NAME="org.fossify.messages.debug"
BUILD_LOG="smoke_test_build_$(date +%Y%m%d_%H%M%S).log"
CRASH_DETECTED=0

echo -e "${BLUE}=== Fossify Messages Smoke Test Runner ===${NC}"
echo -e "${BLUE}Date: $(date)${NC}"
echo -e "${BLUE}Device: ${DEVICE_ID}${NC}"
echo -e "${BLUE}Package: ${PACKAGE_NAME}${NC}"
echo

# Function: Print section header
section() {
    echo -e "\n${BLUE}[$(date +%H:%M:%S)]${NC} ${YELLOW}$1${NC}"
}

# Function: Print success
success() {
    echo -e "${GREEN}✅ $1${NC}"
}

# Function: Print error
error() {
    echo -e "${RED}❌ $1${NC}"
}

# Function: Print info
info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# 1. VERIFY DEVICE CONNECTION
section "Step 1: Verifying device connection"
if adb devices | grep -q "$DEVICE_ID"; then
    success "Device $DEVICE_ID found"
else
    error "Device $DEVICE_ID not found. Available devices:"
    adb devices
    exit 1
fi

# 2. BUILD APK
section "Step 2: Building APK (skipping detekt for speed)"
if ./gradlew assembleDebug -x detekt >> "$BUILD_LOG" 2>&1; then
    success "APK build successful"
    # Find the newest debug APK
    APK_FILE=$(find app/build/outputs/apk -type f \
        -path '*/debug/*.apk' \
        ! -name '*androidTest.apk' \
        ! -name '*unaligned.apk' \
        | sort -r | head -1)
    if [ -z "$APK_FILE" ]; then
        error "APK file not found!"
        info "Available APK outputs:"
        find app/build/outputs/apk -type f \( -name '*.apk' -o -name '*.apks' \) | sort
        exit 1
    fi
    info "APK: $APK_FILE"
else
    error "APK build failed. Check $BUILD_LOG"
    tail -20 "$BUILD_LOG"
    exit 1
fi

# 3. UNINSTALL PREVIOUS VERSION
section "Step 3: Clearing previous installation"
adb -s "$DEVICE_ID" uninstall "$PACKAGE_NAME" > /dev/null 2>&1 || true
success "Previous version uninstalled (or didn't exist)"

# 4. INSTALL APK
section "Step 4: Installing APK on device"
if adb -s "$DEVICE_ID" install -g "$APK_FILE" >> "$BUILD_LOG" 2>&1; then
    success "APK installed successfully"
else
    error "APK installation failed. Check $BUILD_LOG"
    exit 1
fi

# 5. GRANT PERMISSIONS
section "Step 5: Granting required permissions"
PERMISSIONS=(
    "android.permission.READ_SMS"
    "android.permission.READ_CONTACTS"
    "android.permission.SEND_SMS"
    "android.permission.READ_PHONE_STATE"
)

for perm in "${PERMISSIONS[@]}"; do
    adb -s "$DEVICE_ID" shell pm grant "$PACKAGE_NAME" "$perm" > /dev/null 2>&1 || true
done
success "Permissions granted"

# 6. CLEAR LOGS AND PREPARE MONITORING
section "Step 6: Clearing device logs"
adb -s "$DEVICE_ID" logcat -c
success "Logcat cleared"

# Function to get app PID
get_app_pid() {
    adb -s "$DEVICE_ID" shell pidof -s "$PACKAGE_NAME" 2>/dev/null || echo ""
}

# 7. LAUNCH APP
section "Step 7: Launching application"
# Use monkey to launch the default launcher activity
adb -s "$DEVICE_ID" shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
success "App launched"

# Wait for app process to start (up to 10 seconds)
info "Waiting for app process..."
for i in {1..10}; do
    PID=$(get_app_pid)
    if [ -n "$PID" ]; then
        success "App PID: $PID"
        break
    fi
    sleep 1
done

if [ -z "$PID" ]; then
    error "App process not found within 10 seconds"
    exit 1
fi

# 8. START LOGCAT MONITORING (filtered by PID)
section "Step 8: Monitoring logcat for crashes"
LOG_MONITOR_FILE="logcat_$(date +%Y%m%d_%H%M%S).txt"
# Use a temporary file to capture logs from the monitoring background process
MONITOR_TMP=$(mktemp)

# Start logcat filtered by PID and write to both monitor file and temp file
adb -s "$DEVICE_ID" logcat --pid="$PID" | tee "$LOG_MONITOR_FILE" > "$MONITOR_TMP" &
LOGCAT_PID=$!
info "Logcat monitoring started (background PID: $LOGCAT_PID)"
info "Logfile: $LOG_MONITOR_FILE"

# Monitor for 10 seconds
info "Monitoring for 10 seconds..."
sleep 10

# Stop logcat monitoring
kill $LOGCAT_PID 2>/dev/null || true

# Check for crashes in the captured logs
if grep -q "FATAL EXCEPTION" "$MONITOR_TMP"; then
    error "Crash detected during monitoring!"
    echo -e "\n${RED}--- Crash stack trace (first 30 lines) ---${NC}"
    grep -A 30 "FATAL EXCEPTION" "$MONITOR_TMP" | head -40
    CRASH_DETECTED=1
else
    success "No crashes detected within 10 seconds after launch"
fi

# Clean up temp file
rm -f "$MONITOR_TMP"

# 9. FINAL VERDICT
section "SMOKE TEST COMPLETE"
echo
if [ $CRASH_DETECTED -eq 0 ]; then
    echo -e "${GREEN}✅✅✅ SMOKE TEST PASSED ✅✅✅${NC}"
    echo -e "${GREEN}No crashes detected. App is stable for defense.${NC}"
    echo
    echo -e "${YELLOW}📋 Log saved to: $LOG_MONITOR_FILE${NC}"
    exit 0
else
    echo -e "${RED}❌❌❌ SMOKE TEST FAILED ❌❌❌${NC}"
    echo -e "${RED}Crashes detected. Fix before defense.${NC}"
    echo
    echo -e "${YELLOW}📋 Log saved to: $LOG_MONITOR_FILE${NC}"
    exit 1
fi
