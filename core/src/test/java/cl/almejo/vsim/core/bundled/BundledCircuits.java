package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.io.CircuitFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every example of Archivo ▸ Ejemplos (spec M6.1 §3.1) by id, in menu order, from the builder classes. Run
 * {@link #main} (or {@code BundledCircuitsTest.writeTheResources}, see its comment) to rewrite the files under
 * {@code core/src/main/resources/cl/almejo/vsim/core/bundled/examples}; {@code BundledCircuitsTest} checks that the
 * committed files are exactly what these builders produce.
 */
public final class BundledCircuits {

    private BundledCircuits() {
    }

    /** One builder per example, in the order of {@link BundledExamples#ids()}. */
    public static Map<String, Supplier<CircuitBuilder>> builders() {
        Map<String, Supplier<CircuitBuilder>> builders = new LinkedHashMap<>();
        builders.put("full-adder", CombinationalExamples::fullAdder);
        builders.put("adder-4-bits", CombinationalExamples::adderFourBits);
        builders.put("mux-4-1", CombinationalExamples::multiplexer);
        builders.put("decoder-2-4", CombinationalExamples::decoder);
        builders.put("parity-3-bits", CombinationalExamples::parity);
        builders.put("multiplier-2x2", CombinationalExamples::multiplier);
        builders.put("register-4-bits", SequentialExamples::register);
        builders.put("shift-register-4-bits", SequentialExamples::shiftRegister);
        builders.put("counter-4-bits", SequentialExamples::counter);
        builders.put("johnson-counter", SequentialExamples::johnsonCounter);
        builders.put("bus-8-bits", BusAndAluExamples::bus);
        builders.put("alu-4-bits", BusAndAluExamples::alu);
        builders.put("mini-alu", BlockExamples::miniAlu);
        builders.put("accumulator", BlockExamples::accumulator);
        builders.put("rom-program", MemoryExamples::romProgram);
        builders.put("ram-fill", MemoryExamples::ramFill);
        builders.put("arithmetic", ClassExamples::arithmetic);
        builders.put("modular-elements", ClassExamples::modularElements);
        return builders;
    }

    /** Writes every example into {@code arguments[0]} (default: the resource folder, run from {@code core/}). */
    public static void main(String[] arguments) throws IOException {
        Path directory = Path.of(arguments.length > 0 ? arguments[0]
                : "src/main/resources/" + BundledExamples.RESOURCE_DIRECTORY);
        Files.createDirectories(directory);
        for (Map.Entry<String, Supplier<CircuitBuilder>> entry : builders().entrySet()) {
            CircuitBuilder builder = entry.getValue().get();
            Path file = directory.resolve(entry.getKey() + ".vsim");
            Files.writeString(file, new CircuitFormat(builder.registry()).write(builder.document()) + "\n");
            System.out.println("wrote " + file);
        }
    }
}
