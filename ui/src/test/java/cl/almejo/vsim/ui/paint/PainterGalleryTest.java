package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Writes {@code ui/build/painter-gallery.png}: every type in every rotation plus an "on" state, for visual review. */
class PainterGalleryTest {

    private static final double SCALE = 2.5;
    private static final int LABEL_HEIGHT = 20;
    private static final int PADDING = 10;
    /** Painters may draw this far outside their bounds (leads, shadows, glows); see PaintersTest. */
    private static final int MARGIN = 8;
    /** The Terminal gallery's tiles: drawn at 1.5×, each in a strip this many pixels high and wide. */
    private static final double TERMINAL_SCALE = 1.5;
    private static final int TERMINAL_TILE_WIDTH = 360;
    private static final int TERMINAL_TILE_HEIGHT = 190;
    private static final Map<String, Object> GALLERY_TERMINAL = Map.of("rows", 4, "columns", 20);

    /** Types shown, one per row: every standard type (interface pins included) and a user component. */
    static List<ComponentType> galleryTypes() {
        List<ComponentType> types = new ArrayList<>(StandardComponentTypes.registry().all());
        // the default 24x80 Terminal is 656 px wide (the UART's screen too): a small one keeps the gallery's image a
        // sane size
        types.replaceAll(type -> hasScreen(type.id()) ? type.specializedFor(GALLERY_TERMINAL) : type);
        types.add(SubcircuitTypes.typeOf(ExampleDefinitions.inverter()));
        types.add(SubcircuitTypes.typeOf(ExampleDefinitions.byteBuffer()));
        for (String pinTypeId : List.of(InterfacePins.INPUT_PIN, InterfacePins.OUTPUT_PIN)) {
            types.add(StandardComponentTypes.registry().require(pinTypeId).specializedFor(Map.of(InterfacePins.WIDTH, 8)));
        }
        return types;
    }

    private static boolean hasScreen(String typeId) {
        return typeId.equals(StandardComponentTypes.TERMINAL) || typeId.equals(StandardComponentTypes.UART_16550);
    }

    /** Side of the square area a type needs below its label, in image pixels, in any rotation. */
    static int drawingSide(ComponentType type) {
        double largest = Math.max(type.size().width(), type.size().height()) * PainterSupport.UNIT + 2 * MARGIN;
        return (int) Math.ceil(largest * SCALE) + 2 * PADDING;
    }

