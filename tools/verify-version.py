#!/usr/bin/env python3
"""Verify that Gradle, BuildConfig, module.prop, and an optional tag agree."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except OSError as exc:
        raise SystemExit(f"Cannot read {path}: {exc}") from exc


def one(pattern: str, text: str, label: str) -> str:
    match = re.search(pattern, text)
    if not match:
        raise SystemExit(f"Missing {label}")
    return match.group(1)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", help="Optional release tag, for example v3.0.0")
    args = parser.parse_args()

    gradle = read(ROOT / "app/build.gradle.kts")
    build_config = read(ROOT / "app/src/main/java/com/freeform/unbounded/BuildConfig.kt")
    module_prop = read(ROOT / "app/src/main/resources/META-INF/xposed/module.prop")

    version_code = one(r"versionCode\s*=\s*(\d+)", gradle, "Gradle versionCode")
    version_name = one(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"', gradle, "Gradle versionName")

    build_code = one(r"VERSION_CODE:\s*Int\s*=\s*(\d+)", build_config, "BuildConfig VERSION_CODE")
    build_name = one(r'VERSION_NAME:\s*String\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"', build_config, "BuildConfig VERSION_NAME")

    module_code = one(r"(?m)^versionCode=(\d+)\s*$", module_prop, "module.prop versionCode")
    module_name = one(r"(?m)^version=v([0-9]+\.[0-9]+\.[0-9]+)\s*$", module_prop, "module.prop version")

    versions = {
        "Gradle": (version_name, version_code),
        "BuildConfig": (build_name, build_code),
        "module.prop": (module_name, module_code),
    }
    if len({value for value, _ in versions.values()}) != 1 or len({code for _, code in versions.values()}) != 1:
        details = ", ".join(f"{source}={name} ({code})" for source, (name, code) in versions.items())
        raise SystemExit(f"Version mismatch: {details}")

    if args.tag:
        expected_tag = f"v{version_name}"
        if args.tag != expected_tag:
            raise SystemExit(f"Tag {args.tag!r} does not match {expected_tag!r}")

    print(f"Version OK: v{version_name} (versionCode {version_code})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
