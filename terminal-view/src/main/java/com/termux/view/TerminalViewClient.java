package com.termux.view;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.termux.terminal.TerminalSession;

/**
 * The interface for communication between {@link TerminalView} and its client. It allows for getting
 * various  configuration options from the client and for sending back data to the client like logs,
 * key events, both hardware and IME (which makes it different from that available with
 * {@link View#setOnKeyListener(View.OnKeyListener)}, etc. It must be set for the
 * {@link TerminalView} through {@link TerminalView#setTerminalViewClient(TerminalViewClient)}.
 */
public interface TerminalViewClient {

    /**
     * Callback function on scale events according to {@link ScaleGestureDetector#getScaleFactor()}.
     */
    float onScale(float scale);



    /**
     * On a single tap on the terminal if terminal mouse reporting not enabled.
     */
    void onSingleTapUp(MotionEvent e);

    boolean shouldBackButtonBeMappedToEscape();

    boolean shouldEnforceCharBasedInput();

    /** Opt-in local word editing for predictive IMEs. Character-based input takes precedence. */
    default boolean shouldEnableImeSuggestions() {
        return false;
    }

    /** A host may offer an explicit Text/Keys choice without changing the opt-in property. */
    default boolean shouldPauseImeSuggestions() { return false; }

    /** Full-screen input remains immediate unless the host explicitly opts into text entry. */
    default boolean shouldEnableImeSuggestionsInAlternateScreen() { return false; }

    /** Notify the host after a code point actually reaches this session, not a local shortcut. */
    default void onTerminalCodePointSent(TerminalSession session, int codePoint, boolean altDown) {}

    /** Refresh a host's input-mode control after property, screen or session changes. */
    default void onImeInputModeChanged() {}

    /** Peek at terminal modifiers without consuming one-shot extra-key state. */
    default boolean hasTerminalInputModifiers() {
        return false;
    }

    boolean shouldUseCtrlSpaceWorkaround();

    boolean isTerminalViewSelected();



    void copyModeChanged(boolean copyMode);



    boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session);

    boolean onKeyUp(int keyCode, KeyEvent e);

    boolean onLongPress(MotionEvent event);



    boolean readControlKey();

    boolean readAltKey();

    boolean readShiftKey();

    boolean readFnKey();



    boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session);


    void onEmulatorSet();


    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);

}
