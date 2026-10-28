package cl.almejo.vsim.ui.i18n;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentPreset;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.standard.ArithmeticTypes;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The «Descripción» texts: every palette entry has one in both languages, a parameter value adds its paragraph and a
 * user component shows its own. Parts added later only need their texts: {@link #everyPaletteEntryHasADescription}
 * walks the registry and the presets.
 */
class DescriptionsTest {

    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();

    @AfterEach
    void restoreSpanish() {
        Messages.initialize(Messages.SPANISH);
    }

    /** Every type and preset the palette offers, by the id its base text is keyed by. */
    private static List<String> paletteIds() {
        List<String> ids = new ArrayList<>();
        REGISTRY.all().forEach(type -> ids.add(type.id()));
        StandardComponentTypes.presets().forEach(preset -> ids.add(preset.id()));
        return ids;
    }

    @Test
    void everyPaletteEntryHasADescription() {
        List<String> missing = new ArrayList<>();
        for (Locale locale : List.of(Messages.SPANISH, Locale.ENGLISH)) {
            ResourceBundle bundle = Messages.bundle(locale);
            for (String id : paletteIds()) {
                String key = Descriptions.baseKey(id);
                if (!bundle.containsKey(key) || bundle.getString(key).isBlank()) {
                    missing.add(locale + ":" + key);
                }
            }
        }
        assertThat(paletteIds()).contains(StandardComponentTypes.RAM, StandardComponentTypes.NAND_PRESET);
        assertThat(missing).isEmpty();
    }

    /** A parameter paragraph keyed by a type, parameter or value that does not exist would never show: a typo. */
    @Test
    void everyParameterParagraphNamesARealTypeParameterAndValue() {
        List<String> unknown = new ArrayList<>();
        for (String key : Messages.bundle(Messages.SPANISH).keySet()) {
            if (!key.startsWith(Descriptions.PREFIX) || paletteIds().contains(key.substring(
                    Descriptions.PREFIX.length()))) {
                continue;
            }
            if (!namesAParameterValue(key.substring(Descriptions.PREFIX.length()))) {
                unknown.add(key);
            }
        }
        assertThat(unknown).isEmpty();
    }

    private static boolean namesAParameterValue(String rest) {
        for (ComponentType type : REGISTRY.all()) {
            for (ParameterSpec parameter : type.parameters()) {
                String start = type.id() + "." + parameter.name() + ".";
                if (rest.startsWith(start) && valuesOf(parameter).contains(rest.substring(start.length()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> valuesOf(ParameterSpec parameter) {
        return switch (parameter.kind()) {
            case BOOLEAN -> List.of("true", "false");
            case CHOICE, COLOR -> parameter.choices();
            case TEXT -> List.of(Descriptions.TEXT_SET);
            case INTEGER -> rangeOf(parameter);
        };
    }

    private static List<String> rangeOf(ParameterSpec parameter) {
        assertThat(parameter.kind()).isEqualTo(ParameterKind.INTEGER);
        List<String> values = new ArrayList<>();
        for (int value = parameter.minimum(); value <= Math.min(parameter.maximum(), 64); value++) {
            values.add(String.valueOf(value));
        }
        return values;
    }

    @Test
    void anAndWithNegateOutputIsDescribedAsTheNand() {
        ComponentType and = REGISTRY.require(StandardComponentTypes.AND);

        String plain = Descriptions.of(and, Map.of());
        String negated = Descriptions.of(and, Map.of(StandardComponentTypes.NEGATE, true));

        assertThat(plain).isNotBlank().doesNotContain("NAND");
        assertThat(negated).contains("NAND").isNotEqualTo(plain);
        ComponentPreset nand = StandardComponentTypes.preset(StandardComponentTypes.NAND_PRESET).orElseThrow();
        assertThat(Descriptions.of(and, nand.parameters())).isEqualTo(negated);
    }

    @Test
    void aParameterValueAddsItsParagraphAfterTheBaseText() {
        ComponentType ram = REGISTRY.require(StandardComponentTypes.RAM);

        String clocked = Descriptions.of(ram, Map.of());
        String level = Descriptions.of(ram, Map.of(MemoryTypes.WRITE, MemoryTypes.WRITE_LEVEL));
        String linked = Descriptions.of(ram, Map.of(MemoryTypes.LINK, "programas/ram.hex"));

        assertThat(level).startsWith(clocked).contains("\n\n").contains("WE = 1");
        assertThat(level.length()).isGreaterThan(clocked.length());
        assertThat(linked).startsWith(clocked).contains("nunca");
    }

    @Test
    void theSignedComparatorExplainsTwosComplement() {
        ComponentType comparator = REGISTRY.require(StandardComponentTypes.COMPARATOR);

        String signed = Descriptions.of(comparator, Map.of(StandardComponentTypes.SIGNEDNESS,
                StandardComponentTypes.SIGNEDNESS_SIGNED));

        assertThat(signed).startsWith(Descriptions.of(comparator, Map.of())).contains("complemento a dos");
    }

    @Test
    void theArithmeticPartsExplainTheirAssumptionsAndTheShifterFollowsItsType() {
        ComponentType shifter = REGISTRY.require(ArithmeticTypes.SHIFTER);

        String arithmetic = Descriptions.of(shifter, Map.of(ArithmeticTypes.SHIFT_TYPE,
                ArithmeticTypes.ARITHMETIC_RIGHT));
        String rotating = Descriptions.of(shifter, Map.of(ArithmeticTypes.SHIFT_TYPE, ArithmeticTypes.ROTATE_LEFT));

        assertThat(arithmetic).startsWith("Desplazador: mueve").contains("bit más alto");
        assertThat(rotating).contains("vuelven a entrar").isNotEqualTo(arithmetic);
        assertThat(Descriptions.of(REGISTRY.require(ArithmeticTypes.DIVIDER), Map.of())).contains("división por cero");
        assertThat(Descriptions.of(REGISTRY.require(ArithmeticTypes.DIVIDER),
                Map.of(StandardComponentTypes.SIGNEDNESS, StandardComponentTypes.SIGNEDNESS_SIGNED)))
                .contains("hacia cero");
    }

    @Test
    void invalidParametersStillGiveTheBaseText() {
        ComponentType constant = REGISTRY.require(StandardComponentTypes.CONSTANT);

        assertThat(Descriptions.of(constant, Map.of("value", "no es un número")))
                .isEqualTo(Descriptions.of(constant, Map.of()));
    }

    @Test
    void aUserComponentShowsItsDefinitionsDescriptionAndNothingWithoutOne() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        ComponentDefinition described = inverter.withDescription("Invierte la entrada.\n\nSirve para probar.");

        assertThat(Descriptions.of(SubcircuitTypes.typeOf(described), Map.of()))
                .isEqualTo("Invierte la entrada.\n\nSirve para probar.");
        assertThat(Descriptions.of(SubcircuitTypes.typeOf(inverter.withDescription("")), Map.of())).isEmpty();
    }

    @Test
    void theSummaryIsTheFirstParagraph() {
        ComponentDefinition described = ExampleDefinitions.inverter()
                .withDescription("Invierte la entrada.\n\nSirve para probar.");

        assertThat(Descriptions.summary(SubcircuitTypes.typeOf(described), Map.of()))
                .hasValue("Invierte la entrada.");
        assertThat(Descriptions.summary(SubcircuitTypes.typeOf(described.withDescription("  ")), Map.of()))
                .isEmpty();
    }

    @Test
    void theTextFollowsTheLanguage() {
        ComponentType adder = REGISTRY.require(StandardComponentTypes.ADDER);
        assertThat(Descriptions.of(adder, Map.of())).contains("acarreo");

        Messages.initialize(Locale.ENGLISH);

        assertThat(Descriptions.of(adder, Map.of())).contains("carry");
        assertThat(Descriptions.of(adder, Map.of(), Messages.bundle(Messages.SPANISH))).contains("acarreo");
    }

    @Test
    void aTypeWithoutTextsHasNoDescription() {
        ComponentType unknown = new ComponentType("no-such-type", ComponentCategory.DEBUG, new GridSize(2, 2),
                List.of(), List.of(), parameters -> null);

        assertThat(Descriptions.of(unknown, Map.of())).isEmpty();
        assertThat(Descriptions.summary(unknown, Map.of())).isEqualTo(Optional.empty());
    }
}
