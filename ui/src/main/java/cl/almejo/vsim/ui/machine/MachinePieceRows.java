package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineBody;
import cl.almejo.vsim.core.machine.MachinePinEdits;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PropertiesPanel;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * What a machine's piece shows in Propiedades of the circuit about its machine (hito 3, mockup «Entradas y salidas
 * en Propiedades»): the pins, the states, the type and the edge are the machine's, the same in every copy, so they are
 * only shown here, under «De la máquina (se cambia adentro)», with «Entrar ↘ para cambiarlas», which opens the
 * machine's tab at this copy. The rows are built each time Propiedades shows the piece, and a change of the machine
 * (made in its tab) changes the piece too, so they always say what the machine is now.
 *
 * <p>No live «Estado ahora» row: the piece's face already shows the state, and a live row would need a snapshot
 * listener of its own.
 */
final class MachinePieceRows implements PropertiesPanel.ComponentRows {


    private final OpenDocument place;
    private final Consumer<Component> enter;

    /** @param enter enters a piece, as a double click on it does ({@code EditorView.enterComponent}) */
    MachinePieceRows(OpenDocument place, Consumer<Component> enter) {
        this.place = Objects.requireNonNull(place, "place");
        this.enter = Objects.requireNonNull(enter, "enter");
    }

    @Override
    public void addRows(Component component, PropertiesPanel.Rows rows) {
        Optional<StateMachine> machine = place.registry().findTypeOf(component)
                .flatMap(SubcircuitTypes::definitionOf)
                .flatMap(ComponentDefinition::machine);
        if (machine.isEmpty()) {
            return;
        }
        rows.addSection(Messages.text("machine.piece.section"));
        rows.add(Messages.text("machine.properties.inputs"), new JLabel(inputsText(machine.get())));
        rows.add(Messages.text("machine.properties.outputs"), new JLabel(outputsText(machine.get())));
        rows.add(Messages.text("machine.properties.states"), new JLabel(statesText(machine.get())));
        rows.add(Messages.text("machine.properties.kind"), new JLabel(MachineInspectorRows.kindOf(machine.get())));
        rows.add(Messages.text("parameter.edge"), new JLabel(edgeText(machine.get())));
        rows.addWide(new JButton(Actions.of(Messages.text("machine.piece.enter"), () -> enter.accept(component))));
    }

    /** «x, y»; as a bus, «in[1:0] = x·0 y·1»; «—» without inputs. */
    static String inputsText(StateMachine machine) {
        return pinsText(machine.inputNames(), machine.inputsAsBus(),
                MachineBody.busName(machine, MachinePinEdits.Side.INPUT));
    }

    /** «Z, Y»; as a bus, «out[1:0] = Z·0 Y·1»; «—» without outputs. */
    static String outputsText(StateMachine machine) {
        return pinsText(machine.outputNames(), machine.outputsAsBus(),
                MachineBody.busName(machine, MachinePinEdits.Side.OUTPUT));
    }

    /** «3 · inicial S0»; only «3» while no state is the initial one. */
    static String statesText(StateMachine machine) {
        String count = String.valueOf(machine.states().size());
        return machine.initialStateId().flatMap(machine::state).map(MachineState::name)
                .map(initial -> Messages.format("machine.piece.states", count, initial))
                .orElse(count);
    }

    /** «Bajada (como los flip-flops)» or «Subida». */
    static String edgeText(StateMachine machine) {
        return Messages.text(StateMachine.EDGE_RISING.equals(machine.edge())
                ? "machine.piece.edge.rising" : "machine.piece.edge.falling");
    }

    private static String pinsText(List<String> names, boolean asBus, String busName) {
        if (names.isEmpty()) {
            return Messages.text("machine.properties.none");
        }
        if (!asBus) {
            return String.join(", ", names);
        }
        String bits = IntStream.range(0, names.size())
                .mapToObj(bit -> Messages.format("machine.piece.busBit", names.get(bit), String.valueOf(bit)))
                .collect(Collectors.joining(" "));
        return Messages.format("machine.piece.bus", busText(busName, names.size()), bits);
    }

    /** {@code in[1:0]}, or {@code in[0]} for one bit. */
    static String busText(String busName, int width) {
        return width == 1 ? busName + "[0]" : busName + "[" + (width - 1) + ":0]";
    }
}
