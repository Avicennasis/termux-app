package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSessionClient;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerminalRendererImeTest {
    @Test
    public void longUnicodePreviewIsClippedInsideTerminalWithoutChangingEmulator() {
        TerminalSessionClient callback = (TerminalSessionClient) Proxy.newProxyInstance(
            TerminalSessionClient.class.getClassLoader(), new Class[]{TerminalSessionClient.class},
            (proxy, method, args) -> method.getReturnType() == Integer.class ? Integer.valueOf(0) : null);
        TerminalOutput output = new TerminalOutput() {
            public void write(byte[] data, int offset, int count) { fail("Rendering must not send terminal input"); }
            public void titleChanged(String oldTitle, String newTitle) {}
            public void onCopyTextToClipboard(String text) {}
            public void onPasteTextFromClipboard() {}
            public void onBell() {}
            public void onColorsChanged() {}
        };
        TerminalEmulator emulator = new TerminalEmulator(output, 8, 2, 8, 16, 100, callback);
        TerminalRenderer renderer = new TerminalRenderer(14, Typeface.MONOSPACE);
        Bitmap bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.MAGENTA);
        String text = "café😀中文字" + new String(new char[100]).replace('\0', 'x');
        renderer.renderImeDraft(emulator, new Canvas(bitmap), 0, text, text.length());
        int bottom = renderer.mFontLineSpacingAndAscent + emulator.mRows * renderer.mFontLineSpacing;
        for (int y = bottom + 1; y < bitmap.getHeight(); y++)
            for (int x = 0; x < bitmap.getWidth(); x++) assertEquals(Color.MAGENTA, bitmap.getPixel(x, y));
        assertEquals(0, emulator.getCursorRow());
        assertEquals(0, emulator.getCursorCol());
        assertEquals("", emulator.getScreen().getSelectedText(0, 0, 7, 1).trim());
    }
}
