#!/usr/bin/env python3
"""Validate mapping-packs catalog and per-version JSON packs.

Exit 0 when catalog + packs are consistent enough to publish remotely.
Does not require a Grindr APK (fingerprint remaps stay manual / JADX).
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


REQUIRED_TOP = ("schemaVersion", "versionName", "versionCode", "symbols")
ALLOWED_KINDS = frozenset({"class", "method", "field"})
HOOK_STATUSES = frozenset({"mapped", "partial", "skipped", "failed"})


def load_json(path: Path) -> Any:
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def validate_pack(path: Path, expected_code: int | None = None) -> list[str]:
    errors: list[str] = []
    try:
        data = load_json(path)
    except (OSError, json.JSONDecodeError) as exc:
        return [f"{path.name}: cannot parse JSON ({exc})"]

    for key in REQUIRED_TOP:
        if key not in data:
            errors.append(f"{path.name}: missing top-level `{key}`")

    schema = data.get("schemaVersion")
    if schema not in (1, 2):
        errors.append(f"{path.name}: schemaVersion must be 1 or 2, got {schema!r}")

    vc = data.get("versionCode")
    if not isinstance(vc, int):
        errors.append(f"{path.name}: versionCode must be int")
    elif expected_code is not None and vc != expected_code:
        errors.append(
            f"{path.name}: versionCode {vc} != filename / catalog {expected_code}"
        )
    elif path.stem.isdigit() and vc != int(path.stem):
        errors.append(f"{path.name}: versionCode {vc} != filename stem {path.stem}")

    symbols = data.get("symbols")
    if not isinstance(symbols, dict) or not symbols:
        errors.append(f"{path.name}: symbols must be a non-empty object")
    else:
        empty = 0
        for key, sym in symbols.items():
            if not isinstance(sym, dict):
                errors.append(f"{path.name}: symbol `{key}` is not an object")
                continue
            kind = sym.get("kind", "class")
            if kind not in ALLOWED_KINDS:
                errors.append(f"{path.name}: symbol `{key}` bad kind {kind!r}")
            if "name" not in sym:
                errors.append(f"{path.name}: symbol `{key}` missing name")
            elif sym.get("name") == "":
                empty += 1
        if empty:
            # Advisory — soft-skips are allowed under schema v2.
            print(f"note: {path.name}: {empty}/{len(symbols)} symbols soft-skipped (empty name)")

    hooks = data.get("hooks")
    if hooks is not None:
        if not isinstance(hooks, dict):
            errors.append(f"{path.name}: hooks must be an object")
        else:
            for hook, meta in hooks.items():
                if not isinstance(meta, dict):
                    errors.append(f"{path.name}: hooks.{hook} must be an object")
                    continue
                status = meta.get("status")
                if status not in HOOK_STATUSES:
                    errors.append(
                        f"{path.name}: hooks.{hook}.status invalid: {status!r}"
                    )

    return errors


def validate_index(index_path: Path, packs_dir: Path) -> list[str]:
    errors: list[str] = []
    try:
        index = load_json(index_path)
    except (OSError, json.JSONDecodeError) as exc:
        return [f"index.json: cannot parse ({exc})"]

    packs = index.get("packs")
    if not isinstance(packs, list) or not packs:
        return ["index.json: packs must be a non-empty array"]

    seen: set[int] = set()
    for entry in packs:
        if not isinstance(entry, dict):
            errors.append("index.json: pack entry must be an object")
            continue
        vc = entry.get("versionCode")
        if not isinstance(vc, int):
            errors.append(f"index.json: bad versionCode {vc!r}")
            continue
        if vc in seen:
            errors.append(f"index.json: duplicate versionCode {vc}")
        seen.add(vc)
        pack_path = packs_dir / f"{vc}.json"
        if not pack_path.is_file():
            errors.append(f"index.json: missing pack file {pack_path.name}")
        else:
            errors.extend(validate_pack(pack_path, expected_code=vc))
            pack = load_json(pack_path)
            if entry.get("versionName") and pack.get("versionName") != entry.get(
                "versionName"
            ):
                errors.append(
                    f"index.json: versionName mismatch for {vc}: "
                    f"index={entry.get('versionName')!r} pack={pack.get('versionName')!r}"
                )

    orphan = sorted(
        int(p.stem)
        for p in packs_dir.glob("*.json")
        if p.stem.isdigit() and int(p.stem) not in seen
    )
    for vc in orphan:
        print(f"note: {vc}.json present but not listed in index.json")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--packs-dir",
        type=Path,
        default=Path("mapping-packs"),
        help="Remote / source pack directory (default: mapping-packs)",
    )
    parser.add_argument(
        "--assets-dir",
        type=Path,
        default=None,
        help="Optional bundled assets dir to cross-check (e.g. app/src/module/assets/mappings)",
    )
    parser.add_argument(
        "--require-tip",
        type=Path,
        default=Path("latest_play.json"),
        help="Ensure tip versionCode from this file has a pack (default: latest_play.json)",
    )
    args = parser.parse_args()

    packs_dir: Path = args.packs_dir
    index_path = packs_dir / "index.json"
    errors: list[str] = []

    if not packs_dir.is_dir():
        print(f"error: packs dir missing: {packs_dir}", file=sys.stderr)
        return 2

    if index_path.is_file():
        errors.extend(validate_index(index_path, packs_dir))
    else:
        errors.append(f"missing {index_path}")
        for pack in sorted(packs_dir.glob("*.json")):
            if pack.name == "index.json" or not pack.stem.isdigit():
                continue
            errors.extend(validate_pack(pack))

    if args.require_tip and args.require_tip.is_file():
        tip = load_json(args.require_tip)
        tip_code = tip.get("versionCode")
        tip_name = tip.get("versionName")
        tip_pack = packs_dir / f"{tip_code}.json"
        if not tip_pack.is_file():
            errors.append(
                f"tip {tip_name} ({tip_code}) has no pack at {tip_pack}"
            )
        else:
            print(f"ok: tip {tip_name} ({tip_code}) has pack {tip_pack.name}")

    if args.assets_dir:
        assets: Path = args.assets_dir
        if not assets.is_dir():
            errors.append(f"assets dir missing: {assets}")
        else:
            for pack in packs_dir.glob("*.json"):
                if not pack.stem.isdigit() and pack.name != "index.json":
                    continue
                peer = assets / pack.name
                if not peer.is_file():
                    errors.append(f"assets missing copy of {pack.name}")
                elif pack.read_bytes() != peer.read_bytes():
                    errors.append(f"assets drift: {pack.name} != {peer}")

    if errors:
        print("FAIL:")
        for err in errors:
            print(f"  - {err}")
        return 1

    print("PASS: mapping packs validated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
