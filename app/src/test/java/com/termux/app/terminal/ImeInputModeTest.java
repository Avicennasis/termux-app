package com.termux.app.terminal;

import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ImeInputModeTest {
    private TerminalSession session() {
        TerminalSessionClient callback = (TerminalSessionClient) Proxy.newProxyInstance(
            TerminalSessionClient.class.getClassLoader(), new Class[]{TerminalSessionClient.class},
            (proxy, method, args) -> method.getReturnType() == Integer.class ? Integer.valueOf(0) : null);
        TerminalSession session = new TerminalSession("unused", "/", new String[0], new String[0], 100, callback);
        ReflectionHelpers.setField(session, "mEmulator", new TerminalEmulator(session, 80, 24, 8, 16, 100, callback));
        ReflectionHelpers.setField(session, "mShellPid", 1);
        return session;
    }

    private void screen(TerminalSession session, boolean alternate) {
        byte[] bytes = (alternate ? "\u001b[?1049h" : "\u001b[?1049l").getBytes(StandardCharsets.UTF_8);
        session.getEmulator().append(bytes, bytes.length);
    }

    private String output(TerminalSession session) {
        Object queue = ReflectionHelpers.getField(session, "mTerminalToProcessIOQueue");
        byte[] bytes = new byte[4096];
        int count = ReflectionHelpers.callInstanceMethod(queue, "read",
            ReflectionHelpers.ClassParameter.from(byte[].class, bytes),
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
        return count <= 0 ? "" : new String(bytes, 0, count, StandardCharsets.UTF_8);
    }

    private TerminalView view(TerminalSession session) {
        return view(session, new boolean[1]);
    }

    private TerminalView view(TerminalSession session, boolean[] control) {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.mTermSession = session;
        view.mEmulator = session.getEmulator();
        view.setTerminalViewClient(new TermuxTerminalViewClientBase() {
            @Override public boolean shouldEnableImeSuggestions() { return true; }
            @Override public boolean shouldEnableImeSuggestionsInAlternateScreen() { return true; }
            @Override public boolean shouldPauseImeSuggestions() { return !ImeInputMode.isText(view.getCurrentSession()); }
            @Override public boolean hasTerminalInputModifiers() { return control[0]; }
            @Override public boolean readControlKey() {
                boolean pressed = control[0]; control[0] = false; return pressed;
            }
            @Override public void onTerminalCodePointSent(TerminalSession target, int codePoint, boolean altDown) {
                if (ImeInputMode.onCodePointSent(target, codePoint, altDown)) view.updateImeInputMode();
            }
        });
        return view;
    }

    @Test public void choiceIsPerSessionSurvivesANewViewAndResetsOnScreenChanges() {
        TerminalSession first = session(), second = session();
        screen(first, true);
        screen(second, true);
        assertFalse(ImeInputMode.isText(first));
        ImeInputMode.toggle(first);
        assertTrue(view(first).shouldEnableImeSuggestions());
        assertFalse(view(second).shouldEnableImeSuggestions());
        screen(first, false);
        assertTrue(ImeInputMode.isText(first));
        screen(first, true);
        assertFalse(ImeInputMode.isText(first));
        ImeInputMode.toggle(first);
        screen(first, false);
        assertTrue(ImeInputMode.isText(first));
    }

    @Test public void ctrlGSelectsKeysUntilAnExplicitTapAndThenPredictionsWorkAgain() {
        for (boolean hardware : new boolean[]{false, true}) {
            TerminalSession session = session();
            screen(session, true);
            ImeInputMode.toggle(session);
            boolean[] control = new boolean[1];
            TerminalView view = view(session, control);
            InputConnection text = view.onCreateInputConnection(new EditorInfo());
            text.setComposingText("draft", 1);
            if (hardware) {
                view.onKeyDown(KeyEvent.KEYCODE_G,
                    new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_G, 0, KeyEvent.META_CTRL_ON));
            } else {
                control[0] = true;
                assertTrue(text.setComposingText("g", 1));
                assertFalse(text.commitText("g", 1));
                assertFalse(control[0]);
            }
            assertEquals("draft\u0007", output(session));
            assertFalse(view.shouldEnableImeSuggestions());
            assertFalse(text.commitText("late", 1));
            InputConnection keys = view.onCreateInputConnection(new EditorInfo());
            assertTrue(keys.commitText("t", 1));
            assertEquals("t", output(session));
            // Neither output nor an arbitrarily long pause resumes buffering in command mode.
            view.onScreenUpdated();
            assertFalse(view.shouldEnableImeSuggestions());
            assertTrue(keys.commitText("n", 1));
            assertEquals("n", output(session));
            ImeInputMode.toggle(session);
            view.updateImeInputMode();
            assertFalse(keys.commitText("late", 1));
            InputConnection fresh = view.onCreateInputConnection(new EditorInfo());
            fresh.setComposingText("wprld", 1);
            assertEquals("", output(session));
            fresh.commitText("world ", 1);
            assertEquals("world ", output(session));
        }
    }

    @Test public void ordinaryControlsAndAltChordsDoNotPauseShellTextInput() {
        TerminalSession shell = session();
        assertFalse(ImeInputMode.onCodePointSent(shell, 7, false));
        screen(shell, true);
        ImeInputMode.toggle(shell);
        assertFalse(ImeInputMode.onCodePointSent(shell, 7, true));
        assertFalse(ImeInputMode.onCodePointSent(shell, 3, false));
        assertTrue(ImeInputMode.isText(shell));
    }
}
