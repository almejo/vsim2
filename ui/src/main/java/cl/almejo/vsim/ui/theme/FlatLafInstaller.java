package cl.almejo.vsim.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;

/**
 * FlatLaf light or dark (the macOS variants on macOS, spec §6.1), at the interface's size and font; every open window
 * is updated at once. The size is FlatLaf's {@code defaultFont} in percent: FlatLaf derives its insets and icon scale
 * from that font, so the whole interface grows, live, without a restart.
 */
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
    public boolean install(boolean dark) {
        return install(dark, InterfaceLook.STANDARD);
    }

    @Override
    public boolean install(boolean dark, InterfaceLook look) {
        // A failing theme switch must never break the other preference listeners (it runs inside one).
        try {
            FlatLaf.setPreferredFontFamily(look.fontFamily().orElse(null));
            FlatLaf.setGlobalExtraDefaults(defaultsFor(look));
            if (!FlatLaf.setup(lookAndFeel(osName, dark))) {
                LOGGER.warn("Could not install the {} look and feel", dark ? "dark" : "light");
                return false;
            }
            FlatLaf.updateUI();
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not switch to the {} look and feel", dark ? "dark" : "light", exception);
            return false;
        }
    }

    /** FlatLaf's extra defaults for {@code look}: none at the standard size. */
    static Map<String, String> defaultsFor(InterfaceLook look) {
        return look.zoomPercent() == 100 ? null : Map.of("defaultFont", look.zoomPercent() + "%");
    }
}
