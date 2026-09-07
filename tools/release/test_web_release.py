# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import web_release as release


class WebReleaseTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name) / "dist"
        self.root.mkdir()
        for name, data in {"index.html": b'<script src="kinetickk.js"></script>',
                           "kinetickk.js": b'fetch("app.wasm")',
                           "app.wasm": b"\0asm\x01\0\0\0",
                           "META-INF/LICENSE": b"GPL-3.0-or-later",
                           "META-INF/NOTICE": b"Copyright 2026"}.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        self.output = Path(self.directory.name) / "release"

    def package(self):
        return release.package(self.root, self.output, "0.2.0", "a" * 40)

    def test_packages_original_bytes_with_types_and_provenance(self):
        doc = self.package()
        self.assertEqual(doc["tag"], "0.2.0")
        self.assertEqual(doc["revision"], "a" * 40)
        self.assertEqual(doc["files"]["app.wasm"]["contentType"], "application/wasm")
        for path, entry in doc["files"].items():
            data = (self.output / entry["asset"]).read_bytes()
            self.assertEqual(data, (self.root / path).read_bytes())
            self.assertEqual(release.digest(data), entry["sha256"])

    def test_rejects_missing_legal_files_and_source_maps(self):
        (self.root / "META-INF/LICENSE").unlink()
        with self.assertRaisesRegex(ValueError, "Missing browser artifact"):
            self.package()
        (self.root / "META-INF/LICENSE").write_text("GPL")
        (self.root / "app.wasm.map").write_text("{}")
        with self.assertRaisesRegex(ValueError, "source maps"):
            self.package()

    def test_rejects_unsafe_tag_and_invalid_wasm(self):
        with self.assertRaises(ValueError):
            release.validate_identity("../main", "a" * 40)
        (self.root / "app.wasm").write_text("not wasm")
        with self.assertRaisesRegex(ValueError, "Invalid Wasm"):
            self.package()

    def test_manifest_is_uploaded_last_and_partial_upload_resumes(self):
        doc = self.package()
        assets = []
        uploaded = []

        def gh(*args):
            if args[0] == "release":
                path = Path(args[3])
                data = path.read_bytes()
                uploaded.append(path.name)
                assets.append({"name": path.name, "state": "uploaded", "size": len(data),
                               "digest": f"sha256:{release.digest(data)}"})
                return b""
            return json.dumps([assets]).encode()

        def api(path):
            return assets if "/assets?" in path else {"id": 123}

        first = next(iter(doc["files"].values()))
        assets.append({"name": first["asset"], "state": "uploaded", "size": first["size"],
                       "digest": f"sha256:{first['sha256']}"})
        with patch.object(release, "api", side_effect=api), patch.object(release, "gh", side_effect=gh), \
             patch.object(release, "release_identity", return_value=("0.2.0", "a" * 40)):
            release.publish(self.output)
        self.assertEqual(uploaded[-1], release.MANIFEST)
        self.assertNotIn(first["asset"], uploaded)

    def test_failed_asset_verification_never_publishes_manifest(self):
        doc = self.package()
        entry = next(iter(doc["files"].values()))
        bad = {"name": entry["asset"], "state": "uploaded", "size": entry["size"], "digest": "wrong"}
        with patch.object(release, "api", side_effect=[{"id": 123}, [bad]]), \
             patch.object(release, "release_identity", return_value=("0.2.0", "a" * 40)), \
             patch.object(release, "gh") as gh:
            with self.assertRaisesRegex(ValueError, "different bytes"):
                release.publish(self.output)
            gh.assert_not_called()

    def test_moved_tag_is_rejected_before_any_upload(self):
        self.package()
        with patch.object(release, "api", return_value={}), \
             patch.object(release, "release_identity", return_value=("0.2.0", "b" * 40)), \
             patch.object(release, "gh") as gh:
            with self.assertRaisesRegex(ValueError, "tag moved"):
                release.publish(self.output)
            gh.assert_not_called()


if __name__ == "__main__":
    unittest.main()
