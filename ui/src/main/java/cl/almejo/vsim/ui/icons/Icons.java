package cl.almejo.vsim.ui.icons;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Action;
import javax.swing.Icon;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The SVG icons under {@code ui/src/main/resources/icons/} (spec §6.11). They are drawn with FlatLaf's action colors
 * ({@code #6E6E6E}, {@code #389FD6}, {@code #59A869}, {@code #DB5860}), which {@link FlatSVGIcon} recolors in dark
 * themes.
 */
public final class Icons {

    public static final int MENU_SIZE = 16;
    public static final int TOOLBAR_SIZE = 24;
    static final String DIRECTORY = "icons/";

    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();

    private Icons() {
    }

    /** The icon {@code name} ({@code icons/<name>.svg}) at {@code size} x {@code size} pixels (before UI scaling). */
    public static Icon get(String name, int size) {
        return CACHE.computeIfAbsent(name + "@" + size, key -> load(name, size));
    }

    /** Gives {@code action} the icon {@code name}: 16 px in menus, 24 px in toolbars. */
    public static void install(Action action, String name) {
        action.putValue(Action.SMALL_ICON, get(name, MENU_SIZE));
        action.putValue(Action.LARGE_ICON_KEY, get(name, TOOLBAR_SIZE));
    }

    private static Icon load(String name, int size) {
        String resource = DIRECTORY + name + ".svg";
        ClassLoader loader = Icons.class.getClassLoader();
        if (loader.getResource(resource) == null) {
            throw new IllegalArgumentException("no icon " + resource);
        }
        return new FlatSVGIcon(resource, size, size, loader);
    }
}
