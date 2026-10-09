package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionIds;
import cl.almejo.vsim.core.library.DefinitionNames;
import cl.almejo.vsim.core.library.PinOrder;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the "Crear componente" / "Actualizar componente" dialog edits (spec §5.6): the name (required, unique ignoring
 * case and surrounding spaces), the pin order of each side and, when creating, whether the selection is replaced.
 * No Swing here, so the rules are tested headless.
 */
public final class ComponentForm {

    public enum Mode { CREATE, UPDATE }

    /** What the user accepted: a new unique slug id when creating, the target's id when updating. */
    public record Result(String definitionId, String name, PinOrder pinOrder, boolean replaceSelection) {
    }

    private final Mode mode;
    private final Map<ElementId, String> pinNames;
    private final List<String> takenNames;
    private final Set<String> takenIds;
    private final List<ComponentDefinition> targets;
    private final List<ElementId> inputs;
    private final List<ElementId> outputs;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private String name;
    private ComponentDefinition target;
    private boolean replaceSelection;

    private ComponentForm(Mode mode, PinOrder order, Map<ElementId, String> pinNames, Collection<String> takenNames,
                          Set<String> takenIds, List<ComponentDefinition> targets) {
        this.mode = mode;
        this.pinNames = Map.copyOf(pinNames);
        this.takenNames = List.copyOf(takenNames);
        this.takenIds = Set.copyOf(takenIds);
        this.targets = List.copyOf(targets);
        this.inputs = new ArrayList<>(order.inputs());
        this.outputs = new ArrayList<>(order.outputs());
        this.target = targets.isEmpty() ? null : targets.get(0);
        this.name = target == null ? "" : target.name();
        this.replaceSelection = mode == Mode.CREATE;
    }

    /** {@code takenNames}/{@code takenIds}: what the library and the document already use. */
    public static ComponentForm create(PinOrder order, Map<ElementId, String> pinNames, Collection<String> takenNames,
                                       Set<String> takenIds) {
        return new ComponentForm(Mode.CREATE, order, pinNames, takenNames, takenIds, List.of());
    }

    /** {@code targets}: the definitions the user may replace, the first one preselected. */
    public static ComponentForm update(PinOrder order, Map<ElementId, String> pinNames, Collection<String> takenNames,
                                       List<ComponentDefinition> targets) {
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("there is no definition to update");
        }
        return new ComponentForm(Mode.UPDATE, order, pinNames, takenNames, Set.of(), targets);
    }

    /** The visible reason for a name problem. */
    public static String describe(DefinitionNames.NameProblem problem, String name) {
        return switch (problem) {
            case EMPTY -> Messages.text("template.name.empty");
            case TAKEN -> Messages.format("template.name.taken", name == null ? "" : name.strip());
        };
    }

    public Mode mode() {
        return mode;
    }

    public String name() {
        return name;
    }

    public void setName(String newName) {
        String next = newName == null ? "" : newName;
        if (!next.equals(name)) {
            name = next;
            fire();
        }
    }

    public List<ComponentDefinition> targets() {
        return targets;
    }

    public Optional<ComponentDefinition> target() {
        return Optional.ofNullable(target);
    }

    /** Chooses which definition an update replaces; the name becomes that definition's name. */
    public void setTarget(ComponentDefinition next) {
        if (!targets.contains(next)) {
            throw new IllegalArgumentException("not a definition of this form: " + next.id());
        }
        target = next;
        name = next.name();
        fire();
    }

    public boolean replaceSelection() {
        return replaceSelection;
    }

    public void setReplaceSelection(boolean replace) {
        if (mode == Mode.CREATE && replace != replaceSelection) {
            replaceSelection = replace;
            fire();
        }
    }

    public List<String> inputNames() {
        return inputs.stream().map(pinNames::get).toList();
    }

    public List<String> outputNames() {
        return outputs.stream().map(pinNames::get).toList();
    }

    /** Moves input {@code index} by {@code offset} places; moves that leave the list are ignored. */
    public void moveInput(int index, int offset) {
        move(inputs, index, offset);
    }

    public void moveOutput(int index, int offset) {
        move(outputs, index, offset);
    }

    /** Why OK is disabled, ready to show; empty when the form can be accepted. */
    public Optional<String> problem() {
        List<String> others = mode == Mode.UPDATE
                ? takenNames.stream().filter(taken -> !DefinitionNames.same(taken, target.name())).toList()
                : takenNames;
        return DefinitionNames.problem(name, others).map(problem -> describe(problem, name));
    }

    public boolean canAccept() {
        return problem().isEmpty();
    }

    public Result result() {
        if (!canAccept()) {
            throw new IllegalStateException("the form cannot be accepted: " + problem().orElse(""));
        }
        String id = mode == Mode.CREATE ? DefinitionIds.slug(name, takenIds) : target.id();
        return new Result(id, name.strip(), new PinOrder(inputs, outputs), replaceSelection);
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void move(List<ElementId> pins, int index, int offset) {
        int destination = index + offset;
        if (offset == 0 || index < 0 || index >= pins.size() || destination < 0 || destination >= pins.size()) {
            return;
        }
        pins.add(destination, pins.remove(index));
        fire();
    }

    private void fire() {
        listeners.forEach(Runnable::run);
    }
}
