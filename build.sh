#!/bin/bash

# Default values
BUILD_ATTEMPT_FILE=".build_number"
if [ ! -f "$BUILD_ATTEMPT_FILE" ]; then
    echo "841" > "$BUILD_ATTEMPT_FILE"
fi

VARIANT="debug"
INSTALL_ON_DEVICES=false
PACKAGE_STYLE="universal"
CHANGE_VERSION=""
SKIP_BUILD=false

# Parse arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h|--help)
            echo "Usage: ./build.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -h, --help        Show this help message and exit"
            echo "  --debug           Build the debug version of the app (default)"
            echo "  --release         Build the release version of the app"
            echo "  -i                Install the app on all connected devices via ADB and auto-launch"
            echo "  -I, --install-only Install existing compiled APK without compiling/building"
            echo "                    (Works with --debug or --release)"
            echo "  -a                Build all package styles (arm64, armeabi, x86, x86_64, universal)."
            echo "                    If omitted, defaults to building only the universal APK."
            echo "  -c <version>      Change the base app version (e.g., -c 6.0.2)"
            echo "  -k, --kill        Kill all active Gradle daemons and build processes and exit"
            echo ""
            echo "Examples:"
            echo "  ./build.sh --debug -i              # Build debug universal APK and install"
            echo "  ./build.sh --release -a            # Build release for all architectures"
            echo "  ./build.sh --install-only --debug   # Install existing debug APK without compile"
            echo "  ./build.sh --install-only --release # Install existing release APK without compile"
            echo "  ./build.sh -k                      # Kill all active build processes"
            exit 0
            ;;
        --debug) VARIANT="debug" ;;
        --release) VARIANT="release" ;;
        -i) INSTALL_ON_DEVICES=true ;;
        -I|--install-only|--no-compile|--no-build)
            SKIP_BUILD=true
            INSTALL_ON_DEVICES=true
            ;;
        -a) PACKAGE_STYLE="all" ;;
        -c) 
            CHANGE_VERSION="$2"
            shift # Past argument
            ;;
        -k|--kill)
            echo "Stopping all Gradle daemons and killing active build processes..."
            ./gradlew --stop 2>/dev/null || true
            pkill -9 -f "GradleDaemon" 2>/dev/null || true
            pkill -9 -f "gradlew" 2>/dev/null || true
            echo "All build processes successfully killed."
            exit 0
            ;;
        *) echo "Unknown parameter passed: $1"; exit 1 ;;
    esac
    shift
done

