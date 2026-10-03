package com.termux.view;

import android.text.Selection;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 36}, manifest = Config.NONE)
public class TerminalInputConnectionTest {

    static class FakeTerminal implements TerminalInputConnection.Target {
        final StringBuilder output = new StringBuilder();
        final List<Integer> keys = new ArrayList<>();
        boolean valid = true;
        boolean modifiers;
        int restarts;
        int updates;

        public boolean isValid() { return valid; }
        public boolean hasModifiers() { return modifiers; }
        public void writeText(CharSequence text, boolean applyModifiers) {
            if (applyModifiers && modifiers) {
                output.append((char) (text.charAt(0) - 'a' + 1));
                modifiers = false;
            } else {
                output.append(text);
            }
        }
        public boolean sendKeyEvent(KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) keys.add(event.getKeyCode());
            return true;
        }
        public void onStateChanged() { updates++; }
        public void restartInput() { restarts++; }
    }

    private FakeTerminal terminal;
    private TerminalInputConnection connection;

    @Before
    public void setUp() {
        terminal = new FakeTerminal();
        connection = TerminalInputConnection.create(new View(RuntimeEnvironment.getApplication()), terminal);
    }

    private String draft() { return connection.getEditable().toString(); }
    private void key(int key) { assertTrue(connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key))); }
    private void assertOutput(String expected) { assertEquals(expected, terminal.output.toString()); }

    @Test
    public void singleCharacterCommitsRemainReplaceableUntilWordBoundary() {
        for (char c : "hello".toCharArray()) assertTrue(connection.commitText(String.valueOf(c), 1));
        assertOutput("");
        assertEquals("hello", connection.getTextBeforeCursor(100, 0).toString());
        connection.commitText(" ", 1);
        assertOutput("hello ");
        assertEquals("", draft());
    }

    @Test
    public void composingUpdatesAndSuggestionCommitSendExactlyOnce() {
        connection.setComposingText("h", 1);
        connection.setComposingText("he", 1);
        connection.setComposingText("helo", 1);
        assertOutput("");
        connection.commitText("hello ", 1);
        connection.finishComposingText();
        connection.finishComposingText();
        assertOutput("hello ");
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(connection.getEditable()));
    }

    @Test
    public void finishCompositionKeepsTokenEditableUntilCloseSendsItOnce() {
        connection.setComposingText("café", 1);
        connection.finishComposingText();
        assertOutput("");
        assertEquals("café", draft());
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(connection.getEditable()));
        connection.closeConnection();
        assertOutput("café");
        assertFalse(connection.commitText("late", 1));
    }

    @Test
    public void finishThenReplaceInBatchDoesNotSendOldWord() {
        connection.setComposingText("teh", 1);
        connection.beginBatchEdit();
        connection.finishComposingText();
        connection.setSelection(0, 3);
        connection.commitText("the ", 1);
        assertOutput("");
        assertFalse(connection.endBatchEdit());
        assertOutput("the ");
    }

    @Test
    public void nestedBatchKeepsSpaceAndReplacementAtomic() {
        connection.beginBatchEdit();
        connection.commitText("teh ", 1);
        connection.beginBatchEdit();
        connection.setComposingRegion(0, 3);
        connection.commitText("the", 1);
        assertTrue(connection.endBatchEdit());
        assertOutput("");
        connection.setSelection(4, 4);
        connection.endBatchEdit();
        assertOutput("the ");
    }

    @Test
    public void glideCommitsAndConsecutiveCommandsDoNotRepeatFirstWord() {
        connection.setComposingText("hello", 1);
        connection.commitText("hello ", 1);
        connection.setComposingText("world", 1);
        connection.commitText("world", 1);
        connection.commitText("!\n", 1);
        connection.commitText("echo 123\n", 1);
        assertOutput("hello world!\necho 123\n");
        assertEquals("", draft());
    }

    @Test
    public void rejectsStaleSelectionInsteadOfReplacingTerminalEcho() {
        connection.commitText("sent ", 1);
        assertFalse(connection.setSelection(0, 4));
        assertFalse(connection.setComposingRegion(0, 4));
        assertFalse(connection.deleteSurroundingText(4, 0));
        assertOutput("sent ");
    }

    @Test
    public void selectedTextAndReversedSelectionAreLocalOnly() {
        connection.commitText("abcdef", 1);
        assertTrue(connection.setSelection(4, 2));
        assertEquals("cd", connection.getSelectedText(0).toString());
        assertEquals("ab", connection.getTextBeforeCursor(50, 0).toString());
        assertEquals("ef", connection.getTextAfterCursor(50, 0).toString());
        connection.commitText("XY", 1);
        connection.finishComposingText();
        connection.flushPendingText();
        assertOutput("abXYef");
    }

    @Test
    public void deletionAroundSelectionExcludesSelectedText() {
        connection.setComposingText("abcdef", 1);
        connection.setSelection(2, 4);
        connection.deleteSurroundingText(1, 1);
        assertEquals("acdf", draft());
        assertEquals("cd", connection.getSelectedText(0).toString());
        connection.finishComposingText();
        connection.flushPendingText();
        assertOutput("acdf");
    }

    @Test
    public void backspaceTapAndHoldEditDraftBeforeSendingTerminalKeys() {
        connection.commitText("abcd", 1);
        for (int i = 0; i < 4; i++) key(KeyEvent.KEYCODE_DEL);
        assertEquals("", draft());
        assertTrue(terminal.keys.isEmpty());
        key(KeyEvent.KEYCODE_DEL);
        assertEquals(Integer.valueOf(KeyEvent.KEYCODE_DEL), terminal.keys.get(0));
        assertOutput("");
    }

    @Test
    public void codePointDeletionPreservesEmojiAndAccentedCharacters() {
        connection.setComposingText("é😀中x", 1);
        assertTrue(connection.deleteSurroundingTextInCodePoints(2, 0));
        assertEquals("é😀", draft());
        assertTrue(connection.deleteSurroundingTextInCodePoints(1, 0));
        assertEquals("é", draft());
        connection.finishComposingText();
        connection.flushPendingText();
        assertOutput("é");
    }

    @Test
    public void utf16DeletionDoesNotSplitSurrogatePairs() {
        connection.setComposingText("a😀b", 1);
        connection.setSelection(3, 3);
        assertFalse(connection.setSelection(2, 2));
        connection.deleteSurroundingText(1, 0);
        assertEquals("ab", draft());
        connection.finishComposingText();
        connection.flushPendingText();
        assertOutput("ab");
    }

    @Test
    public void emojiCommitFinishAndUtf16BackspaceRemainLocal() {
        connection.commitText("café ", 1);
        connection.commitText("😀", 1);
        connection.finishComposingText();
        assertOutput("café ");
        assertEquals("😀", connection.getTextBeforeCursor(100, 0).toString());
        assertTrue(connection.deleteSurroundingText(2, 0));
        assertEquals("", draft());
        assertTrue(terminal.keys.isEmpty());
        connection.commitText("😀", 1);
        connection.finishComposingText();
        key(KeyEvent.KEYCODE_ENTER);
        assertOutput("café 😀");
        assertEquals(1, terminal.keys.size());
        assertEquals(Integer.valueOf(KeyEvent.KEYCODE_ENTER), terminal.keys.get(0));
    }

    @Test
    public void finishThenReplaceOutsideBatchAndEmojiJoinersStayEditable() {
        connection.setComposingText("teh", 1);
        connection.finishComposingText();
        assertTrue(connection.setSelection(0, 3));
        connection.commitText("the ", 1);
        assertOutput("the ");
        connection.commitText("👩🏽\u200D💻", 1);
        connection.finishComposingText();
        assertEquals("👩🏽\u200D💻", draft());
        assertTrue(connection.deleteSurroundingText(7, 0));
        assertEquals("", draft());
        assertTrue(terminal.keys.isEmpty());
        connection.commitText("👩🏽\u200D💻!", 1);
        assertOutput("the 👩🏽\u200D💻!");
    }

    @Test
    public void malformedSurrogatesAndNegativeDeletionDoNotMutateText() {
        connection.setComposingText("a\uD83Db", 1);
        assertFalse(connection.deleteSurroundingTextInCodePoints(1, 0));
        assertFalse(connection.deleteSurroundingText(-1, 0));
        assertEquals("a\uD83Db", draft());
        assertOutput("");
    }

    @Test
    public void cjkCompositionAndPreEditCursorStayInEditable() {
        connection.setComposingText("ㄱ", 1);
        connection.setComposingText("가", 1);
        connection.setComposingText("간字", 1);
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(1, Selection.getSelectionEnd(connection.getEditable()));
        assertTrue(terminal.keys.isEmpty());
        assertOutput("");
        connection.setSelection(2, 2);
        connection.commitText("간字 ", 1);
        assertOutput("간字 ");
    }

    @Test
    public void modifiersDoNotAbortCompositionButModifiedInputDoesNotDuplicate() {
        connection.setComposingText("abc", 1);
        key(KeyEvent.KEYCODE_SHIFT_LEFT);
        assertEquals("abc", draft());
        terminal.modifiers = true;
        connection.setComposingText("c", 1);
        assertFalse(connection.commitText("c", 1));
        assertOutput("abc\u0003");
        assertEquals(1, terminal.restarts);
    }

    @Test
    public void terminalKeysFinalizeDraftAndRetireConnection() {
        for (int key : new int[]{KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_TAB,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END}) {
            setUp();
            connection.setComposingText("draft", 1);
            key(key);
            assertOutput("draft");
            assertEquals(Integer.valueOf(key), terminal.keys.get(0));
            assertFalse(connection.commitText("late", 1));
        }
    }

    @Test
    public void enterAndEditorActionsSendPendingWordBeforeEnter() {
        connection.setComposingText("echo", 1);
        assertTrue(connection.performEditorAction(EditorInfo.IME_ACTION_DONE));
        assertOutput("echo");
        assertEquals(Integer.valueOf(KeyEvent.KEYCODE_ENTER), terminal.keys.get(0));
    }

    @Test
    public void textEnterFinalizesEntireDraftAtMidCursorEvenInBatch() {
        for (String enter : new String[]{"\n", "\r"}) {
            setUp();
            connection.setComposingText("가", 1);
            connection.finishComposingText();
            connection.setSelection(0, 0);
            connection.setComposingText("나", 1);
            connection.finishComposingText();
            assertEquals("나가", draft());
            assertEquals(1, Selection.getSelectionEnd(connection.getEditable()));
            connection.beginBatchEdit();
            assertTrue(connection.commitText(enter, 1));
            assertOutput("나가");
            assertEquals("", draft());
            assertEquals(1, terminal.keys.size());
            assertEquals(Integer.valueOf(KeyEvent.KEYCODE_ENTER), terminal.keys.get(0));
            assertFalse(connection.endBatchEdit());
            assertFalse(connection.commitText("late", 1));
            connection.flushPendingText();
            assertOutput("나가");
        }
    }

    @Test
    public void closeFinishesOpenBatchOnceAndRejectsAllLaterMutation() {
        connection.beginBatchEdit();
        connection.setComposingText("pending", 1);
        connection.closeConnection();
        connection.closeConnection();
        assertOutput("pending");
        assertEquals("", draft());
        assertFalse(connection.beginBatchEdit());
        assertFalse(connection.endBatchEdit());
        assertFalse(connection.setComposingText("late", 1));
        assertFalse(connection.finishComposingText());
        assertFalse(connection.setSelection(0, 0));
        assertFalse(connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
    }

    @Test
    public void invalidatedTargetNeverReceivesStaleInput() {
        connection.setComposingText("draft", 1);
        terminal.valid = false;
        assertFalse(connection.commitText("late", 1));
        connection.closeConnection();
        assertOutput("");
        assertEquals("", draft());
    }

    @Test
    public void longWordIsBoundedAndCompositionOverflowIsAtomic() {
        String word = new String(new char[4096]).replace('\0', 'x');
        assertTrue(connection.setComposingText(word, 1));
        assertFalse(connection.setComposingText(word + "x", 1));
        assertEquals(word, draft());
        connection.commitText(word + "x", 1);
        assertOutput(word + "x");
        assertEquals("", draft());
    }

    @Test
    @Config(sdk = {34, 36})
    public void api34ReplaceTextRevisesCurrentWordWithoutPtyBackspaces() {
        connection.setComposingText("teh", 1);
        assertTrue(connection.replaceText(3, 0, "the ", 1, null));
        assertOutput("the ");
        assertTrue(terminal.keys.isEmpty());
        assertFalse(connection.replaceText(0, 3, "old", 1, null));
        connection.commitText("next", 1);
        connection.replaceText(0, 4, "new", 1, null);
        connection.finishComposingText();
        connection.flushPendingText();
        assertOutput("the new");
    }

    @Test
    @Config(sdk = {34, 36})
    public void api34ReplacementWithCtrlFinalizesPriorDraftWithoutApplyingModifierToIt() {
        connection.setComposingText("draft", 1);
        terminal.modifiers = true;
        assertTrue(connection.replaceText(0, 5, "c", 1, null));
        assertOutput("draft\u0003");
        assertFalse(connection.commitText("c", 1));
        assertEquals(1, terminal.restarts);
    }

    @Test
    public void extractedTextContainsOnlyDraftAndUnsupportedMonitorIsRejected() {
        connection.commitText("already sent ", 1);
        connection.setComposingText("draft", 1);
        assertEquals("draft", connection.getExtractedText(null, 0).text.toString());
        assertNull(connection.getExtractedText(null, InputConnection.GET_EXTRACTED_TEXT_MONITOR));
        assertFalse(connection.requestCursorUpdates(1));
    }
}
