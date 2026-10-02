package com.termux.view;

import android.os.Build;
import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.TextAttribute;

import androidx.annotation.RequiresApi;

/**
 * An opt-in word editor in front of a terminal, NOT an editor of its screen or PTY echo.
 * Only unsent input is editable. This lets an IME revise a word without guessing whether
 * terminal backspaces would undo previously sent input (they need not do so in a TUI).
 * Words committed one character at a time are held until a boundary or an explicit finish.
 * No terminal output, history, or already sent input is returned to the IME.
 */
class TerminalInputConnection extends BaseInputConnection {

    static final int MAX_DRAFT_LENGTH = 4096;

    interface Target {
        boolean isValid();
        boolean hasModifiers();
        void writeText(CharSequence text, boolean applyModifiers);
        boolean sendKeyEvent(KeyEvent event);
        void onStateChanged();
        void restartInput();
    }

    private final Target mTarget;
    private final Editable mDraft;
    private boolean mOwnsDraft = true;
    private int mBatchDepth;
    private boolean mFinishRequested;
    private boolean mClosed;

    static TerminalInputConnection create(View view, Target target) {
        return create(view, target, new SpannableStringBuilder());
    }

    static TerminalInputConnection create(View view, Target target, Editable draft) {
        if (Build.VERSION.SDK_INT >= 34) return new Api34(view, target, draft);
        return new TerminalInputConnection(view, target, draft);
    }

    TerminalInputConnection(View view, Target target) {
        this(view, target, new SpannableStringBuilder());
    }

    TerminalInputConnection(View view, Target target, Editable draft) {
        super(view, true);
        mTarget = target;
        mDraft = draft;
        if (Selection.getSelectionStart(mDraft) < 0 || Selection.getSelectionEnd(mDraft) < 0)
            Selection.setSelection(mDraft, mDraft.length());
    }

    boolean isActive() {
        return !mClosed && mTarget.isValid();
    }

    /**
     * Android may request a connection during binding and then keep the existing one.
     * Both must see the same unsent editor, as TextView connections see one Editable.
     * If Android actually replaces the old binding, its close must not clear the draft
     * already handed to the new connection. The hosting view owns explicit retirement.
     */
    Editable handOffDraft() {
        mOwnsDraft = false;
        return mDraft;
    }

    @Override
    public Editable getEditable() {
        return mDraft;
    }

    @Override
    public boolean beginBatchEdit() {
        if (!isActive()) return false;
        mBatchDepth++;
        return true;
    }

    @Override
    public boolean endBatchEdit() {
        if (!isActive() || mBatchDepth == 0) return false;
        if (--mBatchDepth == 0) {
            drainReadyText();
            mTarget.onStateChanged();
        }
        return mBatchDepth > 0;
    }

    /** Modifier input must retain the existing terminal mapping, not enter the word editor. */
    private boolean sendModifiedText(CharSequence text) {
        if (!mTarget.hasModifiers()) return false;
        // Previously drafted letters were typed BEFORE the modifier was pressed. Do not let
        // flushing them consume a one-shot Ctrl/Alt/Shift/Fn button or apply that modifier.
        flushPendingText();
        mTarget.writeText(text, true);
        // A composing update may be followed by commitText for the same input. Retire this
        // connection before restarting, so that late callback cannot send a second Ctrl+C.
        closeConnection();
        mTarget.restartInput();
        return true;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (!isActive() || text == null) return false;
        if (sendModifiedText(text)) return true;
        beginBatchEdit();
        boolean result = super.commitText(text, newCursorPosition);
        if (mDraft.length() > MAX_DRAFT_LENGTH) mFinishRequested = true;
        endBatchEdit();
        return result;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (!isActive() || text == null) return false;
        if (sendModifiedText(text)) return true;
        int start = getComposingSpanStart(mDraft);
        int end = getComposingSpanEnd(mDraft);
        if (start < 0 || end < 0) {
            start = Selection.getSelectionStart(mDraft);
            end = Selection.getSelectionEnd(mDraft);
        }
        if ((long) mDraft.length() - Math.abs(end - start) + text.length() > MAX_DRAFT_LENGTH)
            return false;
        beginBatchEdit();
        boolean result = super.setComposingText(text, newCursorPosition);
        endBatchEdit();
        return result;
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        if (!isActive() || !validRange(start, end)) return false;
        beginBatchEdit();
        boolean result = super.setComposingRegion(start, end);
        endBatchEdit();
        return result;
    }

