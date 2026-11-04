package cl.almejo.vsim.core.io.logisim;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LogisimEnds} against Logisim 2.7.1 itself: {@code ends-fixture.circ} places Logisim's parts in every facing
 * and many attribute combinations, and {@code ends-fixture.ends} is what Logisim's own classes computed for their
 * ends (its {@code Dump} on that file).
 */
class LogisimEndsTest {

    /** A RAM with separate data has 8 ends: its data end 0 then only drives. */
    private static final int SEPARATE_RAM_ENDS = 8;

    @Test
    void everyEndIsWhereLogisimPutsIt() throws Exception {
        LogisimProject project = read("ends-fixture.circ");
        Map<LogisimPoint, String> expected = expectedEnds();
        LogisimCircuit main = project.circuit("main").orElseThrow();

        List<String> mismatches = new ArrayList<>();
        for (LogisimComponent component : main.components()) {
            String actual = describe(LogisimEnds.of(component, project));
            String wanted = expected.get(component.location());
            if (!actual.equals(wanted)) {
                mismatches.add(component.name() + " " + component.attributes() + "\n   Logisim: " + wanted
                        + "\n   ours:    " + actual);
            }
        }

        assertThat(main.components()).hasSize(expected.size());
        assertThat(mismatches).isEmpty();
    }

    @Test
    void endsTakeVsimsPinNamesAndSayWhichVsimLacks() throws Exception {
        LogisimProject project = read("ends-fixture.circ");
        LogisimComponent ram = project.circuit("main").orElseThrow().components().stream()
                .filter(component -> component.name().equals("RAM")).findFirst().orElseThrow();

        List<LogisimEnd> ends = LogisimEnds.of(ram, project);

        assertThat(ends).extracting(LogisimEnd::name).containsExactly("Q", "A", "CS", "OE", "CLR", "CLK", "WE", "D");
        assertThat(ends.stream().filter(end -> !end.inVsim()).map(LogisimEnd::name).collect(Collectors.toSet()))
                .isEqualTo(Set.of("OE", "CLR"));
    }

    @Test
    void aSubcircuitsEndsAreNamedByItsPinsAndFollowItsAppearance() throws Exception {
        LogisimProject project = read("ends-fixture.circ");
        LogisimComponent instance = project.circuit("main").orElseThrow().components().stream()
                .filter(component -> component.name().equals("sub")).findFirst().orElseThrow();

        List<LogisimEnd> ends = LogisimEnds.of(instance, project);

        assertThat(ends).extracting(LogisimEnd::name).containsExactlyInAnyOrder("100,100", "100,150", "200,50",
                "300,120");
        assertThat(ends).filteredOn(end -> end.name().equals("300,120")).singleElement()
                .satisfies(end -> {
                    assertThat(end.role()).isEqualTo(LogisimEnd.Role.OUTPUT);
                    assertThat(end.point()).isEqualTo(instance.location());
                });
    }

    @Test
    void aCircuitWithoutAppearanceHasItsEndsOnTheBoxItsSymbolIsDrawnWith() throws Exception {
        LogisimProject project = read("ends-fixture.circ");
        LogisimCircuit plain = project.circuit("plain").orElseThrow();
        List<LogisimAppearance.Shape> box = LogisimDefaultAppearance.build(LogisimInterfacePin.of(plain)).shapes();
        LogisimAppearance.Anchor anchor = box.stream().filter(LogisimAppearance.Anchor.class::isInstance)
                .map(LogisimAppearance.Anchor.class::cast).findFirst().orElseThrow();
        LogisimComponent eastward = project.circuit("main").orElseThrow().components().stream()
                .filter(component -> component.name().equals("plain")
                        && component.attributes().textOr("facing", "east").equals("east"))
                .findFirst().orElseThrow();

        List<LogisimEnd> ends = LogisimEnds.of(eastward, project);

        for (LogisimEnd end : ends) {
            LogisimAppearance.Port port = box.stream().filter(LogisimAppearance.Port.class::isInstance)
                    .map(LogisimAppearance.Port.class::cast)
                    .filter(candidate -> end.name().equals(candidate.pin().x() + "," + candidate.pin().y()))
                    .findFirst().orElseThrow();
            int portX = port.x() + port.width() / 2 - (anchor.x() + anchor.width() / 2);
            int portY = port.y() + port.height() / 2 - (anchor.y() + anchor.height() / 2);
            assertThat(end.point()).as(end.name()).isEqualTo(new LogisimPoint(eastward.location().x() + portX,
                    eastward.location().y() + portY));
        }
        assertThat(ends).hasSize(7);
    }

    @Test
    void aProbeAndATextHaveNoEnds() throws Exception {
        LogisimProject project = read("ends-fixture.circ");
        LogisimComponent probe = project.circuit("main").orElseThrow().components().stream()
                .filter(component -> component.name().equals("Probe")).findFirst().orElseThrow();

        assertThat(LogisimEnds.of(probe, project)).isEmpty();
    }

    /** Our ends as the fixture writes Logisim's: {@code x,y:I|O|IO:width} in order. */
    private static String describe(List<LogisimEnd> ends) {
        return ends.stream().map(end -> end.point().x() + "," + end.point().y() + ":" + flag(end) + ":" + end.width())
                .collect(Collectors.joining(" "));
    }

    private static String flag(LogisimEnd end) {
        return switch (end.role()) {
            case INPUT -> "I";
            case OUTPUT -> "O";
            case BIDIRECTIONAL, ALIAS -> "IO";
        };
    }

    /**
     * Logisim's ends by component location, with the flags of the few ends whose direction VSim reads otherwise
     * ({@link #semantic}) rewritten to ours, and probes (which become texts, joining nothing) without ends.
     */
    private Map<LogisimPoint, String> expectedEnds() throws Exception {
        Map<LogisimPoint, String> ends = new LinkedHashMap<>();
        try (InputStream input = getClass().getResourceAsStream("ends-fixture.ends");
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                String name = columns[1];
                ends.put(LogisimPoint.parse(columns[0]), name.equals("Probe") ? "" : semantic(name, columns[2]));
            }
        }
        return ends;
    }

    /**
     * Logisim flags the adder's carry out as an input (a slip in its {@code Adder.java}) and a memory's data end as
     * both ways even when it only drives (a ROM, a RAM with separate data); VSim reads them as outputs.
     */
    private static String semantic(String part, String ends) {
        String[] each = ends.split(" ");
        if (part.equals("Adder")) {
            each[4] = each[4].replace(":I:", ":O:");
        }
        if (part.equals("ROM") || part.equals("RAM") && each.length == SEPARATE_RAM_ENDS) {
            each[0] = each[0].replace(":IO:", ":O:");
        }
        return String.join(" ", each);
    }

    private LogisimProject read(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            return new LogisimReader().read(input);
        }
    }
}
