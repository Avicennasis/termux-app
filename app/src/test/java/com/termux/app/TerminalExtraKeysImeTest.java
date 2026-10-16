package com.termux.app;

import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
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
public class TerminalExtraKeysImeTest {
    @Test
    public void multiRowPopupsAndMacrosKeepTerminalControlsWhileComposing() throws Exception {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTerminalViewClient(new TermuxTerminalViewClientBase() {
            @Override
            public boolean shouldEnableImeSuggestions() { return true; }
        });
        TerminalSessionClient callback = (TerminalSessionClient) Proxy.newProxyInstance(
            TerminalSessionClient.class.getClassLoader(), new Class[]{TerminalSessionClient.class},
            (proxy, method, args) -> method.getReturnType() == Integer.class ? Integer.valueOf(0) : null);
        TerminalSession session = new TerminalSession("unused", "/", new String[0], new String[0], 100, callback);
        TerminalEmulator emulator = new TerminalEmulator(session, 80, 24, 8, 16, 100, callback);
        ReflectionHelpers.setField(session, "mEmulator", emulator);
        ReflectionHelpers.setField(session, "mShellPid", 1);
        view.mTermSession = session;
        view.mEmulator = emulator;
        TerminalExtraKeys handler = new TerminalExtraKeys(view);
        ExtraKeysInfo info = new ExtraKeysInfo(
            "[[{macro:'CTRL c'}, {macro:'CTRL d'}, {macro:'CTRL l'}, {macro:'CTRL z'}, {macro:'ALT b'}],"
                + "['ESC','TAB','UP','LEFT',{key:'HOME',popup:'END'},'custom']]",
            "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        ExtraKeyButton[][] rows = info.getMatrix();
        assertEquals(2, rows.length);
        assertEquals(5, rows[0].length);
        assertEquals(6, rows[1].length);
        String[] expected = {"\u0003", "\u0004", "\u000c", "\u001a", "\u001bb"};
        for (int i = 0; i < rows[0].length; i++) {
            InputConnection connection = view.onCreateInputConnection(new EditorInfo());
            connection.setComposingText("draft", 1);
            handler.onExtraKeyButtonClick(null, rows[0][i], null);
            assertEquals("draft" + expected[i], output(session));
            assertFalse(connection.commitText("late", 1));
        }
        String[] second = {"\u001b", "\t", "\u001b[A", "\u001b[D", "\u001b[H", "custom"};
        for (int i = 0; i < rows[1].length; i++) {
            view.onCreateInputConnection(new EditorInfo()).setComposingText("draft", 1);
            handler.onExtraKeyButtonClick(null, rows[1][i], null);
            assertEquals("draft" + second[i], output(session));
        }
        assertNotNull(rows[1][4].getPopup());
        view.onCreateInputConnection(new EditorInfo()).setComposingText("draft", 1);
        handler.onExtraKeyButtonClick(null, rows[1][4].getPopup(), null);
        assertEquals("draft\u001b[F", output(session));
    }

    private String output(TerminalSession session) {
        Object queue = ReflectionHelpers.getField(session, "mTerminalToProcessIOQueue");
        byte[] buffer = new byte[4096];
        int count = ReflectionHelpers.callInstanceMethod(queue, "read",
            ReflectionHelpers.ClassParameter.from(byte[].class, buffer),
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
        return new String(buffer, 0, count, StandardCharsets.UTF_8);
    }
}
