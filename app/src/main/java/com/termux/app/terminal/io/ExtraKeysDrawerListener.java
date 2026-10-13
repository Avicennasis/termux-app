package com.termux.app.terminal.io;

import android.view.View;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.termux.shared.termux.extrakeys.ExtraKeysView;

/** Block toolbar keys throughout drawer gestures and animations, including the final closing frame. */
public final class ExtraKeysDrawerListener extends DrawerLayout.SimpleDrawerListener {
    private final DrawerLayout mDrawer;
    private int mDrawerState = DrawerLayout.STATE_IDLE;
    private ExtraKeysView mKeys;

    public ExtraKeysDrawerListener(DrawerLayout drawer) {
        mDrawer = drawer;
    }

    public void setExtraKeysView(ExtraKeysView keys) {
        mKeys = keys;
        updateInputEnabled();
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
    }

    @Override
    public void onDrawerStateChanged(int newState) {
        mDrawerState = newState;
        updateInputEnabled();
    }
}
