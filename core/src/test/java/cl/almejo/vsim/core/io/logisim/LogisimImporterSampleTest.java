package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole import on the real Logisim file of {@link LogisimSampleTest} (manual, skipped without
 * {@code -Dvsim.logisim.sample}): imports it, saves and reopens it with {@link CircuitFormat} (to
 * {@code -Dvsim.logisim.sampleOutput} when given), then runs the reopened circuit for a while at full speed and prints
 * what it shows. Running like Logisim is not checked here (E3 compares).
 */
class LogisimImporterSampleTest {

    private static final String OUTPUT_PROPERTY = "vsim.logisim.sampleOutput";
    private static final int CLOCK_CYCLES = 3000;
    private static final long WALL_CLOCK_LIMIT_MILLISECONDS = 120_000;

    @Test
    void theSampleImportsSavesReopensAndRuns() throws Exception {
        LogisimProject project = LogisimSampleTest.readSample();
        ComponentTypeRegistry registry = StandardComponentTypes.registry();

        long importStart = System.nanoTime();
        ImportResult result = LogisimImporter.importProject(project, registry);
        long importMilliseconds = (System.nanoTime() - importStart) / 1_000_000;
        CircuitDocument document = result.document();
        printCounts(document, result.notes(), importMilliseconds);

        CircuitFormat format = new CircuitFormat(registry);
        long saveStart = System.nanoTime();
        String saved = format.write(document);
        long saveMilliseconds = (System.nanoTime() - saveStart) / 1_000_000;
        String output = System.getProperty(OUTPUT_PROPERTY);
        if (output != null && !output.isBlank()) {
            Files.writeString(Path.of(output), saved, StandardCharsets.UTF_8);
            System.out.println("   saved to " + output);
        }
        long openStart = System.nanoTime();
        CircuitDocument reopened = format.read(saved);
        long openMilliseconds = (System.nanoTime() - openStart) / 1_000_000;
        System.out.println("== save " + saveMilliseconds + " ms (" + saved.length() / 1024 + " KiB), open "
                + openMilliseconds + " ms");
        assertThat(format.write(reopened)).isEqualTo(saved);

        run(reopened, registry);
    }

    private static void printCounts(CircuitDocument document, List<ImportNote> notes, long importMilliseconds) {
        System.out.println("== import of «" + document.name() + "» in " + importMilliseconds + " ms: "
                + document.definitions().size() + " definitions");
        System.out.println("   main: " + document.components().size() + " parts, " + document.wires().size()
                + " wires, " + document.labels().size() + " texts");
        int parts = document.components().size();
        int wires = document.wires().size();
        for (ComponentDefinition definition : document.definitions()) {
            System.out.println("   «" + definition.name() + "» (" + definition.id() + "): "
                    + definition.body().components().size() + " parts, " + definition.body().wires().size()
                    + " wires, " + definition.body().labels().size() + " texts, " + definition.pinout().size()
                    + " pins, box " + definition.size().width() + " x " + definition.size().height());
            parts += definition.body().components().size();
            wires += definition.body().wires().size();
        }
        System.out.println("   in all: " + parts + " parts, " + wires + " wires");
        Map<String, Long> byKind = notes.stream().collect(Collectors.groupingBy(
                note -> note.kind() + " " + note.messageKey(), TreeMap::new, Collectors.counting()));
        System.out.println("== notes: " + notes.size());
        byKind.forEach((kind, count) -> System.out.println("   " + kind + ": " + count));
        notes.stream().filter(note -> note.kind() != ImportNote.Kind.APPROXIMATED
                        || !note.messageKey().equals(LogisimNotes.NO_DELAY))
                .forEach(note -> System.out.println("      " + note.circuit() + " "
                        + note.where().map(Object::toString).orElse("") + " " + note.messageKey() + " "
                        + note.arguments()));
    }

