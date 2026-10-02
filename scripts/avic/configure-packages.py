#!/usr/bin/env python3
"""Apply reproducible personal-prefix changes to the pinned community builder."""
import pathlib
import shutil
import sys

root = pathlib.Path(sys.argv[1]).resolve()
here = pathlib.Path(__file__).resolve().parent
package = "com.termuxavic"
repo = "https://dev.simmons.systems/termux-avic/apt"
prefix = f"/data/data/{package}/files/usr"

def replace(path, old, new):
    target = root / path
    text = target.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"Expected exactly one patch location in {path}")
    target.write_text(text.replace(old, new))

replace("scripts/properties.sh", 'TERMUX_APP__PACKAGE_NAME="com.termux"',
        f'TERMUX_APP__PACKAGE_NAME="{package}"')
replace("scripts/properties.sh", 'TERMUX_API_APP__PACKAGE_NAME="com.termux.api"',
        f'TERMUX_API_APP__PACKAGE_NAME="{package}.api"')
# The pinned builder's EXIT trap tries to signal itself with an empty signal,
# turning a successful archive build into exit 1. Preserve the original status.
replace("scripts/build-bootstraps.sh", '\tbuild_bootstrap_killtree "$1" $$;',
        '\t[ -z "$1" ] || build_bootstrap_killtree "$1" $$;')
# Codeberg regenerated this source archive. All 244 files were independently
# compared with the official 1.23.1 git tag (43620935a169c03ab5744d12f5917269ba92567e).
# Keep checksum validation enabled with the verified byte identity.
replace("x11-packages/foot/build.sh",
        "02072b8f0aaf26907b6b02293c875539ce52fc59079344e7cf811ab03394cfa3",
        "b3fa774983abb5f95aecca4557d146091d8666bc65fb310d4d3e38327357ade9")
# Upstream's one-line compatibility fix for the builder image's SWIG 4.4.
# https://github.com/NLnetLabs/unbound/commit/1d3d78dff5df323dfb1c729f2e22dc5bd8bc9593
shutil.copyfile(here / "libunbound-swig-4.4.patch",
                root / "packages/libunbound/libunbound-swig-4.4.patch")
# tar's existing Android patch edits Makefile.am. Regenerate with the actual
# host Automake instead of invoking the release archive's unavailable 1.16.
replace("packages/tar/build.sh", "termux_step_pre_configure() {\n",
        "termux_step_pre_configure() {\n\tautoreconf -fi\n")
replace("packages/util-linux/build.sh", "termux_step_pre_configure() {\n",
        "termux_step_pre_configure() {\n\tautoreconf -fi\n")
# GCC 15 defaults to C23, where Bash's native mkbuiltins helper's bool typedef
# is invalid. Keep that host helper on GNU C17; target Clang flags are unchanged.
replace("packages/bash/build.sh", "termux_step_pre_configure() {\n",
        "termux_step_pre_configure() {\n"
        '\tTERMUX_PKG_EXTRA_CONFIGURE_ARGS+=" CFLAGS_FOR_BUILD=-std=gnu17"\n')
replace("packages/libtirpc/build.sh", "\tautomake\n", "\tautomake --add-missing\n")
# Keep even the installed helper's example comment consistent with this fork.
# Its executable code already uses the configured TERMUX__PREFIX correctly.
exec_build = root / "packages/termux-exec/build.sh"
exec_build.write_text(exec_build.read_text() + '''
termux_step_post_get_source() {
    sed -i "s|/data/data/com.termux/files/usr|$TERMUX_PREFIX|g" \\
        app/main/scripts/termux/api/termux_exec/service/ld_preload/termux-exec-ld-preload-lib.in
}
''')
# DocBook moved its historical releases to its official archive in 2025.
# Keep all five original SHA-256 values; only the download host changes.
docbook = root / "packages/docbook-xml/build.sh"
text = docbook.read_text()
assert text.count("https://docbook.org/xml/") == 5
docbook.write_text(text.replace("https://docbook.org/xml/", "https://archive.docbook.org/xml/"))
# The former dos2unix homepage now redirects to HTML. Its official SourceForge
# archive has the identical release bytes and original checksum.
replace("packages/dos2unix/build.sh",
        "https://waterlan.home.xs4all.nl/dos2unix/dos2unix-${TERMUX_PKG_VERSION}.tar.gz",
        "https://downloads.sourceforge.net/project/dos2unix/dos2unix/${TERMUX_PKG_VERSION}/dos2unix-${TERMUX_PKG_VERSION}.tar.gz")
replace("packages/nano/build.sh",
        "https://nano-editor.org/dist/latest/nano-$TERMUX_PKG_VERSION.tar.xz",
        "https://www.nano-editor.org/dist/v8/nano-$TERMUX_PKG_VERSION.tar.xz")
# htop detects libandroid-execinfo in this builder's shared dependency prefix.
# Declare that runtime library explicitly, including on a clean phone install.
replace("packages/htop/build.sh", 'TERMUX_PKG_VERSION="3.4.1"',
        'TERMUX_PKG_VERSION="3.4.1"\nTERMUX_PKG_REVISION=1')
