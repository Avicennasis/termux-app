package com.termux.app;

import com.termux.shared.termux.TermuxConstants;

import org.junit.Test;

import static org.junit.Assert.*;

public class TermuxAvicIdentityTest {
    @Test
    public void personalIdentityUsesIndependentPathsAndPermission() {
        assertEquals("com.termuxavic", TermuxConstants.TERMUX_PACKAGE_NAME);
        assertEquals("/data/data/com.termuxavic/files/home", TermuxConstants.TERMUX_HOME_DIR_PATH);
        assertEquals("/data/data/com.termuxavic/files/usr", TermuxConstants.TERMUX_PREFIX_DIR_PATH);
        assertEquals("com.termuxavic.permission.RUN_COMMAND", TermuxConstants.PERMISSION_RUN_COMMAND);
        assertEquals("com.termuxavic.files", TermuxConstants.TERMUX_FILE_SHARE_URI_AUTHORITY);
    }

    @Test
    public void renamedPackageKeepsRealTermuxComponents() throws Exception {
        assertEquals("com.termux.app.TermuxActivity", TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY_NAME);
        assertEquals("com.termux.app.activities.SettingsActivity", TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME);
        assertEquals("com.termux.app.TermuxService", TermuxConstants.TERMUX_APP.TERMUX_SERVICE_NAME);
        assertEquals("com.termux.BuildConfig", TermuxConstants.TERMUX_APP.BUILD_CONFIG_CLASS_NAME);
        Class.forName(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY_NAME, false, getClass().getClassLoader());
    }
}
