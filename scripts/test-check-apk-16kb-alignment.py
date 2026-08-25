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


def elf32(p_align: int) -> bytes:
    """A minimal little-endian ELF32 with one PT_LOAD segment at [p_align].

    armeabi-v7a is 32-bit, so the ELF32 branch parses two of the eight libraries
    that actually ship. Without a fixture it was the only branch nothing covered.
    """
    e_phoff = 52
    e_phentsize = 32
    header = bytearray(52)
    header[0:4] = b"\x7fELF"
    header[4] = 1  # ELFCLASS32
    header[5] = 1  # ELFDATA2LSB
    header[6] = 1  # EV_CURRENT
    struct.pack_into("<H", header, 0x10, 3)  # e_type = ET_DYN
    struct.pack_into("<H", header, 0x12, 0x28)  # e_machine = ARM
    struct.pack_into("<I", header, 0x14, 1)  # e_version
    struct.pack_into("<I", header, 0x1C, e_phoff)
    struct.pack_into("<H", header, 0x28, 52)  # e_ehsize
    struct.pack_into("<H", header, 0x2A, e_phentsize)
    struct.pack_into("<H", header, 0x2C, 1)  # e_phnum

    phdr = bytearray(e_phentsize)
    struct.pack_into("<I", phdr, 0x00, 1)  # p_type = PT_LOAD
    struct.pack_into("<I", phdr, 0x1C, p_align)
    # Padded to 64 bytes so the minimum-length guard in the checker is satisfied.
    return (bytes(header) + bytes(phdr)).ljust(64, b"\x00")


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


def write_apk(
    path: Path,
    *,
    p_align: int,
    stored: bool,
    pad_to_page: bool,
    bits: int = 64,
    abi: str = "arm64-v8a",
) -> None:
    """Builds an APK carrying one native library under the requested conditions."""
    name = f"lib/{abi}/libfixture.so"
    blob = elf64(p_align) if bits == 64 else elf32(p_align)
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


def run_checker(apk: Path, *extra: str) -> subprocess.CompletedProcess[str]:
    # Fixtures carry a single synthetic ABI, so they opt out of the shipping-ABI
    # expectation; that rule gets its own assertions below.
    return subprocess.run(
        [sys.executable, str(CHECKER), "--any-abis", *extra, str(apk)],
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

        # armeabi-v7a is ELF32, so the 32-bit branch parses two of the eight
        # libraries that ship. Both outcomes are covered.
        good32 = root / "aligned32.apk"
        write_apk(good32, p_align=PAGE_SIZE, stored=True, pad_to_page=True, bits=32, abi="armeabi-v7a")
        if expect(run_checker(good32), 0, "16 KB alignment: OK", "a compliant 32-bit library"):
            return 1

        small32 = root / "small-align32.apk"
        write_apk(small32, p_align=4096, stored=True, pad_to_page=True, bits=32, abi="armeabi-v7a")
        if expect(run_checker(small32), 1, "p_align 4096", "a 4 KB-linked 32-bit library"):
            return 1

        # Without --any-abis the gate expects the full shipping set, so an APK
        # carrying one ABI, or none at all, must fail rather than pass by having
        # nothing to inspect.
        missing_abis = subprocess.run(
            [sys.executable, str(CHECKER), str(good)],
            check=False, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
        )
        if expect(missing_abis, 1, "expected native libraries for", "an APK missing shipping ABIs"):
            return 1

        bare = root / "no-libs.apk"
        with zipfile.ZipFile(bare, "w") as apk:
            apk.writestr("classes.dex", b"\x00")
        no_libs = subprocess.run(
            [sys.executable, str(CHECKER), str(bare)],
            check=False, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
        )
        if expect(no_libs, 1, "expected native libraries for", "an APK with no native libraries at all"):
            return 1

    print("16 KB alignment checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
