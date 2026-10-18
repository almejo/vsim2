package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionIds;
import cl.almejo.vsim.core.library.DefinitionNames;
import cl.almejo.vsim.core.library.PinOrder;
import cl.almejo.vsim.core.library.TestParts;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * What the "Crear componente" / "Actualizar componente" dialog edits (spec §5.6): the name (required, unique ignoring
 * case and surrounding spaces), the pin order of each side and, when creating, whether the selection is replaced and
 * which of its test parts (switches, constants, clocks, LEDs, displays, time diagrams) become pins.
 * No Swing here, so the rules are tested headless.
 */
public final class ComponentForm {

    public enum Mode { CREATE, UPDATE }

    /**
     * What the user accepted: a new unique slug id when creating, the target's id when updating, and the test parts
     * checked to become pins.
     */
    public record Result(String definitionId, String name, PinOrder pinOrder, boolean replaceSelection,
                         Set<ElementId> convertedParts) {
        public Result {
            convertedParts = Set.copyOf(convertedParts);
        }

        /** A result without test parts. */
        public Result(String definitionId, String name, PinOrder pinOrder, boolean replaceSelection) {
            this(definitionId, name, pinOrder, replaceSelection, Set.of());
        }
    }

    /** The pins, in their default order, and their names, that a choice of test parts gives. */
    public record PinPlan(PinOrder order, Map<ElementId, String> names) {
        public PinPlan {
            Objects.requireNonNull(order, "order");
            names = Map.copyOf(names);
        }
    }

    private final Mode mode;
    private final List<TestParts.TestPart> testParts;
    private final Function<Set<ElementId>, PinPlan> planner;
    private final Set<ElementId> convertedParts = new LinkedHashSet<>();
    private Map<ElementId, String> pinNames;
    private final List<String> takenNames;
    private final Set<String> takenIds;
    private final List<ComponentDefinition> targets;
    private final List<ElementId> inputs;
    private final List<ElementId> outputs;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private String name;
    private ComponentDefinition target;
    private boolean replaceSelection;

    private ComponentForm(Mode mode, List<TestParts.TestPart> testParts, Function<Set<ElementId>, PinPlan> planner,
                          Collection<String> takenNames, Set<String> takenIds, List<ComponentDefinition> targets) {
        this.mode = mode;
        this.testParts = List.copyOf(testParts);
        this.planner = planner;
        testParts.forEach(part -> convertedParts.add(part.id()));
        PinPlan plan = planner.apply(Set.copyOf(convertedParts));
        PinOrder order = plan.order();
        this.pinNames = plan.names();
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
        return create(List.of(), fixed(order, pinNames), takenNames, takenIds);
    }

    /**
     * A create form that also offers the selection's {@code testParts} (position order), all checked at first.
     * {@code planner} gives the pins and names for a set of checked parts (the selection converted headless).
     */
    public static ComponentForm create(List<TestParts.TestPart> testParts, Function<Set<ElementId>, PinPlan> planner,
                                       Collection<String> takenNames, Set<String> takenIds) {
        return new ComponentForm(Mode.CREATE, testParts, planner, takenNames, takenIds, List.of());
    }

    private static Function<Set<ElementId>, PinPlan> fixed(PinOrder order, Map<ElementId, String> pinNames) {
        PinPlan plan = new PinPlan(order, pinNames);
        return converted -> plan;
    }

    /** {@code targets}: the definitions the user may replace, the first one preselected. */
    public static ComponentForm update(PinOrder order, Map<ElementId, String> pinNames, Collection<String> takenNames,
                                       List<ComponentDefinition> targets) {
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("there is no definition to update");
        }
        return new ComponentForm(Mode.UPDATE, List.of(), fixed(order, pinNames), takenNames, Set.of(), targets);
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

    /** The selection's test parts in position order; empty when it has none (the dialog is then as before). */
    public List<TestParts.TestPart> testParts() {
        return testParts;
    }

    /** True when test part {@code id} is checked: it becomes pins (a clock: «Entrada», not «Reloj interno»). */
    public boolean isConverted(ElementId id) {
        return convertedParts.contains(id);
    }

    /**
     * Checks or unchecks test part {@code id}: its pins appear or disappear. The pins that stay keep the order the user
     * gave them; a pin that appears goes right after the pin before it in the default order.
     */
    public void setConverted(ElementId id, boolean converted) {
        if (testParts.stream().noneMatch(part -> part.id().equals(id))) {
            throw new IllegalArgumentException("not a test part of this form: " + id);
        }
        boolean changed = converted ? convertedParts.add(id) : convertedParts.remove(id);
        if (!changed) {
            return;
        }
        PinPlan plan = planner.apply(Set.copyOf(convertedParts));
        pinNames = plan.names();
        merge(inputs, plan.order().inputs());
        merge(outputs, plan.order().outputs());
        fire();
    }

    /** {@code current} becomes {@code wanted}'s pins, keeping the current order of those it already had. */
    private static void merge(List<ElementId> current, List<ElementId> wanted) {
        current.retainAll(wanted);
        for (int index = 0; index < wanted.size(); index++) {
            ElementId pin = wanted.get(index);
            if (current.contains(pin)) {
                continue;
            }
            int place = 0;
            for (int before = index - 1; before >= 0; before--) {
                int found = current.indexOf(wanted.get(before));
                if (found >= 0) {
                    place = found + 1;
                    break;
                }
            }
            current.add(place, pin);
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
        return new Result(id, name.strip(), new PinOrder(inputs, outputs), replaceSelection, convertedParts);
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
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
