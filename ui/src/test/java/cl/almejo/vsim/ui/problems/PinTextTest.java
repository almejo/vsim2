package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PinTextTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private String describe(String typeId, Map<String, Object> parameters, int pinIndex, boolean unconnected,
                            Optional<Value> value) {
        return describe(typeId, Rotation.R0, parameters, pinIndex, unconnected, value);
    }

    private String describe(String typeId, Rotation rotation, Map<String, Object> parameters, int pinIndex,
                            boolean unconnected, Optional<Value> value) {
        Component component = registry.require(typeId).newComponent(new CircuitDocument().newComponentId(),
                new GridPoint(0, 0), rotation, parameters, "");
        return PinText.describe(component, registry.typeOf(component), pinIndex, unconnected, value);
    }

    @Test
    void withoutASnapshotAPinShowsOnlyItsName() {
        assertThat(describe(StandardComponentTypes.AND2, Map.of(), 0, true, Optional.empty())).isEqualTo("in0");
        assertThat(describe(StandardComponentTypes.FLIP_FLOP_DATA, Map.of(), 2, false, Optional.empty()))
                .isEqualTo("clock");
    }

    @Test
    void theValueFollowsAndALooseZInputSaysItIsNotConnected() {
        assertThat(describe(StandardComponentTypes.NOT, Map.of(), 1, false, Optional.of(Value.ONE)))
                .isEqualTo("out = 1");
        assertThat(describe(StandardComponentTypes.AND2, Map.of(), 1, true, Optional.of(Value.HIGH_IMPEDANCE)))
                .isEqualTo("in1 = Z (sin conectar)");
        assertThat(describe(StandardComponentTypes.NOT, Map.of(), 0, false, Optional.of(Value.HIGH_IMPEDANCE)))
                .as("connected to something that drives nothing").isEqualTo("in = Z");
        assertThat(describe(StandardComponentTypes.SWITCH, Map.of(), 0, true, Optional.of(Value.ZERO)))
                .as("a loose output still drives its value").isEqualTo("out = 0");
        assertThat(describe(StandardComponentTypes.LED, Map.of(), 0, false, Optional.of(Value.ONE.withConflict())))
                .isEqualTo("in = X");
    }

    @Test
    void aSevenSegmentDisplayNamesTheBitOfEachPin() {
        String display = StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY;
        assertThat(describe(display, Map.of(), 0, true, Optional.empty())).isEqualTo("in0 · bit 0 (el de la derecha)");
        assertThat(describe(display, Map.of(), 1, true, Optional.empty())).isEqualTo("in1 · bit 1");
        assertThat(describe(display, Map.of(), 3, true, Optional.empty()))
                .isEqualTo("in3 · bit 3 (el de la izquierda)");
        assertThat(describe(display, Map.of(), 0, true, Optional.of(Value.HIGH_IMPEDANCE)))
                .isEqualTo("in0 · bit 0 (el de la derecha) = Z (sin conectar)");
        String twoDigits = StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE;
        assertThat(describe(twoDigits, Map.of(), 3, false, Optional.of(Value.ONE))).isEqualTo("in3 · bit 3 = 1");
        assertThat(describe(twoDigits, Map.of(), 7, true, Optional.empty()))
                .isEqualTo("in7 · bit 7 (el de la izquierda)");
    }

    @Test
    void theBusInputOfADisplayShowsItsWidthAndValueButNoBit() {
        String display = StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY;
        assertThat(describe(display, Map.of(), 4, true, Optional.empty())).isEqualTo("bus · 4 bits");
        assertThat(describe(display, Map.of(), 4, false, Optional.of(Value.of(4, 0x9))))
                .isEqualTo("bus · 4 bits = 0x9 (1001)");
        assertThat(describe(display, Map.of(), 4, true, Optional.of(Value.highImpedance(4))))
                .isEqualTo("bus · 4 bits = 0xZ (ZZZZ) (sin conectar)");
        String twoDigits = StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE;
        assertThat(describe(twoDigits, Map.of(), 8, true, Optional.empty())).isEqualTo("bus · 8 bits");
        assertThat(describe(twoDigits, Rotation.R180, Map.of(), 7, true, Optional.empty()))
                .as("the last bit pin is still named by where it is drawn")
                .isEqualTo("in7 · bit 7 (el de la derecha)");
    }

    @Test
    void theBitWordFollowsTheRotationOfTheDisplay() {
        String display = StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY;
        assertThat(describe(display, Rotation.R180, Map.of(), 0, true, Optional.empty()))
                .isEqualTo("in0 · bit 0 (el de la izquierda)");
        assertThat(describe(display, Rotation.R180, Map.of(), 3, true, Optional.empty()))
                .isEqualTo("in3 · bit 3 (el de la derecha)");
        String bit0 = describe(display, Rotation.R90, Map.of(), 0, true, Optional.empty());
        String bit3 = describe(display, Rotation.R90, Map.of(), 3, true, Optional.empty());
        assertThat(bit0).matches("in0 · bit 0 \\(el de (abajo|arriba)\\)");
        assertThat(bit3).matches("in3 · bit 3 \\(el de (abajo|arriba)\\)");
        assertThat(bit0.contains("abajo")).isNotEqualTo(bit3.contains("abajo"));
        assertThat(describe(display, Rotation.R270, Map.of(), 1, true, Optional.empty())).isEqualTo("in1 · bit 1");
        Messages.initialize(Locale.ENGLISH);
        assertThat(describe(display, Rotation.R90, Map.of(), 0, true, Optional.empty())).containsAnyOf("the bottom one",
                "the top one");
    }

    @Test
    void aWidePinShowsItsWidthAndItsValueLikeTheBusTooltip() {
        Map<String, Object> fourBits = Map.of(StandardComponentTypes.WIDTH, 4);
        String splitter = StandardComponentTypes.BUS_SPLITTER;
        assertThat(describe(splitter, fourBits, 0, true, Optional.empty())).isEqualTo("bus · 4 bits");
        assertThat(describe(splitter, fourBits, 0, false, Optional.of(Value.of(4, 0x9))))
                .isEqualTo("bus · 4 bits = 0x9 (1001)");
        assertThat(describe(splitter, fourBits, 0, false, Optional.of(new Value(4, 0x9, 0x6, false))))
                .isEqualTo("bus · 4 bits = 0x? (1ZZ1)");
        assertThat(describe(splitter, fourBits, 0, false, Optional.of(Value.of(4, 0x9).withConflict())))
                .isEqualTo("bus · 4 bits = X");
        assertThat(describe(splitter, fourBits, 0, true, Optional.of(Value.highImpedance(4))))
                .isEqualTo("bus · 4 bits = 0xZ (ZZZZ) (sin conectar)");
        assertThat(describe(splitter, fourBits, 2, true, Optional.empty())).as("a one-bit side pin").isEqualTo("b1");
    }

    @Test
    void englishWordsComeFromTheEnglishBundle() {
        Messages.initialize(Locale.ENGLISH);
        assertThat(describe(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, Map.of(), 0, true,
                Optional.of(Value.HIGH_IMPEDANCE))).isEqualTo("in0 · bit 0 (the rightmost) = Z (not connected)");
    }
}
