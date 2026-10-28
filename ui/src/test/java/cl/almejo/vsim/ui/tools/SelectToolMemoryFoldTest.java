package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.paint.MemoryPainter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** I6: the ▾ / ▸ square in a memory's title bar folds and unfolds its content window with a click (one undo step). */
class SelectToolMemoryFoldTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();

    @AfterEach
    void close() {
        context.close();
    }

    private Component memory(Rotation rotation, GridPoint position) {
        ComponentType type = context.openDocument().registry().require(StandardComponentTypes.RAM);
        Component component = type.newComponent(context.openDocument().document().newComponentId(), position,
                rotation, Map.of(), "");
        context.openDocument().execute(new AddElementsCommand(List.of(component)));
        return component;
    }

    private String showContents(Component component) {
        Component current = context.openDocument().document().component(component.id()).orElseThrow();
        return (String) context.openDocument().registry().typeOf(current).resolveParameters(current.parameters())
                .get(MemoryTypes.SHOW_CONTENTS);
    }

    /** The centre of the fold square of {@code component}, as a grid position after its rotation and place. */
    private static Point2D glyphOf(Component component) {
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.RAM)
                .specializedFor(component.parameters());
        Rectangle2D local = MemoryPainter.foldToggleArea(type, component.rotation())
                .orElseThrow();
        return toGrid(component, new Point2D.Double(local.getCenterX(), local.getCenterY()));
    }

    private static Point2D toGrid(Component component, Point2D local) {
        AffineTransform placed = new AffineTransform();
        placed.translate(component.position().x() * GridPoint.PIXELS_PER_UNIT,
                component.position().y() * GridPoint.PIXELS_PER_UNIT);
        placed.rotate(component.rotation().quarterTurns() * Math.PI / 2);
        Point2D document = placed.transform(local, null);
        return new Point2D.Double(document.getX() / GridPoint.PIXELS_PER_UNIT,
                document.getY() / GridPoint.PIXELS_PER_UNIT);
    }

    private void click(Point2D gridPosition) {
        context.click(tool, gridPosition.getX(), gridPosition.getY());
    }

    @Test
    void clickingTheFoldSquareFoldsAndUnfoldsTheWindowOneUndoStepEach() {
        Component ram = memory(Rotation.R0, new GridPoint(10, 10));
        Point2D glyph = glyphOf(ram);

        click(glyph);
        assertThat(showContents(ram)).isEqualTo(MemoryTypes.SHOW_CONTENTS_NO);
        assertThat(context.openDocument().history().undoDescription()).contains("command.toggleMemoryContents");
        assertThat(context.selection.ids()).containsExactly(ram.id());

        click(glyph);
        assertThat(showContents(ram)).isEqualTo(MemoryTypes.SHOW_CONTENTS_YES);

        context.openDocument().history().undo();
        assertThat(showContents(ram)).isEqualTo(MemoryTypes.SHOW_CONTENTS_NO);
    }

    @Test
    void theSquareFollowsAMemoryTurnedUpsideDown() {
        Component ram = memory(Rotation.R180, new GridPoint(40, 40));

        click(glyphOf(ram));

        assertThat(showContents(ram)).isEqualTo(MemoryTypes.SHOW_CONTENTS_NO);
    }

    @Test
    void aMemoryTurnedSidewaysHasNoSquareAndAClickWhereItWouldBeOnlySelects() {
        Component ram = memory(Rotation.R90, new GridPoint(40, 10));
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.RAM)
                .specializedFor(ram.parameters());
        assertThat(MemoryPainter.foldToggleArea(type, Rotation.R90)).isEmpty();
        Rectangle2D upright = MemoryPainter.foldToggleArea(type, Rotation.R0).orElseThrow();

        click(toGrid(ram, new Point2D.Double(upright.getCenterX(), upright.getCenterY())));

        assertThat(context.selection.ids()).containsExactly(ram.id());
        assertThat(showContents(ram)).isEqualTo(MemoryTypes.SHOW_CONTENTS_YES);
        assertThat(context.openDocument().history().undoDescription()).isNotEqualTo(Optional.of("command.toggleMemoryContents"));
    }

    @Test
    void aClickOnTheBodyOnlySelects() {
        Component ram = memory(Rotation.R0, new GridPoint(10, 10));

        context.click(tool, 16, 18);

        assertThat(context.selection.ids()).containsExactly(ram.id());
        assertThat(showContents(ram)).isEqualTo(MemoryTypes.SHOW_CONTENTS_YES);
    }

    @Test
    void aDragThatStartsOnTheSquareMovesTheMemoryWithoutFoldingIt() {
        Component ram = memory(Rotation.R0, new GridPoint(10, 10));
        Point2D glyph = glyphOf(ram);

        context.drag(tool, glyph.getX(), glyph.getY(), glyph.getX() + 3, glyph.getY() + 2);

        Component moved = context.openDocument().document().component(ram.id()).orElseThrow();
        assertThat(moved.position()).isEqualTo(new GridPoint(13, 12));
        assertThat(showContents(ram)).isEqualTo(MemoryTypes.SHOW_CONTENTS_YES);
        assertThat(context.openDocument().history().undoDescription()).contains("command.move");
    }

    @Test
    void aDoubleClickOnTheSquareNeverOpensTheContentEditor() {
        Component ram = memory(Rotation.R0, new GridPoint(10, 10));
        Point2D glyph = glyphOf(ram);

        tool.mousePressed(context, TestToolContext.at(glyph.getX(), glyph.getY()));
        tool.mouseReleased(context, TestToolContext.at(glyph.getX(), glyph.getY()));
        tool.mousePressed(context, TestToolContext.event(glyph.getX(), glyph.getY(), CanvasMouseEvent.PRIMARY, 2,
                false));
        tool.mouseReleased(context, TestToolContext.event(glyph.getX(), glyph.getY(), CanvasMouseEvent.PRIMARY, 2,
                false));

        assertThat(context.memoryEdited).isNull();
        assertThat(context.configured).isNull();
        assertThat(showContents(ram)).as("each click of the double click folds or unfolds")
                .isEqualTo(MemoryTypes.SHOW_CONTENTS_YES);
    }

    @Test
    void theSquareShowsAHandOverIt() {
        Component ram = memory(Rotation.R0, new GridPoint(10, 10));
        Point2D glyph = glyphOf(ram);

        tool.mouseMoved(context, TestToolContext.at(glyph.getX(), glyph.getY()));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.HAND);

        tool.mouseMoved(context, TestToolContext.at(16, 18));
        assertThat(context.lastCursor()).isNotEqualTo(CanvasCursor.HAND);
    }
}
