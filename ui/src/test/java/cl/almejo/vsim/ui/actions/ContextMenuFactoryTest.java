package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ContextMenuFactoryTest {

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, new WindowCommands() {
            @Override public void newDocument() { }
            @Override public void open() { }
            @Override public void save() { }
            @Override public void saveAs() { }
            @Override public void closeWindow() { }
            @Override public void exit() { }
        });
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private static List<String> texts(JPopupMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    @Test
    void componentMenuOffersConfigureRotateAndDelete() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        view.canvas().selection().select(gate.id());
        assertThat(texts(ContextMenuFactory.create(Optional.of(gate), view.actions())))
                .containsExactly("Configurar…", "Rotar ↻", "Rotar ↺", "---", "Borrar");
    }

    @Test
    void wireMenuOffersDeleteWire() {
        Wire wire = new Wire(new ElementId("w9"), GridPoint.ORIGIN, new GridPoint(3, 0));
        assertThat(texts(ContextMenuFactory.create(Optional.of(wire), view.actions()))).containsExactly("Borrar cable");
    }

    @Test
    void emptyCanvasMenuOffersCenterView() {
        assertThat(texts(ContextMenuFactory.create(Optional.empty(), view.actions()))).containsExactly("Centrar circuito");
    }
}
