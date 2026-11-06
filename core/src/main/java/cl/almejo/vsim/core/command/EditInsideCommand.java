package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.BodyEdits;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An edit made inside a component's tab (spec §6.1, §10.4): {@code bodyCommand} changes the working copy of the
 * definition's body, and the definition is written back into the document with the planning of «Actualizar
 * componente…» under the inside rules ({@link ReplaceDefinitionCommand#prepareInside}): every placed piece follows,
 * one undo step of the document. Applied to the document; the working copy is the one it was made on.
 *
 * <p>The working copy changes first and goes back first, so whoever watches the document (a body tab refreshing its
 * copy) always finds the copy already matching the definition. The write-back prepared on the first apply is reused by
 * a redo: the history puts the document back in the state it was prepared against.
 */
public final class EditInsideCommand implements WrapsCommand, LoosensPins {

    private final String definitionId;
    private final Command bodyCommand;
    private final CircuitDocument workingCopy;
    private final ComponentTypeRegistry registry;
    /** The write-back, prepared on the first apply; null while not applied yet or when the body did not change. */
    private ReplaceDefinitionCommand writeBack;
    private boolean prepared;
    private String placeName = "";

    public EditInsideCommand(String definitionId, Command bodyCommand, CircuitDocument workingCopy,
                             ComponentTypeRegistry registry) {
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.bodyCommand = Objects.requireNonNull(bodyCommand, "bodyCommand");
        this.workingCopy = Objects.requireNonNull(workingCopy, "workingCopy");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void apply(CircuitDocument document) {
        ComponentDefinition previous = document.definition(definitionId).orElseThrow(() ->
                new IllegalStateException("the document has no definition " + definitionId));
        placeName = previous.name();
        bodyCommand.apply(workingCopy);
        try {
            if (!prepared) {
                writeBack = prepareWriteBack(document, previous);
                prepared = true;
            }
            if (writeBack != null) {
                writeBack.apply(document);
            }
        } catch (RuntimeException exception) {
            bodyCommand.revert(workingCopy);
            throw exception;
        }
    }

    /** The replacement of the definition by the working copy's body, or null when the body is as it was. */
    private ReplaceDefinitionCommand prepareWriteBack(CircuitDocument document, ComponentDefinition previous) {
        ComponentDefinition replacement = BodyEdits.rebuilt(previous, workingCopy);
        if (replacement.sameContentAs(previous)) {
            return null;
        }
        // definitions pasted into the body go to the document (spec §6.5); the ones it has already stay as they are
        DefinitionBundle bundle = DefinitionBundle.of(replacement,
                id -> workingCopy.definition(id).or(() -> document.definition(id)));
        return ReplaceDefinitionCommand.prepareInside(document, registry, bundle);
    }

    @Override
    public void revert(CircuitDocument document) {
        bodyCommand.revert(workingCopy);
        if (writeBack != null) {
            writeBack.revert(document);
        }
    }

    @Override
    public String description() {
        return bodyCommand.description();
    }

    /** The name of the definition edited, as it was when the edit was applied (the undo text names the place). */
    public String placeName() {
        return placeName;
    }

    public String definitionId() {
        return definitionId;
    }

    /** The edit made on the body: what the caller executed inside the component. */
    @Override
    public Command wrapped() {
        return bodyCommand;
    }

    /** The working copy this edit was made on. */
    public CircuitDocument workingCopy() {
        return workingCopy;
    }

    /** The placed pieces that could not take a new pin where the definition puts it; empty before the first apply. */
    public List<KeptOldShape> keptOldShapes() {
        return writeBack == null ? List.of() : writeBack.keptOldShapes();
    }

    @Override
    public List<PinRef> loosenedPins() {
        return writeBack == null ? List.of() : writeBack.loosenedPins();
    }

    @Override
    public boolean changesNothing() {
        return bodyCommand.changesNothing();
    }

    /**
     * A run of edits in the same definition and working copy whose inner commands merge (keyboard nudges, a label
     * typed) is one undo step, as in the main circuit.
     */
    @Override
    public Optional<Command> mergedWith(Command next) {
        if (!(next instanceof EditInsideCommand following) || !following.definitionId.equals(definitionId)
                || following.workingCopy != workingCopy) {
            return Optional.empty();
        }
        return bodyCommand.mergedWith(following.bodyCommand)
                .map(merged -> new EditInsideCommand(definitionId, merged, workingCopy, registry));
    }
}