replace("packages/htop/build.sh", 'TERMUX_PKG_DEPENDS="libandroid-support, ncurses"',
        'TERMUX_PKG_DEPENDS="libandroid-execinfo, libandroid-support, ncurses"')
# The upstream command catalogue downloads stock-prefix repository Contents.
# Reuse its native program, with a catalogue generated from our compiled DEBs.
replace("packages/command-not-found/build.sh", "\ttermux_setup_nodejs",
        '\tsed -i "s|COMMAND ./generate-db.js|COMMAND python3 $TERMUX_PKG_BUILDER_DIR/avic-generate-commands.py|" CMakeLists.txt')
shutil.copyfile(here / "generate-commands.py",
                root / "packages/command-not-found/avic-generate-commands.py")
replace("packages/apt/build.sh", 'echo "deb https://packages-cf.termux.dev/apt/termux-main/ stable main"',
        f'echo "deb [signed-by={prefix}/share/termux-keyring/termux-avic.gpg] {repo} stable main"')
replace("packages/apt/build.sh", 'echo "# deb https://packages.termux.dev/apt/termux-main/ stable main"',
        'echo "# Only packages compiled for com.termuxavic are compatible."')
replace("packages/termux-tools/build.sh", 'TERMUX_PKG_VERSION="1.46.0+really1.45.0"',
        'TERMUX_PKG_VERSION="1.46.0+really1.45.0"\nTERMUX_PKG_REVISION=1')
replace("packages/termux-tools/build.sh", "termux_step_pre_configure() {\n",
        "termux_step_pre_configure() {\n"
        '\texport TERMUX_APP_PACKAGE TERMUX_PREFIX TERMUX_BASE_DIR TERMUX_CACHE_DIR TERMUX_ANDROID_HOME TERMUX_PACKAGE_FORMAT TERMUX_PACKAGE_MANAGER\n'
        # Android components retain com.termux Java names, while their app ID changes.
        '\tsed -i \'s|@TERMUX_APP_PACKAGE@/@TERMUX_APP_PACKAGE@\\.|@TERMUX_APP_PACKAGE@/com.termux.|g\' scripts/*.in\n'
        '\tsed -i \'s|echo "deb $MAIN stable main"|echo "deb [signed-by=@TERMUX_PREFIX@/share/termux-keyring/termux-avic.gpg] $MAIN stable main"|\' scripts/pkg.in\n')
replace("packages/termux-tools/build.sh", '\tTERMUX_PKG_CONFFILES="$(cat "$TERMUX_PKG_BUILDDIR/conffiles")"',
        '\tTERMUX_PKG_CONFFILES="$(cat "$TERMUX_PKG_BUILDDIR/conffiles")"\n'
        '\t# A fork must never switch to a stock com.termux mirror.\n'
        '\trm -rf "$TERMUX_PREFIX/etc/termux/mirrors"\n'
        '\tmkdir -p "$TERMUX_PREFIX/etc/termux/mirrors"\n'
        f'\tprintf \'WEIGHT=1\\nMAIN={repo}\\n\' > "$TERMUX_PREFIX/etc/termux/mirrors/default"\n'
        '\tln -sfn "$TERMUX_PREFIX/etc/termux/mirrors/default" "$TERMUX_PREFIX/etc/termux/chosen_mirrors"\n'
        '\tTERMUX_PKG_CONFFILES="$(printf \'%s\\n\' "$TERMUX_PKG_CONFFILES" | sed \'\\|^etc/termux/mirrors/|d\')"\n'
        '\tTERMUX_PKG_CONFFILES+=$\'\\netc/termux/mirrors/default\'\n'
        '\tprintf \'Welcome to Termux-Avic!\\nPersonal fork: packages are compiled for com.termuxavic.\\n\' > "$TERMUX_PREFIX/etc/motd"')

# Trust only the personal repository key in this package ecosystem.
(root / "packages/termux-keyring/build.sh").write_text('''TERMUX_PKG_HOMEPAGE=https://github.com/Avicennasis/termux-app
TERMUX_PKG_DESCRIPTION="Termux-Avic personal package repository signing key"
TERMUX_PKG_LICENSE="Apache-2.0"
TERMUX_PKG_MAINTAINER="Simmons Systems"
TERMUX_PKG_VERSION=3.13
TERMUX_PKG_REVISION=1
TERMUX_PKG_SKIP_SRC_EXTRACT=true
TERMUX_PKG_PLATFORM_INDEPENDENT=true
TERMUX_PKG_ESSENTIAL=true

termux_step_make_install() {
    install -Dm600 "$TERMUX_PKG_BUILDER_DIR/termux-avic.gpg" "$TERMUX_PREFIX/share/termux-keyring/termux-avic.gpg"
    mkdir -p "$TERMUX_PREFIX/etc/apt/trusted.gpg.d"
    ln -sf "$TERMUX_PREFIX/share/termux-keyring/termux-avic.gpg" "$TERMUX_PREFIX/etc/apt/trusted.gpg.d/termux-avic.gpg"
}
''')
shutil.copyfile(here / "termux-avic-repository.gpg", root / "packages/termux-keyring/termux-avic.gpg")
print(f"Configured {root} for {package}, {prefix}, {repo}")
