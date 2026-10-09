package cl.almejo.vsim.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlatLafInstallerTest {

    @Test
    void macOsGetsTheMacThemesAndOtherSystemsTheStandardOnes() {
        assertThat(FlatLafInstaller.lookAndFeel("Mac OS X", false)).isExactlyInstanceOf(FlatMacLightLaf.class);
        assertThat(FlatLafInstaller.lookAndFeel("Mac OS X", true)).isExactlyInstanceOf(FlatMacDarkLaf.class);
        assertThat(FlatLafInstaller.lookAndFeel("Linux", false)).isExactlyInstanceOf(FlatLightLaf.class);
        assertThat(FlatLafInstaller.lookAndFeel("Windows 11", true)).isExactlyInstanceOf(FlatDarkLaf.class);
    }
}
