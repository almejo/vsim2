package cl.almejo.vsim.core.tunnel;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TunnelsTest {

    private final TunnelCircuit circuit = new TunnelCircuit();

    private String nameOf(Component component) {
        return Tunnels.nameOf(circuit.component(component.id()));
    }

    @Test
    void theKindOfATunnelComesFromItsType() {
        Component entrance = circuit.entrance("A", new GridPoint(0, 0));
        Component exit = circuit.exit("A", new GridPoint(0, 10));
        Component other = circuit.place(StandardComponentTypes.LED, 20, 0, Map.of());

        assertThat(Tunnels.kindOf(entrance)).contains(TunnelKind.ENTRANCE);
        assertThat(Tunnels.kindOf(exit)).contains(TunnelKind.EXIT);
        assertThat(Tunnels.kindOf(other)).isEmpty();
        assertThat(Tunnels.isTunnel(StandardComponentTypes.TUNNEL_EXIT)).isTrue();
        assertThat(Tunnels.isTunnel(StandardComponentTypes.LED)).isFalse();
    }

    @Test
    void groupsCollectTheEntrancesAndExitsOfEachNameSortedByName() {
        Component clockIn = circuit.entrance("CLK", new GridPoint(0, 0));
        Component clockOut = circuit.exit("CLK", new GridPoint(0, 10));
        Component secondClockOut = circuit.exit("CLK", new GridPoint(0, 20));
        Component orphan = circuit.exit("A", new GridPoint(0, 30));

        Map<String, Tunnels.TunnelGroup> groups = Tunnels.groups(circuit.document());

        assertThat(groups.keySet()).containsExactly("A", "CLK");
        assertThat(groups.get("CLK").entrances()).containsExactly(clockIn.id());
        assertThat(groups.get("CLK").exits()).containsExactly(clockOut.id(), secondClockOut.id());
        assertThat(groups.get("A").entrances()).isEmpty();
        assertThat(groups.get("A").exits()).containsExactly(orphan.id());
    }

    @Test
    void aNewEntranceProposesTheFirstNameNoEntranceUses() {
        assertThat(Tunnels.proposedEntranceName(circuit.document())).isEqualTo("T1");
        circuit.entrance("T1", new GridPoint(0, 0));
        circuit.entrance("T3", new GridPoint(0, 10));
        circuit.exit("T2", new GridPoint(0, 20));

        assertThat(Tunnels.proposedEntranceName(circuit.document())).isEqualTo("T2");
    }

    @Test
    void aNewExitProposesTheLastEntranceWhileItStillExistsElseT1() {
        circuit.entrance("CLK", new GridPoint(0, 0));

        assertThat(Tunnels.proposedExitName(circuit.document(), Optional.of("CLK"))).isEqualTo("CLK");
        assertThat(Tunnels.proposedExitName(circuit.document(), Optional.of("GONE"))).isEqualTo("T1");
        assertThat(Tunnels.proposedExitName(circuit.document(), Optional.empty())).isEqualTo("T1");
    }

    @Test
    void aNewEntranceTakesTheFirstColourNoEntranceUsesAndWrapsWhenAllAreUsed() {
        assertThat(Tunnels.freeColor(circuit.document())).isZero();
        circuit.entrance("A", 0, new GridPoint(0, 0));
        circuit.entrance("B", 2, new GridPoint(0, 10));
        assertThat(Tunnels.freeColor(circuit.document())).isEqualTo(1);

        for (int color = 1; color < Tunnels.COLOR_COUNT; color++) {
            if (color != 2) {
                circuit.entrance("C" + color, color, new GridPoint(0, 20 + color * 10));
            }
        }
        circuit.entrance("EXTRA", 0, new GridPoint(50, 0));

        assertThat(Tunnels.freeColor(circuit.document())).isEqualTo(9 % Tunnels.COLOR_COUNT);
    }

    @Test
    void theColourOfANameIsItsEntrancesOrNoneWithoutEntrance() {
        circuit.entrance("CLK", 5, new GridPoint(0, 0));
        circuit.exit("CLK", new GridPoint(0, 10));
        circuit.exit("ORPHAN", new GridPoint(0, 20));

        assertThat(Tunnels.colorOf(circuit.document(), "CLK")).isEqualTo(5);
        assertThat(Tunnels.colorOf(circuit.document(), "ORPHAN")).isEqualTo(Tunnels.NO_COLOR);
    }

    @Test
    void theNamesSplitByASelectionHaveTunnelsOnBothSides() {
        Component clockIn = circuit.entrance("CLK", new GridPoint(0, 0));
        circuit.exit("CLK", new GridPoint(0, 10));
        Component selectIn = circuit.entrance("SEL", new GridPoint(0, 20));
        Component selectOut = circuit.exit("SEL", new GridPoint(0, 30));
        Component dataOut = circuit.exit("DATA", new GridPoint(0, 40));
        circuit.entrance("DATA", new GridPoint(0, 50));

        assertThat(Tunnels.namesSplitBy(circuit.document(),
                List.of(clockIn.id(), selectIn.id(), selectOut.id(), dataOut.id())))
                .containsExactly("CLK", "DATA");
    }

    @Test
    void theColoursByNameTakeTheFirstEntranceOfEachName() {
        Component first = circuit.entrance("CLK", 5, new GridPoint(0, 0));
        circuit.entrance("CLK", 2, new GridPoint(0, 10));
        circuit.entrance("SEL", 1, new GridPoint(0, 20));
        Component exit = circuit.exit("ORPHAN", new GridPoint(0, 30));

        assertThat(Tunnels.colorsByName(circuit.document())).isEqualTo(Map.of("CLK", 5, "SEL", 1));
        assertThat(Tunnels.colorParameterOf(circuit.component(first.id()))).isEqualTo(5);
        assertThat(Tunnels.colorParameterOf(circuit.component(exit.id()))).isEqualTo(Tunnels.NO_COLOR);
    }

    @Test
    void anExitHasNoColourParameter() {
        ComponentType exit = circuit.registry().require(StandardComponentTypes.TUNNEL_EXIT);
        ComponentType entrance = circuit.registry().require(StandardComponentTypes.TUNNEL_ENTRANCE);

        assertThat(exit.parameters()).extracting(parameter -> parameter.name()).containsExactly(Tunnels.NAME);
        assertThat(entrance.parameters()).extracting(parameter -> parameter.name())
                .containsExactly(Tunnels.NAME, Tunnels.COLOR);
    }

    @Test
    void aBlankNameIsRefused() {
        ComponentType entrance = circuit.registry().require(StandardComponentTypes.TUNNEL_ENTRANCE);

        assertThatThrownBy(() -> entrance.resolveParameters(Map.of(Tunnels.NAME, "   ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theTagGrowsWithTheNameAndThePinSitsOneUnitOutsideIt() {
        assertThat(Tunnels.lengthInGridUnits("A")).isLessThan(Tunnels.lengthInGridUnits("CLOCK_ENABLE"));

        Component shortOne = circuit.entrance("A", new GridPoint(0, 0));
        Component longOne = circuit.entrance("CLOCK_ENABLE", new GridPoint(0, 10));
        ComponentType shortType = circuit.registry().typeOf(shortOne);
        ComponentType longType = circuit.registry().typeOf(longOne);

        assertThat(shortType.size().width()).isEqualTo(1 + Tunnels.lengthInGridUnits("A"));
        assertThat(longType.size().width()).isEqualTo(1 + Tunnels.lengthInGridUnits("CLOCK_ENABLE"));
        PinSpec pin = shortType.pin(0);
        assertThat(pin.position().x()).isZero();
        assertThat(pin.isAdaptiveWidth()).isTrue();
    }

    @Test
    void anEntrancesPinIsOnTheLeftAndAnExitsOnTheRight() {
        Component entrance = circuit.entrance("CLK", new GridPoint(0, 0));
        Component exit = circuit.exit("CLK", new GridPoint(0, 10));
        ComponentType entranceType = circuit.registry().typeOf(entrance);
        ComponentType exitType = circuit.registry().typeOf(exit);

        assertThat(entranceType.pin(0).position()).isEqualTo(new GridPoint(0, 1));
        assertThat(exitType.pin(0).position()).isEqualTo(new GridPoint(1 + Tunnels.lengthInGridUnits("CLK"), 1));
        assertThat(exitType.size()).isEqualTo(entranceType.size());
        assertThat(exitType.pin(0).isAdaptiveWidth()).isTrue();
    }

    @Test
    void thePinStaysOnTheWireSideAtEveryRotation() {
        for (Rotation rotation : Rotation.values()) {
            Component tunnel = circuit.place(StandardComponentTypes.TUNNEL_EXIT, 0, 0, rotation,
                    Map.of(Tunnels.NAME, "CLK"));
            ComponentType type = circuit.registry().typeOf(tunnel);
            Bounds bounds = type.bounds(tunnel);
            GridPoint pin = type.pinLocation(tunnel, 0);

            assertThat(pin.x() == bounds.x() || pin.x() == bounds.x() + bounds.width()
                    || pin.y() == bounds.y() || pin.y() == bounds.y() + bounds.height())
                    .as("pin on the edge at " + rotation).isTrue();
        }
    }

    @Test
    void renamingAnEntranceRenamesEveryTunnelOfItsNameInOneUndoStep() {
        Component entrance = circuit.entrance("CLK", new GridPoint(0, 0));
        List<Component> exits = List.of(circuit.exit("CLK", new GridPoint(0, 10)),
                circuit.exit("CLK", new GridPoint(0, 20)), circuit.exit("CLK", new GridPoint(0, 30)));
        Component other = circuit.exit("OTHER", new GridPoint(0, 40));
        History history = new History(circuit.document());

        history.execute(Tunnels.renameCommand(circuit.document(), circuit.registry(), entrance.id(), " TICK "));

        assertThat(nameOf(entrance)).isEqualTo("TICK");
        assertThat(exits).allSatisfy(exit -> assertThat(nameOf(exit)).isEqualTo("TICK"));
        assertThat(nameOf(other)).isEqualTo("OTHER");

        history.undo();
        assertThat(nameOf(entrance)).isEqualTo("CLK");
        assertThat(exits).allSatisfy(exit -> assertThat(nameOf(exit)).isEqualTo("CLK"));

        history.redo();
        assertThat(exits).allSatisfy(exit -> assertThat(nameOf(exit)).isEqualTo("TICK"));
    }

    @Test
    void renamingAnEntranceToANameThatAlreadyHasAnEntranceChangesOnlyIt() {
        Component entrance = circuit.entrance("CLK", new GridPoint(0, 0));
        Component exit = circuit.exit("CLK", new GridPoint(0, 10));
        circuit.entrance("RESET", new GridPoint(0, 20));
        History history = new History(circuit.document());

        history.execute(Tunnels.renameCommand(circuit.document(), circuit.registry(), entrance.id(), "RESET"));

        assertThat(nameOf(entrance)).isEqualTo("RESET");
        assertThat(nameOf(exit)).isEqualTo("CLK");
    }

    @Test
    void renamingATunnelKeepsItsPinWhereItsWireIsWhateverTheKindAndTurn() {
        for (TunnelKind kind : TunnelKind.values()) {
            for (Rotation rotation : Rotation.values()) {
                Component tunnel = circuit.place(kind.typeId(), 10, 10, rotation, Map.of(Tunnels.NAME, "A"));
                GridPoint pin = circuit.pin(tunnel, 0);
                History history = new History(circuit.document());

                history.execute(Tunnels.renameCommand(circuit.document(), circuit.registry(), tunnel.id(),
                        "CLOCK_ENABLE"));

                assertThat(circuit.pin(circuit.component(tunnel.id()), 0)).as(kind + " " + rotation).isEqualTo(pin);
                history.undo();
                assertThat(circuit.component(tunnel.id())).isEqualTo(tunnel);
                circuit.document().remove(tunnel.id());
            }
        }
    }

    @Test
    void renamingOneOfTwoEntrancesOfANameChangesOnlyThatOne() {
        Component original = circuit.entrance("CLK", new GridPoint(0, 0));
        Component copy = circuit.entrance("CLK", new GridPoint(0, 10));
        Component exit = circuit.exit("CLK", new GridPoint(0, 20));
        History history = new History(circuit.document());

        history.execute(Tunnels.renameCommand(circuit.document(), circuit.registry(), copy.id(), "CLK2"));

        assertThat(Tunnels.groups(circuit.document())).containsOnly(
                Map.entry("CLK", new Tunnels.TunnelGroup("CLK", List.of(original.id()), List.of(exit.id()))),
                Map.entry("CLK2", new Tunnels.TunnelGroup("CLK2", List.of(copy.id()), List.of())));
    }

    @Test
    void renamingAnExitChangesOnlyThatExit() {
        circuit.entrance("CLK", new GridPoint(0, 0));
        Component renamed = circuit.exit("CLK", new GridPoint(0, 10));
        Component kept = circuit.exit("CLK", new GridPoint(0, 20));
        History history = new History(circuit.document());

        history.execute(Tunnels.renameCommand(circuit.document(), circuit.registry(), renamed.id(), "RESET"));

        assertThat(nameOf(renamed)).isEqualTo("RESET");
        assertThat(nameOf(kept)).isEqualTo("CLK");
        history.undo();
        assertThat(nameOf(renamed)).isEqualTo("CLK");
    }

    @Test
    void theRenameCommandHasItsOwnDescription() {
        Component entrance = circuit.entrance("CLK", new GridPoint(0, 0));

        Command command = Tunnels.renameCommand(circuit.document(), circuit.registry(), entrance.id(), "B");

        assertThat(command.description()).isEqualTo("command.renameTunnel");
    }

    @Test
    void renamingSomethingThatIsNotATunnelIsRefused() {
        Component led = circuit.place(StandardComponentTypes.LED, 0, 0, Map.of());

        assertThatThrownBy(() -> Tunnels.renameCommand(circuit.document(), circuit.registry(), led.id(), "B"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tunnels.renameCommand(circuit.document(), circuit.registry(),
                new ElementId("c99"), "B")).isInstanceOf(IllegalArgumentException.class);
    }
}
