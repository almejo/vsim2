package cl.almejo.vsim.core.component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void aBooleanParameterHasItsDefaultAndNoRange() {
        ParameterSpec negate = ParameterSpec.bool("negate", false);

        assertThat(negate.kind()).isEqualTo(ParameterKind.BOOLEAN);
        assertThat(negate.defaultValue()).isEqualTo(false);
        assertThat(ParameterSpec.bool("negate", true).defaultValue()).isEqualTo(true);
    }

    @Test
    void aBooleanNormalizesABooleanOrTheTextsTrueAndFalseInAnyCase() {
        ParameterSpec negate = ParameterSpec.bool("negate", false);

        assertThat(negate.normalize(Boolean.TRUE)).isEqualTo(true);
        assertThat(negate.normalize(false)).isEqualTo(false);
        assertThat(negate.normalize("TRUE")).isEqualTo(true);
        assertThat(negate.normalize("false")).isEqualTo(false);
        assertThat(negate.normalize("False")).isEqualTo(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "1", "", "maybe"})
    void aBooleanRefusesAnyOtherText(String text) {
        assertThatThrownBy(() -> ParameterSpec.bool("negate", false).normalize(text))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negate");
    }

    @Test
    void aBooleanRefusesANumber() {
        assertThatThrownBy(() -> ParameterSpec.bool("negate", false).normalize(1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anIntegerAcceptsEveryIntegralNumberType() {
        ParameterSpec width = ParameterSpec.integer("width", 1, 1, 32);
        assertThat(width.normalize((byte) 8)).isEqualTo(8);
        assertThat(width.normalize((short) 8)).isEqualTo(8);
        assertThat(width.normalize(8)).isEqualTo(8);
        assertThat(width.normalize(8L)).isEqualTo(8);
        assertThat(width.normalize(" 8 ")).isEqualTo(8);
        assertThat(width.normalize(java.math.BigInteger.valueOf(8))).isEqualTo(8);
        assertThat(width.normalize(8.0)).as("a whole Double, as Jackson reads 8.0").isEqualTo(8);
        assertThat(width.normalize(new java.math.BigDecimal("8.0"))).isEqualTo(8);
    }

    @Test
    void anIntegerRefusesWhatIsNotAWholeNumber() {
        ParameterSpec width = ParameterSpec.integer("width", 1, 1, 32);
        for (Object invalid : new Object[]{8.9, 8.5f, new java.math.BigDecimal("8.5"), Double.NaN,
                Double.POSITIVE_INFINITY, new java.math.BigInteger("18446744073709551624"), 33L, "8.9"}) {
            assertThatThrownBy(() -> width.normalize(invalid)).as(String.valueOf(invalid))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("width");
        }
    }
}
