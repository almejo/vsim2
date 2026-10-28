package cl.almejo.vsim.core.logic;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GateCostTest {

    @Test
    void theFamilyComesFromTheTypeAndNegateOutput() {
        assertThat(GateCost.family("and", Map.of("inputs", 3, "negate", false))).contains("AND");
        assertThat(GateCost.family("and", Map.of("inputs", 2, "negate", true))).contains("NAND");
        assertThat(GateCost.family("or", Map.of("negate", true))).contains("NOR");
        assertThat(GateCost.family("xor", Map.of("negate", false))).contains("XOR");
        assertThat(GateCost.family("xor", Map.of("negate", true))).contains("XNOR");
        assertThat(GateCost.family("buffer", Map.of("negate", true))).contains("NOT");
    }

    @Test
    void whatIsNotAGateOfAFamilyHasNone() {
        assertThat(GateCost.family("buffer", Map.of("negate", false))).isEqualTo(Optional.empty());
        assertThat(GateCost.family("input-pin", Map.of())).isEqualTo(Optional.empty());
        assertThat(GateCost.family("mux", Map.of("inputs", "2"))).isEqualTo(Optional.empty());
    }

    @Test
    void aGateIsCountedByItsNameAndInputs() {
        assertThat(GateCost.key("and", Map.of("inputs", 3, "negate", true))).isEqualTo("nand3");
        assertThat(GateCost.key("xor", Map.of("inputs", 2, "negate", true))).isEqualTo("xnor2");
        assertThat(GateCost.key("buffer", Map.of("negate", true))).isEqualTo("not");
        assertThat(GateCost.familyOfKey("xnor2")).isEqualTo("XNOR");
        assertThat(GateCost.familyOfKey("not")).isEqualTo("NOT");
        assertThat(GateCost.FAMILIES).contains("XNOR");
    }
}
