# Termux-Avic .8: keyboard access and Page mode

The default two-row toolbar now places End before Up, replaces PGUP with a
sticky PAGE toggle, and replaces PGDN with the keyboard toggle (⌨).

Tap PAGE to turn its label red and the Up/Down arrows gold. Those two buttons
then send Page Up/Page Down, including held-key repeat. Other keys do not turn
Page mode off; tap PAGE again to restore ordinary arrows. Page mode survives
activity recreation and toolbar reloads. It applies to toolbar arrow buttons,
not hardware arrows or explicit custom macros. Ctrl/Alt/Shift retain their
ordinary behavior; Shift+Page continues to scroll Termux's own scrollback.

The drawer's Keyboard button is now a full-width 56dp target below the session
list, with a 40dp inert strip beneath it. Its existing long press still toggles
the extra-key toolbar. Settings and New session remain at the top.
The button has a visible border and theme-aware text and fill in light and dark
mode. This corrects an invisible dark-mode drawer label in the first .7 device
build; .8 is the replacement delivery.

Extra keys dim and ignore input while the drawer is visible, being dragged, or
animating. An active press, popup, or repeat is canceled when the guard engages;
queued repeats and the canceled press's release cannot send terminal input
after the drawer closes. Pending modifier selections and Page mode are retained.

Custom `extra-keys` layouts are preserved. Add `PAGE` and `KEYBOARD` explicitly
to a custom layout to use these controls; existing `PGUP`/`PGDN` still work.

This update keeps the independent `com.termuxavic` package, private signer,
bootstrap, and predictive Text/Keys feature. Updating the APK restarts local
sessions; it does not clear app data.
