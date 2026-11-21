package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Avanzar N t» with a large N (SIM-3): the step runs in slices like a running simulation, so Pausar, Reiniciar,
 * edits and Cerrar are served while it goes on, and a small step publishes the same result as before.
 */
class SimulationThreadLongStepTest {

    /** A clock that toggles every unit: one event per simulated unit, the slowest kind of time to simulate. */
    private static final long LONG_STEP = 2_000_000;
    /** «Avanzar N t» with N at the preference's maximum (AppPreferences.MAXIMUM_STEP_UNITS). */
    private static final long HUGE_STEP = 1_000_000_000;
    private static final long NANOS_PER_MILLISECOND = 1_000_000;
    /** More than one tick's budget of slices runs with the clock below, less than {@link #LONG_STEP}. */
    private static final long SEVERAL_TICKS_STEP = 1_000_000;
    /** The AI's event budget for a step request: plenty for one event of the clock below. */
    private static final long AI_EVENT_BUDGET = 1_000;

    /** Every reading of this clock moves it one millisecond on, so a tick reaches its budget after a few slices. */
    private final AtomicLong nanos = new AtomicLong();
    private final SimulationThread thread = new SimulationThread(new SimulationEngine(),
            () -> nanos.addAndGet(NANOS_PER_MILLISECOND));

    /** Clock k (1 / 1) on a LED: net n changes at every unit. */
    private static FlatCircuit fastClock() {
        ComponentTypeRegistry standard = StandardComponentTypes.registry();
        return new FlatCircuit(List.of(
                new FlatComponent("k", standard.require(StandardComponentTypes.CLOCK),
                        Map.of("time-up", 1, "time-down", 1)),
                new FlatComponent("led", standard.require(StandardComponentTypes.LED), Map.of())),
                List.of(new FlatNet("n", List.of(new FlatPinRef("k", 0), new FlatPinRef("led", 0)), 1, false)));
    }

    @Test
    void aLongStepRunsInSlicesAndPublishesItsTimeAsItGoes() {
        thread.update(fastClock());
        thread.tick();

        thread.step(LONG_STEP);
        thread.tick();

        long reached = thread.latestSnapshot().time();
        assertThat(reached).as("one tick runs one budget of slices, not the whole step").isBetween(1L, LONG_STEP - 1);
        assertThat(thread.latestSnapshot().running()).as("shown as running, so «Pausar» is at hand").isTrue();
        assertThat(thread.latestStep()).as("published once the step ends").isEmpty();

        thread.tick();
        assertThat(thread.latestSnapshot().time()).as("the next tick goes on").isGreaterThan(reached);
    }

    @Test
    void pausingEndsAStepWhereItGotAndPublishesWhatItDid() {
        thread.update(fastClock());
        thread.tick();
        thread.step(LONG_STEP);
        thread.tick();

        thread.pause();
        thread.tick();

        long reached = thread.latestSnapshot().time();
        assertThat(reached).isBetween(1L, LONG_STEP - 1);
        StepResult published = thread.latestStep().orElseThrow().result();
        assertThat(published.fromTime()).isZero();
        assertThat(published.time()).isEqualTo(reached);
        assertThat(published.changedNets()).containsExactly("n");

        thread.tick();
        assertThat(thread.latestSnapshot().time()).as("stopped").isEqualTo(reached);
        assertThat(thread.latestSnapshot().running()).isFalse();
    }

    /** Review I3: the snapshots say «running» during the step, so the AI's steps are refused as while running. */
    @Test
    void theAisStepsDuringAStepInProgressAreRefusedAndTheStepGoesOn() {
        thread.update(fastClock());
        thread.tick();
        thread.step(LONG_STEP);
        thread.tick();
        long reached = thread.latestSnapshot().time();

        CompletableFuture<RequestReport> event = thread.advanceToNextEventWithin(1, AI_EVENT_BUDGET);
        CompletableFuture<RequestReport> edge = thread.advanceToNextClockEdgeWithin(ClockEdgeMode.ANY, 1,
                AI_EVENT_BUDGET);
        CompletableFuture<RequestReport> units = thread.stepWithin(1, AI_EVENT_BUDGET);
        thread.tick();

        assertThat(List.of(event.join().status(), edge.join().status(), units.join().status()))
                .containsOnly(RequestReport.Status.REFUSED_WHILE_RUNNING);
        assertThat(thread.latestStep()).as("the user's step was not ended").isEmpty();
        assertThat(thread.latestSnapshot().running()).isTrue();
        assertThat(thread.latestSnapshot().time()).as("it goes on").isGreaterThan(reached);
    }

    @Test
    void restartingDropsAStepInProgress() {
        thread.update(fastClock());
        thread.tick();
        thread.step(LONG_STEP);
        thread.tick();

        thread.restart(fastClock(), Map.of());
        thread.tick();
        thread.tick();

        assertThat(thread.latestSnapshot().time()).isZero();
        assertThat(thread.latestStep()).isEmpty();
    }

    @Test
    void aStepOverSeveralTicksGivesTheSameResultAsOneCall() {
        SimulationEngine twin = new SimulationEngine();
        twin.update(fastClock());
        StepResult expected = twin.advanceBy(SEVERAL_TICKS_STEP);
        thread.update(fastClock());
        thread.tick();

        thread.step(SEVERAL_TICKS_STEP);
        CompletableFuture<SimulationSnapshot> published = thread.published();
        int ticks = 0;
        while (!published.isDone() && ticks < 1_000) {
            thread.tick();
            ticks++;
        }

        assertThat(ticks).as("it took more than one tick").isGreaterThan(1);
        assertThat(published.join().time()).isEqualTo(SEVERAL_TICKS_STEP);
        assertThat(published.join().running()).as("paused again once the step ends").isFalse();
        assertThat(thread.latestStep().orElseThrow().result()).isEqualTo(expected);
    }

    /**
     * On a real thread with the real clock: with the clock above every slice would seem to take one millisecond, so
     * the slices would double up to a billion units (minutes of work) and close() could come too late to stop the
     * thread in time (review M4).
     */
    @Test
    void aHugeStepServesRequestsAndCloseStopsTheThread() throws Exception {
        SimulationThread realTime = new SimulationThread(new SimulationEngine());
        realTime.update(fastClock());
        realTime.start();
        realTime.published().get(30, TimeUnit.SECONDS);

        realTime.step(HUGE_STEP);
        RequestReport during = realTime.report().get(30, TimeUnit.SECONDS);
        realTime.close();

        assertThat(during.snapshot().time()).isBetween(0L, HUGE_STEP - 1);
        assertThat(realTime.isAlive()).as("the step does not keep the thread busy after close()").isFalse();
    }
}
