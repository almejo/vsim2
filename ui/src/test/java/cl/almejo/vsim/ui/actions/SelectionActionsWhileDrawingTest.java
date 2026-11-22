package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.CanvasClicks;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.WireMode;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionActionsWhileDrawingTest {

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, UiTestSupport.NO_WINDOW);
    }

    @AfterEach
    void dispose() {
        view.dispose();
    }

    @Test
    void cutCopyPasteAndDuplicateAreOffWithAReasonWhileAContinuousWireIsDrawn() {
        view.services().preferences().setWireMode(WireMode.CONTINUOUS);
        CircuitCanvas canvas = view.canvas();
        canvas.setSize(800, 300);
        canvas.applyViewState(new ViewState(1, canvas.getWidth() / 16.0, canvas.getHeight() / 16.0));
        UiTestSupport.pinAt(openDocument, 5, 5);
        Component gate = UiTestSupport.place(openDocument, StandardComponentTypes.BUFFER, 20, 20,
                Map.of(StandardComponentTypes.NEGATE, true));
        view.selection().select(gate.id());
        SelectionActions actions = view.selectionActions();
        assertThat(actions.cut().isEnabled()).isTrue();
        assertThat(actions.copy().isEnabled()).isTrue();
        assertThat(actions.duplicate().isEnabled()).isTrue();

        CanvasClicks.click(canvas, 40, 40);
        assertThat(canvas.drawsBetweenClicks()).isTrue();
        for (javax.swing.Action action : new javax.swing.Action[]{actions.cut(), actions.copy(), actions.paste(),
                actions.duplicate()}) {
            assertThat(action.isEnabled()).isFalse();
        }
        assertThat(Actions.disabledReason(actions.cut())).contains(Messages.text("reason.finishTheWire"));

        canvas.interruptGesture();
        assertThat(actions.cut().isEnabled()).isTrue();
        assertThat(actions.copy().isEnabled()).isTrue();
        assertThat(actions.duplicate().isEnabled()).isTrue();
    }
}
