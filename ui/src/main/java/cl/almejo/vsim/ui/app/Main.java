package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.i18n.Messages;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
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
        Locale.setDefault(Messages.locale());
        JComponent.setDefaultLocale(Messages.locale());
        List<Path> files = Arrays.stream(arguments).map(Path::of).toList();
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            Application application = new Application(AppPreferences.system());
            installQuitHandler(application);
            application.start(files);
        });
    }

    /** macOS Cmd+Q: ask to save modified documents instead of quitting at once. No-op elsewhere. */
    private static void installQuitHandler(Application application) {
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            return;
        }
        Desktop.getDesktop().setQuitHandler((event, response) -> {
            if (application.exit()) {
                response.performQuit();
            } else {
                response.cancelQuit();
            }
        });
    }
}
