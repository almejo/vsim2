package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Palette ▸ Memoria ▸ «Máquina de estados» (spec §1 «Create»): puts a new machine's {@code definition} in the
 * document and places one piece of it with its top-left at {@code origin}, as one undo step. The piece's id is taken
 * on the first apply and kept for a redo.
 */
public final class CreateMachineCommand implements Command {

    private final ComponentDefinition definition;
    private final GridPoint origin;
    private PutDefinitionsCommand putDefinition;
    private AddElementsCommand addPiece;
    private ElementId instanceId;

    /** @throws IllegalArgumentException when {@code definition} is not a machine */
    public CreateMachineCommand(ComponentDefinition definition, GridPoint origin) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.origin = Objects.requireNonNull(origin, "origin");
        if (!definition.isMachine()) {
            throw new IllegalArgumentException(definition.id() + " is not a state machine");
        }
    }

    /** @throws IllegalArgumentException when the document already has a definition with the machine's id */
    @Override
    public void apply(CircuitDocument document) {
        if (instanceId == null) {
            if (document.definition(definition.id()).isPresent()) {
                throw new IllegalArgumentException("the document already has a definition " + definition.id());
            }
            instanceId = document.newComponentId();
            Component piece = new Component(instanceId, SubcircuitTypes.typeIdOf(definition.id()), origin,
                    Rotation.R0, Map.of(), "");
            putDefinition = new PutDefinitionsCommand(List.of(definition));
            addPiece = new AddElementsCommand(List.of(piece));
        }
        document.batch(() -> {
            putDefinition.apply(document);
            try {
                addPiece.apply(document);
            } catch (RuntimeException exception) {
                putDefinition.revert(document);
                throw exception;
            }
        });
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> {
            addPiece.revert(document);
            putDefinition.revert(document);
        });
    }

    /** The placed piece, so the editor can select it; empty before the first apply. */
    public Optional<ElementId> instanceId() {
        return Optional.ofNullable(instanceId);
    }

    public ComponentDefinition definition() {
        return definition;
    }

    @Override
    public String description() {
        return "command.createMachine";
    }
}
