package cl.almejo.vsim.ui.theme;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformDarkModeProbeTest {

    private final List<List<String>> commands = new ArrayList<>();

    /** A runner that records the command and answers {@code output} without starting any process. */
    private CommandRunner answering(Optional<String> output) {
        return command -> {
            commands.add(command);
            return output;
        };
    }

    @Test
    void macOsAsksTheGlobalInterfaceStyle() {
        assertThat(new PlatformDarkModeProbe("Mac OS X", answering(Optional.of("Dark\n"))).prefersDark()).isTrue();
        assertThat(commands).containsExactly(List.of("defaults", "read", "-g", "AppleInterfaceStyle"));
    }

    @Test
    void windowsReadsAppsUseLightTheme() {
        String dark = "\r\nHKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\r\n"
                + "    AppsUseLightTheme    REG_DWORD    0x0\r\n\r\n";
        String light = dark.replace("0x0", "0x1");
        assertThat(new PlatformDarkModeProbe("Windows 11", answering(Optional.of(dark))).prefersDark()).isTrue();
        assertThat(new PlatformDarkModeProbe("Windows 11", answering(Optional.of(light))).prefersDark()).isFalse();
        assertThat(commands.get(0)).containsExactly("reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme");
    }

    @Test
    void gnomeReadsTheColorScheme() {
        assertThat(new PlatformDarkModeProbe("Linux", answering(Optional.of("'prefer-dark'\n"))).prefersDark()).isTrue();
        assertThat(new PlatformDarkModeProbe("Linux", answering(Optional.of("'default'\n"))).prefersDark()).isFalse();
        assertThat(commands.get(0)).containsExactly("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
    }

    @Test
    void aFailedOrMissingCommandMeansLight() {
        assertThat(new PlatformDarkModeProbe("Mac OS X", answering(Optional.empty())).prefersDark()).isFalse();
        assertThat(new PlatformDarkModeProbe("Windows 10", answering(Optional.empty())).prefersDark()).isFalse();
        assertThat(new PlatformDarkModeProbe("Linux", answering(Optional.empty())).prefersDark()).isFalse();
    }

    @Test
    void unexpectedOutputMeansLight() {
        assertThat(new PlatformDarkModeProbe("Mac OS X", answering(Optional.of("Light"))).prefersDark()).isFalse();
        assertThat(new PlatformDarkModeProbe("Windows 10", answering(Optional.of("ERROR: not found"))).prefersDark())
                .isFalse();
        assertThat(new PlatformDarkModeProbe("Linux", answering(Optional.of(""))).prefersDark()).isFalse();
    }

    @Test
    void aRunnerThatThrowsMeansLight() {
        CommandRunner failing = command -> {
            throw new UncheckedIOException(new IOException("no such program"));
        };
        assertThat(new PlatformDarkModeProbe("Linux", failing).prefersDark()).isFalse();
    }

    @Test
    void otherSystemsAreLightWithoutRunningAnything() {
        assertThat(new PlatformDarkModeProbe("SunOS", answering(Optional.of("Dark"))).prefersDark()).isFalse();
        assertThat(new PlatformDarkModeProbe("", answering(Optional.of("Dark"))).prefersDark()).isFalse();
        assertThat(commands).isEmpty();
    }

    @Test
    void aMissingSystemNameMeansLight() {
        assertThat(new PlatformDarkModeProbe(null, answering(Optional.of("Dark"))).prefersDark()).isFalse();
        assertThat(commands).isEmpty();
    }
}
