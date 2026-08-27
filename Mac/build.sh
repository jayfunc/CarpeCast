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

# Compile SwiftUI App
swiftc -O \
    -target x86_64-apple-macosx11.0 \
    "$SRC_DIR"/ContentView.swift "$SRC_DIR"/MediaManager.swift "$SRC_DIR"/NetworkManager.swift "$SRC_DIR"/PlayerView.swift \
    -o "$MACOS_DIR/${APP_NAME}_x86_64"

swiftc -O \
    -target arm64-apple-macosx11.0 \
    "$SRC_DIR"/ContentView.swift "$SRC_DIR"/MediaManager.swift "$SRC_DIR"/NetworkManager.swift "$SRC_DIR"/PlayerView.swift \
    -o "$MACOS_DIR/${APP_NAME}_arm64"

lipo -create -output "$MACOS_DIR/$APP_NAME" "$MACOS_DIR/${APP_NAME}_x86_64" "$MACOS_DIR/${APP_NAME}_arm64"
rm "$MACOS_DIR/${APP_NAME}_x86_64" "$MACOS_DIR/${APP_NAME}_arm64"

# Compile CLI Helper as dynamic library (.dylib)
swiftc -O -emit-library -target x86_64-apple-macosx11.0 "$SRC_DIR"/NowPlayingHelper.swift -o "$MACOS_DIR/mac_nowplaying_x86_64.dylib"
swiftc -O -emit-library -target arm64-apple-macosx11.0 "$SRC_DIR"/NowPlayingHelper.swift -o "$MACOS_DIR/mac_nowplaying_arm64.dylib"
lipo -create -output "$MACOS_DIR/libmac_nowplaying.dylib" "$MACOS_DIR/mac_nowplaying_x86_64.dylib" "$MACOS_DIR/mac_nowplaying_arm64.dylib"
rm "$MACOS_DIR/mac_nowplaying_x86_64.dylib" "$MACOS_DIR/mac_nowplaying_arm64.dylib"

# Copy Perl wrapper
cp "$SRC_DIR/nowplaying_wrapper.pl" "$MACOS_DIR/nowplaying_wrapper.pl"
chmod +x "$MACOS_DIR/nowplaying_wrapper.pl"

# Copy Info.plist
cp "$SRC_DIR/Info.plist" "$APP_BUNDLE/Contents/Info.plist"

# Create PkgInfo
echo -n "APPL????" > "$APP_BUNDLE/Contents/PkgInfo"

# Code sign the app with ad-hoc signature and MediaRemote entitlements
echo "Codesigning app with entitlements..."
codesign --force --deep --sign - --entitlements "$SRC_DIR/CarpeCast.entitlements" "$APP_BUNDLE"

echo "Build complete! App bundle created at $APP_BUNDLE"
