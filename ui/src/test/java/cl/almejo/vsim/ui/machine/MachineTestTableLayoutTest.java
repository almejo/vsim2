package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.theme.ReadableColors;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.Test;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test table's sizes follow the font and the interface scale, its «→ S…» column has the room its text needs, and
 * the texts on its tints and its greys are readable (hito 4, fix round 2, task I points 4 and 5).
 */
class MachineTestTableLayoutTest {

    private static final double MINIMUM = ReadableColors.MINIMUM_TEXT_CONTRAST;

    private static StateMachine namedInitialState(String name) {
        StateMachine detector = MachineFixtures.detector().withState("s0", state -> state.withName(name));
        MachineTest test = MachineTest.empty("p1", "Prueba 1", MachineTests.DEFAULT_COLUMNS);
        test = MachineTests.withExpected(test, 0, "Z", LogicValue.ZERO);
        return detector.withTests(List.of(test));
    }

    private static int stateRow(MachineTestTableModel model) {
        for (int index = 0; index < model.rows().size(); index++) {
            if (model.rows().get(index).kind() == MachineTestTableModel.Kind.STATE) {
                return index;
            }
        }
        throw new AssertionError("no state row");
    }

    @Test
    void theGhostColumnHasRoomForTheWholeStateName() {
        // the detector's last edge goes back to its initial state: «→ INICIO»
        MachineTestTable table = new MachineTestTable(new FakeTestSession(namedInitialState("INICIO")),
                () -> ColorSchemes.LIGHT);
        MachineTestTableModel model = table.grid().model();
        Rectangle ghost = model.cell(stateRow(model), model.columnCount());
        FontMetrics metrics = table.grid().getFontMetrics(table.grid().getFont());
        String text = model.ghostText().orElseThrow();

        assertThat(text).isEqualTo("→ INICIO");
        assertThat(PainterSupport.ellipsized(text, metrics, ghost.width - 4))
                .as("«→ INICIO» whole in a %d px column", ghost.width).isEqualTo(text);
    }

    @Test
    void aLongStateNameStillGetsRoomUpToAReasonableLimit() {
        MachineTestTable table = new MachineTestTable(new FakeTestSession(namedInitialState("ESPERANDO_EL_PRIMERO")),
                () -> ColorSchemes.LIGHT);
        MachineTestTableModel model = table.grid().model();
        Rectangle ghost = model.cell(stateRow(model), model.columnCount());
        Rectangle column = model.cell(stateRow(model), 0);

        assertThat(ghost.width).isGreaterThan(column.width);
        assertThat(model.width()).as("the whole row is as wide as its cells")
                .isEqualTo(model.columnCount() * column.width + ghost.width);
    }

    @Test
    void rowsAndColumnsFollowTheInterfaceScale() throws Exception {
        MachineTestTable normal = new MachineTestTable(new FakeTestSession(namedInitialState("INICIO")),
                () -> ColorSchemes.LIGHT);
        MachineTestTableModel small = normal.grid().model();
        int smallRow = small.rows().get(stateRow(small)).height();
        int smallColumn = small.cell(0, 0).width;

        UiTestSupport.atDoubleUiScale(() -> {
            MachineTestTable doubled = new MachineTestTable(new FakeTestSession(namedInitialState("INICIO")),
                    () -> ColorSchemes.LIGHT);
            MachineTestTableModel big = doubled.grid().model();
            FontMetrics metrics = doubled.grid().getFontMetrics(doubled.grid().getFont());

            assertThat(big.rows().get(stateRow(big)).height()).as("a row at 200 %").isGreaterThanOrEqualTo(
                    2 * smallRow - 2).isGreaterThan(metrics.getHeight());
            assertThat(big.cell(0, 0).width).as("a column at 200 %").isGreaterThanOrEqualTo(2 * smallColumn - 2);
            assertThat(doubled.grid().header().getPreferredSize().height)
                    .isGreaterThanOrEqualTo(metrics.getHeight());
            assertThat(doubled.grid().names().getPreferredSize().width)
                    .isGreaterThanOrEqualTo(2 * normal.grid().names().getPreferredSize().width - 2);
        });
    }

