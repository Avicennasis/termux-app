package com.termux.app.terminal.io;

import android.view.View;
import android.view.KeyEvent;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.termux.R;
import com.termux.shared.termux.extrakeys.ExtraKeysView;

/** Block toolbar keys throughout drawer gestures and animations, including the final closing frame. */
public final class ExtraKeysDrawerListener extends DrawerLayout.SimpleDrawerListener {
    private final DrawerLayout mDrawer;
    private final View mReturnFocus;
    private int mDrawerState = DrawerLayout.STATE_IDLE;
    private ExtraKeysView mKeys;

    public ExtraKeysDrawerListener(DrawerLayout drawer) {
        this(drawer, null);
    }

    public ExtraKeysDrawerListener(DrawerLayout drawer, View returnFocus) {
        mDrawer = drawer;
        mReturnFocus = returnFocus;
    }

    public void setExtraKeysView(ExtraKeysView keys) {
        mKeys = keys;
        updateInputEnabled();
    }

    /** Let a hardware Tab enter the drawer even when TerminalView would consume it. */
    public boolean focusDrawerOnTab(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getKeyCode() != KeyEvent.KEYCODE_TAB
            || event.isCtrlPressed() || event.isAltPressed() || event.isMetaPressed()
            || !mDrawer.isDrawerOpen(GravityCompat.START)) return false;
        View drawerView = mDrawer.findViewById(R.id.left_drawer);
        if (drawerView == null || drawerView.hasFocus()) return false;
        if (drawerView.isInTouchMode()) drawerView.requestFocusFromTouch();
        return drawerView.requestFocus(event.isShiftPressed() ? View.FOCUS_BACKWARD : View.FOCUS_FORWARD);
    }

    private void updateInputEnabled() {
        if (mKeys != null)
            mKeys.setInputEnabled(mDrawerState == DrawerLayout.STATE_IDLE
                && !mDrawer.isDrawerVisible(GravityCompat.START));
    }

    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {
        updateInputEnabled();
    }

    @Override
    public void onDrawerOpened(View drawerView) {
        updateInputEnabled();
    }

    @Override
    public void onDrawerClosed(View drawerView) {
        updateInputEnabled();
        if (mReturnFocus != null && drawerView.hasFocus())
            mReturnFocus.requestFocus();
    }

    @Override
    public void onDrawerStateChanged(int newState) {
        mDrawerState = newState;
        updateInputEnabled();
    }
}
