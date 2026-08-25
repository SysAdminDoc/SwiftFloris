#!/usr/bin/env python3
"""Verify an APK's native libraries are 16 KB page compatible.

Android 15 introduced devices with a 16 KB memory page size, and from
Android 16 Google Play requires new and updated apps to support them. A
library built for 4 KB pages will not load there, and the failure is a hard
crash at first use rather than a warning.

Two separate things have to hold, and only one of them is what `zipalign`
checks:

* Every `lib/**/*.so` entry must be **stored** (uncompressed) and start on a
  16 KB boundary inside the ZIP, so the loader can map it directly.
* Every `PT_LOAD` segment inside each ELF must declare `p_align` of at least
  16384, which is a property of how the library was linked and survives any
  amount of re-zipping.

Only addon APKs were checked before this, and only for the first condition,
while the app itself ships SQLCipher's native libraries across four ABIs.

Run: python scripts/check-apk-16kb-alignment.py <apk> [more.apk ...]
"""

from __future__ import annotations

import struct
import sys
import zipfile
from pathlib import Path

PAGE_SIZE = 16 * 1024
PT_LOAD = 1
NATIVE_LIB_SUFFIX = ".so"


class ElfError(Exception):
    """The bytes are not an ELF we can read program headers out of."""


def load_segment_alignments(blob: bytes) -> list[int]:
    """Returns the `p_align` of every PT_LOAD segment in [blob].

    Deliberately hand-rolled rather than shelling out to readelf or llvm-objdump:
    this has to run on the machine that produced the APK, and on Windows neither
    tool is reliably on PATH.
    """
    if len(blob) < 64 or blob[:4] != b"\x7fELF":
        raise ElfError("not an ELF file")

    ei_class = blob[4]
    ei_data = blob[5]
    if ei_class not in (1, 2):
        raise ElfError(f"unknown ELF class {ei_class}")
    if ei_data not in (1, 2):
        raise ElfError(f"unknown ELF data encoding {ei_data}")
    endian = "<" if ei_data == 1 else ">"
    is_64 = ei_class == 2

    if is_64:
        e_phoff = struct.unpack_from(endian + "Q", blob, 0x20)[0]
        e_phentsize = struct.unpack_from(endian + "H", blob, 0x36)[0]
        e_phnum = struct.unpack_from(endian + "H", blob, 0x38)[0]
    else:
        e_phoff = struct.unpack_from(endian + "I", blob, 0x1C)[0]
        e_phentsize = struct.unpack_from(endian + "H", blob, 0x2A)[0]
        e_phnum = struct.unpack_from(endian + "H", blob, 0x2C)[0]

    if e_phoff == 0 or e_phnum == 0:
        raise ElfError("no program headers")

    alignments: list[int] = []
    for index in range(e_phnum):
        offset = e_phoff + index * e_phentsize
        if offset + e_phentsize > len(blob):
            raise ElfError("program header table runs past end of file")
        p_type = struct.unpack_from(endian + "I", blob, offset)[0]
        if p_type != PT_LOAD:
            continue
        # p_align is the last field of the program header in both widths.
        align_offset = offset + (0x30 if is_64 else 0x1C)
        fmt = endian + ("Q" if is_64 else "I")
        alignments.append(struct.unpack_from(fmt, blob, align_offset)[0])
    if not alignments:
        raise ElfError("no PT_LOAD segments")
    return alignments


def check_apk(apk_path: Path, expected_abis: set[str] | None = None) -> list[str]:
    problems: list[str] = []
    with zipfile.ZipFile(apk_path) as apk:
        libs = [
            info
            for info in apk.infolist()
            if info.filename.startswith("lib/")
            and info.filename.endswith(NATIVE_LIB_SUFFIX)
            and not info.is_dir()
        ]
        found_abis = {info.filename.split("/")[1] for info in libs if info.filename.count("/") >= 2}

        if expected_abis is not None:
            missing = sorted(expected_abis - found_abis)
            if missing:
                problems.append(
                    f"{apk_path.name}: expected native libraries for {missing} and found none. "
                    f"An ABI that disappears makes this check pass by having nothing to check."
                )
            unexpected = sorted(found_abis - expected_abis)
            if unexpected:
                problems.append(
                    f"{apk_path.name}: carries unexpected ABIs {unexpected}; "
                    f"add them to the expected set once they are meant to ship"
                )

        if not libs:
            if expected_abis:
                return problems
            print(f"  {apk_path.name}: no native libraries")
            return problems

        for info in sorted(libs, key=lambda i: i.filename):
            name = info.filename

            if info.compress_type != zipfile.ZIP_STORED:
                problems.append(
                    f"{apk_path.name}: {name} is compressed; it must be stored so the loader can map it"
                )

            # data_offset is where the entry's bytes actually start, past its
            # local header and any name/extra padding.
            header_offset = info.header_offset
            apk.fp.seek(header_offset + 26)
            name_len, extra_len = struct.unpack("<HH", apk.fp.read(4))
            data_offset = header_offset + 30 + name_len + extra_len
            if data_offset % PAGE_SIZE != 0:
                problems.append(
                    f"{apk_path.name}: {name} starts at byte {data_offset}, "
                    f"which is not a multiple of {PAGE_SIZE}"
                )

            blob = apk.read(name)
            try:
                alignments = load_segment_alignments(blob)
            except ElfError as error:
                problems.append(f"{apk_path.name}: {name} could not be parsed as ELF ({error})")
                continue

            worst = min(alignments)
            if worst < PAGE_SIZE:
                problems.append(
                    f"{apk_path.name}: {name} has a PT_LOAD segment with p_align {worst}, "
                    f"below {PAGE_SIZE}; relink with NDK r28+ or "
                    f"-Wl,-z,max-page-size={PAGE_SIZE}"
                )
            else:
                print(f"  PASS {name} (stored, page-aligned, p_align {worst})")
    return problems


# What the app is expected to ship. Named rather than inferred so an ABI that
# silently disappears (a stray abiFilters, a dropped dependency) fails this gate
# instead of making it pass by leaving nothing to check.
RELEASE_ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}


def main(argv: list[str]) -> int:
    args = [a for a in argv[1:] if a != "--any-abis"]
    expected: set[str] | None = RELEASE_ABIS if len(args) == len(argv) - 1 else None
    if not args:
        print("::error::usage: check-apk-16kb-alignment.py [--any-abis] <apk> [more.apk ...]")
        return 2

    all_problems: list[str] = []
    for raw in args:
        apk_path = Path(raw)
        if not apk_path.is_file():
            print(f"::error::APK not found: {apk_path}")
            return 1
        print(f"16 KB alignment: {apk_path}")
        all_problems.extend(check_apk(apk_path, expected_abis=expected))

    for problem in all_problems:
        print(f"::error::{problem}")
    if all_problems:
        return 1
    print("16 KB alignment: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
