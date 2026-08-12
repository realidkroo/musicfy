#!/bin/bash
set -e

# default values
BUILD_ATTEMPT_FILE=".build_number"
if [ ! -f "$BUILD_ATTEMPT_FILE" ]; then
    echo "841" > "$BUILD_ATTEMPT_FILE"
fi

FLAVOR="foss"
VARIANT="debug"
INSTALL_ON_DEVICES=false
PACKAGE_STYLE="universal"
CHANGE_VERSION=""
SKIP_BUILD=false
PUBLISH_RELEASE=false
CHANGELOG_TEXT=""
CUSTOM_BUILD_INFO=""
IS_CUSTOM_BUILD=false

BETA_NOTICE_FILE="app/src/main/kotlin/com/example/musicfy/ui/screens/beta/BetaNoticeScreen.kt"

cleanup() {
    if [ "$IS_CUSTOM_BUILD" = true ]; then
        echo ""
        echo "Restoring original build files after custom build..."
        [ -f app/build.gradle.kts.bak ] && mv app/build.gradle.kts.bak app/build.gradle.kts
        [ -f "$BETA_NOTICE_FILE.bak" ] && mv "$BETA_NOTICE_FILE.bak" "$BETA_NOTICE_FILE"
        [ -f "$BUILD_ATTEMPT_FILE.bak" ] && mv "$BUILD_ATTEMPT_FILE.bak" "$BUILD_ATTEMPT_FILE"
        echo "Original build files successfully restored."
    fi
}
trap cleanup EXIT INT TERM

