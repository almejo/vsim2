package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ConvertedSelection;
import cl.almejo.vsim.core.library.DefinitionIds;
import cl.almejo.vsim.core.library.DefinitionNames;
import cl.almejo.vsim.core.library.PinOrder;
import cl.almejo.vsim.core.library.TestParts;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
 * case and surrounding spaces), the pin order of each side, the pins' names (only in the component; unique across both
 * sides, ignoring case and surrounding spaces) and, when creating, whether the selection is replaced and
 * which of its test parts (switches, constants, clocks, LEDs, displays, time diagrams) become pins.
 * No Swing here, so the rules are tested headless.
 */
public final class ComponentForm {

    public enum Mode { CREATE, UPDATE }

    /**
     * What the user accepted: a new unique slug id when creating, the target's id when updating, the test parts
     * checked to become pins, the new names of the pins the user renamed (stripped; only those whose name differs
     * from the default one) and the component's «Descripción» (stripped; empty when none).
     */
    public record Result(String definitionId, String name, PinOrder pinOrder, boolean replaceSelection,
                         Set<ElementId> convertedParts, Map<ElementId, String> pinNames, String description) {
        public Result {
            convertedParts = Set.copyOf(convertedParts);
            pinNames = Map.copyOf(pinNames);
            Objects.requireNonNull(description, "description");
        }

        /** A result without a description. */
        public Result(String definitionId, String name, PinOrder pinOrder, boolean replaceSelection,
                      Set<ElementId> convertedParts, Map<ElementId, String> pinNames) {
            this(definitionId, name, pinOrder, replaceSelection, convertedParts, pinNames, "");
        }

        /** A result without renamed pins. */
        public Result(String definitionId, String name, PinOrder pinOrder, boolean replaceSelection,
                      Set<ElementId> convertedParts) {
            this(definitionId, name, pinOrder, replaceSelection, convertedParts, Map.of());
        }

        /** A result without test parts nor renamed pins. */
        public Result(String definitionId, String name, PinOrder pinOrder, boolean replaceSelection) {
            this(definitionId, name, pinOrder, replaceSelection, Set.of());
        }
    }

    /**
     * The pins, in their default order, and their names, that a choice of test parts gives, and what each test part
     * gives ({@link ConvertedSelection#parts()}).
     */
    public record PinPlan(PinOrder order, Map<ElementId, String> names,
                          Map<ElementId, ConvertedSelection.PartPins> parts) {
        public PinPlan {
            Objects.requireNonNull(order, "order");
            names = Map.copyOf(names);
            parts = Map.copyOf(parts);
        }

        /** A plan without test parts. */
        public PinPlan(PinOrder order, Map<ElementId, String> names) {
            this(order, names, Map.of());
        }
    }

    private final Mode mode;
    private final List<TestParts.TestPart> testParts;
    private final Function<Set<ElementId>, PinPlan> planner;
    private final Set<ElementId> convertedParts = new LinkedHashSet<>();
    private Map<ElementId, String> pinNames;
    /** The names the user typed, as typed; kept for pins that disappear (a test part unchecked) and come back. */
    private final Map<ElementId, String> renamedPins = new HashMap<>();
    private Map<ElementId, ConvertedSelection.PartPins> partPins;
    private final List<String> takenNames;
    private final Set<String> takenIds;
    private final List<ComponentDefinition> targets;
    private final List<ElementId> inputs;
    private final List<ElementId> outputs;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private String name;
    /** The component's «Descripción», as typed. */
    private String description;
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
        this.partPins = plan.parts();
        this.takenNames = List.copyOf(takenNames);
        this.takenIds = Set.copyOf(takenIds);
        this.targets = List.copyOf(targets);
        this.inputs = new ArrayList<>(order.inputs());
        this.outputs = new ArrayList<>(order.outputs());
        this.target = targets.isEmpty() ? null : targets.get(0);
        this.name = target == null ? "" : target.name();
        this.description = target == null ? "" : target.description();
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

    /** Chooses which definition an update replaces; the name and the description become that definition's. */
    public void setTarget(ComponentDefinition next) {
        if (!targets.contains(next)) {
            throw new IllegalArgumentException("not a definition of this form: " + next.id());
        }
        target = next;
        name = next.name();
        description = next.description();
        fire();
    }

    /** The «Descripción» the component gets: what Propiedades shows for its instances. Optional. */
    public String description() {
        return description;
    }

    public void setDescription(String newDescription) {
        String next = newDescription == null ? "" : newDescription;
        if (!next.equals(description)) {
            description = next;
            fire();
        }
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
        partPins = plan.parts();
        merge(inputs, plan.order().inputs());
        merge(outputs, plan.order().outputs());
        fire();
    }

