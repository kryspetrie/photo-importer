"""Shared helpers for translating locale JSON files from en.json."""

from __future__ import annotations

import json
import re
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
I18N_DIR = ROOT / "src/main/resources/i18n"
SOURCE = I18N_DIR / "en.json"
DELIM = "\n###I18N###\n"
BATCH_SIZE = 40
BATCH_SLEEP_SECONDS = 0.5

# Keys from UI consistency phases that should be translated (not left as English copy-paste).
TRANSLATED_UI_KEYS: frozenset[str] = frozenset(
    {
        "SETTINGS_VERIFY_COPIES_DESC",
        "SETTINGS_DELETE_SOURCE_DESC",
        "APP_KS_TABS",
        "APP_KS_ENTER_SUBMIT",
        "APP_KS_WIZARD_HELP_HINT",
        "META_KS_EDITING",
        "META_KS_PREV_NEXT",
        "META_KS_SAVE",
        "META_KS_LOCATION",
        "META_KS_FACES",
        "META_KS_KEYWORDS",
        "META_KS_BROWSER",
        "META_KS_APPLY_MULTI",
    }
)

# Values that are intentionally identical in every locale (extensions, symbols).
IDENTICAL_ACROSS_LOCALES: frozenset[str] = frozenset({"SETTINGS_SIDECAR_TYPES_DESC"})

# Bundled locale code → Google Translate target language code.
LOCALES: dict[str, str] = {
    "de": "de",
    "zh": "zh-CN",
    "ja": "ja",
    "es": "es",
    "fr": "fr",
    "pt": "pt",
    "nl": "nl",
    "sv": "sv",
    "ru": "ru",
    "hi": "hi",
    "ar": "ar",
    "ko": "ko",
    "bn": "bn",
    "id": "id",
    "ur": "ur",
    "tr": "tr",
    "vi": "vi",
    "it": "it",
}

PLACEHOLDER_RE = re.compile(r"\{[^}]+\}")


class LocaleTranslateError(Exception):
    """Raised when locale sync cannot proceed."""


