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
printf 'enum BuildInfo { static let commitHash = "%s" }\n' "$(git rev-parse --short HEAD)" > "$BUILD_DIR/BuildInfo.swift"

# Compile SwiftUI App
swiftc -O \
    -target x86_64-apple-macosx11.0 \
    "$SRC_DIR"/ContentView.swift "$SRC_DIR"/MediaManager.swift "$SRC_DIR"/NetworkManager.swift "$SRC_DIR"/UDPSocket.swift "$SRC_DIR"/PlayerView.swift "$BUILD_DIR"/BuildInfo.swift \
    -o "$MACOS_DIR/${APP_NAME}_x86_64"

swiftc -O \
    -target arm64-apple-macosx11.0 \
    "$SRC_DIR"/ContentView.swift "$SRC_DIR"/MediaManager.swift "$SRC_DIR"/NetworkManager.swift "$SRC_DIR"/UDPSocket.swift "$SRC_DIR"/PlayerView.swift "$BUILD_DIR"/BuildInfo.swift \
    -o "$MACOS_DIR/${APP_NAME}_arm64"

lipo -create -output "$MACOS_DIR/$APP_NAME" "$MACOS_DIR/${APP_NAME}_x86_64" "$MACOS_DIR/${APP_NAME}_arm64"
rm "$MACOS_DIR/${APP_NAME}_x86_64" "$MACOS_DIR/${APP_NAME}_arm64"

# Build MediaRemoteAdapter (stable C/Objective-C implementation via Perl)
echo "Building MediaRemoteAdapter..."
git clone https://github.com/ungive/mediaremote-adapter.git "$BUILD_DIR/mediaremote-adapter"
cd "$BUILD_DIR/mediaremote-adapter"
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
cd -

# Copy MediaRemoteAdapter into resources
mkdir -p "$APP_BUNDLE/Contents/Frameworks"
cp -R "$BUILD_DIR/mediaremote-adapter/build/MediaRemoteAdapter.framework" "$APP_BUNDLE/Contents/Frameworks/"
cp "$BUILD_DIR/mediaremote-adapter/bin/mediaremote-adapter.pl" "$MACOS_DIR/mediaremote-adapter.pl"
chmod +x "$MACOS_DIR/mediaremote-adapter.pl"

# Copy Info.plist and resources
cp "$SRC_DIR/Info.plist" "$APP_BUNDLE/Contents/Info.plist"
cp "$SRC_DIR/MenuBarIcon.png" "$RESOURCES_DIR/" || true

# Create PkgInfo
echo -n "APPL????" > "$APP_BUNDLE/Contents/PkgInfo"

# Code sign the app with ad-hoc signature and MediaRemote entitlements
echo "Codesigning app with entitlements..."
codesign --force --deep --sign - --entitlements "$SRC_DIR/CarpeCast.entitlements" "$APP_BUNDLE"

echo "Build complete! App bundle created at $APP_BUNDLE"
