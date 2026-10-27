package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.io.CircuitFormatException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BundledExamplesTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @Test
    void theExamplesAreListedInMenuOrderEachWithItsOwnResource() {
        assertThat(BundledExamples.ids()).containsExactly("full-adder", "adder-4-bits", "mux-4-1", "decoder-2-4",
                "parity-3-bits", "multiplier-2x2", "register-4-bits", "shift-register-4-bits", "counter-4-bits",
                "johnson-counter", "bus-8-bits", "alu-4-bits", "mini-alu", "accumulator", "rom-program");
        assertThat(BundledExamples.all()).first().isEqualTo(new BundledExample("full-adder",
                "/cl/almejo/vsim/core/bundled/examples/full-adder.vsim"));
        assertThat(BundledExamples.all()).extracting(BundledExample::resourceName)
                .allMatch(name -> name.startsWith("/" + BundledExamples.RESOURCE_DIRECTORY + "/"))
                .doesNotHaveDuplicates();
    }

    @Test
    void anUnknownIdIsRejected() {
        assertThatThrownBy(() -> BundledExamples.open("jk-flip-flop", registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jk-flip-flop");
    }

    @Test
    void aMissingResourceIsAReadError() {
        BundledExample example = BundledExamples.all().get(0);
        assertThatThrownBy(() -> BundledExamples.open(example, registry, name -> null))
                .isInstanceOfSatisfying(CircuitFormatException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(CircuitFormatException.Reason.READ_ERROR);
                    assertThat(exception.detail()).isEqualTo(example.resourceName());
                });
    }

    @Test
    void aResourceThatFailsWhileReadingIsAReadError() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk gone");
            }
        };
        assertThatThrownBy(() -> BundledExamples.open(BundledExamples.all().get(1), registry, name -> failing))
                .isInstanceOfSatisfying(CircuitFormatException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(CircuitFormatException.Reason.READ_ERROR));
    }

    @Test
    void eachOpenReadsTheResourceOfThatExampleIntoANewDocument() throws Exception {
        CircuitDocument source = new CircuitDocument();
        source.setName("Sumador completo");
        source.add(registry.require(StandardComponentTypes.SWITCH).newComponent(source.newComponentId(),
                new GridPoint(2, 4), Rotation.R0, Map.of("value", "1"), "A"));
        byte[] content = new CircuitFormat(registry).write(source).getBytes(StandardCharsets.UTF_8);
        List<String> asked = new ArrayList<>();
        BundledExample example = BundledExamples.all().get(0);

        CircuitDocument first = BundledExamples.open(example, registry, name -> {
            asked.add(name);
            return new ByteArrayInputStream(content);
        });
        CircuitDocument second = BundledExamples.open(example, registry, name -> new ByteArrayInputStream(content));

        assertThat(asked).containsExactly(example.resourceName());
        assertThat(first.name()).isEqualTo("Sumador completo");
        assertThat(first.components()).singleElement().satisfies(component -> assertThat(component.label())
                .isEqualTo("A"));
        assertThat(second).isNotSameAs(first);
        assertThat(second.components()).isEqualTo(first.components());
    }
}