    @Override
    public boolean setSelection(int start, int end) {
        if (!isActive() || !validRange(start, end)) return false;
        beginBatchEdit();
        boolean result = super.setSelection(start, end);
        endBatchEdit();
        return result;
    }

    private boolean validRange(int start, int end) {
        return start >= 0 && end >= 0 && start <= mDraft.length() && end <= mDraft.length()
            && isCodePointBoundary(start) && isCodePointBoundary(end);
    }

    private boolean isCodePointBoundary(int offset) {
        return offset == 0 || offset == mDraft.length()
            || !Character.isHighSurrogate(mDraft.charAt(offset - 1))
            || !Character.isLowSurrogate(mDraft.charAt(offset));
    }

    /** An API 34 replacement is atomic: finishing composition must not flush its old text. */
    private boolean replaceDraft(int start, int end, CharSequence text, int newCursorPosition) {
        if (!isActive() || text == null || !validRange(start, end)) return false;
        if (sendModifiedText(text)) return true;
        beginBatchEdit();
        removeComposingSpans(mDraft);
        super.setSelection(start, end);
        boolean result = super.commitText(text, newCursorPosition);
        if (mDraft.length() > MAX_DRAFT_LENGTH) mFinishRequested = true;
        endBatchEdit();
        return result;
    }

    @RequiresApi(34)
    private static final class Api34 extends TerminalInputConnection {
        Api34(View view, Target target, Editable draft) {
            super(view, target, draft);
        }

        @Override
        public boolean replaceText(int start, int end, CharSequence text, int newCursorPosition,
                                   TextAttribute textAttribute) {
            return super.replaceDraft(start, end, text, newCursorPosition);
        }
    }

    @Override
    public boolean finishComposingText() {
        if (!isActive()) return false;
        beginBatchEdit();
        super.finishComposingText();
        mFinishRequested = true;
        endBatchEdit();
        return true;
    }

    /** Finish once even if an IME left a batch open when a terminal key or focus change arrived. */
    void flushPendingText() {
        if (!isActive()) return;
        removeComposingSpans(mDraft);
        mFinishRequested = true;
        drainReadyText();
        mTarget.onStateChanged();
    }

    @Override
    public void closeConnection() {
        if (mClosed) return;
        if (mOwnsDraft) flushPendingText();
        mClosed = true;
        mBatchDepth = 0;
        if (mOwnsDraft) {
            mDraft.clear();
            mDraft.clearSpans();
            Selection.setSelection(mDraft, 0);
        }
        // BaseInputConnection calls virtual finishComposingText(). An owning connection
        // already flushed; a former binding must leave the shared draft for its successor.
        // Our closed guard makes the superclass call a harmless no-op in either case.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) super.closeConnection();
    }

    private void drainReadyText() {
        int limit = Math.min(Selection.getSelectionStart(mDraft), Selection.getSelectionEnd(mDraft));
        int composingStart = getComposingSpanStart(mDraft);
        if (composingStart >= 0) limit = Math.min(limit, composingStart);
        int count = 0;
        if (mFinishRequested) {
            count = mDraft.length();
        } else {
            for (int i = 0; i < limit; ) {
                int codePoint = Character.codePointAt(mDraft, i);
                i += Character.charCount(codePoint);
                int type = Character.getType(codePoint);
                if (!Character.isLetterOrDigit(codePoint) && type != Character.NON_SPACING_MARK
                        && type != Character.COMBINING_SPACING_MARK && type != Character.ENCLOSING_MARK)
                    count = i;
            }
        }
        mFinishRequested = false;
        if (count == 0) return;
        String ready = mDraft.subSequence(0, count).toString();
        // Remove first: reentrant callbacks or close must never send the same draft twice.
        mDraft.delete(0, count);
        if (mDraft.length() == 0) {
            removeComposingSpans(mDraft);
            Selection.setSelection(mDraft, 0);
        }
        mTarget.writeText(ready, false);
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        return deleteAroundSelection(beforeLength, afterLength, false);
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        return deleteAroundSelection(beforeLength, afterLength, true);
    }

