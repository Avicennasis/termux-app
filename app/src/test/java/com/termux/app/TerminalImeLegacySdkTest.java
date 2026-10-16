package com.termux.app;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

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

/** The app's older Robolectric supports API 21, which the Android 16 test runner no longer does. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21)
public class TerminalImeLegacySdkTest {
    @Test
    public void legacyInputAndPredictiveDraftWorkOnMinimumSdkIncludingLiteralExtraKeys() throws Exception {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        TermuxTerminalViewClientBase legacy = new TermuxTerminalViewClientBase();
        view.setTerminalViewClient(legacy);
        TerminalSessionClient callback = (TerminalSessionClient) Proxy.newProxyInstance(
            TerminalSessionClient.class.getClassLoader(), new Class[]{TerminalSessionClient.class},
            (proxy, method, args) -> method.getReturnType() == Integer.class ? Integer.valueOf(0) : null);
        TerminalSession session = new TerminalSession("unused", "/", new String[0], new String[0], 100, callback);
        TerminalEmulator emulator = new TerminalEmulator(session, 80, 24, 8, 16, 100, callback);
        ReflectionHelpers.setField(session, "mEmulator", emulator);
        ReflectionHelpers.setField(session, "mShellPid", 1);
        view.mTermSession = session;
        view.mEmulator = emulator;

        EditorInfo info = new EditorInfo();
        InputConnection connection = view.onCreateInputConnection(info);
        assertEquals(InputType.TYPE_NULL, info.inputType);
        connection.commitText("abc", 1);
        assertEquals("abc", output(session));

        view.setTerminalViewClient(new TermuxTerminalViewClientBase() {
            @Override
            public boolean shouldEnableImeSuggestions() { return true; }
        });
        connection = view.onCreateInputConnection(info);
        assertEquals(InputType.TYPE_CLASS_TEXT, info.inputType & InputType.TYPE_MASK_CLASS);
        assertTrue((info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0);
        connection.setComposingText("caf", 1);
        connection.setComposingText("café中文", 1);
        connection.commitText("café中文 ", 1);
        assertEquals("café中文 ", output(session));
        connection.setComposingText("😀", 1);
        connection.deleteSurroundingText(2, 0);
        connection.commitText("draft", 1);
        ExtraKeysInfo keys = new ExtraKeysInfo("[['custom']]", "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        new TerminalExtraKeys(view).onExtraKeyButtonClick(null, keys.getMatrix()[0][0], null);
        assertEquals("draftcustom", output(session));
        assertFalse(connection.commitText("late", 1));

        connection = view.onCreateInputConnection(info);
        connection.setComposingText("finish", 1);
        view.finishImeInput(); // Must not call the API 24 BaseInputConnection.closeConnection on API 21.
        assertEquals("finish", output(session));
        assertFalse(connection.finishComposingText());
    }

    private String output(TerminalSession session) {
        Object queue = ReflectionHelpers.getField(session, "mTerminalToProcessIOQueue");
        byte[] buffer = new byte[4096];
        int count = ReflectionHelpers.callInstanceMethod(queue, "read",
            ReflectionHelpers.ClassParameter.from(byte[].class, buffer),
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
        return count <= 0 ? "" : new String(buffer, 0, count, StandardCharsets.UTF_8);
    }
}
