#!/usr/bin/env bash
# A native build interpreter matching the pinned target Python's minor version.
# Portable community build, with its GitHub release asset digest pinned.
set -euo pipefail
AVIC_PYTHON=/home/builder/lib/avic-build-python-3.12.11
AVIC_ARCHIVE=/home/builder/lib/avic-build-python-3.12.11.tar.gz
if [[ ! -x "$AVIC_PYTHON/bin/python3.12" ]]; then
    curl -fL 'https://github.com/astral-sh/python-build-standalone/releases/download/20250702/cpython-3.12.11%2B20250702-x86_64-unknown-linux-gnu-install_only_stripped.tar.gz' -o "$AVIC_ARCHIVE"
    printf '%s  %s\n' 2eed351d3f6e99b4b6d1fe1f4202407fe041d799585dffdf6d93c49d1f899e37 "$AVIC_ARCHIVE" | sha256sum -c -
    mkdir -p "$AVIC_PYTHON"
    tar -xzf "$AVIC_ARCHIVE" --strip-components=1 -C "$AVIC_PYTHON"
fi
# Invoke the actual portable binary so venv records its real stdlib directory.
# A direct /usr/bin symlink makes CPython's venv home point at the wrong prefix.
printf '#!/bin/sh\nexec "%s/bin/python3.12" "$@"\n' "$AVIC_PYTHON" > /tmp/avic-python3.12-wrapper
if [[ -L /usr/bin/python3.12 ]]; then sudo -n unlink /usr/bin/python3.12; fi
sudo -n install -m755 /tmp/avic-python3.12-wrapper /usr/bin/python3.12
sudo -n ln -sf /usr/bin/python3.12 /usr/local/bin/python3.12
python3.12 --version
