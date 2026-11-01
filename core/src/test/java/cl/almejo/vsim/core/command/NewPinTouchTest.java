package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Turning «Mostrar Q̅» on never puts the new pin on a wire or another pin: that would short it silently. */
class NewPinTouchTest {

    private static final Map<String, Object> HIDDEN = Map.of(StandardComponentTypes.SHOW_INVERTED, false);
    private static final Map<String, Object> SHOWN = Map.of(StandardComponentTypes.SHOW_INVERTED, true);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ComponentType flipFlopType = registry.require(StandardComponentTypes.FLIP_FLOP_DATA);
    private final CircuitDocument document = new CircuitDocument();

    private Component place(Rotation rotation, Map<String, Object> parameters) {
        Component component = flipFlopType.newComponent(document.newComponentId(), new GridPoint(10, 10), rotation,
                parameters, "");
        document.add(component);
        return component;
    }

    private Component withShown(Component component) {
        return component.withParameters(flipFlopType.resolveParameters(SHOWN));
    }

    /** A wire from Q through the point where Q̅ would be, one cell beyond it. */
    private void wireDownFromQ(Component flipFlop) {
        GridPoint output = flipFlopType.pinLocation(flipFlop, 1);
        GridPoint future = flipFlopType.pinLocation(withShown(flipFlop), 3);
        GridPoint beyond = new GridPoint(future.x() + future.x() - output.x(), future.y() + future.y() - output.y());
        document.add(new Wire(document.newWireId(), output, beyond));
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void aQWireOverTheFuturePinRefusesTurningItOnInEveryRotation(Rotation rotation) {
        Component flipFlop = place(rotation, HIDDEN);
        wireDownFromQ(flipFlop);

        Optional<String> pin = NewPinTouch.find(document, registry, flipFlop, withShown(flipFlop));

        assertThat(pin).contains("nQ");
    }

    @Test
    void anotherPartsPinOnTheFuturePointRefusesToo() {
        Component flipFlop = place(Rotation.R0, HIDDEN);
        // the second flip-flop's D (0, 2) lands on Q̅ (4, 3) of the first
        document.add(flipFlopType.newComponent(document.newComponentId(), new GridPoint(14, 11), Rotation.R0, HIDDEN,
                ""));

        assertThat(NewPinTouch.find(document, registry, flipFlop, withShown(flipFlop))).contains("nQ");
    }

    @Test
    void aCleanFlipFlopIsNotRefused() {
        Component flipFlop = place(Rotation.R0, HIDDEN);

        assertThat(NewPinTouch.find(document, registry, flipFlop, withShown(flipFlop))).isEmpty();
    }

    @Test
    void aWireOnQAloneIsNotRefused() {
        Component flipFlop = place(Rotation.R0, HIDDEN);
        GridPoint output = flipFlopType.pinLocation(flipFlop, 1);
        document.add(new Wire(document.newWireId(), output, new GridPoint(output.x() + 3, output.y())));

        assertThat(NewPinTouch.find(document, registry, flipFlop, withShown(flipFlop))).isEmpty();
    }

    @Test
    void turningItOffOverAWireIsNeverRefused() {
        Component flipFlop = place(Rotation.R0, SHOWN);
        document.add(new Wire(document.newWireId(), new GridPoint(14, 13), new GridPoint(14, 16)));

        assertThat(NewPinTouch.find(document, registry, flipFlop,
                flipFlop.withParameters(flipFlopType.resolveParameters(HIDDEN)))).isEmpty();
    }
}
