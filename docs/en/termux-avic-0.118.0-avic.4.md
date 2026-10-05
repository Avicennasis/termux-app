# Termux-Avic 0.118.0-avic.4

S24 testing of .3 exposed a drawer layout problem in landscape: the tall docked
Gboard keyboard left a 153-pixel terminal/drawer area, while separate Settings
and action rows needed 231 pixels. Only 48 pixels of the action buttons remained
visible, clipping their labels. This release puts the existing Settings, Keyboard
and New Session controls in one top row. New Session wraps onto two lines on the
tested phone; the complete buttons fit above the extra keys. When the landscape
session list has little space, hide the keyboard using the now-reachable control.
Existing IDs, handlers, long-press behavior and session-list scrolling remain.

Predictive-input code is unchanged from .3. When enabled, predictions pause in
Zellij and other alternate-screen applications, so each modal key arrives
immediately. They resume in the main screen and when selecting an idle main-screen
session. Predictions remain opt-in and default-off. This also pauses predictions
in Zellij shell panes; Termux cannot distinguish their command modes. Main-screen
programs needing immediate characters and secret input still require disabling
predictions manually. The stock character-based override keeps precedence.

Tap the default SHIFT extra key, then Left for Shift+Left. Hold SHIFT to lock it
across repeated keys and tap again to unlock. Custom extra-key layouts still
override the default; add SHIFT to a custom row and reload settings if needed.

## Validation layers

- Final .4 automated tests: **252 debug + 252 release**, zero failures, errors or
  skips. The focused predictive-only branch retains **247 debug + 247 release**.
- Main lint fails with **4 existing errors and 69 existing warnings**. Normalized
  issues match the prior personal baseline exactly. Checks have not been weakened.
- The earlier Android15/API35 VM validated the predictive fixes, real PTY and
  Zellij0.45.1 with Gboard14.2.09. Its drawer used the .3 layout; no .4 VM UI
  validation is claimed. The earlier system-shell fixture remains separate.
- Physical testing used the privately signed, full arm64 personal APK on Galaxy
  S24 Ultra SM-S928U1, Android16/API36, One UI8.5, Gboard
  18.3.2.977415014-release-arm64-v8a on 2026-10-04 (UTC 2026-10-05).
- The broad input recheck ran .3. The final .4 repeats the affected drawer controls,
  Shift bytes and Zellij/session behavior. Its input sources and embedded bootstrap
  archive are unchanged from .3. These are production TermuxActivity/Bash/PTY
  tests, not a renamed fixture or Android's system shell.

| Physical case | Result and scope |
| --- | --- |
| Safe update | .2 → .3 → .4, same private signer and personal UID/data directory; original Play APK, version, UID and data directory unchanged |
| Existing environment | Real Bash5.3.3 and personal HOME/PREFIX; Bash, APT, dpkg, Vim, Nano, Python and OpenSSH execute; Python ssl/readline imports pass |
| User files | Existing .bashrc and .ssh directory remain present; no contents collected |
| Zellij | Actual Gboard Ctrl+G,n,t opens successive tabs in a disposable Zellij0.45.1 session over isolated ADB-reverse SSH; no live user keymap or remote session changed |
| Mode transitions | Alternate screen uses immediate input while retaining requested no-personalized-learning flags; exit and cached Zellij/idle-shell switches restore the correct mode without new output |
| Shift | Exact Shift+Left, plain Left and Ctrl+Shift+Left bytes; long-press lock survives two arrows and unlocks to ordinary Left |
| Suggestions/correction | hello candidate selection, wprld → world autocorrection and literal rejection pass after leaving Zellij |
| Token boundary | Main-screen hello delivers zero bytes before Space, then exactly hello plus Space |
| Text/Unicode | hello world123, glide hello world, ordinary Backspace, café and emoji insertion/deletion/reinsertion pass with real Gboard controls |
| Editors/output | Vim and Nano receive immediate text, save hello and restore predictions on exit; 50 synthetic output lines do not corrupt composition |
| Settings/modes | Normal disable/enable control works; disabled mode and character override deliver h immediately; original property file restored byte for byte |
| Rotation | Portrait hel completes to hello after landscape rotation; docked extra keys, including SHIFT, remain reachable |
| Final drawer | Full labels/buttons visible in the previously clipped landscape layout; Keyboard, New Session and Settings exercised |

Only synthetic words, byte counts, pass/fail metadata and non-sensitive device
identity were retained. ADB text injection prepared commands and saved/quitted
Vim; it is not evidence of Gboard composition. IME claims use visible key taps,
candidate selection, glide, accent/emoji controls and extra keys.

The journal retains failed and inconclusive attempts: an incorrect raw-capture
length/process group, inaccessible Gboard surface text selectors, an uppercase
ENABLE dialog selector and an immediate character-mode attempt before keyboard
reattachment was awaited. Corrected/stable retries passed. The .3 landscape
clipping was a real product defect and triggered this release.

Fresh bootstrap was not repeated over the user's working PREFIX. Its exact archive
matches the previously bootstrapped .2 release. The broad fresh-bootstrap/package,
extra-key popup/macro, history/completion, split-screen, one-handed and floating
results remain historical .2 coverage. These layouts were not all rerun here.
Floating Gboard can still cover controls; dock or reposition it. No universal
overlap-free claim, live-account/Codex validation, physical hardware-keyboard,
CJK, voice or exhaustive grapheme-cluster coverage is claimed.

## Artifact and maintenance

- Package: `com.termuxavic`; version `0.118.0-avic.4`, code `1003`.
- Variant: `apt-android-7-release`; ABI: `arm64-v8a`.
- [Private APK download](https://dev.simmons.systems/termux-avic/termux-avic-0.118.0-avic.4-arm64-v8a.apk).
- APK SHA-256: `3e5d1fca3bd4a33acd273ad1642848a858133e43a4715a4c02215a4436259b66`.
- Certificate SHA-256: `488412491b6dd75e8b9120f0ac0af8358610535a07ebd9a8d9dbb3e235157c01`.
- Bootstrap SHA-256: `e2bce916f9ccca9e304b3f8982b9b8ce948fd37095680b1e08d901faf107f7b5`.

Use a normal same-signer update with `adb -s R5CXB20H1PE install --user 0 -r APK`.
Never uninstall/clear either app, install the stock-package VM/debug APK on the
phone, or use a forced downgrade. Local sessions restart on an app update.
Rollback requires rebuilding the desired source with the same private signer and
a version code higher than the installed code. The private handoff contains exact
commands, artifact/device audits, signing location, CI status and cleanup evidence.

[The distribution guide](termux-avic.md) retains custom-prefix bootstrap/package
build instructions, curated-repository limits and stock-plugin incompatibility.
The focused predictive-only branch contains no personal branding, drawer, Shift
default or package infrastructure changes. No upstream PR, maintainer contact or
public signed release has been authorized.
