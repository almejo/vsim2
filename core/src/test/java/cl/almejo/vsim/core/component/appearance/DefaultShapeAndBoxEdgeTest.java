package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.DefinitionPin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The default shape is real figures from the start (the Rectángulo template), and one rule resizes the box: what is
 * on an edge moves with that edge, a control point moves with its node, everything else stays centred. A drawing
 * equal to the default shape is stored as no own drawing. The 6 × 4 box is 48 × 32 eighths.
 */
class DefaultShapeAndBoxEdgeTest {
    private static final ElementId INPUT = new ElementId("input");
    private static final ElementId OUTPUT = new ElementId("output");

    @Test
    void theDefaultShapeBecomesTheRectangleAndTheNameAsFigures() {
        SymbolDraft opened = blank().withDefaultShapeAsFigures();

        assertThat(opened.appearance().drawing()).contains(SymbolTemplates.defaultShape(new GridSize(6, 4)));
        assertThat(opened.elements()).hasSize(2);
        assertThat(((SymbolText) opened.elements().getLast()).text()).as("the name follows the component's")
                .isEmpty();
        assertThat(opened.selectAll().selection()).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void theNameSitsWhereTheDefaultBoxWritesIt() {
        assertThat(((SymbolText) SymbolTemplates.defaultShape(new GridSize(6, 4)).elements().getLast()).anchor())
                .isEqualTo(new SymbolPoint(24, 8));
        assertThat(((SymbolText) SymbolTemplates.defaultShape(new GridSize(6, 3)).elements().getLast()).anchor())
                .isEqualTo(new SymbolPoint(24, 12));
    }

    @Test
    void theRectangleTemplateIsAnOwnDrawing() {
        assertThat(blank().applyTemplate(SymbolTemplates.Template.RECTANGLE).storedAppearance().drawing()).isPresent();
    }

    @Test
    void anOwnDrawingIsOpenedAsItIs() {
        SymbolDraft alu = blank().applyTemplate(SymbolTemplates.Template.ALU);

        assertThat(alu.withDefaultShapeAsFigures()).isSameAs(alu);
    }

    @Test
    void choosingTheDefaultShapeGivesItsFiguresAndKeepsTheMovedPins() {
        SymbolDraft restored = blank().movePin(INPUT, new GridPoint(0, 3)).applyTemplate(SymbolTemplates.Template.ALU)
                .applyTemplate(SymbolTemplates.Template.DEFAULT_BOX);

        assertThat(restored.elements()).hasSize(2);
        assertThat(restored.storedAppearance().drawing()).isEmpty();
        assertThat(restored.storedAppearance().pins()).contains(new PinPlacement(INPUT, new GridPoint(0, 3)));
    }

    @Test
    void anUnchangedDefaultShapeIsStoredAsNoOwnDrawing() {
        assertThat(blank().withDefaultShapeAsFigures().storedAppearance()).isEqualTo(blank().appearance());
    }

    @Test
    void anEditedDefaultShapeIsStoredAsADrawing() {
        SymbolDraft edited = blank().withDefaultShapeAsFigures().moveNode(0, 2, new SymbolPoint(40, 32),
                SnapMode.FREE);

        assertThat(edited.storedAppearance().drawing()).isEqualTo(edited.appearance().drawing()).isPresent();
    }

    @Test
    void theRectangleGrowsWithTheBoxAndStaysTheDefaultShape() {
        SymbolDraft resized = blank().withDefaultShapeAsFigures().resizeBox(new GridSize(10, 6));

        assertThat(points(resized, 0)).containsExactly(new SymbolPoint(0, 0), new SymbolPoint(80, 0),
                new SymbolPoint(80, 48), new SymbolPoint(0, 48));
        assertThat(resized.storedAppearance().drawing()).isEmpty();
    }

    @Test
    void theTipOfATriangleOnTheRightEdgeFollowsIt() {
        SymbolDraft resized = blank().applyTemplate(SymbolTemplates.Template.TRIANGLE).resizeBox(new GridSize(10, 6));

        assertThat(points(resized, 0)).containsExactly(new SymbolPoint(0, 0), new SymbolPoint(80, 24),
                new SymbolPoint(0, 48));
    }

    @Test
    void anInnerTextStaysCentred() {
        SymbolDraft resized = blank().addText(new SymbolText(new SymbolPoint(20, 12), Optional.of("A"),
                TextSize.SMALL, TextAlignment.LEFT)).resizeBox(new GridSize(10, 6));

        assertThat(((SymbolText) resized.elements().getFirst()).anchor()).isEqualTo(new SymbolPoint(36, 20));
    }

    @Test
    void aControlPointMovesWithItsNode() {
        SymbolPath curve = new SymbolPath(List.of(new SymbolNode(new SymbolPoint(48, 8),
                Optional.of(new SymbolPoint(40, 20))), new SymbolNode(new SymbolPoint(24, 28), Optional.empty())),
                false, false);

        SymbolDraft resized = blank().addFigure(curve).resizeBox(new GridSize(10, 6));

        SymbolPath moved = (SymbolPath) resized.elements().getFirst();
        assertThat(moved.nodes().getFirst()).isEqualTo(new SymbolNode(new SymbolPoint(80, 16),
                Optional.of(new SymbolPoint(72, 28))));
        assertThat(moved.nodes().getLast().point()).isEqualTo(new SymbolPoint(40, 36));
    }

    @Test
    void aControlPointOnTheBoxEdgeStaysOnItSoAnAndGateStretchesAndShrinks() {
        SymbolDraft and = blank().applyTemplate(SymbolTemplates.Template.AND);

        SymbolDraft grown = and.resizeBox(new GridSize(10, 6));
        SymbolDraft shrunk = and.resizeBox(new GridSize(4, 4));

        assertThat(controls(grown)).containsExactly(new SymbolPoint(80, 0), new SymbolPoint(80, 48));
        assertThat(points(grown, 0)).containsExactly(new SymbolPoint(0, 0), new SymbolPoint(40, 0),
                new SymbolPoint(80, 24), new SymbolPoint(40, 48), new SymbolPoint(0, 48));
        assertThat(controls(shrunk)).containsExactly(new SymbolPoint(32, 0), new SymbolPoint(32, 32));
    }

    @Test
    void ctrlScalesTheDefaultShapeAndItStaysTheDefault() {
        SymbolDraft scaled = blank().withDefaultShapeAsFigures().scaleBox(new GridSize(12, 8));

        assertThat(points(scaled, 0)).containsExactly(new SymbolPoint(0, 0), new SymbolPoint(96, 0),
                new SymbolPoint(96, 64), new SymbolPoint(0, 64));
        assertThat(scaled.storedAppearance().drawing()).isEmpty();
    }

    @Test
    void movingAPinOfTheDefaultShapeKeepsItTheDefault() {
        SymbolDraft moved = blank().withDefaultShapeAsFigures().movePin(OUTPUT, new GridPoint(6, 1));

        assertThat(moved.appearance().drawing()).isPresent();
        assertThat(moved.storedAppearance().drawing()).isEmpty();
    }

    private static List<SymbolPoint> points(SymbolDraft draft, int index) {
        return ((SymbolPath) draft.elements().get(index)).nodes().stream().map(SymbolNode::point).toList();
    }

    private static List<SymbolPoint> controls(SymbolDraft draft) {
        return ((SymbolPath) draft.elements().getFirst()).nodes().stream().map(SymbolNode::control)
                .flatMap(Optional::stream).toList();
    }

    private static SymbolDraft blank() {
        List<DefinitionPin> pins = List.of(new DefinitionPin(PinSpec.input("A", 0, 2), INPUT),
                new DefinitionPin(PinSpec.output("Y", 6, 2), OUTPUT));
        return SymbolDraft.of(new ComponentAppearance(new GridSize(6, 4), Optional.empty(),
                List.of(new PinPlacement(INPUT, new GridPoint(0, 2)),
                        new PinPlacement(OUTPUT, new GridPoint(6, 2)))), pins, false).withDefinitionName("Sumador");
    }
}
