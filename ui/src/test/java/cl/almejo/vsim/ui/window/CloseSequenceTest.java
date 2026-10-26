package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.actions.LabelActions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** M6.8: closing a window or the application finishes the open inline edits before asking to save. */
class CloseSequenceTest {

    private OpenDocument openDocument;
    private EditorView view;
    private Component gate;
    private final List<Boolean> modifiedWhenAsked = new ArrayList<>();

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of()),
                NO_WINDOW);
        gate = place(openDocument, StandardComponentTypes.NOT, 0, 10, Map.of());
        openDocument.history().markSaved();
        new LabelActions(openDocument, view.canvas().selection(), view.inlineEditor()).editLabel(gate);
        view.inlineEditor().field().setText("U7");
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private boolean askSave() {
        modifiedWhenAsked.add(openDocument.isModified());
        return true;
    }

    @Test
    void theLastWindowFinishesTheEditBeforeAskingToSave() {
        boolean closes = CloseSequence.confirm(List.of(view.inlineEditor()), this::askSave);

        assertThat(closes).isTrue();
        assertThat(modifiedWhenAsked).containsExactly(true);
        assertThat(view.inlineEditor().isOpen()).isFalse();
        assertThat(openDocument.document().component(gate.id()).orElseThrow().label()).isEqualTo("U7");
    }

    @Test
    void aCancelledQuestionKeepsTheWindowAndTheAppliedEdit() {
        boolean closes = CloseSequence.confirm(List.of(view.inlineEditor()), () -> false);

        assertThat(closes).isFalse();
        assertThat(openDocument.document().component(gate.id()).orElseThrow().label()).isEqualTo("U7");
    }
}