    private boolean deleteAroundSelection(int beforeLength, int afterLength, boolean codePoints) {
        if (!isActive() || beforeLength < 0 || afterLength < 0) return false;
        if (mDraft.length() == 0) {
            // Only ordinary single Backspace/Delete is meaningful without a local draft.
            // Reject speculative word replacements/deletions of already processed PTY input.
            if (beforeLength == 0 && afterLength == 0) return true;
            if (beforeLength + (long) afterLength != 1) return false;
            int key = beforeLength == 1 ? KeyEvent.KEYCODE_DEL : KeyEvent.KEYCODE_FORWARD_DEL;
            return mTarget.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
        }
        int a = Math.min(Selection.getSelectionStart(mDraft), Selection.getSelectionEnd(mDraft));
        int b = Math.max(Selection.getSelectionStart(mDraft), Selection.getSelectionEnd(mDraft));
        int start;
        int end;
        if (codePoints) {
            // Code-point deletion must do nothing on malformed surrogate input.
            if (!hasValidSurrogates(mDraft)) return false;
            int before = Math.min(beforeLength, Character.codePointCount(mDraft, 0, a));
            int after = Math.min(afterLength, Character.codePointCount(mDraft, b, mDraft.length()));
            start = Character.offsetByCodePoints(mDraft, a, -before);
            end = Character.offsetByCodePoints(mDraft, b, after);
        } else {
            start = Math.max(0, a - beforeLength);
            end = (int) Math.min(mDraft.length(), b + (long) afterLength);
            // Never leave half an emoji in the draft even for UTF-16 based keyboards.
            if (!isCodePointBoundary(start)) start--;
            if (!isCodePointBoundary(end)) end++;
        }
        beginBatchEdit();
        mDraft.delete(b, end);
        mDraft.delete(start, a);
        endBatchEdit();
        return true;
    }

    private static boolean hasValidSurrogates(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i == text.length() || !Character.isLowSurrogate(text.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (!isActive()) return false;
        int key = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN && !event.isCtrlPressed() && !event.isAltPressed()
                && !mTarget.hasModifiers()) {
            if (key == KeyEvent.KEYCODE_DEL || key == KeyEvent.KEYCODE_FORWARD_DEL) {
                if (Selection.getSelectionStart(mDraft) != Selection.getSelectionEnd(mDraft))
                    return commitText("", 1);
                return deleteAroundSelection(key == KeyEvent.KEYCODE_DEL ? 1 : 0,
                    key == KeyEvent.KEYCODE_FORWARD_DEL ? 1 : 0, true);
            }
            if (mDraft.length() > 0 && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                int cursor = Selection.getSelectionEnd(mDraft);
                int direction = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if ((direction < 0 && cursor > 0) || (direction > 0 && cursor < mDraft.length()))
                    return setSelection(Character.offsetByCodePoints(mDraft, cursor, direction),
                        Character.offsetByCodePoints(mDraft, cursor, direction));
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && !KeyEvent.isModifierKey(key)) {
            flushPendingText();
            boolean result = mTarget.sendKeyEvent(event);
            closeConnection();
            mTarget.restartInput();
            return result;
        }
        return mTarget.sendKeyEvent(event);
    }

    @Override
    public boolean performEditorAction(int actionCode) {
        if (!isActive()) return false;
        if (actionCode != EditorInfo.IME_ACTION_UNSPECIFIED && actionCode != EditorInfo.IME_ACTION_NONE
                && actionCode != EditorInfo.IME_ACTION_DONE && actionCode != EditorInfo.IME_ACTION_GO
                && actionCode != EditorInfo.IME_ACTION_SEND) return false;
        return sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        if (!isActive()) return null;
        // Do not opt into monitoring: fullscreen/extracted editing cannot represent a terminal.
        if ((flags & GET_EXTRACTED_TEXT_MONITOR) != 0) return null;
        ExtractedText result = new ExtractedText();
        result.text = mDraft.toString();
        result.startOffset = 0;
        result.partialStartOffset = result.partialEndOffset = -1;
        result.selectionStart = Selection.getSelectionStart(mDraft);
        result.selectionEnd = Selection.getSelectionEnd(mDraft);
        return result;
    }
}
