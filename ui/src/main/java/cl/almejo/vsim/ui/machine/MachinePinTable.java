package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineBody;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.MachinePin;
import cl.almejo.vsim.core.machine.MachinePinEdits;
import cl.almejo.vsim.core.machine.MachinePinEdits.PinNameProblem;
import cl.almejo.vsim.core.machine.MachinePinEdits.Side;
import cl.almejo.vsim.core.machine.MachinePinUses;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.ui.theme.WrappedErrorText;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;

/**
 * One side of a machine's pin list in its Propiedades (spec 2026-11-14 §1 «Pins come from the names», the mockup's
 * pin table): a title with «3 de 16», «Como un bus» with {@code in[1:0]}, and one row per pin: a handle to drag it
 * (its bit in bus mode), its name (Enter or leaving the field renames it everywhere), where it is used («en 4
 * flechas», a click shows them on the diagram) or «sin usar» in the warning colour with a ✕ that removes it. The
 * inputs end with {@code RST} and {@code CLK}, greyed, «siempre». «+ Agregar entrada» opens a field for a new unused
 * pin; at the limit it is disabled with the reason. Alt+↑ / Alt+↓ in a name moves that pin, as dragging does.
 * <p>
 * Each change is one edit of the machine through the {@link Host}: one undo step, the wires follow the pins by their
 * ids. After a change the rows are rebuilt only when what they show changed; what is typed and not applied yet, and
 * the field with the focus, are kept across it.
 */
