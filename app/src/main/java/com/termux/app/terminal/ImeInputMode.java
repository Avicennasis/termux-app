package com.termux.app.terminal;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Temporary, per-session Text/Keys choices, used only on the Android main thread.
 * Session objects outlive activity recreation; weak keys release choices when sessions end.
 * No input, screen contents, remote identities or keybinding maps are stored here.
 */
public final class ImeInputMode {
    private static final Map<TerminalSession, State> STATES = new WeakHashMap<>();

    private static final class State {
        boolean alternate;
        Boolean text;
        State(boolean alternate) { this.alternate = alternate; }
    }

    private ImeInputMode() {}

    private static State state(TerminalSession session) {
        TerminalEmulator emulator = session.getEmulator();
        boolean alternate = emulator != null && emulator.isAlternateBufferActive();
        State state = STATES.get(session);
        if (state == null || state.alternate != alternate) {
            // A different screen restores the safe default: Text in a shell, Keys in a TUI.
            state = new State(alternate);
            STATES.put(session, state);
        }
        return state;
    }

    public static boolean isText(TerminalSession session) {
        if (session == null) return true;
        State state = state(session);
        return state.text != null ? state.text : !state.alternate;
    }

    public static void toggle(TerminalSession session) {
        if (session != null) state(session).text = !isText(session);
    }

    public static boolean onCodePointSent(TerminalSession session, int codePoint, boolean altDown) {
        if (session == null || codePoint != 7 || altDown) return false;
        State state = state(session);
        if (!state.alternate || !isText(session)) return false;
        // Ctrl+G is this personal app's Zellij prefix. Stay in Keys until an explicit tap;
        // neither an idle timer nor a guessed sequence length decides when text resumes.
        state.text = false;
        return true;
    }
}
