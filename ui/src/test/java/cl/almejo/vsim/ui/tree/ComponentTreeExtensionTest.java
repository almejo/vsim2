package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.Test;

import java.awt.Container;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ComponentTreeExtensionTest {

    private static Optional<ComponentTreePanel> findPanel(Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof ComponentTreePanel panel) {
                return Optional.of(panel);
            }
            if (child instanceof Container nested) {
                Optional<ComponentTreePanel> found = findPanel(nested);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    @Test
    void theExtensionAddsTheComponentsTabAndStopsListeningWhenTheViewCloses() {
        try (OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            EditorServices services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                    .withExtensions(List.of(new ComponentTreeExtension()));
            EditorView view = new EditorView(openDocument, services, NO_WINDOW);
            try {
                view.inspector().showTab("Componentes");
                assertThat(view.inspector().selectedTab()).contains("Componentes");
                ComponentTreePanel panel = findPanel(view).orElseThrow();
                Component first = place(openDocument, StandardComponentTypes.BUFFER, 0, 0, Map.of(StandardComponentTypes.NEGATE, true));
                assertThat(panel.rowText(first.id().value())).contains("NOT");

                view.dispose();
                Component second = place(openDocument, StandardComponentTypes.BUFFER, 10, 0, Map.of(StandardComponentTypes.NEGATE, true));
                assertThat(panel.rowText(second.id().value())).isEmpty();
            } finally {
                view.dispose();
            }
        }
    }

    @Test
    void disposingTheViewRemovesEveryListenerTheTabAdded() throws ReflectiveOperationException {
        try (OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            int documentListenersBefore = listenerCount(openDocument, "changeListeners");
            EditorServices withoutTree = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT);
            EditorView plainView = new EditorView(openDocument, withoutTree, NO_WINDOW);
            int documentListenersPlain = listenerCount(openDocument, "changeListeners");
            int canvasListenersPlain = listenerCount(plainView.canvas(), "snapshotListeners");
            plainView.dispose();
            int canvasListenersPlainDisposed = listenerCount(plainView.canvas(), "snapshotListeners");

            EditorServices services = withoutTree.withExtensions(List.of(new ComponentTreeExtension()));
            EditorView view = new EditorView(openDocument, services, NO_WINDOW);
            try {
                assertThat(listenerCount(openDocument, "changeListeners")).isEqualTo(documentListenersPlain + 1);
                assertThat(listenerCount(view.canvas(), "snapshotListeners")).isEqualTo(canvasListenersPlain + 1);
            } finally {
                view.dispose();
            }
            assertThat(listenerCount(openDocument, "changeListeners")).isEqualTo(documentListenersBefore);
            assertThat(listenerCount(view.canvas(), "snapshotListeners")).isEqualTo(canvasListenersPlainDisposed);
        }
    }

    private static int listenerCount(Object owner, String fieldName) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((Collection<?>) field.get(owner)).size();
    }
}