final class MachinePinTable extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final String WIDE = "span 4, growx, wmin 0";
    private static final String MOVE_UP = "machine-pin-up";
    private static final String MOVE_DOWN = "machine-pin-down";
    private static final String CANCEL_NEW = "machine-pin-cancel";
    private static final int DROP_LINE_HEIGHT = 2;

    /** What the list edits and where it shows things. */
    interface Host {

        /** The machine as it is now; empty once it left. */
        Optional<StateMachine> machine();

        /** Makes {@code change} of the machine as one undo step. */
        void edit(UnaryOperator<StateMachine> change);

        /** Shows where a pin is used on the diagram (selects them and makes them blink). */
        void reveal(MachinePinUses.Uses uses);

        /** The colours now (read at paint time, never kept). */
        ColorScheme scheme();
    }

    /** One pin's row: its id (stable across renames), the name the model has and the field it is typed in. */
    private record Row(String pinId, String name, JTextField field, JComponent handle) {
    }

    private final Side side;
    private final transient Host host;
    private final transient List<Row> rows = new ArrayList<>();
    private final transient Map<String, JLabel> usesLabels = new HashMap<>();
    private final transient Map<String, AbstractButton> usesLinks = new HashMap<>();
    private final transient Map<String, AbstractButton> removeButtons = new HashMap<>();
    private final List<String> rowNames = new ArrayList<>();
    private final List<String> firstColumn = new ArrayList<>();
    private final JLabel counter = new MutedLabel("");
    private final JCheckBox busBox = new JCheckBox(Messages.text("machine.pins.asBus"));
    private final JLabel busLabel = new JLabel("");
    private final JButton addButton;
    private final JTextField newNameField = FragmentRefusingTransferHandler.install(new JTextField());
    private final WrappedErrorText problem = new WrappedErrorText(" ");
    /** What the rows were built from; they are rebuilt only when it changes. */
    private transient List<Object> builtFrom = List.of();
    /** True while the rows are being rebuilt: their fields losing the focus then apply nothing. */
    private boolean rebuilding;
    /** True while the bus box is set from the model: its change is not the user's. */
    private boolean settingBus;
    private transient Optional<Integer> dropIndex = Optional.empty();

    /** The pin list of {@code side}, editing through {@code host}. */
    MachinePinTable(Side side, Host host) {
        super(new MigLayout("wrap 4, insets 0, gapy 1, hidemode 3", "[][grow, fill, 40::][right][]"));
        this.side = Objects.requireNonNull(side, "side");
        this.host = Objects.requireNonNull(host, "host");
        setOpaque(false);
        busBox.setToolTipText(Messages.text(isInputs() ? "machine.pins.asBus.inputs.tooltip"
                : "machine.pins.asBus.outputs.tooltip"));
        busBox.addActionListener(event -> {
            if (!settingBus) {
                boolean asBus = busBox.isSelected();
                host.edit(machine -> MachinePinEdits.asBus(machine, side, asBus));
            }
        });
        busLabel.setFont(monospaced());
        addButton = new JButton(Messages.text(isInputs() ? "machine.pins.addInput" : "machine.pins.addOutput"));
        addButton.putClientProperty("JButton.buttonType", "borderless");
        addButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addButton.addActionListener(event -> openNewName());
        newNameField.getAccessibleContext().setAccessibleName(Messages.text(isInputs()
                ? "machine.pins.newInput.accessible" : "machine.pins.newOutput.accessible"));
        newNameField.setVisible(false);
        newNameField.addActionListener(event -> applyNewName(true));
        newNameField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                if (newNameField.isVisible()) {
                    applyNewName(false);
                }
            }
        });
        newNameField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                CANCEL_NEW);
        newNameField.getActionMap().put(CANCEL_NEW, action(this::closeNewName));
        problem.setVisible(false);
        refresh();
    }

    /** Brings the rows up to the machine as it is now (rebuilt only when what they show changed). */
    void refresh() {
        Optional<StateMachine> machine = host.machine();
        if (machine.isEmpty()) {
            return;
        }
        List<Object> shown = shownFrom(machine.get());
        if (shown.equals(builtFrom)) {
            return;
        }
        rebuild(machine.get());
        builtFrom = shown;
    }

    // --- what tests and the panel read ---

    Optional<JTextField> nameField(String name) {
        return rowOf(name).map(Row::field);
    }

    Optional<JComponent> handle(String name) {
        return rowOf(name).map(Row::handle);
    }

    Optional<AbstractButton> removeButton(String name) {
        return Optional.ofNullable(removeButtons.get(name));
    }

    Optional<AbstractButton> usesLink(String name) {
        return Optional.ofNullable(usesLinks.get(name));
    }

    /** «en 4 flechas», «sin usar», «siempre»; empty for a name not listed. */
    String usesText(String name) {
        return Optional.ofNullable(usesLinks.get(name)).map(AbstractButton::getText)
                .or(() -> Optional.ofNullable(usesLabels.get(name)).map(JLabel::getText)).orElse("");
    }

    /** The colour the «en ninguna flecha» text of {@code name} is drawn with now; none when the pin is used. */
    Color usesLabelColor(String name) {
        return Optional.ofNullable(usesLabels.get(name)).map(JLabel::getForeground).orElse(null);
    }

    AbstractButton addButton() {
        return addButton;
    }

    /** The field of a new pin's name, while it is open. */
    Optional<JTextField> newNameField() {
        return newNameField.isVisible() ? Optional.of(newNameField) : Optional.empty();
    }

    JCheckBox busBox() {
        return busBox;
    }

    /** {@code in[1:0]} when the side is a bus with names; empty otherwise. */
    String busText() {
        return busLabel.getText();
    }

    /** «3 de 16». */
    String counterText() {
        return counter.getText();
    }

    /** Why the last name typed was refused; empty when nothing is. */
    Optional<String> problemText() {
        return problem.isVisible() ? Optional.of(problem.getText()) : Optional.empty();
    }

    /** The names listed, top to bottom ({@code RST} and {@code CLK} last on the inputs). */
    List<String> rowNames() {
        return List.copyOf(rowNames);
    }

    /** What the first column shows for each pin: «⋮⋮», or its bit in bus mode. */
    List<String> firstColumn() {
        return List.copyOf(firstColumn);
    }

    // --- building ---

    private boolean isInputs() {
        return side == Side.INPUT;
    }

    private int limit() {
        return isInputs() ? MachineLimits.MAXIMUM_INPUTS : MachineLimits.MAXIMUM_OUTPUTS;
    }

    /** What the rows show of {@code machine}: they are rebuilt only when this changes. */
    private List<Object> shownFrom(StateMachine machine) {
        List<Object> shown = new ArrayList<>();
        shown.add(MachineBody.isBus(machine, side));
        shown.add(MachineBody.busName(machine, side));
        for (MachinePin pin : machine.pins(side)) {
            shown.add(pin);
            shown.add(MachinePinUses.of(machine, pin.name()));
        }
        return shown;
    }

    private void rebuild(StateMachine machine) {
        Map<String, String> typed = new HashMap<>();
        Optional<String> focusedPin = Optional.empty();
        for (Row row : rows) {
            if (!row.field().getText().equals(row.name())) {
                typed.put(row.pinId(), row.field().getText());
            }
            if (row.field().isFocusOwner()) {
                focusedPin = Optional.of(row.pinId());
            }
        }
        rebuilding = true;
        try {
            removeAll();
            rows.clear();
            usesLabels.clear();
            usesLinks.clear();
            removeButtons.clear();
            rowNames.clear();
            firstColumn.clear();
            buildTop(machine);
            boolean asBus = MachineBody.isBus(machine, side);
            if (asBus && !machine.pins(side).isEmpty()) {
                add(new MutedLabel(Messages.text("machine.pins.bit")));
                add(new MutedLabel(Messages.text("machine.pins.name")), "span 3");
            }
            List<MachinePin> pins = machine.pins(side);
            for (int index = 0; index < pins.size(); index++) {
                addPinRow(machine, pins.get(index), index, asBus, typed);
            }
            if (isInputs()) {
                addFixedRow(MachineLimits.RESET);
                addFixedRow(MachineLimits.CLOCK);
            }
            add(newNameField, WIDE);
            add(addButton, "span 4, left, gapleft 0");
            add(problem, WIDE);
        } finally {
            rebuilding = false;
        }
        focusedPin.flatMap(pinId -> rows.stream().filter(row -> row.pinId().equals(pinId)).findFirst())
                .ifPresent(row -> row.field().requestFocusInWindow());
        revalidate();
        repaint();
    }

    private void buildTop(StateMachine machine) {
        int count = machine.pins(side).size();
        JLabel title = new JLabel(Messages.text(isInputs() ? "machine.properties.inputs"
                : "machine.properties.outputs"));
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            title.setFont(font.deriveFont(Font.BOLD));
        }
        counter.setText(Messages.format("machine.pins.count", count, limit()));
        add(title, "span 2");
        add(counter, "span 2, right");
        settingBus = true;
        try {
            busBox.setSelected(MachineBody.isBus(machine, side));
        } finally {
            settingBus = false;
        }
        boolean nothingToJoin = count == 0 && !busBox.isSelected();
        busBox.setEnabled(!nothingToJoin);
        busBox.setToolTipText(Messages.text(nothingToJoin
                ? (isInputs() ? "machine.pins.asBus.noInputs" : "machine.pins.asBus.noOutputs")
                : (isInputs() ? "machine.pins.asBus.inputs.tooltip" : "machine.pins.asBus.outputs.tooltip")));
        // always in its cell, empty when not a bus: a hidden cell would shift the rows under it
        busLabel.setText(busBox.isSelected() && count > 0
                ? MachinePieceRows.busText(MachineBody.busName(machine, side), count) : "");
        add(busBox, "span 2");
        add(busLabel, "span 2, left");
        boolean full = count >= limit();
        addButton.setEnabled(!full);
        addButton.setToolTipText(full
                ? Messages.format(isInputs() ? "machine.limit.tooManyInputs" : "machine.limit.tooManyOutputs",
                limit())
                : Messages.text(isInputs() ? "machine.pins.addInput.tooltip" : "machine.pins.addOutput.tooltip"));
        if (full) {
            closeNewName();
        }
    }

    private void addPinRow(StateMachine machine, MachinePin pin, int index, boolean asBus,
                           Map<String, String> typed) {
        String busName = MachineBody.busName(machine, side);
        JLabel handle = new MutedLabel(asBus ? String.valueOf(index) : Messages.text("machine.pins.handle"));
        handle.setToolTipText(asBus ? Messages.format("machine.pins.bit.tooltip", index, busName)
                : Messages.text("machine.pins.handle.tooltip"));
        handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        JTextField field = FragmentRefusingTransferHandler.install(new JTextField(typed.getOrDefault(pin.id(),
                pin.name())));
        field.getAccessibleContext().setAccessibleName(Messages.format(isInputs() ? "machine.pins.input.accessible"
                : "machine.pins.output.accessible", pin.name()));
        Row row = new Row(pin.id(), pin.name(), field, handle);
        field.addActionListener(event -> applyRename(row, true));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                if (!rebuilding && rows.contains(row)) {
                    applyRename(row, false);
                }
            }
        });
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,
                InputEvent.ALT_DOWN_MASK), MOVE_UP);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,
                InputEvent.ALT_DOWN_MASK), MOVE_DOWN);
        field.getActionMap().put(MOVE_UP, action(() -> moveBy(row, -1)));
        field.getActionMap().put(MOVE_DOWN, action(() -> moveBy(row, 1)));
        DragToReorder drag = new DragToReorder(row);
        handle.addMouseListener(drag);
        handle.addMouseMotionListener(drag);
        rows.add(row);
        rowNames.add(pin.name());
        firstColumn.add(handle.getText());
        add(handle, "gapright 4");
        add(field, "wmin 0");
        MachinePinUses.Uses uses = MachinePinUses.of(machine, pin.name());
        if (uses.isUnused()) {
            JLabel unused = new WarningLabel(Messages.text("machine.pins.unused"));
            unused.setToolTipText(Messages.format("machine.pins.unused.tooltip", pin.name()));
            usesLabels.put(pin.name(), unused);
            add(unused);
            JButton remove = new JButton(Messages.text("machine.pins.remove"));
            remove.putClientProperty("JButton.buttonType", "borderless");
            remove.setToolTipText(Messages.format("machine.pins.remove.tooltip", pin.name()));
            remove.getAccessibleContext().setAccessibleName(Messages.format("machine.pins.remove.tooltip",
                    pin.name()));
            remove.addActionListener(event -> host.edit(current -> current.pin(side, pin.name()).isPresent()
                    && MachinePinUses.of(current, pin.name()).isUnused()
                    ? MachinePinEdits.removed(current, side, pin.name()) : current));
            removeButtons.put(pin.name(), remove);
            add(remove);
        } else {
            String where = usesText(uses);
            JButton link = new MachineLinkButton(where);
            link.setToolTipText(Messages.format("machine.pins.uses.tooltip", pin.name(), where));
            link.addActionListener(event -> host.reveal(uses));
            usesLinks.put(pin.name(), link);
            add(link, "span 2");
        }
    }

    private void addFixedRow(String name) {
        JLabel nameLabel = new JLabel(name);
        nameLabel.setEnabled(false);
        nameLabel.setToolTipText(Messages.format("machine.pins.always.tooltip", name));
        JLabel always = new MutedLabel(Messages.text("machine.pins.always"));
        always.setToolTipText(nameLabel.getToolTipText());
        usesLabels.put(name, always);
        rowNames.add(name);
        add(new JLabel(""));
        add(nameLabel, "gapleft 4");
        add(always, "span 2");
    }

    /** «en 4 flechas», «en 1 estado», «en 2 estados y 3 flechas». */
    static String usesText(MachinePinUses.Uses uses) {
        int states = uses.stateIds().size();
        int arrows = uses.transitionIds().size();
        String statesText = Messages.format(states == 1 ? "machine.pins.uses.states.one"
                : "machine.pins.uses.states.many", states);
        String arrowsText = Messages.format(arrows == 1 ? "machine.pins.uses.arrows.one"
                : "machine.pins.uses.arrows.many", arrows);
        if (states > 0 && arrows > 0) {
            return Messages.format("machine.pins.uses.both", statesText, arrowsText);
        }
        return Messages.format("machine.pins.uses", states > 0 ? statesText : arrowsText);
    }

    private Optional<Row> rowOf(String name) {
        return rows.stream().filter(row -> row.name().equals(name)).findFirst();
    }

    // --- edits ---

    /**
     * Renames the pin of {@code row} to what its field says, when it changed and the name is good; else says why. A
     * refused name stays while the field has the focus ({@code typing}), so it can be fixed; leaving the field puts
     * back the name the machine has, the reason still under it.
     */
    private void applyRename(Row row, boolean typing) {
        String typedName = row.field().getText().strip();
        if (typedName.equals(row.name())) {
            row.field().setText(row.name());
            return;
        }
        Optional<String> refusal = host.machine().flatMap(machine -> refusal(machine, row.name(), typedName));
        if (refusal.isPresent()) {
            showProblem(refusal);
            if (!typing) {
                row.field().setText(row.name());
            }
            return;
        }
        showProblem(Optional.empty());
        host.edit(machine -> machine.pin(side, row.name()).isPresent()
                ? MachinePinEdits.renamed(machine, side, row.name(), typedName) : machine);
    }

    /** Why {@code newName} cannot be the name of the pin {@code oldName} ({@code ""} for a new one), in words. */
    private Optional<String> refusal(StateMachine machine, String oldName, String newName) {
        if (newName.isEmpty()) {
            return Optional.of(Messages.text("machine.pins.problem.empty"));
        }
        return MachinePinEdits.nameProblem(machine, side, oldName, newName).map(found -> describe(found, newName));
    }

    private String describe(PinNameProblem found, String name) {
        return switch (found) {
            case NOT_A_NAME -> Messages.format("machine.pins.problem.notAName", name);
            case RESERVED -> Messages.format("machine.pins.problem.reserved", name);
            case TAKEN_INPUT -> Messages.format("machine.pins.problem.takenInput", name);
            case TAKEN_OUTPUT -> Messages.format("machine.pins.problem.takenOutput", name);
            case TOO_MANY -> Messages.format(isInputs() ? "machine.limit.tooManyInputs"
                    : "machine.limit.tooManyOutputs", limit());
        };
    }

    private void openNewName() {
        newNameField.setText("");
        newNameField.setVisible(true);
        revalidate();
        newNameField.requestFocusInWindow();
    }

    private void closeNewName() {
        newNameField.setText("");
        newNameField.setVisible(false);
        showProblem(Optional.empty());
        revalidate();
        repaint();
    }

    /** Adds the pin typed in the new name's field; an empty field just closes. */
    private void applyNewName(boolean typing) {
        String typedName = newNameField.getText().strip();
        if (typedName.isEmpty()) {
            closeNewName();
            return;
        }
        Optional<String> refusal = host.machine().flatMap(machine -> refusal(machine, "", typedName));
        if (refusal.isPresent()) {
            showProblem(refusal);
            if (!typing) {
                newNameField.setText("");
                newNameField.setVisible(false);
            }
            return;
        }
        closeNewName();
        host.edit(machine -> MachinePinEdits.nameProblem(machine, side, "", typedName).isEmpty()
                ? MachinePinEdits.added(machine, side, typedName) : machine);
    }

    private void moveBy(Row row, int step) {
        int from = rows.indexOf(row);
        int to = from + step;
        if (from < 0 || to < 0 || to >= rows.size()) {
            return;
        }
        movePin(from, to);
    }

    private void movePin(int from, int to) {
        String movedName = rows.get(from).name();
        host.edit(machine -> {
            List<MachinePin> pins = machine.pins(side);
            boolean stillThere = from < pins.size() && to < pins.size() && pins.get(from).name().equals(movedName);
            return stillThere ? MachinePinEdits.moved(machine, side, from, to) : machine;
        });
    }

    private void showProblem(Optional<String> text) {
        problem.setText(text.orElse(" "));
        problem.setVisible(text.isPresent());
        revalidate();
    }

    private static Font monospaced() {
        Font font = UIManager.getFont("Label.font");
        int size = font == null ? 12 : font.getSize();
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    private static AbstractAction action(Runnable behaviour) {
        return new AbstractAction() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                behaviour.run();
            }
        };
    }

    // --- dragging a row by its handle ---

    @Override
    protected void paintChildren(Graphics graphics) {
        super.paintChildren(graphics);
        dropIndex.ifPresent(index -> {
            Color line = UIManager.getColor("Component.focusColor");
            graphics.setColor(line != null ? line : getForeground());
            graphics.fillRect(0, dropLineY(index), getWidth(), DROP_LINE_HEIGHT);
        });
    }

    /** Where the line of a drop at {@code index} goes: over that row, or under the last one. */
    private int dropLineY(int index) {
        if (rows.isEmpty()) {
            return 0;
        }
        if (index >= rows.size()) {
            Rectangle last = boundsIn(rows.get(rows.size() - 1).field());
            return last.y + last.height;
        }
        return boundsIn(rows.get(index).field()).y - DROP_LINE_HEIGHT;
    }

    private Rectangle boundsIn(JComponent component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), this);
    }

    /** The index a row dragged from {@code from} lands on with the pointer at {@code y} (in this panel). */
    private int targetIndex(int from, int y) {
        int target = 0;
        for (int index = 0; index < rows.size(); index++) {
            if (index == from) {
                continue;
            }
            Rectangle bounds = boundsIn(rows.get(index).field());
            if (bounds.y + bounds.height / 2 < y) {
                target++;
            }
        }
        return target;
    }

    /** Drags a pin's row by its handle to another place in the list: one move on release. */
    private final class DragToReorder extends MouseAdapter {
        private final Row row;
        private OptionalInt target = OptionalInt.empty();

        private DragToReorder(Row row) {
            this.row = row;
        }

        @Override
        public void mousePressed(MouseEvent event) {
            target = OptionalInt.empty();
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            int from = rows.indexOf(row);
            if (from < 0) {
                return;
            }
            Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), MachinePinTable.this);
            int landing = targetIndex(from, point.y);
            target = OptionalInt.of(landing);
            dropIndex = landing == from ? Optional.empty() : Optional.of(landing > from ? landing + 1 : landing);
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            dropIndex = Optional.empty();
            repaint();
            int from = rows.indexOf(row);
            if (target.isPresent() && from >= 0 && target.getAsInt() != from) {
                movePin(from, target.getAsInt());
            }
            target = OptionalInt.empty();
        }
    }

    // --- small components ---

    /** A label in the theme's muted colour, read at paint time. */
    private static class MutedLabel extends JLabel {
        @Serial
        private static final long serialVersionUID = 1L;

        private MutedLabel(String text) {
            super(text);
        }

        @Override
        public Color getForeground() {
            Color colour = UIManager.getColor("Label.disabledForeground");
            return colour != null ? colour : super.getForeground();
        }
    }

    /** «en ninguna flecha»: in the readable warning text colour (read at paint time), and in words. */
    private static final class WarningLabel extends JLabel {
        @Serial
        private static final long serialVersionUID = 1L;

        private WarningLabel(String text) {
            super(text);
        }

        @Override
        public Color getForeground() {
            return ReadableColors.warningText();
        }
    }
}
