package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.simulation.SimulationSpeeds;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.command.HistoryListener;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.RotateElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.ConstantFormatChange;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.about.AboutInformation;
import cl.almejo.vsim.ui.about.AboutPresenter;
import cl.almejo.vsim.ui.about.LinkOpener;
import cl.almejo.vsim.ui.about.ProblemReportLink;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitBounds;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.ConfigureDialog;
import cl.almejo.vsim.ui.panels.PinJoinRefusal;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.ZoomTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tunnel.TunnelNames;
import cl.almejo.vsim.ui.waveform.TimeAxis;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;

import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Every user command of an editor window, as Swing actions whose enabled state follows the editor. */
public final class EditorActions {

    /** The speed selector's choices in units per millisecond; the infinite one runs as fast as the engine can. */
    public static final List<Double> SPEEDS = SimulationSpeeds.AVAILABLE;

    /** The icon before the speed selector in the toolbar, by file name under {@code icons/} (M6.1-E). */
    public static final String SPEED_ICON = "speed";

    private final OpenDocument openDocument;
    /** The active pane, resolved whenever an action runs (the window may be split). */
    private final Supplier<CircuitCanvas> activePane;
    private final Selection selection;
    private final int menuMask = ShortcutCatalog.menuMask();
    private final HistoryListener historyListener = history -> refresh();
    /** How far «Avanzar N t» goes (M6.1-B: the preference, followed by {@code FixedStepFeatures}). */
    private long stepUnits = AppPreferences.DEFAULT_STEP_UNITS;

    private final Action newDocument;
    private final Action open;
    private final Action save;
    private final Action saveAs;
    private final Action closeTab;
    private final Action closeWindow;
    private final Action exit;
    private final Action undo;
    private StepRunner steps;
    private final Action redo;
    private final Action delete;
    private final List<Consumer<Set<ElementId>>> deleteListeners = new CopyOnWriteArrayList<>();
    /** Grid units a Mayús+arrow moves the selection. */
    private static final int FAR_NUDGE = 5;

    private final Map<String, Action> moveActions = new LinkedHashMap<>();
    private final Action rotateClockwise;
    private final Action rotateCounterClockwise;
    private final Action configure;
    private final Action selectTool;
    private final Action panTool;
    private final Action zoomTool;
    private final Action zoomIn;
    private final Action zoomOut;
    private final Action zoomReset;
    private final Action zoomToFit;
    private final Action zoomToSelection;
    private final Action centerView;
    private final Action start;
    private final Action pause;
    private final Action restart;
    private final Action startOrPause;
    private final Action step;
    private final Action newView;
    private final Action newWindowWithCircuit;
    private final Action moveGroup;
    private final Action foldGroup;
    private final Action nextTab;
    private final Action previousTab;
    private final Action closeComponents;
    private final Action about;
    private final Action reportProblem;

    /**
     * The actions of one window: {@code activePane} (its active pane) is resolved each time an action runs (zoom, centre,
     * the Configure dialog's owner, the nudges' tool check), {@code selection} is the window's one selection and the
     * tool actions go through {@code toolChoice} to every pane. The window feeds {@link #refreshSimulationButtons}.
     */
    public EditorActions(OpenDocument openDocument, Supplier<CircuitCanvas> activePane, Selection selection,
                         ToolChoice toolChoice, WindowCommands window, AboutPresenter aboutPresenter) {
        this(openDocument, activePane, selection, toolChoice, window, aboutPresenter, LinkOpener.desktop(),
                AboutInformation::current);
    }

