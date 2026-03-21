#!/usr/bin/env bash
set -euo pipefail

VERSION="1.0.0"
OUTPUT_DIR="dist"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

cd "$SCRIPT_DIR"

mkdir -p "$OUTPUT_DIR"

bold() { printf '\033[1m%s\033[0m\n' "$1"; }
ok()   { printf '\033[1;32m✓ %s\033[0m\n' "$1"; }
warn() { printf '\033[1;33m⚠ %s\033[0m\n' "$1"; }
fail() { printf '\033[1;31m✗ %s\033[0m\n' "$1"; }

# ---------------------------------------------------------------------------
# macOS (.dmg) — native build, only works on macOS
# ---------------------------------------------------------------------------
build_macos() {
  bold "Building macOS .dmg..."
  if [[ "$(uname)" != "Darwin" ]]; then
    warn "Skipping macOS build (not running on macOS)"
    return
  fi
  ./gradlew packageDmg --no-daemon
  cp build/compose/binaries/main/dmg/*.dmg "$OUTPUT_DIR/" 2>/dev/null && \
    ok "macOS .dmg → $OUTPUT_DIR/" || fail "macOS .dmg not found"
}

# ---------------------------------------------------------------------------
# Linux (.deb) — Docker build
# ---------------------------------------------------------------------------
build_linux() {
  bold "Building Linux .deb via Docker..."
  if ! command -v docker &>/dev/null; then
    fail "Docker not found — skipping Linux build"
    return
  fi
  if ! docker info &>/dev/null; then
    fail "Docker daemon not running — skipping Linux build"
    return
  fi

  docker build --platform linux/amd64 -f Dockerfile.linux-build -t petrie-linux-build .
  docker run --rm --platform linux/amd64 -v "$SCRIPT_DIR/$OUTPUT_DIR:/output" petrie-linux-build
  ok "Linux .deb → $OUTPUT_DIR/"
}

# ---------------------------------------------------------------------------
# Windows (.msi) — needs a Windows host or CI
# ---------------------------------------------------------------------------
build_windows() {
  bold "Windows .msi..."
  if [[ "$(uname -o 2>/dev/null || uname)" == "Msys" || "$(uname -o 2>/dev/null || uname)" == "Cygwin" ]]; then
    ./gradlew packageMsi --no-daemon
    cp build/compose/binaries/main/msi/*.msi "$OUTPUT_DIR/" 2>/dev/null && \
      ok "Windows .msi → $OUTPUT_DIR/" || fail "Windows .msi not found"
  else
    warn "Skipping Windows build (requires a Windows host or CI)"
    echo "  Use GitHub Actions with 'runs-on: windows-latest' to build .msi"
  fi
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
TARGETS="${1:-all}"

case "$TARGETS" in
  macos)   build_macos ;;
  linux)   build_linux ;;
  windows) build_windows ;;
  all)
    build_macos
    build_linux
    build_windows
    echo ""
    bold "Done. Artifacts in $OUTPUT_DIR/:"
    ls -lh "$OUTPUT_DIR/" 2>/dev/null || echo "  (empty)"
    ;;
  *)
    echo "Usage: $0 [macos|linux|windows|all]"
    exit 1
    ;;
esac
