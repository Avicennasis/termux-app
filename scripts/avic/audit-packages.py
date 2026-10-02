#!/usr/bin/env python3
"""Audit the custom Debian packages and produce a non-sensitive inventory."""
import hashlib
import io
import json
import pathlib
import subprocess
import sys
import tarfile

root = pathlib.Path(sys.argv[1])
prefix = "data/data/com.termuxavic/files/usr/"
items = []
failures = []
for deb in sorted(root.glob("*.deb")):
    metadata = subprocess.check_output(["dpkg-deb", "-f", str(deb)], text=True)
    fields = dict(line.split(": ", 1) for line in metadata.splitlines()
                  if ": " in line and not line.startswith(" "))
    if fields["Architecture"] not in ["aarch64", "all"]:
        failures.append(f"Unexpected architecture: {deb.name}")
    for flag, controls in [("--fsys-tarfile", False), ("--ctrl-tarfile", True)]:
        data = subprocess.check_output(["dpkg-deb", flag, str(deb)])
        with tarfile.open(fileobj=io.BytesIO(data)) as archive:
            for entry in archive:
                name = entry.name.removeprefix("./")
                if name.startswith("data/data/com.termux/"):
                    failures.append(f"Stock private path: {deb.name}:{name}")
                if "/data/data/com.termux/" in entry.linkname:
                    failures.append(f"Stock symlink: {deb.name}:{name}")
                runtime = controls or (name.startswith(prefix) and
                    name[len(prefix):].startswith(("bin/", "lib/", "libexec/", "etc/")))
                if runtime and entry.isfile():
                    if b"/data/data/com.termux/" in archive.extractfile(entry).read():
                        failures.append(f"Stock prefix in runtime: {deb.name}:{name}")
    items.append({"file": deb.name, "package": fields["Package"],
                  "version": fields["Version"], "architecture": fields["Architecture"],
                  "depends": fields.get("Depends", ""),
                  "sha256": hashlib.sha256(deb.read_bytes()).hexdigest()})
if failures:
    raise SystemExit("\n".join(failures))
print(json.dumps({"package_id": "com.termuxavic", "prefix": "/" + prefix.rstrip("/"),
                  "packages": items}, indent=2))
