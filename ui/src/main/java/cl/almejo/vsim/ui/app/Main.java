package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.AppIcons;
import cl.almejo.vsim.ui.preferences.PreferencesDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Taskbar;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] arguments) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "VSim 2");
        AppPreferences preferences = AppPreferences.system();
        Messages.initialize(StartupLanguage.choose(System.getProperty("vsim.language", ""), preferences.language(),
                Locale.getDefault()));
        Locale.setDefault(Messages.locale());
        JComponent.setDefaultLocale(Messages.locale());
        List<Path> files = Arrays.stream(arguments).map(Path::of).toList();
        SwingUtilities.invokeLater(() -> {
            Application application = new Application(preferences);
            installQuitHandler(application);
            installPreferencesHandler(application);
            installDockIcon();
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

    /** macOS application menu ▸ Settings…: the same dialog as Archivo ▸ Preferencias…. No-op elsewhere. */
    private static void installPreferencesHandler(Application application) {
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.APP_PREFERENCES)) {
            return;
        }
        Desktop.getDesktop().setPreferencesHandler(event -> SwingUtilities.invokeLater(() -> PreferencesDialog.show(
                KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow(), application.preferences())));
    }

    /** macOS Dock (and any taskbar that takes one): the largest application icon. Cosmetic, never fails startup. */
    private static void installDockIcon() {
        try {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                List<Image> images = AppIcons.images();
                if (!images.isEmpty()) {
                    Taskbar.getTaskbar().setIconImage(images.get(images.size() - 1));
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Cannot set the taskbar icon", exception);
        }
    }
}
