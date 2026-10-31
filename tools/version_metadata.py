"""读取当前版本源，并兼容历史标签中的 Gradle 版本定义。"""

from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parents[1]


def parse_properties(text: str) -> dict[str, str]:
    result = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith(("#", "!")):
            continue
        key, value = line.split("=", 1)
        if key.strip() in result:
            raise ValueError(f"Duplicate property: {key.strip()}")
        result[key.strip()] = value.strip()
    return result


def read_version(root: Path = ROOT, ref: str | None = None) -> tuple[str, str]:
    def read(path: str) -> str | None:
        if ref is None:
            file = root / path
            return file.read_text(encoding="utf-8") if file.is_file() else None
        result = subprocess.run(
            ["git", "show", f"{ref}:{path}"], cwd=root,
            capture_output=True, text=True, encoding="utf-8",
        )
        return result.stdout if result.returncode == 0 else None

    properties = read("version.properties")
    if properties is not None:
        values = parse_properties(properties)
        return values["versionName"], values["versionCode"]
    if ref is None:
        raise ValueError("Missing version.properties")
    legacy = read("app/build.gradle.kts")
    if legacy is None:
        raise ValueError(f"No version metadata at {ref}")
    name = re.search(r'versionName\s*=\s*"([^\"]+)"', legacy)
    code = re.search(r"versionCode\s*=\s*(\d+)", legacy)
    if name is None or code is None:
        raise ValueError(f"Cannot read historical version at {ref}")
    return name.group(1), code.group(1)
