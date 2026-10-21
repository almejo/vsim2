package cl.almejo.vsim.ui.theme;

import javax.swing.LookAndFeel;
import java.util.List;

/** For contrast tests: every theme {@link FlatLafInstaller} can install (FlatLaf light, dark and their macOS ones). */
public final class AppThemes {

    private AppThemes() {
    }

    public static List<LookAndFeel> all() {
        return List.of(FlatLafInstaller.lookAndFeel("linux", false), FlatLafInstaller.lookAndFeel("linux", true),
                FlatLafInstaller.lookAndFeel("mac", false), FlatLafInstaller.lookAndFeel("mac", true));
    }
}
