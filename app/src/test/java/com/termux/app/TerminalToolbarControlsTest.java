package com.termux.app;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.app.terminal.io.ExtraKeysDrawerListener;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class TerminalToolbarControlsTest {
    private TermuxActivity activity;
    private ExtraKeysView keys;
    private TermuxTerminalExtraKeys handler;
    private TerminalView terminal;
    private TerminalSession session;
    private int keyboardToggles;

    @Before
    public void setUp() {
        // Attach an activity context without starting the real service or bootstrap installer.
        activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        ReflectionHelpers.setField(activity, "mProperties", TermuxAppSharedProperties.init(activity));
        keys = new ExtraKeysView(activity, null);
        activity.setExtraKeysView(keys);
        terminal = new TerminalView(activity, null);
        ReflectionHelpers.setField(activity, "mTerminalView", terminal);
        terminal.setTerminalViewClient(new TermuxTerminalViewClientBase() {
            @Override public boolean readControlKey() { return Boolean.TRUE.equals(keys.readSpecialButton(SpecialButton.CTRL, true)); }
            @Override public boolean readAltKey() { return Boolean.TRUE.equals(keys.readSpecialButton(SpecialButton.ALT, true)); }
            @Override public boolean readShiftKey() { return Boolean.TRUE.equals(keys.readSpecialButton(SpecialButton.SHIFT, true)); }
        });
        TerminalSessionClient callback = (TerminalSessionClient) Proxy.newProxyInstance(
            TerminalSessionClient.class.getClassLoader(), new Class[]{TerminalSessionClient.class},
            (proxy, method, args) -> method.getReturnType() == Integer.class ? Integer.valueOf(0) : null);
        session = new TerminalSession("unused", "/", new String[0], new String[0], 100, callback);
        TerminalEmulator emulator = new TerminalEmulator(session, 80, 24, 8, 16, 100, callback);
        ReflectionHelpers.setField(session, "mEmulator", emulator);
        ReflectionHelpers.setField(session, "mShellPid", 1);
        terminal.mTermSession = session;
        terminal.mEmulator = emulator;
        TermuxTerminalViewClient client = new TermuxTerminalViewClient(activity, null) {
            @Override public void onToggleSoftKeyboardRequest() { keyboardToggles++; }
        };
        handler = new TermuxTerminalExtraKeys(activity, terminal, client, null);
        keys.setExtraKeysViewClient(handler);
        keys.reload(handler.getExtraKeysInfo(), 75);
        handler.updatePageModeButtons();
    }

    @Test
    public void pageModeStaysOnAndSendsRealPageSequencesUntilToggledOff() {
        click("PAGE");
        assertEquals("", output());
        click("UP");
        click("DOWN");
        click("UP");
        click("LEFT");
        assertEquals("\u001b[5~\u001b[6~\u001b[5~\u001b[D", output());
        assertTrue(handler.isPageMode());
        click("PAGE");
        click("UP");
        click("DOWN");
        assertEquals("\u001b[A\u001b[B", output());
        assertFalse(handler.isPageMode());
    }

    @Test
    public void pageTogglePreservesOneShotModifiersAndPageKeysUseThem() {
        click("CTRL");
        click("PAGE");
        assertTrue(keys.readSpecialButton(SpecialButton.CTRL, false));
        click("UP");
        assertEquals("\u001b[5;5~", output());
        assertFalse(keys.readSpecialButton(SpecialButton.CTRL, false));
        assertTrue(handler.isPageMode());
        click("SHIFT");
        click("PAGE");
        assertTrue(keys.readSpecialButton(SpecialButton.SHIFT, false));
        assertEquals("", output());
    }

    @Test
    public void pageColorsAndDescriptionsSurviveToolbarReloadAndRestore() {
        click("PAGE");
        assertEquals(keys.getButtonActiveTextColor(), button("PAGE").getCurrentTextColor());
        assertEquals(activity.getResources().getColor(R.color.extra_keys_page_arrow), button("UP").getCurrentTextColor());
        assertEquals("Page Down", button("DOWN").getContentDescription());
        keys.reload(handler.getExtraKeysInfo(), 75);
        handler.updatePageModeButtons();
        assertTrue(button("PAGE").isSelected());
        assertEquals(activity.getResources().getColor(R.color.extra_keys_page_arrow), button("DOWN").getCurrentTextColor());
        handler = new TermuxTerminalExtraKeys(activity, terminal, null, null);
        handler.setPageMode(true);
        assertTrue(button("PAGE").isSelected());
        handler.setPageMode(false);
        assertEquals(keys.getButtonTextColor(), button("UP").getCurrentTextColor());
    }

    @Test
    public void customMacrosAndExplicitPageKeysRetainTheirMeaning() throws Exception {
        handler.setPageMode(true);
        ExtraKeysInfo custom = new ExtraKeysInfo("[[{macro:'UP'},'PGUP','PGDN']]", "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        for (ExtraKeyButton info : custom.getMatrix()[0]) handler.onExtraKeyButtonClick(null, info, null);
        assertEquals("\u001b[A\u001b[5~\u001b[6~", output());
    }

    @Test
    public void keyboardKeyTogglesWithoutSendingTerminalInputOrClearingPage() {
        click("PAGE");
        click("KEYBOARD");
        click("KEYBOARD");
        assertEquals(2, keyboardToggles);
        assertTrue(handler.isPageMode());
        assertEquals("", output());
    }

    @Test
    @Config(qualifiers = "notnight")
    public void drawerKeyboardHasReadableTextAndBorderInLightTheme() {
        assertDrawerKeyboardContrast();
    }

    @Test
    @Config(qualifiers = "night")
    public void drawerKeyboardHasReadableTextAndBorderInDarkTheme() {
        assertDrawerKeyboardContrast();
    }

    private void assertDrawerKeyboardContrast() {
        View layout = LayoutInflater.from(activity).inflate(R.layout.activity_termux, null, false);
        MaterialButton button = layout.findViewById(R.id.toggle_keyboard_button);
        int background = button.getBackgroundTintList().getDefaultColor();
        assertTrue("Keyboard text must contrast with its fill",
            ColorUtils.calculateContrast(button.getCurrentTextColor(), background) >= 4.5);
        assertTrue("Keyboard border must be visible", button.getStrokeWidth() > 0);
        assertTrue(ColorUtils.calculateContrast(button.getStrokeColor().getDefaultColor(), background) >= 3);
        assertEquals(Math.round(56 * activity.getResources().getDisplayMetrics().density), button.getLayoutParams().height);
    }

    @Test
    public void drawerBlocksKeysDuringOpeningAndUntilClosingHasFinished() {
        DrawerLayout drawer = new DrawerLayout(activity);
        drawer.addView(new FrameLayout(activity), new DrawerLayout.LayoutParams(-1, -1));
        FrameLayout panel = new FrameLayout(activity);
        DrawerLayout.LayoutParams params = new DrawerLayout.LayoutParams(240, -1);
        params.gravity = Gravity.START;
        drawer.addView(panel, params);
        ExtraKeysDrawerListener guard = new ExtraKeysDrawerListener(drawer);
        drawer.addDrawerListener(guard);
        guard.setExtraKeysView(keys);
        click("CTRL");
        guard.onDrawerStateChanged(DrawerLayout.STATE_DRAGGING);
        assertFalse(keys.isInputEnabled());
        click("ESC");
        click("PAGE");
        assertEquals("", output());
        assertFalse(handler.isPageMode());
        assertTrue(keys.readSpecialButton(SpecialButton.CTRL, false));
        drawer.openDrawer(panel, false);
        guard.onDrawerStateChanged(DrawerLayout.STATE_IDLE);
        assertFalse(keys.isInputEnabled());
        keys.reload(handler.getExtraKeysInfo(), 75);
        assertFalse(button("ESC").isEnabled());
        guard.onDrawerStateChanged(DrawerLayout.STATE_SETTLING);
        drawer.closeDrawer(panel, false);
        guard.onDrawerClosed(panel);
        assertFalse(keys.isInputEnabled());
        guard.onDrawerStateChanged(DrawerLayout.STATE_IDLE);
        assertTrue(keys.isInputEnabled());
        click("ESC");
        assertEquals("\u001b", output());
    }

    @Test
    public void cancelingHeldArrowDropsQueuedRepeatsAndItsRelease() throws Exception {
        keys.setLongPressTimeout(200);
        ExtraKeyButton up = info("UP");
        keys.startScheduledExecutors(button("UP"), up, button("UP"));
        ScheduledExecutorService executor = ReflectionHelpers.getField(keys, "mScheduledExecutor");
        CountDownLatch queued = new CountDownLatch(1);
        executor.schedule(queued::countDown, 225, TimeUnit.MILLISECONDS);
        assertTrue(queued.await(2, TimeUnit.SECONDS));
        keys.setInputEnabled(false);
        keys.setInputEnabled(true);
        MotionEvent release = MotionEvent.obtain(0, 250, MotionEvent.ACTION_UP, 0, 0, 0);
        assertTrue(keys.dispatchTouchEvent(release));
        release.recycle();
        ShadowLooper.shadowMainLooper().idle();
        assertEquals("", output());
        click("UP");
        assertEquals("\u001b[A", output());
    }

    private ExtraKeyButton info(String key) {
        for (ExtraKeyButton[] row : handler.getExtraKeysInfo().getMatrix())
            for (ExtraKeyButton info : row) if (key.equals(info.getKey())) return info;
        throw new AssertionError("Missing key: " + key);
    }

    private MaterialButton button(String key) {
        int index = 0;
        for (ExtraKeyButton[] row : handler.getExtraKeysInfo().getMatrix()) {
            for (ExtraKeyButton info : row) {
                if (key.equals(info.getKey())) return (MaterialButton) keys.getChildAt(index);
                index++;
            }
        }
        throw new AssertionError("Missing button: " + key);
    }

    private void click(String key) { button(key).performClick(); }

    private String output() {
        Object queue = ReflectionHelpers.getField(session, "mTerminalToProcessIOQueue");
        byte[] bytes = new byte[4096];
        int count = ReflectionHelpers.callInstanceMethod(queue, "read",
            ReflectionHelpers.ClassParameter.from(byte[].class, bytes),
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
        return new String(bytes, 0, count, StandardCharsets.UTF_8);
    }
}
