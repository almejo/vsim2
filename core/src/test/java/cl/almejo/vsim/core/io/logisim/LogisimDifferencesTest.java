package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * What the import says when the imported circuit does not do what Logisim's does (the «Diferencias reales»): a RAM
 * whose write control VSim reads otherwise, a counter whose LD means something else, a loose control of a controlled
 * buffer; and a part with no free place near its own, which goes apart rather than touch another net.
 */
class LogisimDifferencesTest {

    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();
    /** A wire every 7 px, horizontal and vertical: once spread, a wire passes over every grid point of the area. */
    private static final int MESH_STEP = 7;
    private static final int MESH_LINES = 60;

    @Test
    void aSeparateBusRamWithItsWriteEnableLooseWritesInLogisimButNotInVsim() throws Exception {
        List<ImportNote> notes = notesOf("""
                <comp lib="4" loc="(300,200)" name="RAM"><a name="bus" val="separate"/></comp>
                """);

        assertThat(notes).filteredOn(note -> note.kind() == ImportNote.Kind.DIFFERENT)
                .extracting(ImportNote::messageKey).containsExactly(LogisimNotes.LOOSE_WRITE_ENABLE);
    }

    @Test
    void aCombinedOrAsynchronousRamNeverWritesInVsimAndSaysSo() throws Exception {
        List<ImportNote> notes = notesOf("""
                <comp lib="4" loc="(300,200)" name="RAM"/>
                <comp lib="4" loc="(600,200)" name="RAM"><a name="bus" val="asynch"/></comp>
                """);

        assertThat(notes).filteredOn(note -> note.kind() == ImportNote.Kind.DIFFERENT)
                .extracting(ImportNote::messageKey, note -> note.arguments().getFirst())
                .containsExactlyInAnyOrder(
                        tuple(LogisimNotes.RAM_WITHOUT_WRITE_ENABLE, "combined"),
                        tuple(LogisimNotes.RAM_WITHOUT_WRITE_ENABLE, "asynch"));
    }

    @Test
    void aCounterWithItsLoadConnectedIsNotedAndOneWithoutIsNot() throws Exception {
        List<ImportNote> notes = notesOf("""
                <comp lib="4" loc="(300,100)" name="Counter"/>
                <comp lib="0" loc="(250,90)" name="Constant"/>
                <wire from="(250,90)" to="(270,90)"/>
                <comp lib="4" loc="(300,400)" name="Counter"/>
                """);

        assertThat(notes).filteredOn(note -> note.kind() == ImportNote.Kind.DIFFERENT)
                .singleElement().satisfies(note -> {
                    assertThat(note.messageKey()).isEqualTo(LogisimNotes.COUNTER_LOAD);
                    assertThat(note.where()).contains(new LogisimPoint(300, 100));
                });
    }

    @Test
    void aControlledBufferWithItsControlLooseGivesAnErrorInLogisimAndNothingInVsim() throws Exception {
        List<ImportNote> notes = notesOf("""
                <comp lib="1" loc="(300,300)" name="Controlled Buffer"/>
                <comp lib="1" loc="(300,500)" name="Controlled Buffer"/>
                <comp lib="0" loc="(290,540)" name="Constant"><a name="facing" val="north"/></comp>
                <wire from="(290,510)" to="(290,540)"/>
                """);

        assertThat(notes).filteredOn(note -> note.kind() == ImportNote.Kind.DIFFERENT)
                .singleElement().satisfies(note -> {
                    assertThat(note.messageKey()).isEqualTo(LogisimNotes.LOOSE_BUFFER_CONTROL);
                    assertThat(note.where()).contains(new LogisimPoint(300, 300));
                });
    }

    @Test
    void aPartWithNoFreePlaceNearItsOwnGoesApartAndTouchesNoOtherNet() throws Exception {
        ImportResult result = importCircuit(mesh() + """
                <comp lib="1" loc="(213,213)" name="NOT Gate"/>
                """);
        CircuitDocument document = result.document();
        Component not = document.components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.BUFFER))
                .findFirst().orElseThrow();
        Netlist netlist = new NetlistCompiler(REGISTRY).compile(document);

        for (int pin = 0; pin < REGISTRY.typeOf(not).pinCount(); pin++) {
            Optional<Net> net = netlist.netForPin(new PinRef(not.id(), pin));
            assertThat(net).hasValueSatisfying(found -> {
                assertThat(found.wires()).isEmpty();
                assertThat(found.pins()).hasSize(1);
            });
        }
        assertThat(result.notes()).extracting(ImportNote::messageKey).contains(LogisimNotes.CROWDED_PART);
    }

    /** Horizontal and vertical wires over a square around (213, 213), each its own net (they only cross). */
    private static String mesh() {
        StringBuilder wires = new StringBuilder();
        int end = MESH_STEP * MESH_LINES;
        for (int line = 0; line <= MESH_LINES; line++) {
            int at = line * MESH_STEP;
            wires.append("<wire from=\"(-20,").append(at).append(")\" to=\"(").append(end + 20).append(',').append(at)
                    .append(")\"/>\n");
            wires.append("<wire from=\"(").append(at).append(",-20)\" to=\"(").append(at).append(',').append(end + 20)
                    .append(")\"/>\n");
        }
        return wires.toString();
    }

    private static List<ImportNote> notesOf(String components) throws Exception {
        return importCircuit(components).notes();
    }

    private static ImportResult importCircuit(String components) throws Exception {
        String file = """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <project source="2.7.1" version="1.0">
                  <lib desc="#Wiring" name="0"/>
                  <lib desc="#Gates" name="1"/>
                  <lib desc="#Memory" name="4"/>
                  <main name="main"/>
                  <circuit name="main">
                """ + components + """
                  </circuit>
                </project>
                """;
        return LogisimImporter.importFrom(new ByteArrayInputStream(file.getBytes(StandardCharsets.UTF_8)), REGISTRY);
    }
}
