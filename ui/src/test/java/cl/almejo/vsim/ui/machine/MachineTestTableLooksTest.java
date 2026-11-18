package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the test table looks, light and dark (hito 4, task 5): writes {@code ui/build/machine-test-table-light.png} and
 * {@code -dark.png} for the report (not committed), and checks the colours the user chose for ✓, ✗ and «no importa».
 */
class MachineTestTableLooksTest {

    private static final int WIDTH = 760;
    private static final int HEIGHT = 330;
    private static final double NEAR = 40;

    /** The detector with x = 0110 1100, Z expected 1 in columns 3 (right) and 5 (Mealy thinking: wrong). */
    private static StateMachine detectorWithATest() {
        StateMachine detector = MachineFixtures.detector()
                .withState("s2", state -> state.withColor(Optional.of(StateColor.GREEN)));
        MachineTest test = MachineTest.empty("p1", "Dos 1 seguidos", MachineTests.DEFAULT_COLUMNS);
        boolean[] inputs = {false, true, true, false, true, true, false, false};
        for (int column = 0; column < inputs.length; column++) {
            test = MachineTests.withInput(test, column, "x", inputs[column]);
        }
        test = MachineTests.withExpected(test, 3, "Z", LogicValue.ONE);
        test = MachineTests.withExpected(test, 5, "Z", LogicValue.ONE);
        test = MachineTests.withExpected(test, 0, "Z", LogicValue.ZERO);
        MachineTest zeros = MachineTest.empty("p2", "Solo ceros", 4);
        return detector.withTests(List.of(test, zeros));
    }

    @Test
    void theTableInTheLightAndTheDarkTheme() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            render(new FlatLightLaf(), ColorSchemes.LIGHT, "light");
            render(new FlatDarkLaf(), ColorSchemes.DARK, "dark");
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    private static void render(LookAndFeel theme, ColorScheme scheme, String name) throws Exception {
        UIManager.setLookAndFeel(theme);
        FakeTestSession session = new FakeTestSession(detectorWithATest());
        session.chooseColumn(2);
        MachineTestTable table = new MachineTestTable(session, () -> scheme);
        SwingUtilities.updateComponentTreeUI(table);
        table.setSize(WIDTH, HEIGHT);
        layOut(table);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            table.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        write(image, "machine-test-table-" + name + ".png");

        BufferedImage cells = paintGrid(table.grid());
        MachineTestTableModel model = table.grid().model();
        int verdict = model.rows().size() - 1;
        assertThat(near(cells, model.cell(verdict, 3), MachineTestCellPainter.pass(scheme)))
                .as("a ✓ in the chosen green").isTrue();
        assertThat(near(cells, model.cell(verdict, 5), scheme.wireConflict())).as("a ✗ in the error red").isTrue();
        int expected = verdict - 1;
        assertThat(near(cells, model.cell(expected, 1), scheme.wireUnknown())).as("«·» in grey").isTrue();
        assertThat(near(cells, model.cell(expected, 1), scheme.wireConflict())).as("never a red x").isFalse();
    }

    private static BufferedImage paintGrid(MachineTestTableGrid grid) {
        grid.setSize(grid.getPreferredSize());
        BufferedImage image = new BufferedImage(grid.getWidth(), grid.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            grid.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static boolean near(BufferedImage image, Rectangle cell, Color colour) {
        for (int x = cell.x + 2; x < cell.x + cell.width - 2; x++) {
            for (int y = cell.y + 2; y < cell.y + cell.height - 2; y++) {
                Color pixel = new Color(image.getRGB(x, y));
                double distance = Math.sqrt(Math.pow(pixel.getRed() - colour.getRed(), 2)
                        + Math.pow(pixel.getGreen() - colour.getGreen(), 2)
                        + Math.pow(pixel.getBlue() - colour.getBlue(), 2));
                if (distance < NEAR) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void layOut(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                layOut(child);
            }
        }
    }

    private static void write(BufferedImage image, String name) throws IOException {
        Path output = Path.of("build", name);
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
    }
}
