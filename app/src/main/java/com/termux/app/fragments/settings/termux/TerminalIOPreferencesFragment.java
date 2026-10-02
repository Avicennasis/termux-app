package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import androidx.annotation.Keep;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.settings.ImeSuggestionsSettings;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;

import java.io.IOException;

@Keep
public class TerminalIOPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TerminalIOPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_terminal_io_preferences, rootKey);

        SwitchPreferenceCompat suggestions = findPreference("terminal_ime_suggestions");
        if (suggestions == null) return;
        suggestions.setChecked(TermuxAppSharedProperties.getProperties().isTerminalImeSuggestionsEnabled());
        suggestions.setOnPreferenceChangeListener((preference, value) -> {
            boolean enabled = (boolean) value;
            if (enabled) {
                new AlertDialog.Builder(context)
                    .setTitle(R.string.termux_ime_suggestions_title)
                    .setMessage(R.string.termux_ime_suggestions_guidance)
                    .setPositiveButton(R.string.termux_ime_suggestions_enable, (dialog, which) -> {
                        if (saveSuggestions(context, true)) suggestions.setChecked(true);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
                return false;
            }
            return saveSuggestions(context, false);
        });
    }

    private boolean saveSuggestions(Context context, boolean enabled) {
        try {
            ImeSuggestionsSettings.setEnabled(enabled);
            TermuxAppSharedProperties.getProperties().loadTermuxPropertiesFromDisk();
            return true;
        } catch (IOException e) {
            Toast.makeText(context, R.string.termux_ime_suggestions_save_failed, Toast.LENGTH_LONG).show();
            return false;
        }
    }

}

class TerminalIOPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;
    private final TermuxAppSharedPreferences mPreferences;

    private static TerminalIOPreferencesDataStore mInstance;

    private TerminalIOPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized TerminalIOPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TerminalIOPreferencesDataStore(context);
        }
        return mInstance;
    }



    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null) return;
        if (key == null) return;

        switch (key) {
            case "soft_keyboard_enabled":
                    mPreferences.setSoftKeyboardEnabled(value);
                break;
            case "soft_keyboard_enabled_only_if_no_hardware":
                mPreferences.setSoftKeyboardEnabledOnlyIfNoHardware(value);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null) return false;

        switch (key) {
            case "soft_keyboard_enabled":
                return mPreferences.isSoftKeyboardEnabled();
            case "soft_keyboard_enabled_only_if_no_hardware":
                return mPreferences.isSoftKeyboardEnabledOnlyIfNoHardware();
            default:
                return false;
        }
    }

}
