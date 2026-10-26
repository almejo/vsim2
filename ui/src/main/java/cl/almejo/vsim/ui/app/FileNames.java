package cl.almejo.vsim.ui.app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class FileNames {

    public static final String EXTENSION = ".vsim";

    private FileNames() {
    }

    public static Path withExtension(Path path) {
        return withExtension(path, EXTENSION);
    }

    /**
     * {@code path}, with {@code extension} (dot included, like «.pdf») added unless it already ends in it, whatever the
     * case of either.
     */
    public static Path withExtension(Path path, String extension) {
        String fileName = path.getFileName().toString();
        return fileName.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT)) ? path
                : path.resolveSibling(fileName + extension);
    }

    /** {@code name} with the characters file systems refuse ({@code / \ : * ? " < > |}) replaced by {@code _}. */
    public static String safeName(String name) {
        return name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    /** One spelling per file: the real path when it exists (links and ".." resolved), else absolute and normalized. */
    public static Path normalized(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            return path.toAbsolutePath().normalize();
        }
    }
}
