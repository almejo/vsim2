package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionUpdateSafety;
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
 * ({@link MachineDefinitions#edited}). When the pins stay, the definition is simply replaced (renaming a state does
 * not touch the body or the pieces); when they change (a new name in a condition), the change goes through the
 * planning of an edit inside a component ({@link ReplaceDefinitionCommand#prepareInside}), so every placed piece and
 * the bodies that contain the machine follow with their wires. One undo step; revert restores definitions, pieces and
 * wires.
 *
 * <p>From the machine's tab it is executed like any edit there ({@code OpenDocument.execute}): the
 * {@link EditInsideCommand} wrapping it applies it to the document ({@link EditsDefinitionItself}).
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
        if (!DefinitionUpdateSafety.sameInterface(previous, replacement)) {
            ComponentTypeRegistry registry = STANDARD_TYPES.extendedWith(SubcircuitTypes.resolverFor(document));
            pinChange = ReplaceDefinitionCommand.prepareInside(document, registry,
                    DefinitionBundle.of(replacement, document::definition));
        }
        prepared = true;
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

    @Override
    public String description() {
        return "command.editMachine";
    }
}
