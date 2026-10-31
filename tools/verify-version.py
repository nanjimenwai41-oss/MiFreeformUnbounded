#!/usr/bin/env python3
"""校验单一版本源、发布标签及已发布祖先版本。"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

from version_metadata import read_version, parse_properties


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


def validate_channel(name: str, channel: str) -> None:
    expected = "stable" if semver_key(name)[3] else "prerelease"
    if channel != expected:
        raise ValueError(f"releaseChannel must be {expected!r} for {name}")


def select_previous_release(releases: list, current_tag: str, is_ancestor) -> str:
    records = [record for page in releases for record in (page if isinstance(page, list) else [page])]
    candidates = []
    for record in records:
        tag = record.get("tag_name", "")
        if record.get("draft") or not record.get("published_at") or tag == current_tag or not tag.startswith("v"):
            continue
        try:
            key = semver_key(tag[1:])
        except ValueError:
            continue
        if is_ancestor(tag):
            candidates.append((key, tag))
    if not candidates:
        raise ValueError("No published ancestor release found")
    return max(candidates)[1]


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except OSError as exc:
        raise SystemExit(f"Cannot read {path}: {exc}") from exc


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", help="Optional release tag, for example v3.0.0")
    baseline = parser.add_mutually_exclusive_group()
    baseline.add_argument("--previous-ref", help="Require an upgrade over this existing Git ref")
    baseline.add_argument("--published-releases", type=Path, help="GitHub published-release JSON, including paginated arrays")
    parser.add_argument("--json", action="store_true", help="Print version metadata as JSON")
    parser.add_argument("--github-output", type=Path, help="Write validated publication flags for GitHub Actions")
    args = parser.parse_args()

    try:
        version_name, version_code = read_version(ROOT)
        channel = parse_properties(read(ROOT / "version.properties"))["releaseChannel"]
    except (OSError, KeyError, ValueError) as error:
        raise SystemExit(f"Version metadata error: {error}") from error

    if args.tag:
        expected_tag = f"v{version_name}"
        if args.tag != expected_tag:
            raise SystemExit(f"Tag {args.tag!r} does not match {expected_tag!r}")

    try:
        validate_version(version_name, version_code)
        validate_channel(version_name, channel)
        previous_ref = args.previous_ref
        if args.published_releases:
            def is_ancestor(tag: str) -> bool:
                return subprocess.run(
                    ["git", "merge-base", "--is-ancestor", tag, "HEAD"], cwd=ROOT,
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                ).returncode == 0
            previous_ref = select_previous_release(
                json.loads(read(args.published_releases)), args.tag or f"v{version_name}", is_ancestor,
            )
        if previous_ref:
            previous_name, previous_code = read_version(ROOT, previous_ref)
            verify_upgrade(version_name, version_code, previous_name, previous_code)
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"Version verification failed: {error}") from error

    if args.github_output:
        prerelease = channel == "prerelease"
        with args.github_output.open("a", encoding="utf-8") as output:
            output.write(f"prerelease={str(prerelease).lower()}\nlatest={str(not prerelease).lower()}\n")
    if args.json:
        print(json.dumps({"versionName": version_name, "versionCode": int(version_code),
                          "releaseChannel": channel, "prerelease": channel == "prerelease"}))
    else:
        print(f"Version OK: v{version_name} (versionCode {version_code})" +
              (f", previous release {previous_ref}" if previous_ref else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
