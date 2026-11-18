package cl.almejo.vsim.ui.machine;

import org.junit.jupiter.api.AfterEach;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every test of {@link MachineClipboardTest} again, with the diagram read and edited through a machine source installed in front
 * of the circuit's (plan 2026-11-14, hito 4 task 3): the gestures never go around {@code MachineCanvas.machine()} and
 * {@code MachineCanvas.edit}, so the test mode's draft can take their place.
 */
class MachineClipboardThroughSourceTest extends MachineClipboardTest {

    private final List<ForwardingMachineSource> sources = new ArrayList<>();

    @Override
    void throughSource(MachineCanvas shownCanvas) {
        sources.add(ForwardingMachineSource.installOn(shownCanvas));
    }

    @AfterEach
    void everyReadWentThroughTheSource() {
        assertThat(sources).isNotEmpty();
        assertThat(sources).allSatisfy(source -> assertThat(source.reads()).isPositive());
    }
}
