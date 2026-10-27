package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.io.LibraryDirectory;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.actions.ContextMenuFactory;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.actions.SelectionActions;
import cl.almejo.vsim.ui.actions.ToolChoice;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasHost;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.canvas.ZoomTargets;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.library.TemplateActions;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.panels.StatusBar;
import cl.almejo.vsim.ui.panels.ZoomControl;
import cl.almejo.vsim.ui.problems.ProblemsController;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.tools.PlaceComponentTool;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.window.panes.CanvasPanes;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Content of an editor window: toolbar, palette, canvas, inspector and status bar, wired to one {@link OpenDocument}.
 * Feature code adds menu items through {@link #menu(MenuId)}, inspector tabs through {@link #inspector()} and
 * context-menu items through {@link #addContextMenuContributor}, from an {@link EditorExtension}.
 */
public final class EditorView extends JPanel implements CanvasHost {

    private static final int PALETTE_WIDTH = 230;

    private final OpenDocument openDocument;
    /** The one selection of this window, shown and changed by every pane. */
    private final Selection selection = new Selection();
    private final CanvasPanes canvasPanes;
    private final List<PaneInstallation> paneInstallations = new ArrayList<>();
    /** What the window's tool choice makes for each pane; null while the panes use their default tool. */
    private Supplier<? extends Tool> toolChoice;
    private boolean spreadingDefaultTool;
    private final EditorActions actions;
    private final SelectionActions selectionActions;
    private final PalettePanel palette;
    private final StatusBar statusBar = new StatusBar();
    private final ZoomControl zoomControl;
    private final SelectionMoves selectionMoves;
    private final InspectorPanel inspector = new InspectorPanel();
    private final CanvasArea canvasArea;
    private final BottomDock bottomDock;
    private final ProblemsController problems;
    private final JMenuBar menuBar;
    private final Map<MenuId, JMenu> menus = new EnumMap<>(MenuId.class);
    private final List<ContextMenuContributor> contextMenuContributors = new CopyOnWriteArrayList<>();
    private final Map<String, Action> shortcutActions = new LinkedHashMap<>();
    private final EditorToolBar toolBarBuilder;
    private final JToolBar toolBar;
    private final LabelFeatures labelFeatures;
    private final InlineTextEditor inlineEditor;
    private TemplateActions templateActions;
    private final EditorServices services;
    private final Runnable appearanceListener;
    private final Runnable textFontListener;
    private final List<Runnable> extensionDisposers = new ArrayList<>();
    private Consumer<List<Path>> componentFileImporter;
    private final WindowCommands windowCommands;
    private Consumer<TruthTableSpec> tableWizard;
    private Consumer<List<Path>> circuitOpener = files -> {
    };

    public EditorView(OpenDocument openDocument, PainterRegistry painters, ColorScheme scheme, WindowCommands window) {
        this(openDocument, EditorServices.of(painters, scheme), window);
    }

    public EditorView(OpenDocument openDocument, EditorServices services, WindowCommands window) {
        super(new BorderLayout());
        this.openDocument = openDocument;
        this.services = services;
        this.windowCommands = window;
        this.selectionMoves = new SelectionMoves(services.preferences(), statusBar::showNotice);
        CircuitCanvas canvas = newPane();
        this.canvasPanes = new CanvasPanes(this::newPane, canvas);
        this.actions = new EditorActions(openDocument, this::canvas, selection, new ToolChoice() {
            @Override
            public void choose(Supplier<? extends Tool> newTool) {
                setToolOnEveryPane(newTool);
            }

            @Override
            public void useDefault() {
                useDefaultToolOnEveryPane();
            }
        }, window);
        this.selectionActions = new SelectionActions(openDocument, this::canvas, selection, actions,
                FragmentClipboard.system());
        this.palette = new PalettePanel(openDocument.registry(), services.painters(), services.appearance(), type -> {
            setToolOnEveryPane(() -> new PlaceComponentTool(type));
            canvas().requestFocusInWindow();
        });
        this.menuBar = actions.createMenuBar();
        indexMenus();
        this.zoomControl = new ZoomControl(new ActivePaneZoom(), new ZoomControl.Actions(actions.zoomOut(),
                actions.zoomIn(), actions.zoomReset(), actions.zoomToFit(), actions.zoomToSelection()));
        addAfter(menu(MenuId.VIEW), actions.zoomToSelection(), zoomControl.typeZoomAction());
        toolBarBuilder = new EditorToolBar(actions);
        toolBar = toolBarBuilder.toolBar();

        this.canvasArea = new CanvasArea(canvasPanes);
        this.bottomDock = new BottomDock(canvasArea);
        JSplitPane inspectorSplit = new InspectorSplit(bottomDock, inspector);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, palette, inspectorSplit);
        split.setDividerLocation(PALETTE_WIDTH);
        split.setContinuousLayout(true);
        split.setResizeWeight(0);
        add(toolBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        canvasPanes.addPanesListener(this::panesChanged);
        canvasPanes.addActiveListener(this::activePaneChanged);
        selection.addListener(() -> statusBar.showSelection(selection.ids().size()));
        statusBar.showSnapshot(SimulationSnapshot.EMPTY);
        statusBar.setZoomControl(zoomControl);
        statusBar.showZoom(canvas.viewport().zoom());
        statusBar.onRunStateClicked(() -> performIfEnabled(actions.pause()),
                () -> performIfEnabled(actions.start()));
        inlineEditor = new InlineTextEditor(this::canvas);
        forEachPane(this::configurePane);
        bindZoomKeys();
        bindEscapeToTheTool();
        palette.setFocusAfterMouseChoice(() -> canvas().requestFocusInWindow());
        toolChanged(canvas.tool());
        palette.setFileDropHandler(this::dropFiles);
        installSelectionActions();
        labelFeatures = LabelFeatures.install(this);
        problems = new ProblemsController(this);
        appearanceListener = this::repaintPanes;
        services.appearance().addListener(appearanceListener);
        textFontListener = this::repaintPanes;
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
    /**
     * The active pane: the one the user last pressed or focused (the only one while the window is not split).
     * Actions that read or move the view resolve it when they run, never once at install.
     */
    public CircuitCanvas canvas() { return canvasPanes.active(); }

    /** Every pane of this window, in reading order. */
    public List<CircuitCanvas> panes() {
        return canvasPanes.panes();
    }

    /** The panes of this window and their dividers (splitting, removing, the layout to remember). */
    public CanvasPanes canvasPanes() {
        return canvasPanes;
    }

    /** The one selection of this window: every pane shows and changes it (separate windows have their own). */
    public Selection selection() {
        return selection;
    }

    /** Runs {@code listener} whenever another pane becomes the active one. Returns its removal. */
    public Runnable addActivePaneListener(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        return canvasPanes.addActiveListener(pane -> listener.run());
    }

    /**
     * Runs {@code install} on every pane now and on every pane created later; when a pane is removed, the runnable
     * {@code install} returned for it runs (before the pane is disposed). The returned runnable undoes the
     * installation on every pane still there; calling it again does nothing.
     */
    public Runnable forEachPane(Function<CircuitCanvas, Runnable> install) {
        PaneInstallation installation = new PaneInstallation(Objects.requireNonNull(install, "install"));
        paneInstallations.add(installation);
        panes().forEach(installation::installOn);
        return () -> {
            if (paneInstallations.remove(installation)) {
                installation.undoAll();
            }
        };
    }

    /**
     * The window's tool choice: every pane gets its own tool from {@code newTool} (a gesture in one pane never touches
     * another), and so do the panes created later, until the active pane goes back to its default tool.
     */
    public void setToolOnEveryPane(Supplier<? extends Tool> newTool) {
        Objects.requireNonNull(newTool, "newTool");
        toolChoice = newTool;
        for (CircuitCanvas pane : panes()) {
            pane.setTool(newTool.get());
        }
    }

    /** Puts every pane back on its default tool (Elegir) and ends the window's tool choice. */
    public void useDefaultToolOnEveryPane() {
        toolChoice = null;
        spreadingDefaultTool = true;
        try {
            panes().forEach(CircuitCanvas::useDefaultTool);
        } finally {
            spreadingDefaultTool = false;
        }
    }
    public EditorActions actions() { return actions; }
    public SelectionActions selectionActions() { return selectionActions; }
    public PalettePanel palette() { return palette; }
    public StatusBar statusBar() { return statusBar; }

    /** The zoom control of the status bar: it shows and drives the active pane (and holds «Escribir el zoom…»). */
    public ZoomControl zoomControl() { return zoomControl; }
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

    /** The file and window operations of the window this view is in (a do-nothing stand-in in tests). */
    public WindowCommands windowCommands() {
        return windowCommands;
    }

    /** Opens the Karnaugh wizard on a table, when the Karnaugh extension is installed (the truth-table window uses it). */
    public Optional<Consumer<TruthTableSpec>> tableWizard() {
        return Optional.ofNullable(tableWizard);
    }

    /** Set by the Karnaugh extension (and cleared with {@code null} when it is disposed). */
    public void setTableWizard(Consumer<TruthTableSpec> opener) {
        this.tableWizard = opener;
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

    /**
     * Shows {@code header} between the toolbar and the canvas, as wide as the canvas (the find bar): it pushes the
     * canvas down, and the circuit stays where it was on screen. The returned runnable takes it away again.
     */
    public Runnable addAboveCanvas(JComponent header) {
        canvasArea.add(header, BorderLayout.NORTH);
        canvasArea.layOutNow();
        return () -> {
            if (header.getParent() == canvasArea) {
                canvasArea.remove(header);
                canvasArea.layOutNow();
            }
        };
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
        return ContextMenuFactory.create(target, actions, selection, gridPosition, contextMenuContributors);
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
        steps.add(this::undoPaneInstallations);
        steps.add(canvasPanes::dispose);
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
        Point2D gridPosition = source.viewport().screenToGrid(screenPosition.getX(), screenPosition.getY());
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

    /** Puts {@code action} into {@code menu} right after the item of {@code anchor}. */
    private static void addAfter(JMenu menu, Action anchor, Action action) {
        for (int index = 0; index < menu.getItemCount(); index++) {
            JMenuItem item = menu.getItem(index);
            if (item != null && item.getAction() == anchor) {
                menu.insert(action, index + 1);
                return;
            }
        }
        throw new IllegalStateException("no menu item for " + anchor.getValue(Action.NAME));
    }

    /**
     * What the zoom control drives: the active pane, resolved on every call (spec §7: the control follows the pane
     * the user last pressed or focused). The slider zooms at once; a typed or picked value travels.
     */
    private final class ActivePaneZoom implements ZoomControl.Target {

        @Override
        public double zoom() {
            return canvas().viewport().zoom();
        }

        @Override
        public void zoomAroundCenter(double zoom) {
            canvas().zoomAroundCenter(zoom);
        }

        @Override
        public void zoomTo(double zoom) {
            CircuitCanvas pane = canvas();
            pane.zoomTo(ZoomTargets.atZoom(pane.viewState(), zoom));
        }

        @Override
        public void returnFocus() {
            canvas().requestFocusInWindow();
        }
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
     * Escape reaches the canvas tool wherever the focus is in this view (bug 2026-10-18: after a click in the palette
     * the focus stayed there and a placement could not be cancelled). Components that use Escape themselves (the
     * inline editor, a table or combo box, an open menu) get it first; with the default tool it is left alone.
     */
    private void bindEscapeToTheTool() {
        String actionKey = "escapeToTheTool";
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), actionKey);
        getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public boolean isEnabled() {
                // the canvas's own key handler already gave the key to the tool
                CircuitCanvas canvas = canvas();
                return canvas.tool() != canvas.defaultTool() && !canvas.isFocusOwner()
                        && MenuSelectionManager.defaultManager().getSelectedPath().length == 0;
            }

            @Override
            public void actionPerformed(ActionEvent event) {
                if (isEnabled()) {
                    canvas().pressKeyOnTool(KeyEvent.VK_ESCAPE, false);
                }
            }
        });
    }

    /**
     * Adds a toggle button for an extension's tool right after the Cable button; it shows as pressed while a tool of
     * {@code toolType} is active. Returns what takes the button back.
     */
    public Runnable addToolButton(Action action, Class<? extends Tool> toolType) {
        return toolBarBuilder.addToolButton(action, toolType);
    }

    /**
     * Adds a toggle button that is not a tool (it stays outside the tool group) right after the last tool button,
     * Zoom. Returns what takes it back.
     */
    public Runnable addToolbarToggleAfterTools(JToggleButton button) {
        return toolBarBuilder.addToolbarToggleAfterTools(button);
    }

    /** The toolbar's button for tools of {@code toolType} added with {@link #addToolButton}, if any. */
    public Optional<JToggleButton> toolButton(Class<? extends Tool> toolType) {
        return toolBarBuilder.toolButton(toolType);
    }

    /**
     * The canvas with what {@link #addAboveCanvas} puts over it. When the canvas's top edge moves (a header shows or
     * goes), the view pans by the same amount in the other direction, in the same layout pass, so the circuit does not
     * jump on screen. Adding or removing a header lays the area out at once ({@link #layOutNow}), so whatever runs next
     * (the find bar searching and travelling to a match) already sees the canvas at its new size and place.
     */
    private static final class CanvasArea extends JPanel {

        private final CanvasPanes panes;

        CanvasArea(CanvasPanes panes) {
            super(new BorderLayout());
            this.panes = panes;
            add(panes, BorderLayout.CENTER);
        }

        /** Every pane that was laid out keeps its circuit in place on screen when its top edge moves. */
        @Override
        public void doLayout() {
            Map<CircuitCanvas, Integer> topsBefore = new IdentityHashMap<>();
            for (CircuitCanvas pane : panes.panes()) {
                if (pane.getWidth() > 0 && pane.getHeight() > 0) {
                    topsBefore.put(pane, panes.getY() + pane.getY());
                }
            }
            super.doLayout();
            if (panes.getWidth() > 0 && panes.getHeight() > 0) {
                panes.doLayout(); // in the same pass, so each pane is where the compensation measures it
            }
            topsBefore.forEach((pane, topBefore) -> {
                int shift = panes.getY() + pane.getY() - topBefore;
                if (shift != 0) {
                    pane.compensateCanvasMove(0, -shift);
                }
            });
        }

        /**
         * Lays out the header and the canvas now (also without a window: tests) when the area already has a size; Swing
         * validates as usual afterwards.
         */
        void layOutNow() {
            revalidate();
            if (getWidth() > 0 && getHeight() > 0) {
                doLayout();
            }
            repaint();
        }
    }

    /** Runs {@code action} as its toolbar button would: only while it is enabled (the status-bar run state). */
    private void performIfEnabled(Action action) {
        if (action.isEnabled()) {
            action.actionPerformed(new ActionEvent(statusBar, ActionEvent.ACTION_PERFORMED, ""));
        }
    }

    /** A new pane's canvas: same document, a renderer of its own, the window's selection. */
    private CircuitCanvas newPane() {
        CircuitRenderer renderer = new CircuitRenderer(openDocument.registry(), services.painters(),
                services.appearance());
        return new CircuitCanvas(openDocument, renderer, this, selection);
    }

    /** What this view sets on every pane: view moves, file drops, its key bindings, the status bar, the tool. */
    private Runnable configurePane(CircuitCanvas pane) {
        pane.setAnimateViewMoves(services.preferences()::animateViewMoves);
        pane.setFileDropHandler(this::dropFiles);
        Map<KeyStroke, String> bound = new LinkedHashMap<>();
        bindKey(pane, bound, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete", actions.delete());
        bindKey(pane, bound, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete", actions.delete());
        bindKey(pane, bound, KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "rotateClockwise", actions.rotateClockwise());
        bindKey(pane, bound, KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK),
                "rotateCounterClockwise", actions.rotateCounterClockwise());
        Map<String, Action> canvasKeys = new LinkedHashMap<>(actions.moveActions());
        canvasKeys.put("view.zoomTool", actions.zoomTool());
        canvasKeys.put("view.zoomToFit", actions.zoomToFit());
        canvasKeys.put("view.zoomToSelection", actions.zoomToSelection());
        canvasKeys.forEach((id, action) -> ShortcutCatalog.find(id).flatMap(entry -> entry.defaultStroke())
                .ifPresent(stroke -> bindKey(pane, bound, stroke, id, action)));
        if (toolChoice != null) {
            pane.setTool(toolChoice.get());
        }
        Runnable removeToolListener = pane.addToolListener(tool -> paneToolChanged(pane, tool));
        Runnable removeViewListener = pane.addViewListener(() -> {
            if (pane == canvas()) {
                statusBar.showZoom(pane.viewport().zoom());
            }
        });
        Consumer<SimulationSnapshot> snapshotListener = snapshot -> {
            if (pane == canvas()) {
                statusBar.showSnapshot(snapshot);
                actions.refreshSimulationButtons(snapshot.running());
            }
        };
        pane.addSnapshotListener(snapshotListener);
        return () -> {
            inlineEditor.cancelIfOpenIn(pane); // the edit's field lives on the pane that goes away
            removeToolListener.run();
            removeViewListener.run();
            pane.removeSnapshotListener(snapshotListener);
            bound.forEach(pane::unbindKey);
        };
    }

    private static void bindKey(CircuitCanvas pane, Map<KeyStroke, String> bound, KeyStroke stroke, String name,
                                Action action) {
        pane.bindKey(stroke, name, action);
        bound.put(stroke, name);
    }

    /**
     * A pane switched tools. Only the active pane speaks for the window: the toolbar shows its tool, and when it goes
     * back to its default tool (Escape, a placement done) the window's tool choice ends on every pane.
     */
    private void paneToolChanged(CircuitCanvas pane, Tool tool) {
        if (pane != canvas()) {
            return;
        }
        if (tool == pane.defaultTool() && !spreadingDefaultTool) {
            toolChoice = null;
            spreadingDefaultTool = true;
            try {
                for (CircuitCanvas other : panes()) {
                    if (other != pane && other.tool() != other.defaultTool()) {
                        other.useDefaultTool();
                    }
                }
            } finally {
                spreadingDefaultTool = false;
            }
        }
        toolChanged(tool);
    }

    private void activePaneChanged(CircuitCanvas pane) {
        statusBar.showZoom(pane.viewport().zoom());
        toolChanged(pane.tool());
    }

    /** Panes came or went: the {@link #forEachPane} installations follow (the undoing in reverse order first). */
    private void panesChanged(List<CircuitCanvas> panes) {
        for (int index = paneInstallations.size() - 1; index >= 0; index--) {
            paneInstallations.get(index).undoExcept(panes);
        }
        for (PaneInstallation installation : List.copyOf(paneInstallations)) {
            panes.forEach(installation::installOn);
        }
    }

    private void undoPaneInstallations() {
        while (!paneInstallations.isEmpty()) {
            paneInstallations.removeLast().undoAll();
        }
    }

    private void repaintPanes() {
        panes().forEach(CircuitCanvas::repaint);
    }

    private void toolChanged(Tool tool) {
        toolBarBuilder.showTool(tool);
        if (!tool.placesFromPalette()) {
            palette.clearSelection();
        }
    }
}
