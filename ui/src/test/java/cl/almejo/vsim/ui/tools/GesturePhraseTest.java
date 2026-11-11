package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The line the status bar shows while a gesture is under way: what each tool says, and when it says nothing. */
class GesturePhraseTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool select = (SelectTool) context.defaultTool;
    private Component buffer;
    private GridPoint outputPin;

    @BeforeEach
    void placeABuffer() {
        Messages.initialize(Messages.SPANISH);
        buffer = place(context.openDocument(), StandardComponentTypes.BUFFER, 10, 10,
                Map.of(StandardComponentTypes.NEGATE, true));
        outputPin = context.openDocument().registry().typeOf(buffer).pinLocation(buffer, 1);
        context.openDocument().history().clear();
    }

    @AfterEach
    void close() {
        context.close();
    }

    private String phrase(Tool tool) {
        return tool.gesturePhrase(context);
    }

    private void press(double gridX, double gridY) {
        select.mousePressed(context, TestToolContext.at(gridX, gridY));
    }

    private void dragTo(double gridX, double gridY) {
        select.mouseDragged(context, TestToolContext.withKeys(gridX, gridY, false, false, false));
    }

    private void releaseAt(double gridX, double gridY) {
        select.mouseReleased(context, TestToolContext.at(gridX, gridY));
    }

    private void startMovingTheBuffer() {
        context.selection.select(buffer.id());
        press(11, 11);
        dragTo(15, 11);
    }

    @Test
    void aContinuousWireSaysItsKeysWhileThePathIsUnderWay() {
        press(outputPin.x(), outputPin.y());
        releaseAt(outputPin.x(), outputPin.y());

        assertThat(phrase(select)).startsWith("Dibujando un cable:")
                .contains("Esc: cancela", "Retroceso: quita la última esquina", "Espacio: cambia el codo");
    }

    @Test
    void theContinuousWirePhraseGoesWhenEscapeCancelsThePath() {
        press(outputPin.x(), outputPin.y());
        releaseAt(outputPin.x(), outputPin.y());
        select.keyPressed(context, KeyEvent.VK_ESCAPE, false);

        assertThat(phrase(select)).isEmpty();
    }

    @Test
    void aPiecewiseWireDragSaysEscAndSpaceButNotBackspace() {
        context.preferences.setWireMode(WireMode.PIECES);
        press(outputPin.x(), outputPin.y());
        dragTo(outputPin.x() + 5, outputPin.y() + 3);

        assertThat(phrase(select)).startsWith("Dibujando un cable:").contains("Esc: cancela", "Espacio: cambia el codo")
                .doesNotContain("Retroceso");
        releaseAt(outputPin.x() + 5, outputPin.y() + 3);
        assertThat(phrase(select)).isEmpty();
    }

    @Test
    void movingStartsAtTheFirstDragNotAtThePress() {
        context.selection.select(buffer.id());
        press(11, 11);
        assertThat(phrase(select)).isEmpty();

        dragTo(15, 11);
        assertThat(phrase(select)).startsWith("Moviendo:").contains("Ctrl al soltar: copia", "Esc: cancela");
    }

    @Test
    void movingNamesTheConfiguredInvertKey() {
        startMovingTheBuffer();
        assertThat(phrase(select)).contains("Mayús: los cables no siguen");

        context.preferences.setInvertModifier(AppPreferences.InvertModifier.ALT);
        assertThat(phrase(select)).contains("Alt: los cables no siguen").doesNotContain("Mayús");
    }

    @Test
    void movingSaysTheWiresFollowWhenTheInvertKeyDisconnectsThem() {
        context.preferences.setWiresFollow(false);
        startMovingTheBuffer();

        assertThat(phrase(select)).contains("Mayús: los cables siguen");
    }

    @Test
    void movingWithoutAnInvertKeyDoesNotMentionIt() {
        context.preferences.setInvertModifier(AppPreferences.InvertModifier.NONE);
        startMovingTheBuffer();

        assertThat(phrase(select)).startsWith("Moviendo:").doesNotContain("cables");
    }

    @Test
    void theMovingPhraseGoesOnReleaseAndOnEscape() {
        startMovingTheBuffer();
        releaseAt(15, 11);
        assertThat(phrase(select)).isEmpty();

        context.selection.select(buffer.id());
        press(16, 11);
        dragTo(20, 11);
        select.keyPressed(context, KeyEvent.VK_ESCAPE, false);
        assertThat(phrase(select)).isEmpty();
    }

    @Test
    void hoveringSaysNothing() {
        select.mouseMoved(context, TestToolContext.at(outputPin.x(), outputPin.y()));
        select.mouseMoved(context, TestToolContext.at(11, 11));

        assertThat(phrase(select)).isEmpty();
    }

    @Test
    void pastingSaysEnterAndEscapeUntilItEnds() {
        CircuitFragment fragment = new CircuitFragment(List.of(context.openDocument().registry()
                .require(StandardComponentTypes.BUFFER)
                .newComponent(new ElementId("c9"), GridPoint.ORIGIN, Rotation.R0, Map.of(), "")), List.of(), List.of());
        FloatingSelectionTool pasting = new FloatingSelectionTool(fragment, new GridPoint(3, 3));
        context.setTool(pasting);

        assertThat(phrase(pasting)).startsWith("Pegando:").contains("Enter", "Esc: cancela");
        pasting.keyPressed(context, KeyEvent.VK_ESCAPE, false);
        assertThat(phrase(pasting)).isEmpty();
    }

    @Test
    void placingAPartSaysItsNameAndTheRotationKeys() {
        ComponentType clock = context.openDocument().registry().require(StandardComponentTypes.CLOCK);
        PlaceComponentTool placing = new PlaceComponentTool(clock);

        assertThat(phrase(placing)).startsWith("Colocando " + Messages.componentName(clock) + ":")
                .contains("R / Mayús+R: gira", "Mayús+clic: sigue colocando", "Esc: cancela");
    }

    @Test
    void placingAPartNamesTheRotationKeysTheUserChose() {
        ComponentType clock = context.openDocument().registry().require(StandardComponentTypes.CLOCK);
        context.keyBindings.assign("edit.rotateClockwise", javax.swing.KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_DOWN_MASK));

        assertThat(phrase(new PlaceComponentTool(clock))).contains("Ctrl+R / Mayús+R: gira");
    }

    @Test
    void movingASingleSelectedWireAlsoSaysTheKeyThatSlidesIt() {
        Wire wire = new Wire(context.openDocument().document().newWireId(), new GridPoint(30, 0),
                new GridPoint(40, 0));
        context.openDocument().execute(new AddWireCommand(wire));
        context.selection.select(wire.id());
        press(35, 0);
        dragTo(35, 4);

        assertThat(phrase(select)).startsWith("Moviendo:").contains("Alt: desliza el cable");
        releaseAt(35, 4);

        startMovingTheBuffer();
        assertThat(phrase(select)).doesNotContain("desliza el cable");
    }
}
