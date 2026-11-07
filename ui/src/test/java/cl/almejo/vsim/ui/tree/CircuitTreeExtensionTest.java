package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Container;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The extension puts the circuit tree above the palette, not in the inspector, and takes it back on dispose. */
class CircuitTreeExtensionTest {

    private static Optional<CircuitTree> findTree(Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof CircuitTree tree) {
                return Optional.of(tree);
            }
            if (child instanceof Container nested) {
                Optional<CircuitTree> found = findTree(nested);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    @Test
    void theTreeGoesAboveThePaletteAndTheInspectorHasNoComponentsTab() {
        try (OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            EditorServices services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                    .withExtensions(List.of(new CircuitTreeExtension()));
            EditorView view = new EditorView(openDocument, services, NO_WINDOW);
            try {
                view.setSize(900, 600);
                layOut(view);
                CircuitTree tree = findTree(view).orElseThrow();

                assertThat(findTree(view.inspector())).isEmpty();
                assertThat(SwingUtilities.convertPoint(tree, 0, 0, view).y)
                        .isLessThan(SwingUtilities.convertPoint(view.palette(), 0, 0, view).y);
                assertThat(view.leftColumn().title(cl.almejo.vsim.ui.window.LeftColumn.Section.CIRCUIT))
                        .contains(Messages.text("tree.title"));
                Component gate = place(openDocument, StandardComponentTypes.BUFFER, 0, 0, Map.of());
                assertThat(tree.rowText(gate.id().value())).contains("Buffer");

                view.dispose();
                place(openDocument, StandardComponentTypes.BUFFER, 10, 0, Map.of());
                assertThat(tree.rowText("c2")).isEmpty();
                assertThat(findTree(view)).isEmpty();
            } finally {
                view.dispose();
            }
        }
    }
}
