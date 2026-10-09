package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.i18n.Messages;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class Main {

    private Main() {
    }

    public static void main(String[] arguments) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "VSim 2");
        String language = System.getProperty("vsim.language", "");
        Messages.initialize(language.isBlank() ? Locale.getDefault() : Locale.forLanguageTag(language));
        List<Path> files = Arrays.stream(arguments).map(Path::of).toList();
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            new Application().start(files);
        });
    }
}
