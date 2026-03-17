#!/bin/bash
# Convenience launcher for Petrie Image Importer
# Run from project root: ./photo-import.sh [command]
#
# Commands:
#   (none)    Launch the GUI application
#   --cli     Run in CLI mode (pass additional args after --cli)
#   build     Build the project
#   test      Run tests
#   format    Auto-format all source code
#   package   Build native installer for current OS
#   clean     Clean build artifacts
#   install   Add this script to your PATH

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

check_java() {
    if ! command -v java &> /dev/null; then
        echo "Java is not installed."
        echo "Please install JDK 21+ from: https://adoptium.net/"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
        echo "Java 21 or higher is required. Found Java $JAVA_VERSION"
        echo "Please upgrade from: https://adoptium.net/"
        exit 1
    fi
}

case "${1:-}" in
    --cli)
        check_java
        shift
        ./gradlew run --args="--cli $*" --no-daemon --quiet
        ;;
    build)
        check_java
        echo "Building..."
        ./gradlew build --no-daemon
        ;;
    test)
        check_java
        echo "Running tests..."
        ./gradlew test --no-daemon
        ;;
    format)
        check_java
        echo "Formatting source code..."
        ./gradlew ktfmtFormatMain ktfmtFormatTest ktfmtFormatScripts --no-daemon
        ;;
    package)
        check_java
        echo "Building native installer..."
        ./build-installers.sh
        ;;
    clean)
        echo "Cleaning build artifacts..."
        ./gradlew clean --no-daemon
        ;;
    install)
        PROFILE_FILE="$HOME/.bashrc"
        if [ -n "$ZSH_VERSION" ] || [ -f "$HOME/.zshrc" ]; then
            PROFILE_FILE="$HOME/.zshrc"
        fi

        ALIAS_LINE="alias photo-import='$SCRIPT_DIR/photo-import.sh'"
        if grep -q "photo-import" "$PROFILE_FILE" 2>/dev/null; then
            echo "photo-import alias already exists in $PROFILE_FILE"
        else
            echo "$ALIAS_LINE" >> "$PROFILE_FILE"
            echo "Added alias to $PROFILE_FILE"
            echo "Restart your shell or run: source $PROFILE_FILE"
        fi
        ;;
    help|--help|-h)
        echo "Petrie Image Importer"
        echo ""
        echo "Usage: ./photo-import.sh [command]"
        echo ""
        echo "Commands:"
        echo "  (none)          Launch the GUI application"
        echo "  --cli [args]    Run in CLI mode"
        echo "  build           Build the project"
        echo "  test            Run tests"
        echo "  format          Auto-format all source code"
        echo "  package         Build native installer for current OS"
        echo "  clean           Clean build artifacts"
        echo "  install         Add photo-import alias to your shell profile"
        echo "  help            Show this help message"
        echo ""
        echo "CLI Examples:"
        echo "  ./photo-import.sh --cli import /source /dest"
        echo "  ./photo-import.sh --cli import /source /dest --dry-run"
        echo "  ./photo-import.sh --cli check-duplicates /photos"
        ;;
    *)
        check_java
        echo "Launching Petrie Image Importer..."
        ./gradlew run --no-daemon --quiet
        ;;
esac
