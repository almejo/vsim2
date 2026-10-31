package cl.almejo.vsim.core.tunnel;

import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFragment;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pasting an entrada whose name the target already has gives it a free name, so a name never gets two entradas. */
class PasteTunnelRenameTest {

    private final TunnelCircuit source = new TunnelCircuit();
    private final TunnelCircuit target = new TunnelCircuit();

    private CircuitFragment fragmentOf(Component... components) {
        return CircuitFragment.of(source.document(), Arrays.stream(components).map(Component::id).toList(),
                source.registry());
    }

    private List<String> namesOf(TunnelCircuit circuit, TunnelKind kind) {
        return circuit.document().components().stream()
                .filter(component -> Tunnels.kindOf(component).orElse(null) == kind)
                .map(Tunnels::nameOf).toList();
    }

    private void assertNoDuplicateEntrances(TunnelCircuit circuit) {
        assertThat(Tunnels.groups(circuit.document()).values())
                .allSatisfy(group -> assertThat(group.entrances().size()).isLessThanOrEqualTo(1));
    }

    @Test
    void anEntranceWhoseNameIsTakenGetsAFreeName() {
        target.entrance("A", new GridPoint(10, 10));
        target.exit("A_2", new GridPoint(30, 30));
        Component entrance = source.entrance("A", new GridPoint(0, 0));
        new PasteCommand(fragmentOf(entrance), new GridPoint(0, 20)).apply(target.document());
        assertThat(namesOf(target, TunnelKind.ENTRANCE)).containsExactly("A", "A_3");
        assertNoDuplicateEntrances(target);
    }

    @Test
    void anEntranceAndItsExitsAreRenamedTogether() {
        target.entrance("A", new GridPoint(10, 10));
        Component entrance = source.entrance("A", new GridPoint(0, 0));
        Component exit = source.exit("A", new GridPoint(20, 0));
        new PasteCommand(fragmentOf(entrance, exit), new GridPoint(0, 20)).apply(target.document());
        assertThat(namesOf(target, TunnelKind.ENTRANCE)).containsExactly("A", "A_2");
        assertThat(namesOf(target, TunnelKind.EXIT)).containsExactly("A_2");
        Component pastedExit = target.document().components().get(2);
        Component pastedEntrance = target.document().components().get(1);
        // The pins keep their distance (20, 0): renaming the salida did not move its pin.
        assertThat(target.pin(pastedExit, 0).x() - target.pin(pastedEntrance, 0).x()).isEqualTo(20);
        assertThat(target.pin(pastedExit, 0).y()).isEqualTo(target.pin(pastedEntrance, 0).y());
        assertNoDuplicateEntrances(target);
    }

    @Test
    void anExitAloneKeepsItsName() {
        target.entrance("A", new GridPoint(10, 10));
        Component exit = source.exit("A", new GridPoint(0, 0));
        new PasteCommand(fragmentOf(exit), new GridPoint(0, 20)).apply(target.document());
        assertThat(namesOf(target, TunnelKind.EXIT)).containsExactly("A");
    }

    @Test
    void aFreeNameIsKept() {
        target.entrance("B", new GridPoint(10, 10));
        Component entrance = source.entrance("A", new GridPoint(0, 0));
        new PasteCommand(fragmentOf(entrance), new GridPoint(0, 20)).apply(target.document());
        assertThat(namesOf(target, TunnelKind.ENTRANCE)).containsExactly("B", "A");
    }

    @Test
    void oneUndoRemovesThePasteAndRedoKeepsTheNewName() {
        target.entrance("A", new GridPoint(10, 10));
        Component entrance = source.entrance("A", new GridPoint(0, 0));
        PasteCommand paste = new PasteCommand(fragmentOf(entrance), new GridPoint(0, 20));
        paste.apply(target.document());
        paste.revert(target.document());
        assertThat(namesOf(target, TunnelKind.ENTRANCE)).containsExactly("A");
        paste.apply(target.document());
        assertThat(namesOf(target, TunnelKind.ENTRANCE)).containsExactly("A", "A_2");
    }
}
