package cl.almejo.vsim.ui.preferences;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PreferencesSearchTest {

    @Test
    void foldRemovesAccentsAndCase() {
        assertThat(PreferencesSearch.fold("Librería")).isEqualTo("libreria");
        assertThat(PreferencesSearch.fold("MAYÚS")).isEqualTo("mayus");
        assertThat(PreferencesSearch.fold("Español ñandú")).isEqualTo("espanol nandu");
    }

    @Test
    void aQueryMatchesAsASubstringIgnoringAccentsAndCaseOnBothSides() {
        assertThat(PreferencesSearch.matches("Animaciones", "animacion")).isTrue();
        assertThat(PreferencesSearch.matches("Tecla para invertir Mayús", "MAYÚS")).isTrue();
        assertThat(PreferencesSearch.matches("Tecla para invertir Mayús", "mayus")).isTrue();
        assertThat(PreferencesSearch.matches("Directorio de la librería", "libreria")).isTrue();
        assertThat(PreferencesSearch.matches("Directorio de la libreria", "librería")).isTrue();
        assertThat(PreferencesSearch.matches("Animaciones", "zzz")).isFalse();
    }

    @Test
    void theQueryIsTrimmedAndABlankQueryMatchesEverything() {
        assertThat(PreferencesSearch.matches("Tema", "  tema ")).isTrue();
        assertThat(PreferencesSearch.matches("Tema", "")).isTrue();
        assertThat(PreferencesSearch.matches("Tema", "   ")).isTrue();
        assertThat(PreferencesSearch.matches("", "tema")).isFalse();
    }

    @Test
    void rangesPointIntoTheOriginalTextEvenWhenItHasAccents() {
        assertThat(PreferencesSearch.ranges("Tecla Mayús y mayús", "mayus"))
                .containsExactly(new PreferencesSearch.Range(6, 11), new PreferencesSearch.Range(14, 19));
        assertThat(PreferencesSearch.ranges("Tema", "")).isEmpty();
        assertThat(PreferencesSearch.ranges("Tema", "zz")).isEmpty();
    }
}
