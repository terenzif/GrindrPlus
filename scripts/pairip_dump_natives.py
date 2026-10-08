#!/usr/bin/env python3
"""Dump PairIP-decrypted native RX from a running stock Grindr process.

Requires: adb + root, Grindr launched with Vector module disabled so PairIP
can decrypt in-process. Writes a decrypt pack keyed by versionCode.

Usage:
  python scripts/pairip_dump_natives.py --package com.grindrapp.android \\
      --version-code 185656 --out pairip-decrypt/185656
"""

from __future__ import annotations

import argparse
import re
import struct
import subprocess
import sys
from pathlib import Path


def adb(*args: str, check: bool = True) -> str:
    r = subprocess.run(
        ["adb", *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr or r.stdout}")
    return (r.stdout or "") + (r.stderr or "")


def adb_su(cmd: str) -> str:
    return adb("shell", "su", "-c", cmd)


def pid_of(package: str) -> int:
    out = adb("shell", "pidof", package, check=False).strip()
    if not out:
        raise RuntimeError(f"no process for {package}; launch stock Grindr first")
    return int(out.split()[0])


def maps_for(pid: int, needle: str) -> list[tuple[int, int, int]]:
    """Return list of (start, end, file_offset) for r-x maps matching needle."""
    text = adb_su(f"cat /proc/{pid}/maps")
    rows: list[tuple[int, int, int]] = []
    for line in text.splitlines():
        if needle not in line or "r-x" not in line:
            continue
        m = re.match(
            r"^([0-9a-f]+)-([0-9a-f]+)\s+r-xp\s+([0-9a-f]+)\s+",
            line,
            re.I,
        )
        if not m:
            continue
        rows.append((int(m.group(1), 16), int(m.group(2), 16), int(m.group(3), 16)))
    return rows


def dump_range(pid: int, start: int, size: int, remote: str) -> None:
    # dd skip/count in 4096-byte pages; require page alignment
    if start % 4096 or size % 4096:
        # round size up
        end = start + size
        start_a = start & ~4095
        end_a = (end + 4095) & ~4095
        skip = start_a // 4096
        count = (end_a - start_a) // 4096
        adb_su(
            f"dd if=/proc/{pid}/mem bs=4096 skip={skip} count={count} "
            f"of={remote} 2>/dev/null"
        )
        # trim leading/trailing pad locally after pull
    else:
        adb_su(
            f"dd if=/proc/{pid}/mem bs=4096 skip={start // 4096} "
            f"count={size // 4096} of={remote} 2>/dev/null"
        )


def elf_rx_file_range(so: bytes) -> tuple[int, int, int]:
    """Return (file_offset, filesz, vaddr) for the first executable PT_LOAD."""
    e_phoff = struct.unpack_from("<Q", so, 32)[0]
    e_phentsize, e_phnum = struct.unpack_from("<HH", so, 54)
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = (
            struct.unpack_from("<IIQQQQQQ", so, off)
        )
        if p_type == 1 and (p_flags & 1):
            return p_offset, p_filesz, p_vaddr
    raise RuntimeError("no RX PT_LOAD")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--package", default="com.grindrapp.android")
    ap.add_argument("--version-code", type=int, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument(
        "--apk-so",
        type=Path,
        help="Encrypted libsqliteJni.so extracted from split APK (for splice)",
    )
    ap.add_argument("--lib-name", default="libsqliteJni.so")
    args = ap.parse_args()

    pid = pid_of(args.package)
    # Prefer named map; else APK split maps containing the lib (offset heuristic)
    rows = maps_for(pid, args.lib_name)
    if not rows:
        # Fall back: contiguous r-x mappings of arm64 split starting near zip entry
        rows = maps_for(pid, "split_config.arm64_v8a.apk")
        if not rows:
            raise RuntimeError("no r-x maps found; is the library loaded yet?")
        # Keep the largest contiguous run by file offset for sqlite-sized RX (~0xb5000)
        rows = sorted(rows, key=lambda r: r[2])
        # Heuristic: pick the run whose total size is closest to 0xb5000
        best: list[tuple[int, int, int]] = []
        best_score = 10**18
        i = 0
        while i < len(rows):
            j = i
            total = 0
            start_fo = rows[i][2]
            while j < len(rows) and rows[j][2] == start_fo + total:
                total += rows[j][1] - rows[j][0]
                j += 1
            score = abs(total - 0xB5000)
            if score < best_score:
                best_score = score
                best = rows[i:j]
            i = j if j > i else i + 1
        rows = best
        print(f"using arm64 split RX run maps={len(rows)} score={best_score}")

    start = rows[0][0]
    end = rows[-1][1]
    size = end - start
    print(f"pid={pid} dump [{hex(start)}, {hex(end)}) size={hex(size)}")

    remote = "/data/local/tmp/gpp_pairip_rx.bin"
    page_start = start & ~4095
    page_end = (end + 4095) & ~4095
    adb_su(
        f"dd if=/proc/{pid}/mem bs=4096 skip={page_start // 4096} "
        f"count={(page_end - page_start) // 4096} of={remote} 2>/dev/null"
    )
    args.out.mkdir(parents=True, exist_ok=True)
    local_raw = args.out / f"{args.lib_name}.rx.raw"
    adb("pull", remote, str(local_raw))
    raw = local_raw.read_bytes()
    trim = start - page_start
    rx = raw[trim : trim + size]
    (args.out / f"{args.lib_name}.rx.bin").write_bytes(rx)
    print(f"wrote {args.lib_name}.rx.bin ({len(rx)} bytes)")

    if args.apk_so and args.apk_so.is_file():
        so = bytearray(args.apk_so.read_bytes())
        fo, fsz, vaddr = elf_rx_file_range(so)
        # Process maps are page-aligned to vaddr&~0xfff; PT_LOAD may start mid-page.
        map_base = vaddr & ~0xFFF
        skip = vaddr - map_base
        chunk = rx[skip : skip + fsz]
        if len(chunk) < fsz:
            raise RuntimeError(
                f"RX dump too short for PT_LOAD (need {fsz}+{skip}, have {len(rx)})"
            )
        so[fo : fo + fsz] = chunk
        out_so = args.out / args.lib_name
        out_so.write_bytes(so)
        print(
            f"spliced decrypted RX into {out_so} @ file_off={hex(fo)} "
            f"skip={hex(skip)} n={hex(fsz)}"
        )
        print(f"RX@JNI-ish {rx[0x7B4:0x7B4 + 16].hex()}")

    meta = args.out / "pack.json"
    meta.write_text(
        "{\n"
        f'  "versionCode": {args.version_code},\n'
        f'  "package": "{args.package}",\n'
        f'  "libs": ["{args.lib_name}"],\n'
        f'  "note": "Stock in-memory RX dump; PairIP decrypted .text"\n'
        "}\n",
        encoding="utf-8",
    )
    print(f"pack -> {args.out}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as e:
        print(f"error: {e}", file=sys.stderr)
        raise SystemExit(1)
