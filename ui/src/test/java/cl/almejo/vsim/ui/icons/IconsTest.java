package cl.almejo.vsim.ui.icons;

import com.formdev.flatlaf.util.UIScale;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IconsTest {

    private static final Path DIRECTORY = Path.of("src/main/resources/icons");
    /** FlatLaf's action colors: FlatSVGIcon recolors exactly these in dark themes. */
    private static final Set<String> ACTION_COLORS = Set.of("#6E6E6E", "#389FD6", "#59A869", "#DB5860");
    private static final Pattern PAINT = Pattern.compile("(?:fill|stroke)=\"([^\"]*)\"");

    static List<String> iconNames() throws IOException {
        try (Stream<Path> files = Files.list(DIRECTORY)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".svg"))
                    .map(name -> name.substring(0, name.length() - ".svg".length()))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void everyIconDrawsSomethingInsideItsSizeForMenusAndToolbars() throws IOException {
        for (String name : iconNames()) {
            for (int size : new int[]{Icons.MENU_SIZE, Icons.TOOLBAR_SIZE}) {
                Icon icon = Icons.get(name, size);
                assertThat(icon.getIconWidth()).as(name).isEqualTo(UIScale.scale(size));
                assertThat(icon.getIconHeight()).as(name).isEqualTo(UIScale.scale(size));
                BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
                        BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                icon.paintIcon(null, graphics, 0, 0);
                graphics.dispose();
                int painted = 0;
                for (int x = 0; x < image.getWidth(); x++) {
                    for (int y = 0; y < image.getHeight(); y++) {
                        if ((image.getRGB(x, y) >>> 24) != 0) {
                            painted++;
                        }
                    }
                }
                assertThat(painted).as(name + " at " + size).isGreaterThan(size * 2);
            }
        }
    }

    @Test
    void iconsUseOnlyTheColorsTheDarkThemeRecolors() throws IOException {
        for (String name : iconNames()) {
            String svg = Files.readString(DIRECTORY.resolve(name + ".svg"));
            Matcher paint = PAINT.matcher(svg);
            while (paint.find()) {
                String color = paint.group(1);
                assertThat(color.equals("none") || ACTION_COLORS.contains(color.toUpperCase()))
                        .as(name + " uses " + color).isTrue();
            }
            assertThat(svg).as(name).doesNotContain("style=").doesNotContain("<style");
        }
    }

    @Test
    void anUnknownIconFailsAtOnce() {
        assertThatThrownBy(() -> Icons.get("no-such-icon", Icons.MENU_SIZE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-icon");
    }

    @Test
    void installGivesAnActionItsMenuAndToolbarIcons() {
        Action action = new AbstractAction("x") {
            @Override
            public void actionPerformed(ActionEvent event) {
            }
        };
        Icons.install(action, "save");
        assertThat(action.getValue(Action.SMALL_ICON)).isSameAs(Icons.get("save", Icons.MENU_SIZE));
        assertThat(action.getValue(Action.LARGE_ICON_KEY)).isSameAs(Icons.get("save", Icons.TOOLBAR_SIZE));
    }
}
