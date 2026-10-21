package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentTypeTest {

    private final ComponentTypeRegistry registry = TestComponentTypes.registry();
    private final ComponentType buffer = registry.require(TestComponentTypes.BUFFER);

    @Test
    void integerParameterNormalization() {
        ParameterSpec delay = ParameterSpec.integer("delay", 1, 1, 10);
        assertThat(delay.normalize(5)).isEqualTo(5);
        assertThat(delay.normalize("7")).isEqualTo(7);
        assertThat(delay.normalize(3L)).isEqualTo(3);
        assertThatThrownBy(() -> delay.normalize(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> delay.normalize("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void choiceAndTextParameterNormalization() {
        ParameterSpec value = ParameterSpec.choice("value", "0", List.of("0", "1", "Z"));
        assertThat(value.normalize(1)).isEqualTo("1");
        assertThat(value.normalize("Z")).isEqualTo("Z");
        assertThatThrownBy(() -> value.normalize("2")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ParameterSpec.text("color", "red").normalize(42)).isEqualTo("42");
    }

    @Test
    void aColourParameterIsAChoiceOfColourNames() {
        ParameterSpec color = ParameterSpec.color("color", "red", List.of("red", "green"));
        assertThat(color.kind()).isEqualTo(ParameterKind.COLOR);
        assertThat(color.normalize("green")).isEqualTo("green");
        assertThatThrownBy(() -> color.normalize("pink")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParameterSpec.color("color", "pink", List.of("red")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theLedColourIsAColourParameter() {
        ComponentType led = StandardComponentTypes.registry().require(StandardComponentTypes.LED);
        assertThat(led.parameters().getFirst().kind()).isEqualTo(ParameterKind.COLOR);
        assertThat(led.resolveParameters(Map.of())).isEqualTo(Map.of("color", "red"));
    }

    @Test
    void choiceDefaultMustBeAChoice() {
        assertThatThrownBy(() -> ParameterSpec.choice("value", "9", List.of("0", "1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveParametersFillsDefaultsAndDropsUnknownKeys() {
        assertThat(buffer.resolveParameters(Map.of())).isEqualTo(Map.of("delay", 1));
        assertThat(buffer.resolveParameters(Map.of("delay", 4, "unknown", "x"))).isEqualTo(Map.of("delay", 4));
    }

    @Test
    void pinLocationAppliesPositionAndRotation() {
        Component component = new Component(new ElementId("c1"), buffer.id(), new GridPoint(10, 10), Rotation.R0, Map.of(), "");
        assertThat(buffer.pinLocation(component, 1)).isEqualTo(new GridPoint(12, 11));
        Component rotated = component.withRotation(Rotation.R90);
        assertThat(buffer.pinLocation(rotated, 1)).isEqualTo(new GridPoint(9, 12));
        assertThat(buffer.bounds(rotated)).isEqualTo(new Bounds(8, 10, 2, 2));
    }

    @Test
    void pinDirectionsHelpers() {
        assertThat(buffer.pin(0).isInput()).isTrue();
        assertThat(buffer.pin(0).isDriver()).isFalse();
        assertThat(buffer.pin(1).isDriver()).isTrue();
        assertThat(new PinSpec("io", GridPoint.ORIGIN, PinDirection.BIDIRECTIONAL, 1).isInput()).isTrue();
    }

    @Test
    void registryRejectsDuplicatesAndReportsUnknownTypes() {
        assertThatThrownBy(() -> registry.register(buffer)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.require("nope"))
                .isInstanceOf(UnknownComponentTypeException.class)
                .satisfies(exception -> assertThat(((UnknownComponentTypeException) exception).typeId()).isEqualTo("nope"));
        assertThat(registry.find("nope")).isEmpty();
        assertThat(registry.all()).extracting(ComponentType::id)
                .containsExactly(TestComponentTypes.SOURCE, TestComponentTypes.BUFFER,
                        TestComponentTypes.INVERTER, TestComponentTypes.WIDE_PROBE);
    }

    @Test
    void newComponentStoresResolvedParametersAndRejectsInvalidOnes() {
        Component component = buffer.newComponent(new ElementId("c7"), new GridPoint(1, 2), Rotation.R90,
                Map.of("delay", "3", "unknown", "x"), "B1");
        assertThat(component).isEqualTo(new Component(new ElementId("c7"), buffer.id(), new GridPoint(1, 2),
                Rotation.R90, Map.of("delay", 3), "B1"));
        assertThat(buffer.newComponent(new ElementId("c8"), GridPoint.ORIGIN, Rotation.R0, null, null).parameters())
                .isEqualTo(Map.of("delay", 1));
        assertThatThrownBy(() -> buffer.newComponent(new ElementId("c9"), GridPoint.ORIGIN, Rotation.R0,
                Map.of("delay", 0), "")).isInstanceOf(IllegalArgumentException.class);
    }

    /** A test type whose bus pin and box follow {@code width}: "bus" at the origin, "last" at (2, width). */
    private static final ComponentType RIBBON = ComponentType.parameterized("test-ribbon", ComponentCategory.BASIC,
            List.of(ParameterSpec.integer("width", 2, 1, 8)),
            parameters -> {
                int width = (Integer) parameters.get("width");
                return new ComponentShape(new GridSize(2, width),
                        List.of(new PinSpec("bus", GridPoint.ORIGIN, PinDirection.INPUT, width),
                                PinSpec.output("last", 2, width)));
            },
            parameters -> (context, pinIndex) -> {
            });

    private static Component ribbonAt(Rotation rotation, int width) {
        return RIBBON.newComponent(new ElementId("c1"), new GridPoint(10, 10), rotation, Map.of("width", width), "");
    }

    @Test
    void aParameterizedTypeShowsTheShapeOfItsDefaults() {
        assertThat(RIBBON.isParameterized()).isTrue();
        assertThat(buffer.isParameterized()).isFalse();
        assertThat(RIBBON.size()).isEqualTo(new GridSize(2, 2));
        assertThat(RIBBON.pins()).containsExactly(new PinSpec("bus", GridPoint.ORIGIN, PinDirection.INPUT, 2),
                PinSpec.output("last", 2, 2));
    }

    @Test
    void specializingFollowsTheParametersAndKeepsTheTypeWhenNothingChanges() {
        ComponentType wide = RIBBON.specializedFor(Map.of("width", 5));
        assertThat(wide.id()).isEqualTo(RIBBON.id());
        assertThat(wide.size()).isEqualTo(new GridSize(2, 5));
        assertThat(wide.pin(0).width()).isEqualTo(5);
        assertThat(wide.pin(1).position()).isEqualTo(new GridPoint(2, 5));
        assertThat(wide).isEqualTo(RIBBON.specializedFor(Map.of("width", "5")));
        assertThat(wide.specializedFor(Map.of("width", 2))).isEqualTo(RIBBON);
        assertThat(RIBBON.specializedFor(Map.of())).isSameAs(RIBBON);
        assertThat(RIBBON.specializedFor(Map.of("width", 2))).isSameAs(RIBBON);
        assertThat(buffer.specializedFor(Map.of("delay", 4))).isSameAs(buffer);
        assertThatThrownBy(() -> RIBBON.specializedFor(Map.of("width", 9)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pinsAndBoundsOfAComponentFollowItsOwnParameters() {
        Component wide = ribbonAt(Rotation.R0, 5);
        assertThat(RIBBON.forComponent(wide).size()).isEqualTo(new GridSize(2, 5));
        assertThat(RIBBON.forComponent(wide).pin(0).width()).isEqualTo(5);
        assertThat(RIBBON.pinLocation(wide, 1)).isEqualTo(new GridPoint(12, 15));
        assertThat(RIBBON.bounds(wide)).isEqualTo(new Bounds(10, 10, 2, 5));
        Component turned = ribbonAt(Rotation.R90, 5);
        assertThat(RIBBON.pinLocation(turned, 1)).isEqualTo(new GridPoint(5, 12));
        assertThat(RIBBON.bounds(turned))
                .isEqualTo(Bounds.rotated(new GridPoint(10, 10), new GridSize(2, 5), Rotation.R90));
    }

    @Test
    void pinFactoriesTakeAWidth() {
        assertThat(PinSpec.input("a", 0, 2, 8)).isEqualTo(new PinSpec("a", new GridPoint(0, 2), PinDirection.INPUT, 8));
        assertThat(PinSpec.output("y", 4, 2, 8))
                .isEqualTo(new PinSpec("y", new GridPoint(4, 2), PinDirection.OUTPUT, 8));
    }

    @Test
    void aParameterizedTypeRunsItsLayoutOncePerSetOfParameters() {
        AtomicInteger layouts = new AtomicInteger();
        ComponentType counted = ComponentType.parameterized("test-counted", ComponentCategory.BASIC,
                List.of(ParameterSpec.integer("width", 2, 1, 8)),
                parameters -> {
                    layouts.incrementAndGet();
                    int width = (Integer) parameters.get("width");
                    return new ComponentShape(new GridSize(2, width), List.of(PinSpec.output("last", 2, width)));
                },
                parameters -> (context, pinIndex) -> {
                });
        Component wide = counted.newComponent(new ElementId("c1"), new GridPoint(10, 10), Rotation.R0,
                Map.of("width", 5), "");
        int before = layouts.get();

        GridPoint first = counted.pinLocation(wide, 0);
        GridPoint second = counted.pinLocation(wide, 0);
        Bounds bounds = counted.bounds(wide);

        assertThat(layouts.get() - before).isEqualTo(1);
        assertThat(first).isEqualTo(new GridPoint(12, 15)).isEqualTo(second);
        assertThat(bounds).isEqualTo(new Bounds(10, 10, 2, 5));
        assertThat(counted.specializedFor(Map.of("width", 5))).isEqualTo(counted.forComponent(wide));
        assertThat(layouts.get() - before).isEqualTo(1);
    }

    /** A raw parameter value that counts how often it is normalized (read as a number). */
    private static final class CountingWidth extends Number {
        private final int value;
        private int reads;

        CountingWidth(int value) {
            this.value = value;
        }

        @Override
        public long longValue() {
            reads++;
            return value;
        }

        @Override
        public int intValue() {
            return (int) longValue();
        }

        @Override
        public float floatValue() {
            return longValue();
        }

        @Override
        public double doubleValue() {
            return longValue();
        }
    }

    @Test
    void aSpecializedTypeAnswersPinsAndBoundsOfItsOwnComponentWithoutResolvingItsParametersAgain() {
        CountingWidth width = new CountingWidth(5);
        Component wide = new Component(new ElementId("c1"), RIBBON.id(), new GridPoint(10, 10), Rotation.R0,
                Map.of("width", width), "");
        ComponentType specialized = RIBBON.forComponent(wide);
        int afterSpecializing = width.reads;

        assertThat(specialized.pinLocation(wide, 1)).isEqualTo(new GridPoint(12, 15));
        assertThat(specialized.bounds(wide)).isEqualTo(new Bounds(10, 10, 2, 5));
        assertThat(width.reads).isEqualTo(afterSpecializing);

        Component narrow = ribbonAt(Rotation.R0, 3); // another component: its own parameters still apply
        assertThat(specialized.pinLocation(narrow, 1)).isEqualTo(new GridPoint(12, 13));
        assertThat(specialized.bounds(narrow)).isEqualTo(new Bounds(10, 10, 2, 3));
        assertThat(specialized).isEqualTo(RIBBON.specializedFor(Map.of("width", 5)))
                .hasSameHashCodeAs(RIBBON.specializedFor(Map.of("width", 5)));
        assertThat(RIBBON).isEqualTo(specialized.specializedFor(Map.of("width", 2)));
        assertThat(specialized.specializedFor(Map.of("width", 2))).isEqualTo(RIBBON).hasSameHashCodeAs(RIBBON);
    }

    @Test
    void typesBuiltTwiceFromTheSameLayoutStayEqual() {
        ComponentLayout layout = parameters -> new ComponentShape(new GridSize(1, 1), List.of());
        BehaviorFactory behavior = parameters -> (context, pinIndex) -> {
        };
        ComponentType first = ComponentType.parameterized("test-twice", ComponentCategory.BASIC, List.of(), layout,
                behavior);
        ComponentType second = ComponentType.parameterized("test-twice", ComponentCategory.BASIC, List.of(), layout,
                behavior);
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void typesBuiltWithTheCanonicalConstructorStayEqualToParameterizedOnes() {
        ComponentLayout layout = parameters -> new ComponentShape(new GridSize(1, 1), List.of());
        BehaviorFactory behavior = parameters -> (context, pinIndex) -> {
        };
        ComponentType parameterized = ComponentType.parameterized("test-canonical", ComponentCategory.BASIC,
                List.of(), layout, behavior);
        ComponentType canonical = new ComponentType("test-canonical", ComponentCategory.BASIC, new GridSize(1, 1),
                List.of(), List.of(), behavior, layout);
        assertThat(canonical).isEqualTo(parameterized).hasSameHashCodeAs(parameterized);
    }
}
