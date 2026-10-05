# Termux-Avic 0.118.0-avic.5

The previous full-screen bypass fixed immediate Zellij shortcuts but also disabled
predictions in every shell pane. This version adds an explicit Text/Keys control
without replacing the local unsent-token editor or guessing a remote keybinding mode.

- The rightmost first-row extra key displays the current mode. Tap Keys to use
  predictive Text in Zellij panes. Tap Text for immediate Keys input in TUIs.
- Ctrl+G in predictive Text on the alternate screen selects Keys automatically.
  All following shortcut letters stay immediate, including pauses or long sequences.
  Tap Keys to resume Text after the shortcut completes.
- Each Termux session keeps its own choice through rotation and activity recreation.
  A terminal screen change restores Text in the main screen or Keys in the alternate
  screen. Default-off and character-input precedence are unchanged.
- Custom extra-key layouts remain untouched. Add `IME` or use the terminal's
  long-press → More menu to switch. Shift, modifier popups and the top drawer row remain.
- No-personalized-learning stays requested in both modes. No terminal output/history
  is supplied to the keyboard. Disabled mode and character mode cannot be overridden
  by the quick control. Late Text and Keys callbacks are rejected after a mode/session
  change.

Termux does not know which application is inside a Zellij pane. Select Keys for Vim
command mode or another TUI requiring immediate letters, and Text for word entry.
The automatic Ctrl+G convenience is specific to that prefix; other prefixes require
selecting Keys before the sequence. There is no remote helper or Zellij configuration
change. Disable predictive input before secrets, and review corrected commands before
Enter. Only the current unsent token can be corrected.

Package `com.termuxavic`, code1004, apt-android-7 release/arm64-v8a, stable personal
signer. The custom-prefix bootstrap/packages and original Play `com.termux` are
unchanged. An ordinary same-signer update retains HOME and settings but restarts
local app sessions. No upstream PR or public signed release is created.

Exact local, CI and physical results are recorded in the private
`termux-avic-predictive-handoff/` beside the worktree. Do not treat historical fixture
or .4 hardware results as validation of this version; read the current report.
