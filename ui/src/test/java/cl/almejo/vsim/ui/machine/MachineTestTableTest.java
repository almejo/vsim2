package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The test table of «Modo prueba» (hito 4, task 5), headless, on a fake session. */
class MachineTestTableTest {

    private static final String X = "x";
    private static final String Z = "Z";

    private final FakeTestSession session = new FakeTestSession(MachineFixtures.detector());
    private final AtomicReference<ColorScheme> scheme = new AtomicReference<>(ColorSchemes.LIGHT);
    private final MachineTestTable table = new MachineTestTable(session, scheme::get);

    private MachineTestTableModel model() {
        return table.grid().model();
    }

    private List<String> rowLabels() {
        return model().rows().stream().map(MachineTestTableModel.Row::label).toList();
    }

    private int rowOf(MachineTestTableModel.Kind kind, String pinId) {
        List<MachineTestTableModel.Row> rows = model().rows();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).kind() == kind && rows.get(index).pinId().filter(pinId::equals).isPresent()) {
                return index;
            }
        }
        throw new AssertionError("no row " + kind + " " + pinId);
    }

    private int stateRow() {
        List<MachineTestTableModel.Row> rows = model().rows();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).kind() == MachineTestTableModel.Kind.STATE) {
                return index;
            }
        }
        throw new AssertionError("no state row");
    }

    private MachineTest test() {
        return session.machine().tests().get(session.chosenTest());
    }

    private void click(int row, int column, int modifiers) {
        Rectangle cell = model().cell(row, column);
        mouse(table.grid(), new Point(cell.x + cell.width / 2, cell.y + cell.height / 2), modifiers,
                MouseEvent.BUTTON1, 1);
    }

    private static void mouse(JComponent component, Point point, int modifiers, int button, int clicks) {
        MouseEvent event = new MouseEvent(component, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), modifiers,
                point.x, point.y, clicks, false, button);
        for (MouseListener listener : component.getMouseListeners()) {
            if (listener.getClass().getName().startsWith("cl.almejo")) {
                listener.mousePressed(event);
            }
        }
    }

    private void key(int keyCode, int modifiers) {
        KeyStroke stroke = KeyStroke.getKeyStroke(keyCode, modifiers);
        Object name = table.grid().getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
        assertThat(name).as("bound: %s", stroke).isNotNull();
        Action action = table.grid().getActionMap().get(name);
        action.actionPerformed(new ActionEvent(table.grid(), ActionEvent.ACTION_PERFORMED, ""));
    }

    private static void perform(JMenuItem item) {
        assertThat(item.isEnabled()).as("enabled: %s", item.getText()).isTrue();
        item.doClick();
    }

    private static JMenuItem item(JPopupMenu menu, String key) {
        return Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast)
                .filter(item -> item.getText().equals(Messages.text(key))).findFirst()
                .orElseThrow(() -> new AssertionError("no item " + key));
    }

    @Test
    void theDetectorShowsItsInputTheStateItsOutputWhatWasExpectedAndTheVerdict() {
        assertThat(rowLabels()).containsExactly(Messages.text("machine.test.row.inputs"), X,
                Messages.text("machine.test.row.state"), Messages.text("machine.test.row.outputs"), Z,
                Messages.format("machine.test.row.expected", Z), Messages.text("machine.test.row.verdict"));
        assertThat(model().columnCount()).isEqualTo(MachineTests.DEFAULT_COLUMNS);
    }

    @Test
    void aBusShowsItsHeaderAndOneRowPerBit() {
        StateMachine bus = MachineFixtures.detector().withPinNames(List.of(X, "y"), List.of(Z)).withBus(true, false);
        session.replaceMachine(bus);

        assertThat(rowLabels()).startsWith(Messages.text("machine.test.row.inputs"), "in[1:0] = x·0 y·1", X, "y",
                Messages.text("machine.test.row.state"));
        assertThat(model().rows().get(1).kind()).isEqualTo(MachineTestTableModel.Kind.BUS);
    }

    @Test
    void theEmptyFirstTestShowsButIsWrittenOnlyWhenACellChanges() {
        assertThat(table.tabs().getTabCount()).isEqualTo(1);
        assertThat(table.tabs().getTitleAt(0)).startsWith(Messages.format("machine.test.defaultName", 1));
        assertThat(session.steps()).isEmpty();
        assertThat(session.machine().tests()).isEmpty();

        click(rowOf(MachineTestTableModel.Kind.INPUT, X), 2, 0);

        assertThat(session.machine().tests()).hasSize(1);
        assertThat(test().name()).isEqualTo(Messages.format("machine.test.defaultName", 1));
        assertThat(test().columns()).hasSize(MachineTests.DEFAULT_COLUMNS);
    }

    @Test
    void aClickOnAnInputTogglesIt() {
        int row = rowOf(MachineTestTableModel.Kind.INPUT, X);

        click(row, 2, 0);
        assertThat(test().columns().get(2).input(X)).isTrue();
        click(row, 2, 0);
        assertThat(test().columns().get(2).input(X)).isFalse();

        assertThat(session.steps()).containsExactly(Messages.text("machine.test.step.input"),
                Messages.text("machine.test.step.input"));
        assertThat(session.chosenColumn()).isEqualTo(2);
    }

    @Test
    void aClickOnAnExpectedCellCyclesNothingZeroOneNothing() {
        int row = rowOf(MachineTestTableModel.Kind.EXPECTED, Z);
        List<LogicValue> seen = new ArrayList<>();
        for (int click = 0; click < 3; click++) {
            click(row, 1, 0);
            seen.add(test().columns().get(1).expected(Z));
        }
        assertThat(seen).containsExactly(LogicValue.ZERO, LogicValue.ONE, LogicValue.DONT_CARE);
        assertThat(session.steps()).containsOnly(Messages.text("machine.test.step.expected")).hasSize(3);
    }

    @Test
    void aTypedValueFillsTheBlockInOneStep() {
        click(rowOf(MachineTestTableModel.Kind.INPUT, X), 0, InputEvent.SHIFT_DOWN_MASK);
        key(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
        key(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
        key(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK);

        key(KeyEvent.VK_1, 0);

        for (int column = 0; column < 3; column++) {
            assertThat(test().columns().get(column).input(X)).as("x in %d", column).isTrue();
            assertThat(test().columns().get(column).expected(Z)).as("Z in %d", column).isEqualTo(LogicValue.ONE);
        }
        assertThat(test().columns().get(3).input(X)).isFalse();
        assertThat(session.steps()).containsExactly(Messages.text("machine.test.step.fill"));

        key(KeyEvent.VK_DELETE, 0);
        assertThat(test().columns().get(1).expected(Z)).isEqualTo(LogicValue.DONT_CARE);
        assertThat(test().columns().get(1).input(X)).as("an input has no «no importa»").isTrue();
    }

    @Test
    void addingAColumnCopiesTheLastInputs() {
        click(rowOf(MachineTestTableModel.Kind.INPUT, X), 7, 0);

        table.addColumnButton().doClick();

        assertThat(test().columns()).hasSize(9);
        assertThat(test().columns().get(8).input(X)).isTrue();
        assertThat(session.steps()).last().isEqualTo(Messages.text("machine.test.step.addColumn"));
    }

    @Test
    void aColumnIsInsertedAndDeletedFromItsHeaderMenu() {
        click(rowOf(MachineTestTableModel.Kind.INPUT, X), 1, 0);

        perform(item(table.columnMenu(2), "machine.test.column.insertBefore"));
        assertThat(test().columns()).hasSize(9);
        assertThat(test().columns().get(2).input(X)).as("holds the column before").isTrue();

        perform(item(table.columnMenu(1), "machine.test.column.delete"));
        assertThat(test().columns()).hasSize(8);
        assertThat(test().columns().get(1).input(X)).isTrue();
        assertThat(session.steps()).endsWith(Messages.text("machine.test.step.insertColumn"),
                Messages.text("machine.test.step.deleteColumn"));
    }

    @Test
    void atSixtyFourColumnsAddingIsOffAndSaysWhy() {
        MachineTest full = MachineTest.empty("p1", "Larga", MachineTests.MAXIMUM_COLUMNS);
        session.replaceMachine(session.machine().withTests(List.of(full)));

        assertThat(table.addColumnButton().isEnabled()).isFalse();
        assertThat(table.addColumnButton().getToolTipText())
                .isEqualTo(Messages.format("machine.test.column.full", MachineTests.MAXIMUM_COLUMNS));
        assertThat(item(table.columnMenu(0), "machine.test.column.insertBefore").isEnabled()).isFalse();
    }

    @Test
    void testsAreAddedRenamedDuplicatedAndDeletedEachInOneStep() {
        table.newTestButton().doClick();
        assertThat(session.machine().tests()).extracting(MachineTest::name)
                .containsExactly(Messages.format("machine.test.defaultName", 1),
                        Messages.format("machine.test.defaultName", 2));
        assertThat(session.chosenTest()).isEqualTo(1);

        JTextField editor = table.startRenaming(1);
        editor.setText("Dos 1 seguidos");
        editor.postActionEvent();
        assertThat(session.machine().tests().get(1).name()).isEqualTo("Dos 1 seguidos");

        perform(item(table.tabMenu(1), "machine.test.menu.duplicate"));
        assertThat(session.machine().tests()).hasSize(3);
        assertThat(session.machine().tests().get(2).name())
                .isEqualTo(Messages.format("machine.test.copyName", "Dos 1 seguidos"));

        perform(item(table.tabMenu(0), "machine.test.menu.delete"));
        assertThat(session.machine().tests()).extracting(MachineTest::name).containsExactly("Dos 1 seguidos",
                Messages.format("machine.test.copyName", "Dos 1 seguidos"));

        assertThat(session.steps()).containsExactly(Messages.text("machine.test.step.addTest"),
                Messages.text("machine.test.step.renameTest"), Messages.text("machine.test.step.duplicateTest"),
                Messages.text("machine.test.step.deleteTest"));
        assertThat(table.tabs().getTabCount()).isEqualTo(2);
    }

    @Test
    void atSixteenTestsANewOneIsOffAndSaysWhy() {
        List<MachineTest> tests = new ArrayList<>();
        for (int index = 1; index <= MachineTests.MAXIMUM_TESTS; index++) {
            tests.add(MachineTest.empty("p" + index, "Prueba " + index, 2));
        }
        session.replaceMachine(session.machine().withTests(tests));

        assertThat(table.newTestButton().isEnabled()).isFalse();
        assertThat(table.newTestButton().getToolTipText())
                .isEqualTo(Messages.format("machine.test.tab.full", MachineTests.MAXIMUM_TESTS));
        assertThat(item(table.tabMenu(0), "machine.test.menu.duplicate").isEnabled()).isFalse();
    }

    @Test
    void aMismatchIsTintedAndSaysWhatCameOut() {
        int expected = rowOf(MachineTestTableModel.Kind.EXPECTED, Z);
        click(expected, 0, 0);
        click(expected, 0, 0); // · → 0 → 1: Z = 1 expected in S0, where Z = 0
        int output = rowOf(MachineTestTableModel.Kind.OUTPUT, Z);

        Rectangle cell = model().cell(output, 0);
        MouseEvent hover = new MouseEvent(table.grid(), MouseEvent.MOUSE_MOVED, 0, 0, cell.x + cell.width / 2,
                cell.y + 2, 0, false);
        assertThat(table.grid().getToolTipText(hover)).isEqualTo(Messages.format("machine.test.cell.mismatch", 1, 0));
        assertThat(table.tabs().getTitleAt(0)).endsWith("✗");
        assertThat(table.summaryText()).contains("✗").contains(Messages.format("machine.test.summary.failure", 1,
                Z, 0));

        BufferedImage image = paint();
        Color pixel = new Color(image.getRGB(cell.x + 3, cell.y + 3));
        Color background = ColorSchemes.LIGHT.diagramBackground();
        assertThat(pixel.getRed()).as("the fail tint pulls towards red").isGreaterThan(background.getRed() + 15);
    }

    @Test
    void choosingAColumnTellsTheSessionAndTheArrowsMoveIt() {
        mouse(table.header(), new Point(model().cell(0, 3).x + 5, 5), 0, MouseEvent.BUTTON1, 1);
        assertThat(session.chosenColumn()).isEqualTo(3);

        key(KeyEvent.VK_RIGHT, 0);
        assertThat(session.chosenColumn()).isEqualTo(4);
        key(KeyEvent.VK_LEFT, 0);
        key(KeyEvent.VK_LEFT, 0);
        assertThat(session.chosenColumn()).isEqualTo(2);
        assertThat(session.steps()).as("choosing is no step").isEmpty();
    }

    @Test
    void theGhostColumnSaysWhereTheLastEdgeLeads() {
        assertThat(model().ghostText()).contains(Messages.format("machine.test.ghost", "S0"));

        int input = rowOf(MachineTestTableModel.Kind.INPUT, X);
        click(input, 6, 0);
        click(input, 7, 0); // x = 1 on the last two edges: S0 → S1 → S2
        assertThat(model().ghostText()).contains(Messages.format("machine.test.ghost", "S2"));
        assertThat(stateRow()).isGreaterThan(input);
    }

    @Test
    void aDiagramEditRunsTheTestAgain() {
        int input = rowOf(MachineTestTableModel.Kind.INPUT, X);
        click(input, 6, 0);
        click(input, 7, 0);
        StateMachine withoutTheSecondArrow = session.machine().withTransitions(session.machine().transitions()
                .stream().filter(transition -> !transition.id().equals("t3")).toList());

        session.replaceMachine(withoutTheSecondArrow);

        assertThat(model().ghostText()).contains(Messages.format("machine.test.ghost", "S1"));
    }

    @Test
    void closingStopsListening() {
        int before = session.listenerCount();
        table.close();
        assertThat(session.listenerCount()).isEqualTo(before - 1);
    }

    @Test
    void theDarkThemeIsReadWhenPainting() {
        BufferedImage light = paint();
        scheme.set(ColorSchemes.DARK);
        BufferedImage dark = paint();
        Rectangle ghost = model().cell(model().rows().size() - 1, model().columnCount());
        int x = ghost.x + ghost.width - 2;
        int y = ghost.y + 2;
        assertThat(new Color(light.getRGB(x, y))).isEqualTo(ColorSchemes.LIGHT.diagramBackground());
        assertThat(new Color(dark.getRGB(x, y))).isEqualTo(ColorSchemes.DARK.diagramBackground());
    }

    private BufferedImage paint() {
        MachineTestTableGrid grid = table.grid();
        grid.setSize(grid.getPreferredSize());
        BufferedImage image = new BufferedImage(grid.getWidth(), grid.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            grid.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
