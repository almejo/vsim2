package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.frames.FrameTool;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.inline.InlineEditRequest;
import cl.almejo.vsim.ui.inline.InlineValidators;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.PalettePanelAccess;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import cl.almejo.vsim.ui.tools.PlaceComponentTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escape cancels a placement wherever the focus is in the editor window (bug 2026-10-18: after a click in the palette
 * the focus stayed there and Escape never reached the placing tool). Keys are resolved as Swing does for a focused
 * component: its own bindings first, then its ancestors'.
 */
class EscapeCancelsPlacementTest {

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, new NoWindow());
    }

    private static final class NoWindow implements WindowCommands {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    /** What Swing does with a key pressed while {@code focused} has the focus, once its key listeners let it by. */
    private static boolean press(java.awt.Component focused, int keyCode) {
        return SwingUtilities.processKeyBindings(
                new KeyEvent(focused, KeyEvent.KEY_PRESSED, 0, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    @Test
    void escapeWithTheFocusInThePaletteCancelsPlacingAPaletteComponent() {
        view.palette().choose(StandardComponentTypes.AND2);
        assertThat(view.canvas().tool()).isInstanceOf(PlaceComponentTool.class);

        assertThat(press(PalettePanelAccess.tree(view.palette()), KeyEvent.VK_ESCAPE)).isTrue();

        assertThat(view.canvas().tool()).isInstanceOf(SelectTool.class);
        assertThat(PalettePanelAccess.hasSelection(view.palette())).isFalse();
        assertThat(openDocument.document().components()).isEmpty();
        assertThat(openDocument.history().canUndo()).isFalse();
    }

    @Test
    void escapeWithTheFocusInTheSearchFieldCancelsPlacingAndKeepsTheSearchText() {
        view.palette().setFilter("and");
        view.palette().choose(StandardComponentTypes.AND2);

        assertThat(press(PalettePanelAccess.searchField(view.palette()), KeyEvent.VK_ESCAPE)).isTrue();

        assertThat(view.canvas().tool()).isInstanceOf(SelectTool.class);
        assertThat(PalettePanelAccess.searchField(view.palette()).getText()).isEqualTo("and");
    }

    @Test
    void escapeWithTheFocusOutsideTheCanvasCancelsAFloatingPaste() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        view.canvas().selection().select(gate.id());
        view.selectionActions().duplicate().actionPerformed(
                new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, ""));
        assertThat(view.canvas().tool()).isInstanceOf(FloatingSelectionTool.class);

        assertThat(press(PalettePanelAccess.tree(view.palette()), KeyEvent.VK_ESCAPE)).isTrue();

        assertThat(view.canvas().tool()).isInstanceOf(SelectTool.class);
        assertThat(openDocument.document().components()).containsExactly(gate);
        openDocument.history().undo();
        assertThat(openDocument.history().canUndo()).isFalse();
    }

    @Test
    void escapeWithTheFocusOutsideTheCanvasLeavesTheFrameTool() {
        view.canvas().setTool(new FrameTool());

        assertThat(press(PalettePanelAccess.tree(view.palette()), KeyEvent.VK_ESCAPE)).isTrue();

        assertThat(view.canvas().tool()).isInstanceOf(SelectTool.class);
    }

    @Test
    void withTheSelectToolEscapeOutsideTheCanvasIsLeftAlone() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        view.canvas().selection().select(gate.id());

        assertThat(press(PalettePanelAccess.tree(view.palette()), KeyEvent.VK_ESCAPE)).isFalse();

        assertThat(view.canvas().selection().ids()).containsExactly(gate.id());
    }

    @Test
    void theInlineEditorsEscapeCancelsTheEditOnly() {
        view.palette().choose(StandardComponentTypes.AND2);
        view.inlineEditor().open(new InlineEditRequest(new Rectangle2D.Double(80, 40, 160, 24),
                new Font(Font.SANS_SERIF, Font.PLAIN, 12), "hola", InlineValidators.anyText(), text -> { }));

        assertThat(press(view.inlineEditor().field(), KeyEvent.VK_ESCAPE)).isTrue();

        assertThat(view.inlineEditor().isOpen()).isFalse();
        assertThat(view.canvas().tool()).isInstanceOf(PlaceComponentTool.class);
    }

    @Test
    void placingStillWorksAfterAnEscapeFromThePalette() {
        view.palette().choose(StandardComponentTypes.AND2);
        press(PalettePanelAccess.tree(view.palette()), KeyEvent.VK_ESCAPE);
        view.palette().choose(StandardComponentTypes.AND2);

        view.canvas().tool().mousePressed(view.canvas(), TestToolContext.at(4, 4));

        assertThat(openDocument.document().components()).hasSize(1);
    }
}
