#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Publish a complete, immutable browser build as public GitHub release assets."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import quote

REPO = "4wl2d/KINETICKK"
MANIFEST = "kinetickk-web.json"
TAG = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
SHA = re.compile(r"[0-9a-f]{40}")
MIME = {".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8",
        ".mjs": "text/javascript; charset=utf-8", ".wasm": "application/wasm",
        ".css": "text/css; charset=utf-8", ".json": "application/json",
        ".png": "image/png", ".svg": "image/svg+xml", ".woff2": "font/woff2",
        ".ogg": "audio/ogg", ".mp3": "audio/mpeg"}


def gh(*args):
    return subprocess.check_output(["gh", *args], timeout=180)


def api(path):
    return json.loads(gh("api", f"repos/{REPO}/{path}"))


def validate_identity(tag, revision):
    if not TAG.fullmatch(tag) or not SHA.fullmatch(revision):
        raise ValueError("Expected a simple release tag (1-64 letters, digits, dots, _ or -) and full commit SHA")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def release_identity(release):
    tag = release["tag_name"]
    if release["draft"] or release["prerelease"]:
        raise ValueError("Only published stable releases can be deployed")
    revision = api(f"commits/{quote(tag, safe='')}")["sha"]
    validate_identity(tag, revision)
    return tag, revision


def verify_asset(asset, data):
    if (asset["state"] != "uploaded" or asset["size"] != len(data)
            or asset.get("digest") != f"sha256:{digest(data)}"):
        raise ValueError(f"Release asset is incomplete or has different bytes: {asset['name']}")


def resolve():
    release = api("releases/latest")
    tag, revision = release_identity(release)
    existing = next((a for a in release["assets"] if a["name"] == MANIFEST), None)
    if existing:
        document = json.loads(gh("api", f"repos/{REPO}/releases/assets/{existing['id']}",
                                 "-H", "Accept: application/octet-stream"))
        if (document.get("schemaVersion") != 1 or document.get("tag") != tag
                or document.get("revision") != revision):
            raise ValueError("Published web build differs from this tag; publish a new release instead of moving tags")
        assets = {a["name"]: a for a in release["assets"]}
        for entry in document["files"].values():
            asset = assets.get(entry["asset"], {})
            if (asset.get("state") != "uploaded" or asset.get("size") != entry["size"]
                    or asset.get("digest") != f"sha256:{entry['sha256']}"):
                raise ValueError("Published web release is missing a verified asset")
    elif release.get("immutable"):
        raise ValueError("Cannot attach a web build to an immutable release; attach assets before locking the release")
    values = {"tag": tag, "revision": revision, "needed": str(existing is None).lower()}
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.writelines(f"{key}={value}\n" for key, value in values.items())
    print(json.dumps(values))


def package(root, output, tag, revision):
    validate_identity(tag, revision)
    paths = sorted(p for p in root.rglob("*") if p.is_file())
    if not 1 <= len(paths) <= 256:
        raise ValueError("Browser build must contain 1-256 files")
    files = {}
    payloads = {}
    for path in paths:
        name = path.relative_to(root).as_posix()
        if path.is_symlink() or not path.resolve().is_relative_to(root.resolve()):
            raise ValueError(f"Symlink outside the browser distribution: {name}")
        if any(not re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9._-]*", part) for part in name.split("/")):
            raise ValueError(f"Unsupported browser asset path: {name}")
        data = path.read_bytes()
        if not data or len(data) > 25 * 1024 * 1024:
            raise ValueError(f"Browser asset must be nonempty and at most 25 MiB: {name}")
        if path.suffix == ".map" or (path.suffix in (".js", ".mjs", ".css") and b"sourceMappingURL=" in data):
            raise ValueError(f"Production source maps are forbidden: {name}")
        if path.suffix == ".wasm" and not data.startswith(b"\0asm\x01\0\0\0"):
            raise ValueError(f"Invalid Wasm asset: {name}")
        sha = digest(data)
        asset = f"kinetickk-web-{sha}{path.suffix}"
        files[name] = {"asset": asset, "sha256": sha, "size": len(data),
                       "contentType": MIME.get(path.suffix, "text/plain; charset=utf-8")}
        payloads[asset] = data
    for required in ("index.html", "kinetickk.js", "META-INF/LICENSE", "META-INF/NOTICE"):
        if required not in files:
            raise ValueError(f"Missing browser artifact: {required}")
    if not any(name.endswith(".wasm") for name in files):
        raise ValueError("Missing Wasm application")
    document = {"schemaVersion": 1, "tag": tag, "revision": revision, "files": files}
    encoded = (json.dumps(document, sort_keys=True, indent=2) + "\n").encode()
    if len(encoded) > 128 * 1024:
        raise ValueError("Web manifest exceeds 128 KiB")
    output.mkdir(parents=True, exist_ok=False)
    for name, data in payloads.items():
        (output / name).write_bytes(data)
    (output / MANIFEST).write_bytes(encoded)
    return document


def publish(directory):
    manifest_data = (directory / MANIFEST).read_bytes()
    document = json.loads(manifest_data)
    tag, revision = document["tag"], document["revision"]
    validate_identity(tag, revision)
    release = api(f"releases/tags/{quote(tag, safe='')}")
    if release_identity(release) != (tag, revision):
        raise ValueError("Release tag moved after the build; refusing to publish")
    assets = {a["name"]: a for a in api(f"releases/{release['id']}/assets?per_page=100")}
    if len(assets) >= 100:
        assets = {a["name"]: a for page in json.loads(gh("api", "--paginate", "--slurp",
                  f"repos/{REPO}/releases/{release['id']}/assets?per_page=100")) for a in page}
    # Never replace files or a committed manifest. Reruns can resume partial uploads.
    if MANIFEST in assets:
        verify_asset(assets[MANIFEST], manifest_data)
        print(f"Web release {tag} is already published")
        return
    for entry in document["files"].values():
        name = entry["asset"]
        if Path(name).name != name or not name.startswith("kinetickk-web-"):
            raise ValueError("Invalid release asset filename")
        data = (directory / name).read_bytes()
        if len(data) != entry["size"] or digest(data) != entry["sha256"]:
            raise ValueError(f"Packaged asset changed: {name}")
        if name not in assets:
            gh("release", "upload", tag, str(directory / name), "--repo", REPO)
            assets = {a["name"]: a for page in json.loads(gh("api", "--paginate", "--slurp",
                      f"repos/{REPO}/releases/{release['id']}/assets?per_page=100")) for a in page}
        verify_asset(assets[name], data)
    # The manifest is the readiness marker and must always be uploaded last.
    gh("release", "upload", tag, str(directory / MANIFEST), "--repo", REPO)
    print(f"Published complete web release {tag} ({revision})")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("resolve")
    build = sub.add_parser("package")
    build.add_argument("--root", type=Path, required=True)
    build.add_argument("--output", type=Path, required=True)
    build.add_argument("--tag", required=True)
    build.add_argument("--revision", required=True)
    upload = sub.add_parser("publish")
    upload.add_argument("--directory", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "resolve":
        resolve()
    elif args.command == "package":
        package(args.root, args.output, args.tag, args.revision)
    else:
        publish(args.directory)