    /** As above, with the browser and the installation data «Reportar un problema…» uses injected (tests). */
    public EditorActions(OpenDocument openDocument, Supplier<CircuitCanvas> activePane, Selection selection,
                         ToolChoice toolChoice, WindowCommands window, AboutPresenter aboutPresenter,
                         LinkOpener links, Supplier<AboutInformation> information) {
        this.openDocument = openDocument;
        this.steps = new StepRunner() {
            @Override
            public void undo() {
                openDocument.history().undo();
            }

            @Override
            public void redo() {
                openDocument.history().redo();
            }
        };
        this.activePane = activePane;
        this.selection = selection;
        newDocument = Actions.of(Messages.text("action.new"), shortcut(KeyEvent.VK_N, 0), window::newDocument);
        open = Actions.of(Messages.text("action.open"), shortcut(KeyEvent.VK_O, 0), window::open);
        save = Actions.of(Messages.text("action.save"), shortcut(KeyEvent.VK_S, 0), window::save);
        saveAs = Actions.of(Messages.text("action.saveAs"), shortcut(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK),
                window::saveAs);
        closeTab = Actions.of(Messages.text("action.closeTab"), shortcut(KeyEvent.VK_W, 0), window::closeTab);
        closeWindow = Actions.of(Messages.text("action.closeWindow"),
                shortcut(KeyEvent.VK_W, InputEvent.SHIFT_DOWN_MASK), window::closeWindow);
        exit = Actions.of(Messages.text("action.exit"), shortcut(KeyEvent.VK_Q, 0), window::exit);
        undo = Actions.of(Messages.text("action.undo"), shortcut(KeyEvent.VK_Z, 0), this::undoStep);
        redo = Actions.of(Messages.text("action.redo"), shortcut(KeyEvent.VK_Z, InputEvent.SHIFT_DOWN_MASK),
                () -> steps.redo());
        delete = Actions.of(Messages.text("action.delete"), null, this::deleteSelection);
        delete.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.delete"));
        rotateClockwise = Actions.of(Messages.text("action.rotateClockwise"), null, () -> rotateSelection(true));
        rotateClockwise.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.rotateClockwise"));
        rotateCounterClockwise = Actions.of(Messages.text("action.rotateCounterClockwise"), null,
                () -> rotateSelection(false));
        rotateCounterClockwise.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.rotateCounterClockwise"));
        addMoveActions("edit.moveLeft", "action.moveLeft", -1, 0);
        addMoveActions("edit.moveRight", "action.moveRight", 1, 0);
        addMoveActions("edit.moveUp", "action.moveUp", 0, -1);
        addMoveActions("edit.moveDown", "action.moveDown", 0, 1);
        addFarMoveActions();
        configure = Actions.of(Messages.text("action.configure"), null,
                () -> singleConfigurable().ifPresent(this::configure));
        selectTool = Actions.of(Messages.text("action.toolSelect"), null, toolChoice::useDefault);
        panTool = Actions.of(Messages.text("action.toolPan"), null, () -> toolChoice.choose(PanTool::new));
        zoomTool = Actions.of(Messages.text("action.toolZoom"), null, () -> toolChoice.choose(ZoomTool::new));
        zoomTool.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.toolZoom"));
        zoomIn = Actions.of(Messages.text("action.zoomIn"), shortcut(KeyEvent.VK_EQUALS, 0), 
                () -> activePane.get().zoomIn());
        zoomOut = Actions.of(Messages.text("action.zoomOut"), shortcut(KeyEvent.VK_MINUS, 0),
                () -> activePane.get().zoomOut());
        zoomReset = Actions.of(Messages.text("action.zoomReset"), shortcut(KeyEvent.VK_0, 0),
                () -> activePane.get().resetZoom());
        zoomToFit = Actions.of(Messages.text("action.zoomToFit"), null, this::zoomToCircuit);
        zoomToFit.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.zoomToFit"));
        zoomToSelection = Actions.of(Messages.text("action.zoomToSelection"), null, this::zoomToSelected);
        zoomToSelection.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.zoomToSelection"));
        centerView = Actions.of(Messages.text("action.centerView"), null, () -> activePane.get().centerCircuit());
        start = Actions.of(Messages.text("action.start"), null, openDocument::resume);
        pause = Actions.of(Messages.text("action.pause"), null, openDocument::pause);
        startOrPause = Actions.of(Messages.text("action.startOrPause"), KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0),
                () -> {
                    if (pause.isEnabled()) {
                        openDocument.pause();
                    } else {
                        openDocument.resume();
                    }
                });
        restart = Actions.of(Messages.text("action.restart"), shortcut(KeyEvent.VK_F5, 0), openDocument::restart);
        step = Actions.of(Messages.text("action.step"), KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0),
                () -> openDocument.step(stepUnits));
        setStepUnits(stepUnits);
        newView = Actions.of(Messages.text("action.newWindow"), null, window::newView);
        newWindowWithCircuit = Actions.of(Messages.text("action.newWindowWithCircuit"),
                shortcut(KeyEvent.VK_N, InputEvent.SHIFT_DOWN_MASK), window::newWindowWithCircuit);
        moveGroup = Actions.of(Messages.text("action.moveGroup"), null, window::moveGroupToNewWindow);
        foldGroup = Actions.of(Messages.text("action.foldGroup"), null, window::toggleFold);
        // Ctrl on every platform: ⌘+Tab is the system's application switcher on macOS
        nextTab = Actions.of(Messages.text("action.nextTab"),
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK), window::nextTab);
        previousTab = Actions.of(Messages.text("action.previousTab"),
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                window::previousTab);
        closeComponents = Actions.of(Messages.text("action.closeComponents"), null, window::closeComponents);
        about = Actions.of(Messages.text("action.about"), null, () -> aboutPresenter.show(activePane.get()));
        reportProblem = Actions.of(Messages.text("action.reportProblem"), null,
                () -> links.open(ProblemReportLink.url(information.get(), Messages.locale())));

        openDocument.history().addListener(historyListener);
        selection.addListener(this::refresh);
        refresh();
        refreshSimulationButtons(true);
    }

    public Action newDocument() { return newDocument; }
    public Action open() { return open; }
    public Action save() { return save; }
    public Action saveAs() { return saveAs; }
    public Action closeWindow() { return closeWindow; }
    public Action exit() { return exit; }
    /** «Nueva ventana con este circuito» (Ctrl+Mayús+N). */
    public Action newWindowWithCircuit() { return newWindowWithCircuit; }
    /** «Mover el grupo a una ventana nueva». */
    public Action moveGroup() { return moveGroup; }
    /** «Plegar / desplegar el grupo». */
    public Action foldGroup() { return foldGroup; }
    /** «Pestaña siguiente» (Ctrl+Tab). */
    public Action nextTab() { return nextTab; }
    /** «Pestaña anterior» (Ctrl+Mayús+Tab). */
    public Action previousTab() { return previousTab; }
    /** «Cerrar pestaña» (Ctrl+W), in Archivo and in Ventana. */
    public Action closeTab() { return closeTab; }
    /** «Cerrar los componentes de este circuito». */
    public Action closeComponents() { return closeComponents; }
    /**
     * What Deshacer / Rehacer run: by default the history's step, here; the view gives one that shows the place of a
     * step made in another tab first ({@code UndoInItsPlace}).
     */
    public interface StepRunner {
        void undo();

        void redo();
    }

    /** Deshacer and Rehacer run their step through {@code runner} from now on. */
    public void runStepsWith(StepRunner runner) {
        steps = Objects.requireNonNull(runner, "runner");
    }

    public Action undo() { return undo; }
    public Action redo() { return redo; }
    public Action delete() { return delete; }

    /**
     * Adds a listener told the ids «Borrar» is about to remove, while they are still in the document (M6.3: the
     * deleted-block notice). The returned runnable removes it.
     */
    public Runnable addDeleteListener(Consumer<Set<ElementId>> listener) {
        deleteListeners.add(listener);
        return () -> deleteListeners.remove(listener);
    }
    /** The arrow-key moves of the selection by their catalog id ({@code edit.moveLeft}, {@code edit.moveLeftFar}, …). */
    public Map<String, Action> moveActions() { return moveActions; }
    public Action rotateClockwise() { return rotateClockwise; }
    public Action rotateCounterClockwise() { return rotateCounterClockwise; }
    public Action configure() { return configure; }
    public Action selectTool() { return selectTool; }
    public Action panTool() { return panTool; }
    /** «Zoom»: the zoom tool on every pane (a rectangle fills the pane, a click steps in, Alt/Mayús+click out). */
    public Action zoomTool() { return zoomTool; }
    public Action zoomIn() { return zoomIn; }
    public Action zoomOut() { return zoomOut; }
    public Action zoomReset() { return zoomReset; }
    /** «Ver todo el circuito»: the active pane zooms until the whole circuit fills it; an empty circuit stays put. */
    public Action zoomToFit() { return zoomToFit; }
    /** «Zoom a la selección»: the active pane zooms until the selection fills it; enabled while something is selected. */
    public Action zoomToSelection() { return zoomToSelection; }
    public Action centerView() { return centerView; }
    public Action start() { return start; }
    public Action pause() { return pause; }
    /** «Reiniciar» (M6.1-B): always enabled. */
    public Action restart() { return restart; }
    /** F5 (M6.1-B): pauses a running simulation and starts a paused one, like play/pause on a player. */
    public Action startOrPause() { return startOrPause; }
    public Action step() { return step; }
    public Action newView() { return newView; }
    public Action about() { return about; }

    public Action reportProblem() { return reportProblem; }

    /**
     * The size of the fixed step (M6.1-B, spec §4.4): «Avanzar 100 t» and «Avanza la simulación 100 unidades de
     * tiempo», the number grouped like times («1 000»).
     */
    public void setStepUnits(long units) {
        if (units < 1) {
            throw new IllegalArgumentException("the step must be at least 1 time unit: " + units);
        }
        stepUnits = units;
        String formatted = TimeAxis.format(units);
        step.putValue(Action.NAME, Messages.format("action.stepBy", formatted));
        step.putValue(Action.SHORT_DESCRIPTION, Messages.format("tooltip.step", formatted));
    }

    public long stepUnits() {
        return stepUnits;
    }

    /**
     * Opens the Configure dialog for {@code component}; an accepted change becomes one undo step, with the tunnel
     * rename rules when a tunnel's name changes and a Constante's bits kept when only its format changes (the same
     * change as the inspector's).
     */
    public void configure(Component component) {
        ComponentType type = openDocument.registry().typeOf(component);
        if (type.parameters().isEmpty()) {
            return;
        }
        ConfigureDialog.show(activePane.get(), type, component, activePane.get()::scheme,
                        values -> PinJoinRefusal.of(openDocument, component.id(), values))
                .ifPresent(values -> TunnelNames.applyParameters(openDocument, component.id(), type,
                        ConstantFormatChange.keepingTheBits(type.id(), type.resolveParameters(component.parameters()),
                                type.resolveParameters(values))));
    }

    /** Stops following the shared document: the window showing these actions closed, the document may live on. */
    public void dispose() {
        openDocument.history().removeListener(historyListener);
    }

    public JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = tagged(new JMenu(Messages.text("menu.file")), MenuId.FILE);
        file.add(newDocument);
        file.add(open);
        file.add(save);
        file.add(saveAs);
        file.addSeparator();
        file.add(closeTab);
        file.add(closeWindow);
        file.add(exit);
        JMenu edit = tagged(new JMenu(Messages.text("menu.edit")), MenuId.EDIT);
        edit.add(undo);
        edit.add(redo);
        edit.addSeparator();
        edit.add(delete);
        edit.add(rotateClockwise);
        edit.add(rotateCounterClockwise);
        edit.add(configure);
        JMenu view = tagged(new JMenu(Messages.text("menu.view")), MenuId.VIEW);
        view.add(selectTool);
        view.add(panTool);
        view.add(zoomTool);
        view.addSeparator();
        view.add(zoomIn);
        view.add(zoomOut);
        view.add(zoomReset);
        view.add(zoomToFit);
        view.add(zoomToSelection);
        view.add(centerView);
        JMenu simulation = tagged(new JMenu(Messages.text("menu.simulation")), MenuId.SIMULATION);
        simulation.add(start);
        simulation.add(pause);
        simulation.add(startOrPause);
        simulation.add(restart);
        simulation.add(step);
        JMenu tools = tagged(new JMenu(Messages.text("menu.tools")), MenuId.TOOLS); // filled by extensions
        JMenu windowMenu = tagged(new JMenu(Messages.text("menu.window")), MenuId.WINDOW);
        windowMenu.add(newView);
        windowMenu.add(newWindowWithCircuit);
        windowMenu.add(moveGroup);
        windowMenu.add(foldGroup);
        windowMenu.addSeparator();
        windowMenu.add(nextTab);
        windowMenu.add(previousTab);
        windowMenu.add(closeTab);
        windowMenu.add(closeComponents);
        windowMenu.addSeparator();
        // «Dividir ▸» and «Siguiente panel» follow (SplitViewsFeatures)
        JMenu help = tagged(new JMenu(Messages.text("menu.help")), MenuId.HELP);
        help.add(about);
        help.add(reportProblem);
        menuBar.add(file);
        menuBar.add(edit);
        menuBar.add(view);
        menuBar.add(simulation);
        menuBar.add(tools);
        menuBar.add(windowMenu);
        menuBar.add(help);
        return menuBar;
    }

    /** Marks {@code menu} with its {@link MenuId} so {@code EditorView.menu(id)} can find it. */
    private static JMenu tagged(JMenu menu, MenuId id) {
        menu.putClientProperty(MenuId.class, id);
        return menu;
    }

    public JComboBox<Double> createSpeedSelector() {
        JComboBox<Double> selector = new JComboBox<>(SPEEDS.toArray(Double[]::new)) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return getPreferredSize(); // a toolbar would otherwise stretch it over all the free width
            }
        };
        selector.setSelectedItem(openDocument.simulationSpeed());
        selector.setToolTipText(Messages.text("toolbar.speedTip"));
        selector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                   boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setText(value instanceof Double speed ? speedLabel(speed, index < 0) : "");
                return this;
            }
        });
        selector.addActionListener(event -> openDocument.setSpeed((Double) selector.getSelectedItem()));
        return selector;
    }

    /** «×10» in the open list and «Velocidad ×10» when closed; the infinite speed reads «Máxima». */
    private static String speedLabel(Double speed, boolean closed) {
        if (speed.isInfinite()) {
            return Messages.text(closed ? "speed.maximumClosed" : "speed.maximum");
        }
        return Messages.format(closed ? "speed.closed" : "speed.label", speed);
    }

    /**
     * «Deshacer agregar», or «Deshacer agregar (en Ram)» for a step made inside a component (spec §10.4), whichever tab
     * shows the item: the text says where the step goes back before the view goes there.
     */
    private static String stepName(Command command, String namedKey, String insideKey) {
        String name = Messages.commandName(command.description());
        return OpenDocument.insideEditOf(command)
                .map(inside -> Messages.format(insideKey, name, inside.placeName()))
                .orElseGet(() -> Messages.format(namedKey, name));
    }

    /**
     * Deshacer (Ctrl+Z, the button, the menu): while a tool of the window draws between clicks it drops that tool's last
     * step (a continuous wire's last corner) and never undoes an earlier change; otherwise the history's step.
     */
    private void undoStep() {
        CircuitCanvas pane = activePane.get();
        if (pane != null && pane.dropLastToolStep()) {
            return;
        }
        steps.undo();
    }

    /** True while a tool of the active pane's window draws between clicks (a continuous wire's path). */
    private boolean drawsBetweenClicks() {
        CircuitCanvas pane = activePane.get();
        return pane != null && pane.drawsBetweenClicks();
    }

    public void refresh() {
        History history = openDocument.history();
        save.setEnabled(openDocument.canSave());
        boolean dropsACorner = drawsBetweenClicks();
        undo.setEnabled(dropsACorner || history.canUndo());
        // while a wire is drawn Deshacer drops its last corner, not the history's step its name would announce
        undo.putValue(Action.NAME, dropsACorner ? Messages.text("action.undo") : history.undoCommand()
                .map(command -> stepName(command, "action.undoNamed", "action.undoInside"))
                .orElse(Messages.text("action.undo")));
        // an older step must not come back under the path being drawn, as Deshacer never undoes one then
        redo.setEnabled(!dropsACorner && history.canRedo());
        redo.putValue(Action.NAME, history.redoCommand()
                .map(command -> stepName(command, "action.redoNamed", "action.redoInside"))
                .orElse(Messages.text("action.redo")));
        Set<ElementId> selected = selection.ids();
        // while a path is drawn the keys that act on the selection do nothing
        boolean actsOnSelection = !selected.isEmpty() && !dropsACorner;
        delete.setEnabled(actsOnSelection);
        rotateClockwise.setEnabled(actsOnSelection);
        rotateCounterClockwise.setEnabled(actsOnSelection);
        moveActions.values().forEach(move -> move.setEnabled(actsOnSelection));
        zoomToSelection.setEnabled(!selected.isEmpty());
        configure.setEnabled(singleConfigurable().isPresent());
    }

    public void refreshSimulationButtons(boolean running) {
        start.setEnabled(!running);
        pause.setEnabled(running);
        step.setEnabled(!running);
    }

    private void deleteSelection() {
        Set<ElementId> selected = selection.effectiveIds();
        if (!selected.isEmpty()) {
            deleteListeners.forEach(listener -> listener.accept(selected));
            openDocument.execute(new RemoveElementsCommand(selected));
        }
    }

    private void addMoveActions(String id, String nameKey, int unitX, int unitY) {
        moveActions.put(id, Actions.of(Messages.text(nameKey), null, () -> nudgeSelection(unitX, unitY)));
    }

    private void addFarMoveActions() {
        addMoveActions("edit.moveLeftFar", "action.moveLeftFar", -FAR_NUDGE, 0);
        addMoveActions("edit.moveRightFar", "action.moveRightFar", FAR_NUDGE, 0);
        addMoveActions("edit.moveUpFar", "action.moveUpFar", 0, -FAR_NUDGE);
        addMoveActions("edit.moveDownFar", "action.moveDownFar", 0, FAR_NUDGE);
    }

    /**
     * Moves the selection as a drag of the select tool would (same expanded ids; the wires follow by the follow mode,
     * M6.7), except that a run of these moves is one undo step. Nothing happens while the select tool is not the active tool or is in the middle
     * of a gesture (a drag, a wire, a rubber band).
     */
    private void nudgeSelection(int deltaX, int deltaY) {
        CircuitCanvas pane = activePane.get();
        if (selection.isEmpty() || !(pane.tool() instanceof SelectTool select) || !select.isIdle()) {
            return;
        }
        pane.selectionMoves().nudge(openDocument, selection, selection.effectiveIds(), deltaX, deltaY);
    }

    private void zoomToCircuit() {
        CircuitCanvas pane = activePane.get();
        CircuitBounds.of(openDocument.document(), openDocument.registry()).ifPresent(pane::zoomToFit);
    }

    private void zoomToSelected() {
        CircuitCanvas pane = activePane.get();
        List<DocumentElement> selected = selection.ids().stream()
                .map(id -> openDocument.document().element(id))
                .flatMap(Optional::stream)
                .toList();
        DrawnBounds.of(selected, openDocument.registry()).ifPresent(pane::zoomToFit);
    }

    private void rotateSelection(boolean clockwise) {
        Set<ElementId> selected = selection.effectiveIds();
        RotateElementsCommand rotate = new RotateElementsCommand(selected, clockwise, openDocument.registry());
        if (rotate.changes(openDocument.document())) {
            openDocument.execute(rotate);
        }
    }

    private List<Component> selectedComponents() {
        return selection.ids().stream()
                .map(id -> openDocument.document().component(id))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Component> singleConfigurable() {
        List<Component> components = selectedComponents();
        if (components.size() != 1 || selection.ids().size() != 1) {
            return Optional.empty();
        }
        Component component = components.get(0);
        return openDocument.registry().typeOf(component).parameters().isEmpty()
                ? Optional.empty() : Optional.of(component);
    }

    private KeyStroke shortcut(int keyCode, int extraModifiers) {
        return KeyStroke.getKeyStroke(keyCode, menuMask | extraModifiers);
    }
}
