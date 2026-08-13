# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

from __future__ import annotations

import hashlib
import json
import pathlib
import tempfile
import unittest
import zipfile

import archive_evidence


class EvidenceArchiveTest(unittest.TestCase):
    def test_archive_is_reproducible_and_manifest_covers_every_source_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            source = root / "source"
            (source / "nested").mkdir(parents=True)
            (source / "alpha.txt").write_text("alpha\n", encoding="utf-8")
            (source / "nested" / "sample.bin").write_bytes(b"\x00\x01\x02")
            first = root / "first.zip"
            second = root / "second.zip"

            first_result = archive_evidence.create_archive(source, first, "evidence")
            second_result = archive_evidence.create_archive(source, second, "evidence")

            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(first_result["archiveSha256"], second_result["archiveSha256"])
            with zipfile.ZipFile(first) as archive:
                manifest = json.loads(
                    archive.read("evidence/evidence-manifest.json").decode("utf-8"),
                )
                self.assertEqual(2, manifest["fileCount"])
                for entry in manifest["files"]:
                    data = archive.read(f"evidence/{entry['path']}")
                    self.assertEqual(len(data), entry["bytes"])
                    self.assertEqual(hashlib.sha256(data).hexdigest(), entry["sha256"])

    def test_archive_refuses_symlinks_and_overwrite(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            source = root / "source"
            source.mkdir()
            target = source / "target.txt"
            target.write_text("target", encoding="utf-8")
            output = root / "evidence.zip"
            archive_evidence.create_archive(source, output, "evidence")
            with self.assertRaises(archive_evidence.EvidenceArchiveError):
                archive_evidence.create_archive(source, output, "evidence")
            link = source / "link.txt"
            try:
                link.symlink_to(target)
            except OSError:
                self.skipTest("symlinks are unavailable")
            with self.assertRaises(archive_evidence.EvidenceArchiveError):
                archive_evidence.collect_files(source)


if __name__ == "__main__":
    unittest.main()
