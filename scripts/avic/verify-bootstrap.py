#!/usr/bin/env python3
"""Reject stock-prefix runtime files and unsafe symlinks in the custom bootstrap."""
import hashlib
import json
import pathlib
import argparse
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("bootstrap", type=pathlib.Path)
parser.add_argument("--apk", type=pathlib.Path, help="Also verify the exact archive embedded in a built APK")
args = parser.parse_args()
path = args.bootstrap
failures = []
with zipfile.ZipFile(path) as archive:
    names = set(archive.namelist())
    for required in ["bin/bash", "bin/apt", "bin/dpkg", "bin/pkg", "bin/termux-reload-settings", "SYMLINKS.txt"]:
        if required not in names:
            failures.append(f"Missing {required}")
    for entry in archive.infolist():
        if entry.is_dir():
            continue
        data = archive.read(entry)
        # Documentation/tests can discuss stock paths; executable files cannot use them.
        runtime = entry.filename.startswith(("bin/", "lib/", "libexec/", "etc/"))
        if runtime and b"/data/data/com.termux/" in data:
            failures.append(f"Stock private path in runtime file: {entry.filename}")
        if entry.filename.startswith("etc/apt/") and b"https://packages" in data:
            failures.append(f"Stock package repository in {entry.filename}")
    if b"/data/data/com.termux/" in archive.read("SYMLINKS.txt"):
        failures.append("Stock-prefix bootstrap symlink")
if failures:
    raise SystemExit("\n".join(failures))
if args.apk:
    with zipfile.ZipFile(args.apk) as apk:
        if path.read_bytes() not in apk.read("lib/arm64-v8a/libtermux-bootstrap.so"):
            raise SystemExit("APK embeds a different bootstrap; rebuild the native library")
print(json.dumps({"package": "com.termuxavic", "prefix": "/data/data/com.termuxavic/files/usr",
                  "abi": "arm64-v8a", "package_variant": "apt-android-7",
                  "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                  "package_source_commit": "fb44d31f3445d7efd2cee6dada47aa19401c1329"}, indent=2))
