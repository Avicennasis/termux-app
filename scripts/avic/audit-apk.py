#!/usr/bin/env python3
"""Read-only isolation/signature preflight. This tool never installs an APK."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import tempfile
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument("apk", type=pathlib.Path)
p.add_argument("--sdk", type=pathlib.Path, default=pathlib.Path("/home/avicennasis/Android/Sdk"))
p.add_argument("--serial", default="R5CXB20H1PE")
p.add_argument("--expected-certificate", required=True)
args = p.parse_args()
sdk = args.sdk

def run(*cmd):
    return subprocess.check_output(list(map(str, cmd)), text=True)

badging = run(sdk / "build-tools/36.0.0/aapt", "dump", "badging", args.apk)
manifest = run(sdk / "build-tools/36.0.0/aapt", "dump", "xmltree", args.apk, "AndroidManifest.xml")
certs = run(sdk / "build-tools/36.0.0/apksigner", "verify", "--print-certs", args.apk)
identity = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
cert = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)", certs)
assert identity and identity[1] == "com.termuxavic", "Wrong applicationId"
assert cert == [args.expected_certificate.lower()], "Wrong private signing identity"
assert "application-label:'Termux-Avic'" in badging, "Wrong launcher label"
shared_user = re.search(r'android:sharedUserId[^\n]*="([^"]+)"', manifest)
assert shared_user and shared_user[1] == "com.termuxavic", "Wrong shared-user identity"
for attribute in ["sharedUserId", "authorities", "permission", "taskAffinity"]:
    values = re.findall(r"android:" + attribute + r"[^\n]*=\"([^\"]+)\"", manifest)
    assert all(v != "com.termux" and not v.startswith("com.termux.") for v in values), f"Stock identity in {attribute}"
assert "com.termuxavic.documents" in manifest and "com.termuxavic.files" in manifest, "Wrong providers"
assert "com.termuxavic.permission.RUN_COMMAND" in manifest, "Wrong command permission"
assert "com.termux.app.TermuxActivity" in manifest, "Real TermuxActivity missing"
assert not re.search(r"android:debuggable[^\n]*0xffffffff", manifest), "Everyday APK must be non-debuggable"
assert not any(x in manifest for x in ["ProbeActivity", "MainActivity", "termuximetest"]), "Fixture component in APK"
bootstrap = pathlib.Path(__file__).with_name("bootstrap-aarch64.zip").read_bytes()
with zipfile.ZipFile(args.apk) as archive:
    native = [name for name in archive.namelist() if name.startswith("lib/")]
    assert native and all(name.startswith("lib/arm64-v8a/") for name in native), "Unexpected ABI"
    assert bootstrap in archive.read("lib/arm64-v8a/libtermux-bootstrap.so"), "Embedded bootstrap differs"

adb = sdk / "platform-tools/adb"
def device(*cmd):
    return run(adb, "-s", args.serial, *cmd)

assert device("get-state").strip() == "device", "Device unavailable"
packages = {}
for package in ["com.termux", "com.termuxavic"]:
    listing = device("shell", "pm", "list", "packages", "-U", "--show-versioncode", package)
    packages[package] = next((line for line in listing.splitlines() if line.startswith("package:" + package + " ")), None)
if packages["com.termuxavic"]:
    old_uid = re.search(r"uid:(\d+)", packages["com.termux"] or "")
    new_uid = re.search(r"uid:(\d+)", packages["com.termuxavic"])
    assert not old_uid or old_uid[1] != new_uid[1], "Shared original app UID"
    dump = device("shell", "dumpsys", "package", "com.termuxavic")
    old_version = re.search(r"versionCode=(\d+)", dump)
    assert old_version and int(old_version[1]) <= int(identity[2]), "Would downgrade personal app"
    remote = device("shell", "pm", "path", "com.termuxavic").splitlines()[0].removeprefix("package:")
    with tempfile.TemporaryDirectory(prefix="termux-avic-certificate-") as directory:
        installed = pathlib.Path(directory) / "base.apk"
        device("pull", remote, str(installed))
        installed_certs = run(sdk / "build-tools/36.0.0/apksigner", "verify", "--print-certs", installed)
        assert re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)", installed_certs) == cert, "Existing personal app has another certificate"
print(json.dumps({"package": identity[1], "version_code": int(identity[2]), "version_name": identity[3],
                  "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(), "certificate_sha256": cert[0],
                  "variant": "release", "abi": "arm64-v8a", "shared_uid": "com.termuxavic",
                  "stock_private_prefix_reused": False, "fixture_ui": False,
                  "embedded_bootstrap_sha256": hashlib.sha256(bootstrap).hexdigest(),
                  "device_packages": packages, "install_performed": False}, indent=2))
