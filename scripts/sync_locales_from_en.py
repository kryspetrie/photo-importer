#!/usr/bin/env python3
"""Sync all locale files with new entries from en.json using web translation.

Run this after adding or changing StringKey entries in src/main/resources/i18n/en.json.
Only keys missing from each locale file are translated; existing translations are kept.

Requirements:
  pip install deep-translator

Examples:
  python3 scripts/sync_locales_from_en.py
  python3 scripts/sync_locales_from_en.py --dry-run
  python3 scripts/sync_locales_from_en.py --locale de --locale ja
  python3 scripts/sync_locales_from_en.py --check
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

# Allow importing sibling module when run as a script.
sys.path.insert(0, str(Path(__file__).resolve().parent))

from locale_translate import (  # noqa: E402
    LOCALES,
    LocaleTranslateError,
    check_all_locales_synced,
    load_en,
    sync_all_locales,
)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Translate new en.json strings into all bundled locale files.",
    )
    parser.add_argument(
        "--locale",
        action="append",
        help=f"Sync only these locale codes (default: all {len(LOCALES)} bundled locales)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Report missing keys without calling the translation service",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Exit with code 1 if any locale file is missing en.json keys (no network)",
    )
    args = parser.parse_args()

    try:
        en = load_en()
    except LocaleTranslateError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    targets = args.locale or list(LOCALES.keys())
    unknown = [code for code in targets if code not in LOCALES]
    if unknown:
        print(f"error: unknown locale code(s): {', '.join(unknown)}", file=sys.stderr)
        print(f"Known codes: {', '.join(sorted(LOCALES))}", file=sys.stderr)
        return 1

    if args.check:
        errors = check_all_locales_synced(targets)
        if errors:
            print("Locale files are out of sync with en.json:", file=sys.stderr)
            for message in errors:
                print(f"  - {message}", file=sys.stderr)
            print("\nRun: python3 scripts/sync_locales_from_en.py", file=sys.stderr)
            return 1
        print(f"All {len(targets)} locale files contain every en.json key ({len(en)} keys).")
        return 0

    mode = "dry run" if args.dry_run else "sync"
    print(f"Starting locale {mode} from en.json ({len(en)} keys)")
    print(f"Targets: {', '.join(targets)}")

    try:
        added = sync_all_locales(targets, dry_run=args.dry_run)
    except LocaleTranslateError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    total = sum(added.values())
    updated = sum(1 for count in added.values() if count > 0)
    if args.dry_run:
        print(f"\nDry run complete: {total} key(s) would be translated across {updated} locale file(s).")
    elif total == 0:
        print("\nNothing to do — all locale files are already up to date.")
    else:
        print(f"\nDone — translated {total} new key(s) across {updated} locale file(s).")
        print("Review machine translations before release, especially for de/zh hand-maintained locales.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
