#!/usr/bin/env python3
"""Reject an APK containing a stale or missing Freeform native library."""

import argparse
from pathlib import Path
import struct
import zipfile


def build_id(data: bytes) -> str:
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected a little-endian ELF64 library")
    phoff = struct.unpack_from("<Q", data, 32)[0]
    phsize, phcount = struct.unpack_from("<HH", data, 54)
    for index in range(phcount):
        header = phoff + index * phsize
        if struct.unpack_from("<I", data, header)[0] != 4:  # PT_NOTE
            continue
        offset = struct.unpack_from("<Q", data, header + 8)[0]
        end = offset + struct.unpack_from("<Q", data, header + 32)[0]
        while offset + 12 <= end:
            namesize, descsize, kind = struct.unpack_from("<III", data, offset)
            offset += 12
            name = data[offset:offset + namesize]
            offset += (namesize + 3) & ~3
            description = data[offset:offset + descsize]
            offset += (descsize + 3) & ~3
            if kind == 3 and name == b"GNU\0" and len(description) == descsize:
                return description.hex()
    raise ValueError("ELF library has no GNU build ID")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=("debug", "release"), default="release")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--native-library", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    apk = args.apk or root / f"app/build/outputs/apk/{args.variant}/app-{args.variant}.apk"
    candidates = list((root / "app/build/intermediates/cxx" / args.variant.capitalize()).glob(
        "*/obj/local/arm64-v8a/libfreeformnative.so"))
    library = args.native_library or (max(candidates, key=lambda p: p.stat().st_mtime) if candidates else None)
    if library is None:
        raise SystemExit("Native verification failed: no compiled arm64-v8a library")
    try:
        expected = build_id(library.read_bytes())
        with zipfile.ZipFile(apk) as archive:
            actual = build_id(archive.read("lib/arm64-v8a/libfreeformnative.so"))
        if actual != expected:
            raise ValueError(f"APK build ID {actual} does not match compiled library {expected}")
    except (OSError, KeyError, ValueError, struct.error, zipfile.BadZipFile) as error:
        raise SystemExit(f"Native verification failed: {error}") from error
    print(f"Native OK: {args.variant} arm64-v8a build ID {actual}")


if __name__ == "__main__":
    main()
