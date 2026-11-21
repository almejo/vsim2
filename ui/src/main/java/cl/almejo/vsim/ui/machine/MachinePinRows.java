package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.EditInsideCommand;
import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.command.ReplaceDefinitionCommand;
import cl.almejo.vsim.core.command.WrapsCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.library.RemovedPinWire;
import cl.almejo.vsim.core.library.UpdateNote;
import cl.almejo.vsim.core.machine.MachinePinEdits.Side;
import cl.almejo.vsim.core.machine.MachinePinUses;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.window.InstanceNames;
import net.miginfocom.swing.MigLayout;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The pins of a machine in its own Propiedades (hito 3, task 6): the inputs' and the outputs' lists
 * ({@link MachinePinTable}, each with a «?» that says how they are edited), and the «Flanco» combo, «Bajada (como los
 * flip-flops)» or «Subida», which the machine's rows place in their own section.
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
    /** The update notes about a piece that kept its old shape (the others, «el pin in se ubicó…», are noise here). */
    private static final Set<String> KEPT_SHAPE_NOTES = Set.of("appearance.update.keptOldShape",
            "appearance.update.newPinOwnShape");

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
        super(new MigLayout("wrap 2, insets 0, hidemode 3", "[left][grow, fill, 60lp::]"));
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
        add(inputs, WIDE + ", gaptop 4lp");
        add(outputs, WIDE + ", gaptop 8lp");
        edgeBox.setToolTipText(Messages.text("machine.pins.edge.tooltip"));
        edgeBox.addActionListener(event -> {
            if (!settingEdge) {
                String edge = edgeBox.getSelectedIndex() == RISING_INDEX ? StateMachine.EDGE_RISING
                        : StateMachine.EDGE_FALLING;
                edit(machine -> machine.withEdge(edge));
            }
        });
        refresh();
    }

    /** One side's list. */
    MachinePinTable table(Side side) {
        return side == Side.INPUT ? inputs : outputs;
    }

    /** «Flanco»: the machine's own rows put it in their «Máquina» section ({@link #refresh()} keeps it up to date). */
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

    /**
     * Makes {@code change} as one undo step, then says on the status line whose wires it left loose and which pieces
     * had to keep their old shape (a pin would have touched another net there).
     */
    private void edit(UnaryOperator<StateMachine> change) {
        OpenDocument place = canvas.place();
        OpenDocument circuit = place.parent().orElse(place);
        Optional<Command> before = circuit.history().undoCommand();
        if (!canvas.edit(change)) {
            return;
        }
        // the circuit's newest step, when this edit made one (not when the canvas edits a test's draft)
        Optional<Command> command = circuit.history().undoCommand()
                .filter(done -> before.map(earlier -> earlier != done).orElse(true));
        List<String> lines = new ArrayList<>();
        looseWiresNotice(command.map(MachinePinRows::removedPinWires).orElse(List.of())).ifPresent(lines::add);
        command.ifPresent(done -> lines.addAll(keptShapeNotes(done, circuit.document())));
        if (!lines.isEmpty()) {
            notices.accept(String.join(" ", lines));
        }
    }

    /** The wires {@code command} (a machine edit, maybe inside its tab's write-back) left without their pin. */
    static List<RemovedPinWire> removedPinWires(Command command) {
        Optional<EditInsideCommand> inside = OpenDocument.insideEditOf(command);
        if (inside.isPresent()) {
            return inside.get().removedPinWires();
        }
        return machineEditOf(command).map(EditMachineCommand::removedPinWires).orElse(List.of());
    }

    /**
     * «Máquina 1 se queda con la forma anterior para no tocar otra red.», «Máquina 1: el pin in quedó en otro lugar
     * (…)»: what {@code command}'s update of the placed pieces of {@code circuit} says about a piece that did not take
     * the machine's face; empty when every piece took it.
     */
    static List<String> keptShapeNotes(Command command, CircuitDocument circuit) {
        Optional<ReplaceDefinitionCommand> update = machineEditOf(command)
                .flatMap(EditMachineCommand::plannedUpdate);
        if (update.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (UpdateNote note : update.get().notes()) {
            if (KEPT_SHAPE_NOTES.contains(note.messageKey())) {
                lines.add(Messages.format(note.messageKey(), note.arguments().toArray()));
            }
        }
        for (KeptOldShape kept : update.get().keptOldShapes()) {
            String piece = kept.instance().value();
            lines.add(Messages.format("problem.keptOldShape", InstanceNames.name(piece, circuit).orElse(piece),
                    String.join(", ", kept.newPinNames())));
        }
        return lines;
    }

    /** The machine edit {@code command} is or wraps (in its tab's inside edit, in a marking command). */
    private static Optional<EditMachineCommand> machineEditOf(Command command) {
        Command unwrapped = OpenDocument.insideEditOf(command).<Command>map(EditInsideCommand::wrapped)
                .orElse(command);
        while (unwrapped instanceof WrapsCommand wraps) {
            unwrapped = wraps.wrapped();
        }
        return unwrapped instanceof EditMachineCommand machineEdit ? Optional.of(machineEdit) : Optional.empty();
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