# parse args
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h|--help)
            echo "Usage: ./build.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -h, --help               Show this help message and exit"
            echo "  --debug                  Build the debug version of the app (default)"
            echo "  --release                Build the release version of the app"
            echo "  -fd, --fdroid            Build F-Droid compliant flavor (strips self-update permission)"
            echo "  -i                       Install the app on all connected devices via ADB and auto-launch"
            echo "  -I, --install-only       Install/publish existing compiled APK without compiling"
            echo "                           (Works with --debug, --release, or -commit)"
            echo "  -a                       Build all package styles (arm64, armeabi, x86, x86_64, universal)."
            echo "                           If omitted, defaults to building only the universal APK."
            echo "  -c <version>             Change the base app version (e.g., -c 6.0.2)"
            echo "  -g <date,ver,attempt>    Temporarily scope custom build date, version, and attempt number,"
            echo "                           then automatically restore original files when done."
            echo "                           Format: -g \"2026-08-12,6.0.2,999\" or -g \"date,ver,attempt\""
            echo "  -commit <changelog>,     Publish release to GitHub 'dev' tag with the given changelog"
            echo "  --commit <changelog>"
            echo "  -k, --kill               Kill all active Gradle daemons and build processes and exit"
            echo ""
            echo "Examples:"
            echo "  ./build.sh --debug -i                              # Build debug universal APK & install"
            echo "  ./build.sh --debug -i -g \"2026-08-12,6.0.2,999\"  # Temp custom build date, ver & attempt & restore"
            echo "  ./build.sh --release -a                            # Build release for all architectures (self-updating)"
            echo "  ./build.sh -fd --release -a                        # Build F-Droid release for all architectures"
            echo "  ./build.sh --release -a -commit \"lyrics fix\"       # Build all & publish to GitHub dev tag"
            echo "  ./build.sh --install-only --release -commit \"notes\" # Publish existing release APKs"
            echo "  ./build.sh -k                                      # Kill all active build processes"
            exit 0
            ;;
        --debug) VARIANT="debug" ;;
        --release) VARIANT="release" ;;
        -fd|--fdroid) FLAVOR="fdroid" ;;
        -i) INSTALL_ON_DEVICES=true ;;
        -I|--install-only|--no-compile|--no-build)
            SKIP_BUILD=true
            ;;
        -a) PACKAGE_STYLE="all" ;;
        -c)
            CHANGE_VERSION="$2"
            shift
            ;;
        -g|--custom-build)
            CUSTOM_BUILD_INFO="$2"
            shift
            ;;
        -commit|--commit|-m|--publish)
            PUBLISH_RELEASE=true
            CHANGELOG_TEXT="$2"
            shift
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
    # custom build scope
    if [ -n "$CUSTOM_BUILD_INFO" ]; then
        IS_CUSTOM_BUILD=true
        CUSTOM_DATE=$(echo "$CUSTOM_BUILD_INFO" | cut -d',' -f1)
        CUSTOM_VER=$(echo "$CUSTOM_BUILD_INFO" | cut -d',' -f2)
        CUSTOM_ATTEMPT=$(echo "$CUSTOM_BUILD_INFO" | cut -d',' -f3)

        echo "Backup original configuration files for custom build scope..."
        cp app/build.gradle.kts app/build.gradle.kts.bak
        [ -f "$BETA_NOTICE_FILE" ] && cp "$BETA_NOTICE_FILE" "$BETA_NOTICE_FILE.bak"
        cp "$BUILD_ATTEMPT_FILE" "$BUILD_ATTEMPT_FILE.bak"
    fi

    # build attempt count
    if [ -n "$CUSTOM_ATTEMPT" ]; then
        BUILD_ATTEMPT="$CUSTOM_ATTEMPT"
    else
        BUILD_ATTEMPT=$(cat "$BUILD_ATTEMPT_FILE")
        BUILD_ATTEMPT=$((BUILD_ATTEMPT + 1))
        echo "$BUILD_ATTEMPT" > "$BUILD_ATTEMPT_FILE"
    fi

    echo "Starting build #$BUILD_ATTEMPT for flavor: $FLAVOR ($VARIANT)..."

    # version override
    if [ -n "$CUSTOM_VER" ]; then
        CHANGE_VERSION="$CUSTOM_VER"
    fi

    if [ -n "$CHANGE_VERSION" ]; then
        echo "Changing base app version to $CHANGE_VERSION..."
        sed -i '' "s/versionName = \".*\"/versionName = \"$CHANGE_VERSION\"/g" app/build.gradle.kts
    fi

    # base version
    BASE_VERSION=$(grep 'versionName =' app/build.gradle.kts | head -n 1 | sed 's/.*versionName = "\(.*\)".*/\1/' | sed 's/ build#.*

    # version name update
    sed -i '' "s/versionName = \".*\"/versionName = \"$BASE_VERSION build#$BUILD_ATTEMPT\"/g" app/build.gradle.kts

    # beta notice text update
    if [ -f "$BETA_NOTICE_FILE" ]; then
        NOTICE_TITLE="musicfy build #$BUILD_ATTEMPT $VARIANT"
        if [ -n "$CUSTOM_DATE" ]; then
            NOTICE_TITLE="musicfy build #$BUILD_ATTEMPT $VARIANT ($CUSTOM_DATE)"
        fi
        sed -i '' -e "s/text = \"Musicfy DEV PREV\"/text = \"$NOTICE_TITLE\"/g" "$BETA_NOTICE_FILE"
        sed -i '' -e "s/text = \"musicfy build #[0-9]* .*\"/text = \"$NOTICE_TITLE\"/g" "$BETA_NOTICE_FILE"
        sed -i '' -e "s/text = \"[0-9]*\.[0-9]*\.[0-9]*DEV\"/text = \"$BASE_VERSION build#$BUILD_ATTEMPT\"/g" "$BETA_NOTICE_FILE"
        sed -i '' -e "s/text = \"[0-9]*\.[0-9]*\.[0-9]* build#[0-9]*\"/text = \"$BASE_VERSION build#$BUILD_ATTEMPT\"/g" "$BETA_NOTICE_FILE"
    fi

    # gradle task
    CAP_FLAVOR="$(tr '[:lower:]' '[:upper:]' <<< ${FLAVOR:0:1})${FLAVOR:1}"
    CAP_VARIANT="$(tr '[:lower:]' '[:upper:]' <<< ${VARIANT:0:1})${VARIANT:1}"

    if [ "$PACKAGE_STYLE" == "all" ]; then
        echo "Building ALL $FLAVOR package styles for $VARIANT..."
        TASK=":app:assembleArm64${CAP_FLAVOR}${CAP_VARIANT} :app:assembleArmeabi${CAP_FLAVOR}${CAP_VARIANT} :app:assembleX86${CAP_FLAVOR}${CAP_VARIANT} :app:assembleX86_64${CAP_FLAVOR}${CAP_VARIANT} :app:assembleUniversal${CAP_FLAVOR}${CAP_VARIANT}"
    else
        echo "Building universal $FLAVOR package style for $VARIANT..."
        TASK="assembleUniversal${CAP_FLAVOR}${CAP_VARIANT}"
    fi

    # build task run
    ./gradlew $TASK

    # clean old apks
    OUTPUT_DIR="apk-generated"
    mkdir -p "$OUTPUT_DIR"
    rm -f "$OUTPUT_DIR"/*-${VARIANT}.apk 2>/dev/null || true
    find app/build/outputs/apk -name "*${FLAVOR}*-$VARIANT.apk" -exec cp {} "$OUTPUT_DIR/" \;
    echo "Copied $FLAVOR $VARIANT APKs to $OUTPUT_DIR/"
else
    echo "Skipping compilation (--install-only/--no-compile requested for $VARIANT variant)..."
    BUILD_ATTEMPT=$(cat "$BUILD_ATTEMPT_FILE")
    BASE_VERSION=$(grep 'versionName =' app/build.gradle.kts | head -n 1 | sed 's/.*versionName = "\(.*\)".*/\1/' | sed 's/ build#.*
fi

# github release publish
if [ "$PUBLISH_RELEASE" = true ]; then
    echo "Preparing GitHub release on 'dev' tag..."
    OUTPUT_DIR="apk-generated"
    mkdir -p "$OUTPUT_DIR"

    # format apk asset names
    format_apk() {
        local src_pattern="$1"
        local dest_name="$2"
        local src_file=$(find app/build/outputs/apk "$OUTPUT_DIR" -name "$src_pattern" 2>/dev/null | head -n 1)
        if [ -n "$src_file" ] && [ -f "$src_file" ]; then
            cp "$src_file" "$OUTPUT_DIR/$dest_name"
            echo "Prepared $dest_name"
        fi
    }

    format_apk "*universal-${FLAVOR}-release.apk" "musicfy-$BASE_VERSION-universal.apk"
    format_apk "*arm64-${FLAVOR}-release.apk" "musicfy-$BASE_VERSION-arm64-v8a.apk"
    format_apk "*armeabi-${FLAVOR}-release.apk" "musicfy-$BASE_VERSION-armeabi-v7a.apk"
    format_apk "*x86_64-${FLAVOR}-release.apk" "musicfy-$BASE_VERSION-x86_64.apk"
    format_apk "*x86-${FLAVOR}-release.apk" "musicfy-$BASE_VERSION-x86.apk"

    if [ -z "$CHANGELOG_TEXT" ]; then
        CHANGELOG_TEXT="musicfy $BASE_VERSION ($BUILD_ATTEMPT)"
    fi

    # dev tag update
    echo "Updating git tag 'dev' on main..."
    git tag -f dev main 2>/dev/null || true
    git push -f origin dev 2>/dev/null || true

    TITLE="musicfy $BASE_VERSION ($BUILD_ATTEMPT)"
    echo "Publishing GitHub release: '$TITLE'..."

    if gh release view dev >/dev/null 2>&1; then
        gh release edit dev --title "$TITLE" --notes "$CHANGELOG_TEXT" --prerelease
        gh release upload dev "$OUTPUT_DIR"/musicfy-"$BASE_VERSION"-*.apk --clobber
    else
        gh release create dev "$OUTPUT_DIR"/musicfy-"$BASE_VERSION"-*.apk --title "$TITLE" --notes "$CHANGELOG_TEXT" --prerelease
    fi

    echo "Release successfully published to https://github.com/realidkroo/musicfy/releases/tag/dev"
fi

# adb install and launch
if [ "$INSTALL_ON_DEVICES" = true ]; then
    # search output apks
    APK_PATH=$(find app/build/outputs/apk -name "*${PACKAGE_STYLE}*${FLAVOR}*${VARIANT}.apk" 2>/dev/null | head -n 1)
    
    if [ -z "$APK_PATH" ]; then
        APK_PATH=$(find app/build/outputs/apk -name "*${FLAVOR}*${VARIANT}.apk" 2>/dev/null | head -n 1)
    fi

    if [ -z "$APK_PATH" ]; then
        APK_PATH=$(find apk-generated -name "*${FLAVOR}*${VARIANT}.apk" 2>/dev/null | head -n 1)
    fi
    
    if [ -z "$APK_PATH" ]; then
        echo "Error: No existing $FLAVOR $VARIANT APK found to install!"
    else
        DEVICES=$(adb devices | grep -v "^List" | grep -w "device" | cut -f1)
        
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
