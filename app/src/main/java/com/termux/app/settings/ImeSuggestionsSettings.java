package com.termux.app.settings;

import android.util.AtomicFile;

import com.termux.shared.file.FileUtils;
import com.termux.shared.file.filesystem.FileType;
import com.termux.shared.settings.properties.SharedProperties;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Writes only a small managed property block; custom extra keys and comments are retained. */
public final class ImeSuggestionsSettings {

    private static final String BEGIN = "# Termux-Avic predictive input (Settings)";
    private static final String END = "# End Termux-Avic predictive input";
    private static final Pattern BLOCK = Pattern.compile("(?:\\r?\\n){2}" + Pattern.quote(BEGIN)
        + "\\r?\\nterminal-ime-suggestions = (?:true|false)\\r?\\n" + Pattern.quote(END) + "\\r?\\n?");

    private ImeSuggestionsSettings() {}

    public static void setEnabled(boolean enabled) throws IOException {
        File file = SharedProperties.getPropertiesFileFromList(
            TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, "ImeSuggestionsSettings");
        if (file == null) file = new File(TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE_PATH);
        FileType type = FileUtils.getFileType(file.getAbsolutePath(), false);
        if (type != FileType.REGULAR && type != FileType.NO_EXIST)
            throw new IOException("The properties path must be a regular file.");
        write(file, enabled);
    }

    public static String update(String original, boolean enabled) {
        String retained = BLOCK.matcher(original).replaceAll("");
        // A blank line also terminates an unfinished properties continuation at EOF.
        return retained + "\n\n" + BEGIN + "\nterminal-ime-suggestions = " + enabled + "\n" + END + "\n";
    }

    static void write(File file, boolean enabled) throws IOException {
        File parent = file.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs()))
            throw new IOException("Cannot create the properties directory.");
        AtomicFile atomicFile = new AtomicFile(file);
        String original = file.exists() ? new String(atomicFile.readFully(), StandardCharsets.UTF_8) : "";
        FileOutputStream stream = null;
        try {
            stream = atomicFile.startWrite();
            stream.write(update(original, enabled).getBytes(StandardCharsets.UTF_8));
            atomicFile.finishWrite(stream);
        } catch (IOException e) {
            if (stream != null) atomicFile.failWrite(stream);
            throw e;
        }
    }
}
