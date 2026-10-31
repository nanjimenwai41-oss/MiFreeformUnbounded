from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"


class ModuleScopeTest(unittest.TestCase):
    def test_api_102_static_scope_is_enabled(self):
        properties = dict(
            line.split("=", 1)
            for line in (MAIN / "resources/META-INF/xposed/module.prop")
            .read_text(encoding="utf-8").splitlines()
            if line and not line.startswith("#")
        )
        self.assertEqual("true", properties["staticScope"])
        self.assertEqual("102", properties["minApiVersion"])
        self.assertEqual("102", properties["targetApiVersion"])

    def test_packaged_scope_matches_all_nine_restart_targets(self):
        declared = (MAIN / "resources/META-INF/xposed/scope.list").read_text(
            encoding="utf-8"
        ).splitlines()
        restart_source = (MAIN / "java/com/freeform/unbounded/RestartScopes.kt").read_text(
            encoding="utf-8"
        )
        restart_packages = re.findall(
            r'RestartScope\("[^"]+",\s*"[^"]+",\s*"([^"]+)"', restart_source
        )
        self.assertEqual(9, len(restart_packages))
        self.assertEqual(restart_packages, declared)
        self.assertEqual(len(declared), len(set(declared)))
        for package in declared:
            self.assertRegex(package, r"^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$")

    def test_static_scope_packages_are_visible_to_the_module(self):
        manifest = ET.parse(MAIN / "AndroidManifest.xml").getroot()
        visible = {
            package.attrib["{http://schemas.android.com/apk/res/android}name"]
            for package in manifest.findall("queries/package")
        }
        declared = set((MAIN / "resources/META-INF/xposed/scope.list").read_text(
            encoding="utf-8"
        ).splitlines())
        self.assertEqual(declared, visible)


if __name__ == "__main__":
    unittest.main()
