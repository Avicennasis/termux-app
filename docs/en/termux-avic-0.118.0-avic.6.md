# Termux-Avic 0.118.0-avic.6

This version retains the Text/Keys control introduced in .5 and fixes the context
menu's switch to Keys. A menu temporarily owns window focus; Android's input
restart must wait until the terminal regains focus, including when the selected
mode disables predictions. Otherwise the label changes while Gboard retains an
obsolete predictive connection. The queued restart now survives that focus gap.

The S24 exposed this issue during .5 testing. A regression test reproduces the
missing restart on Android 6 and Android 16 before the fix, and checks that a
fresh Keys connection sends input immediately while retired callbacks remain
invalid. Tests and final physical results are recorded in the private
`termux-avic-predictive-handoff/` beside the worktree.

Use Keys for Zellij shortcuts and immediate editor commands. Tap Keys to select
predictive Text in a shell pane or editor insert mode. Ctrl+G selects Keys
automatically in the alternate screen; tap Keys to resume Text afterward. The
master predictive-input setting remains opt-in, and the character-input override
takes precedence. The quick control does not enable either disabled mode.

Package `com.termuxavic`, version code1005, apt-android-7 release/arm64-v8a, stable
private signer. The custom-prefix bootstrap and packages, Shift key and top
drawer controls are unchanged. Original Play `com.termux` stays independent.
No upstream PR or public signed release is created.
