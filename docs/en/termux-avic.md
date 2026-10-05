# Termux-Avic personal distribution

This branch uses the actual Termux app, TermuxActivity, native PTY, sessions,
first-run bootstrap installer, settings, launcher/adaptive icons and configurable
extra keys. It contains the existing optional predictive-input implementation;
the separate IME fixture and its dashboard are not part of this app.

## Identity and coexistence

- Application ID and independent shared-user identity: `com.termuxavic`.
- HOME: `/data/data/com.termuxavic/files/home`.
- PREFIX: `/data/data/com.termuxavic/files/usr`.
- Java namespace: `com.termux` (the classes retain their original names).
- Launcher: `com.termuxavic/com.termux.app.TermuxActivity`.
- Providers: `com.termuxavic.documents` and `com.termuxavic.files`.
- External-command permission: `com.termuxavic.permission.RUN_COMMAND`.
- Intent names and preferences use `com.termuxavic`.

It has no shared UID, signing identity, private paths or plugin permissions in
common with the existing `com.termux` Play installation. Do not uninstall or
change the original app. Its HOME, packages, aliases (including `ss2`) and
credentials are independent. Saved original APKs are not HOME/data backups.

Stock Termux plugin APKs are incompatible. A plugin would need its own
`com.termuxavic.*` package, the personal shared-user identity/signing certificate,
correct component namespaces, patched paths/intents, and a compatible rebuilt
native package. No such plugin is supplied or installed by this branch.

## Predictive input

Open Settings → Termux-Avic → Terminal I/O → Predictive keyboard input.
The switch writes `terminal-ime-suggestions` in the normal `termux.properties`
file, keeping its other settings, comments and extra-key layouts. It remains
**off by default**. Returning to the terminal applies the change. Editing the
property and running `termux-reload-settings` also works.

Only the current unsent token is editable. Letters wait for a boundary or
terminal control action; earlier words cannot be retroactively corrected.
`enforce-char-based-input=true` takes precedence without being changed by the
switch. Full-screen applications start in immediate Keys mode. Tap the Text/Keys
extra key to select predictive Text in Zellij shell panes; Ctrl+G selects Keys for
modal shortcuts, and a tap returns to Text. Each session retains its choice through
rotation. Entering or leaving the alternate screen restores the safe default.
Disable predictions before secrets, private commands or applications that need
immediate characters while using the main terminal screen. A preview can appear
even in a program that disables terminal echo.

The keyboard sees enabled-mode typed input. NO_PERSONALIZED_LEARNING remains
requested, but keyboards can ignore it. Terminal output/history is never sent
to the keyboard. See [the feature guidance](terminal-ime-suggestions.md) for
the full limitations. Floating Gboard can cover extra keys at arbitrary
positions; reposition or dock it. No universal overlap-free claim is made.

## Drawer and Shift key

Settings, Keyboard and New Session share one row at the top of the session drawer,
away from the bottom extra-key row. Their normal tap and long-press actions are
retained.

The default second extra-key row includes `SHIFT`, after `ALT`. Tap Shift, then
Left Arrow to send Shift+Left (`ESC [ 1 ; 2 D`) to applications such as Codex.
The modifier is consumed by the next key; long-press Shift to lock it for repeated
modified keys, then tap it again to unlock. Other default keys and the `-`/`|`
popup remain available. A custom `extra-keys` property overrides this default;
add `'SHIFT'` to your chosen row and run `termux-reload-settings` if you use one.

## Packages and bootstrap

Termux's [maintainer documentation](https://github.com/termux/termux-packages/wiki/For-maintainers#build-bootstrap-archives)
recommends the community `infra-improvs` builder for custom-prefix bootstraps.
This distribution pins its source to
`fb44d31f3445d7efd2cee6dada47aa19401c1329` and the builder image to
`ghcr.io/termux/package-builder@sha256:44999857244619e25bc8e3427904a16d36cc0c0e087704af039e5bdf94814ac9`.
It compiles packages from source for the personal prefix, including runtime
libraries, shebangs, wrappers, package metadata and symlinks. It also keeps the Java component namespace separate from the application ID
in command wrappers, so normal file-open, reload and wake-lock commands target
the personal app. It does not patch stock precompiled binaries or reuse Play
Termux's prefix.

The verified bootstrap is tracked separately under `scripts/avic/` so CI can
build without private mesh access. The APK embeds that archive for offline
first-run installation. Its checksum and provenance are in `bootstrap.json`.
The personal signed APT repository is
`https://dev.simmons.systems/termux-avic/apt` (mesh access required). This is a
curated package set, not the entire upstream catalog. To add a package, rebuild
it and every dependency for the custom prefix, then update the signed repository. Bump the package revision for changed
published DEBs; the publisher refuses to overwrite a published file with
different bytes. APT uses hashes for stable index downloads during updates.
The upstream command-not-found program is retained, with its command catalogue
generated from the compiled personal DEBs instead of stock repository contents.
Refresh that package when extending the curated repository.

Never switch this distribution to official Termux mirrors: those binaries use
`/data/data/com.termux/files/usr` and cannot be mixed with these packages.

On this workstation, rebuild the bootstrap and additional packages with:

