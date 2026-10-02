package systems.simmons.termuximetest;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/** Standalone hardware fixture; never accesses com.termux data, properties, services or bootstrap. */
public class MainActivity extends Activity implements TerminalSessionClient {
    private static final String[] FIXTURES = {"hello world", "one two three", "café 😀 中文", "hello, world!", "Hello World"};
    private TerminalView terminal;
    private ExtraKeysView extraKeys;
    private final ArrayList<TerminalSession> sessions = new ArrayList<>();
    private int selected;
    private boolean predictive;
    private boolean charBased;
    private boolean twoRows;
    private TextView status;
    private LinearLayout root;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        predictive = getPreferences(0).getBoolean("predictive", false);
        charBased = getPreferences(0).getBoolean("charBased", false);
        twoRows = getPreferences(0).getBoolean("twoRows", false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setPadding(dp(8), dp(4), dp(8), dp(4));
        root.addView(status);

        LinearLayout controls = row();
        button(controls, "Predict", () -> { predictive = !predictive; applyMode(); });
        button(controls, "Char", () -> { charBased = !charBased; applyMode(); });
        button(controls, "Rows", () -> { twoRows = !twoRows; reloadKeys(); applyMode(); });
        button(controls, "Keyboard", this::toggleKeyboard);
        LinearLayout sessionControls = row();
        button(sessionControls, "Probe", this::chooseProbe);
        button(sessionControls, "Shell", () -> startSession(-1));
        button(sessionControls, "Next", this::nextSession);
        button(sessionControls, "New", () -> startSession(0));

        terminal = new TerminalView(this, null);
        // Match activity_termux.xml: custom terminal views must accept touch-mode focus.
        terminal.setFocusableInTouchMode(true);
        terminal.setTextSize(dp(14));
        terminal.setTypeface(Typeface.MONOSPACE);
        terminal.setIsTerminalViewKeyLoggingEnabled(false);
        terminal.setTerminalViewClient(new TermuxTerminalViewClientBase() {
            public boolean shouldEnableImeSuggestions() { return predictive; }
            public boolean shouldEnforceCharBasedInput() { return charBased; }
            public boolean readControlKey() { return read(SpecialButton.CTRL, true); }
            public boolean readAltKey() { return read(SpecialButton.ALT, true); }
            public boolean readShiftKey() { return read(SpecialButton.SHIFT, true); }
            public boolean readFnKey() { return read(SpecialButton.FN, true); }
            public boolean hasTerminalInputModifiers() {
                return read(SpecialButton.CTRL, false) || read(SpecialButton.ALT, false)
                    || read(SpecialButton.SHIFT, false) || read(SpecialButton.FN, false);
            }
        });
        root.addView(terminal, new LinearLayout.LayoutParams(-1, 0, 1));
        extraKeys = new ExtraKeysView(this, null);
        extraKeys.setExtraKeysViewClient(new TerminalExtraKeys(terminal));
        root.addView(extraKeys);
        setContentView(root);
        reloadKeys();

        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof ArrayList) {
            for (Object entry : (ArrayList<?>) retained) {
                TerminalSession session = (TerminalSession) entry;
                session.updateTerminalSessionClient(this);
                sessions.add(session);
            }
        }
        if (sessions.isEmpty()) startSession(0);
        else { selected = Math.min(getPreferences(0).getInt("selected", 0), sessions.size() - 1); attach(); }
        terminal.requestFocus();
        terminal.postDelayed(this::showKeyboard, 300);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        root.addView(row, new LinearLayout.LayoutParams(-1, dp(40)));
        return row;
    }

    private void button(LinearLayout row, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setPadding(0, 0, 0, 0);
        button.setFocusable(false);
        button.setOnClickListener(view -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private boolean read(SpecialButton button, boolean consume) {
        return extraKeys != null && Boolean.TRUE.equals(extraKeys.readSpecialButton(button, consume));
    }

    private void applyMode() {
        getPreferences(0).edit().putBoolean("predictive", predictive).putBoolean("charBased", charBased)
            .putBoolean("twoRows", twoRows).apply();
        terminal.updateImeInputMode();
        updateStatus();
        terminal.requestFocus();
        showKeyboard();
    }

    private void updateStatus() {
        status.setText("IME test · " + (predictive ? "Predict ON" : "Predict OFF")
            + (charBased ? " · Char ON (wins)" : " · Char OFF")
            + " · Session " + (selected + 1) + " · Synthetic input only");
    }

    private void reloadKeys() {
        String layout = twoRows
            ? "[['ESC','TAB','CTRL','ALT','UP','HOME','END'],['LEFT','DOWN','RIGHT',{key:'HOME',popup:'END'},{macro:'CTRL c',display:'C-c'},'custom']]"
            : "[['ESC','TAB','CTRL','ALT','LEFT','DOWN','UP','RIGHT']]";
        try {
            ExtraKeysInfo info = new ExtraKeysInfo(layout, "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            int height = dp(twoRows ? 88 : 48);
            extraKeys.setLayoutParams(new LinearLayout.LayoutParams(-1, height));
            extraKeys.reload(info, height);
        } catch (Exception error) { throw new IllegalStateException("Fixture extra-key layout is invalid", error); }
    }

    private void chooseProbe() {
        new AlertDialog.Builder(this).setTitle("Synthetic input fixture")
            .setItems(FIXTURES, (dialog, which) -> startSession(which)).show();
    }

    private void startSession(int fixture) {
        File home = new File(getFilesDir(), "home");
        if (!home.isDirectory() && !home.mkdirs()) throw new IllegalStateException("Cannot create fixture home");
        String[] args;
        if (fixture < 0) args = new String[]{"sh", "-i"};
        else {
            File script = new File(getFilesDir(), "probe.sh");
            try (InputStream input = getAssets().open("probe.sh"); FileOutputStream output = new FileOutputStream(script)) {
                byte[] buffer = new byte[4096];
                for (int count; (count = input.read(buffer)) != -1; ) output.write(buffer, 0, count);
            } catch (Exception error) { throw new IllegalStateException("Cannot copy fixture script", error); }
            args = new String[]{"sh", script.getAbsolutePath(), FIXTURES[fixture],
                new File(getFilesDir(), "results.txt").getAbsolutePath(), Integer.toString(fixture)};
        }
        TerminalSession session = new TerminalSession("/system/bin/sh", home.getAbsolutePath(), args,
            new String[]{"PATH=/system/bin", "HOME=" + home.getAbsolutePath(), "TERM=xterm-256color",
                "LANG=C.UTF-8", "PS1=> ", "TMPDIR=" + getCacheDir().getAbsolutePath()}, 2000, this);
        session.mSessionName = fixture < 0 ? "Android system shell" : "Synthetic probe " + fixture;
        sessions.add(session);
        selected = sessions.size() - 1;
        attach();
        showKeyboard();
    }

    private void nextSession() {
        if (sessions.isEmpty()) return;
        selected = (selected + 1) % sessions.size();
        attach();
        showKeyboard();
    }

    private void attach() {
        terminal.attachSession(sessions.get(selected));
        getPreferences(0).edit().putInt("selected", selected).apply();
        updateStatus();
    }

    private void showKeyboard() {
        terminal.requestFocus();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT);
    }

    private void toggleKeyboard() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).toggleSoftInput(0, 0);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override
    public Object onRetainNonConfigurationInstance() { return sessions; }

    @Override
    protected void onDestroy() {
        terminal.finishImeInput();
        if (!isChangingConfigurations()) for (TerminalSession session : sessions) session.finishIfRunning();
        super.onDestroy();
    }

    public void onTextChanged(TerminalSession session) { if (session == terminal.getCurrentSession()) terminal.onScreenUpdated(); }
    public void onTitleChanged(TerminalSession session) {}
    public void onSessionFinished(TerminalSession session) { if (session == terminal.getCurrentSession()) terminal.onScreenUpdated(); }
    public void onCopyTextToClipboard(TerminalSession session, String text) {}
    public void onPasteTextFromClipboard(TerminalSession session) {}
    public void onBell(TerminalSession session) {}
    public void onColorsChanged(TerminalSession session) { terminal.invalidate(); }
    public void onTerminalCursorStateChange(boolean state) { terminal.invalidate(); }
    public void setTerminalShellPid(TerminalSession session, int pid) {}
    public Integer getTerminalCursorStyle() { return 0; }
    public void logError(String tag, String message) {}
    public void logWarn(String tag, String message) {}
    public void logInfo(String tag, String message) {}
    public void logDebug(String tag, String message) {}
    public void logVerbose(String tag, String message) {}
    public void logStackTraceWithMessage(String tag, String message, Exception error) {}
    public void logStackTrace(String tag, Exception error) {}
}
