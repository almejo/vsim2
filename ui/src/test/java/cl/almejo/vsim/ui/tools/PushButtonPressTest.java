package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The push button in the Select tool: a press drives 1 and its release 0, wherever the release happens; it is let go
 * on Esc, focus loss, the tool going away and the simulation stopping. Never an edit, never a selection or a move.
 */
class PushButtonPressTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private Component button;

    @BeforeEach
    void placeTheButton() {
        button = place(context.openDocument(), StandardComponentTypes.PUSH_BUTTON, 0, 0, Map.of());
        context.openDocument().resume();
        context.openDocument().history().markSaved();
        expectValue(Value.ZERO);
    }

    @AfterEach
    void close() {
        context.close();
    }

    private void expectValue(Value value) {
        waitUntil(() -> new VisibleState.SwitchState(value)
                .equals(context.openDocument().latestSnapshot().componentState(button.id().value())));
    }

    private void press() {
        tool.mousePressed(context, TestToolContext.at(1, 1));
        awaitSimulation(context.openDocument());
    }

    @Test
    void aPressGivesOneAndTheReleaseZero() {
        press();
        expectValue(Value.ONE);

        tool.mouseReleased(context, TestToolContext.at(1, 1));
        expectValue(Value.ZERO);
    }

    @Test
    void aReleaseFarFromTheButtonLetsItGoToo() {
        press();
        tool.mouseDragged(context, TestToolContext.at(30, 20));
        expectValue(Value.ONE);

        tool.mouseReleased(context, TestToolContext.at(30, 20));

        expectValue(Value.ZERO);
        assertThat(context.openDocument().document().component(button.id()).orElseThrow().position())
                .as("the press never moves it").isEqualTo(button.position());
    }

    @Test
    void escapeLetsItGo() {
        press();
        expectValue(Value.ONE);

        tool.keyPressed(context, KeyEvent.VK_ESCAPE, false);

        expectValue(Value.ZERO);
    }

    @Test
    void losingTheFocusLetsItGo() {
        press();
        expectValue(Value.ONE);

        tool.focusLost(context);

        expectValue(Value.ZERO);
    }

    @Test
    void theToolGoingAwayLetsItGo() {
        press();
        expectValue(Value.ONE);

        tool.deactivated(context);

        expectValue(Value.ZERO);
    }

    @Test
    void pausingOrRestartingTheSimulationLetsItGo() {
        press();
        expectValue(Value.ONE);
        context.openDocument().pause();
        expectValue(Value.ZERO);
        tool.mouseReleased(context, TestToolContext.at(1, 1));

        press();
        expectValue(Value.ONE);
        context.openDocument().restart();
        expectValue(Value.ZERO);
    }

    @Test
    void enteringStepModeLetsItGo() {
        press();
        expectValue(Value.ONE);
        context.openDocument().setStepMode(true);
        expectValue(Value.ZERO);
    }

    @Test
    void theAisRestartLetsItGo() {
        press();
        expectValue(Value.ONE);
        context.openDocument().restartWithin(1_000);
        expectValue(Value.ZERO);
    }

    @Test
    void aPressIsNoEditAndSelectsNothing() {
        Component other = place(context.openDocument(), StandardComponentTypes.LED, 20, 0, Map.of());
        context.selection().select(other.id());
        context.openDocument().history().markSaved();
        long changesBefore = context.openDocument().changeCount();

        press();
        tool.mouseReleased(context, TestToolContext.at(1, 1));
        awaitSimulation(context.openDocument());

        assertThat(context.selection().ids()).containsExactly(other.id());
        assertThat(context.openDocument().isModified()).isFalse();
        assertThat(context.openDocument().changeCount()).as("no undo step").isEqualTo(changesBefore);
        assertThat(context.openDocument().document().component(button.id()).orElseThrow().parameters()).isEmpty();
    }

    @Test
    void aFileSavedWhileItIsHeldStoresNoValueForIt() {
        String released = context.openDocument().serialized();
        press();
        expectValue(Value.ONE);

        assertThat(context.openDocument().serialized()).isEqualTo(released).doesNotContain("\"value\"");
    }

    @Test
    void quickPressesAreEachAPressNotADoubleClick() {
        press();
        tool.mouseReleased(context, TestToolContext.at(1, 1));
        tool.mousePressed(context, TestToolContext.event(1, 1, CanvasMouseEvent.PRIMARY, 2, false));
        awaitSimulation(context.openDocument());
        expectValue(Value.ONE);

        tool.mouseReleased(context, TestToolContext.event(1, 1, CanvasMouseEvent.PRIMARY, 2, false));

        expectValue(Value.ZERO);
        assertThat(context.configured).as("no properties opened").isNull();
    }

    @Test
    void aShiftClickSelectsItInsteadOfPressingIt() {
        context.clickWith(tool, 1, 1, true, false);
        awaitSimulation(context.openDocument());

        assertThat(context.selection().ids()).containsExactly(button.id());
        assertThat(context.openDocument().latestSnapshot().componentState(button.id().value()))
                .isEqualTo(new VisibleState.SwitchState(Value.ZERO));
    }

    @Test
    void theButtonShowsTheHandCursor() {
        tool.mouseMoved(context, TestToolContext.at(1, 1));

        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.HAND);
    }
}
