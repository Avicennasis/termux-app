#!/usr/bin/env bash
# Use the custom-bootstrap builder explicitly recommended by Termux's maintainer wiki.
set -euo pipefail
AVIC_SCRIPTS=$(cd -- "$(dirname -- "$0")" && pwd)
AVIC_PACKAGES=${1:?Pass a fresh scratch checkout path}
AVIC_BUILDER_COMMIT=fb44d31f3445d7efd2cee6dada47aa19401c1329
if [[ ! -d "$AVIC_PACKAGES/.git" ]]; then
    [[ ! -e "$AVIC_PACKAGES" ]] || { echo "Scratch path already exists" >&2; exit 1; }
    git init "$AVIC_PACKAGES"
    git -C "$AVIC_PACKAGES" remote add origin https://github.com/agnostic-apollo/termux-packages.git
    git -C "$AVIC_PACKAGES" fetch --depth 1 origin "$AVIC_BUILDER_COMMIT"
    git -C "$AVIC_PACKAGES" checkout --detach "$AVIC_BUILDER_COMMIT"
fi
[[ $(git -C "$AVIC_PACKAGES" rev-parse HEAD) == "$AVIC_BUILDER_COMMIT" ]] || {
    echo "Checkout must be at pinned builder $AVIC_BUILDER_COMMIT" >&2; exit 1;
}
python3 "$AVIC_SCRIPTS/configure-packages.py" "$AVIC_PACKAGES"
cd "$AVIC_PACKAGES"
export CONTAINER_NAME=${AVIC_BUILD_CONTAINER:-termux-avic-package-builder}
if docker container inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
    AVIC_MOUNT=$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/home/builder/termux-packages"}}{{.Source}}{{end}}{{end}}' "$CONTAINER_NAME")
    [[ "$AVIC_MOUNT" == "$(pwd -P)" ]] || {
        echo "Container mounts another checkout; choose a fresh AVIC_BUILD_CONTAINER name." >&2
        exit 1
    }
fi
export TERMUX_BUILDER_IMAGE_NAME=ghcr.io/termux/package-builder@sha256:44999857244619e25bc8e3427904a16d36cc0c0e087704af039e5bdf94814ac9
# The pinned source uses NDK r28c; the image also contains a newer NDK.
./scripts/run-docker.sh ./scripts/setup-android-sdk.sh
docker cp "$AVIC_SCRIPTS/setup-host-python.sh" "$CONTAINER_NAME:/tmp/avic-setup-host-python.sh"
./scripts/run-docker.sh bash /tmp/avic-setup-host-python.sh
./scripts/run-docker.sh env TERMUX_PKG_MAKE_PROCESSES=8 ./scripts/build-bootstraps.sh --architectures aarch64 --no-build-unneeded-subpackages
# Editors are installable through the personal APT repository, not fixture substitutes.
./scripts/run-docker.sh env TERMUX_PKG_MAKE_PROCESSES=8 ./build-package.sh -a aarch64 --no-build-unneeded-subpackages nano
./scripts/run-docker.sh env TERMUX_PKG_MAKE_PROCESSES=8 ./build-package.sh -a aarch64 --no-build-unneeded-subpackages vim
./scripts/run-docker.sh env TERMUX_PKG_MAKE_PROCESSES=8 ./build-package.sh -a aarch64 --no-build-unneeded-subpackages htop
./scripts/run-docker.sh env TERMUX_PKG_MAKE_PROCESSES=8 ./build-package.sh -a aarch64 --no-build-unneeded-subpackages openssh
./scripts/run-docker.sh env TERMUX_PKG_MAKE_PROCESSES=8 ./build-package.sh -a aarch64 --no-build-unneeded-subpackages git
./scripts/run-docker.sh env TERMUX_PKG_MAKE_PROCESSES=8 ./build-package.sh -a aarch64 --no-build-unneeded-subpackages python
