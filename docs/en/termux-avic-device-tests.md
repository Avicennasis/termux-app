# Termux-Avic production validation — 2026-10-02

These tests ran the actual Termux app, installer, Bash, PTY, sessions and package
environment on the Galaxy S24 Ultra SM-S928U1, Android 16/API 36, One UI 8.5.
Gboard was 18.3.2.977415014-release-arm64-v8a. Visible Gboard taps, glide gestures
and production extra keys were driven over ADB. ADB text injection was used
only for synthetic shell setup; it is not evidence of IME typing.

## Validation layers

- **Automated:** 236 debug and 236 release unit tests pass, with zero failures,
  errors or skips. Existing IME, terminal and extra-key tests remain, with five
  additional personal-app identity/settings tests.
- **Lint:** exits 1 with the existing four errors and 69 warnings. Comparison
  found no added findings. It is not clean and checks were not weakened.
- **Earlier fixture:** commit 8632d731dcd8ad13a6dbc27db1468812da3386b2 provides
  isolated Gboard/control coverage using Android's system shell. It does not
  validate a complete Termux installation.
- **Production:** privately signed full-app release code 1000 exercised the
  broad IME/control/session matrix. Final 0.118.0-avic.2/code 1001 repeated a
  fresh PREFIX bootstrap, package installation, native execution, Settings and
  modes, typing/glide/Backspace, restart and layout checks. Predictive-input
  Java code is unchanged between these builds.

## Physical matrix

| Case | Result and scope |
| --- | --- |
| Launcher/Activity | Termux-Avic label, stock launcher/adaptive icons and real TermuxActivity; no fixture/dashboard components in the APK |
| Bootstrap | Embedded archive installs real Bash 5.3.3 offline; final fresh PREFIX completes the bootstrap second stage |
| Environment/isolation | Personal HOME/PREFIX, UID 10277 distinct from Play UID 10608; personal shell cannot read original private HOME |
| Package manager | Final fresh pkg update/install succeeds against the signed personal repository without manual mirror repair |
| Native programs | Bash, APT, dpkg, nano, Vim, htop, OpenSSH, Git and Python run from personal PREFIX; Python ssl/readline imports pass |
| Default/Settings switch | Off on first install; normal Terminal I/O control enables/disables the property, explains privacy and reloads on return |
| Suggestions | Selected hello suggestion replaces hel correctly in the real shell |
| Autocorrection/rejection | wprld corrects to world; rejecting the candidate preserves wprld |
| Glide | hello world reaches the shell, including a final-build repeat |
| Ordinary input | Letters, numbers, Enter, two spaces, punctuation, capitalization and a 45-character word pass |
| Unicode/emoji | café and 😀 pass; emoji deletion leaves no surrogate fragment |
| Backspace | Ordinary, held/repeated and emoji deletion pass; final build repeats ordinary deletion |
| Extra keys/popups | Exact 38-byte replay covers Ctrl-C/D/L/Z, Alt-B, Esc, Tab, arrows, Home/End, popup, macro, literal, DEL and Enter |
| Configurable layout | Temporary third-row macro/literal/BKSP/ENTER/keyboard layout works; stock two rows restored |
| Bash history/completion | Up/Enter recalls synthetic history; unique Tab completion works |
| Ctrl/Alt shell behavior | Ctrl-C interrupts sleep, Ctrl-Z stops a job, Ctrl-L preserves command, Alt-B/Ctrl-K edits correctly |
| Sessions | Pending input finishes in its original session; new session receives only its own word; return has no cross-session leakage |
| Output while composing | 50 bounded synthetic output lines do not corrupt the pending word |
| Echo disabled | Synthetic hello arrives correctly, but local preview remains visible: an explicit privacy limitation |
| Settings reload | Pending word finalizes safely; disabling and character override take effect |
| Character override | enforce-char-based-input=true wins over configured suggestions; immediate h visible without predictive connection |
| Vim | Predictive insert flushes on Esc and saves; final disabled mode enters h immediately, navigates and saves hello |
| Nano | Predictive input flushes before Ctrl-O/save; final disabled mode enters h immediately and saves hello |
| Htop | Corrected package opens, arrows/Page Up/Down work, q exits immediately with predictions off; Android restricts some statistics |
| Rotation | Portrait hel survives landscape transition and completes to hello; original rotation restored |
| Insets | Stock non-fullscreen portrait and docked Gboard keep tested extra keys reachable |
| Split-screen | Native Samsung multi-window with Display in other pane; hello plus left/right navigation passes with docked Gboard |
| One-handed Gboard | hello passes; full-width keyboard restored |
| Floating Gboard | Default position covers part of extra-key rows; repositioned above them, hello passes and rows are reachable |
| Background/resume | hel finalizes before Home; returning plus lo yields hello without duplication |
| Cold restart | Normal startup retains working personal PREFIX and returns to real shell |

Floating Gboard is not overlap-free at every position. Dock or reposition it
when it covers controls. Predictive mode buffers the current token and can
delay normal Vim/TUI commands; disable it for immediate input or secrets.

## Failed attempts and fixes

The case journal retains failed attempts. Initially the personal mirror was a
directory where pkg expects a file. termux-tools revision 1 and the final ZIP
fix it; a fresh bootstrap and package install subsequently pass without repair.

An incremental NDK build embedded an older ZIP. The pre-install audit rejected
it; that APK was never installed. A generated assembly dependency header and
exact embedded-ZIP verification now protect local and CI builds.

Htop initially omitted libandroid-execinfo. An early manually posted
htop_navigation_exit PASS was invalid: screen review showed a linker failure.
That record is excluded. Revision 1 declares the dependency; actual launch,
navigation and immediate exit subsequently pass. Finding a program on PATH
does not prove that it executes.

Other failed trials involved an omitted expected trailing space, a redundant
symbols/ABC switch, or coordinates used while the keyboard was hidden/attaching.
They were corrected and rerun. Engineering guards now require the actual focused
window, including in multi-window mode, and wait for keyboard attachment.

## Coverage limits and evidence

This is one device and one Gboard version. No physical Bluetooth/USB keyboard
was connected; the Ctrl/Alt event shortcut used simulated ADB key events.
CJK, voice, every emoji grapheme cluster, accessibility configurations and
every floating position were not physically validated.

Only synthetic words and non-sensitive metadata were retained. No passwords,
tokens, original history or original private-file contents were recorded.
Private screenshots, inventories, hashes, build logs and metadata-only case
journal are in ../termux-avic-handoff/ on the workstation. They are outside
the product APK. Temporary settings and the result bridge are restored/removed
at delivery.

The curated repository contains 128 DEB archives / 126 package names, rather
than the entire upstream catalog. Its pinned source predates current updates
and needs deliberate maintenance. Stock plugins and official com.termux
binary mirrors are incompatible. The bootstrap command-not-found catalog
covers the core build snapshot; refresh it when extending the repository.

See [build, privacy and installation instructions](termux-avic.md). No upstream
PR, maintainer contact or public signed release was made.
