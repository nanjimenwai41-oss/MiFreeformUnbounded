#!/usr/bin/env python3
"""Check APK package/version, debuggable flag, and the established Release certificate."""

import argparse
from pathlib import Path
import os
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def sdk_tool(name: str) -> str:
    suffix = ".bat" if name == "apksigner" and os.name == "nt" else ".exe" if os.name == "nt" else ""
    found = shutil.which(name + suffix)
    if found:
        return found
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        properties = ROOT / "local.properties"
        if properties.is_file():
            match = re.search(r"(?m)^sdk.dir=(.+)$", properties.read_text(encoding="utf-8"))
            if match:
                sdk = match.group(1).strip().replace("\\:", ":").replace("\\\\", "\\")
    if sdk:
        candidates = [path for path in (Path(sdk) / "build-tools").glob("*/" + name + suffix) if path.is_file()]
        if candidates:
            return str(max(candidates, key=lambda path: tuple(int(part) for part in re.findall(r"\d+", path.parent.name))))
    raise SystemExit(f"Cannot locate Android SDK tool: {name}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=ROOT / "app/build/outputs/apk/release/app-release.apk")
    args = parser.parse_args()
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    version = re.search(r'versionName\s*=\s*"([^\"]+)"', gradle).group(1)
    code = re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1)
    expected_certificate = (ROOT / "tools/release-certificate.sha256").read_text().strip().lower()
    badging = subprocess.check_output([sdk_tool("aapt"), "dump", "badging", str(args.apk)], text=True, encoding="utf-8")
    if not re.search(rf"package: name='com\.freeform\.unbounded' versionCode='{re.escape(code)}' versionName='{re.escape(version)}'", badging):
        raise SystemExit("Release APK package/version does not match source metadata")
    if "application-debuggable" in badging:
        raise SystemExit("Release APK must not be debuggable")
    certificates = subprocess.check_output([sdk_tool("apksigner"), "verify", "--print-certs", str(args.apk)], text=True, encoding="utf-8")
    digests = re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F]+)", certificates)
    if len(digests) != 1 or digests[0].lower() != expected_certificate:
        raise SystemExit("Release APK certificate does not match the published legacy Release identity")
    print(f"Release APK OK: v{version} ({code}), non-debuggable, certificate SHA-256 {expected_certificate}")


if __name__ == "__main__":
    main()
