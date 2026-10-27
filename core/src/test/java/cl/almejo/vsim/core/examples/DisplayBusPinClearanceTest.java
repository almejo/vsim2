package cl.almejo.vsim.core.examples;

import cl.almejo.vsim.core.bundled.BundledExample;
import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.io.SavedFiles;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 7-segment displays' bus input was added to existing types (M7 follow-up): a pin joins any wire or pin on its point,
 * so a circuit saved before could change meaning. No bundled example and no test fixture saved before it
 * (the new curated circuits in examples/ use that pin on purpose) may have anything on that new pin, in its own body
 * or in the body of any component it defines.
 */
class DisplayBusPinClearanceTest {

    private static final Path REPOSITORY = Path.of("..");

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @Test
    void noExampleTouchesTheBusInputOfADisplay() throws Exception {
        Map<String, CircuitDocument> documents = new LinkedHashMap<>();
        for (BundledExample example : BundledExamples.all()) {
            documents.put(example.id(), BundledExamples.open(example.id(), registry));
        }
        CircuitReader reader = new CircuitReader(registry);
        for (String file : trackedExampleFiles()) {
            if (file.endsWith(".vsim") || file.endsWith(".json")) {
                documents.put(file, reader.read(REPOSITORY.resolve(file)));
            }
        }
        assertThat(documents.keySet()).contains("bus-8-bits",
                "core/src/test/resources/fixtures/m3/selection-playground.vsim");

        List<String> touched = new ArrayList<>();
        int displays = 0;
        for (Map.Entry<String, CircuitDocument> entry : documents.entrySet()) {
            displays += touchedBusPins(entry.getKey(), entry.getValue(), touched);
            for (ComponentDefinition definition : entry.getValue().definitions()) {
                displays += touchedBusPins(entry.getKey() + " › " + definition.name(),
                        bodyOf(entry.getValue(), definition), touched);
            }
        }
        assertThat(displays).as("displays checked").isGreaterThanOrEqualTo(9);
        assertThat(touched).isEmpty();
    }

    @Test
    void savedFilesWithDisplaysLoadAndSaveByteForByte() throws Exception {
        CircuitFormat format = new CircuitFormat(registry);
        List<String> files = new ArrayList<>();
        for (BundledExample example : BundledExamples.all()) {
            files.add("core/src/main/resources" + example.resourceName());
        }
        trackedExampleFiles().stream().filter(file -> file.endsWith(".vsim")).forEach(files::add);
        int withDisplays = 0;
        for (String file : files) {
            String content = Files.readString(REPOSITORY.resolve(file));
            if (!content.contains(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY)) {
                continue;
            }
            withDisplays++;
            String written = format.write(format.read(content));
            assertThat(SavedFiles.withoutSavedWith(written.stripTrailing())).as(file + " (the file ends with a newline)")
                    .isEqualTo(SavedFiles.withoutSavedWith(content.stripTrailing()));
            // a memory's «Conexión» choice is also called "bus"; only a display pin named so would be a problem
            assertThat(written.replace("\"connection\" : \"bus\"", "")).as(file)
                    .doesNotContain("\"" + StandardComponentTypes.DISPLAY_BUS_PIN + "\"");
        }
        assertThat(withDisplays).isGreaterThanOrEqualTo(6);
    }

    /** Adds a line per display whose bus pin shares its net with a wire or another pin; returns the displays seen. */
    private int touchedBusPins(String where, CircuitDocument document, List<String> touched) {
        ComponentTypeRegistry resolving = registry.extendedWith(SubcircuitTypes.resolverFor(document));
        Netlist netlist = new NetlistCompiler(resolving).compile(document);
        int displays = 0;
        for (Component component : document.components()) {
            if (!StandardComponentTypes.isSevenSegmentsDisplay(component.typeId())) {
                continue;
            }
            displays++;
            PinRef busPin = new PinRef(component.id(),
                    StandardComponentTypes.sevenSegmentsBusPin(resolving.typeOf(component)));
            Net net = netlist.netForPin(busPin).orElseThrow();
            if (!net.wires().isEmpty() || !net.pins().equals(Set.of(busPin))) {
                touched.add(where + ": " + component.id().value() + " touches " + net.wires() + " " + net.pins());
            }
        }
        return displays;
    }

    private static CircuitDocument bodyOf(CircuitDocument document, ComponentDefinition definition) {
        CircuitDocument body = new CircuitDocument();
        document.definitionsUsedBy(definition.body().components()).forEach(body::putDefinition);
        definition.body().elements().forEach(body::add);
        return body;
    }

    private static List<String> trackedExampleFiles() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "ls-files", "core/src/test/resources/fixtures",
                "core/src/test/resources/vsim1")
                .directory(REPOSITORY.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as("git ls-files fixtures: %s", output).isZero();
        return output.lines().filter(line -> !line.isBlank()).toList();
    }
}
