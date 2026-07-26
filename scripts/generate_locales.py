#!/usr/bin/env python3
"""Generate or refresh locale JSON files from en.json using machine translation.

For day-to-day workflow after editing en.json, prefer:
  python3 scripts/sync_locales_from_en.py

This script can still fully regenerate a locale file:
  pip install deep-translator
  python3 scripts/generate_locales.py --locale es --force
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from locale_translate import (  # noqa: E402
    BATCH_SIZE,
    BATCH_SLEEP_SECONDS,
    I18N_DIR,
    LOCALES,
    SOURCE,
    LocaleTranslateError,
    get_translator,
    load_en,
    merge_missing_from_en,
    translate_batch,
    write_locale,
)

SKIP_BY_DEFAULT = {"de", "zh"}


def generate(locale: str, target: str, force: bool) -> None:
    out = I18N_DIR / f"{locale}.json"
    if out.exists() and locale in SKIP_BY_DEFAULT and not force:
        print(f"skip {locale} (existing, use --force to overwrite)")
        return

    en = load_en()
    translator = get_translator("en", target)
    keys = list(en.keys())
    result: dict[str, str] = {}

    for start in range(0, len(keys), BATCH_SIZE):
        batch_keys = keys[start : start + BATCH_SIZE]
        batch_values = [en[k] for k in batch_keys]
        translated = batch_values if locale == "en" else translate_batch(batch_values, translator)
        for key, value in zip(batch_keys, translated):
            result[key] = value
        end = min(start + BATCH_SIZE, len(keys))
        print(f"  {locale}: {end}/{len(keys)}")
        time.sleep(BATCH_SLEEP_SECONDS)

    path = write_locale(locale, result, en)
    print(f"wrote {path} ({len(result)} keys)")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--locale", action="append")
    parser.add_argument("--force", action="store_true")
    parser.add_argument(
        "--merge-missing",
        action="store_true",
        help="Only translate keys missing from each locale (same as sync_locales_from_en.py)",
    )
    args = parser.parse_args()

    if args.merge_missing:
        targets = args.locale or list(LOCALES.keys())
    else:
        targets = args.locale or [code for code in LOCALES if code not in SKIP_BY_DEFAULT]

    try:
        for locale in targets:
            if locale not in LOCALES:
                print(f"unknown locale: {locale}", file=sys.stderr)
                continue
            if args.merge_missing:
                print(f"merging missing keys into {locale}...")
                merge_missing_from_en(locale)
            else:
                print(f"generating {locale}...")
                generate(locale, LOCALES[locale], args.force)
    except LocaleTranslateError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
