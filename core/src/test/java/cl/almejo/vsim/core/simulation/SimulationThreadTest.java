package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.BehaviorFactory;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationThreadTest {

    private static final long MILLISECOND = 1_000_000L;

    private final ComponentTypeRegistry registry = TestComponentTypes.registry();
    private final AtomicLong nanos = new AtomicLong();
    private final SimulationThread thread = new SimulationThread(new SimulationEngine(), nanos::get);

    private FlatCircuit singleSource() {
        ComponentType source = registry.require(TestComponentTypes.SOURCE);
        return new FlatCircuit(List.of(new FlatComponent("s", source, Map.of("value", "0"))),
                List.of(new FlatNet("out", List.of(new FlatPinRef("s", 0)), 1, false)));
    }

    private FlatCircuit inverterLoop() {
        ComponentType inverter = registry.require(TestComponentTypes.INVERTER);
        return new FlatCircuit(List.of(new FlatComponent("i", inverter, Map.of("delay", 1))),
                List.of(new FlatNet("loop", List.of(new FlatPinRef("i", 0), new FlatPinRef("i", 1)), 1, false)));
    }

    @Test
    void startsPausedWithAnEmptySnapshot() {
        thread.tick();
        assertThat(thread.latestSnapshot().running()).isFalse();
        assertThat(thread.latestSnapshot().time()).isZero();
    }

    @Test
    void runningAdvancesWithTheClockAndSpeed() {
        thread.update(FlatCircuit.EMPTY);
        thread.resume();
        thread.tick();
        nanos.addAndGet(5 * MILLISECOND);
        thread.tick();
        assertThat(thread.latestSnapshot().time()).isEqualTo(5);
        assertThat(thread.latestSnapshot().running()).isTrue();

        thread.setSpeed(2.0);
        nanos.addAndGet(5 * MILLISECOND);
        thread.tick();
        assertThat(thread.latestSnapshot().time()).isEqualTo(15);
    }

    @Test
    void fractionsOfAUnitAreCarriedOver() {
        thread.setSpeed(0.5);
        thread.resume();
        thread.tick();
        nanos.addAndGet(MILLISECOND);
        thread.tick();
        nanos.addAndGet(MILLISECOND);
        thread.tick();
        assertThat(thread.latestSnapshot().time()).isEqualTo(1);
    }

    @Test
    void pauseStopsTimeAndStepAdvancesWhilePaused() {
        thread.resume();
        thread.tick();
        thread.pause();
        nanos.addAndGet(10 * MILLISECOND);
        thread.tick();
        assertThat(thread.latestSnapshot().time()).isZero();
        thread.step(3);
        thread.tick();
        assertThat(thread.latestSnapshot().time()).isEqualTo(3);
        assertThat(thread.latestSnapshot().running()).isFalse();
    }

    @Test
    void catchUpIsCappedPerTick() {
        thread.resume();
        thread.tick();
        nanos.addAndGet(10_000 * MILLISECOND);
        thread.tick();
        assertThat(thread.latestSnapshot().time()).isEqualTo(250);
    }

    @Test
    void updateAndInteractAreAppliedOnTick() {
        thread.update(singleSource());
        thread.tick();
        assertThat(thread.latestSnapshot().netValue("out")).isEqualTo(Value.ZERO);
        thread.interact("s");
        thread.tick();
        assertThat(thread.latestSnapshot().netValue("out")).isEqualTo(Value.ONE);
        assertThat(thread.latestSnapshot().time()).isZero();
    }

    @Test
    void stepIsIgnoredWhileRunning() {
        thread.resume();
        thread.tick();
        thread.step(100);
        thread.tick();
        assertThat(thread.latestSnapshot().time()).isZero();
    }

    @Test
    void engineFailurePausesAndTheThreadKeepsServing() {
        FlatCircuit broken = new FlatCircuit(List.of(),
                List.of(new FlatNet("bad", List.of(new FlatPinRef("missing", 0)), 1, false)));
        thread.update(broken);
        thread.resume();
        thread.tick();
        assertThat(thread.latestSnapshot().running()).isFalse();
        assertThat(thread.latestSnapshot().failure()).hasValueSatisfying(
                message -> assertThat(message).contains("missing"));
        thread.tick();
        assertThat(thread.latestSnapshot().failure()).isPresent();

        thread.update(FlatCircuit.EMPTY);
        thread.resume();
        thread.tick();
        assertThat(thread.latestSnapshot().failure()).isEmpty();
        nanos.addAndGet(5 * MILLISECOND);
        thread.tick();
        assertThat(thread.latestSnapshot().running()).isTrue();
        assertThat(thread.latestSnapshot().time()).isEqualTo(5);
    }

    @Test
    void speedMustBePositive() {
        assertThatThrownBy(() -> thread.setSpeed(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void realThreadKeepsServingPauseWhileAnOscillatorRuns() throws Exception {
        try (SimulationThread realThread = new SimulationThread(new SimulationEngine())) {
            realThread.start();
            realThread.update(inverterLoop());
            realThread.setSpeed(50.0);
            realThread.resume();
            waitUntil(() -> realThread.latestSnapshot().time() > 100);
            realThread.pause();
            waitUntil(() -> !realThread.latestSnapshot().running());
            long pausedAt = realThread.latestSnapshot().time();
            Thread.sleep(50);
            assertThat(realThread.latestSnapshot().time()).isEqualTo(pausedAt);
            realThread.close();
            assertThat(realThread.isAlive()).isFalse();
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000 * MILLISECOND;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 2 seconds");
            }
            Thread.sleep(5);
        }
    }

    private static FlatCircuit single(BehaviorFactory factory) {
        ComponentType type = new ComponentType("test-special", ComponentCategory.DEBUG, new GridSize(2, 2),
                List.of(), List.of(), factory);
        return new FlatCircuit(List.of(new FlatComponent("x", type, Map.of())), List.of());
    }

    @Test
    void anErrorIsPublishedAndStopsTheLoop() throws Exception {
        try (SimulationThread realThread = new SimulationThread(new SimulationEngine())) {
            realThread.start();
            realThread.update(single(parameters -> {
                throw new AssertionError("broken behavior");
            }));
            waitUntil(() -> realThread.latestSnapshot().failure().isPresent());
            assertThat(realThread.latestSnapshot().failure().orElseThrow()).contains("broken behavior");
            assertThat(realThread.latestSnapshot().running()).isFalse();
            waitUntil(() -> !realThread.isAlive());
        }
    }

    @Test
    void closeInterruptsAThreadThatDoesNotStopInTime() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch never = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        SimulationThread realThread = new SimulationThread(new SimulationEngine(), System::nanoTime, 50);
        realThread.start();
        realThread.update(single(parameters -> {
            entered.countDown();
            try {
                never.await();
            } catch (InterruptedException exception) {
                interrupted.set(true);
            }
            return (context, pinIndex) -> {
            };
        }));
        entered.await();
        realThread.close();
        waitUntil(interrupted::get);
        waitUntil(() -> !realThread.isAlive());
    }
}
