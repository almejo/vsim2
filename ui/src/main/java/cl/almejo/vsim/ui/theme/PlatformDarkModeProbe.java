package cl.almejo.vsim.ui.theme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple per-platform dark-mode checks (overview M4-D): macOS global interface style, Windows
 * {@code AppsUseLightTheme}, GNOME {@code color-scheme}. Anything unknown or failing means light.
 */
public final class PlatformDarkModeProbe implements DarkModeProbe {

    static final List<String> MAC_COMMAND = List.of("defaults", "read", "-g", "AppleInterfaceStyle");
    static final List<String> WINDOWS_COMMAND = List.of("reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme");
    static final List<String> GNOME_COMMAND = List.of("gsettings", "get", "org.gnome.desktop.interface",
            "color-scheme");

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformDarkModeProbe.class);
    private static final Pattern WINDOWS_VALUE = Pattern.compile("AppsUseLightTheme\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)");

    private final String osName;
    private final CommandRunner runner;

    public PlatformDarkModeProbe(String osName, CommandRunner runner) {
        this.osName = osName;
        this.runner = runner;
    }

    @Override
    public boolean prefersDark() {
        String system = osName.toLowerCase(Locale.ROOT);
        try {
            if (system.contains("mac")) {
                return runner.run(MAC_COMMAND).map(output -> output.strip().equalsIgnoreCase("dark")).orElse(false);
            }
            if (system.startsWith("windows")) {
                return runner.run(WINDOWS_COMMAND).map(PlatformDarkModeProbe::windowsAppsUseDark).orElse(false);
            }
            if (system.contains("linux") || system.contains("bsd")) {
                return runner.run(GNOME_COMMAND).map(output -> output.contains("prefer-dark")).orElse(false);
            }
            return false;
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not detect the system appearance", exception);
            return false;
        }
    }

    private static boolean windowsAppsUseDark(String output) {
        Matcher matcher = WINDOWS_VALUE.matcher(output);
        return matcher.find() && Long.parseLong(matcher.group(1), 16) == 0;
    }
}
