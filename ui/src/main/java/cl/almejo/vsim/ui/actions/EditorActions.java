package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.command.HistoryListener;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.RotateElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.ConfigureDialog;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.WireTool;
import cl.almejo.vsim.ui.waveform.TimeAxis;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Every user command of an editor window, as Swing actions whose enabled state follows the editor. */
public final class EditorActions {

    public static final List<Double> SPEEDS = List.of(0.1, 1.0, 10.0, 100.0);

    /** The icon before the speed selector in the toolbar, by file name under {@code icons/} (M6.1-E). */
    public static final String SPEED_ICON = "speed";

    private final OpenDocument openDocument;
    private final CircuitCanvas canvas;
    private final int menuMask = ShortcutCatalog.menuMask();
    private final HistoryListener historyListener = history -> refresh();
    /** How far «Avanzar N t» goes (M6.1-B: the preference, followed by {@code FixedStepFeatures}). */
    private long stepUnits = AppPreferences.DEFAULT_STEP_UNITS;

    private final Action newDocument;
    private final Action open;
    private final Action save;
    private final Action saveAs;
    private final Action closeWindow;
    private final Action exit;
    private final Action undo;
    private final Action redo;
    private final Action delete;
    private final Action rotateClockwise;
    private final Action rotateCounterClockwise;
    private final Action configure;
    private final Action selectTool;
    private final Action wireTool;
    private final Action panTool;
    private final Action zoomIn;
    private final Action zoomOut;
    private final Action zoomReset;
    private final Action centerView;
    private final Action start;
    private final Action pause;
    private final Action restart;
    private final Action step;
    private final Action newView;
    private final Action about;

    public EditorActions(OpenDocument openDocument, CircuitCanvas canvas, WindowCommands window) {
        this.openDocument = openDocument;
        this.canvas = canvas;
        newDocument = action(Messages.text("action.new"), shortcut(KeyEvent.VK_N, 0), window::newDocument);
        open = action(Messages.text("action.open"), shortcut(KeyEvent.VK_O, 0), window::open);
        save = action(Messages.text("action.save"), shortcut(KeyEvent.VK_S, 0), window::save);
        saveAs = action(Messages.text("action.saveAs"), shortcut(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK),
                window::saveAs);
        closeWindow = action(Messages.text("action.close"), shortcut(KeyEvent.VK_W, 0), window::closeWindow);
        exit = action(Messages.text("action.exit"), shortcut(KeyEvent.VK_Q, 0), window::exit);
        undo = action(Messages.text("action.undo"), shortcut(KeyEvent.VK_Z, 0), () -> openDocument.history().undo());
        redo = action(Messages.text("action.redo"), shortcut(KeyEvent.VK_Z, InputEvent.SHIFT_DOWN_MASK),
                () -> openDocument.history().redo());
        delete = action(Messages.text("action.delete"), null, this::deleteSelection);
        delete.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.delete"));
        rotateClockwise = action(Messages.text("action.rotateClockwise"), null, () -> rotateSelection(true));
        rotateClockwise.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.rotateClockwise"));
        rotateCounterClockwise = action(Messages.text("action.rotateCounterClockwise"), null,
                () -> rotateSelection(false));
        rotateCounterClockwise.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.rotateCounterClockwise"));
        configure = action(Messages.text("action.configure"), null,
                () -> singleConfigurable().ifPresent(this::configure));
        selectTool = action(Messages.text("action.toolSelect"), null, canvas::useDefaultTool);
        wireTool = action(Messages.text("action.toolWire"), null, () -> canvas.setTool(new WireTool()));
        panTool = action(Messages.text("action.toolPan"), null, () -> canvas.setTool(new PanTool()));
        zoomIn = action(Messages.text("action.zoomIn"), shortcut(KeyEvent.VK_EQUALS, 0), canvas::zoomIn);
        zoomOut = action(Messages.text("action.zoomOut"), shortcut(KeyEvent.VK_MINUS, 0), canvas::zoomOut);
        zoomReset = action(Messages.text("action.zoomReset"), shortcut(KeyEvent.VK_0, 0), canvas::resetZoom);
        centerView = action(Messages.text("action.centerView"), null, canvas::centerCircuit);
        start = action(Messages.text("action.start"), null, openDocument::resume);
        pause = action(Messages.text("action.pause"), null, openDocument::pause);
        restart = action(Messages.text("action.restart"), shortcut(KeyEvent.VK_F5, 0), openDocument::restart);
        step = action(Messages.text("action.step"), KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0),
                () -> openDocument.step(stepUnits));
        setStepUnits(stepUnits);
        newView = action(Messages.text("action.newWindow"), null, window::newView);
        about = action(Messages.text("action.about"), null, () -> JOptionPane.showMessageDialog(canvas,
                Messages.text("dialog.about.message"), Messages.text("action.about"), JOptionPane.INFORMATION_MESSAGE));

