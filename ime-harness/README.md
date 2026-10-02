# Isolated physical IME fixture

Fork-only test tooling, separate from the upstream feature branch. Builds the exact
terminal-view, terminal-emulator and extra-key library sources in this checkout.
It does not rename the Termux application or modify TermuxConstants/PREFIX.

Application ID: `systems.simmons.termuximetest`; no shared UID, network/storage
permissions, Termux services, command API, or bootstrap. All files and shell
processes belong to this app's own UID. Android's `/system/bin/sh` runs in a real
PTY through the existing Termux JNI library. Only synthetic PASS/FAIL/length
probe results are persisted; input-content logging is disabled.

Use `./gradlew :ime-harness:assembleDebug` with the repository-supported JDK 17.
Predict and Char buttons exercise the real TerminalView mode callbacks (Char
takes precedence); Rows enables multirow, popup and macro keys; Probe compares
received lines against named synthetic fixtures; Shell starts Android mksh.
New/Next exercise session changes. The initial mode is off. Rotation retains
sessions while recreating the view; the real input connection is retired.

Probe also has deterministic typo-rejection, two-space, numeric, long-word,
accent/emoji, custom-key ordering and bounded background-output fixtures. A raw
38-byte fixture checks Ctrl+C/D/L/Z, Alt+B, Esc, Tab, arrows, Home/End, popup End,
the C-c macro, custom text, Backspace and Enter without running those controls as
commands. Only PASS/FAIL/length results are stored. For reproducible test setup,
`adb shell am start -n systems.simmons.termuximetest/.MainActivity --ei fixture N`
selects one of those fixed fixtures; actual Gboard tests must tap/swipe the IME,
not use `adb input text`. Invalid fixture numbers select the first fixture.

The test buttons are hidden in landscape so the real terminal/extra-key/IME
layout has space comparable to TermuxActivity. They remain available in portrait.
The fixture keeps the screen awake only while it is foreground; it does not
change the phone's lock/display settings. APK CI runs only on the fixture branch.

This can check physical Gboard/IME protocols, Unicode, PTY input and the real
extra-key controls. It does **not** validate full TermuxActivity insets/settings,
bootstrap/packages, Bash, vim/nano, plugin compatibility or all production
lifecycle behavior. Full-Termux hardware certification remains pending.

The installed Google Play `com.termux` is untouched. Do not install the full
feature APK over it: signing certificates differ. This fixture is not a renamed
parallel Termux distribution and has no Termux package ecosystem.
