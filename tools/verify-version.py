#!/usr/bin/env python3
"""Verify that Gradle, BuildConfig, module.prop, and an optional tag agree."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def semver_key(name: str) -> tuple:
    match = re.fullmatch(
        r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
        r"(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
        r"(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?", name,
    )
    if not match:
        raise ValueError(f"Invalid Semantic Version: {name!r}")
    prerelease = match.group(4)
    identifiers = prerelease.split(".") if prerelease else []
    if any(part.isdigit() and len(part) > 1 and part.startswith("0") for part in identifiers):
        raise ValueError(f"Numeric prerelease identifiers must not have leading zeroes: {name!r}")
    return (*map(int, match.group(1, 2, 3)), not prerelease,
            tuple((0, int(part)) if part.isdigit() else (1, part) for part in identifiers))


def validate_version(name: str, code: str) -> None:
    semver_key(name)
    if not re.fullmatch(r"[1-9][0-9]*", code) or int(code) > 2_100_000_000:
        raise ValueError(f"versionCode must be a positive integer at most 2100000000: {code!r}")


def verify_upgrade(name: str, code: str, previous_name: str, previous_code: str) -> None:
    validate_version(name, code)
    validate_version(previous_name, previous_code)
    if int(code) <= int(previous_code):
        raise ValueError(f"versionCode {code} must exceed {previous_code}")
    if semver_key(name) <= semver_key(previous_name):
        raise ValueError(f"Version {name} must follow {previous_name}")


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
    parser.add_argument("--previous-ref", help="Require an upgrade over this existing Git ref")
    args = parser.parse_args()

    gradle = read(ROOT / "app/build.gradle.kts")
    build_config = read(ROOT / "app/src/main/java/com/freeform/unbounded/BuildConfig.kt")
    module_prop = read(ROOT / "app/src/main/resources/META-INF/xposed/module.prop")

    version_code = one(r"versionCode\s*=\s*(\d+)", gradle, "Gradle versionCode")
    version_name = one(
        r'versionName\s*=\s*"([^\"]+)"',
        gradle,
        "Gradle versionName",
    )

    build_code = one(r"VERSION_CODE:\s*Int\s*=\s*(\d+)", build_config, "BuildConfig VERSION_CODE")
    build_name = one(
        r'VERSION_NAME:\s*String\s*=\s*"([^\"]+)"',
        build_config,
        "BuildConfig VERSION_NAME",
    )

    module_code = one(r"(?m)^versionCode=(\d+)\s*$", module_prop, "module.prop versionCode")
    module_name = one(
        r"(?m)^version=v([^\r\n]+?)\s*$",
        module_prop,
        "module.prop version",
    )

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

    try:
        validate_version(version_name, version_code)
        if args.previous_ref:
            previous = subprocess.check_output(
                ["git", "show", f"{args.previous_ref}:app/build.gradle.kts"],
                cwd=ROOT, text=True, encoding="utf-8",
            )
            previous_name = one(r'versionName\s*=\s*"([^\"]+)"', previous, "previous versionName")
            previous_code = one(r"versionCode\s*=\s*(\d+)", previous, "previous versionCode")
            verify_upgrade(version_name, version_code, previous_name, previous_code)
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"Version verification failed: {error}") from error

    print(f"Version OK: v{version_name} (versionCode {version_code})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