        openDocument.history().addListener(historyListener);
        canvas.selection().addListener(this::refresh);
        canvas.addSnapshotListener(snapshot -> refreshSimulationButtons(snapshot.running()));
        refresh();
        refreshSimulationButtons(true);
    }

    public Action newDocument() { return newDocument; }
    public Action open() { return open; }
    public Action save() { return save; }
    public Action saveAs() { return saveAs; }
    public Action closeWindow() { return closeWindow; }
    public Action exit() { return exit; }
    public Action undo() { return undo; }
    public Action redo() { return redo; }
    public Action delete() { return delete; }
    public Action rotateClockwise() { return rotateClockwise; }
    public Action rotateCounterClockwise() { return rotateCounterClockwise; }
    public Action configure() { return configure; }
    public Action selectTool() { return selectTool; }
    public Action wireTool() { return wireTool; }
    public Action panTool() { return panTool; }
    public Action zoomIn() { return zoomIn; }
    public Action zoomOut() { return zoomOut; }
    public Action zoomReset() { return zoomReset; }
    public Action centerView() { return centerView; }
    public Action start() { return start; }
    public Action pause() { return pause; }
    /** «Reiniciar» (M6.1-B): always enabled. */
    public Action restart() { return restart; }
    public Action step() { return step; }
    public Action newView() { return newView; }
    public Action about() { return about; }

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

    /** Opens the Configure dialog for {@code component}; an accepted change becomes one undo step. */
    public void configure(Component component) {
        ComponentType type = openDocument.registry().typeOf(component);
        if (type.parameters().isEmpty()) {
            return;
        }
        ConfigureDialog.show(canvas, type, component)
                .ifPresent(values -> openDocument.execute(new ChangeParametersCommand(component.id(), type, values)));
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
        view.add(wireTool);
        view.add(panTool);
        view.addSeparator();
        view.add(zoomIn);
        view.add(zoomOut);
        view.add(zoomReset);
        view.add(centerView);
        JMenu simulation = tagged(new JMenu(Messages.text("menu.simulation")), MenuId.SIMULATION);
        simulation.add(start);
        simulation.add(pause);
        simulation.add(restart);
        simulation.add(step);
        JMenu windowMenu = tagged(new JMenu(Messages.text("menu.window")), MenuId.WINDOW);
        windowMenu.add(newView);
        JMenu help = tagged(new JMenu(Messages.text("menu.help")), MenuId.HELP);
        help.add(about);
        menuBar.add(file);
        menuBar.add(edit);
        menuBar.add(view);
        menuBar.add(simulation);
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
        selector.setSelectedItem(1.0);
        selector.setToolTipText(Messages.text("toolbar.speedTip"));
        selector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                   boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setText(Messages.format(index < 0 ? "speed.closed" : "speed.label", value));
                return this;
            }
        });
        selector.addActionListener(event -> openDocument.setSpeed((Double) selector.getSelectedItem()));
        return selector;
    }

    public void refresh() {
        History history = openDocument.history();
        undo.setEnabled(history.canUndo());
        undo.putValue(Action.NAME, history.undoDescription()
                .map(key -> Messages.format("action.undoNamed", Messages.commandName(key)))
                .orElse(Messages.text("action.undo")));
        redo.setEnabled(history.canRedo());
        redo.putValue(Action.NAME, history.redoDescription()
                .map(key -> Messages.format("action.redoNamed", Messages.commandName(key)))
                .orElse(Messages.text("action.redo")));
        Set<ElementId> selected = canvas.selection().ids();
        delete.setEnabled(!selected.isEmpty());
        rotateClockwise.setEnabled(!selected.isEmpty());
        rotateCounterClockwise.setEnabled(!selected.isEmpty());
        configure.setEnabled(singleConfigurable().isPresent());
    }

    public void refreshSimulationButtons(boolean running) {
        start.setEnabled(!running);
        pause.setEnabled(running);
        step.setEnabled(!running);
    }

    private void deleteSelection() {
        Set<ElementId> selected = canvas.selection().ids();
        if (!selected.isEmpty()) {
            openDocument.execute(new RemoveElementsCommand(selected));
        }
    }

    private void rotateSelection(boolean clockwise) {
        Set<ElementId> selected = canvas.selection().ids();
        RotateElementsCommand rotate = new RotateElementsCommand(selected, clockwise, openDocument.registry());
        if (rotate.changes(openDocument.document())) {
            openDocument.execute(rotate);
        }
    }

    private List<Component> selectedComponents() {
        return canvas.selection().ids().stream()
                .map(id -> openDocument.document().component(id))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Component> singleConfigurable() {
        List<Component> components = selectedComponents();
        if (components.size() != 1 || canvas.selection().ids().size() != 1) {
            return Optional.empty();
        }
        Component component = components.get(0);
        return openDocument.registry().typeOf(component).parameters().isEmpty()
                ? Optional.empty() : Optional.of(component);
    }

    private KeyStroke shortcut(int keyCode, int extraModifiers) {
        return KeyStroke.getKeyStroke(keyCode, menuMask | extraModifiers);
    }

    private static Action action(String name, KeyStroke accelerator, Runnable behavior) {
        Action action = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent event) {
                behavior.run();
            }
        };
        if (accelerator != null) {
            action.putValue(Action.ACCELERATOR_KEY, accelerator);
        }
        return action;
    }
}
