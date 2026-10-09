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
}
