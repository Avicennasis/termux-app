---
page_ref: /docs/apps/termux/terminal-ime-suggestions.html
---

# Predictive keyboard input

`terminal-ime-suggestions` is an experimental, opt-in `termux.properties` setting
for keyboards that predict, correct, or glide-type words, such as Gboard. It
defaults to `false`. The usual character-oriented terminal input remains the
default. Actual keyboard behavior depends on its version, language, and settings.

## Enable or disable

Add this to the existing `~/.termux/termux.properties` file (or
`~/.config/termux/termux.properties` if that is the file you use):

```properties
# The keyboard can see input, including passwords. Read the privacy notes below.
terminal-ime-suggestions = true
enforce-char-based-input = false
```

Run `termux-reload-settings` to apply the change. This restarts the keyboard's
input connection, not the activity or shell sessions. Pending input is finalized
once before the mode changes. If the keyboard does not refresh immediately,
hide and show it. Set `terminal-ime-suggestions = false` (or remove the property)
and reload to restore ordinary input.

`enforce-char-based-input = true` always takes precedence and keeps its existing
Samsung keyboard workaround. To use predictions it must be `false`, its default.
The predictive property does not silently redefine that setting.

The extra-keys toolbar remains available with its existing Ctrl, Alt, Esc, Tab,
arrows, custom layouts, multiple rows, popups, macros, and keyboard toggle. This
mode does not switch to the toolbar's separate text-input page. Keep Gboard's
own “Show suggestion strip” setting enabled.

## How input behaves

The terminal screen is not an editable Android document. Predictive mode gives
the keyboard a small, local draft of **unsent input only**. Its text and cursor
are previewed, underlined, inside the terminal view. Composing changes,
autocorrection, selection, and replacement affect this draft, not text already
processed by the shell. The default input connection is unchanged; composing
support depends on the keyboard, which may use an ASCII fallback in ordinary
mode. Language-specific prediction and replacement require testing with the
actual keyboard, version, and language.

A composing token reaches the terminal when the IME commits it and a word boundary
is available, or a terminal control is used. Non-composing character commits are
also held until a boundary so keyboards that replace the current token can do so
safely. Finishing composition removes its pre-edit state while leaving the token
editable. Emoji, their modifiers and joiners stay in that draft, allowing Unicode
Backspace without trying to undo PTY input. Spaces, punctuation and Enter release
committed input through that boundary. IME batches are processed together so a
finish/replacement sequence does not send the old spelling first. Enter finalizes
the entire draft before taking its terminal action, including when a keyboard
sends Enter as a newline character while its local cursor is inside the draft.
Termux's newline to carriage-return behavior is retained.

Backspace edits the local draft first; with an empty draft an ordinary single
Backspace is sent to the terminal. Selection and forward deletion inside the
draft are local. Multi-character deletion and replacement of previously sent
text are deliberately rejected: terminal applications need not treat backspaces
as reversible edits. Corrections to earlier words and arbitrary movement through
the terminal's command line are consequently unsupported. Surrounding-text
queries expose only the local draft, without shell history or terminal output.

The IME may move its pre-edit cursor inside the draft. Termux extra-key and
hardware navigation/control keys finalize the draft before taking their usual
terminal action, then reset keyboard state. Modifier-only keys do not prematurely
finish composition. A session switch, focus loss, activity destruction, or mode
change finalizes to the old session and retires its input connection; late IME
callbacks cannot type into the next session. Background output only redraws the
preview and never becomes IME text.

Predictions automatically pause while the terminal's alternate screen is active,
so full-screen applications receive ordinary letters immediately. This supports
multi-step key sequences in Zellij and command modes in editors. Entering that
screen finalizes the pending draft once; leaving restores predictions if the
property is still enabled. The property itself is not changed. Predictions also
pause in shells inside Zellij: Termux sees the multiplexer's screen, not its
individual panes or keybinding modes.

The draft is limited to 4096 UTF-16 units. Oversized composition updates are
rejected atomically; oversized ordinary commits are finalized rather than kept
for later correction. Long previews wrap inside the terminal and are clipped
to its visible area; they may not be fully visible near its bottom edge.

## Privacy and limitations

**The selected keyboard sees predictive-mode input, including commands and
secrets.** Termux cannot reliably detect password prompts in a shell, SSH session,
or TUI. The local preview may show input even when the terminal application has
disabled echo. Disable this mode before entering passwords, tokens, API keys, or
other private text. Review keyboard autocorrections before executing a command.

Termux requests `IME_FLAG_NO_PERSONALIZED_LEARNING`, along with no fullscreen or
extracted-text UI. This asks the keyboard not to update its learned vocabulary;
it is not a confidentiality guarantee. The keyboard may ignore the request and
still has access to every character you type. This feature does not collect
history, read terminal contents into the IME, provide network suggestions, or
log composing/committed text. Terminal key diagnostics are suppressed while this
mode is active. Other terminal debug logs can still contain unrelated sensitive
information; do not collect secret sessions.

Whether Gboard offers useful predictions in this mode with the learning flag
must be checked on a physical device. No setting silently enables personalized
learning. Predictions, glide input, voice input, and language-specific replacement
protocols are compatibility expectations to verify, not guarantees for every IME.

Full-screen applications using the alternate screen automatically use ordinary
input. Disable predictions for games or other programs that require immediate
letters while using the main terminal screen. Extra-key terminal controls remain
immediate after finalizing the draft, but buffering words changes the timing of
ordinary letters. Editing already echoed words, reconverting earlier CJK text, fullscreen
IME editors, handwriting cursor geometry, and correction of text already sent to
the PTY are outside the local draft's contract.

Android's IME resize/inset handling and Termux's existing toolbar overlap
workaround are preserved. Physical testing must check the suggestion bar and
extra keys together in portrait, landscape, split-screen, floating-keyboard, and
one-handed layouts; automated tests cannot establish that a particular keyboard
reports correct insets.

A floating keyboard controls its own window position and may cover terminal
controls, including the extra-keys toolbar, in either input mode. Move it clear
of those controls or return to the keyboard's docked layout. This mode cannot
reserve screen space for a floating window that reports no IME resize inset.

A commented configuration sample is available in
[terminal-ime-suggestions.properties](terminal-ime-suggestions.properties).

## Contributor checks

Run `./gradlew test` with a complete JDK 21 or newer: the Robolectric Android 16
replays require Java 21. These exercise Android 6 and 16 input connections,
API 34 replacement, UTF-8 terminal output, and the extra-key macro path without
starting a PTY. The app module's existing test runner separately checks Android 5
compatibility, since the Android 16 runner no longer supports that SDK.
`./gradlew lint` and `./gradlew assembleDebug` remain the normal
lint and debug build tasks; the APK build continues to support JDK 17. These
checks supplement physical IME testing and do not prove Gboard compatibility.
