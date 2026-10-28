package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** Hovering a multi-bit Switch: its value in four notations and the bit under the pointer. */
class MultiBitTooltipTest {

    /** The pixel over bit 3 of a 4-bit switch at the origin (cell x 4..12, y 5..19, bit 0 at x 28..36 at 8 pixels per unit). */
    private static final int BIT_THREE_X = 8;
    private static final int BIT_THREE_Y = 12;
    private static final int BIT_ZERO_X = 32;

    private OpenDocument openDocument;
    private EditorView view;
    private Component lever;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        lever = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of("width", 4, "value", "0b1010"));
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private MouseEvent hoverAt(int x, int y) {
        return new MouseEvent(view.canvas(), MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false);
    }

    private void show(Value value) {
        view.problems().showSnapshot(new SimulationSnapshot(1, openDocument.netlistToken(), 10, true, Map.of(),
                Map.of(lever.id().value(), new VisibleState.SwitchState(value)), List.of(), Optional.empty()));
    }

    @Test
    void theValueIsWrittenInBinaryHexDecimalAndSignedWithTheBitUnderThePointer() {
        show(Value.of(4, 0b1010));

        assertThat(view.canvas().getToolTipText(hoverAt(BIT_THREE_X, BIT_THREE_Y)))
                .isEqualTo("0b1010 = 0xA = 10 (con signo: -6) · bit 3");
        assertThat(view.canvas().getToolTipText(hoverAt(BIT_ZERO_X, BIT_THREE_Y)))
                .isEqualTo("0b1010 = 0xA = 10 (con signo: -6) · bit 0");
    }

    @Test
    void aStaleSnapshotStillNamesTheBit() {
        assertThat(view.canvas().getToolTipText(hoverAt(BIT_THREE_X, BIT_THREE_Y))).isEqualTo("bit 3");
    }

    @Test
    void betweenCellsOnlyTheValueShows() {
        show(Value.of(4, 0b1010));

        assertThat(view.canvas().getToolTipText(hoverAt(1, 1))).isEqualTo("0b1010 = 0xA = 10 (con signo: -6)");
    }

    @Test
    void aOneBitSwitchHasNoTooltip() {
        Component oneBit = place(openDocument, StandardComponentTypes.SWITCH, 0, 10, Map.of("value", "1"));

        assertThat(oneBit).isNotNull();
        assertThat(view.canvas().getToolTipText(hoverAt(20, 88))).isNull();
    }

    @Test
    void aLedNamesItsBitAndWritesAZBitByBitInsteadOfAsANumber() {
        Component led = place(openDocument, StandardComponentTypes.LED, 0, 10, Map.of("width", 4));
        int bodyLeft = MultiBitLayout.LEAD_UNITS * GridPoint.PIXELS_PER_UNIT;
        int rowY = 10 * GridPoint.PIXELS_PER_UNIT + BIT_THREE_Y;
        view.problems().showSnapshot(new SimulationSnapshot(1, openDocument.netlistToken(), 10, true, Map.of(),
                Map.of(led.id().value(), new VisibleState.LedState(new Value(4, 0b1001, 0b0010, false))), List.of(),
                Optional.empty()));

        assertThat(view.canvas().getToolTipText(hoverAt(bodyLeft + BIT_ZERO_X - 8, rowY))).isEqualTo("10Z1 · bit 1");
        assertThat(view.canvas().getToolTipText(hoverAt(bodyLeft + BIT_THREE_X, rowY))).isEqualTo("10Z1 · bit 3");
    }
}