    /** Runs the circuit {@link #CLOCK_CYCLES} clock cycles (or until the wall-clock limit) and prints what it shows. */
    private static void run(CircuitDocument document, ComponentTypeRegistry baseRegistry) {
        ComponentTypeRegistry registry = baseRegistry.extendedWith(SubcircuitTypes.resolverFor(document));
        long flattenStart = System.nanoTime();
        SimulationEngine engine = new SimulationEngine();
        engine.update(new CircuitFlattener(registry)
                .flatten(document, new NetlistCompiler(registry).compile(document)));
        System.out.println("== simulation: compiled and flattened in " + (System.nanoTime() - flattenStart) / 1_000_000
                + " ms");
        Map<String, Component> flat = new LinkedHashMap<>();
        collect(document, document.components(), "", flat);
        long cycle = document.components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.CLOCK))
                .mapToLong(component -> number(component, "time-up") + number(component, "time-down"))
                .findFirst().orElse(2 * LogisimTiming.HALF_PERIOD);
        List<String> programCounters = flat.entrySet().stream()
                .filter(entry -> entry.getValue().typeId().equals(StandardComponentTypes.REGISTER)
                        && entry.getValue().label().equalsIgnoreCase("pc"))
                .map(Map.Entry::getKey).toList();
        long runStart = System.nanoTime();
        int cycles = 0;
        while (cycles < CLOCK_CYCLES
                && (System.nanoTime() - runStart) / 1_000_000 < WALL_CLOCK_LIMIT_MILLISECONDS) {
            engine.runUntil(engine.now() + cycle);
            cycles++;
            if (cycles % (CLOCK_CYCLES / 10) == 0) {
                Map<String, VisibleState> states = engine.snapshot().componentStates();
                System.out.println("   after " + cycles + " cycles: " + programCounters.stream()
                        .map(id -> "PC " + states.getOrDefault(id, new VisibleState.None()))
                        .collect(Collectors.joining(", ")));
            }
        }
        long runMilliseconds = (System.nanoTime() - runStart) / 1_000_000;
        List<Problem> problems = engine.snapshot().problems();
        System.out.println("   " + cycles + " clock cycles of " + cycle + " in " + runMilliseconds + " ms, time "
                + engine.now() + ", problems " + problems.size() + " "
                + problems.stream().collect(Collectors.groupingBy(problem -> problem.getClass().getSimpleName(),
                TreeMap::new, Collectors.counting())));
        System.out.println("      connectivity by cause: " + problems.stream()
                .filter(ConnectivityProblem.class::isInstance).map(ConnectivityProblem.class::cast)
                .collect(Collectors.groupingBy(problem -> problem.cause() + " in "
                                + innerType(flat, problem.elementId()), TreeMap::new, Collectors.counting())));
        problems.stream().filter(TunnelProblem.class::isInstance)
                .forEach(problem -> System.out.println("      " + problem));
        Map<String, VisibleState> states = engine.snapshot().componentStates();
        flat.forEach((id, component) -> {
            if (component.typeId().equals(StandardComponentTypes.REGISTER) && !component.label().isBlank()) {
                System.out.println("   register " + id + " «" + component.label() + "» = "
                        + states.getOrDefault(id, new VisibleState.None()));
            }
        });
        flat.forEach((id, component) -> {
            if (component.typeId().equals(StandardComponentTypes.TERMINAL)) {
                engine.terminal(id).ifPresent(terminal -> System.out.println("   terminal " + id + " printed "
                        + terminal.screen().printedCount() + " characters: «" + terminal.screen().transcript()
                        + "»"));
            }
        });
    }

    /** The type of the part a problem names (by flat id), for counting. */
    private static String innerType(Map<String, Component> flat, String elementId) {
        Component component = flat.get(elementId);
        return component == null ? "?" : component.typeId();
    }

    private static long number(Component component, String parameter) {
        Object value = component.parameters().get(parameter);
        return value instanceof Number number ? number.longValue() : LogisimTiming.HALF_PERIOD;
    }

    /** Every component of {@code components} and of the bodies they stand for, by flat id. */
    private static void collect(CircuitDocument document, List<Component> components, String prefix,
                                Map<String, Component> flat) {
        Function<String, Optional<ComponentDefinition>> definitionOf = typeId ->
                SubcircuitTypes.definitionIdOf(typeId).flatMap(document::definition);
        for (Component component : components) {
            String id = prefix + component.id().value();
            flat.put(id, component);
            definitionOf.apply(component.typeId()).ifPresent(definition ->
                    collect(document, definition.body().components(), id + InstancePath.SEPARATOR, flat));
        }
    }
}
