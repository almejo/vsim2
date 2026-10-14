package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WireTidyCancellationTest {

    private final CircuitDocument document = new CircuitDocument();

    private Set<ElementId> messyDocument() {
        Set<ElementId> ids = new HashSet<>();
        for (int index = 0; index < 20; index++) {
            int y = index * 3;
            for (int segment = 0; segment < 4; segment++) {
                ElementId id = document.newWireId();
                document.add(new Wire(id, new GridPoint(segment * 2, y), new GridPoint(segment * 2 + 2, y)));
                ids.add(id);
            }
        }
        return ids;
    }

    @Test
    void cancellingBeforeStartingLeavesNoPlan() {
        Set<ElementId> scope = messyDocument();
        Optional<TidyPlan> plan = WireTidy.plan(document, TestComponentTypes.registry(), scope,
                TidyOptions.DEFAULTS, () -> true, round -> {
                });
        assertThat(plan).isEmpty();
    }

    @Test
    void cancellingInTheMiddleStopsEarlyAndLeavesNoPlan() {
        Set<ElementId> scope = messyDocument();
        AtomicInteger polls = new AtomicInteger();
        Optional<TidyPlan> plan = WireTidy.plan(document, TestComponentTypes.registry(), scope,
                TidyOptions.DEFAULTS, () -> polls.incrementAndGet() > 5, round -> {
                });
        assertThat(plan).isEmpty();
        assertThat(polls.get()).isLessThan(20);
    }

    @Test
    void progressReportsEachRoundStartingAtOne() {
        Set<ElementId> scope = messyDocument();
        List<Integer> rounds = new ArrayList<>();
        Optional<TidyPlan> plan = WireTidy.plan(document, TestComponentTypes.registry(), scope,
                TidyOptions.DEFAULTS, () -> false, rounds::add);
        assertThat(plan).isPresent();
        assertThat(plan.orElseThrow().isEmpty()).isFalse();
        assertThat(rounds).startsWith(1, 2).isSorted();
    }

    @Test
    void withoutCancellationItMatchesThePlainVariant() {
        Set<ElementId> scope = messyDocument();
        TidyPlan plain = WireTidy.plan(document, TestComponentTypes.registry(), scope, TidyOptions.DEFAULTS);
        Optional<TidyPlan> cancellable = WireTidy.plan(document, TestComponentTypes.registry(), scope,
                TidyOptions.DEFAULTS, () -> false, round -> {
                });
        assertThat(cancellable).contains(plain);
    }
}
