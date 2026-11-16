package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.DefinitionUpdateSafety;
import cl.almejo.vsim.core.library.RemovedPinWire;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One edit of a «Máquina de estados»'s diagram (a state drawn, an arrow, a condition, an output; spec §2.2): puts
 * {@code newMachine}, with the names now in use as pins and the body generated again
 * ({@link MachineDefinitions#edited}). When the pins stay (also in order), the definition is simply replaced
 * (renaming a state does not touch the body or the pieces); when they change (a new name in a condition, two pins
 * swapped), the change goes through the
 * planning of an edit inside a component ({@link ReplaceDefinitionCommand#prepareInside}), so every placed piece and
 * the bodies that contain the machine follow with their wires. One undo step; revert restores definitions, pieces and
 * wires.
 *
 * <p>It applies to the circuit document that owns the definition: the machine's tab executes it on its parent
 * document's history (one undo step there), and the tab's working copy follows the new body as it follows any change
 * made elsewhere. Wrapped in an {@link EditInsideCommand} it works too ({@link EditsDefinitionItself}).
 */
public final class EditMachineCommand implements EditsDefinitionItself, LoosensPins {

    /** The built-in types; the document's own definitions are added per apply. */
    private static final ComponentTypeRegistry STANDARD_TYPES = StandardComponentTypes.registry();

    private final String definitionId;
    private final StateMachine newMachine;
    private ComponentDefinition previous;
    private ComponentDefinition replacement;
    /** The planned update when the pins change; null for a plain replacement or before the first apply. */
    private ReplaceDefinitionCommand pinChange;
    private boolean prepared;

    public EditMachineCommand(String definitionId, StateMachine newMachine) {
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.newMachine = Objects.requireNonNull(newMachine, "newMachine");
    }

    /**
     * @throws IllegalArgumentException when the document has no definition {@code definitionId} or it is not a
     *                                  machine
     */
    @Override
    public void apply(CircuitDocument document) {
        if (!prepared) {
            prepare(document);
        }
        if (pinChange != null) {
            pinChange.apply(document);
        } else if (replacement != previous) {
            document.putDefinition(replacement);
        }
    }

    /** Plans the edit against the document as it is now; a redo reuses the plan (the history restores that state). */
    private void prepare(CircuitDocument document) {
        previous = document.definition(definitionId).orElseThrow(() -> new IllegalArgumentException(
                "no definition " + definitionId + " in the document"));
        replacement = MachineDefinitions.edited(previous, newMachine);
        if (!DefinitionUpdateSafety.sameInterface(previous, replacement)
                || !sourcesInOrder(previous).equals(sourcesInOrder(replacement))) {
            ComponentTypeRegistry registry = STANDARD_TYPES.extendedWith(SubcircuitTypes.resolverFor(document));
            pinChange = ReplaceDefinitionCommand.prepareInside(document, registry,
                    DefinitionBundle.of(replacement, document::definition));
        }
        prepared = true;
    }

    /**
     * The pins' sources in pinout order. Two pins of the same width swapped (Alt+↓ on «x» above «y») leave every
     * place, direction and width as it was, so {@link DefinitionUpdateSafety#sameInterface} sees no change; their
     * sources did move, and only the planned update makes each wire follow its own pin (hito 3 review, C1).
     */
    private static List<ElementId> sourcesInOrder(ComponentDefinition definition) {
        return definition.pinout().stream().map(DefinitionPin::source).toList();
    }

    @Override
    public void revert(CircuitDocument document) {
        if (pinChange != null) {
            pinChange.revert(document);
        } else if (replacement != previous) {
            document.putDefinition(previous);
        }
    }

    @Override
    public String definitionId() {
        return definitionId;
    }

    /** The machine asked for (before its names in use become pins). */
    public StateMachine newMachine() {
        return newMachine;
    }

    @Override
    public Optional<ReplaceDefinitionCommand> plannedUpdate() {
        return Optional.ofNullable(pinChange);
    }

    /** The pins of placed pieces left unconnected by a pin change; empty before the first apply. */
    @Override
    public List<PinRef> loosenedPins() {
        return pinChange == null ? List.of() : pinChange.loosenedPins();
    }

    /**
     * The wire ends left loose because their pin went away (a pin removed, or a side turned «Como un bus» or back),
     * on the placed pieces and in the bodies that hold them, by the name the pin had; empty before the first apply.
     */
    public List<RemovedPinWire> removedPinWires() {
        return pinChange == null ? List.of() : pinChange.removedPinWires();
    }

    @Override
    public String description() {
        return "command.editMachine";
    }
}
