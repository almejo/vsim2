package cl.almejo.vsim.core.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElementIdTest {

    @Test
    void elementIdsCannotContainTheHierarchySeparator() {
        assertThatThrownBy(() -> new ElementId("c5/c2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ElementId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ElementId("c5").value()).isEqualTo("c5");
    }
}
