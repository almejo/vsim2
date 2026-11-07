package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.simulation.PauseNotice;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The «Punto de parada»: it pauses once each time its condition becomes true, never for X/Z, never when off. */
class BreakpointBehaviorTest {

    private static final String TYPE = "breakpoint";
    private static final int INPUT = 0;

    private static Map<String, Object> parameters(int width, String condition, String value, boolean active) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("width", width);
        parameters.put("condition", condition);
        parameters.put("value", value);
        parameters.put("active", active);
        return parameters;
    }

    /** Started and settled with the input at 0; a «distinto de» that holds from the start has paused once already. */
    private static BlockHarness breakpoint(String condition, String value) {
        BlockHarness harness = new BlockHarness(TYPE, parameters(4, condition, value, true)).settle();
        harness.takePauseRequest();
        return harness;
    }

    @Test
    void theTypeLivesInDepuracionWithOneInputPinOfTheChosenWidth() {
        ComponentType type = StandardComponentTypes.registry().require(TYPE);
        assertThat(type.category()).isEqualTo(ComponentCategory.DEBUG);
        assertThat(type.specializedFor(parameters(12, "equals", "0", true)).pin(INPUT).width()).isEqualTo(12);
    }

    @Test
    void equalsPausesWhenTheInputBecomesTheValueAndSaysWhy() {
        BlockHarness harness = breakpoint("equals", "9");

        harness.set(INPUT, 8).settle();
        assertThat(harness.takePauseRequest()).isEmpty();
        harness.set(INPUT, 9).settle();

        PauseNotice notice = harness.takePauseRequest().orElseThrow();
        assertThat(notice.messageKey()).isEqualTo("breakpoint.stopped.equals");
        assertThat(notice.arguments()).containsExactly("1001");
    }

    @Test
    void aConditionThatStaysTruePausesOnlyOnceAndAgainAfterBecomingFalse() {
        BlockHarness harness = breakpoint("equals", "9");

        harness.set(INPUT, 9).settle();
        assertThat(harness.takePauseRequest()).isPresent();
        harness.set(INPUT, 9).advance(100).settle();
        assertThat(harness.takePauseRequest()).isEmpty();
        harness.set(INPUT, 10).settle();
        assertThat(harness.takePauseRequest()).isEmpty();
        harness.set(INPUT, 9).settle();
        assertThat(harness.takePauseRequest()).as("false then true again").isPresent();
    }

    @Test
    void differsPausesOnEveryNewValueThatDiffers() {
        BlockHarness harness = breakpoint("differs", "0");

        harness.set(INPUT, 0).settle();
        assertThat(harness.takePauseRequest()).isEmpty();
        harness.set(INPUT, 3).settle();
        PauseNotice notice = harness.takePauseRequest().orElseThrow();
        assertThat(notice.messageKey()).isEqualTo("breakpoint.stopped.differs");
        assertThat(notice.arguments()).containsExactly("0011", "0000");
        harness.set(INPUT, 4).settle();
        assertThat(harness.takePauseRequest()).as("a counter going 3 → 4 stops again (the user's hand test)").isPresent();
        harness.set(INPUT, 4).advance(100).settle();
        assertThat(harness.takePauseRequest()).as("the same value again is no change").isEmpty();
        harness.set(INPUT, 0).settle();
        assertThat(harness.takePauseRequest()).isEmpty();
    }

    @Test
    void anInactiveBreakpointNeverPauses() {
        BlockHarness harness = new BlockHarness(TYPE, parameters(4, "equals", "9", false)).settle();

        harness.set(INPUT, 9).settle();

        assertThat(harness.takePauseRequest()).isEmpty();
    }

    @Test
    void anUndefinedInputNeverMatchesNeitherForEqualsNorForDiffers() {
        BlockHarness equals = breakpoint("equals", "9");
        BlockHarness differs = breakpoint("differs", "9");

        for (BlockHarness harness : new BlockHarness[]{equals, differs}) {
            harness.set(INPUT, 9).settle();
            harness.takePauseRequest();
            harness.conflict(INPUT).settle();
        }

        assertThat(equals.takePauseRequest()).isEmpty();
        assertThat(differs.takePauseRequest()).isEmpty();
    }

    @Test
    void aReleasedInputNeverMatchesEither() {
        BlockHarness equals = breakpoint("equals", "9");
        BlockHarness differs = breakpoint("differs", "9");

        for (BlockHarness harness : new BlockHarness[]{equals, differs}) {
            harness.set(INPUT, 9).settle();
            harness.takePauseRequest();
            harness.release(INPUT).settle();
        }

        assertThat(equals.takePauseRequest()).isEmpty();
        assertThat(differs.takePauseRequest()).isEmpty();
    }

    @Test
    void aPartlyUndefinedInputDoesNotMatchEither() {
        BlockHarness harness = breakpoint("differs", "9");

        harness.set(INPUT, new Value(4, 0b0001, 0b1110, false)).settle();

        assertThat(harness.takePauseRequest()).isEmpty();
    }

    @Test
    void aRebuildWhileTheConditionHoldsDoesNotPauseAgain() {
        BlockHarness harness = breakpoint("equals", "9");
        harness.set(INPUT, 9).settle();
        harness.takePauseRequest();

        harness.reconfigure(parameters(4, "equals", "9", true)).settle();

        assertThat(harness.takePauseRequest()).isEmpty();
    }

    @Test
    void theValueIsTypedLikeAConstantAndMustFitTheWidth() {
        ComponentType type = StandardComponentTypes.registry().require(TYPE);

        assertThat(type.check(parameters(4, "equals", "0b1001", true))).isEmpty();
        assertThat(type.check(parameters(4, "equals", "-3", true))).isEmpty();
        assertThat(type.check(parameters(4, "equals", "16", true)).map(ParameterProblem::messageKey))
                .contains("constant.doesNotFit");
        assertThat(type.check(parameters(4, "equals", "zz", true)).map(ParameterProblem::messageKey))
                .contains("constant.invalidValue");
        assertThat(type.check(parameters(64, "equals", "0xFFFFFFFFFFFFFFFF", true))).isEmpty();
    }

    @Test
    void theDefaultsAreAnActiveEqualsZeroOnEightBits() {
        ComponentType type = StandardComponentTypes.registry().require(TYPE);

        Map<String, Object> resolved = type.resolveParameters(Map.of());

        assertThat(resolved).containsEntry("width", 8).containsEntry("condition", "equals")
                .containsEntry("value", "0").containsEntry("active", true);
    }

    @Test
    void theParametersRoundTripThroughTheFileAndAnOldFileWithoutThemGetsTheDefaults() throws CircuitFormatException {
        CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), TYPE, new GridPoint(2, 2), Rotation.R0,
                parameters(12, "differs", "0xABC", false), "alto"));

        CircuitDocument loaded = format.read(format.write(document));

        assertThat(Set.copyOf(loaded.elements())).isEqualTo(Set.copyOf(document.elements()));
        CircuitDocument bare = format.read("""
                {"formatVersion":2,"kind":"circuit","components":[{"id":"c1","type":"breakpoint","x":1,"y":1}]}
                """);
        Component component = bare.components().iterator().next();
        assertThat(StandardComponentTypes.registry().require(TYPE).resolveParameters(component.parameters()))
                .containsEntry("width", 8).containsEntry("active", true);
    }
}
