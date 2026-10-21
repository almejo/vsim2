package cl.almejo.vsim.core.io;

import java.util.regex.Pattern;

/** Helpers for byte-for-byte checks of saved files. */
public final class SavedFiles {

    private static final Pattern SAVED_WITH = Pattern.compile("(\"savedWith\"\\s*:\\s*)\"[^\"]*\"");

    private SavedFiles() {
    }

    /**
     * {@code json} with its {@code "savedWith"} value blanked: a committed file keeps the version that saved it, so
     * a byte-for-byte check must not fail just because the app version changed since.
     */
    public static String withoutSavedWith(String json) {
        return SAVED_WITH.matcher(json).replaceFirst("$1\"\"");
    }
}
