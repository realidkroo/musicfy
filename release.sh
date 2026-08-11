#!/bin/bash
set -e

# release.sh — Script to publish Musicfy releases to GitHub.
# Format adheres to GithubUpdates.kt specification.

BUILD_ATTEMPT_FILE=".build_number"
if [ ! -f "$BUILD_ATTEMPT_FILE" ]; then
    echo "841" > "$BUILD_ATTEMPT_FILE"
fi

BUILD_NUMBER=$(cat "$BUILD_ATTEMPT_FILE")
VERSION_NAME=$(grep 'versionName =' app/build.gradle.kts | head -n 1 | sed 's/.*versionName = "\(.*\)".*/\1/' | sed 's/ build#.*//')

TAG="dev"
IS_PRERELEASE=true
CHANGELOG_NOTES=""
CHANGELOG_FILE=""
SKIP_BUILD=false

while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h|--help)
            echo "Usage: ./release.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -m, --message <text>   Set changelog body message for the release"
            echo "  -f, --file <path>      Read changelog body from a Markdown file"
            echo "  -t, --tag <tag>        Set release tag (default: dev)"
            echo "  --stable               Mark as stable release (default is dev pre-release)"
            echo "  --no-compile, --no-build  Skip compilation and publish existing APKs"
            echo ""
            exit 0
            ;;
        -m|--message)
            CHANGELOG_NOTES="$2"
            shift
            ;;
        -f|--file)
            CHANGELOG_FILE="$2"
            shift
            ;;
        -t|--tag)
            TAG="$2"
            shift
            ;;
        --stable)
            IS_PRERELEASE=false
            TAG="v$VERSION_NAME"
            ;;
        --no-compile|--no-build)
            SKIP_BUILD=true
            ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

# Build changelog notes according to GithubUpdates.kt spec:
# "first non-blank line becomes the one-line summary on the card"
if [ -n "$CHANGELOG_FILE" ] && [ -f "$CHANGELOG_FILE" ]; then
    CHANGELOG_NOTES=$(cat "$CHANGELOG_FILE")
elif [ -z "$CHANGELOG_NOTES" ]; then
    # Auto-generate changelog from recent git commit history
    HEADLINE="Musicfy $VERSION_NAME build #$BUILD_NUMBER update"
    
    # Get last 7 commits, filtering out automated build commits
    COMMITS=$(git log -n 7 --pretty=format:"- %s" | grep -v "bump build attempt" | grep -v "Merge" || true)
    
    if [ -z "$COMMITS" ]; then
        COMMITS="- General performance improvements and bug fixes."
    fi

    CHANGELOG_NOTES="$HEADLINE

### 🚀 What's Changed
$COMMITS

---
*Built with love for Musicfy dev channel.*"
fi

echo "=========================================="
echo "Publishing Release for Musicfy"
echo "Version:     $VERSION_NAME"
echo "Build #:     $BUILD_NUMBER"
echo "Tag:         $TAG"
echo "Pre-release: $IS_PRERELEASE"
echo "Skip build:  $SKIP_BUILD"
echo "=========================================="
echo "Changelog Body Preview:"
echo "------------------------------------------"
echo "$CHANGELOG_NOTES"
echo "------------------------------------------"

# 1. Build all FOSS release APKs if not skipped
if [ "$SKIP_BUILD" = false ]; then
    echo "Building all release APK architectures..."
    ./build.sh --release -a
else
    echo "Skipping compilation (--no-build requested)..."
fi

# 2. Format and rename APK assets in apk-generated directory according to GithubUpdates.kt
OUTPUT_DIR="apk-generated"
mkdir -p "$OUTPUT_DIR"

echo "Formatting APK asset names for release..."
format_apk() {
    local src_pattern="$1"
    local dest_name="$2"

    local src_file=$(find app/build/outputs/apk "$OUTPUT_DIR" -name "$src_pattern" 2>/dev/null | head -n 1)
    if [ -n "$src_file" ] && [ -f "$src_file" ]; then
        cp "$src_file" "$OUTPUT_DIR/$dest_name"
        echo "Prepared $dest_name"
    fi
}

format_apk "*universal-foss-release.apk" "musicfy-$VERSION_NAME-universal.apk"
format_apk "*arm64-foss-release.apk" "musicfy-$VERSION_NAME-arm64-v8a.apk"
format_apk "*armeabi-foss-release.apk" "musicfy-$VERSION_NAME-armeabi-v7a.apk"
format_apk "*x86_64-foss-release.apk" "musicfy-$VERSION_NAME-x86_64.apk"
format_apk "*x86-foss-release.apk" "musicfy-$VERSION_NAME-x86.apk"

# 3. Update Git Tag
echo "Updating git tag '$TAG' on main..."
git tag -f "$TAG" main
git push -f origin "$TAG"

# 4. Create / Update GitHub Release via gh CLI
TITLE="musicfy $VERSION_NAME ($BUILD_NUMBER)"
echo "Publishing GitHub Release: '$TITLE' on tag '$TAG'..."

PRERELEASE_FLAG=""
if [ "$IS_PRERELEASE" = true ]; then
    PRERELEASE_FLAG="--prerelease"
fi

gh release create "$TAG" \
    "$OUTPUT_DIR"/musicfy-"$VERSION_NAME"-*.apk \
    --title "$TITLE" \
    --notes "$CHANGELOG_NOTES" \
    $PRERELEASE_FLAG \
    --clobber

echo "=========================================="
echo "Successfully published release on GitHub!"
echo "Tag: https://github.com/realidkroo/musicfy/releases/tag/$TAG"
echo "=========================================="