    @Test
    void theTextsOnTheTintsAndTheGreyAreReadableInEveryScheme() throws Exception {
        for (String id : ColorSchemes.ids()) {
            ColorScheme scheme = ColorSchemes.byId(id).orElseThrow();
            Color background = scheme.diagramBackground();
            Color passTint = ReadableColors.over(PainterSupport.withAlpha(scheme.testPass(),
                    MachineTestCellPainter.TINT_ALPHA), background);
            Color failTint = ReadableColors.over(PainterSupport.withAlpha(scheme.testFail(),
                    MachineTestCellPainter.TINT_ALPHA), background);
            assertThat(ReadableColors.contrast(MachineTestCellPainter.passText(scheme), passTint))
                    .as("✓ on its tint in %s", id).isGreaterThanOrEqualTo(MINIMUM);
            assertThat(ReadableColors.contrast(MachineTestCellPainter.failText(scheme), failTint))
                    .as("✗ on its tint in %s", id).isGreaterThanOrEqualTo(MINIMUM);
            assertThat(ReadableColors.contrast(MachineTestCellPainter.quiet(scheme), background))
                    .as("«·» and the grey texts in %s", id).isGreaterThanOrEqualTo(MINIMUM);
        }
    }

    @Test
    void theHintLineIsTheThemesMutedTextReadAtPaintTime() throws Exception {
        javax.swing.LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
            MachineTestTable table = new MachineTestTable(new FakeTestSession(namedInitialState("INICIO")),
                    () -> ColorSchemes.LIGHT);
            assertThat(table.hint().getForeground()).isEqualTo(ReadableColors.mutedText());
            UIManager.setLookAndFeel(new FlatDarkLaf());
            assertThat(table.hint().getForeground()).as("the theme changed live").isEqualTo(ReadableColors.mutedText());
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void theGreyIsTheThemesMutedTextWhenItReadsOnTheDiagram() throws Exception {
        javax.swing.LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            assertThat(MachineTestCellPainter.quiet(ColorSchemes.DARK)).isEqualTo(ReadableColors.mutedText());
            UIManager.setLookAndFeel(new FlatLightLaf());
            assertThat(MachineTestCellPainter.quiet(ColorSchemes.LOGISIM)).isEqualTo(ReadableColors.mutedText());
        } finally {
            UIManager.setLookAndFeel(previous);
        }
        assertThat(MachineTestCellPainter.TINT_ALPHA).as("the tint stays 18 %").isEqualTo(46);
    }

    /**
     * Fix round 4 (M4): in a 760 px dock the verdict line is whole (it is the useful part); the grey hint beside it
     * gives way first, cut with «…», and both say their whole text in their tooltip.
     */
    @Test
    void theVerdictLineIsWholeInANarrowDockAndTheHintGivesWayFirst() throws Exception {
        for (double scale : new double[]{1, 1.5}) {
            javax.swing.LookAndFeel previous = UIManager.getLookAndFeel();
            try {
                System.setProperty("flatlaf.uiScale", String.valueOf(scale));
                UIManager.setLookAndFeel(new FlatLightLaf());
                MachineTestTable table = new MachineTestTable(new FakeTestSession(MachineFixtures.detector()),
                        () -> ColorSchemes.LIGHT);
                javax.swing.SwingUtilities.updateComponentTreeUI(table);
                table.setSize((int) (760 * scale), (int) (330 * scale));
                UiTestSupport.layOut(table);
                javax.swing.JLabel summary = table.summary();
                javax.swing.JLabel hint = table.hint();

                assertThat(summary.getWidth()).as("«%s» whole at %s x", summary.getText(), scale)
                        .isGreaterThanOrEqualTo(summary.getPreferredSize().width);
                assertThat(hint.getWidth()).as("the hint is cut first").isLessThan(hint.getPreferredSize().width);
                assertThat(summary.getToolTipText()).isEqualTo(summary.getText());
                assertThat(hint.getToolTipText()).isEqualTo(hint.getText());
            } finally {
                System.clearProperty("flatlaf.uiScale");
                UIManager.setLookAndFeel(previous);
            }
        }
    }
}
