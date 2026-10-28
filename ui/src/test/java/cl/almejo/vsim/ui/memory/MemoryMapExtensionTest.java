package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.simulation.MemoryMapRequest;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.Test;

import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class MemoryMapExtensionTest {

    private static Optional<MemoryMapPanel> findPanel(Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof MemoryMapPanel panel) {
                return Optional.of(panel);
            }
            if (child instanceof Container nested) {
                Optional<MemoryMapPanel> found = findPanel(nested);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    @Test
    void theTabFollowsTheSelectionAndLetsGoWhenTheViewCloses() {
        try (OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            EditorServices plain = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT);
            EditorView plainView = new EditorView(openDocument, plain, NO_WINDOW);
            int documentListenersPlain = openDocument.changeListenerCount();
            int snapshotListenersPlain = plainView.canvas().snapshotListenerCount();
            plainView.dispose();
            int snapshotListenersPlainDisposed = plainView.canvas().snapshotListenerCount();

            EditorView view = new EditorView(openDocument, plain.withExtensions(List.of(new MemoryMapExtension(mapView -> new MemoryMapActions() { }))),
                    NO_WINDOW);
            try {
                view.inspector().showTab("Mapa de memoria");
                assertThat(view.inspector().selectedTab()).contains("Mapa de memoria");
                MemoryMapPanel panel = findPanel(view).orElseThrow();
                assertThat(openDocument.changeListenerCount()).isEqualTo(documentListenersPlain + 1);
                assertThat(view.canvas().snapshotListenerCount()).isEqualTo(snapshotListenersPlain + 1);

                Component ram = place(openDocument, StandardComponentTypes.RAM, 0, 0,
                        Map.of(MemoryTypes.WORDS, "64"));
                view.selection().select(ram.id());
                assertThat(panel.request()).contains(new MemoryMapRequest(ram.id().value(), 0, 1, 64));

                view.dispose();
                assertThat(openDocument.changeListenerCount()).isEqualTo(documentListenersPlain);
                assertThat(view.canvas().snapshotListenerCount()).isEqualTo(snapshotListenersPlainDisposed);
                assertThat(panel.request()).as("no more frames once closed").isEmpty();
            } finally {
                view.dispose();
            }
        }
    }

    /** A launcher that records what it was asked instead of showing the (modal) editor. */
    private static final class RecordingLauncher extends MemoryEditorLauncher {
        final List<String> calls = new ArrayList<>();

        RecordingLauncher() {
            super(null);
        }

        @Override
        public void open(EditorView view, ElementId componentId, OptionalLong address) {
            calls.add("open " + componentId + " " + address);
        }

        @Override
        public void dump(EditorView view, ElementId componentId) {
            calls.add("dump " + componentId);
        }
    }

    @Test
    void theLiveActionsOpenTheEditorAtTheClickedAddressAndDumpTheRam() {
        try (OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            RecordingLauncher launcher = new RecordingLauncher();
            EditorServices services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                    .withExtensions(List.of(new MemoryMapExtension(view -> MemoryMapActions.of(view, launcher))));
            EditorView view = new EditorView(openDocument, services, NO_WINDOW);
            try {
                MemoryMapPanel panel = findPanel(view).orElseThrow();
                Component ram = place(openDocument, StandardComponentTypes.RAM, 0, 0,
                        Map.of(MemoryTypes.WORDS, "64"));
                view.selection().select(ram.id());
                assertThat(panel.dumpButton().isEnabled()).isTrue();
                MemoryMapPanel.MapGrid grid = panel.grid();
                grid.setSize(640, 640);

                grid.dispatchEvent(new MouseEvent(grid, MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false,
                        MouseEvent.BUTTON1));
                panel.dumpButton().doClick();

                assertThat(launcher.calls).containsExactly("open " + ram.id() + " OptionalLong[0]",
                        "dump " + ram.id());
            } finally {
                view.dispose();
            }
        }
    }

    @Test
    void theMapPaintsWithTheColoursOfTheActiveScheme() {
        for (ColorScheme scheme : List.of(ColorScheme.LIGHT, ColorSchemes.DARK)) {
            assertThat(MemoryMapPanel.accessColor(scheme, VisibleState.MemoryAccess.READ))
                    .isEqualTo(scheme.memoryRead());
            assertThat(MemoryMapPanel.accessColor(scheme, VisibleState.MemoryAccess.WRITE))
                    .isEqualTo(scheme.memoryWrite());
        }
        assertThat(ColorScheme.LIGHT.memoryRead()).isNotEqualTo(ColorSchemes.DARK.memoryRead());
    }
}
