package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.snap.DefaultSnapper;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ajustar for a frame dragged by its title bar: the frame (0, 0, 20 × 12) has the lines y 0, 6, 12; a NOT gate at
 * (40, 15) has 15, 17, 19. Dragged down two units its bottom is one unit off the gate's top.
 */
class FrameMoveSnapTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private Frame frame;

    @BeforeEach
    void create() {
        tool.addDelegate(new FrameGestures());
        context.selection.addExpansion(FramesFeatures.lockedBlocks(context.openDocument()));
        context.snapper = new DefaultSnapper(context.preferences, Dimension::new);
        frame = new Frame(context.openDocument().document().newFrameId(), GridPoint.ORIGIN, 20, 12, "Bloque",
                FrameTint.BLUE, false);
        context.openDocument().execute(new CreateFrameCommand(frame));
        place(context.openDocument(), StandardComponentTypes.NOT, 40, 15, Map.of());
    }

    @AfterEach
    void close() {
        context.close();
    }

    private GridPoint topLeft() {
        return context.openDocument().document().frame(frame.id()).orElseThrow().topLeft();
    }

    @Test
    void aTitleBarMoveSnapsToANeighbour() {
        context.drag(tool, 10, 1, 10, 3);
        assertThat(topLeft()).isEqualTo(new GridPoint(0, 3));
    }

    @Test
    void aRefreshDuringTheMoveIsWhatTheReleaseCommits() {
        tool.mousePressed(context, TestToolContext.at(10, 1));
        tool.mouseDragged(context, TestToolContext.at(10, 3));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        context.preferences.setSnapToAlignments(false);
        assertThat(tool.refreshSnap(context)).isTrue();
        tool.mouseReleased(context, TestToolContext.at(10, 3));
        assertThat(topLeft()).isEqualTo(new GridPoint(0, 2));
    }

    @Test
    void aResizeDoesNotSnap() {
        // the bottom edge of the frame dragged down two units: 14, one off the gate's top, stays 14
        context.drag(tool, 10, 12, 10, 14);
        Frame resized = context.openDocument().document().frame(frame.id()).orElseThrow();
        assertThat(resized.height()).isEqualTo(14);
    }
}
