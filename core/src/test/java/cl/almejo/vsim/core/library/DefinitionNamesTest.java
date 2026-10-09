package cl.almejo.vsim.core.library;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefinitionNamesTest {

    @Test
    void blankNamesAndNamesTakenIgnoringCaseAndSpacesAreProblems() {
        assertThat(DefinitionNames.problem(null, List.of())).contains(DefinitionNames.NameProblem.EMPTY);
        assertThat(DefinitionNames.problem(" \t", List.of())).contains(DefinitionNames.NameProblem.EMPTY);
        assertThat(DefinitionNames.problem("  sumador ", List.of("Sumador"))).contains(DefinitionNames.NameProblem.TAKEN);
        assertThat(DefinitionNames.problem("Sumador 2", List.of("Sumador"))).isEmpty();
        assertThat(DefinitionNames.same(" A ", "a")).isTrue();
        assertThat(DefinitionNames.same("A", "B")).isFalse();
    }

    @Test
    void uniqueAppendsTheFirstFreeNumber() {
        assertThat(DefinitionNames.unique(" Inversor ", List.of("inversor", "Inversor 2"))).isEqualTo("Inversor 3");
        assertThat(DefinitionNames.unique("Negador", List.of("Inversor"))).isEqualTo("Negador");
    }
}
