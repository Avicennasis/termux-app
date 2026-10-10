package com.termux.app;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.termux.R;
import com.termux.app.terminal.SessionRowView;
import com.termux.app.terminal.io.ExtraKeysDrawerListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w375dp-h800dp-notnight-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class AvicUiPresentationTest {
    private Context context;

    @Before public void setUp() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        context = activity;
    }

    @Test public void lightPaletteKeepsTextAndActionsReadable() { assertPaletteContrast(); }

    @Test @Config(qualifiers = "w375dp-h800dp-night-mdpi")
    public void darkPaletteKeepsTextAndActionsReadable() { assertPaletteContrast(); }

    private void assertPaletteContrast() {
        int surface = color(R.color.avic_surface), raised = color(R.color.avic_raised);
        int selected = color(R.color.avic_selected), canvas = color(R.color.avic_canvas);
        for (int background : new int[]{surface, raised, selected, canvas}) {
            assertTrue(ColorUtils.calculateContrast(color(R.color.avic_text), background) >= 4.5);
            assertTrue(ColorUtils.calculateContrast(color(R.color.avic_muted), background) >= 4.5);
        }
        assertTrue(ColorUtils.calculateContrast(color(R.color.avic_teal), raised) >= 4.5);
        assertTrue(ColorUtils.calculateContrast(color(R.color.avic_line), raised) >= 3);
        assertTrue(ColorUtils.calculateContrast(color(R.color.avic_on_accent), color(R.color.avic_accent)) >= 4.5);
        assertTrue(ColorUtils.calculateContrast(color(R.color.avic_error), selected) >= 4.5);
        assertTrue(ColorUtils.calculateContrast(color(R.color.avic_amber), raised) >= 4.5);
        assertTrue(ColorUtils.calculateContrast(color(R.color.avic_toolbar_muted), color(R.color.avic_toolbar_surface)) >= 4.5);
        assertTrue(ColorUtils.calculateContrast(color(R.color.avic_toolbar_active), color(R.color.avic_toolbar_surface)) >= 4.5);
        assertTrue(ColorUtils.calculateContrast(color(R.color.avic_toolbar_active), color(R.color.avic_toolbar_pressed)) >= 4.5);
    }

    @Test public void recycledSessionRowResetsExitStyleAndKeepsNameAndTitle() {
        SessionRowView row = row();
        row.bind(2, "Build", "failed command", false, 1);
        assertEquals("Exited · status 1 · failed command", text(row, R.id.session_subtitle));
        assertEquals(color(R.color.avic_error), ((TextView)row.findViewById(R.id.session_title)).getCurrentTextColor());
        row.bind(3, "Shell", "~/workspace", true, 0);
        assertEquals("3", text(row, R.id.session_number));
        assertEquals("Shell", text(row, R.id.session_title));
        assertEquals("Running · ~/workspace", text(row, R.id.session_subtitle));
        assertEquals(color(R.color.avic_text), ((TextView)row.findViewById(R.id.session_title)).getCurrentTextColor());
        assertTrue(row.getContentDescription().toString().contains("~/workspace"));
    }

    @Test public void sessionSelectionHasAccessibleStateAsWellAsColor() {
        SessionRowView row = row();
        row.bind(1, "Agent", "Working", true, 0);
        row.setChecked(true);
        assertTrue(row.isActivated()); assertTrue(row.isChecked());
        assertEquals(context.getString(R.string.avic_session_current), ViewCompat.getStateDescription(row));
        AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
        row.onInitializeAccessibilityNodeInfo(node);
        assertTrue(node.isCheckable()); assertTrue(node.isChecked());
        row.setSelected(true); // ListView owns keyboard navigation independently of current-session state.
        row.setChecked(false);
        assertFalse(row.isActivated()); assertTrue(row.isSelected()); assertNull(ViewCompat.getStateDescription(row));
        row.onInitializeAccessibilityNodeInfo(node);
        assertFalse(node.isChecked());
        node.recycle();
    }

    @Test public void unnamedSessionShowsTitleOrShellFallback() {
        SessionRowView row = row();
        row.bind(1, null, "remote title", true, 0);
        assertEquals("remote title", text(row, R.id.session_title));
        row.bind(1, null, null, true, 0);
        assertEquals(context.getString(R.string.avic_session_shell), text(row, R.id.session_title));
    }

    @Test public void smallPhoneKeepsDrawerActionsAndSafetyGapWithinBounds() {
        assertDrawerFit(375, 800);
    }

    @Test public void drawerActionsAcceptKeyboardFocusAndReturnItWhenClosed() {
        View root = assertDrawerFit(375, 800);
        DrawerLayout drawer = root.findViewById(R.id.drawer_layout);
        View terminal = root.findViewById(R.id.terminal_view);
        ExtraKeysDrawerListener listener = new ExtraKeysDrawerListener(drawer, terminal);
        drawer.addDrawerListener(listener);
        terminal.requestFocus();
        drawer.openDrawer(GravityCompat.START, false);
        assertFalse(listener.focusDrawerOnTab(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_TAB, 0, KeyEvent.META_CTRL_ON)));
        assertTrue(listener.focusDrawerOnTab(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB)));
        assertTrue(root.findViewById(R.id.settings_button).hasFocus());
        drawer.closeDrawer(GravityCompat.START, false);
        assertTrue(terminal.hasFocus());
        assertFalse(listener.focusDrawerOnTab(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB)));
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land-notnight-mdpi")
    public void landscapeKeepsActionsAndScrollableSessionsVisible() {
        View root = assertDrawerFit(640, 360);
        assertTrue(root.findViewById(R.id.terminal_sessions_list).getHeight() >= 80);
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void largestTextWrapsSessionNamesAndFitsKeyboardButton() {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.fontScale = 2;
        context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
        View root = assertDrawerFit(375, 800);
        TextView keyboard = root.findViewById(R.id.toggle_keyboard_button);
        assertTrue(keyboard.getLayout().getHeight() + keyboard.getCompoundPaddingTop()
            + keyboard.getCompoundPaddingBottom() <= keyboard.getHeight());
        SessionRowView row = row();
        row.bind(10, "A very long session name that needs several lines at the largest font size",
            "A remote shell title that also wraps", true, 0);
        measure(row, 280, 0);
        TextView title = row.findViewById(R.id.session_title);
        assertTrue(title.getLineCount() > 1);
        assertTrue(row.getHeight() >= title.getHeight());
    }

    private View assertDrawerFit(int width, int height) {
        View root = LayoutInflater.from(context).inflate(R.layout.activity_termux, null);
        View pager = root.findViewById(R.id.terminal_toolbar_view_pager);
        pager.setVisibility(View.VISIBLE); pager.getLayoutParams().height = 75;
        measure(root, width, height);
        View drawer = root.findViewById(R.id.left_drawer);
        View settings = root.findViewById(R.id.settings_button);
        View newSession = root.findViewById(R.id.new_session_button);
        View keyboard = root.findViewById(R.id.toggle_keyboard_button);
        assertTrue(settings.getWidth() >= 48 && settings.getHeight() >= 48);
        assertTrue(newSession.getHeight() >= 48);
        assertEquals(56, keyboard.getHeight());
        assertEquals(40, drawer.getHeight() - keyboard.getBottom());
        assertTrue(keyboard.getTop() >= newSession.getBottom());
        assertTrue(keyboard.getRight() <= drawer.getWidth());
        return root;
    }

    private void measure(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, height == 0 ? View.MeasureSpec.UNSPECIFIED : View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private SessionRowView row() {
        return (SessionRowView) LayoutInflater.from(context).inflate(R.layout.item_terminal_sessions_list, null);
    }

    private String text(View row, int id) { return ((TextView)row.findViewById(id)).getText().toString(); }
    private int color(int id) { return context.getResources().getColor(id); }
}
