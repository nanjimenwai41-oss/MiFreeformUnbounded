import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

TOOLS = Path(__file__).parents[1]
sys.path.insert(0, str(TOOLS))
from version_metadata import parse_properties, read_version

spec = importlib.util.spec_from_file_location("changed_scope", TOOLS / "changed-scope.py")
scope = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scope)


class BuildPolicyTest(unittest.TestCase):
    def test_documentation_changes_do_not_require_android(self):
        self.assertEqual({"android": False, "native": False}, scope.classify([
            "README.md", "DEVELOPMENT.md", "AGENTS.md", "releases/v3.1.1-rc.1.md",
        ]))

    def test_android_and_native_changes_keep_their_gates(self):
        self.assertEqual({"android": True, "native": False}, scope.classify(["app/src/main/java/Feature.kt"]))
        self.assertEqual({"android": True, "native": True}, scope.classify(["app/src/main/cpp/GpuDepthCapture.cpp"]))
        self.assertTrue(scope.classify(["version.properties"])["android"])
        self.assertTrue(scope.classify([".github/workflows/ci.yml"])["android"])

    def test_version_source_is_required_and_unambiguous(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(ValueError):
                read_version(root)
            (root / "version.properties").write_text("versionName=3.1.1-rc.1\nversionCode=20261096\n", encoding="utf-8")
            self.assertEqual(("3.1.1-rc.1", "20261096"), read_version(root))
        with self.assertRaises(ValueError):
            parse_properties("versionCode=1\nversionCode=2\n")

    def test_readmes_only_list_three_features(self):
        for name, heading in (("README.md", "## 功能"), ("README_EN.md", "## Features")):
            text = (TOOLS.parent / name).read_text(encoding="utf-8")
            section = text.split(heading + "\n", 1)[1].split("\n## ", 1)[0]
            self.assertEqual(3, sum(line[:1].isdigit() and line[1:3] == ". " for line in section.splitlines()))


if __name__ == "__main__":
    unittest.main()
