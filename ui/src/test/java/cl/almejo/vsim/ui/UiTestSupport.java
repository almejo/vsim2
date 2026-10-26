package cl.almejo.vsim.ui;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.window.WindowCommands;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

public final class UiTestSupport {

    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long SIMULATION_TIMEOUT_SECONDS = 30;

    /** A window that does nothing: for editors under test that never open, save or close anything. */
    public static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private UiTestSupport() {
    }

    public static void waitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TIMEOUT_NANOS;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 5 s");
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting", exception);
            }
        }
    }

    /**
     * Waits until every simulation request {@code document} made so far is applied and published, and returns that
     * snapshot; the step, next event time and pending components read afterwards are at least as new. Prefer it to
     * {@link #waitUntil} after a request: no polling for a state the simulation may move past, and no reading of the
     * fields written before the snapshot while it is still being built. The timeout only guards against a hang.
     */
    public static SimulationSnapshot awaitSimulation(OpenDocument document) {
        try {
            return document.simulationPublished().get(SIMULATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the simulation", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("the simulation did not publish within " + SIMULATION_TIMEOUT_SECONDS + " s",
                    exception);
        }
    }

    public static Component place(OpenDocument document, String typeId, int x, int y, Map<String, Object> parameters) {
        ComponentType type = document.registry().require(typeId);
        Component component = type.newComponent(document.document().newComponentId(), new GridPoint(x, y),
                Rotation.R0, parameters, "");
        document.execute(new AddElementsCommand(List.of(component)));
        return component;
    }
}