def load_en() -> dict[str, str]:
    if not SOURCE.exists():
        raise LocaleTranslateError(f"Missing source file: {SOURCE}")
    try:
        data = json.loads(SOURCE.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise LocaleTranslateError(f"Invalid JSON in {SOURCE}: {exc}") from exc
    if not isinstance(data, dict) or not data:
        raise LocaleTranslateError(f"{SOURCE} must be a non-empty JSON object")
    if not all(isinstance(k, str) and isinstance(v, str) for k, v in data.items()):
        raise LocaleTranslateError(f"{SOURCE} must contain only string keys and string values")
    return data


def load_locale(locale: str) -> dict[str, str]:
    path = I18N_DIR / f"{locale}.json"
    if not path.exists():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise LocaleTranslateError(f"Invalid JSON in {path}: {exc}") from exc
    if not isinstance(data, dict):
        raise LocaleTranslateError(f"{path} must be a JSON object")
    return data


def missing_keys(en: dict[str, str], locale: dict[str, str]) -> list[str]:
    return [key for key in en if key not in locale]


def stale_keys(en: dict[str, str], locale: dict[str, str]) -> list[str]:
    return [key for key in locale if key not in en]


def protect(text: str) -> tuple[str, dict[str, str]]:
    tokens: dict[str, str] = {}

    def repl(match: re.Match[str]) -> str:
        token = f"__PH{len(tokens)}__"
        tokens[token] = match.group(0)
        return token

    return PLACEHOLDER_RE.sub(repl, text), tokens


def restore(text: str, tokens: dict[str, str]) -> str:
    for token, value in tokens.items():
        text = text.replace(token, value)
    return text


def get_translator(source: str, target: str):
    try:
        from deep_translator import GoogleTranslator
    except ImportError as exc:
        raise LocaleTranslateError(
            "Install translation dependency: pip install deep-translator"
        ) from exc
    return GoogleTranslator(source=source, target=target)


def translate_one(text: str, translator) -> str:
    if not text.strip():
        return text
    protected, tokens = protect(text)
    try:
        translated = translator.translate(protected)
        return restore(translated or text, tokens)
    except Exception:
        return text


def translate_batch(texts: list[str], translator) -> list[str]:
    if not texts:
        return []
    protected: list[str] = []
    token_maps: list[dict[str, str]] = []
    for text in texts:
        p, tokens = protect(text)
        protected.append(p)
        token_maps.append(tokens)
    joined = DELIM.join(protected)
    try:
        translated = translator.translate(joined)
        if not translated:
            return texts
        parts = translated.split("###I18N###")
        if len(parts) != len(texts):
            return [translate_one(t, translator) for t in texts]
        return [restore(part.strip(), token_maps[i]) for i, part in enumerate(parts)]
    except Exception as exc:  # noqa: BLE001
        print(f"  batch warn: {exc!r}", file=sys.stderr)
        return [translate_one(t, translator) for t in texts]


def write_locale(locale: str, strings: dict[str, str], en: dict[str, str]) -> Path:
    ordered = {key: strings[key] for key in en if key in strings}
    path = I18N_DIR / f"{locale}.json"
    path.write_text(json.dumps(ordered, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return path


def english_fallback_keys(en: dict[str, str], locale_data: dict[str, str]) -> list[str]:
    """Keys present in both maps whose value still matches en.json (machine copy-paste)."""
    return [
        key
        for key in TRANSLATED_UI_KEYS
        if key in en
        and key in locale_data
        and key not in IDENTICAL_ACROSS_LOCALES
        and locale_data[key] == en[key]
    ]


def fix_english_fallbacks_from_en(
    locale: str,
    *,
    dry_run: bool = False,
    sleep_seconds: float = BATCH_SLEEP_SECONDS,
) -> int:
    """Re-translate [TRANSLATED_UI_KEYS] that still match en.json in [locale]. Returns count fixed."""
    if locale not in LOCALES:
        raise LocaleTranslateError(f"Unknown locale code: {locale}")

    en = load_en()
    existing = load_locale(locale)
    keys_to_fix = english_fallback_keys(en, existing)
    if not keys_to_fix:
        print(f"  {locale}: no English fallbacks in UI keys")
        return 0

    if dry_run:
        preview = ", ".join(keys_to_fix[:5])
        suffix = "..." if len(keys_to_fix) > 5 else ""
        print(f"  {locale}: would re-translate {len(keys_to_fix)} keys ({preview}{suffix})")
        return len(keys_to_fix)

    translator = get_translator("en", LOCALES[locale])
    merged = dict(existing)
    for start in range(0, len(keys_to_fix), BATCH_SIZE):
        batch_keys = keys_to_fix[start : start + BATCH_SIZE]
        batch_values = [en[key] for key in batch_keys]
        translated = translate_batch(batch_values, translator)
        for key, value in zip(batch_keys, translated):
            merged[key] = value
        end = min(start + BATCH_SIZE, len(keys_to_fix))
        print(f"  {locale}: re-translated {end}/{len(keys_to_fix)} English fallbacks")
        time.sleep(sleep_seconds)

    path = write_locale(locale, merged, en)
    print(f"  {locale}: wrote {path} ({len(merged)} keys)")
    return len(keys_to_fix)


def fix_all_english_fallbacks(
    locales: list[str] | None = None,
    *,
    dry_run: bool = False,
) -> dict[str, int]:
    targets = locales or list(LOCALES.keys())
    fixed: dict[str, int] = {}
    for locale in targets:
        fixed[locale] = fix_english_fallbacks_from_en(locale, dry_run=dry_run)
    return fixed


def merge_missing_from_en(
    locale: str,
    *,
    dry_run: bool = False,
    sleep_seconds: float = BATCH_SLEEP_SECONDS,
) -> int:
    """Translate keys present in en.json but missing from locale file. Returns count added."""
    if locale not in LOCALES:
        raise LocaleTranslateError(f"Unknown locale code: {locale}")

    en = load_en()
    existing = load_locale(locale)
    keys_to_add = missing_keys(en, existing)
    if not keys_to_add:
        print(f"  {locale}: up to date ({len(existing)} keys)")
        return 0

    stale = stale_keys(en, existing)
    if stale:
        print(f"  {locale}: removing {len(stale)} stale keys not in en.json")

    if dry_run:
        preview = ", ".join(keys_to_add[:5])
        suffix = "..." if len(keys_to_add) > 5 else ""
        print(f"  {locale}: would translate {len(keys_to_add)} keys ({preview}{suffix})")
        return len(keys_to_add)

    translator = get_translator("en", LOCALES[locale])
    merged = dict(existing)
    for start in range(0, len(keys_to_add), BATCH_SIZE):
        batch_keys = keys_to_add[start : start + BATCH_SIZE]
        batch_values = [en[key] for key in batch_keys]
        translated = translate_batch(batch_values, translator)
        for key, value in zip(batch_keys, translated):
            merged[key] = value
        end = min(start + BATCH_SIZE, len(keys_to_add))
        print(f"  {locale}: translated {end}/{len(keys_to_add)} new keys")
        time.sleep(sleep_seconds)

    path = write_locale(locale, merged, en)
    print(f"  {locale}: wrote {path} ({len(merged)} keys)")
    return len(keys_to_add)


def sync_all_locales(
    locales: list[str] | None = None,
    *,
    dry_run: bool = False,
) -> dict[str, int]:
    targets = locales or list(LOCALES.keys())
    added: dict[str, int] = {}
    for locale in targets:
        added[locale] = merge_missing_from_en(locale, dry_run=dry_run)
    return added


def check_all_locales_synced(locales: list[str] | None = None) -> list[str]:
    """Return human-readable errors when any locale is missing en.json keys."""
    en = load_en()
    targets = locales or list(LOCALES.keys())
    errors: list[str] = []
    for locale in targets:
        if locale not in LOCALES:
            errors.append(f"unknown locale: {locale}")
            continue
        missing = missing_keys(en, load_locale(locale))
        if missing:
            preview = ", ".join(missing[:5])
            suffix = "..." if len(missing) > 5 else ""
            errors.append(f"{locale}.json missing {len(missing)} keys: {preview}{suffix}")
    return errors
