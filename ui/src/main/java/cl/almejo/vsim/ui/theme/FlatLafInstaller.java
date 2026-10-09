package cl.almejo.vsim.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/** FlatLaf light or dark (the macOS variants on macOS, spec §6.1); every open window is updated at once. */
public final class FlatLafInstaller implements LookAndFeelInstaller {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlatLafInstaller.class);

    private final String osName;

    public FlatLafInstaller(String osName) {
        this.osName = osName;
    }

    public static FlatLaf lookAndFeel(String osName, boolean dark) {
        boolean mac = osName.toLowerCase(Locale.ROOT).contains("mac");
        if (mac) {
            return dark ? new FlatMacDarkLaf() : new FlatMacLightLaf();
        }
        return dark ? new FlatDarkLaf() : new FlatLightLaf();
    }

    @Override
    public void install(boolean dark) {
        // A failing theme switch must never break the other preference listeners (it runs inside one).
        try {
            if (!FlatLaf.setup(lookAndFeel(osName, dark))) {
                LOGGER.warn("Could not install the {} look and feel", dark ? "dark" : "light");
                return;
            }
            FlatLaf.updateUI();
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not switch to the {} look and feel", dark ? "dark" : "light", exception);
        }
    }
}
