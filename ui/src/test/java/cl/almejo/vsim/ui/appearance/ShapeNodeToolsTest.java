package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Agregar nodo» (A) and «Quitar nodo» (Q) as tools: the pointer shows what a click does, one click acts without a
 * selection, Ctrl held switches to the other tool and a figure never goes below its minimum of nodes. The box is 6 × 4
 * units at zoom 1 with its corner at (70, 70): the Rectángulo template's nodes are at (70, 70), (118, 70), (118, 102)
 * and (70, 102) on screen.
 */
class ShapeNodeToolsTest {

    @Test
    void theRemoveToolMarksTheNodeUnderThePointerInTheProblemColourAndAClickRemovesIt() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.REMOVE_NODE);

        move(canvas, 118, 70, 0);
        BufferedImage image = paint(canvas);
        assertThat(image.getRGB(118, 70)).isEqualTo(ColorSchemes.LIGHT.wireConflict().getRGB());

        click(canvas, 118, 70, 0);

        assertThat(firstPath(draft).nodes()).hasSize(3)
                .noneMatch(node -> node.point().equals(new SymbolPoint(48, 0)));
    }

    @Test
    void theAddToolInsertsANodeWithOneClickAndCtrlTurnsItIntoRemove() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.ADD_NODE);

        move(canvas, 94, 70, 0);
        click(canvas, 94, 70, 0);
        assertThat(firstPath(draft).nodes()).hasSize(5);

        move(canvas, 118, 102, InputEvent.CTRL_DOWN_MASK);
        assertThat(canvas.getCursor()).isSameAs(ShapeCursors.removeNode());
        click(canvas, 118, 102, InputEvent.CTRL_DOWN_MASK);
        assertThat(firstPath(draft).nodes()).hasSize(4)
                .noneMatch(node -> node.point().equals(new SymbolPoint(48, 32)));
    }

    @Test
    void theRemoveToolWithCtrlAddsANode() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.REMOVE_NODE);

        move(canvas, 94, 102, InputEvent.CTRL_DOWN_MASK);
        click(canvas, 94, 102, InputEvent.CTRL_DOWN_MASK);

        assertThat(firstPath(draft).nodes()).hasSize(5);
    }

    @Test
    void pressingAndReleasingCtrlSwitchesTheModeUnderAStillPointer() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.ADD_NODE);
        move(canvas, 118, 70, 0);
        assertThat(canvas.nodeAction()).isEqualTo(ShapeTool.ADD_NODE);
        assertThat(canvas.getCursor()).isSameAs(ShapeCursors.addNode());

        control(canvas, KeyEvent.KEY_PRESSED);
        assertThat(canvas.nodeAction()).isEqualTo(ShapeTool.REMOVE_NODE);
        assertThat(canvas.getCursor()).isSameAs(ShapeCursors.removeNode());
        assertThat(paint(canvas).getRGB(118, 70)).isEqualTo(ColorSchemes.LIGHT.wireConflict().getRGB());
        control(canvas, KeyEvent.KEY_RELEASED);
        assertThat(canvas.nodeAction()).isEqualTo(ShapeTool.ADD_NODE);
        assertThat(canvas.getCursor()).isSameAs(ShapeCursors.addNode());
    }

    @Test
    void aClosedFigureNeverGoesBelowThreeNodesAndSaysWhy() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.REMOVE_NODE);
        click(canvas, 118, 70, 0);
        SymbolDraft triangle = draft.get();

        move(canvas, 118, 102, 0);
        click(canvas, 118, 102, 0);

        assertThat(draft.get()).isSameAs(triangle);
        assertThat(canvas.refusal()).hasValue(Messages.text("shape.nodeRemove.minimumClosed"));
    }

    @Test
    void anOpenFigureNeverGoesBelowTwoNodesAndSaysWhy() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false).addFigure(SymbolTemplates.line(new SymbolPoint(8, 8),
                new SymbolPoint(40, 8), false)));
        SymbolDraft line = draft.get();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.REMOVE_NODE);

        move(canvas, 78, 78, 0);
        click(canvas, 78, 78, 0);

        assertThat(draft.get()).isSameAs(line);
        assertThat(canvas.refusal()).hasValue(Messages.text("shape.nodeRemove.minimumOpen"));
    }

    @Test
    void theRefusalIsShownInTheHintLine() {
        ShapeEditorPanel panel = new ShapeEditorPanel(new ShapeEditRequest(ShapeEditorPanelTest.definition(),
                ComponentAppearance.of(ShapeEditorPanelTest.definition()), ShapeEditScope.INSTANCE, true, false, "",
                1, "U1", false, false), AppPreferences.inMemory(), () -> ColorSchemes.LIGHT);
        panel.apply(SymbolDraft.of(panel.appearance(), ShapeEditorPanelTest.definition().pinout(), false)
                .addFigure(SymbolTemplates.line(new SymbolPoint(8, 8), new SymbolPoint(40, 8), false)));
        ShapeCanvas canvas = panel.canvas();
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        canvas.setTool(ShapeTool.REMOVE_NODE);

        click(canvas, 78, 78, 0);

        assertThat(panel.messageLines()).contains(Messages.text("shape.nodeRemove.minimumOpen"));
    }

    @Test
    void theAddAndRemoveCursorsLookDifferent() {
        BufferedImage add = ShapeCursors.image(32, false);
        BufferedImage remove = ShapeCursors.image(32, true);
        int different = 0;
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 32; y++) {
                if (add.getRGB(x, y) != remove.getRGB(x, y)) {
                    different++;
                }
            }
        }
        assertThat(different).isGreaterThan(20);
    }

    static AtomicReference<SymbolDraft> rectangle() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        return new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.RECTANGLE));
    }

    static ShapeCanvas canvas(AtomicReference<SymbolDraft> draft) {
        ShapeCanvas canvas = new ShapeCanvas(ShapeEditorPanelTest.definition(), draft::get, draft::set, draft::set,
                () -> ColorSchemes.LIGHT, () -> { });
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        return canvas;
    }

    static SymbolPath firstPath(AtomicReference<SymbolDraft> draft) {
        return (SymbolPath) draft.get().elements().getFirst();
    }

    static void move(ShapeCanvas canvas, int x, int y, int modifiers) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, modifiers, x, y, 0, false));
    }

    static void click(ShapeCanvas canvas, int x, int y, int modifiers) {
        click(canvas, x, y, modifiers, 1);
    }

    static void click(ShapeCanvas canvas, int x, int y, int modifiers, int count) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0,
                modifiers | InputEvent.BUTTON1_DOWN_MASK, x, y, count, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, modifiers, x, y, count, false,
                MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, modifiers, x, y, count, false,
                MouseEvent.BUTTON1));
    }

    /** Ctrl pressed or released, straight to the canvas's key listeners (nothing has the focus headless). */
    static void control(ShapeCanvas canvas, int id) {
        int modifiers = id == KeyEvent.KEY_PRESSED ? InputEvent.CTRL_DOWN_MASK : 0;
        KeyEvent event = new KeyEvent(canvas, id, 0, modifiers, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : canvas.getKeyListeners()) {
            if (id == KeyEvent.KEY_PRESSED) {
                listener.keyPressed(event);
            } else {
                listener.keyReleased(event);
            }
        }
    }

    static BufferedImage paint(ShapeCanvas canvas) {
        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
