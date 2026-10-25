package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.DefinitionPin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SymbolDraftTest {
    private static final ElementId INPUT = new ElementId("input");
    private static final ElementId OUTPUT = new ElementId("output");

    @Test
    void restoringTheDefaultBoxKeepsTheEditedPinsAndIsDifferentFromABlankDrawing() {
        SymbolDraft changed = draft(false).movePin(INPUT, new GridPoint(0, 3))
                .applyTemplate(SymbolTemplates.Template.ALU);
        SymbolDraft restored = changed.applyTemplate(SymbolTemplates.Template.DEFAULT_BOX);
        assertThat(restored.appearance().drawing()).isEmpty();
        assertThat(restored.appearance().pins()).contains(new PinPlacement(INPUT, new GridPoint(0, 3)));
        assertThat(restored.addFigure(SymbolTemplates.rectangle(new SymbolPoint(0, 0), new SymbolPoint(16, 16), false))
                .deleteSelection().appearance().drawing()).isPresent();
    }

    @Test
    void deletingAndUndoingAMixedSelectionPreservesItsPaintOrder() {
        SymbolDraft original = draft(false)
                .addFigure(SymbolTemplates.rectangle(new SymbolPoint(8, 8), new SymbolPoint(24, 24), false))
                .addText(new SymbolText(new SymbolPoint(16, 16), Optional.of("A"), TextSize.SMALL, TextAlignment.LEFT));
        DraftHistory history = new DraftHistory(original);
        history.update(original.select(Set.of(0, 1)).sendToBack().deleteSelection());
        assertThat(history.current().elements()).isEmpty();
        history.undo();
        assertThat(history.current().elements()).containsExactlyElementsOf(original.elements());
        history.redo();
        assertThat(history.current().elements()).isEmpty();
    }

    @Test
    void coincidingPinsRemainEditableButCannotBeAccepted() {
        SymbolDraft invalid = draft(false).movePin(INPUT, new GridPoint(6, 2));
        assertThat(invalid.canAccept()).isFalse();
        assertThat(invalid.problems()).anyMatch(problem -> problem.kind() == DraftProblem.Kind.COINCIDING_PINS);
        assertThat(invalid.movePin(INPUT, new GridPoint(0, 2)).canAccept()).isTrue();
    }

    @Test
    void resizingTheBoxMovesRightEdgePinsWithoutScalingTheDrawing() {
        SymbolDraft original = draft(false).applyTemplate(SymbolTemplates.Template.SQUARE);
        SymbolDraft resized = original.resizeBox(new GridSize(8, 6));
        assertThat(resized.elements()).isEqualTo(original.elements());
        assertThat(resized.appearance().pins()).contains(new PinPlacement(OUTPUT, new GridPoint(8, 2)));
        assertThatThrownBy(() -> original.resizeBox(new GridSize(1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNestedDefinitionCanChangeItsDrawingButCannotMovePinsOrResize() {
        SymbolDraft locked = draft(true);
        assertThatThrownBy(() -> locked.movePin(INPUT, new GridPoint(0, 3)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> locked.resizeBox(new GridSize(8, 8)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(locked.applyTemplate(SymbolTemplates.Template.OR).canAccept()).isTrue();
    }

    @Test
    void insertingACurveNodeSplitsTheCurveWithoutChangingItsOutline() {
        SymbolPath curve = new SymbolPath(List.of(
                new SymbolNode(new SymbolPoint(0, 0), Optional.of(new SymbolPoint(32, 0))),
                new SymbolNode(new SymbolPoint(32, 32), Optional.empty())), false, false);
        SymbolDraft changed = draft(false).addFigure(curve).insertNode(0, 0, new SymbolPoint(24, 8));
        SymbolPath split = (SymbolPath) changed.elements().getFirst();
        assertThat(split.nodes()).hasSize(3);
        assertThat(split.nodes().get(1).point()).isEqualTo(new SymbolPoint(24, 8));
        assertThat(split.nodes().getFirst().control()).contains(new SymbolPoint(16, 0));
        assertThat(split.nodes().get(1).control()).contains(new SymbolPoint(32, 16));
    }

    @Test
    void openingAClosedFigureDropsItsClosingSegmentAndItsFill() {
        SymbolPath triangle = new SymbolPath(List.of(
                new SymbolNode(new SymbolPoint(8, 8), Optional.empty()),
                new SymbolNode(new SymbolPoint(40, 8), Optional.empty()),
                new SymbolNode(new SymbolPoint(24, 24), Optional.of(new SymbolPoint(8, 24)))), true, true);

        SymbolPath opened = (SymbolPath) draft(false).addFigure(triangle).openPath(0).elements().getFirst();

        assertThat(opened.closed()).isFalse();
        assertThat(opened.filled()).isFalse();
        assertThat(opened.nodes()).extracting(SymbolNode::point).containsExactly(new SymbolPoint(8, 8),
                new SymbolPoint(40, 8), new SymbolPoint(24, 24));
        assertThat(opened.nodes().getLast().control()).isEmpty();
    }

    @Test
    void snappingAppliesToFiguresWhilePinsAlwaysUseWholeGridUnits() {
        SymbolDraft moved = draft(false).addFigure(SymbolTemplates.line(new SymbolPoint(8, 8),
                new SymbolPoint(16, 16), false)).moveSelection(3, 1, SnapMode.HALF_UNITS);
        assertThat(((SymbolPath) moved.elements().getFirst()).nodes().getFirst().point())
                .isEqualTo(new SymbolPoint(12, 8));
        assertThat(moved.appearance().pins().getFirst().offset()).isEqualTo(new GridPoint(0, 2));
    }

    @Test
    void theBlockingProblemComesFirstAndEveryProblemIsReturned() {
        SymbolDraft invalid = draft(false).movePin(INPUT, new GridPoint(3, 2)).movePin(OUTPUT, new GridPoint(3, 2));
        assertThat(invalid.problems().getFirst().kind()).isEqualTo(DraftProblem.Kind.COINCIDING_PINS);
        assertThat(invalid.problems()).extracting(DraftProblem::kind).contains(DraftProblem.Kind.PIN_OFF_EDGE);
        assertThat(invalid.problems()).extracting(DraftProblem::kind).hasSize(3);
    }

    @Test
    void movingAFreeFigureWithTheMagnetOnKeepsItsShape() {
        SymbolDraft moved = draft(false).addFigure(SymbolTemplates.line(new SymbolPoint(3, 3),
                new SymbolPoint(10, 10), false)).moveSelection(2, 2, SnapMode.HALF_UNITS);
        List<SymbolNode> nodes = ((SymbolPath) moved.elements().getFirst()).nodes();
        assertThat(nodes.get(1).point().x() - nodes.getFirst().point().x()).isEqualTo(7);
        assertThat(nodes.get(1).point().y() - nodes.getFirst().point().y()).isEqualTo(7);
    }

    @Test
    void aPinFarFromTheOutlineWarnsThatItIsNotOnTheEdge() {
        SymbolDraft triangle = draft(false).applyTemplate(SymbolTemplates.Template.TRIANGLE)
                .movePin(INPUT, new GridPoint(0, 0)).movePin(OUTPUT, new GridPoint(6, 0));
        assertThat(triangle.problems()).filteredOn(problem -> problem.kind() == DraftProblem.Kind.PIN_OFF_EDGE)
                .extracting(DraftProblem::pins).containsExactly(List.of("Y"));
        assertThat(triangle.canAccept()).isTrue();
    }

    @Test
    void aPinOnTheOutlineOrWithinTwoUnitsOfItDoesNotWarn() {
        assertThat(draft(false).applyTemplate(SymbolTemplates.Template.ALU).problems()).isEmpty();
        assertThat(draft(false).applyTemplate(SymbolTemplates.Template.TRIANGLE).problems()).isEmpty();
    }

    private static SymbolDraft draft(boolean locked) {
        List<DefinitionPin> pins = List.of(new DefinitionPin(PinSpec.input("A", 0, 2), INPUT),
                new DefinitionPin(PinSpec.output("Y", 6, 2), OUTPUT));
        return SymbolDraft.of(new ComponentAppearance(new GridSize(6, 4), Optional.empty(),
                List.of(new PinPlacement(INPUT, new GridPoint(0, 2)),
                        new PinPlacement(OUTPUT, new GridPoint(6, 2)))), pins, locked);
    }

    @Test
    void duplicatingNearTheBoxEdgeDoesNotShrinkTheFigure() {
        SymbolDraft duplicated = draft(false).addFigure(SymbolTemplates.rectangle(new SymbolPoint(30, 8),
                new SymbolPoint(46, 24), false)).duplicateSelection();
        SymbolPath copy = (SymbolPath) duplicated.elements().getLast();
        assertThat(copy.nodes().get(1).point().x() - copy.nodes().getFirst().point().x()).isEqualTo(16);
    }

    @Test
    void theMinimumSizeDoesNotMoveAnEdgePinOntoAnInteriorPin() {
        SymbolDraft changed = draft(false).movePin(INPUT, new GridPoint(1, 2));
        SymbolDraft minimum = changed.resizeBox(changed.minimumBoxSize());
        assertThat(minimum.canAccept()).isTrue();
        assertThatThrownBy(() -> changed.resizeBox(new GridSize(1, 2))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletingOrReorderingWithoutASelectionDoesNotEraseTheDefaultBox() {
        SymbolDraft original = draft(false);
        assertThat(original.deleteSelection()).isSameAs(original);
        assertThat(original.bringToFront()).isSameAs(original);
        assertThat(original.sendToBack()).isSameAs(original);
        assertThat(original.appearance().drawing()).isEmpty();
    }

    @Test
    void proportionalScalingStopsAtTheBoxEdgeWithoutDistortingTheFigure() {
        SymbolDraft scaled = draft(false).addFigure(SymbolTemplates.rectangle(new SymbolPoint(8, 8),
                new SymbolPoint(16, 16), false)).scaleSelection(SymbolDraft.Handle.BOTTOM_RIGHT,
                new SymbolPoint(48, 48), true, SnapMode.FREE);
        SymbolPath rectangle = (SymbolPath) scaled.elements().getFirst();
        assertThat(rectangle.nodes().getFirst().point()).isEqualTo(new SymbolPoint(8, 8));
        assertThat(rectangle.nodes().get(2).point()).isEqualTo(new SymbolPoint(32, 32));
    }
}
