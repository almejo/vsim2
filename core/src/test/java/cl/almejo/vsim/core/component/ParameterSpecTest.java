package cl.almejo.vsim.core.component;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterSpecTest {

    @Test
    void notHiddenByDefault() {
        assertThat(ParameterSpec.text("contents", "").hidden()).isFalse();
        assertThat(ParameterSpec.integer("width", 1, 1, 8).hidden()).isFalse();
    }

    @Test
    void asHiddenKeepsKindDefaultAndNormalizer() {
        ParameterSpec hidden = ParameterSpec.text("contents", "", String::trim).asHidden();

        assertThat(hidden.hidden()).isTrue();
        assertThat(hidden.kind()).isEqualTo(ParameterKind.TEXT);
        assertThat(hidden.defaultValue()).isEqualTo("");
        assertThat(hidden.normalize("  x ")).isEqualTo("x");
    }

    @Test
    void hiddenAndDisplayOnlyCombine() {
        ParameterSpec both = ParameterSpec.text("link", "").asHidden().asDisplayOnly();

        assertThat(both.hidden()).isTrue();
        assertThat(both.displayOnly()).isTrue();
        assertThat(ParameterSpec.text("link", "").asDisplayOnly().asHidden().displayOnly()).isTrue();
    }
}
