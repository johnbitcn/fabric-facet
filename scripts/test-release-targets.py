#!/usr/bin/env python3
import pathlib
import runpy
import tempfile
import unittest

load_targets = runpy.run_path(str(pathlib.Path(__file__).with_name("release-targets.py")))["load_targets"]


class ReleaseTargetsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        root = pathlib.Path(self.directory.name)
        self.settings = root / "settings.gradle"
        self.settings.write_text("include 'versions:fabric-26.2'\n"
                                 "include 'versions:neoforge-26.1'\n"
                                 "include 'versions:neoforge-26.1.2'\n"
                                 "\tinclude 'versions:fabric-26.4-snapshot-6'\n")
        self.manifest = root / "v9.0.0.targets"

    def select(self, content):
        self.manifest.write_text(content)
        return load_targets(self.manifest, self.settings)

    def test_only_selected_target_is_returned(self):
        self.assertEqual(["fabric-26.2"], self.select("fabric-26.2\n"))

    def test_comments_and_multiple_targets(self):
        self.assertEqual(["fabric-26.2", "neoforge-26.1.2"],
                         self.select("# Changed targets\n\nfabric-26.2 # rendering\n neoforge-26.1.2 \n"))

    def test_versions_are_exact_not_prefix_matches(self):
        self.assertEqual(["neoforge-26.1.2"], self.select("neoforge-26.1.2\n"))

    def test_missing_manifest_fails(self):
        with self.assertRaises(FileNotFoundError):
            load_targets(self.manifest, self.settings)

    def test_empty_manifest_fails(self):
        with self.assertRaisesRegex(ValueError, "No release targets"):
            self.select("# None\n")

    def test_unknown_target_fails(self):
        with self.assertRaisesRegex(ValueError, "Unknown"):
            self.select("../fabric-26.2\n")

    def test_duplicates_fail(self):
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self.select("fabric-26.2\nfabric-26.2\n")

    def test_snapshot_cannot_enter_stable_release(self):
        with self.assertRaisesRegex(ValueError, "preview workflow"):
            self.select("fabric-26.4-snapshot-6\n")


if __name__ == "__main__":
    unittest.main()