```sh
cd /home/avicennasis/github/ai/termux-avic
scripts/avic/build-packages.sh /tmp/termux-avic-packages-fresh
python3 scripts/avic/verify-bootstrap.py /tmp/termux-avic-packages-fresh/bootstrap-aarch64.zip > scripts/avic/bootstrap.json
cp /tmp/termux-avic-packages-fresh/bootstrap-aarch64.zip scripts/avic/bootstrap-aarch64.zip
python3 scripts/avic/publish-repository.py /tmp/termux-avic-packages-fresh/output \
  /home/avicennasis/www/termux-avic/apt \
  /home/avicennasis/.local/share/termux-avic-signing/gnupg
```

Use a fresh scratch checkout and dedicated container (set `AVIC_BUILD_CONTAINER`
to a new name if the old one mounts another checkout). The recipe uses pinned
NDK r28c for packages; APK native code uses the repository's NDK r29.
The recipe also pins a native Python 3.12.11 build interpreter, refreshes one
verified Codeberg source-archive checksum, uses DocBook's official historical
archive, regenerates tar/util-linux Automake files, selects GNU C17 for Bash's native
build helper ([GCC 15 guidance](https://gcc.gnu.org/gcc-15/porting_to.html)), and applies
[Unbound's SWIG compatibility fix](https://github.com/NLnetLabs/unbound/commit/1d3d78dff5df323dfb1c729f2e22dc5bd8bc9593).
The obsolete dos2unix download host is also replaced by the official
SourceForge release with its unchanged checksum; nano uses its official v8
archive instead of the moving latest directory. It regenerates libtirpc's missing
Automake helper, fixes the bootstrap builder's empty-signal EXIT trap, declares
htop's detected libandroid-execinfo runtime dependency, and ships the normal
single-file personal mirror configuration used by `pkg`. All source checksum checks
stay enabled. Run
`python3 scripts/avic/audit-packages.py /tmp/termux-avic-packages-fresh/output`
before publishing to reject stock private paths in runtime files and package
scripts.

Keep the immutable published DEBs when adding packages. A fresh rebuild can
change archive timestamps even for an unchanged package version; merge only
new versions into the published inventory, or deliberately bump a changed
recipe's revision. The publisher rejects silent replacement.

The pinned package source predates current upstream package updates; maintaining
this curated distribution requires deliberate package/security updates.

## Private APK builds

The stable personal APK key is outside Git at
`/home/avicennasis/.local/share/termux-avic-signing/termux-avic.p12`, alias
`termux-avic`. The mode-0600 password file is beside it as `keystore-password`.
The private repository signing key is in that directory's mode-0700 `gnupg/`.
Back up that directory securely; losing it prevents compatible future updates.
Never commit it or use the public upstream debug key for the everyday app.

```sh
cd /home/avicennasis/github/ai/termux-avic
JAVA_HOME=/tmp/termux-ime-tools/jdk-21.0.12.1+1 ./gradlew test
scripts/avic/build-apk.sh /ABSOLUTE/OUTPUT/termux-avic.apk
```

Set `TERMUX_AVIC_VERSION_CODE` above the highest installed code for future
updates, and set `TERMUX_APP_VERSION_NAME` when changing the release name. The
build checks the bootstrap checksum before compiling and verifies that the
finished APK embeds exactly that archive. A generated assembly dependency
header ensures that incremental NDK builds notice bootstrap changes.

The everyday artifact is a privately signed, non-debuggable **release** APK,
`apt-android-7`, `arm64-v8a`. This branch targets the specified phone ABI.
The existing IME tests and extra-key tests remain. CI builds an **unsigned**
release artifact; private signing stays local. Lint results must be reported
separately: the original project has four existing errors and 69 warnings.
No lint checks are weakened or called clean.

## Safe installation and behavior rollback

Verify the APK package, certificate, hash and any installed `com.termuxavic`
version before installation. Use the signed local APK, not the unsigned CI
artifact or public-key debug output.

```sh
/home/avicennasis/Android/Sdk/platform-tools/adb -s R5CXB20H1PE install --user 0 /ABSOLUTE/OUTPUT/termux-avic.apk
# Future compatible update, same private key and non-decreasing versionCode:
/home/avicennasis/Android/Sdk/platform-tools/adb -s R5CXB20H1PE install --user 0 -r /ABSOLUTE/OUTPUT/termux-avic.apk
```

Disable the normal Predictive keyboard input switch for immediate behavior
rollback, or set `terminal-ime-suggestions=false` and run
`termux-reload-settings`. Preserve the prior character-mode setting.
Removing **only** the personal app uses the command below, and deletes its own
HOME/PREFIX/preferences; back up its new data first. It does not remove Play
Termux. No original-app uninstall, downgrade or migration is authorized.

```sh
/home/avicennasis/Android/Sdk/platform-tools/adb -s R5CXB20H1PE shell pm uninstall --user 0 com.termuxavic
```

Specify user 0 explicitly so an installation does not also create a work-profile
copy. Do not use `-d` to force a downgrade. To restore older application code,
rebuild it with the same private key and a version code above the installed
version, then use the normal update command. This preserves the personal data.

The private handoff directory `../termux-avic-handoff/` holds exact signed-artifact
identity, safe commands, device audit, CI status and physical results. Automated,
old fixture and full-app physical validation must be recorded independently.
No upstream PR, maintainer contact or public release is part of this delivery.

See the [production device-test report](termux-avic-device-tests.md) for the
physical matrix, failed attempts, corrections and coverage limits.
