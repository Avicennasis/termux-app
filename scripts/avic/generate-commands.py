#!/usr/bin/env python3
"""Feed upstream command-not-found only commands from the compiled personal DEBs."""
import io
import json
import os
import pathlib
import subprocess
import tarfile

root = pathlib.Path(os.environ["TERMUX_SCRIPTDIR"])
arch = os.environ["TERMUX_ARCH"]
prefix = os.environ["TERMUX_PREFIX"].lstrip("/") + "/bin/"
commands = {}
for deb in sorted((root / "output").glob("*.deb")):
    fields = subprocess.check_output(["dpkg-deb", "-f", str(deb), "Package", "Architecture"], text=True)
    metadata = dict(line.split(": ", 1) for line in fields.splitlines())
    if metadata["Architecture"] not in [arch, "all"]:
        continue
    data = subprocess.check_output(["dpkg-deb", "--fsys-tarfile", str(deb)])
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        for entry in archive:
            name = entry.name.removeprefix("./")
            if name.startswith(prefix) and not entry.isdir():
                command = name[len(prefix):]
                if "/" not in command:
                    commands.setdefault(metadata["Package"], set()).add(command)
lines = []
for package, names in sorted(commands.items()):
    lines.append(json.dumps(package) + ",")
    lines.extend(json.dumps(" " + name) + "," for name in sorted(names))
pathlib.Path(f"commands-{arch}-termux-main.h").write_text("\n".join(lines) + "\n")
for repo in ["root", "x11"]:
    pathlib.Path(f"commands-{arch}-termux-{repo}.h").write_text("// No packages from this repository in the personal distribution.\n")
print(f"Generated personal command catalogue from {len(commands)} compiled packages.")
