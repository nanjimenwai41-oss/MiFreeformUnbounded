import importlib.util
from pathlib import Path
import unittest
import sys

sys.path.insert(0, str(Path(__file__).parents[1]))

spec = importlib.util.spec_from_file_location("verify_version", Path(__file__).parents[1] / "verify-version.py")
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


class VersionPolicyTest(unittest.TestCase):
    def test_release_channel_must_match_semver(self):
        policy.validate_channel("3.1.1-rc.1", "prerelease")
        policy.validate_channel("3.1.1", "stable")
        for name, channel in (("3.1.1", "prerelease"), ("3.1.1-rc.1", "stable"), ("3.1.1", "other")):
            with self.assertRaises(ValueError):
                policy.validate_channel(name, channel)

    def test_published_baseline_uses_semver_and_commit_ancestry(self):
        tags = ["v3.1.1-rc.9", "v3.1.1-rc.10", "v9.0.0", "v3.1.1-rc.11"]
        releases = [{"tag_name": tag, "draft": False, "published_at": "2026-10-31"} for tag in tags]
        self.assertEqual("v3.1.1-rc.10", policy.select_previous_release(
            releases, "v3.1.1-rc.11", lambda tag: tag != "v9.0.0"))

    def test_drafts_and_unpublished_tags_cannot_be_baselines(self):
        releases = [[{"tag_name": "v3.1.0", "draft": False, "published_at": "2026-10-30"}],
                    [{"tag_name": "v3.1.1-rc.1", "draft": True, "published_at": "2026-10-31"},
                     {"tag_name": "v3.1.1-rc.2", "draft": False, "published_at": None}]]
        self.assertEqual("v3.1.0", policy.select_previous_release(releases, "v3.1.1", lambda _: True))
        with self.assertRaises(ValueError):
            policy.select_previous_release(releases, "v3.1.1", lambda _: False)

    def test_stable_promotes_prerelease_with_higher_android_code(self):
        policy.verify_upgrade("3.1.0", "20261095", "3.0.2-ci.94", "20261094")
        policy.verify_upgrade("3.1.0", "20261096", "3.1.0-rc.1", "20261095")

    def test_reusing_or_lowering_android_code_is_rejected(self):
        for code in ("20261094", "20261093"):
            with self.assertRaises(ValueError):
                policy.verify_upgrade("3.1.0", code, "3.0.2-ci.94", "20261094")

    def test_semver_order_and_build_metadata(self):
        self.assertLess(policy.semver_key("3.1.0-ci.9"), policy.semver_key("3.1.0-ci.10"))
        self.assertLess(policy.semver_key("3.1.0-rc.1"), policy.semver_key("3.1.0"))
        self.assertEqual(policy.semver_key("3.1.0+build.1"), policy.semver_key("3.1.0+build.2"))

    def test_release_name_must_advance(self):
        for name in ("3.0.0", "3.1.0", "3.1.0-rc.2", "3.1.0+build.2"):
            with self.assertRaises(ValueError):
                policy.verify_upgrade(name, "20261096", "3.1.0", "20261095")

    def test_invalid_semver_is_rejected(self):
        for name in ("03.1.0", "3.1", "3.1.0-ci.01", "3.1.0-", "3.1.0-ci..1"):
            with self.assertRaises(ValueError):
                policy.validate_version(name, "20261095")

    def test_invalid_android_code_is_rejected(self):
        for code in ("0", "-1", "020261095", "2100000001", "3.1.0"):
            with self.assertRaises(ValueError):
                policy.validate_version("3.1.0", code)


if __name__ == "__main__":
    unittest.main()
