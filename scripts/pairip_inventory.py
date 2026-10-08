#!/usr/bin/env python3
"""Inventory PairIP markers in a Grindr APK / extracted dir (Morphe B spike helper).

Usage:
  python scripts/pairip_inventory.py path/to/base.apk
  python scripts/pairip_inventory.py path/to/extracted/
"""

from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path

PAIRIP_NAME = re.compile(r"pairip|libpairipcore|\.iap\b", re.I)
OPAQUE_ASSET = re.compile(r"^assets/[A-Za-z0-9_-]{8,}$")


def list_zip(apk: Path) -> list[str]:
    with zipfile.ZipFile(apk) as zf:
        return zf.namelist()


def list_dir(root: Path) -> list[str]:
    return [p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_file()]


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    target = Path(sys.argv[1])
    if not target.exists():
        print(f"missing: {target}", file=sys.stderr)
        return 1

    names = list_zip(target) if target.is_file() else list_dir(target)
    hits = sorted(n for n in names if PAIRIP_NAME.search(n))
    opaque = sorted(
        n
        for n in names
        if OPAQUE_ASSET.match(n) and not n.startswith("assets/io/")
    )
    natives = sorted(n for n in names if n.endswith(".so") and "arm64" in n)

    print(f"source={target}")
    print(f"pairip_path_hits={len(hits)}")
    for n in hits:
        print(f"  {n}")
    print(f"opaque_assets={len(opaque)} (candidates; confirm via VMRunner)")
    for n in opaque[:40]:
        print(f"  {n}")
    if len(opaque) > 40:
        print(f"  … +{len(opaque) - 40} more")
    print("key_natives:")
    for n in natives:
        if "pairip" in n.lower() or "sqlite" in n.lower():
            print(f"  {n}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
