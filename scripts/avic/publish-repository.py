#!/usr/bin/env python3
"""Create and sign a small personal-prefix APT repository using standard Debian tools."""
import hashlib
import gzip
import pathlib
import os
import shutil
import subprocess
import sys
from datetime import datetime, timezone

source = pathlib.Path(sys.argv[1]).resolve()
dest = pathlib.Path(sys.argv[2]).resolve()
keydir = pathlib.Path(sys.argv[3]).resolve()
pool = dest / "pool/main"
binary = dest / "dists/stable/main/binary-aarch64"
pool.mkdir(parents=True, exist_ok=True)
binary.mkdir(parents=True, exist_ok=True)
for deb in sorted(source.glob("*.deb")):
    target = pool / deb.name
    if target.exists() and target.read_bytes() != deb.read_bytes():
        raise SystemExit(f"Bump the package revision before replacing published {deb.name}")
    if not target.exists():
        temporary = target.with_suffix(".new")
        shutil.copy2(deb, temporary)
        os.replace(temporary, target)
packages = subprocess.check_output(["dpkg-scanpackages", "--multiversion", "pool", "/dev/null"], cwd=dest)
(binary / "Packages").write_bytes(packages)
(binary / "Packages.gz").write_bytes(gzip.compress(packages, mtime=0))
by_hash = binary / "by-hash/SHA256"
by_hash.mkdir(parents=True, exist_ok=True)
for file in [binary / "Packages", binary / "Packages.gz"]:
    target = by_hash / hashlib.sha256(file.read_bytes()).hexdigest()
    if not target.exists():
        shutil.copy2(file, target)
release = "Origin: Termux-Avic\nLabel: Termux-Avic\nSuite: stable\nCodename: stable\nArchitectures: aarch64\nComponents: main\nAcquire-By-Hash: yes\nDescription: Packages compiled for /data/data/com.termuxavic/files/usr\n"
release += "Date: " + datetime.now(timezone.utc).strftime("%a, %d %b %Y %H:%M:%S +0000") + "\nSHA256:\n"
for file in [binary / "Packages", binary / "Packages.gz"]:
    release += f" {hashlib.sha256(file.read_bytes()).hexdigest()} {file.stat().st_size} {file.relative_to(dest / 'dists/stable')}\n"
release_path = dest / "dists/stable/Release"
release_path.write_text(release)
for options, target in [(["--clearsign"], "InRelease"), (["--detach-sign", "--armor"], "Release.gpg")]:
    temporary = release_path.parent / (target + ".new")
    subprocess.run(["gpg", "--homedir", str(keydir), "--batch", "--yes", "--local-user", "Termux-Avic packages", "--output", str(temporary), *options, str(release_path)], check=True)
    os.replace(temporary, release_path.parent / target)
print(f"Published {len(list(pool.glob('*.deb')))} personal-prefix packages to {dest}")
