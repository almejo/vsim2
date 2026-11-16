package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.EditInsideCommand;
import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.command.WrapsCommand;
import cl.almejo.vsim.core.library.RemovedPinWire;
import cl.almejo.vsim.core.machine.MachinePinEdits.Side;
import cl.almejo.vsim.core.machine.MachinePinUses;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import net.miginfocom.swing.MigLayout;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.io.Serial;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The pins of a machine in its own Propiedades (hito 3, task 6): the inputs' and the outputs' lists
 * ({@link MachinePinTable}), a line on how they are edited, and «Flanco», «Bajada (como los flip-flops)» or «Subida».
 * Every change is one edit of the machine (one undo step); when one takes pins away (a side turned «Como un bus» or
 * back, an unused pin removed or merged) the status line says whose wires came loose: «Los cables de x, y quedaron
 * sueltos». The rows follow the machine through {@link #refresh()}, which the machine's Propiedades call on each
 * change.
 */
final class MachinePinRows extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final String WIDE = "span 2, growx, wmin 0";
    private static final int FALLING_INDEX = 0;
    private static final int RISING_INDEX = 1;

    private final transient MachineCanvas canvas;
    private final transient Consumer<String> notices;
    private final MachinePinTable inputs;
    private final MachinePinTable outputs;
    private final JComboBox<String> edgeBox = new JComboBox<>(new String[] {
            Messages.text("machine.piece.edge.falling"), Messages.text("machine.piece.edge.rising")});
    /** True while the edge box is set from the model: its change is not the user's. */
    private boolean settingEdge;

    /**
     * The pin rows of {@code canvas}'s machine; {@code notices} is the status line, and a click on a pin's uses makes
     * them blink when {@code animations} says so.
     */
    MachinePinRows(MachineCanvas canvas, Consumer<String> notices, BooleanSupplier animations) {
        super(new MigLayout("wrap 2, insets 0, hidemode 3", "[left][grow, fill, 60::]"));
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.notices = Objects.requireNonNull(notices, "notices");
        Objects.requireNonNull(animations, "animations");
        setOpaque(false);
        MachinePinTable.Host host = new MachinePinTable.Host() {
            @Override
            public Optional<StateMachine> machine() {
                return canvas.machine();
            }

            @Override
            public void edit(UnaryOperator<StateMachine> change) {
                MachinePinRows.this.edit(change);
            }

            @Override
            public void reveal(MachinePinUses.Uses uses) {
                canvas.revealElements(new LinkedHashSet<>(uses.stateIds()), new LinkedHashSet<>(uses.transitionIds()),
                        animations.getAsBoolean());
            }

            @Override
            public ColorScheme scheme() {
                return canvas.colours();
            }
        };
        inputs = new MachinePinTable(Side.INPUT, host);
        outputs = new MachinePinTable(Side.OUTPUT, host);
        add(inputs, WIDE + ", gaptop 4");
        add(outputs, WIDE + ", gaptop 8");
        add(new MachineInspectorRows.Hint(Messages.text("machine.pins.hint"), true), WIDE);
        JLabel edgeCaption = new JLabel(Messages.text("parameter.edge"));
        edgeCaption.setLabelFor(edgeBox);
        edgeBox.setToolTipText(Messages.text("machine.pins.edge.tooltip"));
        edgeBox.addActionListener(event -> {
            if (!settingEdge) {
                String edge = edgeBox.getSelectedIndex() == RISING_INDEX ? StateMachine.EDGE_RISING
                        : StateMachine.EDGE_FALLING;
                edit(machine -> machine.withEdge(edge));
            }
        });
        add(edgeCaption, "gaptop 6");
        add(edgeBox, "gaptop 6, growx 0");
        refresh();
    }

    /** One side's list. */
    MachinePinTable table(Side side) {
        return side == Side.INPUT ? inputs : outputs;
    }

    /** «Flanco». */
    JComboBox<String> edgeBox() {
        return edgeBox;
    }

    /** Brings every row up to the machine as it is now; what is being typed stays. */
    void refresh() {
        inputs.refresh();
        outputs.refresh();
        canvas.machine().ifPresent(machine -> {
            int index = StateMachine.EDGE_RISING.equals(machine.edge()) ? RISING_INDEX : FALLING_INDEX;
            if (edgeBox.getSelectedIndex() != index) {
                settingEdge = true;
                try {
                    edgeBox.setSelectedIndex(index);
                } finally {
                    settingEdge = false;
                }
            }
        });
    }

    /** Makes {@code change} as one undo step, then says on the status line whose wires it left loose. */
    private void edit(UnaryOperator<StateMachine> change) {
        if (!canvas.edit(change)) {
            return;
        }
        OpenDocument place = canvas.place();
        OpenDocument circuit = place.parent().orElse(place);
        List<RemovedPinWire> loose = circuit.history().undoCommand().map(MachinePinRows::removedPinWires)
                .orElse(List.of());
        looseWiresNotice(loose).ifPresent(notices);
    }

    /** The wires {@code command} (a machine edit, maybe inside its tab's write-back) left without their pin. */
    static List<RemovedPinWire> removedPinWires(Command command) {
        Optional<EditInsideCommand> inside = OpenDocument.insideEditOf(command);
        if (inside.isPresent()) {
            return inside.get().removedPinWires();
        }
        Command unwrapped = command;
        while (unwrapped instanceof WrapsCommand wraps) {
            unwrapped = wraps.wrapped();
        }
        return unwrapped instanceof EditMachineCommand machineEdit ? machineEdit.removedPinWires() : List.of();
    }

    /**
     * «Los cables de x, y quedaron sueltos (…)»; empty when no wire came loose. The words agree with the wires, not
     * the names (hito 3 review, M5): two copies wired on {@code x} read «Los cables de x quedaron sueltos», and only
     * one wire reads «El cable de x quedó suelto».
     */
    static Optional<String> looseWiresNotice(List<RemovedPinWire> loose) {
        if (loose.isEmpty()) {
            return Optional.empty();
        }
        List<String> names = loose.stream().map(RemovedPinWire::pinName).distinct().toList();
        return Optional.of(Messages.format(loose.size() == 1 ? "machine.pins.loose.one" : "machine.pins.loose.many",
                String.join(", ", names)));
    }
}
