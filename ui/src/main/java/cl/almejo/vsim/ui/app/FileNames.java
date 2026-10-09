package cl.almejo.vsim.ui.app;

import java.nio.file.Path;
import java.util.Locale;

public final class FileNames {

    public static final String EXTENSION = ".vsim";

    private FileNames() {
    }

    public static Path withExtension(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION) ? path : path.resolveSibling(fileName + EXTENSION);
    }
}
