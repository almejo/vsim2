package cl.almejo.vsim.ui;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class UiTestSupport {

    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

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

    public static Component place(OpenDocument document, String typeId, int x, int y, Map<String, Object> parameters) {
        ComponentType type = document.registry().require(typeId);
        Component component = type.newComponent(document.document().newComponentId(), new GridPoint(x, y),
                Rotation.R0, parameters, "");
        document.execute(new AddElementsCommand(List.of(component)));
        return component;
    }
}