    @Test
    void writesAGalleryOfEveryComponentForVisualReview() throws Exception {
        PainterRegistry painters = PainterRegistry.standard();
        List<ComponentType> types = galleryTypes();
        assertThat(types).extracting(ComponentType::id).contains(InterfacePins.INPUT_PIN, InterfacePins.OUTPUT_PIN,
                SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER));
        int columns = Rotation.values().length + 1;
        int cellWidth = types.stream().mapToInt(PainterGalleryTest::drawingSide).max().orElseThrow();
        int[] rowTops = new int[types.size() + 1];
        for (int row = 0; row < types.size(); row++) {
            rowTops[row + 1] = rowTops[row] + LABEL_HEIGHT + drawingSide(types.get(row));
        }
        BufferedImage image = PaintersTest.blankImage(cellWidth * columns, rowTops[types.size()]);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int row = 0; row < types.size(); row++) {
            ComponentType type = types.get(row);
            for (int column = 0; column < columns; column++) {
                boolean stateColumn = column == Rotation.values().length;
                Rotation rotation = stateColumn ? Rotation.R0 : Rotation.values()[column];
                VisibleState state = stateColumn ? PaintersTest.sampleState(type.id()) : VisibleState.NONE;
                graphics.setColor(PaintersTest.SCHEME.text());
                graphics.drawString(type.id() + " " + (stateColumn ? "on" : rotation.name()),
                        column * cellWidth + 6, rowTops[row] + 14);
                Rectangle2D drawingArea = new Rectangle2D.Double(column * cellWidth, rowTops[row] + LABEL_HEIGHT,
                        cellWidth, drawingSide(type));
                Component component = type.newComponent(new ElementId("c1"), GridPoint.ORIGIN, rotation,
                        hasScreen(type.id()) ? GALLERY_TERMINAL : Map.of(), "");
                Rectangle2D bounds = ComponentDrawing.documentBounds(type, component);
                Rectangle2D reach = new Rectangle2D.Double(
                        drawingArea.getCenterX() - (bounds.getWidth() / 2 + MARGIN) * SCALE,
                        drawingArea.getCenterY() - (bounds.getHeight() / 2 + MARGIN) * SCALE,
                        (bounds.getWidth() + 2 * MARGIN) * SCALE, (bounds.getHeight() + 2 * MARGIN) * SCALE);
                assertThat(drawingArea.contains(reach)).as(type.id() + " " + rotation + " fits its cell").isTrue();
                Graphics2D cell = (Graphics2D) graphics.create();
                cell.translate(drawingArea.getCenterX(), drawingArea.getCenterY());
                cell.scale(SCALE, SCALE);
                cell.translate(-bounds.getCenterX(), -bounds.getCenterY());
                ComponentDrawing.paint(cell, type, component, state, PaintersTest.SCHEME, painters);
                cell.dispose();
            }
        }
        graphics.dispose();
        Path output = Path.of("build/painter-gallery.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
        assertThat(output).exists();
    }

    /**
     * The Terminal's looks side by side for visual review ({@code build/terminal-gallery.png}): each profile with its
     * screen effect, each ANSI palette on the same coloured text, and each cursor (Automan's star at a moment of its
     * motion).
     */
    @Test
    void writesAGalleryOfTheTerminalLooksForVisualReview() throws Exception {
        List<Map<String, Object>> looks = new ArrayList<>();
        ComponentType standard = StandardComponentTypes.registry().require(StandardComponentTypes.TERMINAL);
        for (String profile : TerminalTypes.PROFILES) {
            Map<String, Object> parameters = new HashMap<>(standard.profile(profile).orElseThrow().values());
            parameters.put(TerminalTypes.PROFILE, profile);
            looks.add(parameters);
        }
        for (String palette : TerminalTypes.ANSI_PALETTE_CHOICES) {
            looks.add(Map.of(TerminalTypes.ANSI_PALETTE, palette));
        }
        for (String screenColors : TerminalTypes.SCREEN_COLOR_CHOICES) {
            looks.add(Map.of(TerminalTypes.SCREEN_COLORS, screenColors));
        }
        for (String cursor : TerminalTypes.CURSOR_CHOICES) {
            looks.add(Map.of(TerminalTypes.CURSOR, cursor, TerminalTypes.FONT_SIZE, TerminalTypes.FONT_SIZE_LARGE));
        }
        TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(4, 20));
        for (byte value : ("> hola\r\n\u001b[31mrojo \u001b[1;32mverde \u001b[0;44mazul\u001b[0m\r\n"
                + "\u001b[7m inverso \u001b[0m \u001b[4mraya\u001b[0m\r\nfact(5)=120").getBytes(StandardCharsets.UTF_8)) {
            screen.write(value & 0xFF, 0);
        }
        VisibleState state = new VisibleState.TerminalState(1, screen.version(), screen.frame(), false);
        TerminalPainter painter = (TerminalPainter) PainterRegistry.standard()
                .painterFor(StandardComponentTypes.TERMINAL);
        BufferedImage image = PaintersTest.blankImage(TERMINAL_TILE_WIDTH, TERMINAL_TILE_HEIGHT * looks.size());
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int row = 0; row < looks.size(); row++) {
            Map<String, Object> parameters = new HashMap<>(looks.get(row));
            parameters.putAll(GALLERY_TERMINAL);
            ComponentType type = standard.specializedFor(parameters);
            TerminalMoment moment = new TerminalMoment(TerminalAnimation.LIVE, 1.0, true, 0,
                    Optional.empty(), 0, TerminalMoment.NEVER);
            Graphics2D tile = (Graphics2D) graphics.create();
            tile.translate(MARGIN, row * TERMINAL_TILE_HEIGHT + MARGIN);
            tile.scale(TERMINAL_SCALE, TERMINAL_SCALE);
            painter.paintLive(tile, type, parameters, state, PaintersTest.SCHEME,
                    Optional.of(String.valueOf(looks.get(row).values())), moment);
            tile.dispose();
        }
        graphics.dispose();
        Path output = Path.of("build/terminal-gallery.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
        assertThat(output).exists();
    }
}
