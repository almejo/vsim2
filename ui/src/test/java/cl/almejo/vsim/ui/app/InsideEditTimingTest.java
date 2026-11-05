package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.InstancePaths;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The budget of an edit inside a component (spec §10.4): on the LRV32IM imported from Logisim, a wire added inside
 * its «ram» reaches a new snapshot of the whole circuit within 150 ms (median of 5). Manual: runs only with
 * {@code -Dvsim.lrv32im.sample=<path to lrv32im-importado.vsim>} (the file is the user's, never committed).
 */
class InsideEditTimingTest {

    private static final String SAMPLE_PROPERTY = "vsim.lrv32im.sample";
    private static final String DEFINITION = "ram";
    private static final long BUDGET_MILLISECONDS = 150;
    private static final int RUNS = 5;

    @Test
    void anInsideEditReachesANewSnapshotWithinTheBudget() throws Exception {
        String sample = System.getProperty(SAMPLE_PROPERTY, "");
        assumeTrue(!sample.isBlank(), "set -D" + SAMPLE_PROPERTY + " to time an inside edit on the LRV32IM");
        try (OpenDocument openDocument = OpenDocument.open(Path.of(sample), StandardComponentTypes.registry())) {
            String path = InstancePaths.usesOf(openDocument.document(), DEFINITION).getFirst().path();
            OpenDocument body = openDocument.openBody(DEFINITION, Optional.of(path));
            openDocument.simulationPublished().get(30, TimeUnit.SECONDS);
            List<Long> elapsed = new ArrayList<>();
            for (int run = 0; run < RUNS; run++) {
                elapsed.add(timeOneEdit(openDocument, body, run));
            }
            List<Long> sorted = elapsed.stream().sorted().toList();
            long median = sorted.get(RUNS / 2);
            System.out.println("Inside edit on the LRV32IM, ms per run: " + elapsed + ", median " + median);
            body.close();
            assertThat(median).isLessThanOrEqualTo(BUDGET_MILLISECONDS);
        }
    }

    /** Adds a short wire below the body's drawing and waits for the parent's snapshot after it; milliseconds. */
    private static long timeOneEdit(OpenDocument openDocument, OpenDocument body, int run) throws Exception {
        int below = ElementBounds.union(body.document().elements(), body.registry()).map(Bounds::maximumY)
                .orElse(0) + 10 + 4 * run;
        Wire wire = new Wire(body.document().newWireId(), new GridPoint(0, below), new GridPoint(3, below));
        long start = System.nanoTime();
        body.execute(new AddWireCommand(wire));
        openDocument.simulationPublished().get(30, TimeUnit.SECONDS);
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
