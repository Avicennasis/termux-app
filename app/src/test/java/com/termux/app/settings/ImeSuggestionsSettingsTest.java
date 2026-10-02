package com.termux.app.settings;

import org.junit.Test;

import java.io.StringReader;
import java.util.Properties;

import static org.junit.Assert.*;

public class ImeSuggestionsSettingsTest {

    private Properties parse(String source) throws Exception {
        Properties properties = new Properties();
        properties.load(new StringReader(source));
        return properties;
    }

    @Test
    public void toggleRetainsCommentsCustomExtraKeysAndCharacterOverride() throws Exception {
        String source = "# Personal keys\nextra-keys = [[CTRL,ALT,ESC,TAB]]\nenforce-char-based-input = true\n";
        String enabled = ImeSuggestionsSettings.update(source, true);
        assertTrue(enabled.startsWith(source));
        assertEquals("[[CTRL,ALT,ESC,TAB]]", parse(enabled).getProperty("extra-keys"));
        assertEquals("true", parse(enabled).getProperty("enforce-char-based-input"));
        assertEquals("true", parse(enabled).getProperty("terminal-ime-suggestions"));
        String disabled = ImeSuggestionsSettings.update(enabled, false);
        assertEquals("false", parse(disabled).getProperty("terminal-ime-suggestions"));
        assertEquals(1, disabled.split("# Termux-Avic predictive input", -1).length - 1);
    }

    @Test
    public void existingPropertyAndMultilineLayoutRemainUsable() throws Exception {
        String source = "terminal-ime-suggestions = false\nextra-keys = [ \\\n  [CTRL,ALT], \\\n  [ESC,TAB] ]\n";
        String enabled = ImeSuggestionsSettings.update(source, true);
        assertEquals(parse(source).getProperty("extra-keys"), parse(enabled).getProperty("extra-keys"));
        assertEquals("true", parse(enabled).getProperty("terminal-ime-suggestions"));
    }

    @Test
    public void emptyFileKeepsDefaultOffWhenDisabled() throws Exception {
        assertEquals("false", parse(ImeSuggestionsSettings.update("", false)).getProperty("terminal-ime-suggestions"));
    }
}