if [ "$SKIP_BUILD" = false ]; then
    # Increment build attempt for this run
    BUILD_ATTEMPT=$(cat "$BUILD_ATTEMPT_FILE")
    BUILD_ATTEMPT=$((BUILD_ATTEMPT + 1))
    echo "$BUILD_ATTEMPT" > "$BUILD_ATTEMPT_FILE"

    echo "Starting build #$BUILD_ATTEMPT for variant: $VARIANT"

    # If -c is passed, update the base version in build.gradle.kts first
    if [ -n "$CHANGE_VERSION" ]; then
        echo "Changing base app version to $CHANGE_VERSION..."
        sed -i '' "s/versionName = \".*\"/versionName = \"$CHANGE_VERSION\"/g" app/build.gradle.kts
    fi

    # Extract current base version from build.gradle.kts
    BASE_VERSION=$(grep 'versionName =' app/build.gradle.kts | sed 's/.*versionName = "\(.*\)".*/\1/' | sed 's/ build#.*//')

    # Update build.gradle.kts versionName to include the new build number for the settings page
    sed -i '' "s/versionName = \".*\"/versionName = \"$BASE_VERSION build#$BUILD_ATTEMPT\"/g" app/build.gradle.kts

    # Update BetaNoticeScreen.kt with the new version formats
    BETA_NOTICE_FILE="app/src/main/kotlin/com/example/musicfy/ui/screens/beta/BetaNoticeScreen.kt"

    if [ -f "$BETA_NOTICE_FILE" ]; then
        sed -i '' -e "s/text = \"Musicfy DEV PREV\"/text = \"musicfy build #$BUILD_ATTEMPT $VARIANT\"/g" "$BETA_NOTICE_FILE"
        sed -i '' -e "s/text = \"musicfy build #[0-9]* .*\"/text = \"musicfy build #$BUILD_ATTEMPT $VARIANT\"/g" "$BETA_NOTICE_FILE"
        sed -i '' -e "s/text = \"[0-9]*\.[0-9]*\.[0-9]*DEV\"/text = \"$BASE_VERSION build#$BUILD_ATTEMPT\"/g" "$BETA_NOTICE_FILE"
        sed -i '' -e "s/text = \"[0-9]*\.[0-9]*\.[0-9]* build#[0-9]*\"/text = \"$BASE_VERSION build#$BUILD_ATTEMPT\"/g" "$BETA_NOTICE_FILE"
    fi

    # Determine Gradle Task
    CAP_VARIANT="$(tr '[:lower:]' '[:upper:]' <<< ${VARIANT:0:1})${VARIANT:1}"

    if [ "$PACKAGE_STYLE" == "all" ]; then
        echo "Building ALL FOSS package styles for $VARIANT..."
        TASK="assembleFoss${CAP_VARIANT}"
    else
        echo "Building universal FOSS package style for $VARIANT..."
        TASK="assembleUniversalFoss${CAP_VARIANT}"
    fi

    # Run Gradle Build
    ./gradlew "$TASK"

    # Copy generated FOSS APKs to apk-generated directory
    OUTPUT_DIR="apk-generated"
    mkdir -p "$OUTPUT_DIR"
    find app/build/outputs/apk -name "*foss*-$VARIANT.apk" -exec cp {} "$OUTPUT_DIR/" \;
    echo "Copied FOSS APKs to $OUTPUT_DIR/"
else
    echo "Skipping compilation (--install-only requested for $VARIANT variant)..."
fi

# Install and Auto-open
if [ "$INSTALL_ON_DEVICES" = true ]; then
    # Search in apk-generated first, then app/build/outputs/apk
    APK_PATH=$(find apk-generated app/build/outputs/apk -name "*-universal-foss-$VARIANT.apk" 2>/dev/null | head -n 1)
    
    if [ -z "$APK_PATH" ]; then
        APK_PATH=$(find apk-generated app/build/outputs/apk -name "*foss*-$VARIANT.apk" 2>/dev/null | head -n 1)
    fi

    if [ -z "$APK_PATH" ]; then
        APK_PATH=$(find apk-generated app/build/outputs/apk -name "*$VARIANT*.apk" 2>/dev/null | grep -v "unaligned" | head -n 1)
    fi
    
    if [ -z "$APK_PATH" ]; then
        echo "Error: No existing $VARIANT APK found to install!"
    else
        # Robust tab-separated device serial extraction
        DEVICES=$(adb devices | grep -v "^List" | grep -w "device" | cut -f1)
        
        # Determine target package ID based on variant (.debug for debug builds)
        PACKAGE_NAME="com.example.musicfy"
        if [ "$VARIANT" == "debug" ]; then
            PACKAGE_NAME="com.example.musicfy.debug"
        elif [ "$VARIANT" == "profileable" ]; then
            PACKAGE_NAME="com.example.musicfy.profileable"
        fi

        if [ -z "$DEVICES" ]; then
            echo "No devices connected to install."
        else
            echo "$DEVICES" | while IFS= read -r DEVICE; do
                [ -z "$DEVICE" ] && continue
                echo "Installing $APK_PATH on device '$DEVICE'..."
                adb -s "$DEVICE" install -r "$APK_PATH"
                echo "Launching $PACKAGE_NAME ($VARIANT) on device '$DEVICE'..."
                adb -s "$DEVICE" shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1
            done
        fi
    fi
fi
