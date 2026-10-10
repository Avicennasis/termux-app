package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.termux.app.TermuxActivity;
import com.termux.R;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.google.android.material.button.MaterialButton;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;

import org.json.JSONException;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private ExtraKeysInfo mExtraKeysInfo;
    private boolean mPageMode;

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        super(terminalView);

        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;

        setExtraKeys();
    }


    /**
     * Set the terminal extra keys and style.
     */
    private void setExtraKeys() {
        mExtraKeysInfo = null;

        try {
            // The mMap stores the extra key and style string values while loading properties
            // Check {@link #getExtraKeysInternalPropertyValueFromValue(String)} and
            // {@link #getExtraKeysStyleInternalPropertyValueFromValue(String)}
            String extrakeys = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true);
            String extraKeysStyle = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);

            ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
            if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE.equals(extraKeysStyle)) {
                Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE + "\" is invalid. Using default style instead.");
                extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
            }

            mExtraKeysInfo = new ExtraKeysInfo(extrakeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (JSONException e) {
            Logger.showToast(mActivity, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: " + e.toString(), true);
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: ", e);

            try {
                mExtraKeysInfo = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            } catch (JSONException e2) {
                Logger.showToast(mActivity, "Can't create default extra keys",true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e);
                mExtraKeysInfo = null;
            }
        }
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    public boolean isPageMode() {
        return mPageMode;
    }

    public void setPageMode(boolean enabled) {
        mPageMode = enabled;
        updatePageModeButtons();
    }

    /** PAGE is a persistent toggle, independent of the one-shot terminal modifiers. */
    public void updatePageModeButtons() {
        ExtraKeysView keys = mActivity.getExtraKeysView();
        if (keys == null || mExtraKeysInfo == null) return;
        int gold = mActivity.getResources().getColor(R.color.extra_keys_page_arrow);
        int index = 0;
        for (ExtraKeyButton[] row : mExtraKeysInfo.getMatrix()) {
            for (ExtraKeyButton info : row) {
                View child = keys.getChildAt(index++);
                if (info.isMacro() || !(child instanceof MaterialButton)) continue;
                MaterialButton button = (MaterialButton) child;
                String key = info.getKey();
                if ("PAGE".equals(key)) {
                    button.setTextColor(mPageMode ? keys.getButtonActiveTextColor() : keys.getButtonTextColor());
                    button.setSelected(mPageMode);
                    button.setContentDescription(mActivity.getString(mPageMode
                        ? R.string.extra_keys_page_on : R.string.extra_keys_page_off));
                } else if ("UP".equals(key) || "DOWN".equals(key)) {
                    button.setTextColor(mPageMode ? gold : keys.getButtonTextColor());
                    button.setContentDescription(mPageMode ? mActivity.getString("UP".equals(key)
                        ? R.string.extra_keys_page_up : R.string.extra_keys_page_down) : info.getDisplay());
                } else if ("KEYBOARD".equals(key)) {
                    button.setContentDescription(mActivity.getString(R.string.action_toggle_soft_keyboard));
                }
            }
        }
    }

    @Override
    public void onExtraKeyButtonClick(View view, ExtraKeyButton info, MaterialButton button) {
        // Remap the visible arrow buttons, leaving explicit custom macros unchanged.
        if (mPageMode && !info.isMacro() && ("UP".equals(info.getKey()) || "DOWN".equals(info.getKey()))) {
            super.onTerminalExtraKeyButtonClick(view, "UP".equals(info.getKey()) ? "PGUP" : "PGDN",
                false, false, false, false);
        } else {
            super.onExtraKeyButtonClick(view, info, button);
        }
    }

    /** Keep the IME key's label in sync without reloading or clearing modifier buttons. */
    public void updateImeModeButtons() {
        ExtraKeysView keys = mActivity.getExtraKeysView();
        if (keys == null || mExtraKeysInfo == null) return;
        boolean available = mActivity.getProperties().isTerminalImeSuggestionsEnabled()
            && !mActivity.getProperties().isEnforcingCharBasedInput();
        boolean text = available && mActivity.getTerminalView().shouldEnableImeSuggestions();
        int label = !available ? R.string.termux_ime_mode_off
            : text ? R.string.termux_ime_mode_text : R.string.termux_ime_mode_keys;
        int description = !available ? R.string.termux_ime_mode_unavailable
            : text ? R.string.termux_ime_mode_text_description : R.string.termux_ime_mode_keys_description;
        int index = 0;
        for (ExtraKeyButton[] row : mExtraKeysInfo.getMatrix()) {
            for (ExtraKeyButton info : row) {
                View child = keys.getChildAt(index++);
                if (!info.isMacro() && "IME".equals(info.getKey()) && child instanceof MaterialButton) {
                    ((MaterialButton) child).setText(label);
                    child.setContentDescription(mActivity.getString(description));
                }
            }
        }
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if ("PAGE".equals(key)) {
            setPageMode(!mPageMode);
        } else if ("IME".equals(key)) {
            if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.toggleImeInputMode();
        } else if ("KEYBOARD".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mTermuxTerminalViewClient.getActivity().getDrawer();
            if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.closeDrawer(Gravity.LEFT);
            else
                drawerLayout.openDrawer(Gravity.LEFT);
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        }  else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

}
