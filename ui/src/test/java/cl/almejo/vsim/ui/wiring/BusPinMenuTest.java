package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.canvas.Selection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class BusPinMenuTest {

    private OpenDocument openDocument;
    private Selection selection;
    private BusPinMenuContributor contributor;
    private ConnectionPoints points;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        selection = new Selection();
        contributor = new BusPinMenuContributor(openDocument, selection);
        points = new ConnectionPoints(openDocument.registry());
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private Component placeAt(String typeId, Rotation rotation, Map<String, Object> parameters) {
        ComponentType type = openDocument.registry().require(typeId);
        Component component = type.newComponent(openDocument.document().newComponentId(), new GridPoint(20, 20),
                rotation, parameters, "");
        openDocument.execute(new cl.almejo.vsim.core.command.AddElementsCommand(java.util.List.of(component)));
        return component;
    }

    private BusPinAttachment planOf(Component component, int pinIndex) {
        ConnectionPoints.PinHit hit = new ConnectionPoints.PinHit(
                openDocument.registry().typeOf(component).pinLocation(component, pinIndex), component, pinIndex);
        return BusPinAttachment.plan(openDocument.registry(), hit).orElseThrow();
    }

    private Optional<Net> netOf(Component component, int pinIndex) {
        return openDocument.netlist().netForPin(new PinRef(component.id(), pinIndex));
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void everyBusPinOfARotatedAdderGetsAPartWhoseBusPinLandsOnTheWireEnd(Rotation rotation) {
        Component adder = placeAt(StandardComponentTypes.ADDER, rotation, Map.of(StandardComponentTypes.WIDTH, 8));
        ComponentType adderType = openDocument.registry().typeOf(adder);
        int busPins = 0;
        for (int pinIndex = 0; pinIndex < adderType.pinCount(); pinIndex++) {
            if (adderType.pin(pinIndex).width() < 2) {
                continue;
            }
            busPins++;
            BusPinAttachment plan = planOf(adder, pinIndex);
            boolean output = adderType.pin(pinIndex).direction() == PinDirection.OUTPUT;
            assertThat(plan.typeId()).isEqualTo(output ? StandardComponentTypes.BUS_SPLITTER
                    : StandardComponentTypes.BUS_JOINER);
            assertThat(plan.width()).isEqualTo(8);
            contributor.attach(plan);
            Component part = openDocument.document().components().stream()
                    .filter(candidate -> candidate.typeId().equals(plan.typeId())).findFirst().orElseThrow();
            ComponentType partType = openDocument.registry().typeOf(part);
            assertThat(partType.pinLocation(part, 0)).isEqualTo(plan.wireEnd());
            assertThat(plan.wireStart()).isEqualTo(adderType.pinLocation(adder, pinIndex));
            assertThat(Math.abs(plan.wireEnd().x() - plan.wireStart().x())
                    + Math.abs(plan.wireEnd().y() - plan.wireStart().y())).isEqualTo(2);
            assertThat(netOf(adder, pinIndex)).isPresent();
            assertThat(netOf(part, 0)).isEqualTo(netOf(adder, pinIndex));
            // the bits point away from the component: bit 0 is farther from the component pin than the bus pin
            double busDistance = plan.wireStart().x() == plan.wireEnd().x() ? 0 : 1;
            GridPoint bit = partType.pinLocation(part, 1);
            int away = Math.abs(bit.x() - plan.wireStart().x()) + Math.abs(bit.y() - plan.wireStart().y());
            assertThat(away).isGreaterThan(2).describedAs("bits point away, busDistance " + busDistance);
            assertThat(selection.ids()).containsExactly(part.id());
            openDocument.history().undo();
            assertThat(openDocument.document().components()).containsExactly(adder);
            assertThat(openDocument.document().wires()).isEmpty();
        }
        assertThat(busPins).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void aRegisterOf32BitsGetsFullWidthParts(Rotation rotation) {
        Component register = placeAt(StandardComponentTypes.REGISTER, rotation,
                Map.of(StandardComponentTypes.WIDTH, 32));
        ComponentType type = openDocument.registry().typeOf(register);
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            if (type.pin(pinIndex).width() == 32) {
                BusPinAttachment plan = planOf(register, pinIndex);
                assertThat(plan.width()).isEqualTo(32);
                contributor.attach(plan);
                Component part = openDocument.document().components().stream()
                        .filter(candidate -> candidate.typeId().equals(plan.typeId())).findFirst().orElseThrow();
                assertThat(netOf(part, 0)).isEqualTo(netOf(register, pinIndex));
                openDocument.history().undo();
            }
        }
    }

    @Test
    void aOneBitPinGetsNothing() {
        Component adder = placeAt(StandardComponentTypes.ADDER, Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 8));
        ComponentType type = openDocument.registry().typeOf(adder);
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            if (type.pin(pinIndex).width() == 1) {
                JPopupMenu menu = new JPopupMenu();
                GridPoint pin = type.pinLocation(adder, pinIndex);
                contributor.contribute(menu, Optional.of(adder), selection, new Point2D.Double(pin.x(), pin.y()));
                assertThat(menu.getComponentCount()).isZero();
            }
        }
    }

    @Test
    void theMenuNamesThePartItAdds() {
        Component adder = placeAt(StandardComponentTypes.ADDER, Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 4));
        ComponentType type = openDocument.registry().typeOf(adder);
        GridPoint input = type.pinLocation(adder, 0);
        JPopupMenu inputMenu = new JPopupMenu();
        contributor.contribute(inputMenu, Optional.of(adder), selection, new Point2D.Double(input.x() + 0.2, input.y()));
        assertThat(texts(inputMenu)).containsExactly("Agregar unión de bus");
        GridPoint output = type.pinLocation(adder, 3);
        JPopupMenu outputMenu = new JPopupMenu();
        contributor.contribute(outputMenu, Optional.of(adder), selection, new Point2D.Double(output.x(), output.y()));
        assertThat(texts(outputMenu)).containsExactly("Agregar separador de bus");
        JPopupMenu away = new JPopupMenu();
        contributor.contribute(away, Optional.of(adder), selection, new Point2D.Double(22, 24));
        assertThat(away.getComponentCount()).isZero();
    }

    @Test
    void aPinThatAlreadyHasAWireStillGetsTheItemAndJoinsTheSameNet() {
        Component adder = placeAt(StandardComponentTypes.ADDER, Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 8));
        ComponentType type = openDocument.registry().typeOf(adder);
        GridPoint input = type.pinLocation(adder, 0);
        addWire(openDocument, input.x() - 5, input.y(), input.x(), input.y());
        JPopupMenu menu = new JPopupMenu();
        contributor.contribute(menu, Optional.of(adder), selection, new Point2D.Double(input.x(), input.y()));
        assertThat(menu.getComponentCount()).isEqualTo(1);
        BusPinAttachment plan = planOf(adder, 0);
        contributor.attach(plan);
        Component joiner = openDocument.document().components().stream()
                .filter(candidate -> candidate.typeId().equals(StandardComponentTypes.BUS_JOINER)).findFirst()
                .orElseThrow();
        assertThat(netOf(joiner, 0)).isEqualTo(netOf(adder, 0));
    }
}
