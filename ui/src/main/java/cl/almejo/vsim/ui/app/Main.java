package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.VsimVersion;
import cl.almejo.vsim.ui.about.AboutPresenter;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.AppIcons;
import cl.almejo.vsim.ui.preferences.PreferencesDialog;
import cl.almejo.vsim.ui.theme.MigLayoutDefaults;
import cl.almejo.vsim.ui.theme.ThemeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Taskbar;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] arguments) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "VSim 2");
        MigLayoutDefaults.install();
        AppPreferences preferences = AppPreferences.system();
        Messages.initialize(StartupLanguage.choose(System.getProperty("vsim.language", ""), preferences.language(),
                Locale.getDefault()));
        Locale.setDefault(Messages.locale());
        JComponent.setDefaultLocale(Messages.locale());
        Optional<StartupSplash> splash = StartupSplash.show(VsimVersion.current(), Messages.text("splash.loading"));
        List<Path> files = Arrays.stream(arguments).map(Path::of).toList();
        // M6-D5: asked here, on the main thread, so the EDT never waits up to 2 s for the system's answer.
        ThemeManager.askSystemBeforeStart(preferences);
        // Installed now: macOS may send the open-file event before the Application exists; it waits in `incoming`.
        SystemOpenFiles incoming = new SystemOpenFiles();
        installOpenFileHandler(incoming);
        splash.ifPresent(StartupSplash::awaitFirstPaint);
        SwingUtilities.invokeLater(() -> {
            Application application = new Application(preferences);
            application.setSystemOpenFiles(incoming);
            installQuitHandler(application);
            installPreferencesHandler(application);
            installAboutHandler();
            installDockIcon();
            application.start(files, () -> splash.ifPresent(StartupSplash::awaitMinimumTime));
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

    /** macOS Finder double click / drop on the Dock icon: the files wait in {@code incoming}. No-op elsewhere. */
    private static void installOpenFileHandler(SystemOpenFiles incoming) {
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
            return;
        }
        Desktop.getDesktop().setOpenFileHandler(event -> {
            List<Path> files = event.getFiles().stream().map(File::toPath).toList();
            SwingUtilities.invokeLater(() -> incoming.receive(files));
        });
    }

    /** macOS application menu ▸ Settings…: the same dialog as Archivo ▸ Preferencias…. No-op elsewhere. */
    private static void installPreferencesHandler(Application application) {
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.APP_PREFERENCES)) {
            return;
        }
        Desktop.getDesktop().setPreferencesHandler(event -> SwingUtilities.invokeLater(() -> PreferencesDialog.show(
                KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow(), application.preferences(),
                application.aiServer(), Optional.empty())));
    }

    /** macOS application menu ▸ About VSim 2: the same dialog as Ayuda ▸ «Acerca de VSim 2…». No-op elsewhere. */
    private static void installAboutHandler() {
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT)) {
            return;
        }
        AboutPresenter presenter = AboutPresenter.swing();
        Desktop.getDesktop().setAboutHandler(event -> SwingUtilities.invokeLater(() -> presenter.show(
                KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow())));
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
