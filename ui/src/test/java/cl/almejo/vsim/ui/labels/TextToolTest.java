package cl.almejo.vsim.ui.labels;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;

/** The «Texto» tool: a click asks the editor for a text at the snapped point and goes back to Selección. */
class TextToolTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final TextTool tool = new TextTool();

    @BeforeEach
    void useTheTool() {
        context.setTool(tool);
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void aClickOpensTheTextEditorAtTheSnappedPointAndGoesBackToSeleccion() {
        context.click(tool, 10.2, 9.8);

        assertThat(context.textAddedAt).isEqualTo(new GridPoint(10, 10));
        assertThat(context.tool).isSameAs(context.defaultTool);
        assertThat(context.openDocument().document().labels()).isEmpty();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void escapeGoesBackToSeleccionWithoutAsking() {
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();

        assertThat(context.textAddedAt).isNull();
        assertThat(context.tool).isSameAs(context.defaultTool);
    }
}
