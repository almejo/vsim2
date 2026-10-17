package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.io.LibraryDirectory;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.actions.ContextMenuFactory;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.actions.SelectionActions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasHost;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.library.TemplateActions;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.panels.StatusBar;
import cl.almejo.vsim.ui.problems.ProblemsController;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.PlaceComponentTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.WireTool;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Content of an editor window: toolbar, palette, canvas, inspector and status bar, wired to one {@link OpenDocument}.
 * Feature code adds menu items through {@link #menu(MenuId)}, inspector tabs through {@link #inspector()} and
 * context-menu items through {@link #addContextMenuContributor}, from an {@link EditorExtension}.
 */
public final class EditorView extends JPanel implements CanvasHost {

    private static final int PALETTE_WIDTH = 230;

    private final OpenDocument openDocument;
    private final CircuitCanvas canvas;
    private final EditorActions actions;
    private final SelectionActions selectionActions;
    private final PalettePanel palette;
    private final StatusBar statusBar = new StatusBar();
    private final SelectionMoves selectionMoves;
    private final InspectorPanel inspector = new InspectorPanel();
    private final BottomDock bottomDock;
    private final ProblemsController problems;
    private final JMenuBar menuBar;
    private final Map<MenuId, JMenu> menus = new EnumMap<>(MenuId.class);
    private final List<ContextMenuContributor> contextMenuContributors = new CopyOnWriteArrayList<>();
    private final Map<String, Action> shortcutActions = new LinkedHashMap<>();
    private final JToolBar toolBar = new JToolBar();
    private final ButtonGroup toolButtons = new ButtonGroup();
    private final JToggleButton selectButton;
    private final JToggleButton wireButton;
    private final JToggleButton panButton;
    private final Map<Class<? extends Tool>, JToggleButton> extraToolButtons = new LinkedHashMap<>();
    private final LabelFeatures labelFeatures;
    private final InlineTextEditor inlineEditor;
    private TemplateActions templateActions;
    private final EditorServices services;
    private final Runnable appearanceListener;
    private final Runnable textFontListener;
    private final List<Runnable> extensionDisposers = new ArrayList<>();
    private Consumer<List<Path>> componentFileImporter;
    private Consumer<List<Path>> circuitOpener = files -> {
    };

    public EditorView(OpenDocument openDocument, PainterRegistry painters, ColorScheme scheme, WindowCommands window) {
        this(openDocument, EditorServices.of(painters, scheme), window);
    }

    public EditorView(OpenDocument openDocument, EditorServices services, WindowCommands window) {
        super(new BorderLayout());
        this.openDocument = openDocument;
        this.services = services;
        CircuitRenderer renderer = new CircuitRenderer(openDocument.registry(), services.painters(),
                services.appearance());
        this.selectionMoves = new SelectionMoves(services.preferences(), statusBar::showNotice);
        this.canvas = new CircuitCanvas(openDocument, renderer, this);
        this.actions = new EditorActions(openDocument, canvas, window);
        this.selectionActions = new SelectionActions(openDocument, canvas, actions, FragmentClipboard.system());
        this.palette = new PalettePanel(openDocument.registry(), services.painters(), services.appearance(), type -> {
            canvas.setTool(new PlaceComponentTool(type));
            canvas.requestFocusInWindow();
        });
        this.menuBar = actions.createMenuBar();
        indexMenus();
        selectButton = new JToggleButton(actions.selectTool());
        wireButton = new JToggleButton(actions.wireTool());
        panButton = new JToggleButton(actions.panTool());
        buildToolBar();

        this.bottomDock = new BottomDock(canvas);
        JSplitPane inspectorSplit = new InspectorSplit(bottomDock, inspector);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, palette, inspectorSplit);
        split.setDividerLocation(PALETTE_WIDTH);
        split.setContinuousLayout(true);
        split.setResizeWeight(0);
        add(toolBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        canvas.addToolListener(this::toolChanged);
        canvas.addSnapshotListener(statusBar::showSnapshot);
        canvas.addViewListener(() -> statusBar.showZoom(canvas.viewport().zoom()));
        canvas.selection().addListener(() -> statusBar.showSelection(canvas.selection().ids().size()));
        statusBar.showSnapshot(SimulationSnapshot.EMPTY);
        statusBar.showZoom(canvas.viewport().zoom());
        statusBar.onZoomClicked(canvas::resetZoom);
        statusBar.onRunStateClicked(() -> performIfEnabled(actions.pause()),
                () -> performIfEnabled(actions.start()));
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete", actions.delete());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete", actions.delete());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "rotateClockwise", actions.rotateClockwise());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK), "rotateCounterClockwise",
                actions.rotateCounterClockwise());
        actions.moveActions().forEach((id, action) -> ShortcutCatalog.find(id)
                .flatMap(entry -> entry.defaultStroke()).ifPresent(stroke -> canvas.bindKey(stroke, id, action)));
        bindZoomKeys();
        toolChanged(canvas.tool());
        canvas.setFileDropHandler(this::dropFiles);
        palette.setFileDropHandler(this::dropFiles);
        installSelectionActions();
        inlineEditor = new InlineTextEditor(canvas);
        labelFeatures = LabelFeatures.install(this);
        problems = new ProblemsController(this);
        appearanceListener = canvas::repaint;
        services.appearance().addListener(appearanceListener);
        textFontListener = canvas::repaint;
        TextFonts.addListener(textFontListener);
        try {
            for (EditorExtension extension : services.extensions()) {
                extensionDisposers.add(extension.install(this));
            }
        } catch (RuntimeException exception) {
            try {
                dispose(); // undoes the extensions installed so far and leaves no listener on shared objects
            } catch (RuntimeException disposeFailure) {
                exception.addSuppressed(disposeFailure);
            }
            throw exception;
        }
    }

    /** Edit menu entries (platform shortcuts, so text fields keep their own Ctrl+X/C/V/A) and context menu items. */
    private void installSelectionActions() {
        JMenu edit = menu(MenuId.EDIT);
        edit.addSeparator();
        edit.add(selectionActions.cut());
        edit.add(selectionActions.copy());
        edit.add(selectionActions.paste());
        edit.add(selectionActions.duplicate());
        edit.addSeparator();
        edit.add(selectionActions.selectAll());
        addContextMenuContributor(selectionActions.contributor());
    }

    public OpenDocument openDocument() { return openDocument; }
    public EditorServices services() { return services; }
    public CircuitCanvas canvas() { return canvas; }
    public EditorActions actions() { return actions; }
    public SelectionActions selectionActions() { return selectionActions; }
    public PalettePanel palette() { return palette; }
    public StatusBar statusBar() { return statusBar; }
    public InspectorPanel inspector() { return inspector; }
    public BottomDock bottomDock() { return bottomDock; }
    public LabelFeatures labelFeatures() { return labelFeatures; }

    /** The editor that writes texts right over the canvas (labels, titles, the Constante value). */
    public InlineTextEditor inlineEditor() { return inlineEditor; }

    /** The template actions of this view, once the templates extension is installed. */
    public Optional<TemplateActions> templateActions() {
        return Optional.ofNullable(templateActions);
    }

    /** Set by the templates extension (and cleared with {@code null} when it is disposed). */
    public void setTemplateActions(TemplateActions actions) {
        this.templateActions = actions;
    }
    public ProblemsController problems() { return problems; }
    public JMenuBar menuBar() { return menuBar; }
    public JToolBar toolBar() { return toolBar; }

    /** The menu {@code id} of this window's menu bar. */
    public JMenu menu(MenuId id) {
        JMenu menu = menus.get(id);
        if (menu == null) {
            throw new IllegalStateException("the menu bar has no " + id + " menu");
        }
        return menu;
    }

    /** Adds items to every context menu from now on; the returned runnable takes the contributor back. */
    public Runnable addContextMenuContributor(ContextMenuContributor contributor) {
        contextMenuContributors.add(contributor);
        return () -> contextMenuContributors.remove(contributor);
    }

    /**
     * Offers {@code action} to the keyboard shortcut editor under catalog id {@code id} (M6 contract C3), for actions
     * an extension creates itself; returns what takes it back (call it from the extension's disposer).
     */
    public Runnable registerShortcutAction(String id, Action action) {
        Objects.requireNonNull(action, "action");
        if (shortcutActions.putIfAbsent(id, action) != null) {
            throw new IllegalArgumentException("a shortcut action is already registered as " + id);
        }
        return () -> shortcutActions.remove(id, action);
    }

    /** The actions registered with {@link #registerShortcutAction}, in registration order (read-only). */
    public Map<String, Action> shortcutActions() {
        return Collections.unmodifiableMap(shortcutActions);
    }

    /**
     * Builds the right-click menu for {@code target} (the element under the mouse) clicked at {@code gridPosition}
     * (grid units) without showing it.
     */
    public JPopupMenu contextMenu(Optional<DocumentElement> target, Point2D gridPosition) {
        return ContextMenuFactory.create(target, actions, canvas.selection(), gridPosition, contextMenuContributors);
    }

    /** Where dropped component files ({@code .vsimcomponent.json}) go; null (the default) when nothing imports them. */
    public void setComponentFileImporter(Consumer<List<Path>> importer) {
        componentFileImporter = importer;
    }

    /** Where {@link #openOrImport} sends circuit files (a window: open them); by default they are ignored. */
    public void setCircuitOpener(Consumer<List<Path>> opener) {
        circuitOpener = Objects.requireNonNull(opener);
    }

    /**
     * Files chosen in Archivo ▸ Abrir, given on the command line or dropped: component files are imported into the
     * library (when an importer is set), the others go to the circuit opener.
     */
    public void openOrImport(List<Path> files) {
        openOrImportFiles(files, circuitOpener);
    }

    /**
     * Files dropped on the window, the canvas or the palette: {@link #openOrImport} runs after the drop finishes,
     * because a dialog inside a drop transaction can hang the drag source.
     */
    public void dropFiles(List<Path> files) {
        SwingUtilities.invokeLater(() -> openOrImport(files));
    }

    /**
     * Files dropped on the window or the canvas: component files go to the component importer (when one is set),
     * the others to {@code circuitOpener}. Each receiver is called only when it gets at least one file.
     */
    public void openOrImportFiles(List<Path> files, Consumer<List<Path>> circuitOpener) {
        Consumer<List<Path>> importer = componentFileImporter;
        List<Path> components = importer == null ? List.of()
                : files.stream().filter(LibraryDirectory::isComponentFile).toList();
        List<Path> circuits = files.stream().filter(file -> !components.contains(file)).toList();
        if (!components.isEmpty()) {
            importer.accept(components);
        }
        if (!circuits.isEmpty()) {
            circuitOpener.accept(circuits);
        }
    }

    /**
     * Undoes the extensions (in reverse order) and releases this view. Every step runs even when an earlier one
     * throws; the first failure is rethrown at the end with the later ones attached as suppressed exceptions.
     */
    public void dispose() {
        List<Runnable> steps = new ArrayList<>();
        for (int index = extensionDisposers.size() - 1; index >= 0; index--) {
            steps.add(extensionDisposers.get(index));
        }
        extensionDisposers.clear();
        steps.add(() -> services.appearance().removeListener(appearanceListener));
        steps.add(() -> TextFonts.removeListener(textFontListener));
        steps.add(statusBar::dispose);
        steps.add(palette::dispose);
        steps.add(problems::dispose);
        steps.add(labelFeatures::dispose);
        steps.add(inlineEditor::dispose);
        steps.add(actions::dispose);
        steps.add(canvas::dispose);
        RuntimeException failure = null;
        for (Runnable step : steps) {
            try {
                step.run();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target, Point2D screenPosition) {
        Point2D gridPosition = canvas.viewport().screenToGrid(screenPosition.getX(), screenPosition.getY());
        contextMenu(target, gridPosition).show(source, (int) screenPosition.getX(), (int) screenPosition.getY());
    }

    @Override
    public void configure(Component component) {
        actions.configure(component);
    }

    @Override
    public void editLabel(DocumentElement element) {
        labelFeatures.actions().editLabel(element);
    }

    @Override
    public SelectionMoves selectionMoves() {
        return selectionMoves;
    }

    @Override
    public void editValue(Component component) {
        labelFeatures.actions().editValue(component);
    }

    @Override
    public void editValue(Component component, Value startingValue) {
        labelFeatures.actions().editInputValue(component, startingValue);
    }

    private void indexMenus() {
        for (int index = 0; index < menuBar.getMenuCount(); index++) {
            JMenu menu = menuBar.getMenu(index);
            if (menu.getClientProperty(MenuId.class) instanceof MenuId id) {
                menus.put(id, menu);
            }
        }
    }

    /** Menu accelerators allow one keystroke each; these cover "+" on non-US layouts and the keypad. */
    private void bindZoomKeys() {
        int mask = ShortcutCatalog.menuMask();
        var keys = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, mask), "zoomIn");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, mask), "zoomIn");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, mask | InputEvent.SHIFT_DOWN_MASK), "zoomIn");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, mask), "zoomOut");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, mask), "zoomReset");
        getActionMap().put("zoomIn", actions.zoomIn());
        getActionMap().put("zoomOut", actions.zoomOut());
        getActionMap().put("zoomReset", actions.zoomReset());
    }

    /**
     * Adds a toggle button for an extension's tool right after the Cable button; it shows as pressed while a tool of
     * {@code toolType} is active. Returns what takes the button back.
     */
    public Runnable addToolButton(Action action, Class<? extends Tool> toolType) {
        JToggleButton button = new JToggleButton(action);
        toolButtons.add(button);
        extraToolButtons.put(toolType, button);
        toolBar.add(button, indexOfToolButton(wireButton) + 1);
        toolBar.revalidate();
        return () -> {
            extraToolButtons.remove(toolType, button);
            toolButtons.remove(button);
            toolBar.remove(button);
            toolBar.revalidate();
        };
    }

    /**
     * Adds a toggle button that is not a tool (it stays outside the tool group) right after the last tool button,
     * Mover vista. Returns what takes it back.
     */
    public Runnable addToolbarToggleAfterTools(JToggleButton button) {
        toolBar.add(button, indexOfToolButton(panButton) + 1);
        toolBar.revalidate();
        return () -> {
            toolBar.remove(button);
            toolBar.revalidate();
        };
    }

    /** The toolbar's button for tools of {@code toolType} added with {@link #addToolButton}, if any. */
    public Optional<JToggleButton> toolButton(Class<? extends Tool> toolType) {
        return Optional.ofNullable(extraToolButtons.get(toolType));
    }

    private int indexOfToolButton(JToggleButton button) {
        for (int index = 0; index < toolBar.getComponentCount(); index++) {
            if (toolBar.getComponent(index) == button) {
                return index;
            }
        }
        return toolBar.getComponentCount() - 1;
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
        toolBar.add(actions.restart());
        toolBar.add(actions.step());
        JComboBox<Double> speed = actions.createSpeedSelector();
        JLabel speedIcon = new JLabel(Icons.get(EditorActions.SPEED_ICON, Icons.MENU_SIZE));
        speedIcon.setToolTipText(speed.getToolTipText());
        speedIcon.setLabelFor(speed);
        toolBar.add(speedIcon);
        toolBar.add(speed);
        toolBar.addSeparator();
        toolBar.add(actions.zoomOut());
        toolBar.add(actions.zoomIn());
        toolBar.add(actions.centerView());
    }

    /** Runs {@code action} as its toolbar button would: only while it is enabled (the status-bar run state). */
    private void performIfEnabled(Action action) {
        if (action.isEnabled()) {
            action.actionPerformed(new ActionEvent(statusBar, ActionEvent.ACTION_PERFORMED, ""));
        }
    }

    private void toolChanged(Tool tool) {
        switch (tool) {
            case SelectTool select -> selectButton.setSelected(true);
            case WireTool wire -> wireButton.setSelected(true);
            case PanTool pan -> panButton.setSelected(true);
            default -> {
                JToggleButton extra = extraToolButtons.get(tool.getClass());
                if (extra != null) {
                    extra.setSelected(true);
                } else {
                    toolButtons.clearSelection();
                }
            }
        }
        if (!tool.placesFromPalette()) {
            palette.clearSelection();
        }
    }
}
