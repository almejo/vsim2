package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.actions.ContextMenuFactory;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasHost;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.panels.StatusBar;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.PlaceComponentTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.WireTool;

import javax.swing.ButtonGroup;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.Optional;

/** Content of an editor window: toolbar, palette, canvas and status bar, wired to one {@link OpenDocument}. */
public final class EditorView extends JPanel implements CanvasHost {

    private static final int PALETTE_WIDTH = 230;

    private final OpenDocument openDocument;
    private final CircuitCanvas canvas;
    private final EditorActions actions;
    private final PalettePanel palette;
    private final StatusBar statusBar = new StatusBar();
    private final JMenuBar menuBar;
    private final JToolBar toolBar = new JToolBar();
    private final ButtonGroup toolButtons = new ButtonGroup();
    private final JToggleButton selectButton;
    private final JToggleButton wireButton;
    private final JToggleButton panButton;

    public EditorView(OpenDocument openDocument, PainterRegistry painters, ColorScheme scheme, WindowCommands window) {
        super(new BorderLayout());
        this.openDocument = openDocument;
        CircuitRenderer renderer = new CircuitRenderer(openDocument.registry(), painters, scheme);
        this.canvas = new CircuitCanvas(openDocument, renderer, this);
        this.actions = new EditorActions(openDocument, canvas, window);
        this.palette = new PalettePanel(openDocument.registry(), painters, scheme, type -> {
            canvas.setTool(new PlaceComponentTool(type));
            canvas.requestFocusInWindow();
        });
        this.menuBar = actions.createMenuBar();
        selectButton = new JToggleButton(actions.selectTool());
        wireButton = new JToggleButton(actions.wireTool());
        panButton = new JToggleButton(actions.panTool());
        buildToolBar();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, palette, canvas);
        split.setDividerLocation(PALETTE_WIDTH);
        split.setContinuousLayout(true);
        split.setResizeWeight(0);
        add(toolBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        canvas.addToolListener(this::toolChanged);
        canvas.addSnapshotListener(statusBar::showSnapshot);
        canvas.addViewListener(() -> statusBar.showZoom(canvas.viewport().zoom()));
        statusBar.showSnapshot(SimulationSnapshot.EMPTY);
        statusBar.showZoom(canvas.viewport().zoom());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete", actions.delete());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete", actions.delete());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "rotateClockwise", actions.rotateClockwise());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK), "rotateCounterClockwise",
                actions.rotateCounterClockwise());
        toolChanged(canvas.tool());
    }

    public OpenDocument openDocument() { return openDocument; }
    public CircuitCanvas canvas() { return canvas; }
    public EditorActions actions() { return actions; }
    public PalettePanel palette() { return palette; }
    public StatusBar statusBar() { return statusBar; }
    public JMenuBar menuBar() { return menuBar; }
    public JToolBar toolBar() { return toolBar; }

    public void dispose() {
        canvas.dispose();
    }

    @Override
    public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target, Point2D screenPosition) {
        ContextMenuFactory.create(target, actions)
                .show(source, (int) screenPosition.getX(), (int) screenPosition.getY());
    }

    @Override
    public void configure(Component component) {
        actions.configure(component);
    }

    private void buildToolBar() {
        toolBar.setFloatable(false);
        toolBar.add(actions.newDocument());
        toolBar.add(actions.open());
        toolBar.add(actions.save());
        toolBar.addSeparator();
        toolBar.add(actions.undo());
        toolBar.add(actions.redo());
        toolBar.addSeparator();
        for (JToggleButton button : new JToggleButton[]{selectButton, wireButton, panButton}) {
            toolButtons.add(button);
            toolBar.add(button);
        }
        toolBar.addSeparator();
        toolBar.add(actions.start());
        toolBar.add(actions.pause());
        toolBar.add(actions.step());
        toolBar.add(actions.createSpeedSelector());
        toolBar.addSeparator();
        toolBar.add(actions.zoomOut());
        toolBar.add(actions.zoomIn());
        toolBar.add(actions.centerView());
    }

    private void toolChanged(Tool tool) {
        switch (tool) {
            case SelectTool select -> selectButton.setSelected(true);
            case WireTool wire -> wireButton.setSelected(true);
            case PanTool pan -> panButton.setSelected(true);
            default -> toolButtons.clearSelection();
        }
        if (!(tool instanceof PlaceComponentTool)) {
            palette.clearSelection();
        }
    }
}
