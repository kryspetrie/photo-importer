#!/usr/bin/env bash
# Create/use a project venv, install deps, and sync locale files from en.json.
#
# Usage:
#   ./scripts/sync-locales.sh              # install + sync
#   ./scripts/sync-locales.sh --dry-run    # install + preview only
#   ./scripts/sync-locales.sh --check      # install + verify sync (no translation)
#   ./scripts/sync-locales.sh --locale de  # install + sync one locale

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VENV_DIR="${LOCALE_VENV_DIR:-$ROOT/.venv-locales}"
REQUIREMENTS="$ROOT/scripts/requirements-i18n.txt"
SYSTEM_PYTHON="${PYTHON:-python3}"

echo "==> Petrie locale sync"
echo "    Project: $ROOT"
echo "    Venv:    $VENV_DIR"
echo

if ! command -v "$SYSTEM_PYTHON" >/dev/null 2>&1; then
  echo "error: python3 not found. Install Python 3 and try again." >&2
  exit 1
fi

if [[ ! -d "$VENV_DIR" ]]; then
  echo "==> Creating virtual environment..."
  "$SYSTEM_PYTHON" -m venv "$VENV_DIR"
fi

VENV_PYTHON="$VENV_DIR/bin/python"
if [[ ! -x "$VENV_PYTHON" ]]; then
  echo "error: venv python not found at $VENV_PYTHON" >&2
  exit 1
fi

echo "==> Installing translation dependencies into venv..."
"$VENV_PYTHON" -m pip install --upgrade pip -q
"$VENV_PYTHON" -m pip install -r "$REQUIREMENTS" -q
echo "    OK ($( "$VENV_PYTHON" -m pip show deep-translator | awk '/^Version:/{print $2}' ))"
echo

echo "==> Syncing locales from en.json..."
exec "$VENV_PYTHON" "$ROOT/scripts/sync_locales_from_en.py" "$@"
