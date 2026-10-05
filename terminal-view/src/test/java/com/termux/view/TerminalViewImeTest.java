package com.termux.view;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Build;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.shadows.ShadowInputMethodManager;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/** Replays the real TerminalView -> TerminalSession UTF-8 queue path without starting a PTY. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 36}, manifest = Config.NONE)
public class TerminalViewImeTest {

    /** These sessions have no PTY; keep real View layout and emulator resize in the lifecycle test. */
    @Implements(className = "com.termux.terminal.JNI", isInAndroidSdk = false)
    public static class NoPtyResize {
        @Implementation protected static void __staticInitializer__() {}
        @Implementation protected static void setPtyWindowSize(int fd, int rows, int columns,
                                                               int cellWidth, int cellHeight) {}
    }

    @Implements(InputMethodManager.class)
    public static class RestartTracker extends ShadowInputMethodManager {
        static int restarts;
        @Implementation protected void restartInput(View target) { restarts++; }
    }

    static class Client implements TerminalViewClient {
        boolean predictions;
        boolean charBased;
        boolean selected = true;
        boolean ctrl;
        boolean alt;
        boolean shift;
        boolean alternateText;
        boolean paused;
        final StringBuilder logs = new StringBuilder();

        public boolean shouldEnableImeSuggestions() { return predictions; }
        public boolean shouldEnableImeSuggestionsInAlternateScreen() { return alternateText; }
        public boolean shouldPauseImeSuggestions() { return paused; }
        public boolean shouldEnforceCharBasedInput() { return charBased; }
        public boolean isTerminalViewSelected() { return selected; }
        public boolean hasTerminalInputModifiers() { return ctrl || alt || shift; }
        public boolean readControlKey() { boolean result = ctrl; ctrl = false; return result; }
        public boolean readAltKey() { boolean result = alt; alt = false; return result; }
        public boolean readShiftKey() { boolean result = shift; shift = false; return result; }
        public boolean readFnKey() { return false; }
        public boolean shouldBackButtonBeMappedToEscape() { return false; }
        public boolean shouldUseCtrlSpaceWorkaround() { return false; }
        public float onScale(float scale) { return scale; }
        public void onSingleTapUp(MotionEvent e) {}
        public void copyModeChanged(boolean copyMode) {}
        public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) { return false; }
        public boolean onKeyUp(int keyCode, KeyEvent e) { return false; }
        public boolean onLongPress(MotionEvent e) { return false; }
        public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }
        public void onEmulatorSet() {}
        public void logError(String tag, String message) { logs.append(message); }
        public void logWarn(String tag, String message) { logs.append(message); }
        public void logInfo(String tag, String message) { logs.append(message); }
        public void logDebug(String tag, String message) { logs.append(message); }
        public void logVerbose(String tag, String message) { logs.append(message); }
        public void logStackTraceWithMessage(String tag, String message, Exception e) { logs.append(message); }
        public void logStackTrace(String tag, Exception e) {}
    }

    private Client client;
    private TerminalView view;
    private TerminalSession session;

    private TerminalSession newSession() {
        TerminalSessionClient callback = (TerminalSessionClient) Proxy.newProxyInstance(
            TerminalSessionClient.class.getClassLoader(), new Class[]{TerminalSessionClient.class},
            (proxy, method, args) -> method.getReturnType() == Integer.class ? Integer.valueOf(0) : null);
        TerminalSession result = new TerminalSession("unused", "/", new String[0], new String[0], 100, callback);
        TerminalEmulator emulator = new TerminalEmulator(result, 80, 24, 8, 16, 100, callback);
        ReflectionHelpers.setField(result, "mEmulator", emulator);
        ReflectionHelpers.setField(result, "mShellPid", 1);
        return result;
    }

    @Before
    public void setUp() {
        client = new Client();
        view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTerminalViewClient(client);
        view.setIsTerminalViewKeyLoggingEnabled(false);
        session = newSession();
        view.mTermSession = session;
        view.mEmulator = session.getEmulator();
        view.mRenderer = new TerminalRenderer(14, Typeface.MONOSPACE);
    }

    private InputConnection predictive() {
        client.predictions = true;
        return view.onCreateInputConnection(new EditorInfo());
    }

    private String output(TerminalSession source) {
        Object queue = ReflectionHelpers.getField(source, "mTerminalToProcessIOQueue");
        byte[] bytes = new byte[4096];
        int count = ReflectionHelpers.callInstanceMethod(queue, "read",
            ReflectionHelpers.ClassParameter.from(byte[].class, bytes),
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
        return count <= 0 ? "" : new String(bytes, 0, count, StandardCharsets.UTF_8);
    }

    private void terminalKey(int key, int modifiers) {
        view.onKeyDown(key, new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0, modifiers));
    }

    @Test
    public void legacyDefaultAndCharacterModeKeepTheirEditorInfoAndImmediateInput() {
        for (boolean charMode : new boolean[]{false, true}) {
            client.charBased = charMode;
            EditorInfo info = new EditorInfo();
            InputConnection connection = view.onCreateInputConnection(info);
            assertFalse(connection instanceof TerminalInputConnection);
            assertEquals(charMode ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS : InputType.TYPE_NULL, info.inputType);
            assertEquals(EditorInfo.IME_FLAG_NO_FULLSCREEN, info.imeOptions);
            connection.commitText("a", 1);
            connection.commitText("bc", 1);
            assertEquals("abc", output(session));
        }
    }

    @Test
    public void optInAdvertisesPredictionsAndDiscouragesLearning() {
        client.predictions = true;
        EditorInfo info = new EditorInfo();
        assertTrue(view.onCreateInputConnection(info) instanceof TerminalInputConnection);
        assertEquals(InputType.TYPE_CLASS_TEXT, info.inputType & InputType.TYPE_MASK_CLASS);
        assertEquals(0, info.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        assertTrue((info.inputType & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0);
        assertTrue((info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0);
        assertTrue((info.imeOptions & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0);
        assertEquals(0, info.initialSelStart);
        assertEquals(0, info.initialSelEnd);
    }

    @Test
    public void characterBasedOverrideAndAlternateToolbarSelectionKeepLegacyContract() {
        client.predictions = true;
        client.charBased = true;
        assertFalse(view.onCreateInputConnection(new EditorInfo()) instanceof TerminalInputConnection);
        client.charBased = false;
        client.selected = false;
        EditorInfo info = new EditorInfo();
        assertFalse(view.onCreateInputConnection(info) instanceof TerminalInputConnection);
        assertEquals(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL, info.inputType);
    }

    private void screenSequence(String sequence) {
        byte[] bytes = sequence.getBytes(StandardCharsets.UTF_8);
        view.mEmulator.append(bytes, bytes.length);
        view.onScreenUpdated();
    }

    @Test
    public void alternateScreenZellijSequenceUsesImmediateUnmodifiedLetters() {
        client.predictions = true;
        screenSequence("\u001b[?1049h");
        assertTrue(view.mEmulator.isAlternateBufferActive());
        EditorInfo info = new EditorInfo();
        InputConnection connection = view.onCreateInputConnection(info);
        assertFalse(connection instanceof TerminalInputConnection);
        assertEquals(InputType.TYPE_NULL, info.inputType);
        assertTrue((info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0);
        client.ctrl = true;
        assertTrue(connection.commitText("g", 1));
        assertEquals("\u0007", output(session));
        assertFalse(client.ctrl);
        assertTrue(connection.commitText("n", 1));
        assertEquals("n", output(session));
        assertTrue(connection.commitText("t", 1));
        assertEquals("t", output(session));
        assertTrue(client.predictions);
    }

    @Test
    public void enteringAlternateScreenFinalizesDraftOnceAndRetiresEveryProbe() {
        InputConnection bound = predictive();
        assertTrue(bound.setComposingText("draft", 1));
        InputConnection probe = view.onCreateInputConnection(new EditorInfo());
        client.ctrl = true;
        screenSequence("\u001b[?1049h");
        assertEquals("draft", output(session));
        assertTrue(client.ctrl);
        assertFalse(bound.commitText("late", 1));
        assertFalse(probe.setComposingText("late", 1));
        assertEquals(0, ((TerminalInputConnection) bound).getEditable().length());
        view.onScreenUpdated();
        assertEquals("", output(session));
        InputConnection immediate = view.onCreateInputConnection(new EditorInfo());
        assertTrue(immediate.commitText("g", 1));
        assertEquals("\u0007", output(session));
        assertFalse(client.ctrl);
    }

    @Test
    public void leavingAlternateScreenRestoresPredictionsWithoutChangingProperty() {
        client.predictions = true;
        screenSequence("\u001b[?1049h");
        InputConnection immediate = view.onCreateInputConnection(new EditorInfo());
        assertTrue(immediate.commitText("n", 1));
        assertEquals("n", output(session));
        screenSequence("\u001b[?1049l");
        assertFalse(view.mEmulator.isAlternateBufferActive());
        assertTrue(client.predictions);
        EditorInfo info = new EditorInfo();
        InputConnection draft = view.onCreateInputConnection(info);
        assertTrue(draft instanceof TerminalInputConnection);
        assertTrue((info.inputType & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0);
        assertTrue(draft.setComposingText("hel", 1));
        view.onScreenUpdated();
        assertEquals("", output(session));
        assertTrue(draft.commitText("hello ", 1));
        assertEquals("hello ", output(session));
    }

    @Test
    public void alternateScreenTransitionsPreserveDefaultAndCharacterOverride() {
        for (boolean predictions : new boolean[]{false, true}) {
            client.predictions = predictions;
            client.charBased = true;
            for (String sequence : new String[]{"\u001b[?47h", "\u001b[?47l",
                    "\u001b[?1049h", "\u001b[?1049l"}) {
                screenSequence(sequence);
                EditorInfo info = new EditorInfo();
                InputConnection connection = view.onCreateInputConnection(info);
                assertFalse(connection instanceof TerminalInputConnection);
                assertEquals(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, info.inputType);
                assertEquals(EditorInfo.IME_FLAG_NO_FULLSCREEN, info.imeOptions);
                assertTrue(connection.commitText("x", 1));
                assertEquals("x", output(session));
                assertEquals(predictions, client.predictions);
            }
        }
    }

    @Test
    public void explicitAlternateTextKeepsEditablePredictionsAndKeysSwitchRetiresCallbacks() {
        client.alternateText = true;
        screenSequence("\u001b[?1049h");
        InputConnection text = predictive();
        assertTrue(text.setComposingText("wprld", 1));
        assertEquals("", output(session));
        assertTrue(text.commitText("world ", 1));
        assertEquals("world ", output(session));
        assertTrue(text.setComposingText("draft", 1));
        client.paused = true;
        view.updateImeInputMode();
        assertEquals("draft", output(session));
        assertFalse(text.commitText("late", 1));
        EditorInfo info = new EditorInfo();
        InputConnection keys = view.onCreateInputConnection(info);
        assertEquals(InputType.TYPE_NULL, info.inputType);
        assertTrue((info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0);
        assertTrue(keys.commitText("t", 1));
        assertEquals("t", output(session));
        assertTrue(keys.commitText("n", 1));
        assertEquals("n", output(session));
        client.paused = false;
        view.updateImeInputMode();
        assertFalse(keys.commitText("stale", 1));
        assertFalse(keys.setComposingText("stale", 1));
        assertFalse(keys.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_T)));
        assertEquals("", output(session));
        InputConnection restored = predictive();
        restored.setComposingText("hel", 1);
        assertEquals("", output(session));
        restored.commitText("hello ", 1);
        assertEquals("hello ", output(session));
    }

    @Test
    public void explicitTextCannotOverrideOptInOrCharacterInputAndKeysSuppressInputLogs() {
        client.alternateText = true;
        screenSequence("\u001b[?1049h");
        assertFalse(view.onCreateInputConnection(new EditorInfo()) instanceof TerminalInputConnection);
        client.predictions = true;
        client.charBased = true;
        assertFalse(view.onCreateInputConnection(new EditorInfo()) instanceof TerminalInputConnection);
        client.charBased = false;
        client.paused = true;
        view.setIsTerminalViewKeyLoggingEnabled(true);
        InputConnection keys = view.onCreateInputConnection(new EditorInfo());
        keys.commitText("synthetic", 1);
        terminalKey(KeyEvent.KEYCODE_N, 0);
        assertEquals("syntheticn", output(session));
        assertEquals("", client.logs.toString());
    }

    @Test
    public void keysCompositionSurvivesDiscardedProbeAndFinalizesBeforeTextWithoutStealingCtrl() {
        client.predictions = true;
        client.paused = true;
        InputConnection keys = view.onCreateInputConnection(new EditorInfo());
        assertTrue(keys.setComposingText("한", 1));
        InputConnection discarded = view.onCreateInputConnection(new EditorInfo());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) discarded.closeConnection();
        assertEquals("한", keys.getTextBeforeCursor(10, 0).toString());
        assertTrue(keys.setComposingText("한글", 1));
        assertEquals("", output(session));
        client.ctrl = true;
        client.paused = false;
        view.updateImeInputMode();
        assertEquals("한글", output(session));
        assertTrue(client.ctrl);
        assertFalse(keys.commitText("late", 1));
        assertFalse(discarded.finishComposingText());
        assertEquals("", output(session));
        InputConnection text = view.onCreateInputConnection(new EditorInfo());
        assertTrue(text.commitText("c", 1));
        assertEquals("\u0003", output(session));
        assertFalse(client.ctrl);
    }

    @Test
    @Config(shadows = NoPtyResize.class)
    public void keysCallbacksCannotFollowASessionSwitchOrBecomeValidAfterSwitchingBack() {
        client.predictions = true;
        client.paused = true;
        ReflectionHelpers.setField(view.mRenderer, "mFontWidth", 8f);
        ReflectionHelpers.setField(view.mRenderer, "mFontLineSpacing", 16);
        ReflectionHelpers.setField(view.mRenderer, "mFontLineSpacingAndAscent", 16);
        view.layout(0, 0, 800, 600);
        InputConnection keys = view.onCreateInputConnection(new EditorInfo());
        TerminalSession next = newSession();
        view.attachSession(next);
        assertFalse(keys.commitText("late", 1));
        view.attachSession(session);
        assertFalse(keys.commitText("late", 1));
        assertEquals("", output(next));
        assertEquals("", output(session));
    }

    @Test
    public void unicodeSpacePunctuationAndNewlineUseExistingUtf8AndCrTranslation() {
        InputConnection connection = predictive();
        connection.setComposingText("café😀中文", 1);
        connection.commitText("café😀中文!\n", 1);
        connection.finishComposingText();
        assertEquals("café😀中文!\r", output(session));
    }

    @Test
    public void textEnterAtMidDraftSendsFullUtf8TokenBeforeCrAndRetiresTail() {
        for (String enter : new String[]{"\n", "\r"}) {
            InputConnection connection = predictive();
            connection.setComposingText("가", 1);
            connection.finishComposingText();
            connection.setSelection(0, 0);
            connection.setComposingText("나", 1);
            connection.finishComposingText();
            assertEquals("가", connection.getTextAfterCursor(10, 0).toString());
            assertTrue(connection.commitText(enter, 1));
            assertEquals("나가\r", output(session));
            assertFalse(connection.commitText("late", 1));
            view.finishImeInput();
            assertEquals("", output(session));
            predictive().commitText("next ", 1);
            assertEquals("next ", output(session));
        }
    }

    @Test
    public void extraKeyControlAndNavigationPathsPreserveExactTerminalSequences() {
        int[] keys = {KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END};
        String[] expected = {"\u0003", "\u0004", "\u000c", "\u001a", "\u001b", "\t",
            "\u001b[A", "\u001b[B", "\u001b[D", "\u001b[C", "\u001b[H", "\u001b[F"};
        for (int i = 0; i < keys.length; i++) {
            InputConnection connection = predictive();
            connection.setComposingText("word", 1);
            terminalKey(keys[i], i < 4 ? KeyEvent.META_CTRL_ON : 0);
            assertEquals("key " + keys[i], "word" + expected[i], output(session));
            assertFalse(connection.commitText("stale", 1));
        }
    }

    @Test
    public void altAndLiteralMacroPathsFlushWithoutStealingOneShotModifiers() {
        InputConnection connection = predictive();
        connection.setComposingText("word", 1);
        terminalKey(KeyEvent.KEYCODE_B, KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON);
        assertEquals("word\u001bb", output(session));
        connection = predictive();
        connection.setComposingText("plain", 1);
        client.ctrl = true;
        view.inputCodePoint(TerminalView.KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD, 'c', false, false);
        assertEquals("plain\u0003", output(session));
        assertFalse(client.ctrl);
    }

    @Test
    public void oneShotShiftLeftFinalizesDraftAndPreservesTerminalModifierSequence() {
        for (boolean alternateScreen : new boolean[]{false, true}) {
            InputConnection connection = predictive();
            connection.setComposingText("draft", 1);
            if (alternateScreen) screenSequence("\u001b[?1049h");
            client.shift = true;
            terminalKey(KeyEvent.KEYCODE_DPAD_LEFT, 0);
            assertEquals("draft\u001b[1;2D", output(session));
            assertFalse(client.shift);
            terminalKey(KeyEvent.KEYCODE_DPAD_LEFT, 0);
            assertEquals("\u001b[D", output(session));
            if (alternateScreen) screenSequence("\u001b[?1049l");
        }
    }

    @Test
    public void oneShotCtrlThroughGboardCompositionSendsShortcutOnlyOnce() {
        InputConnection connection = predictive();
        connection.setComposingText("word", 1);
        client.ctrl = true;
        connection.setComposingText("c", 1);
        assertFalse(connection.commitText("c", 1));
        assertEquals("word\u0003", output(session));
    }

    @Test
    public void terminalOutputWhileComposingNeverBecomesImeTextOrCommitsDraft() {
        InputConnection connection = predictive();
        connection.setComposingText("draft", 1);
        byte[] output = "background output\r\n".getBytes(StandardCharsets.UTF_8);
        view.mEmulator.append(output, output.length);
        view.onScreenUpdated();
        assertEquals("draft", connection.getTextBeforeCursor(100, 0).toString());
        assertEquals("", output(session));
        connection.commitText("corrected ", 1);
        assertEquals("corrected ", output(session));
    }

    @Test
    public void sessionAndWindowFocusChangesFinishOldTargetAndRejectLateCallbacks() {
        InputConnection old = predictive();
        old.setComposingText("old", 1);
        TerminalSession next = newSession();
        assertTrue(view.attachSession(next));
        view.mEmulator = next.getEmulator();
        assertEquals("old", output(session));
        assertFalse(old.commitText("late", 1));
        InputConnection fresh = predictive();
        fresh.setComposingText("new", 1);
        view.onWindowFocusChanged(false);
        assertEquals("new", output(next));
        assertFalse(fresh.finishComposingText());
        assertEquals("", output(session));
    }

    @Test
    @Config(shadows = NoPtyResize.class)
    public void sessionSwitchRefreshesModeWithoutWaitingForNewOutput() {
        client.predictions = true;
        // Legacy Robolectric graphics do not measure font spacing; use fixed test cell dimensions.
        ReflectionHelpers.setField(view.mRenderer, "mFontWidth", 8f);
        ReflectionHelpers.setField(view.mRenderer, "mFontLineSpacing", 16);
        ReflectionHelpers.setField(view.mRenderer, "mFontLineSpacingAndAscent", 16);
        view.layout(0, 0, 800, 600);
        InputConnection old = predictive();
        old.setComposingText("draft", 1);
        TerminalSession tui = newSession();
        byte[] alternateScreen = "\u001b[?1049h".getBytes(StandardCharsets.UTF_8);
        tui.getEmulator().append(alternateScreen, alternateScreen.length);
        assertTrue(view.attachSession(tui));
        assertEquals("draft", output(session));
        assertFalse(old.commitText("late", 1));
        assertFalse((Boolean) ReflectionHelpers.getField(view, "mLastImeSuggestionsEnabled"));
        InputConnection immediate = view.onCreateInputConnection(new EditorInfo());
        assertFalse(immediate instanceof TerminalInputConnection);
        immediate.commitText("n", 1);
        assertEquals("n", output(tui));
        assertTrue(view.attachSession(session));
        assertTrue((Boolean) ReflectionHelpers.getField(view, "mLastImeSuggestionsEnabled"));
        InputConnection restored = view.onCreateInputConnection(new EditorInfo());
        assertTrue(restored instanceof TerminalInputConnection);
        restored.commitText("hello", 1);
        assertEquals("", output(session));
        restored.commitText(" ", 1);
        assertEquals("hello ", output(session));
        assertEquals("", output(tui));
    }

    @Test
    public void discardedBindingProbeDoesNotCloseTheConnectionAndroidKeeps() {
        InputConnection bound = predictive();
        bound.setComposingText("old", 1);
        EditorInfo info = new EditorInfo();
        InputConnection probe = view.onCreateInputConnection(info);
        assertEquals(3, info.initialSelStart);
        assertEquals(3, info.initialSelEnd);
        assertEquals("", output(session));
        // Android's BOUND_TO_IMMS path can discard probe and continue using bound.
        assertTrue(bound.setComposingText("word", 1));
        assertEquals("word", probe.getTextBeforeCursor(100, 0).toString());
        assertTrue(bound.commitText("word ", 1));
        assertEquals("word ", output(session));
        assertTrue(bound.finishComposingText());
        assertEquals("", output(session));
    }

    @Test
    public void replacingBindingTransfersDraftWithoutFlushingOrClearingOnOldClose() {
        InputConnection old = predictive();
        old.setComposingText("mistake", 1);
        InputConnection fresh = view.onCreateInputConnection(new EditorInfo());
        ((TerminalInputConnection) old).closeConnection();
        assertEquals("", output(session));
        assertEquals("mistake", fresh.getTextBeforeCursor(100, 0).toString());
        assertFalse(old.commitText("late", 1));
        assertTrue(fresh.setComposingText("corrected", 1));
        assertTrue(fresh.finishComposingText());
        assertEquals("corrected", fresh.getTextBeforeCursor(100, 0).toString());
        assertEquals("", output(session));
        view.finishImeInput();
        assertEquals("corrected", output(session));
        assertEquals("", output(session));
    }

    @Test
    public void focusRetirementInvalidatesEveryConnectionIncludingDiscardedProbes() {
        InputConnection bound = predictive();
        bound.setComposingText("word", 1);
        InputConnection probe = view.onCreateInputConnection(new EditorInfo());
        view.onWindowFocusChanged(false);
        assertEquals("word", output(session));
        assertFalse(bound.commitText("late", 1));
        assertFalse(probe.commitText("late", 1));
        InputConnection fresh = predictive();
        // The same session is selected, but callbacks from the retired generation stay stale.
        assertFalse(bound.setComposingText("late", 1));
        assertTrue(fresh.commitText("fresh ", 1));
        assertEquals("fresh ", output(session));
    }

    @Test
    @Config(shadows = {NoPtyResize.class, RestartTracker.class})
    public void menuModeChangeRebindsImmediateKeysWhenWindowFocusReturns() {
        ReflectionHelpers.setField(view.mRenderer, "mFontWidth", 8f);
        ReflectionHelpers.setField(view.mRenderer, "mFontLineSpacing", 16);
        ReflectionHelpers.setField(view.mRenderer, "mFontLineSpacingAndAscent", 16);
        ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup().visible();
        activity.get().setContentView(view);
        view.setFocusableInTouchMode(true);
        assertTrue(view.requestFocus());
        activity.windowFocusChanged(true);
        ShadowLooper.idleMainLooper();
        InputConnection old = predictive();
        old.setComposingText("word", 1);
        RestartTracker.restarts = 0;

        // A context menu owns window focus while its action selects immediate Keys.
        activity.windowFocusChanged(false);
        client.paused = true;
        view.updateImeInputMode();
        ShadowLooper.idleMainLooper();
        assertEquals(0, RestartTracker.restarts);
        assertEquals("word", output(session));
        assertFalse(old.commitText("late", 1));

        activity.windowFocusChanged(true);
        ShadowLooper.idleMainLooper();
        assertEquals(1, RestartTracker.restarts);
        EditorInfo info = new EditorInfo();
        InputConnection keys = view.onCreateInputConnection(info);
        assertEquals(InputType.TYPE_NULL, info.inputType);
        assertTrue((info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0);
        assertTrue(keys.commitText("n", 1));
        assertEquals("n", output(session));
        activity.pause().stop().destroy();
    }

    @Test
    public void rapidPropertyReloadsRetireDraftAndRestoreDefaultMode() {
        for (int i = 0; i < 10; i++) {
            InputConnection old = predictive();
            old.setComposingText("word", 1);
            client.predictions = false;
            view.updateImeInputMode();
            assertEquals("word", output(session));
            assertFalse(old.commitText("late", 1));
            EditorInfo info = new EditorInfo();
            view.onCreateInputConnection(info).commitText("x", 1);
            assertEquals(InputType.TYPE_NULL, info.inputType);
            assertEquals("x", output(session));
        }
    }

    @Test
    public void diagnosticLoggingNeverIncludesPredictiveInputOrCodePoints() {
        InputConnection connection = predictive();
        view.setIsTerminalViewKeyLoggingEnabled(true);
        connection.setComposingText("PRIVATE", 1);
        connection.commitText("PRIVATE ", 1);
        terminalKey(KeyEvent.KEYCODE_A, 0);
        assertEquals("PRIVATE a", output(session));
        assertEquals("", client.logs.toString());
        screenSequence("\u001b[?1049h");
        view.onCreateInputConnection(new EditorInfo()).commitText("LOCAL", 1);
        terminalKey(KeyEvent.KEYCODE_B, 0);
        assertEquals("LOCALb", output(session));
        assertEquals("", client.logs.toString());
        view.setIsTerminalViewKeyLoggingEnabled(false);
    }

    @Test
    public void malformedUtf16CannotCrashPredictiveFlush() {
        InputConnection connection = predictive();
        connection.setComposingText("a\uD83Dx\uDC00", 1);
        connection.finishComposingText();
        view.finishImeInput();
        assertEquals("a\uFFFDx\uFFFD", output(session));
    }
}
