#!/bin/bash
set -e

APP_NAME="CarpeCast"
SRC_DIR="CarpeCast-Swift"
BUILD_DIR="build"
APP_BUNDLE="$BUILD_DIR/$APP_NAME.app"
MACOS_DIR="$APP_BUNDLE/Contents/MacOS"
RESOURCES_DIR="$APP_BUNDLE/Contents/Resources"

echo "Building $APP_NAME..."

# Create directory structure
mkdir -p "$MACOS_DIR"
mkdir -p "$RESOURCES_DIR"

# Compile Swift files for x86_64 and arm64, then combine with lipo
swiftc -O \
    -target x86_64-apple-macosx11.0 \
    "$SRC_DIR"/*.swift \
    -o "$MACOS_DIR/${APP_NAME}_x86_64"

swiftc -O \
    -target arm64-apple-macosx11.0 \
    "$SRC_DIR"/*.swift \
    -o "$MACOS_DIR/${APP_NAME}_arm64"

lipo -create -output "$MACOS_DIR/$APP_NAME" "$MACOS_DIR/${APP_NAME}_x86_64" "$MACOS_DIR/${APP_NAME}_arm64"
rm "$MACOS_DIR/${APP_NAME}_x86_64" "$MACOS_DIR/${APP_NAME}_arm64"

# Copy Info.plist and Entitlements
cp "$SRC_DIR/Info.plist" "$APP_BUNDLE/Contents/Info.plist"

# Code sign the app with entitlements to allow MediaRemote XPC access
echo "Codesigning app with entitlements..."
codesign --force --deep --sign - --entitlements "$SRC_DIR/CarpeCast.entitlements" "$APP_BUNDLE"

echo "Build complete! App bundle created at $APP_BUNDLE"
