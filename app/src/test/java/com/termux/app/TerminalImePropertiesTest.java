package com.termux.app;

import android.content.Context;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TerminalImePropertiesTest {
    private static class Properties extends TermuxSharedProperties {
        Properties(Context context, File file) {
            super(context, "IME test", Collections.singletonList(file.getAbsolutePath()),
                TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST, new SharedPropertiesParserClient());
        }
    }

    @Test
    public void predictivePropertyDefaultsOffAndReloadsIndependentlyOfCharMode() throws Exception {
        File file = File.createTempFile("termux-ime", ".properties");
        try {
            Properties properties = new Properties(RuntimeEnvironment.getApplication(), file);
            assertFalse(properties.isTerminalImeSuggestionsEnabled());
            assertFalse(properties.isEnforcingCharBasedInput());
            for (String value : new String[]{"true", "false", "TRUE", "invalid"}) {
                Files.write(file.toPath(), ("terminal-ime-suggestions=" + value
                    + "\nenforce-char-based-input=true\n").getBytes(StandardCharsets.UTF_8));
                properties.loadTermuxPropertiesFromDisk();
                assertEquals(value.equalsIgnoreCase("true"), properties.isTerminalImeSuggestionsEnabled());
                assertTrue(properties.isEnforcingCharBasedInput());
            }
        } finally {
            assertTrue(file.delete());
        }
    }
}
