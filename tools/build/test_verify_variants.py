# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

import io
import contextlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from verify_variants import SENTINEL, inspect_paths, main, scan_stream, verify


class VariantArtifactVerificationTest(unittest.TestCase):
    def test_reads_compressed_bytecode_instead_of_only_zip_container(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "game.apk"
            with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("classes.dex", b"dex\n" + SENTINEL.encode())
            result = inspect_paths([path])
            self.assertTrue(any(SENTINEL in match["markers"] for match in result["artifacts"][0]["diagnosticMatches"]))
            self.assertTrue(verify(result, result, "android"))

    def test_detects_diagnostics_in_nested_distribution_jar(self):
        inner = io.BytesIO()
        with zipfile.ZipFile(inner, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("obfuscated/a.class", SENTINEL.encode())
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "distribution.zip"
            with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("app/lib/game.jar", inner.getvalue())
            result = inspect_paths([path])
            self.assertTrue(verify(result, result, "desktop"))
            self.assertTrue(any("!app/lib/game.jar!" in match["entry"] for match in result["artifacts"][0]["diagnosticMatches"]))

    def test_detects_wasm_utf16_literal_at_stream_chunk_boundary(self):
        encoded = SENTINEL.encode("utf-16-le")
        payload = b"x" * ((1 << 20) - 7) + encoded
        _, size, markers = scan_stream(io.BytesIO(payload))
        self.assertEqual(len(payload), size)
        self.assertIn(SENTINEL, markers)

    def test_refuses_absent_or_empty_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                inspect_paths([Path(directory) / "missing.jar"])
            with self.assertRaises(ValueError):
                inspect_paths([Path(directory)])

    def test_release_without_marker_does_not_validate_missing_debug_recorder(self):
        clean = {"artifacts": [{"path": "game.js", "diagnosticMatches": []}]}
        self.assertTrue(verify(clean, clean, "web"))

    def test_repeatability_detects_changed_bytes_with_clean_diagnostics_scan(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            debug, release = root / "debug.wasm", root / "release.wasm"
            debug.write_bytes(SENTINEL.encode())
            release.write_bytes(b"valid release one")
            reference, current = root / "first.json", root / "second.json"
            args = ["--platform", "web", "--debug", str(debug), "--release", str(release)]
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, main(args + ["--output", str(reference)]))
                self.assertEqual(0, main(args + ["--output", str(current), "--reference", str(reference)]))
                release.write_bytes(b"valid release two")
                self.assertEqual(1, main(args + ["--output", str(current), "--reference", str(reference)]))
            self.assertTrue(any("byte-identical" in message for message in json.loads(current.read_text())["failures"]))

    def test_source_maps_are_not_shipping_web_artifacts(self):
        debug = {"artifacts": [{"path": "game.wasm", "diagnosticMatches": [{"markers": [SENTINEL]}]}]}
        release = {"artifacts": [{"path": "game.wasm.map", "diagnosticMatches": []}]}
        self.assertTrue(verify(debug, release, "web"))


if __name__ == "__main__":
    unittest.main()
