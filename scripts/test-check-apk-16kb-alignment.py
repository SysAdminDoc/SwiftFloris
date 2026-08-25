#!/usr/bin/env python3
"""Self-test for the 16 KB alignment gate.

Each fixture breaks exactly one of the three things the gate checks, so a rule
that stops working stops passing here too. The ELF is synthesised rather than
copied out of a real APK: that keeps the fixture readable, and it lets the
p_align case be built at a value no real toolchain would emit.
"""

from __future__ import annotations

import struct
import subprocess
import sys
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-apk-16kb-alignment.py"
PAGE_SIZE = 16 * 1024


def elf64(p_align: int) -> bytes:
    """A minimal little-endian ELF64 with one PT_LOAD segment at [p_align]."""
    e_phoff = 64
    e_phentsize = 56
    header = bytearray(64)
    header[0:4] = b"\x7fELF"
    header[4] = 2  # ELFCLASS64
    header[5] = 1  # ELFDATA2LSB
    header[6] = 1  # EV_CURRENT
    struct.pack_into("<H", header, 0x10, 3)  # e_type = ET_DYN
    struct.pack_into("<H", header, 0x12, 0xB7)  # e_machine = AArch64
    struct.pack_into("<I", header, 0x14, 1)  # e_version
    struct.pack_into("<Q", header, 0x20, e_phoff)
    struct.pack_into("<H", header, 0x34, 64)  # e_ehsize
    struct.pack_into("<H", header, 0x36, e_phentsize)
    struct.pack_into("<H", header, 0x38, 1)  # e_phnum

    phdr = bytearray(e_phentsize)
    struct.pack_into("<I", phdr, 0x00, 1)  # p_type = PT_LOAD
    struct.pack_into("<I", phdr, 0x04, 5)  # p_flags = R+X
    struct.pack_into("<Q", phdr, 0x30, p_align)
    return bytes(header) + bytes(phdr)


def write_apk(path: Path, *, p_align: int, stored: bool, pad_to_page: bool) -> None:
    """Builds an APK carrying one native library under the requested conditions."""
    name = "lib/arm64-v8a/libfixture.so"
    blob = elf64(p_align)
    with zipfile.ZipFile(path, "w") as apk:
        if pad_to_page:
            # Grow an earlier entry's extra field until the library's data lands
            # on a page boundary, which is what zipalign -P 16 does.
            for attempt in range(PAGE_SIZE):
                apk.writestr(f"filler{attempt}", b"\x00")
                offset = _pending_data_offset(apk, name)
                if offset % PAGE_SIZE == 0:
                    break
        info = zipfile.ZipInfo(name)
        info.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
        apk.writestr(info, blob)


def _pending_data_offset(apk: zipfile.ZipFile, name: str) -> int:
    """Where the next entry named [name] would place its payload."""
    return apk.fp.tell() + 30 + len(name.encode("utf-8"))


def run_checker(apk: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(CHECKER), str(apk)],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def expect(result: subprocess.CompletedProcess[str], code: int, needle: str, what: str) -> int:
    if result.returncode != code or needle not in result.stdout:
        print(result.stdout)
        print(f"expected {what} to exit {code} mentioning {needle!r}")
        return 1
    return 0


def main() -> int:
    with TemporaryDirectory() as tmp:
        root = Path(tmp)

        good = root / "aligned.apk"
        write_apk(good, p_align=PAGE_SIZE, stored=True, pad_to_page=True)
        if expect(run_checker(good), 0, "16 KB alignment: OK", "a compliant library"):
            return 1

        small_align = root / "small-align.apk"
        write_apk(small_align, p_align=4096, stored=True, pad_to_page=True)
        if expect(run_checker(small_align), 1, "p_align 4096", "a 4 KB-linked library"):
            return 1

        compressed = root / "compressed.apk"
        write_apk(compressed, p_align=PAGE_SIZE, stored=False, pad_to_page=False)
        if expect(run_checker(compressed), 1, "is compressed", "a compressed library"):
            return 1

        unpadded = root / "unpadded.apk"
        write_apk(unpadded, p_align=PAGE_SIZE, stored=True, pad_to_page=False)
        if expect(run_checker(unpadded), 1, "not a multiple of", "a library off the page boundary"):
            return 1

    print("16 KB alignment checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
