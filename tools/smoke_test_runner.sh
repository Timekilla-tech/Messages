#!/bin/bash

###############################################################################
# SMOKE TEST RUNNER — Fossify Messages (Sprint 3)
#
# Purpose: Automate pre-defense smoke test verification
# Usage: ./tools/smoke_test_runner.sh [device-id]
# Example: ./tools/smoke_test_runner.sh emulator-5554
#
# This script:
# 1. Builds the APK
# 2. Installs on device/emulator
# 3. Grants permissions
# 4. Runs logcat monitoring in background
# 5. Reports build status
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
LOG_FILE="smoke_test_$(date +%Y%m%d_%H%M%S).log"

echo -e "${BLUE}=== Fossify Messages Smoke Test Runner ===${NC}"
echo -e "${BLUE}Date: $(date)${NC}"
echo -e "${BLUE}Device: ${DEVICE_ID}${NC}"
echo -e "${BLUE}Log file: ${LOG_FILE}${NC}"
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
if ./gradlew assembleDebug -x detekt >> "$LOG_FILE" 2>&1; then
    success "APK build successful"
    # Find the newest debug APK across flavor-specific output directories
    APK_FILE=$(find app/build/outputs/apk -type f \
        -path '*/debug/*.apk' \
        ! -name '*androidTest.apk' \
        ! -name '*unaligned.apk' \
        | sort -r | head -1)
    if [ -z "$APK_FILE" ]; then
        error "APK file not found! Expected something like app/build/outputs/apk/<flavor>/debug/*.apk"
        info "Available APK outputs:"
        find app/build/outputs/apk -type f \( -name '*.apk' -o -name '*.apks' \) | sort
        exit 1
    fi
    info "APK: $APK_FILE"
else
    error "APK build failed. Check $LOG_FILE"
    tail -20 "$LOG_FILE"
    exit 1
fi

# 3. UNINSTALL PREVIOUS VERSION
section "Step 3: Clearing previous installation"
adb -s "$DEVICE_ID" uninstall "$PACKAGE_NAME" > /dev/null 2>&1 || true
success "Previous version uninstalled (or didn't exist)"

# 4. INSTALL APK
section "Step 4: Installing APK on device"
if adb -s "$DEVICE_ID" install -g "$APK_FILE" >> "$LOG_FILE" 2>&1; then
    success "APK installed successfully"
else
    error "APK installation failed. Check $LOG_FILE"
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

# 6. START LOGCAT MONITORING
section "Step 6: Starting logcat monitoring in background"
LOG_MONITOR_FILE="logcat_$(date +%Y%m%d_%H%M%S).txt"
adb -s "$DEVICE_ID" logcat | tee "$LOG_MONITOR_FILE" &
LOGCAT_PID=$!
info "Logcat monitoring started (PID: $LOGCAT_PID)"
info "Logcat output: $LOG_MONITOR_FILE"

# Wait a moment for logcat to start
sleep 2

# 7. LAUNCH APP
section "Step 7: Launching application"
adb -s "$DEVICE_ID" shell am start -n "$PACKAGE_NAME/.activities.MainActivity" >> "$LOG_FILE" 2>&1
success "App launched"
info "Waiting 5 seconds for app to stabilize..."
sleep 5

# 8. CHECK FOR CRASHES
section "Step 8: Checking for crashes in logcat"
if grep -i "crash\|fatal\|ANR" "$LOG_MONITOR_FILE" > /dev/null 2>&1; then
    error "Crashes detected in logcat!"
    echo "Recent log entries:"
    tail -30 "$LOG_MONITOR_FILE"
else
    success "No crashes detected in initial 5 seconds"
fi

# 9. PRINT SUMMARY
section "SMOKE TEST SETUP COMPLETE"
echo
echo -e "${GREEN}✅ Prerequisites verified:${NC}"
echo "   • Device: $DEVICE_ID"
echo "   • Package: $PACKAGE_NAME"
echo "   • APK: $APK_FILE"
echo "   • Build log: $LOG_FILE"
echo "   • Logcat: $LOG_MONITOR_FILE (PID: $LOGCAT_PID)"
echo
echo -e "${YELLOW}📋 Next steps:${NC}"
echo "   1. Open the app on your device"
echo "   2. Run through tests 1-11 in SMOKE_TEST_CHECKLIST.md"
echo "   3. Monitor logcat output: tail -f $LOG_MONITOR_FILE"
echo "   4. Record demo video when ready"
echo
echo -e "${YELLOW}⚠️  Remember to:${NC}"
echo "   • Check for red errors in logcat"
echo "   • Test Category creation (TEST 1)"
echo "   • Verify Age headers (TEST 2)"
echo "   • Test Swipe actions (TEST 3)"
echo "   • Test on multiple devices if possible (phone + tablet/foldable)"
echo
echo -e "${BLUE}Logcat streaming in background. Press Ctrl+C to stop when done.${NC}"
echo

# Keep logcat running until user stops
wait $LOGCAT_PID

