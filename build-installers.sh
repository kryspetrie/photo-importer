#!/bin/bash
# Build native installers for Petrie Image Importer
# Run from project root: ./build-installers.sh
#
# This uses the Compose Desktop Gradle plugin which bundles a JRE,
# so end users do NOT need Java installed.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

VERSION="1.0.0"
echo "Building Petrie Image Importer v${VERSION} native installer..."
echo ""

OS="$(uname -s)"
case "$OS" in
    Darwin*)
        echo "Platform: macOS — building DMG..."
        ./gradlew packageDmg --no-daemon
        echo ""
        echo "Done. Installer:"
        find build/compose/binaries -name "*.dmg" -print 2>/dev/null
        ;;
    Linux*)
        echo "Platform: Linux — building DEB..."
        ./gradlew packageDeb --no-daemon
        echo ""
        echo "Done. Installer:"
        find build/compose/binaries -name "*.deb" -print 2>/dev/null
        ;;
    CYGWIN*|MINGW*|MSYS*)
        echo "Platform: Windows — building MSI..."
        ./gradlew packageMsi --no-daemon
        echo ""
        echo "Done. Installer:"
        find build/compose/binaries -name "*.msi" -print 2>/dev/null
        ;;
    *)
        echo "Unknown OS: $OS"
        echo "You can try running the Gradle task directly:"
        echo "  ./gradlew packageDistributionForCurrentOS"
        exit 1
        ;;
esac

echo ""
echo "You can also build an uber JAR (no bundled JRE) with:"
echo "  ./gradlew packageUberJarForCurrentOS"
