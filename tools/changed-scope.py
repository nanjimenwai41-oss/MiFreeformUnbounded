"""按改动范围决定 CI 是否需要 Android 构建与 native 策略测试。"""

import argparse
import os
from pathlib import Path
import subprocess


def classify(paths: list[str]) -> dict[str, bool]:
    android = native = False
    for path in paths:
        native |= path.startswith(("app/src/main/cpp/", "tools/tests/native-", "tools/tests/renderer-"))
        native |= path in ("tools/build-native-depth.ps1", "tools/verify-native.py", "app/build.gradle.kts")
        android |= path.startswith(("app/", "gradle/"))
        android |= path in ("version.properties", "gradle.properties", "settings.gradle.kts", "build.gradle.kts",
                            "gradlew", "gradlew.bat", ".github/workflows/ci.yml")
    return {"android": android or native, "native": native}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base")
    parser.add_argument("--all", action="store_true")
    args = parser.parse_args()
    if args.all or not args.base or set(args.base) == {"0"}:
        result = {"android": True, "native": True}
    else:
        paths = subprocess.check_output(
            ["git", "diff", "--name-only", args.base, "HEAD"], text=True, encoding="utf-8",
        ).splitlines()
        result = classify(paths)
    lines = [f"{key}={str(value).lower()}" for key, value in result.items()]
    print("\n".join(lines))
    if os.environ.get("GITHUB_OUTPUT"):
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
            output.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
