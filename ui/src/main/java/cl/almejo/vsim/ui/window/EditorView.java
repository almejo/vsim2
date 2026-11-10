package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.InsideEditRefusedException;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.LibraryDirectory;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.actions.ContextMenuFactory;
import cl.almejo.vsim.ui.about.AboutPresenter;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.actions.KeyboardFocus;
import cl.almejo.vsim.ui.actions.SelectionActions;
import cl.almejo.vsim.ui.actions.ToolChoice;
import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasHost;
import cl.almejo.vsim.ui.canvas.CircuitBounds;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.canvas.ZoomTargets;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.fullscreen.EdgeBars;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.library.TemplateActions;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.MemoryPainter;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.panels.StatusBar;
import cl.almejo.vsim.ui.panels.ZoomControl;
import cl.almejo.vsim.ui.problems.ProblemsController;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.terminal.CapturedKeys;
import cl.almejo.vsim.ui.terminal.KeyboardCapture;
import cl.almejo.vsim.ui.terminal.KeyboardInspectorRows;
import cl.almejo.vsim.ui.tools.EnterableInstances;
import cl.almejo.vsim.ui.tools.PlaceComponentTool;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.window.panes.CanvasPanes;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
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
    /** The panes as of the last {@link #panesChanged}, to know which one closed. */
    private List<CircuitCanvas> knownPanes = List.of();
    /** What the window's tool choice makes for each pane; null while the panes use their default tool. */
    private Supplier<? extends Tool> toolChoice;
    private boolean spreadingDefaultTool;
    private final EditorActions actions;
    private KeyboardFocus keyboardFocus = KeyboardFocus.SWING;
    /** What the panels hold: always the current {@link #keyboardFocus}. */
    private final KeyboardFocus viewFocus = new KeyboardFocus() {
        @Override
        public Optional<java.awt.Component> owner() {
            return keyboardFocus.owner();
        }

        @Override
        public void request(java.awt.Component target) {
            keyboardFocus.request(target);
        }
    };
    private final SelectionActions selectionActions;
    private final PalettePanel palette;
    /** The left column: the palette alone, or {@link #addAbovePalette} over it as an accordion. */
    private final LeftColumn leftColumn;
    private final Map<String, Action> inspectorToggles = new LinkedHashMap<>();
    private final StatusBar statusBar = new StatusBar();
    private final ZoomControl zoomControl;
    private final SelectionMoves selectionMoves;
    private final InspectorPanel inspector = new InspectorPanel(viewFocus, () -> viewFocus.request(canvas()));
    private final CanvasArea canvasArea;
    private final BottomDock bottomDock;
    /** Canvas | right panel; the left column | this; both with the strip: rearranged by presentation mode. */
    private final JSplitPane inspectorSplit;
    private final JSplitPane split;
    private final JPanel splitAndStrip = new JPanel(new BorderLayout());
    /** The layer over the canvas area while the view is laid out for presentation mode; null otherwise. */
    private FullScreenStage fullScreenStage;
    private final Listeners<Runnable> fullScreenLayoutListeners = new Listeners<>();
    private final ProblemsController problems;
    private final JMenuBar menuBar;
    private final Map<MenuId, JMenu> menus = new EnumMap<>(MenuId.class);
    private final List<ContextMenuContributor> contextMenuContributors = new CopyOnWriteArrayList<>();
    private final Listeners<Runnable> tabShownListeners = new Listeners<>();
    private final Map<String, Action> shortcutActions = new LinkedHashMap<>();

    private final EditorToolBar editorToolBar;
    private final JToolBar toolBar;
    private final LabelFeatures labelFeatures;
    private final InlineTextEditor inlineEditor;
    /** Which part has the keyboard of this window (a Teclado after a click on it), shared by every pane. */
    private final KeyboardCapture keyboardCapture;
    private final CapturedKeys capturedKeys;
    private TemplateActions templateActions;
    private Optional<Consumer<ElementId>> instanceShapeEditor = Optional.empty();
    private final EditorServices services;
    private final Runnable appearanceListener;
    private final Runnable textFontListener;
    private final List<Runnable> extensionDisposers = new ArrayList<>();
    private Consumer<List<Path>> componentFileImporter;
    private PositionedFileImporter memoryFileImporter;
    private Consumer<Component> memoryEditor;
    private Consumer<Component> terminalEditor;
    private final WindowCommands windowCommands;
    private Consumer<TruthTableSpec> tableWizard;
    /** Keeps the «Sin valores» hint of a component tab in step with its context. */
    private final Runnable placeHintListener = this::showPlaceHint;
    /** Takes back the explanation of refused edits a component tab shows (none on the circuit's tab). */
    private Runnable removeRefusalListener = () -> { };
    private Consumer<List<Path>> circuitOpener = files -> {
    };

    public EditorView(OpenDocument openDocument, PainterRegistry painters, ColorScheme scheme, WindowCommands window) {
        this(openDocument, EditorServices.of(painters, scheme), window);
    }

    public EditorView(OpenDocument openDocument, EditorServices services, WindowCommands window) {
        super(new BorderLayout());
        this.openDocument = openDocument;
        this.keyboardCapture = new KeyboardCapture(openDocument.document());
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
        }, window, AboutPresenter.swing());
        actions.runStepsWith(new UndoInItsPlace(this));
        this.selectionActions = new SelectionActions(openDocument, this::canvas, selection, actions,
                FragmentClipboard.system());
        this.palette = new PalettePanel(openDocument.registry(), services.painters(), services.appearance(), entry -> {
            setToolOnEveryPane(() -> new PlaceComponentTool(entry.type(), entry.parameters()));
            canvas().requestFocusInWindow();
        });
        this.leftColumn = new LeftColumn(palette, services.preferences(), viewFocus, () -> viewFocus.request(canvas()));
        createInspectorToggles();

        this.menuBar = actions.createMenuBar();
        indexMenus();

        addLeftColumnItems();
        this.capturedKeys = new CapturedKeys(keyboardCapture, openDocument, new WindowShortcuts(),
                statusBar::showNotice);
        capturedKeys.useClipboard(FragmentClipboard.system()::readPlainText);
        this.zoomControl = new ZoomControl(new ActivePaneZoom(), new ZoomControl.Actions(actions.zoomOut(),
                actions.zoomIn(), actions.zoomReset(), actions.zoomToFit(), actions.zoomToSelection()));
        addAfter(menu(MenuId.VIEW), actions.zoomToSelection(), zoomControl.typeZoomAction());
        editorToolBar = new EditorToolBar(actions);
        toolBar = editorToolBar.toolBar();

        this.canvasArea = new CanvasArea(canvasPanes);
        this.bottomDock = new BottomDock(canvasArea);
        this.inspectorSplit = new InspectorSplit(bottomDock, inspector);
        this.split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftColumn, inspectorSplit);
        split.setDividerLocation(PALETTE_WIDTH);
        split.setContinuousLayout(true);
        split.setResizeWeight(0);
        // the strip of the right panel's sections stays on the edge when the panel closes, so it is outside the split
        splitAndStrip.add(split, BorderLayout.CENTER);
        splitAndStrip.add(inspector.strip(), BorderLayout.EAST);
        add(splitAndStrip, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        canvasPanes.addPanesListener(this::panesChanged);
        knownPanes = List.copyOf(canvasPanes.panes());
        canvasPanes.addActiveListener(this::activePaneChanged);
        selection.addListener(() -> statusBar.showSelection(selection.ids().size()));
        selection.addListener(this::releaseKeyboardIfDeselected);
        keyboardCapture.addListener(this::keyboardCaptureChanged);
        statusBar.showSnapshot(SimulationSnapshot.EMPTY);
        statusBar.setZoomControl(zoomControl);
        statusBar.showZoom(canvas.viewport().zoom());
        statusBar.onRunStateClicked(() -> performIfEnabled(actions.pause()),
                () -> performIfEnabled(actions.start()));
        inlineEditor = new InlineTextEditor(this::canvas);
        forEachPane(this::configurePane);
        forEachPane(pane -> pane.addDrawingListener(actions::refresh)); // Deshacer drops a corner while a wire is drawn
        forEachPane(pane -> pane.addDrawingListener(() -> releaseKeyboardWhileDrawing(pane))); // its keys are the path's
        bindZoomKeys();
        bindEscapeToTheTool();
        palette.setFocusAfterMouseChoice(() -> canvas().requestFocusInWindow());
        palette.useKeyboardFocus(viewFocus);
        palette.setEscapeAction(this::escapeToCanvas);
        toolChanged(canvas.tool());
        palette.setFileDropHandler(this::dropFiles);
        installSelectionActions();
        labelFeatures = LabelFeatures.install(this);
        labelFeatures.properties().addComponentRows(new KeyboardInspectorRows(openDocument));
        problems = new ProblemsController(this);
        if (openDocument.isBody()) {
            openDocument.addChangeListener(placeHintListener);
            removeRefusalListener = openDocument.addRefusalListener(this::explainRefusal);
            showPlaceHint();
        }
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

    /**
     * The user was taken away from this view (its tab was hidden, the AI started a task on its circuit): every pane's
     * tool cancels what must not survive it (a continuous wire's path). Returns the notice that says so («Cable
     * cancelado»), for the caller to show where the user looks now; empty when nothing was cancelled.
     */
    public Optional<String> interruptGestures() {
        Optional<String> notice = Optional.empty();
        for (CircuitCanvas pane : panes()) {
            Optional<String> paneNotice = pane.interruptGesture();
            if (notice.isEmpty()) {
                notice = paneNotice;
            }
        }
        return notice;
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

    /** Which part has this window's keyboard (decision 5): painters and the Terminal window read it. */
    public KeyboardCapture keyboardCapture() { return keyboardCapture; }

    /** Where the focus is and how the view's panels ask for it: Swing's, or the one {@link #useKeyboardFocus} gave. */
    public KeyboardFocus keyboardFocus() { return viewFocus; }

    /**
     * Runs {@code listener} each time this view's tab comes to the front of its window (the circuit tree follows the
     * tab); returns its removal.
     */
    public Runnable addTabShownListener(Runnable listener) {
        return tabShownListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** The window brought this view's tab to the front. */
    public void tabShown() {
        tabShownListeners.fire(Runnable::run);
    }

    /** Replaces Swing's focus for the view's panels (tests: nothing gets the focus headless). */
    public void useKeyboardFocus(KeyboardFocus focus) { keyboardFocus = Objects.requireNonNull(focus, "focus"); }

    /** The keys of this window while a part has its keyboard (the clipboard a paste reads is injectable). */
    public CapturedKeys capturedKeys() { return capturedKeys; }

    /** The template actions of this view, once the templates extension is installed. */
    public Optional<TemplateActions> templateActions() {
        return Optional.ofNullable(templateActions);
    }

    /** Set by the templates extension (and cleared with {@code null} when it is disposed). */
    public void setTemplateActions(TemplateActions actions) {
        this.templateActions = actions;
    }

    /**
     * Set by the appearance extension: opens «Cambiar forma…» of a piece of this view, as its context menu does, for the
     * panels that offer it too (the circuit tree). The runnable takes it back.
     */
    public Runnable setInstanceShapeEditor(Consumer<ElementId> editor) {
        instanceShapeEditor = Optional.of(editor);
        return () -> instanceShapeEditor = Optional.empty();
    }

    /** Opens «Cambiar forma…» of piece {@code instanceId}; false when no extension can (nothing happens then). */
    public boolean changeShapeOf(ElementId instanceId) {
        instanceShapeEditor.ifPresent(editor -> editor.accept(instanceId));
        return instanceShapeEditor.isPresent();
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
    /**
     * The toolbar this view builds; the window hangs the active tab's toolbar above its tab strip (spec §10.1), so the
     * view does not hold it itself.
     */
    public JToolBar toolBar() { return toolBar; }

    /** The menu {@code id} of this window's menu bar. */
    public JMenu menu(MenuId id) {
        JMenu menu = menus.get(id);
        if (menu == null) {
            throw new IllegalStateException("the menu bar has no " + id + " menu");
        }
        return menu;
    }

    /** Ver ▸ «Árbol del circuito» (Alt+1) and «Paleta» (Alt+2) first, checked while their panel is open. */
    private void addLeftColumnItems() {
        JMenu view = menu(MenuId.VIEW);
        view.insert(new JCheckBoxMenuItem(leftColumn.toggleAction(LeftColumn.Section.CIRCUIT)), 0);
        view.insert(new JCheckBoxMenuItem(leftColumn.toggleAction(LeftColumn.Section.PALETTE)), 1);
        int index = 2;
        for (Action toggle : inspectorToggles.values()) {
            view.insert(new JMenuItem(toggle), index++);
        }
        view.insertSeparator(index);
    }

    private void createInspectorToggles() {
        addInspectorToggle(InspectorPanel.PROPERTIES, "inspector.properties", KeyEvent.VK_3);
        addInspectorToggle(InspectorPanel.PROBLEMS, "problems.tab", KeyEvent.VK_4);
        addInspectorToggle(InspectorPanel.NOTES, "stickyNotes.tab", KeyEvent.VK_5);
        addInspectorToggle(InspectorPanel.MEMORY_MAP, "memory.map.tab", KeyEvent.VK_6);
    }

    private void addInspectorToggle(String id, String nameKey, int digit) {
        inspectorToggles.put(id, Actions.of(Messages.text(nameKey),
                KeyStroke.getKeyStroke(digit, InputEvent.ALT_DOWN_MASK),
                () -> inspector.toggleTab(id)));
    }

    /** The four toggles of the right panel's sections (their keys are the ones whose typed character is swallowed). */
    public List<Action> inspectorToggleActions() {
        return List.copyOf(inspectorToggles.values());
    }

    /**
     * What Alt+3 … Alt+6 and Ver ▸ Propiedades … Mapa de memoria do for the right panel's section {@code id}: open the
     * panel on it, switch to it, or close the panel when that section is the open one.
     */
    public Action inspectorToggleAction(String id) {
        return Objects.requireNonNull(inspectorToggles.get(id), id);
    }

    /** The left column: the palette, and the circuit tree above it as an accordion (spec §5.3). */
    public LeftColumn leftColumn() {
        return leftColumn;
    }

    /**
     * Shows {@code component} above the palette in the left column under the header {@code title} (the circuit tree,
     * spec §5.3); the two fold as an accordion and share a remembered divider ({@link LeftColumn}). The returned
     * runnable takes it away again, leaving the palette alone; calling it twice does nothing.
     */
    public Runnable addAbovePalette(String title, JComponent component) {
        return leftColumn.addAbovePalette(title, component);
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

    /**
     * Files an extension takes before they reach the circuit opener (memory files become new memories), with the
     * grid point they were dropped on when there is one.
     */
    public interface PositionedFileImporter {

        boolean accepts(Path file);

        /** Imports {@code files} (all accepted) at {@code position}, or where the extension chooses when empty. */
        void importFiles(List<Path> files, Optional<GridPoint> position);
    }

    /** Where memory files that are opened or dropped go; null (the default) sends them to the circuit opener. */
    public void setMemoryFileImporter(PositionedFileImporter importer) {
        memoryFileImporter = importer;
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
        openOrImportFiles(files, circuitOpener, Optional.empty());
    }

    /**
     * Files dropped on the window, the canvas or the palette: {@link #openOrImport} runs after the drop finishes,
     * because a dialog inside a drop transaction can hang the drag source.
     */
    public void dropFiles(List<Path> files) {
        SwingUtilities.invokeLater(() -> openOrImport(files));
    }

    /** Files dropped on a pane at {@code gridPosition}: like {@link #dropFiles}, memory files land there. */
    public void dropFilesAt(List<Path> files, GridPoint gridPosition) {
        SwingUtilities.invokeLater(() -> openOrImportFiles(files, circuitOpener, Optional.of(gridPosition)));
    }

    /** {@link #openOrImportFiles(List, Consumer, Optional)} without a drop point. */
    public void openOrImportFiles(List<Path> files, Consumer<List<Path>> circuitOpener) {
        openOrImportFiles(files, circuitOpener, Optional.empty());
    }

    /**
     * Files dropped on the window or the canvas: component files go to the component importer and memory files to
     * the memory importer, at {@code position} (when they are set), the others to {@code circuitOpener}. Each
     * receiver is called only when it gets at least one file.
     */
    public void openOrImportFiles(List<Path> files, Consumer<List<Path>> circuitOpener,
                                  Optional<GridPoint> position) {
        Consumer<List<Path>> importer = componentFileImporter;
        PositionedFileImporter memoryImporter = memoryFileImporter;
        List<Path> components = importer == null ? List.of()
                : files.stream().filter(LibraryDirectory::isComponentFile).toList();
        List<Path> memories = memoryImporter == null ? List.of()
                : files.stream().filter(file -> !components.contains(file) && memoryImporter.accepts(file)).toList();
        List<Path> circuits = files.stream()
                .filter(file -> !components.contains(file) && !memories.contains(file)).toList();
        if (!components.isEmpty()) {
            importer.accept(components);
        }
        if (!memories.isEmpty()) {
            memoryImporter.importFiles(memories, position);
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
        steps.add(() -> openDocument.removeChangeListener(placeHintListener));
        steps.add(removeRefusalListener);
        steps.add(statusBar::dispose);
        steps.add(palette::dispose);
        steps.add(problems::dispose);
        steps.add(labelFeatures::dispose);
        steps.add(inlineEditor::dispose);
        steps.add(actions::dispose);

        steps.add(keyboardCapture::dispose);
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

    /**
     * Enters the user component {@code instance} is an instance of (spec §4.4): the window opens or focuses its tab in
     * the circuit's group, with the values of this instance (its path under this place's context). With animated view
     * moves, the new tab starts where the instance is drawn here and grows to the whole body.
     * Built-in parts never enter.
     */
    @Override
    public void enterComponent(Component instance) {
        EnterableInstances.definitionOf(openDocument, instance).ifPresent(definitionId -> {
            OpenDocument circuit = openDocument.parent().orElse(openDocument);
            Optional<ViewState> from = Animations.viewMoves(services.preferences())
                    ? enteringStart(circuit, definitionId, instance) : Optional.empty();
            windowCommands.enter(circuit, definitionId, EnterableInstances.contextOf(openDocument, instance), from);
        });
    }

    /** Where the entered body's view starts: on screen where, and as big as, {@code instance} is drawn now. */
    private Optional<ViewState> enteringStart(OpenDocument circuit, String definitionId, Component instance) {
        Optional<Bounds> body = circuit.document().definition(definitionId)
                .flatMap(definition -> CircuitBounds.of(definition.bodyDocument(circuit.document()),
                        openDocument.registry()));
        Bounds box = ElementBounds.of(instance, openDocument.registry());
        return body.map(bodyBounds -> EnteringView.start(canvas().viewState(), box, bodyBounds));
    }

    @Override
    public void configure(Component component) {
        actions.configure(component);
    }

    @Override
    public void showNotice(String text) {
        statusBar.showNotice(text);
    }

    /** Only the active pane speaks for the window: a gesture phrase of another pane waits until it is active. */
    @Override
    public void gesturePhraseChanged(CircuitCanvas pane, String phrase) {
        if (pane == canvas()) {
            statusBar.showGesture(phrase);
        }
    }

    /** A component tab without an instance to show («Sin valores», spec §6.3) says where to choose one. */
    private void showPlaceHint() {
        boolean noValues = openDocument.isBody() && openDocument.contextPath().isEmpty();
        statusBar.showPlaceHint(noValues ? Messages.text("inside.noValuesHint") : "");
    }

    /**
     * An edit refused inside this component tab ({@link InsideEditRefusedException}): nothing changed, and the
     * status line says why instead of the edit failing silently (the place bar says it too, from its own listener).
     */
    private void explainRefusal(InsideEditRefusedException refusal) {
        statusBar.showNotice(PlaceBar.explanation(refusal));
    }

    @Override
    public void editLabel(DocumentElement element) {
        labelFeatures.actions().editLabel(element);
    }

    @Override
    public void addTextAt(GridPoint point) {
        labelFeatures.actions().addTextAt(point);
    }

    @Override
    public SelectionMoves selectionMoves() {
        return selectionMoves;
    }

    @Override
    public void editValue(Component component) {
        labelFeatures.actions().editValue(component);
    }

    /** Where the memory content editor extension plugs in: what a double click on a RAM or ROM opens. */
    public void setMemoryEditor(Consumer<Component> memoryEditor) {
        this.memoryEditor = Objects.requireNonNull(memoryEditor);
    }

    /** Undoes {@link #setMemoryEditor}: a double click on a memory configures it again. */
    public void clearMemoryEditor() {
        this.memoryEditor = null;
    }

    /** Where the Terminal extension plugs in: what a double click on a Terminal's screen opens (its window). */
    public void setTerminalEditor(Consumer<Component> terminalEditor) {
        this.terminalEditor = Objects.requireNonNull(terminalEditor);
    }

    /** Undoes {@link #setTerminalEditor}: a double click on a Terminal configures it again. */
    public void clearTerminalEditor() {
        this.terminalEditor = null;
    }

    /** Whether a double click on a Terminal's screen opens its window. */
    public boolean hasTerminalEditor() {
        return terminalEditor != null;
    }

    @Override
    public java.util.Optional<cl.almejo.vsim.core.document.ElementId> capturedPart() {
        return keyboardCapture.captured().map(cl.almejo.vsim.core.document.ElementId::new);
    }

    @Override
    public void captureKeyboard(Component component) {
        keyboardCapture.capture(component.id().value());
    }

    @Override
    public boolean takeKey(CircuitCanvas canvas, KeyEvent event) {
        return switch (event.getID()) {
            case KeyEvent.KEY_PRESSED -> capturedKeys.keyPressed(event);
            case KeyEvent.KEY_TYPED -> capturedKeys.keyTyped(event);
            default -> false;
        };
    }

    /** The keyboard went to another window or another component: the capture ends, unless it went to another pane. */
    @Override
    public void canvasFocusLost(CircuitCanvas canvas, Optional<java.awt.Component> opposite) {
        if (opposite.isEmpty() || !panes().contains(opposite.get())) {
            keyboardCapture.release();
        }
    }

    /** A click elsewhere (or any selection without the captured part alone) ends the keyboard capture. */
    private void releaseKeyboardIfDeselected() {
        keyboardCapture.captured()
                .filter(id -> !selection.ids().equals(Set.of(new ElementId(id))))
                .ifPresent(id -> keyboardCapture.release());
    }

    /**
     * A part took or gave back the keyboard: while it has it, Tab is a key for the part, not a focus move, and every
     * pane repaints (the part shows it is capturing).
     */
    private void keyboardCaptureChanged() {
        boolean capturing = keyboardCapture.captured().isPresent();
        for (CircuitCanvas pane : panes()) {
            pane.setFocusTraversalKeysEnabled(!capturing);
            pane.repaint();
        }
    }

    @Override
    public void editMemory(Component component) {
        if (memoryEditor == null) {
            configure(component);
        } else {
            memoryEditor.accept(component);
        }
    }

    @Override
    public void editTerminal(Component component) {
        if (terminalEditor == null) {
            configure(component);
        } else {
            terminalEditor.accept(component);
        }
    }

    /** The square the memory painter draws in the title bar of a RAM or ROM. */
    @Override
    public boolean memoryFoldToggleAt(Component component, Point2D gridPosition) {
        return MemoryTypes.kindOf(component.typeId()).isPresent() && MemoryPainter.isOnFoldToggle(
                openDocument.registry().typeOf(component), component, gridPosition);
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
                return (canvas.tool() != canvas.defaultTool() || canvas.drawsBetweenClicks()) && !canvas.isFocusOwner()
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
     * Esc in a tree of the left column: what Esc does to the canvas's tool wherever the focus is (a placement is
     * cancelled), and the canvas gets the focus.
     */
    public void escapeToCanvas() {
        CircuitCanvas canvas = canvas();
        if (canvas.tool() != canvas.defaultTool() || canvas.drawsBetweenClicks()) {
            canvas.pressKeyOnTool(KeyEvent.VK_ESCAPE, false);
        }
        viewFocus.request(canvas);
    }

    /**
     * Adds a toggle button for an extension's tool right after the Selección button; it shows as pressed while a tool of
     * {@code toolType} is active. Returns what takes the button back.
     */
    public Runnable addToolButton(Action action, Class<? extends Tool> toolType) {
        return editorToolBar.addToolButton(action, toolType);
    }

    /**
     * Adds a toggle button that is not a tool (it stays outside the tool group) right after the last tool button,
     * Zoom. Returns what takes it back.
     */
    public Runnable addToolbarToggleAfterTools(JToggleButton button) {
        return editorToolBar.addToolbarToggleAfterTools(button);
    }

    /**
     * Adds {@code component} to a toolbar slot (the capture split button after the file actions, the waveforms
     * toggle after the speed selector, the presentation toggles at the right edge), after those of the slot with the
     * same or a lower {@code order}, so the order inside a slot does not depend on install order. Returns what takes
     * it back.
     */
    public Runnable addToolbarComponent(JComponent component, ToolbarSlot slot, int order) {
        return editorToolBar.addComponent(component, slot, order);
    }

    /** Like {@link #addToolbarComponent(JComponent, ToolbarSlot, int)} with order 0. */
    public Runnable addToolbarComponent(JComponent component, ToolbarSlot slot) {
        return editorToolBar.addComponent(component, slot, 0);
    }

    /**
     * Lays this view out for presentation mode (its window enters it, or this tab shows while it is on): the canvas
     * area alone fills the view, the left column and the right panel with its strip go behind the left and right
     * screen edges ({@code edgeBars}), the status bar hides (it never shows in the mode). The left column keeps its
     * width and the right panel its own. Returns what puts every piece back where it was, dividers included.
     */
    public Runnable layOutForFullScreen(EdgeBars edgeBars) {
        Objects.requireNonNull(edgeBars, "edgeBars");
        if (fullScreenStage != null) {
            throw new IllegalStateException("the view is already laid out for presentation mode");
        }
        int paletteWidth = split.getDividerLocation();
        int inspectorDivider = inspectorSplit.getDividerLocation();
        JPanel leftBar = edgeBar(EdgeBars.Edge.LEFT, () -> paletteWidth);
        leftBar.add(leftColumn, BorderLayout.CENTER);
        JPanel rightBar = edgeBar(EdgeBars.Edge.RIGHT, () -> inspector.strip().getPreferredSize().width
                + (inspector.isOpen() ? inspector.width() : 0));
        rightBar.add(inspector, BorderLayout.CENTER);
        rightBar.add(inspector.strip(), BorderLayout.EAST);
        remove(splitAndStrip);
        statusBar.setVisible(false);
        FullScreenStage stage = new FullScreenStage(bottomDock, edgeBars::relayout);
        stage.add(leftBar, JLayeredPane.PALETTE_LAYER);
        stage.add(rightBar, JLayeredPane.PALETTE_LAYER);
        add(stage, BorderLayout.CENTER);
        fullScreenStage = stage;
        Runnable unregisterLeft = edgeBars.register(EdgeBars.Edge.LEFT, leftBar, () -> false);
        Runnable unregisterRight = edgeBars.register(EdgeBars.Edge.RIGHT, rightBar, () -> false);
        Runnable stopFollowingTheInspector = inspector.onStateChange(edgeBars::relayout);
        revalidate();
        repaint();
        fullScreenLayoutListeners.fire(Runnable::run);
        return () -> {
            stopFollowingTheInspector.run();
            unregisterRight.run();
            unregisterLeft.run();
            remove(stage);
            stage.removeAll();
            fullScreenStage = null;
            split.setLeftComponent(leftColumn);
            inspectorSplit.setLeftComponent(bottomDock);
            inspectorSplit.setRightComponent(inspector);
            splitAndStrip.add(inspector.strip(), BorderLayout.EAST);
            add(splitAndStrip, BorderLayout.CENTER);
            statusBar.setVisible(true);
            split.setDividerLocation(paletteWidth);
            inspectorSplit.setDividerLocation(inspectorDivider);
            revalidate();
            repaint();
            fullScreenLayoutListeners.fire(Runnable::run);
        };
    }

    /** A bar for presentation mode: opaque, as wide as {@code width} says, with a line on its inner side. */
    private static JPanel edgeBar(EdgeBars.Edge edge, IntSupplier width) {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(width.getAsInt(), super.getPreferredSize().height);
            }
        };
        bar.setOpaque(true);
        bar.setBorder(EdgeBars.innerLine(edge));
        return bar;
    }

    /**
     * The layer over the canvas area while this view is laid out for presentation mode, where the bars behind the
     * edges live (task 5 puts the waveform panel there); empty otherwise.
     */
    public Optional<JLayeredPane> fullScreenStage() {
        return Optional.ofNullable(fullScreenStage);
    }

    /** {@code listener} hears this view being laid out for presentation mode and back. Returns what stops it. */
    public Runnable addFullScreenLayoutListener(Runnable listener) {
        return fullScreenLayoutListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** The canvas area filling the view in presentation mode, the bars over it placed by {@link EdgeBars}. */
    private static final class FullScreenStage extends JLayeredPane {

        private final java.awt.Component content;
        private final Runnable placeBars;

        FullScreenStage(java.awt.Component content, Runnable placeBars) {
            this.content = content;
            this.placeBars = placeBars;
            add(content, JLayeredPane.DEFAULT_LAYER);
        }

        @Override
        public void doLayout() {
            content.setBounds(0, 0, getWidth(), getHeight());
            placeBars.run();
        }
    }

    /** The toolbar's button for tools of {@code toolType} added with {@link #addToolButton}, if any. */
    public Optional<JToggleButton> toolButton(Class<? extends Tool> toolType) {
        return editorToolBar.toolButton(toolType);
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

    /** What this view sets on every pane: view moves, animations, file drops, key bindings, status bar, the tool. */
    private Runnable configurePane(CircuitCanvas pane) {
        pane.setAnimateViewMoves(() -> Animations.viewMoves(services.preferences()));
        pane.setAnimations(() -> Animations.enabled(services.preferences()));
        Runnable stopFollowingAnimations = repaintWhenAnimationsChange(pane);
        pane.setFileDropTarget(this::dropFilesAt);
        pane.setFocusTraversalKeysEnabled(keyboardCapture.captured().isEmpty());
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
                statusBar.showPauseNotice(openDocument.latestPauseNotice());
                statusBar.showSpeed(openDocument.achievedSpeed());
                actions.refreshSimulationButtons(snapshot.running());
            }
        };
        pane.addSnapshotListener(snapshotListener);
        return () -> {
            inlineEditor.cancelIfOpenIn(pane); // the edit's field lives on the pane that goes away
            stopFollowingAnimations.run();
            removeToolListener.run();
            removeViewListener.run();
            pane.removeSnapshotListener(snapshotListener);
            bound.forEach(pane::unbindKey);
        };
    }

    /**
     * Repaints {@code pane} when Preferencias ▸ «Animaciones» changes, so what moves on it (a Terminal's cursor, a
     * Matriz LED's afterglow) stops or starts at once instead of at its next repaint.
     */
    private Runnable repaintWhenAnimationsChange(CircuitCanvas pane) {
        return Animations.onChange(services.preferences(), pane::animationsChanged);
    }

    private static void bindKey(CircuitCanvas pane, Map<KeyStroke, String> bound, KeyStroke stroke, String name,
                                Action action) {
        pane.bindKey(stroke, name, action);
        bound.put(stroke, name);
    }

    private void releaseKeyboardWhileDrawing(CircuitCanvas pane) {
        if (pane.drawsBetweenClicks()) {
            keyboardCapture.release();
        }
    }

    /**
     * A pane switched tools. Only the active pane speaks for the window: the toolbar shows its tool, and when it goes
     * back to its default tool (Escape, a placement done) the window's tool choice ends on every pane.
     */
    private void paneToolChanged(CircuitCanvas pane, Tool tool) {
        if (tool != pane.defaultTool()) {
            keyboardCapture.release(); // another tool: the keys are the editor's again
        }
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
        statusBar.showGesture(pane.gesturePhrase());
        toolChanged(pane.tool());
    }

    /** Panes came or went: the {@link #forEachPane} installations follow (the undoing in reverse order first). */
    private void panesChanged(List<CircuitCanvas> panes) {
        for (CircuitCanvas known : knownPanes) {
            if (!panes.contains(known)) { // a closed pane that held a wire path takes it away: say so
                known.interruptGesture().ifPresent(statusBar::showNotice);
            }
        }
        knownPanes = List.copyOf(panes);
        actions.refresh();
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
        editorToolBar.showTool(tool);
        if (!tool.placesFromPalette()) {
            palette.clearSelection();
        }
    }

    /**
     * The shortcuts of this window a captured key must leave alone (decision 5): every menu accelerator, and every key
     * binding Swing would run from the focused component (its own, then its ancestors').
     */
    private final class WindowShortcuts implements CapturedKeys.Shortcuts {

        @Override
        public boolean isBound(KeyStroke stroke, java.awt.Component focused) {
            return isMenuAccelerator(menuBar, stroke) || isKeyBinding(stroke, focused);
        }

        @Override
        public boolean isPaste(KeyStroke stroke) {
            return stroke.equals(selectionActions.paste().getValue(Action.ACCELERATOR_KEY));
        }

        private static boolean isMenuAccelerator(MenuElement element, KeyStroke stroke) {
            if (element instanceof JMenuItem item && !(item instanceof JMenu) && stroke.equals(item.getAccelerator())) {
                return true;
            }
            for (MenuElement child : element.getSubElements()) {
                if (isMenuAccelerator(child, stroke)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isKeyBinding(KeyStroke stroke, java.awt.Component focused) {
            if (focused instanceof JComponent component && component.getInputMap(WHEN_FOCUSED).get(stroke) != null) {
                return true;
            }
            for (java.awt.Component ancestor = focused; ancestor != null; ancestor = ancestor.getParent()) {
                if (ancestor instanceof JComponent component
                        && component.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(stroke) != null) {
                    return true;
                }
            }
            return false;
        }
    }
}
