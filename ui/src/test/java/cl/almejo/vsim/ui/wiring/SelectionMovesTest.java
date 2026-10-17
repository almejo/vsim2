package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.command.MoveWithWiresCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The one helper every move goes through (M6.7): which command, the notice of new contacts and the one-time hint. */
class SelectionMovesTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> notices = new ArrayList<>();
    private final SelectionMoves moves = new SelectionMoves(preferences, notices::add);
    private Component gate;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        gate = place(openDocument, StandardComponentTypes.NOT, 10, 10, Map.of());
        addWire(14, 12, 30, 12);
        place(openDocument, StandardComponentTypes.LED, 29, 9, Map.of());
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private void addWire(int startX, int startY, int endX, int endY) {
        openDocument.execute(new AddWireCommand(new Wire(openDocument.document().newWireId(),
                new GridPoint(startX, startY), new GridPoint(endX, endY))));
    }

    @Test
    void followingMeansAMoveWithWiresAndNotFollowingTheMoveOfBefore() {
        assertThat(SelectionMoves.command(openDocument, Set.of(gate.id()), 0, 3, true))
                .isInstanceOf(MoveWithWiresCommand.class);
        assertThat(SelectionMoves.command(openDocument, Set.of(gate.id()), 0, 3, false))
                .isInstanceOf(MoveElementsCommand.class);
    }

    @Test
    void theContactNoticeNamesALabelledComponentByTypeAndLabel() {
        openDocument.execute(new SetLabelCommand(gate.id(), "U1"));
        addWire(10, 15, 10, 25);
        moves.move(openDocument, Set.of(gate.id()), 0, 8, true);
        assertThat(notices).containsExactly("El cable quedó tocando el pin «in» de «NOT U1»");
    }

    @Test
    void aContactBetweenWiresOnlySaysSo() {
        Wire lone = new Wire(openDocument.document().newWireId(), new GridPoint(40, 0), new GridPoint(40, 10));
        openDocument.execute(new AddWireCommand(lone));
        addWire(35, 20, 45, 20);
        moves.move(openDocument, Set.of(lone.id()), 0, 10, true);
        assertThat(notices).containsExactly("El cable quedó tocando otro cable");
    }

    @Test
    void theHintNamesTheConfiguredKeyAndComesOncePerSession() {
        preferences.setInvertModifier(AppPreferences.InvertModifier.ALT);
        moves.move(openDocument, Set.of(gate.id()), 0, 2, true);
        new SelectionMoves(preferences, notices::add).move(openDocument, Set.of(gate.id()), 0, 2, true);
        assertThat(notices).containsExactly("Los cables siguieron al mover. Mantén Alt para desconectar");
    }

    @Test
    void withoutAnInvertKeyTheHintPointsAtTheToggle() {
        preferences.setInvertModifier(AppPreferences.InvertModifier.NONE);
        moves.move(openDocument, Set.of(gate.id()), 0, 2, true);
        assertThat(notices).containsExactly(
                "Los cables siguieron al mover. Desactiva «Los cables siguen al mover» para desconectar");
    }

    @Test
    void noHintWhenTheKeyMadeTheWiresFollowOrWhenNothingFollowed() {
        preferences.setWiresFollow(false);
        moves.move(openDocument, Set.of(gate.id()), 0, 2, true);
        moves.move(openDocument, Set.of(gate.id()), 0, 2, false);
        assertThat(notices).isEmpty();
    }

    @Test
    void aNudgeFollowsTheModeAndReportsTheMergedRun() {
        addWire(10, 15, 10, 25);
        for (int step = 0; step < 8; step++) {
            moves.nudge(openDocument, Set.of(gate.id()), 0, 1);
        }
        assertThat(notices).last().isEqualTo("El cable quedó tocando el pin «in» de «NOT»");
        assertThat(openDocument.history().undoDescription()).contains("command.move");
        openDocument.history().undo();
        assertThat(openDocument.history().undoDescription()).contains("command.wire");
    }

    @Test
    void aNudgeRunThatComesBackReportsNothingAboutTheStepBelowIt() {
        addWire(10, 15, 10, 25);
        moves.move(openDocument, Set.of(gate.id()), 0, 8, true);
        assertThat(notices).containsExactly("El cable quedó tocando el pin «in» de «NOT»");
        moves.nudge(openDocument, Set.of(gate.id()), 1, 0);
        notices.clear();

        moves.nudge(openDocument, Set.of(gate.id()), -1, 0);

        assertThat(notices).isEmpty();
        assertThat(openDocument.history().undoCommand()).get().isInstanceOf(MoveWithWiresCommand.class);
    }

    @Test
    void selectedWiresMergedIntoOneSurvivorBecomeItOnceInTheirPlace() {
        Wire earlier = new Wire(openDocument.document().newWireId(), new GridPoint(40, 20), new GridPoint(44, 20));
        Wire middle = new Wire(openDocument.document().newWireId(), new GridPoint(44, 23), new GridPoint(47, 23));
        Wire last = new Wire(openDocument.document().newWireId(), new GridPoint(47, 23), new GridPoint(50, 23));
        List.of(earlier, middle, last).forEach(wire -> openDocument.execute(new AddWireCommand(wire)));
        Selection selection = new Selection();
        selection.set(List.of(last.id(), gate.id(), middle.id()));

        moves.move(openDocument, selection, List.of(middle.id(), last.id()), 0, -3, true);

        assertThat(openDocument.document().wire(earlier.id())).contains(
                new Wire(earlier.id(), new GridPoint(40, 20), new GridPoint(50, 20)));
        assertThat(selection.ids()).containsExactly(earlier.id(), gate.id());
    }

    @Test
    void aMoveWithoutMergesLeavesTheSelectionAlone() {
        Selection selection = new Selection();
        selection.select(gate.id());
        List<Integer> changes = new ArrayList<>();
        selection.addListener(() -> changes.add(1));
        moves.move(openDocument, selection, Set.of(gate.id()), 0, 2, true);
        assertThat(selection.ids()).containsExactly(gate.id());
        assertThat(changes).isEmpty();
    }
}
