package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.tools.CanvasCursor;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The frame controls (spec §5): a frame at (0, 0), 20 x 12; the padlock sits at grid x 16.5..18 and the ⋯ button at
 * 18.25..19.75, both within the title bar (y 0.25..1.75).
 */
class FrameControlsTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private final FrameGestures gestures = new FrameGestures();
    private final List<Frame> titleEdited = new ArrayList<>();
    private final List<Frame> converted = new ArrayList<>();
    private final FrameMenu menu = new FrameMenu(context.openDocument(), titleEdited::add, converted::add);

    @BeforeEach
    void create() {
        tool.addDelegate(gestures);
    }

    @AfterEach
    void close() {
        context.close();
    }

    private Frame frame(boolean locked) {
        Frame frame = new Frame(context.openDocument().document().newFrameId(), new GridPoint(0, 0), 20, 12,
                "Sumador", FrameTint.BLUE, locked);
        context.openDocument().execute(new CreateFrameCommand(frame));
        return frame;
    }

    private Frame current(Frame frame) {
        return context.openDocument().document().frame(frame.id()).orElseThrow();
    }

    private static List<String> texts(JPopupMenu popup) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : popup.getComponents()) {
            texts.add(((JMenuItem) child).getText());
        }
        return texts;
    }

    private static JMenuItem item(JPopupMenu popup, String text) {
        for (java.awt.Component child : popup.getComponents()) {
            if (child instanceof JMenuItem found && found.getText().equals(text)) {
                return found;
            }
        }
        throw new AssertionError("no item " + text);
    }

    // --- the controls

    @Test
    void clickingThePadlockTogglesTheLockAsOneUndoStepEachWay() {
        Frame frame = frame(false);
        context.click(tool, 17, 1);
        assertThat(current(frame).locked()).isTrue();
        assertThat(context.openDocument().history().undoDescription()).contains("command.lockFrame");
        context.click(tool, 17, 1);
        assertThat(current(frame).locked()).isFalse();
        assertThat(context.openDocument().history().undoDescription()).contains("command.unlockFrame");
        context.openDocument().history().undo();
        assertThat(current(frame).locked()).isTrue();
        context.openDocument().history().undo();
        assertThat(current(frame)).isEqualTo(frame);
    }

    @Test
    void clickingTheMenuButtonOpensTheFrameMenuOnThatFrame() {
        Frame frame = frame(false);
        context.click(tool, 19, 1);
        assertThat(context.contextMenuTarget).contains(frame);
        assertThat(context.selection.ids()).containsExactly(frame.id());
        assertThat(current(frame)).isEqualTo(frame);
    }

    @Test
    void theControlsHaveTheHandCursor() {
        frame(false);
        tool.mouseMoved(context, TestToolContext.at(25, 25));
        tool.mouseMoved(context, TestToolContext.at(10, 1));
        tool.mouseMoved(context, TestToolContext.at(17, 1));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.HAND);
        tool.mouseMoved(context, TestToolContext.at(19, 1));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.HAND);
    }

    // --- painting

    private RenderState renderState() {
        OpenDocument source = context.openDocument();
        return new RenderState(source.document(), source.netlist(), source.netlistToken(), source.latestSnapshot(),
                Set.of(), 0);
    }

    private int alphaAround(Frame frame, boolean lockNotMenu) {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setClip(0, 0, 200, 100);
        // as the canvas paints: the overlays (the faint padlock), then the tool's overlay
        new FrameLockOverlay(() -> context.viewport().pixelsPerUnit()).paint(graphics, renderState());
        tool.paintOverlay(graphics, context);
        graphics.dispose();
        var bounds = lockNotMenu ? FramePainter.lockBounds(frame) : FramePainter.menuBounds(frame);
        int maximum = 0;
        for (int x = (int) bounds.getMinX(); x < bounds.getMaxX(); x++) {
            for (int y = (int) bounds.getMinY(); y < bounds.getMaxY(); y++) {
                maximum = Math.max(maximum, image.getRGB(x, y) >>> 24);
            }
        }
        return maximum;
    }

    @Test
    void controlsAreOnlyPaintedWhileTheFrameIsHoveredOrSelected() {
        Frame frame = frame(false);
        assertThat(alphaAround(frame, true)).isZero();
        assertThat(alphaAround(frame, false)).isZero();
        tool.mouseMoved(context, TestToolContext.at(10, 8));
        assertThat(alphaAround(frame, true)).isPositive();
        assertThat(alphaAround(frame, false)).isPositive();
        tool.mouseExited(context);
        assertThat(alphaAround(frame, true)).isZero();
        context.selection.select(frame.id());
        assertThat(alphaAround(frame, true)).isPositive();
        assertThat(alphaAround(frame, false)).isPositive();
    }

    @Test
    void aLockedFrameAlwaysShowsAFaintPadlockAndNoMenuButton() {
        Frame frame = frame(true);
        int faint = alphaAround(frame, true);
        assertThat(faint).isPositive();
        assertThat(alphaAround(frame, false)).isZero();
        tool.mouseMoved(context, TestToolContext.at(10, 8));
        assertThat(alphaAround(frame, true)).isGreaterThan(faint);
    }

    @Test
    void theTitleIsCutBeforeTheControls() {
        Frame frame = new Frame(context.openDocument().document().newFrameId(), new GridPoint(0, 0), 20, 12,
                "Un título larguísimo que no cabe en la barra", FrameTint.NONE, false);
        BufferedImage image = new BufferedImage(200, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        FramePainter.paint(graphics, frame, cl.almejo.vsim.ui.paint.ColorScheme.LIGHT, 8);
        graphics.dispose();
        var lock = FramePainter.lockBounds(frame);
        for (int x = (int) lock.getMinX(); x < lock.getMaxX(); x++) {
            for (int y = 3; y < 14; y++) {
                // only the faint band tint may be there, never title ink (fully opaque dark pixels)
                assertThat(image.getRGB(x, y) >>> 24).as("pixel %d,%d", x, y).isLessThan(100);
            }
        }
    }

    // --- the menu

    @Test
    void theMenuListsTheSpecifiedItems() {
        Frame frame = frame(false);
        JPopupMenu popup = menu.build(frame);
        assertThat(texts(popup)).containsExactly("Cambiar título…", "Color", "Cerrar candado",
                "Convertir en componente…", "Quitar marco");
        JMenu color = (JMenu) popup.getComponent(1);
        List<String> names = new ArrayList<>();
        for (int index = 0; index < color.getItemCount(); index++) {
            names.add(color.getItem(index).getText());
        }
        assertThat(names).containsExactly("Sin color", "Azul", "Verde", "Amarillo", "Rojo", "Morado", "Gris");
        assertThat(texts(menu.build(frame(true)))).contains("Abrir candado");
    }

    @Test
    void theColorRadioReflectsTheTintAndChangingItIsOneUndoStep() {
        Frame frame = frame(false);
        JMenu color = (JMenu) menu.build(frame).getComponent(1);
        assertThat(((JRadioButtonMenuItem) color.getItem(1)).isSelected()).isTrue();
        assertThat(((JRadioButtonMenuItem) color.getItem(0)).isSelected()).isFalse();
        color.getItem(3).doClick();
        assertThat(current(frame).tint()).isEqualTo(FrameTint.YELLOW);
        assertThat(context.openDocument().history().undoDescription()).contains("command.tintFrame");
        context.openDocument().history().undo();
        assertThat(current(frame)).isEqualTo(frame);
        assertThat(((JRadioButtonMenuItem) ((JMenu) menu.build(current(frame)).getComponent(1)).getItem(1))
                .isSelected()).isTrue();
    }

    @Test
    void theLockItemTogglesAndTheTitleItemOpensTheTitleEditor() {
        Frame frame = frame(false);
        item(menu.build(frame), "Cerrar candado").doClick();
        assertThat(current(frame).locked()).isTrue();
        item(menu.build(current(frame)), "Cambiar título…").doClick();
        assertThat(titleEdited).containsExactly(current(frame));
    }

    @Test
    void quitarMarcoRemovesOnlyTheFrameEvenWhenLockedInOneUndoStep() {
        Frame frame = frame(true);
        Component inside = place(context.openDocument(), StandardComponentTypes.BUFFER, 4, 4, Map.of(StandardComponentTypes.NEGATE, true));
        item(menu.build(frame), "Quitar marco").doClick();
        assertThat(context.openDocument().document().frame(frame.id())).isEmpty();
        assertThat(context.openDocument().document().component(inside.id())).contains(inside);
        context.openDocument().history().undo();
        assertThat(current(frame)).isEqualTo(frame);
    }

    @Test
    void convertIsDisabledWithoutContentsAndHandsTheFrameOverOtherwise() {
        Frame frame = frame(false);
        assertThat(item(menu.build(frame), "Convertir en componente…").isEnabled()).isFalse();
        place(context.openDocument(), StandardComponentTypes.BUFFER, 4, 4, Map.of(StandardComponentTypes.NEGATE, true));
        JMenuItem convert = item(menu.build(frame), "Convertir en componente…");
        assertThat(convert.isEnabled()).isTrue();
        convert.doClick();
        assertThat(converted).containsExactly(frame);
    }
}
