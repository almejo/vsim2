package cl.almejo.vsim.ui;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.window.WindowCommands;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
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

    /** The text of each item of {@code menu}, {@code "---"} for a separator. */
    public static List<String> texts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getMenuComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    /** The text of each item of {@code menu}, {@code "---"} for a separator. */
    public static List<String> texts(JPopupMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    /** Runs {@code action} as if its button had been clicked. */
    public static void perform(Action action) {
        action.actionPerformed(new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, ""));
    }

    /** Runs {@code work} on the Swing thread and returns its result; its exception is rethrown here. */
    public static <T> T onEditor(Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(work.call());
            } catch (Exception exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    /** Presses the primary button on {@code canvas} at (20, 20), delivering the event to its mouse listeners. */
    public static void press(CircuitCanvas canvas) {
        MouseEvent event = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, 20, 20, 1, false,
                MouseEvent.BUTTON1);
        for (var listener : canvas.getMouseListeners()) {
            listener.mousePressed(event);
        }
    }

    /** Lays out {@code container} and, recursively, every container inside it. */
    public static void layOut(Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layOut(nested);
            }
        }
    }

    /** A free loopback port (probed, then released). */
    public static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        }
    }

    /** The WCAG relative luminance of {@code color}, from 0 (black) to 1 (white). */
    public static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen())
                + 0.0722 * channel(color.getBlue());
    }

    private static double channel(int value) {
        double scaled = value / 255.0;
        return scaled <= 0.03928 ? scaled / 12.92 : Math.pow((scaled + 0.055) / 1.055, 2.4);
    }

    /** Adds a wire from (startX, startY) to (endX, endY) straight to {@code document} and returns it. */
    public static Wire wire(CircuitDocument document, int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY));
        document.add(wire);
        return wire;
    }

    /** Like {@link #wire}, but returns the wire's id. */
    public static ElementId wireId(CircuitDocument document, int startX, int startY, int endX, int endY) {
        return wire(document, startX, startY, endX, endY).id();
    }

    /** Draws a wire from (startX, startY) to (endX, endY) through {@code document}'s undo history; returns it. */
    public static Wire addWire(OpenDocument document, int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(document.document().newWireId(), new GridPoint(startX, startY),
                new GridPoint(endX, endY));
        document.execute(new AddWireCommand(wire));
        return wire;
    }

    /** Adds an unlocked frame to {@code document} straight away and returns it. */
    public static Frame frame(CircuitDocument document, int x, int y, int width, int height, String title,
                              FrameTint tint) {
        Frame frame = new Frame(document.newFrameId(), new GridPoint(x, y), width, height, title, tint, false);
        document.add(frame);
        return frame;
    }
}
