package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * The classic circuits of Archivo ▸ Ejemplos (spec M6.1 §3): plain format-v2 files under {@link #RESOURCE_DIRECTORY}.
 * The list is explicit (no classpath scanning), so it works the same from the classes folder and from the shaded jar.
 */
public final class BundledExamples {

    /** Where the example files live on the classpath (and under {@code core/src/main/resources}). */
    public static final String RESOURCE_DIRECTORY = "cl/almejo/vsim/core/bundled/examples";

    private static final List<BundledExample> ALL = List.of(
            example("full-adder"),
            example("adder-4-bits"),
            example("mux-4-1"),
            example("decoder-2-4"),
            example("parity-3-bits"),
            example("multiplier-2x2"),
            example("register-4-bits"),
            example("shift-register-4-bits"),
            example("counter-4-bits"),
            example("johnson-counter"),
            example("bus-8-bits"),
            example("alu-4-bits"));

    private BundledExamples() {
    }

    /** Every example, in menu order. */
    public static List<BundledExample> all() {
        return ALL;
    }

    public static List<String> ids() {
        return ALL.stream().map(BundledExample::id).toList();
    }

    /**
     * A new document read from the example's file; each call gives an independent copy. Its embedded definitions come
     * with the file. An unknown id is IllegalArgumentException; a missing or unreadable resource is {@code READ_ERROR}.
     */
    public static CircuitDocument open(String id, ComponentTypeRegistry registry) throws CircuitFormatException {
        return open(require(id), registry, BundledExamples.class::getResourceAsStream);
    }

    /** {@link #open(String, ComponentTypeRegistry)} with the resource lookup given (tests: a missing resource). */
    static CircuitDocument open(BundledExample example, ComponentTypeRegistry registry,
                                Function<String, InputStream> resources) throws CircuitFormatException {
        String content;
        try (InputStream stream = resources.apply(example.resourceName())) {
            if (stream == null) {
                throw new CircuitFormatException(CircuitFormatException.Reason.READ_ERROR, example.resourceName());
            }
            content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.READ_ERROR, example.resourceName(),
                    exception);
        }
        return new CircuitReader(registry).read(content);
    }

    static BundledExample require(String id) {
        return ALL.stream()
                .filter(example -> example.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown example: " + id));
    }

    private static BundledExample example(String id) {
        return new BundledExample(id, "/" + RESOURCE_DIRECTORY + "/" + id + ".vsim");
    }
}
