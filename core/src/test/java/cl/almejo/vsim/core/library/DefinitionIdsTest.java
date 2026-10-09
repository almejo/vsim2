package cl.almejo.vsim.core.library;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefinitionIdsTest {

    @Test
    void slugsAreLowerCaseWithoutAccentsAndJoinedByDashes() {
        assertThat(DefinitionIds.slug("Sumador de 4 bits", Set.of())).isEqualTo("sumador-de-4-bits");
        assertThat(DefinitionIds.slug("  Ñandú   Rápido!! ", Set.of())).isEqualTo("nandu-rapido");
        assertThat(DefinitionIds.slug("***", Set.of())).isEqualTo(DefinitionIds.FALLBACK);
        assertThat(DefinitionIds.slug("Ünïcödé_Ⅻ", Set.of())).matches(ComponentDefinition.ID_PATTERN);
    }

    @Test
    void aTakenIdGetsTheFirstFreeNumber() {
        assertThat(DefinitionIds.slug("Inversor", Set.of("inversor", "inversor-2"))).isEqualTo("inversor-3");
        assertThat(DefinitionIds.slug("Inversór", Set.of("inversor"))).isEqualTo("inversor-2");
        assertThat(DefinitionIds.unique("inverter", Set.of("other"))).isEqualTo("inverter");
    }

    @Test
    void anEszettBecomesDoubleS() {
        assertThat(DefinitionIds.slug("Straße", Set.of())).isEqualTo("strasse");
        assertThat(DefinitionIds.slug("GROẞ", Set.of())).isEqualTo("gross");
    }

    @Test
    void longNamesAreCutAtAWordBoundaryAndStayUniqueWhenNumbered() {
        String name = "Unidad aritmetica y logica de treinta y dos bits con acarreo anticipado y banderas";

        String id = DefinitionIds.slug(name, Set.of());
        String numbered = DefinitionIds.slug(name, Set.of(id));

        assertThat(id).isEqualTo("unidad-aritmetica-y-logica-de-treinta-y-dos-bits-con-acarreo");
        assertThat(id.length()).isLessThanOrEqualTo(DefinitionIds.MAX_LENGTH);
        assertThat(numbered).isEqualTo("unidad-aritmetica-y-logica-de-treinta-y-dos-bits-con-acarreo-2");
        assertThat(numbered.length()).isLessThanOrEqualTo(DefinitionIds.MAX_LENGTH);
        assertThat(DefinitionIds.slug(name, Set.of(id, numbered))).endsWith("-3");
    }

    @Test
    void aLongWordWithoutDashesIsCutHard() {
        String id = DefinitionIds.slug("x".repeat(100), Set.of());
        String numbered = DefinitionIds.slug("x".repeat(100), Set.of(id));

        assertThat(id).isEqualTo("x".repeat(DefinitionIds.MAX_LENGTH));
        assertThat(numbered).isEqualTo("x".repeat(DefinitionIds.MAX_LENGTH - 2) + "-2");
        assertThat(numbered).matches(ComponentDefinition.ID_PATTERN);
    }
}