    /**
     * What test part {@code id} becomes, ready to show. Checked: the pins it gives, «Entrada (A)», «Salida (Y1)»,
     * «Salidas (D bit 0, D bit 2)», or why it gives none («Sin pin: …»). Unchecked: «Queda como constante», «Reloj
     * interno» or «Se quita».
     */
    public String outcome(ElementId id) {
        TestParts.Kind kind = testParts.stream().filter(part -> part.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("not a test part of this form: " + id)).kind();
        if (!isConverted(id)) {
            return Messages.text(switch (kind) {
                case SWITCH, CONSTANT -> "template.testParts.constant";
                case CLOCK -> "template.testParts.internalClock";
                case LED, SEVEN_SEGMENTS_DISPLAY, TIME_DIAGRAM -> "template.testParts.removed";
            });
        }
        ConvertedSelection.PartPins given = partPins.getOrDefault(id,
                new ConvertedSelection.PartPins(List.of(), Optional.empty()));
        if (given.pins().isEmpty()) {
            return given.sharedWith()
                    .map(pin -> Messages.format("template.testParts.sharedOutput", pinName(pin)))
                    .orElseGet(() -> Messages.text("template.testParts.unconnected"));
        }
        String names = String.join(", ", given.pins().stream().map(this::pinName).toList());
        String key = kind.becomesInput() ? "template.testParts.input"
                : given.pins().size() == 1 ? "template.testParts.output" : "template.testParts.outputs";
        return Messages.format(key, names);
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

    /** The inputs' names in their current order: the user's name (as typed) or the default one. */
    public List<String> inputNames() {
        return inputs.stream().map(this::pinName).toList();
    }

    public List<String> outputNames() {
        return outputs.stream().map(this::pinName).toList();
    }

    /**
     * Gives input {@code index} the name {@code newName}, only in the component (the circuit keeps its labels). Its
     * default name undoes the rename. Blank or repeated names are accepted here and reported by {@link #problem()}.
     */
    public void renameInput(int index, String newName) {
        rename(inputs, index, newName);
    }

    public void renameOutput(int index, String newName) {
        rename(outputs, index, newName);
    }

    /** Why input {@code index}'s name cannot be used (blank, or another pin has it), ready to show. */
    public Optional<String> inputProblem(int index) {
        return pinProblem(inputs, index);
    }

    public Optional<String> outputProblem(int index) {
        return pinProblem(outputs, index);
    }

    private String pinName(ElementId pin) {
        return renamedPins.getOrDefault(pin, pinNames.get(pin));
    }

    private void rename(List<ElementId> pins, int index, String newName) {
        if (index < 0 || index >= pins.size()) {
            return;
        }
        ElementId pin = pins.get(index);
        String next = newName == null ? "" : newName;
        if (next.equals(pinName(pin))) {
            return;
        }
        if (next.equals(pinNames.get(pin))) {
            renamedPins.remove(pin);
        } else {
            renamedPins.put(pin, next);
        }
        fire();
    }

    private Optional<String> pinProblem(List<ElementId> pins, int index) {
        if (index < 0 || index >= pins.size()) {
            return Optional.empty();
        }
        String pinName = pinName(pins.get(index));
        if (pinName == null || pinName.isBlank()) {
            return Optional.of(Messages.text("template.pins.empty"));
        }
        long sameName = visiblePins().stream()
                .map(this::pinName)
                .filter(other -> other != null && DefinitionNames.same(other, pinName))
                .count();
        return sameName > 1 ? Optional.of(Messages.format("template.pins.repeated", pinName.strip()))
                : Optional.empty();
    }

    /** The first pin problem, inputs then outputs, top to bottom. */
    private Optional<String> firstPinProblem() {
        for (int index = 0; index < inputs.size(); index++) {
            Optional<String> found = inputProblem(index);
            if (found.isPresent()) {
                return found;
            }
        }
        for (int index = 0; index < outputs.size(); index++) {
            Optional<String> found = outputProblem(index);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private List<ElementId> visiblePins() {
        List<ElementId> all = new ArrayList<>(inputs);
        all.addAll(outputs);
        return all;
    }

    /** Moves input {@code index} by {@code offset} places; moves that leave the list are ignored. */
    public void moveInput(int index, int offset) {
        move(inputs, index, offset);
    }

    public void moveOutput(int index, int offset) {
        move(outputs, index, offset);
    }

    /**
     * Why OK is disabled, ready to show; empty when the form can be accepted. A pin name problem comes first: it is
     * what the user just typed (the component's name is empty until the user gives it one).
     */
    public Optional<String> problem() {
        Optional<String> pinProblem = firstPinProblem();
        if (pinProblem.isPresent()) {
            return pinProblem;
        }
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
        Map<ElementId, String> renamed = new HashMap<>();
        for (ElementId pin : visiblePins()) {
            String newName = renamedPins.containsKey(pin) ? renamedPins.get(pin).strip() : null;
            if (newName != null && !newName.equals(pinNames.get(pin))) {
                renamed.put(pin, newName);
            }
        }
        return new Result(id, name.strip(), new PinOrder(inputs, outputs), replaceSelection, convertedParts,
                renamed, description.strip());
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
