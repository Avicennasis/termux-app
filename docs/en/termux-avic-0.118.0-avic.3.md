# Termux-Avic 0.118.0-avic.3

The predictive token buffer held unmodified letters after a Ctrl shortcut, so
Zellij could not receive each step of a modal key sequence. Predictions now pause
in the alternate screen used by Zellij and full-screen editors. Each key arrives
immediately there; predictions resume on exit or when switching to a main-screen
session, even if that shell produces no new output. The opt-in property remains
unchanged and default-off. The no-personalized-learning request and input-log
suppression remain active during the temporary pause.

This also pauses predictions in shells inside Zellij. Termux cannot distinguish
the multiplexer's panes or command modes. Main-screen applications requiring
immediate characters still need predictions disabled manually. Secret input is
not automatically detected; retain the existing privacy precautions.

Keyboard and New Session move above the session list, immediately below Settings.
Their stock handlers, labels, tap and long-press behavior are retained. The default
extra-key row gains SHIFT beside CTRL/ALT, using stock one-shot and long-press-lock
modifier handling. Tap Shift, then Left Arrow for Shift+Left; long-press Shift for
repeated modified keys and tap it again to unlock. Custom extra-key layouts remain
authoritative: add SHIFT to the chosen row and reload settings if one is configured.
No original keys, popups, package infrastructure or launcher behavior are removed.

The previously validated predictive Enter fix is also included: when a keyboard
sends Enter as a newline while its draft cursor is in the middle, the complete
draft reaches the terminal before Enter, with no suffix left for the next command.

## Validation

- Personal branch: **252 debug + 252 release** unit-test executions, all passing.
- Focused predictive-only branch: **247 debug + 247 release**, all passing.
- New alternate-screen regressions failed before the fix; the cached-session
  regression independently reproduced the lifecycle issue before its fix.
- Main lint still fails on **4 existing errors and 69 existing warnings**. The
  normalized issue list matches the prior personal build; checks are unchanged.
- A real TermuxActivity, native PTY and stock x86_64 bootstrap in the dedicated
  Android15/API35 VM ran Gboard against Zellij0.45.1 over an isolated temporary
  SSH connection. Before the fix Ctrl+G,n,t left one tab; the fixed sequence opened
  new tabs, including repeated sequences. An isolated keymap used that exact
  reported sequence; the user's live Zellij configuration was not changed.
- Exiting Zellij and switching between its session and an idle shell restored the
  appropriate keyboard mode. Main-screen Gboard retained `hello` locally until
  Space and then delivered exactly `hello `.
- Production extra-key taps produced exact Shift+Left, plain Left and Ctrl+Shift+Left
  bytes. Long-press Shift remained locked across repeated arrows, then unlocked.
- The drawer buttons appeared at y168–294, above the list and far from Esc. Keyboard
  show/hide and New Session were exercised. The VM's system gesture navigation
  intercepted edge swipes; the existing Ctrl+Alt+Right shortcut opened the drawer.
  Temporary three-button VM navigation also validated touch opening and both
  keyboard-toggle directions, and is restored after testing.

The VM APK is the full stock-package app with the same terminal-input source,
exact personal drawer XML and Shift default. It is not the arm64 custom-prefix
personal APK. The personal release was separately built, signed, manifest-audited
and verified to embed the exact existing custom-prefix bootstrap archive. No new
fixture testing is claimed. At initial delivery the S24 was disconnected. A later
physical .3 recheck passed the input cases but exposed clipped drawer actions with
tall Gboard in landscape. [.4](termux-avic-0.118.0-avic.4.md) fixes that layout and
documents the production rechecks and their limits. Earlier S24 results belong to
.2. Floating Gboard can still cover controls
at arbitrary positions; no universal overlap-free claim is made.

## Delivery

Package `com.termuxavic`; version code1002; release/apt-android-7; arm64-v8a.
Private download:
https://dev.simmons.systems/termux-avic/termux-avic-0.118.0-avic.3-arm64-v8a.apk

APK SHA-256:
`7090218bc977a6dc58ef30479f47bb8de30c028769cd2ad40d83744d49eab293`

Signing-certificate SHA-256:
`488412491b6dd75e8b9120f0ac0af8358610535a07ebd9a8d9dbb3e235157c01`

Use an ordinary same-signature update of the personal app; finish its running
sessions before updating. HOME/PREFIX stay at their existing personal paths.
Never uninstall or clear either app, and never install the VM/debug or unsigned
CI APK on the phone. Full safe update, source rollback and signing instructions
are in the private `termux-avic-fix-handoff/INSTALL-UPDATE-ROLLBACK.md` runbook.
The existing bootstrap/package recipes and plugin-compatibility limits remain in
[the personal distribution guide](termux-avic.md). No upstream PR, maintainer
contact or public release is authorized by this delivery.
