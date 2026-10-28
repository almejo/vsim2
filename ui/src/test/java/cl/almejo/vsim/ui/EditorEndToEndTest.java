package cl.almejo.vsim.ui;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.tools.PlaceComponentTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import cl.almejo.vsim.ui.tools.WireTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

class EditorEndToEndTest {

    private static VisibleState ledState(OpenDocument document, Component led) {
        return document.latestSnapshot().componentState(led.id().value());
    }

    private static boolean synced(OpenDocument document) {
        return document.latestSnapshot().token() == document.netlistToken();
    }

    @Test
    void placeWireToggleUndoAndSaveASimpleCircuit(@TempDir Path directory) throws Exception {
        try (TestToolContext context = TestToolContext.withNewDocument()) {
            OpenDocument document = context.openDocument();

            new PlaceComponentTool(document.registry().require(StandardComponentTypes.SWITCH))
                    .mousePressed(context, TestToolContext.at(0, 0));
            new PlaceComponentTool(document.registry().require(StandardComponentTypes.LED))
                    .mousePressed(context, TestToolContext.at(10, 0));
            Component led = document.document().components().get(1);

            WireTool wire = new WireTool();
            wire.mousePressed(context, TestToolContext.at(4, 1));
            wire.mouseDragged(context, TestToolContext.at(11, 1));
            wire.mouseDragged(context, TestToolContext.at(11, 3));
            wire.mouseReleased(context, TestToolContext.at(11, 3));
            assertThat(document.document().wires()).hasSize(2);
            waitUntil(() -> synced(document) && ledState(document, led).equals(new VisibleState.LedState(Value.ONE)));

            context.click(new SelectTool(), 1, 1);
            waitUntil(() -> ledState(document, led).equals(new VisibleState.LedState(Value.ZERO)));

            document.history().undo(); // the click: the switch is back at 1 (2026-10-28)
            waitUntil(() -> ledState(document, led).equals(new VisibleState.LedState(Value.ONE)));
            document.history().undo();
            assertThat(document.document().wires()).isEmpty();
            waitUntil(() -> synced(document)
                    && ledState(document, led).equals(new VisibleState.LedState(Value.HIGH_IMPEDANCE)));

            document.history().redo();
            Path file = directory.resolve("interruptor-led.vsim");
            document.save(file);
            try (OpenDocument reopened = OpenDocument.open(file, document.registry())) {
                assertThat(reopened.document().components()).hasSize(2);
                assertThat(reopened.document().wires()).hasSize(2);
            }
        }
    }

    @Test
    void theOldFourBitAdderOpensAndSimulatesWithoutFailures() throws Exception {
        try (OpenDocument document = OpenDocument.open(Path.of("../core/src/test/resources/vsim1/4-bit-adder.json"),
                StandardComponentTypes.registry())) {
            waitUntil(() -> synced(document) && document.latestSnapshot().running());
            assertThat(document.latestSnapshot().failure()).isEmpty();
            assertThat(document.latestSnapshot().componentStates()).isNotEmpty();
            assertThat(document.latestSnapshot().shortCircuits()).isEmpty();
        }
    }

    @Test
    void wiringWithTheSelectionToolAlone() {
        try (TestToolContext context = TestToolContext.withNewDocument()) {
            OpenDocument document = context.openDocument();
            SelectTool select = new SelectTool();
            new PlaceComponentTool(document.registry().require(StandardComponentTypes.SWITCH))
                    .mousePressed(context, TestToolContext.at(0, 0));
            new PlaceComponentTool(document.registry().require(StandardComponentTypes.LED))
                    .mousePressed(context, TestToolContext.at(10, 0));
            Component led = document.document().components().get(1);
            select.mousePressed(context, TestToolContext.at(4, 1));
            select.mouseDragged(context, TestToolContext.at(11, 1.2));
            select.mouseDragged(context, TestToolContext.at(11, 3));
            select.mouseReleased(context, TestToolContext.at(11, 3));
            assertThat(document.document().wires()).hasSize(2);
            waitUntil(() -> synced(document) && ledState(document, led).equals(new VisibleState.LedState(Value.ONE)));
        }
    }
}
