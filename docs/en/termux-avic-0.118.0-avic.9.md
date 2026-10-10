# Termux-Avic 0.118.0-avic.9 — UI review build

This first native UI pass adds color and clearer controls to the drawer and app settings. It follows the keyboard-placement mockup: warm green surfaces, teal secondary actions, amber status details, and a prominent Keyboard action. Both light and dark appearances are included.

The working keyboard/PAGE changes are in [PR #2](https://github.com/Avicennasis/termux-app/pull/2). This branch, `UI/UX-Redesign`, starts from that PR commit. Version .9 uses versionCode 1008 and the existing personal package, signing identity, and custom-prefix bootstrap.

## Presentation

- The drawer has a compact Termux mark, a Settings icon, an outlined New session action, and numbered session cards. Current sessions have a checkmark and checked accessibility state. Names and terminal titles wrap; running and exit status remain available in text.
- The filled Keyboard button remains 56dp high with a 40dp inert gap above the extra keys. Drawer actions have at least 48dp touch targets. Tablet gutters increase to 24dp, and the branding row hides in landscape to leave more room for sessions.
- Settings, app toolbars, and dialog surfaces share the palette. Icons are Android vector drawables from [Google Material Icons](https://github.com/google/material-design-icons), licensed under Apache 2.0. No network assets or new fonts are required by the app.
- Buffered toolbar input now has an explanatory hint: type there, then tap Send. Hardware Tab can enter an open drawer, navigation selection has a focus outline, and closing the drawer returns input focus to the terminal when appropriate.

## Color and type

| Role | Dark | Light |
| --- | --- | --- |
| Drawer surface | `#1C2820` | `#F4F5EB` |
| Raised session surface | `#26362A` | `#FAFBF5` |
| Main text | `#EDF2E5` | `#202D23` |
| Primary action | `#C5E99A` | `#496B24` |
| Secondary action | `#8ED2C3` | `#225F56` |
| Status | `#E7BD75` | `#79571B` |

Native Android typography is retained: 18sp branding, 16sp session names and Keyboard label, 14sp secondary actions, and 12sp session details. Spacing uses a 4/8dp scale with 8dp badges and 12dp card/button corners. Active PAGE/modifier labels stay red; a lighter red keeps them readable during a press.

The palette and targets follow the roles and sizing described in [Material color guidance](https://m3.material.io/styles/color/the-color-system) and [Android Views accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views). Existing terminal colors and properties still control terminal content.

## Behavior

The session controller retains its switching and long-press rename handlers. PAGE persistence, page escape sequences, keyboard toggling, custom extra-key layouts, modifier behavior, the text-input Send action, and drawer input suppression carry forward. The drawer guard adds hardware focus routing without changing its input-blocking conditions.

The web preview is an interactive visual simulation with synthetic sessions; it never sends terminal input. Native screenshots render the real new resources, session component, and drawer guard inside a separate Android 15 preview app. That fixture has no shell or terminal service and does not access the personal app's HOME, PREFIX, or preferences.

## Validation

All debug and release unit tests pass: 283 in each variant, including selection semantics, recycled rows, light/dark contrast, 375dp layout, landscape layout, font scale 2, drawer focus return, PAGE behavior, and input suppression. The signed arm64 review APK builds with the existing bootstrap and certificate. Layout screenshots cover light/dark presentation and settings; the local handoff records the additional device review details.

This review build has not been installed on the S24. The phone retains .8. Native preview testing checks presentation and focus, rather than a full shell/terminal lifecycle or a TalkBack session.

Open the [interactive preview](https://dev.simmons.systems/termux-ui-redesign.html), or view its [standalone source](../design/termux-ui-redesign.html).
