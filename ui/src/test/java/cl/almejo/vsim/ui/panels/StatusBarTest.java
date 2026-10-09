package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StatusBarTest {

    private final StatusBar statusBar = new StatusBar();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private static SimulationSnapshot snapshot(boolean running, int shorts, Optional<String> failure) {
        List<ShortCircuit> problems = java.util.stream.IntStream.range(0, shorts)
                .mapToObj(index -> new ShortCircuit("n" + index, List.of(new DriverRef("c1", 0, Value.ONE)), 0))
                .toList();
        return new SimulationSnapshot(1, 1, 1234, running, Map.of(), Map.of(), List.copyOf(problems), failure);
    }

    @Test
    void showsStateTimeAndZoom() {
        statusBar.showSnapshot(snapshot(true, 0, Optional.empty()));
        statusBar.showZoom(1.25);
        assertThat(statusBar.text()).isEqualTo("▶ corriendo · t = 1.234 · zoom 125 %");
        statusBar.showSnapshot(snapshot(false, 0, Optional.empty()));
        assertThat(statusBar.text()).startsWith("⏸ en pausa");
    }

    @Test
    void showsShortCircuitsOnlyWhenThereAreSome() {
        statusBar.showZoom(1);
        statusBar.showSnapshot(snapshot(true, 2, Optional.empty()));
        assertThat(statusBar.text()).doesNotContain("⚠"); // the Problems controller feeds the count
        statusBar.showShortCircuits(2);
        assertThat(statusBar.text()).contains("⚠ 2 cortocircuitos");
        statusBar.showShortCircuits(0);
        assertThat(statusBar.text()).doesNotContain("⚠");
    }

    @Test
    void showsTheSimulationFailure() {
        statusBar.showZoom(1);
        statusBar.showSnapshot(snapshot(false, 0, Optional.of("IllegalStateException: boom")));
        assertThat(statusBar.text()).contains("Simulación detenida: IllegalStateException: boom");
    }

    @Test
    void showsHowManyElementsAreSelectedOnlyWhenThereAreSome() {
        statusBar.showZoom(1);
        statusBar.showSelection(0);
        assertThat(statusBar.text()).doesNotContain("seleccionado");
        statusBar.showSelection(1);
        assertThat(statusBar.text()).endsWith("zoom 100 % · 1 seleccionado");
        statusBar.showSelection(3);
        assertThat(statusBar.text()).endsWith("3 seleccionados");
        statusBar.showSelection(0);
        assertThat(statusBar.text()).doesNotContain("seleccionado");
    }

    @Test
    void clickingTheShortCircuitWarningRunsTheCallback() {
        AtomicInteger clicks = new AtomicInteger();
        statusBar.onShortCircuitsClicked(clicks::incrementAndGet);
        statusBar.showShortCircuits(1);
        JLabel warning = statusBar.shortCircuitsLabel();
        MouseEvent click = new MouseEvent(warning, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON1);
        for (MouseListener listener : warning.getMouseListeners()) {
            listener.mouseClicked(click);
        }
        assertThat(clicks).hasValue(1);
        assertThat(warning.getCursor().getType()).isEqualTo(Cursor.HAND_CURSOR);
        assertThat(warning.getToolTipText()).isEqualTo("Clic para ir al siguiente problema (F8)");
    }

    @Test
    void showsWidthMismatchesWithOrWithoutShortCircuits() {
        statusBar.showZoom(1);
        statusBar.showProblems(0, 1);
        assertThat(statusBar.text()).contains("⚠ 1 ancho incompatible");
        statusBar.showProblems(2, 3);
        assertThat(statusBar.text()).contains("⚠ 2 cortocircuitos · 3 anchos incompatibles");
        statusBar.showProblems(1, 0);
        assertThat(statusBar.text()).contains("⚠ 1 cortocircuito").doesNotContain("ancho");
        statusBar.showProblems(0, 0);
        assertThat(statusBar.text()).doesNotContain("⚠");
    }

    @Test
    void theWarningTooltipIsNeutralWhenWidthMismatchesAreCounted() {
        statusBar.onShortCircuitsClicked(() -> { });
        JLabel warning = statusBar.shortCircuitsLabel();
        statusBar.showProblems(1, 0);
        assertThat(warning.getToolTipText()).isEqualTo("Clic para ir al siguiente problema (F8)");
        statusBar.showProblems(0, 1);
        assertThat(warning.getToolTipText()).isEqualTo("Clic para ir al siguiente problema o ancho incompatible (F8)");
        statusBar.showProblems(2, 1);
        assertThat(warning.getToolTipText()).isEqualTo("Clic para ir al siguiente problema o ancho incompatible (F8)");
        statusBar.showProblems(1, 0);
        assertThat(warning.getToolTipText()).isEqualTo("Clic para ir al siguiente problema (F8)");
    }
}
