package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class FrameToolTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final FrameTool tool = new FrameTool();

    @BeforeEach
    void language() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void close() {
        context.close();
    }

    private Frame onlyFrame() {
        assertThat(context.openDocument().document().frames()).hasSize(1);
        return context.openDocument().document().frames().get(0);
    }

    @Test
    void draggingCreatesTheDraggedRectangleUnlockedAndTitledBloque() {
        context.drag(tool, 10, 20, 40, 50);
        Frame frame = onlyFrame();
        assertThat(frame.topLeft().x()).isEqualTo(10);
        assertThat(frame.topLeft().y()).isEqualTo(20);
        assertThat(frame.width()).isEqualTo(30);
        assertThat(frame.height()).isEqualTo(30);
        assertThat(frame.locked()).isFalse();
        assertThat(frame.title()).isEqualTo("Bloque");
    }

    @Test
    void draggingUpAndLeftNormalisesTheRectangle() {
        context.drag(tool, 40, 50, 10, 20);
        Frame frame = onlyFrame();
        assertThat(frame.topLeft().x()).isEqualTo(10);
        assertThat(frame.topLeft().y()).isEqualTo(20);
        assertThat(frame.width()).isEqualTo(30);
        assertThat(frame.height()).isEqualTo(30);
    }

    @Test
    void aSmallDragGrowsToTheMinimumSize() {
        context.drag(tool, 10, 10, 11, 11);
        Frame frame = onlyFrame();
        assertThat(frame.width()).isEqualTo(Frame.MINIMUM_WIDTH);
        assertThat(frame.height()).isEqualTo(Frame.MINIMUM_HEIGHT);
    }

    @Test
    void aClickWithoutDraggingCreatesNothing() {
        context.click(tool, 10, 10);
        assertThat(context.openDocument().document().frames()).isEmpty();
    }

    @Test
    void afterCreatingTheToolReturnsToSelectTheFrameIsSelectedAndItsTitleEditorOpens() {
        context.tool = tool;
        context.drag(tool, 10, 20, 40, 50);
        Frame frame = onlyFrame();
        assertThat(context.tool).isSameAs(context.defaultTool);
        assertThat(context.selection.ids()).containsExactly(frame.id());
        assertThat(context.editedLabel).isEqualTo(frame);
    }

    @Test
    void creatingIsOneUndoStep() {
        context.drag(tool, 10, 20, 40, 50);
        assertThat(context.openDocument().history().undoDescription()).contains("command.createFrame");
        context.openDocument().history().undo();
        assertThat(context.openDocument().document().frames()).isEmpty();
    }

    @Test
    void escapeWhileDraggingCancelsAndTheOutlinePaintsOnlyWhileDragging() {
        Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        tool.mousePressed(context, TestToolContext.at(10, 10));
        assertThat(tool.draggedRectangle()).isNull();
        tool.mouseDragged(context, TestToolContext.at(30, 30));
        assertThat(tool.draggedRectangle()).isNotNull();
        tool.paintOverlay(graphics, context);
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(tool.draggedRectangle()).isNull();
        tool.mouseReleased(context, TestToolContext.at(30, 30));
        assertThat(context.openDocument().document().frames()).isEmpty();
    }
}
