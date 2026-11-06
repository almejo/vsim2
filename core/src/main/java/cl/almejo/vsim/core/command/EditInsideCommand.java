package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.BodyEdits;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionGraph;
import cl.almejo.vsim.core.library.DefinitionUpdateProblem;
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * An edit made inside a component's tab (spec §6.1, §10.4): {@code bodyCommand} changes the working copy of the
 * definition's body, and the definition is written back into the document with the planning of «Actualizar
 * componente…» under the inside rules ({@link ReplaceDefinitionCommand#prepareInside}): every placed piece follows,
 * and so do the pieces inside the bodies of the definitions that contain it (spec §6.2 c), one undo step of the
 * document. Applied to the document; the working copy is the one it was made on.
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
        if (prepared && !copyMatches(document)) {
            if (writeBack != null) {
                writeBack.apply(document);
            }
            bringCopyToTheDefinition(document);
            return;
        }
        List<ComponentDefinition> definitionsBefore = prepared ? List.of() : workingCopy.definitions();
        bodyCommand.apply(workingCopy);
        try {
            if (!prepared) {
                requireOtherDefinitionsUnchanged(definitionsBefore);
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

    /**
     * Refuses an edit that changed or dropped one of the working copy's definitions (a nested piece's description,
     * shape or version): only the edited definition's body is written back, so such a change would stay in the tab
     * and never reach the document. The editor runs those commands on the document itself; this keeps any other
     * caller from losing them silently (spec §6.2: never silently). Definitions the edit added (a paste) are fine.
     */
    private void requireOtherDefinitionsUnchanged(List<ComponentDefinition> definitionsBefore) {
        for (ComponentDefinition before : definitionsBefore) {
            if (workingCopy.definition(before.id()).filter(after -> after == before || after.equals(before)).isEmpty()) {
                throw new InsideEditRefusedException("inside.otherDefinitionChanged", List.of(before.name()));
            }
        }
    }

    /** The replacement of the definition by the working copy's body, or null when the body is as it was. */
    private ReplaceDefinitionCommand prepareWriteBack(CircuitDocument document, ComponentDefinition previous) {
        ComponentDefinition replacement = BodyEdits.rebuilt(previous, workingCopy);
        if (replacement.sameContentAs(previous)) {
            return null;
        }
        Function<String, Optional<ComponentDefinition>> lookup =
                id -> workingCopy.definition(id).or(() -> document.definition(id));
        requireNotNestingItself(replacement, lookup);
        // definitions pasted into the body go to the document (spec §6.5); the ones it has already stay as they are
        DefinitionBundle bundle = DefinitionBundle.of(replacement, lookup);
        return ReplaceDefinitionCommand.prepareInside(document, registry, bundle);
    }

    /**
     * Refuses a body that would hold the definition itself or a piece of a definition that contains it (a paste, a
     * dropped file or the AI go around the palette's grey entries, spec §6.5), with the palette's explanation and the
     * name of the piece's component, instead of the cycle's {@link IllegalArgumentException}.
     */
    private void requireNotNestingItself(ComponentDefinition replacement,
                                         Function<String, Optional<ComponentDefinition>> lookup) {
        for (String usedId : replacement.usedDefinitionIds()) {
            if (usedId.equals(definitionId)) {
                throw new InsideEditRefusedException("inside.cannotNestItself", List.of());
            }
            Optional<ComponentDefinition> used = lookup.apply(usedId);
            if (used.isPresent() && reaches(used.get(), lookup)) {
                throw new InsideEditRefusedException("inside.pieceContainsThisComponent", List.of(used.get().name()));
            }
        }
    }

    /** True when {@code definition}'s body uses the edited definition at any depth. */
    private boolean reaches(ComponentDefinition definition, Function<String, Optional<ComponentDefinition>> lookup) {
        Function<String, Optional<ComponentDefinition>> stopAtEdited =
                id -> id.equals(definitionId) ? Optional.empty() : lookup.apply(id);
        return DefinitionGraph.knownClosure(List.of(definition.id()), stopAtEdited).stream()
                .anyMatch(reached -> reached.usedDefinitionIds().contains(definitionId));
    }

    @Override
    public void revert(CircuitDocument document) {
        if (!copyMatches(document)) {
            if (writeBack != null) {
                writeBack.revert(document);
            }
            bringCopyToTheDefinition(document);
            return;
        }
        bodyCommand.revert(workingCopy);
        if (writeBack != null) {
            writeBack.revert(document);
        }
    }

    /**
     * True when the working copy is the document's definition as it is now, so {@link #bodyCommand} can be replayed
     * on it. False for a copy no longer followed: its tab closed after it had followed a change made elsewhere, and
     * that change was undone since (final review I4).
     */
    private boolean copyMatches(CircuitDocument document) {
        return document.definition(definitionId)
                .map(current -> BodyEdits.rebuilt(current, workingCopy).sameContentAs(current))
                .orElse(false);
    }

    /**
     * Brings a copy that no longer matches to the definition, ids kept, instead of replaying the edit on it: the
     * document is the truth and the copy only a view of it, so undo and redo never stick on a stale copy.
     */
    void bringCopyToTheDefinition(CircuitDocument document) {
        document.definition(definitionId).ifPresent(definition -> {
            ReplaceContentCommand refresh = ReplaceContentCommand.between(workingCopy,
                    definition.bodyDocument(document), "inside.refresh");
            if (!refresh.changesNothing()) {
                refresh.apply(workingCopy);
            }
        });
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

    /**
     * What the rewrite of the bodies that contain the definition could not carry (spec §6.2 c); empty before the
     * first apply.
     */
    public List<DefinitionUpdateProblem> nestedProblems() {
        return writeBack == null ? List.of() : writeBack.nestedProblems();
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
