package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** A part asks the running simulation to pause ({@link BehaviorContext#requestPause}), like a breakpoint. */
class PauseRequestTest {

    private static final long MILLISECOND = 1_000_000L;
    private static final String MESSAGE = "pause.test";

    /** Ticks every {@code period} units and asks for a pause at each multiple of {@code pauseEvery}. */
    private static final class Ticker implements Behavior {

        private final long period;
        private final long pauseEvery;
        int ticks;

        Ticker(long period, long pauseEvery) {
            this.period = period;
            this.pauseEvery = pauseEvery;
        }

        @Override
        public void initialize(BehaviorContext context) {
            context.schedule(period, () -> tick(context));
        }

        private void tick(BehaviorContext context) {
            ticks++;
            if (context.now() % pauseEvery == 0) {
                context.requestPause(MESSAGE, List.of("consola", "=" + context.now()));
            }
            context.schedule(period, () -> tick(context));
        }

        @Override
        public void inputChanged(BehaviorContext context, int pinIndex) {
        }
    }

    private final ComponentType tickerType = new ComponentType("ticker", ComponentCategory.INPUT_OUTPUT,
            new GridSize(2, 2), List.of(), List.of(ParameterSpec.integer("pause", 30, 1, 1000)),
            parameters -> new Ticker(10, (Integer) parameters.get("pause")));

    private FlatCircuit circuit(int pauseEvery) {
        return new FlatCircuit(List.of(new FlatComponent("t", tickerType, Map.of("pause", pauseEvery))), List.of());
    }

    @Test
    void theEngineStopsRightAfterTheTimeOfTheRequestAndHandsItOverOnce() {
        SimulationEngine engine = new SimulationEngine();
        engine.update(circuit(30));

        assertThat(engine.advanceUntilPause(1000)).isTrue();

        assertThat(engine.now()).isEqualTo(30);
        PauseNotice notice = engine.takePauseRequest().orElseThrow();
        assertThat(notice.componentId()).isEqualTo("t");
        assertThat(notice.messageKey()).isEqualTo(MESSAGE);
        assertThat(notice.arguments()).containsExactly("consola", "=30");
        assertThat(notice.time()).isEqualTo(30);
        assertThat(engine.takePauseRequest()).isEmpty();
    }

    @Test
    void aPlainAdvanceDoesNotStopButRemembersTheFirstRequest() {
        SimulationEngine engine = new SimulationEngine();
        engine.update(circuit(30));

        engine.advance(100);

        assertThat(engine.now()).isEqualTo(100);
        assertThat(engine.takePauseRequest()).map(PauseNotice::time).contains(30L);
    }

    @Test
    void withoutARequestTheEngineAdvancesTheWholeDuration() {
        SimulationEngine engine = new SimulationEngine();
        engine.update(circuit(1000));

        assertThat(engine.advanceUntilPause(95)).isFalse();

        assertThat(engine.now()).isEqualTo(95);
    }

    @Test
    void aRestartForgetsAPendingRequest() {
        SimulationEngine engine = new SimulationEngine();
        engine.update(circuit(30));
        engine.advance(40);

        engine.restart(circuit(30), Map.of());

        assertThat(engine.takePauseRequest()).isEmpty();
        assertThat(engine.advanceUntilPause(20)).isFalse();
        assertThat(engine.now()).isEqualTo(20);
    }

    @Test
    void aRunningThreadPausesPublishesTheNoticeOnceAndRunsAgainOnResume() {
        AtomicLong nanos = new AtomicLong();
        SimulationThread thread = new SimulationThread(new SimulationEngine(), nanos::get);
        thread.update(circuit(30));
        thread.resume();
        thread.tick();

        nanos.addAndGet(100 * MILLISECOND);
        thread.tick();

        assertThat(thread.latestSnapshot().running()).isFalse();
        assertThat(thread.latestSnapshot().time()).isEqualTo(30);
        PauseNotice first = thread.latestPauseNotice().orElseThrow();
        assertThat(first.time()).isEqualTo(30);
        assertThat(first.arguments()).containsExactly("consola", "=30");

        nanos.addAndGet(100 * MILLISECOND);
        thread.tick();
        assertThat(thread.latestSnapshot().time()).as("stays paused").isEqualTo(30);
        assertThat(thread.latestPauseNotice()).contains(first);

        thread.resume();
        thread.tick();
        nanos.addAndGet(20 * MILLISECOND);
        thread.tick();
        assertThat(thread.latestSnapshot().running()).isTrue();
        assertThat(thread.latestSnapshot().time()).isEqualTo(50);

        nanos.addAndGet(20 * MILLISECOND);
        thread.tick();
        assertThat(thread.latestSnapshot().running()).isFalse();
        assertThat(thread.latestSnapshot().time()).isEqualTo(60);
        PauseNotice second = thread.latestPauseNotice().orElseThrow();
        assertThat(second.time()).isEqualTo(60);
        assertThat(second.sequence()).isGreaterThan(first.sequence());
    }

    @Test
    void steppingWhilePausedPublishesANoticeAndStaysPaused() {
        AtomicLong nanos = new AtomicLong();
        SimulationThread thread = new SimulationThread(new SimulationEngine(), nanos::get);
        thread.update(circuit(30));
        thread.tick();

        thread.step(35);
        thread.tick();

        assertThat(thread.latestSnapshot().running()).isFalse();
        assertThat(thread.latestPauseNotice()).map(PauseNotice::time).contains(30L);
    }

    @Test
    void aRestartClearsTheNotice() {
        AtomicLong nanos = new AtomicLong();
        SimulationThread thread = new SimulationThread(new SimulationEngine(), nanos::get);
        thread.update(circuit(30));
        thread.resume();
        thread.tick();
        nanos.addAndGet(100 * MILLISECOND);
        thread.tick();
        assertThat(thread.latestPauseNotice()).isPresent();

        thread.restart(circuit(30), Map.of());
        thread.tick();

        assertThat(thread.latestPauseNotice()).isEmpty();
    }
}
