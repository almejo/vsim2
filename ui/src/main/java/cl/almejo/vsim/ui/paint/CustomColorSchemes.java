package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.ui.app.AppPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The colour schemes the user made (M6.8), kept in the preferences: child node {@code colorSchemes/<id>} with the key
 * {@code name} and one key per {@link ColorRole} holding {@code #RRGGBBAA}. Ids are {@code custom-<n>}, one more than
 * the highest stored, so an id is never reused while it is stored. Every change tells the preference listeners, so
 * the theme manager repaints with an edited active scheme at once. A scheme stored before a role existed gets that
 * role from {@link ColorScheme#LIGHT}, except the grid line, which is derived from the scheme's own dots, and the
 * pins, which take the scheme's own outline colour (so it draws exactly as before the role existed).
 */
public final class CustomColorSchemes {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomColorSchemes.class);
    private static final String ID_PREFIX = "custom-";
    private static final Pattern ID = Pattern.compile("custom-([1-9][0-9]{0,8})");
    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{8}");
    private static final String NAME = "name";

    private final AppPreferences preferences;

    public CustomColorSchemes(AppPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    /** Whether {@code id} has the form of a custom scheme id (it may not be stored). */
    public static boolean isCustomId(String id) {
        return ID.matcher(id).matches();
    }

    /** Every stored scheme, by name (ignoring case), then by id. */
    public List<NamedColorScheme> list() {
        List<NamedColorScheme> schemes = new ArrayList<>();
        for (String id : storedIds()) {
            byId(id).ifPresent(schemes::add);
        }
        schemes.sort(Comparator.comparing((NamedColorScheme scheme) -> scheme.name().toLowerCase(Locale.ROOT))
                .thenComparing(NamedColorScheme::name)
                .thenComparingInt(scheme -> number(scheme.id())));
        return schemes;
    }

    public Optional<NamedColorScheme> byId(String id) {
        if (!isCustomId(id) || !exists(id)) {
            return Optional.empty();
        }
        Preferences node = root().node(id);
        String name = node.get(NAME, "");
        ColorScheme stored = ColorRole.scheme(role -> parse(node.get(role.key(), ""))
                .orElseGet(() -> role.get(ColorScheme.LIGHT)));
        ColorScheme withGridLine = parse(node.get(ColorRole.GRID_LINE.key(), "")).isPresent() ? stored
                : stored.with(ColorRole.GRID_LINE, ColorScheme.derivedGridLine(stored.grid()));
        ColorScheme withPad = parse(node.get(ColorRole.PAD.key(), "")).isPresent() ? withGridLine
                : withGridLine.with(ColorRole.PAD, withGridLine.componentStroke());
        // a scheme stored before the memory parts has no activity colours: the built-in pair that suits its board
        ColorScheme memoryDefaults = withPad.isDark() ? ColorSchemes.DARK : ColorScheme.LIGHT;
        ColorScheme withRead = parse(node.get(ColorRole.MEMORY_READ.key(), "")).isPresent() ? withPad
                : withPad.with(ColorRole.MEMORY_READ, memoryDefaults.memoryRead());
        ColorScheme scheme = parse(node.get(ColorRole.MEMORY_WRITE.key(), "")).isPresent() ? withRead
                : withRead.with(ColorRole.MEMORY_WRITE, memoryDefaults.memoryWrite());
        return Optional.of(new NamedColorScheme(id, name.isBlank() ? id : name, scheme));
    }

    /** A built-in scheme or a stored one; empty for an id that is neither (the caller falls back to the theme). */
    public Optional<ColorScheme> lookup(String id) {
        Optional<ColorScheme> builtIn = ColorSchemes.byId(id);
        return builtIn.isPresent() ? builtIn : byId(id).map(NamedColorScheme::scheme);
    }

    /** Stores a new scheme under a fresh id. */
    public NamedColorScheme save(String name, ColorScheme scheme) {
        int highest = storedIds().stream().mapToInt(CustomColorSchemes::number).max().orElse(0);
        NamedColorScheme saved = new NamedColorScheme(ID_PREFIX + (highest + 1), name, scheme);
        write(saved);
        return saved;
    }

    /** Rewrites a stored scheme in place (same id); IllegalArgumentException when it is not stored. */
    public void update(NamedColorScheme scheme) {
        if (!isCustomId(scheme.id()) || !exists(scheme.id())) {
            throw new IllegalArgumentException("no custom colour scheme " + scheme.id());
        }
        write(scheme);
    }

    /** Removes a stored scheme; when it was the chosen one, the choice goes back to «Según el tema». */
    public void delete(String id) {
        if (!isCustomId(id) || !exists(id)) {
            return;
        }
        try {
            root().node(id).removeNode();
        } catch (BackingStoreException exception) {
            LOGGER.warn("Could not delete the colour scheme {}", id, exception);
        }
        if (preferences.colorScheme().filter(id::equals).isPresent()) {
            preferences.setColorScheme(Optional.empty());
        }
        preferences.colorSchemesChanged();
    }

    private void write(NamedColorScheme scheme) {
        Preferences node = root().node(scheme.id());
        node.put(NAME, scheme.name());
        for (ColorRole role : ColorRole.values()) {
            node.put(role.key(), format(role.get(scheme.scheme())));
        }
        preferences.colorSchemesChanged();
    }

    private Preferences root() {
        return preferences.colorSchemesNode();
    }

    private boolean exists(String id) {
        try {
            return root().nodeExists(id);
        } catch (BackingStoreException exception) {
            LOGGER.warn("Could not read the colour scheme {}", id, exception);
            return false;
        }
    }

    private List<String> storedIds() {
        try {
            return List.of(root().childrenNames()).stream().filter(CustomColorSchemes::isCustomId).toList();
        } catch (BackingStoreException exception) {
            LOGGER.warn("Could not read the custom colour schemes", exception);
            return List.of();
        }
    }

    private static int number(String id) {
        Matcher matcher = ID.matcher(id);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    static String format(Color color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue(),
                color.getAlpha());
    }

    static Optional<Color> parse(String stored) {
        if (!HEX.matcher(stored).matches()) {
            return Optional.empty();
        }
        long value = Long.parseLong(stored.substring(1), 16);
        return Optional.of(new Color((int) (value >> 24) & 0xFF, (int) (value >> 16) & 0xFF, (int) (value >> 8) & 0xFF,
                (int) value & 0xFF));
    }
}
