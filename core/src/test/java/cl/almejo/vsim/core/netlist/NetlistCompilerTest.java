package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NetlistCompilerTest {

    private final NetlistCompiler compiler = new NetlistCompiler(TestComponentTypes.registry());
    private final CircuitDocument document = new CircuitDocument();

    /** Buffer at (x,y): input pin at (x, y+1), output pin at (x+2, y+1). */
    private ElementId buffer(int x, int y) {
        ElementId id = document.newComponentId();
        document.add(new Component(id, TestComponentTypes.BUFFER, new GridPoint(x, y), Rotation.R0, Map.of(), ""));
        return id;
    }

    private ElementId wire(int x1, int y1, int x2, int y2) {
        ElementId id = document.newWireId();
        document.add(new Wire(id, new GridPoint(x1, y1), new GridPoint(x2, y2)));
        return id;
    }

    private Net netOf(ElementId component, int pin, Netlist netlist) {
        return netlist.netForPin(new PinRef(component, pin)).orElseThrow();
    }

    @Test
    void wireConnectsPinsAtItsEndpoints() {
        ElementId first = buffer(0, 0);
        ElementId second = buffer(10, 0);
        ElementId link = wire(2, 1, 10, 1);
        Netlist netlist = compiler.compile(document);
        Net net = netOf(first, 1, netlist);
        assertThat(net.pins()).containsExactlyInAnyOrder(new PinRef(first, 1), new PinRef(second, 0));
        assertThat(net.wires()).containsExactly(link);
        assertThat(netlist.netForWire(link)).contains(net);
    }

    @Test
    void pinOnWireInteriorConnects() {
        ElementId first = buffer(0, 0);
        ElementId middle = buffer(5, 0);
        wire(2, 1, 12, 1);
        Netlist netlist = compiler.compile(document);
        assertThat(netOf(first, 1, netlist)).isEqualTo(netOf(middle, 0, netlist));
    }

    @Test
    void crossingWiresWithoutPointDoNotConnect() {
        ElementId horizontal = wire(0, 5, 10, 5);
        ElementId vertical = wire(5, 0, 5, 10);
        Netlist netlist = compiler.compile(document);
        assertThat(netlist.netForWire(horizontal)).isNotEqualTo(netlist.netForWire(vertical));
        assertThat(netlist.nets()).hasSize(2);
    }

    @Test
    void teeJunctionConnects() {
        ElementId horizontal = wire(0, 5, 10, 5);
        ElementId branch = wire(5, 5, 5, 10);
        Netlist netlist = compiler.compile(document);
        assertThat(netlist.netForWire(horizontal)).isEqualTo(netlist.netForWire(branch));
    }

    @Test
    void pinsOnSamePointConnectWithoutWire() {
        ElementId first = buffer(0, 0);
        ElementId second = buffer(2, 0);
        Netlist netlist = compiler.compile(document);
        assertThat(netOf(first, 1, netlist)).isEqualTo(netOf(second, 0, netlist));
    }

    @Test
    void unconnectedPinHasItsOwnNet() {
        ElementId lonely = buffer(0, 0);
        Netlist netlist = compiler.compile(document);
        assertThat(netlist.nets()).hasSize(2);
        assertThat(netOf(lonely, 0, netlist).pins()).containsExactly(new PinRef(lonely, 0));
        assertThat(netOf(lonely, 0, netlist).id()).isEqualTo(new NetId("n:p:" + lonely.value() + "#0"));
    }

    @Test
    void netIsNamedAfterItsSmallestAnchorAndPinsComeFirst() {
        ElementId alone = wire(0, 0, 4, 0);
        assertThat(compiler.compile(document).netForWire(alone).orElseThrow().id())
                .isEqualTo(new NetId("n:w:" + alone.value()));
        ElementId gate = buffer(4, -1);
        assertThat(compiler.compile(document).netForWire(alone).orElseThrow().id())
                .isEqualTo(new NetId("n:p:" + gate.value() + "#0"));
    }

    @Test
    void anchorsCompareElementNumbersNumericallySoNewerComponentsNeverDisplaceOlderOnes() {
        ElementId older = new ElementId("c9");
        ElementId newer = new ElementId("c10");
        document.add(new Component(older, TestComponentTypes.BUFFER, new GridPoint(0, 0), Rotation.R0, Map.of(), ""));
        NetId before = netOf(older, 1, compiler.compile(document)).id();
        assertThat(before).isEqualTo(new NetId("n:p:c9#1"));
        document.add(new Component(newer, TestComponentTypes.BUFFER, new GridPoint(2, 0), Rotation.R0, Map.of(), ""));
        Net joined = netOf(older, 1, compiler.compile(document));
        assertThat(joined.pins()).contains(new PinRef(newer, 0));
        assertThat(joined.id()).isEqualTo(before);
    }

    @Test
    void netIdSurvivesEditsThatKeepItsAnchor() {
        ElementId first = buffer(0, 0);
        buffer(10, 0);
        wire(2, 1, 10, 1);
        NetId before = netOf(first, 1, compiler.compile(document)).id();
        ElementId unrelated = buffer(30, 30);
        document.replace(document.component(unrelated).orElseThrow().translate(-40, -40));
        wire(2, -3, 2, 1);
        assertThat(netOf(first, 1, compiler.compile(document)).id())
                .isEqualTo(before)
                .isEqualTo(new NetId("n:p:" + first.value() + "#1"));
    }

    @Test
    void rotatedComponentPinsAreUsed() {
        ElementId id = document.newComponentId();
        document.add(new Component(id, TestComponentTypes.BUFFER, new GridPoint(10, 10), Rotation.R90, Map.of(), ""));
        ElementId link = wire(9, 12, 9, 20);
        Netlist netlist = compiler.compile(document);
        assertThat(netlist.netForWire(link).orElseThrow().pins()).containsExactly(new PinRef(id, 1));
    }

    @Test
    void mixedWidthsAreFlagged() {
        ElementId narrow = buffer(0, 0);
        ElementId wide = document.newComponentId();
        document.add(new Component(wide, TestComponentTypes.WIDE_PROBE, new GridPoint(2, 1), Rotation.R0, Map.of(), ""));
        Netlist netlist = compiler.compile(document);
        Net net = netOf(narrow, 1, netlist);
        assertThat(net.widthMismatch()).isTrue();
        assertThat(net.width()).isEqualTo(8);
    }

    @Test
    void wireOnlyNetHasWidthOne() {
        ElementId alone = wire(0, 0, 4, 0);
        Net net = compiler.compile(document).netForWire(alone).orElseThrow();
        assertThat(net.width()).isEqualTo(1);
        assertThat(net.widthMismatch()).isFalse();
        assertThat(net.points()).containsExactlyInAnyOrder(new GridPoint(0, 0), new GridPoint(4, 0));
    }

    private final ComponentTypeRegistry standard = StandardComponentTypes.registry();

    private Component splitter(int width) {
        Component splitter = standard.require(StandardComponentTypes.BUS_SPLITTER).newComponent(new ElementId("c1"),
                GridPoint.ORIGIN, Rotation.R0, Map.of(StandardComponentTypes.WIDTH, width), "");
        document.add(splitter);
        return splitter;
    }

    @Test
    void aSplitterNarrowerThanItsDefaultHasOnlyItsOwnPins() {
        Component splitter = splitter(2);
        Netlist netlist = new NetlistCompiler(standard).compile(document);
        assertThat(netlist.nets()).hasSize(3);
        assertThat(netlist.netForPin(new PinRef(splitter.id(), 0)))
                .hasValueSatisfying(net -> assertThat(net.width()).isEqualTo(2));
        assertThat(netlist.netForPin(new PinRef(splitter.id(), 3))).isEmpty();
    }

    @Test
    void changingAWidthMovesThePinsAndUndoRestoresThem() {
        Component splitter = splitter(4);
        ElementId tap = wire(2, 4, 6, 4); // b3 of the 4-bit splitter is at (2, 4)
        NetlistCompiler standardCompiler = new NetlistCompiler(standard);
        assertThat(standardCompiler.compile(document).netForWire(tap).orElseThrow().pins())
                .containsExactly(new PinRef(splitter.id(), 4));

        ComponentType splitterType = standard.require(StandardComponentTypes.BUS_SPLITTER);
        ChangeParametersCommand narrower = new ChangeParametersCommand(splitter.id(), splitterType,
                Map.of(StandardComponentTypes.WIDTH, 2));
        narrower.apply(document);
        assertThat(standardCompiler.compile(document).netForWire(tap).orElseThrow().pins()).isEmpty();

        narrower.revert(document);
        assertThat(standardCompiler.compile(document).netForWire(tap).orElseThrow().pins())
                .containsExactly(new PinRef(splitter.id(), 4));
    }
}
