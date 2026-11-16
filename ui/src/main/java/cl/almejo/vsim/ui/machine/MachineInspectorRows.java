package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.logic.ExpressionProblem;
import cl.almejo.vsim.core.machine.MachineConditions;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.simulation.MachineProblem;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.karnaugh.ExpressionProblemText;
import cl.almejo.vsim.ui.problems.MachineProblemText;
import cl.almejo.vsim.ui.theme.WrappedErrorText;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * The right panel's «Propiedades» in a machine's tab (spec 2026-11-14 §1 «The machine tab», the mockup's panel): for
 * a selected state its name, its Moore outputs written {@code Z=1, Y=0} and «Estado inicial»; for an arrow where it
 * goes and its condition, with what is wrong in it; with nothing selected, the machine's states and pins and how to
 * draw. A text applies on Enter or when the focus leaves it, each change one undo step; a wrong name or outputs text
 * only shows what is wrong, while a wrong condition is kept as written and says what is wrong (spec «a bad condition is
 * kept as text»). An arrow also has its Mealy outputs, and the machine's own rows say whether it is Moore, Mealy or
 * both. A change of the machine rebuilds the rows only when what they show changed (another element, another kind of
 * rows); otherwise it updates, in place, the fields that have no focus and no typing waiting for Enter, so what the
 * user is typing is never lost and the focus stays in the field after Enter. The pin list comes in a later milestone.
 * Under the rows, the problems of what is selected (what makes it glow), or with nothing selected the machine's
 * problems, each one with a «Ver» that shows it on the diagram; they follow every change and the running copy.
 */
final class MachineInspectorRows extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String WIDE = "span 2, growx, wmin 0";
    private static final int UNIT_INCREMENT = 16;
    private static final String MACHINE_ROWS = "machine";
    private static final String SEVERAL_ROWS = "several:";
    private static final String NO_ROWS = "none";

    private final transient MachineCanvas canvas;
    private final transient MachineSelection selection;
    private final transient Runnable stopFollowingSelection;
    private final transient Runnable stopFollowingMachine;
    private final WrappedErrorText problem = new WrappedErrorText(" ");
    private transient Optional<JTextField> nameField = Optional.empty();
    private transient Optional<JTextField> machineNameField = Optional.empty();
    private transient Optional<JTextField> outputsField = Optional.empty();
    private transient Optional<JTextField> conditionField = Optional.empty();
    private transient Optional<JTextField> mealyOutputsField = Optional.empty();
    private transient Optional<JLabel> kindLabel = Optional.empty();
    /** What each field of the rows shown would say now; run after a change that does not rebuild them. */
    private final transient List<Runnable> refreshers = new ArrayList<>();
    /** The fields' applies of what was typed and not applied yet, run before the rows change for another selection. */
    private final transient List<Runnable> pendingApplies = new ArrayList<>();
    private boolean disposed;
    /** What the rows were built for: the selected element, and the kind of rows. */
    private transient String builtFor = "";
    private transient Optional<JCheckBox> initialBox = Optional.empty();
    private transient Optional<JLabel> arrowLabel = Optional.empty();
    /** True while this panel's own edit runs: the change it makes does not rebuild the rows under the user. */
    private boolean applying;
    /** Shows a problem on the diagram (selects, centres and blinks what it is about). */
    private final transient Consumer<MachineFinding> reveal;
    private final transient MachineLibrary library;
    private final transient Runnable stopFollowingFrames;
    /** The problems under the rows: a sentence and its «Ver» each. */
    private final JPanel problemList = new JPanel(new MigLayout("wrap 2, insets 0, hidemode 3",
            "[grow, fill, 60::][]"));
    private transient List<MachineFinding> listedProblems = List.of();
    private final transient List<String> problemLines = new ArrayList<>();
    private final transient List<AbstractButton> problemLinks = new ArrayList<>();

    /**
     * The rows of {@code canvas}'s machine; a problem's «Ver» calls {@code reveal}. {@code library} is «Mis
     * componentes»: its names are taken for the machine's, and a machine in it is renamed there too.
     */
    MachineInspectorRows(MachineCanvas canvas, Consumer<MachineFinding> reveal, MachineLibrary library) {
        super(new MigLayout("wrap 2, insets dialog", "[left][grow, fill, 60::]"));
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.library = Objects.requireNonNull(library, "library");
        this.reveal = Objects.requireNonNull(reveal, "reveal");
        this.selection = canvas.selection();
        problemList.setOpaque(false);
        problemList.setVisible(false);
        stopFollowingSelection = selection.addListener(this::selectionChanged);
        stopFollowingMachine = canvas.addChangeListener(this::machineChanged);
        // a running copy's problems (an input in Z at the edge) come with the simulation, not with a change
        stopFollowingFrames = canvas.addFrameListener(this::refreshProblemList);
        rebuild();
    }

    /** This panel in a scroll pane that only scrolls up and down, as the circuit's «Propiedades». */
    JScrollPane inScrollPane() {
        JScrollPane scroll = new JScrollPane(this, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(UNIT_INCREMENT);
        return scroll;
    }

    /** A condition's problem as the user reads it: «Falta cerrar un paréntesis (posición 3)». */
    static String describe(ExpressionProblem expressionProblem) {
        return ExpressionProblemText.describe(expressionProblem);
    }

    /** What the panel says is wrong now; empty when it says nothing. */
    Optional<String> problemText() {
        return problem.isVisible() && !problem.getText().isBlank() ? Optional.of(problem.getText()) : Optional.empty();
    }

    /** «S1 → S2» for a selected arrow; empty otherwise. */
    Optional<String> arrowText() {
        return arrowLabel.map(JLabel::getText);
    }

    /** The problems listed under the rows, as they read («Error: …», «Aviso: …»). */
    List<String> problemLines() {
        return List.copyOf(problemLines);
    }

    /** The «Ver» of each listed problem, in the order of {@link #problemLines()}. */
    List<AbstractButton> problemLinks() {
        return List.copyOf(problemLinks);
    }

    Optional<JTextField> nameField() {
        return nameField;
    }

    /** The machine's own «Nombre» (its component's name), shown with nothing selected. */
    Optional<JTextField> machineNameField() {
        return machineNameField;
    }

    Optional<JTextField> outputsField() {
        return outputsField;
    }

    Optional<JTextField> conditionField() {
        return conditionField;
    }

    Optional<JTextField> mealyOutputsField() {
        return mealyOutputsField;
    }

    /** «Moore», «Mealy» or «Moore y Mealy» in the machine's own rows; empty when another element is selected. */
    Optional<String> kindText() {
        return kindLabel.map(JLabel::getText);
    }

    Optional<JCheckBox> initialBox() {
        return initialBox;
    }

    /** Stops following the diagram (the tab leaves machine mode). */
    void dispose() {
        disposed = true;
        stopFollowingSelection.run();
        stopFollowingMachine.run();
        stopFollowingFrames.run();
    }

    private void selectionChanged() {
        if (builtFor.equals(shownFor())) {
            return;
        }
        // a click on the diagram changes the selection before the field hears it lost the focus: apply it first
        List.copyOf(pendingApplies).forEach(Runnable::run);
        if (!builtFor.equals(shownFor())) {
            rebuild();
        }
    }

    private void machineChanged() {
        if (applying) {
            return;
        }
        refreshOrRebuild();
    }

    /** Rebuilds when the rows must change; otherwise updates the fields that nobody is typing in. */
    private void refreshOrRebuild() {
        if (!builtFor.equals(shownFor()) || builtFor.startsWith(MACHINE_ROWS) || builtFor.startsWith(SEVERAL_ROWS)
                || builtFor.equals(NO_ROWS)) {
            rebuild();
        } else {
            refreshers.forEach(Runnable::run);
            refreshProblemList();
        }
    }

    /** What the rows would be built for now. */
    private String shownFor() {
        Optional<StateMachine> machine = canvas.machine();
        if (machine.isEmpty()) {
            return NO_ROWS;
        }
        int count = selection.stateIds().size() + selection.transitionIds().size();
        if (count == 0) {
            return MACHINE_ROWS;
        }
        if (count > 1) {
            return SEVERAL_ROWS + count;
        }
        if (!selection.stateIds().isEmpty()) {
            String stateId = selection.stateIds().iterator().next();
            return machine.get().state(stateId).isPresent() ? "state:" + stateId : NO_ROWS;
        }
        String transitionId = selection.transitionIds().iterator().next();
        return machine.get().transition(transitionId)
                .map(transition -> "transition:" + transitionId + ":" + transition.isOtherwise())
                .orElse(NO_ROWS);
    }

    private void rebuild() {
        removeAll();
        refreshers.clear();
        pendingApplies.clear();
        nameField = Optional.empty();
        machineNameField = Optional.empty();
        outputsField = Optional.empty();
        conditionField = Optional.empty();
        mealyOutputsField = Optional.empty();
        kindLabel = Optional.empty();
        initialBox = Optional.empty();
        arrowLabel = Optional.empty();
        showProblem(Optional.empty());
        builtFor = shownFor();
        canvas.machine().ifPresent(this::showSelected);
        add(problem, WIDE);
        refreshProblemList();
        add(problemList, WIDE);
        revalidate();
        repaint();
    }

    /** Lists the problems of what is selected now, when they changed. */
    private void refreshProblemList() {
        List<MachineFinding> current = problemsOfSelection();
        if (current.equals(listedProblems)) {
            return;
        }
        listedProblems = current;
        problemList.removeAll();
        problemLines.clear();
        problemLinks.clear();
        problemList.setVisible(!current.isEmpty());
        if (!current.isEmpty()) {
            StateMachine machine = canvas.machine().orElseThrow();
            JLabel header = boldLabel(Messages.text("machine.properties.problems"));
            problemList.add(header, "span 2, gaptop 6");
            current.forEach(finding -> addProblemLine(machine, finding));
        }
        problemList.revalidate();
        problemList.repaint();
    }

    /**
     * The problems that make the selected state or arrow glow (an arrow's unreadable condition is already said by its
     * field's line), the machine's own and its running copy's with nothing selected, none for several.
     */
    private List<MachineFinding> problemsOfSelection() {
        if (canvas.machine().isEmpty()) {
            return List.of();
        }
        int count = selection.stateIds().size() + selection.transitionIds().size();
        if (count == 0) {
            return canvas.problems().stream().map(MachineProblem::finding).toList();
        }
        if (count > 1) {
            return List.of();
        }
        MachineGlows glows = canvas.glows();
        if (!selection.stateIds().isEmpty()) {
            return glows.findingsOfState(selection.stateIds().iterator().next());
        }
        return glows.findingsOfTransition(selection.transitionIds().iterator().next()).stream()
                .filter(finding -> finding.kind() != MachineFinding.Kind.BAD_CONDITION)
                .toList();
    }

    private void addProblemLine(StateMachine machine, MachineFinding finding) {
        String sentence = MachineProblemText.sentence(finding, machine);
        String line = Messages.format(finding.isWarning() ? "machine.properties.problem.warning"
                : "machine.properties.problem.error", sentence);
        JTextArea text = finding.isWarning() ? new Hint(line, false) : new WrappedErrorText(line);
        JButton show = new JButton(Messages.text("machine.properties.problem.show"));
        // a plain button with its frame, not a borderless one (hand test of hito 2: it read as plain text)
        show.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        show.setToolTipText(Messages.text("machine.properties.problem.showTooltip"));
        show.getAccessibleContext().setAccessibleDescription(sentence);
        show.addActionListener(event -> reveal.accept(finding));
        problemList.add(text, "wmin 0");
        problemList.add(show, "top");
        problemLines.add(line);
        problemLinks.add(show);
    }

    private void showSelected(StateMachine machine) {
        int count = selection.stateIds().size() + selection.transitionIds().size();
        if (count == 0) {
            showMachine(machine);
        } else if (count > 1) {
            addHeader(Messages.format("machine.properties.several", count));
        } else if (!selection.stateIds().isEmpty()) {
            machine.state(selection.stateIds().iterator().next()).ifPresent(state -> showState(machine, state));
        } else {
            machine.transition(selection.transitionIds().iterator().next())
                    .ifPresent(transition -> showTransition(machine, transition));
        }
    }

    private void showMachine(StateMachine machine) {
        addHeader(Messages.text("machine.properties.machine"));
        machineNameField = Optional.of(textRow(Messages.text("machine.properties.machineName"),
                MachineNaming.nameOf(canvas.place(), canvas.definitionId()).orElse(""),
                machineNow -> MachineNaming.nameOf(canvas.place(), canvas.definitionId()),
                this::applyMachineName));
        addRow(Messages.text("machine.properties.states"), new JLabel(String.valueOf(machine.states().size())));
        addRow(Messages.text("machine.properties.inputs"), new JLabel(listed(machine.inputNames())));
        addRow(Messages.text("machine.properties.outputs"), new JLabel(listed(machine.outputNames())));
        JLabel kind = new JLabel(kindOf(machine));
        addRow(Messages.text("machine.properties.kind"), kind);
        kindLabel = Optional.of(kind);
        add(hint(Messages.text("machine.properties.howToDraw")), WIDE);
    }

    /** Where the machine assigns outputs: in its states (Moore), on its arrows (Mealy) or both; Moore when nowhere. */
    static String kindOf(StateMachine machine) {
        boolean mealy = machine.transitions().stream().anyMatch(transition -> !transition.outputs().isEmpty());
        boolean moore = machine.states().stream().anyMatch(state -> !state.outputs().isEmpty());
        if (mealy && moore) {
            return Messages.text("machine.properties.kind.both");
        }
        return Messages.text(mealy ? "machine.properties.kind.mealy" : "machine.properties.kind.moore");
    }

    private static String listed(List<String> names) {
        return names.isEmpty() ? Messages.text("machine.properties.none") : String.join(", ", names);
    }

    private void showState(StateMachine machine, MachineState state) {
        addHeader(Messages.text("machine.properties.state"));
        JTextField name = textRow(Messages.text("machine.properties.stateName"), state.name(),
                machineNow -> machineNow.state(state.id()).map(MachineState::name),
                text -> applyName(state.id(), text));
        JTextField outputs = textRow(Messages.text("machine.properties.mooreOutputs"),
                MachineOutputsText.write(state.outputs()),
                machineNow -> machineNow.state(state.id()).map(found -> MachineOutputsText.write(found.outputs())),
                text -> applyOutputs(state.id(), text));
        add(hint(Messages.text("machine.properties.mooreOutputsHint")), WIDE);
        JCheckBox initial = new JCheckBox(Messages.text("machine.properties.initial"),
                machine.initialStateId().filter(state.id()::equals).isPresent());
        initial.addActionListener(event -> apply(current ->
                MachineEdits.withInitial(current, state.id(), initial.isSelected())));
        refreshers.add(() -> canvas.machine().ifPresent(machineNow -> initial.setSelected(
                machineNow.initialStateId().filter(state.id()::equals).isPresent())));
        add(initial, WIDE);
        nameField = Optional.of(name);
        outputsField = Optional.of(outputs);
        initialBox = Optional.of(initial);
    }

    private void showTransition(StateMachine machine, MachineTransition transition) {
        addHeader(Messages.text("machine.properties.transition"));
        add(hint(Messages.text("machine.label.notation")), WIDE);
        JLabel ends = new JLabel(Messages.format("machine.properties.ends", nameOf(machine, transition.fromId()),
                nameOf(machine, transition.toId())));
        addRow(Messages.text("machine.properties.goes"), ends);
        JTextField condition = textRow(Messages.text("machine.properties.condition"), transition.condition(),
                machineNow -> machineNow.transition(transition.id()).map(MachineTransition::condition),
                text -> {
                    if (!applyWithinLimits(current -> MachineEdits.withCondition(current, transition.id(), text))) {
                        return false;
                    }
                    showProblem(MachineConditions.parse(text).problem().map(MachineInspectorRows::describe));
                    return true; // a condition that cannot be read is kept as written
                });
        add(hint(Messages.text(transition.isOtherwise() ? "machine.properties.otherwise"
                : "machine.properties.conditionHint")), WIDE);
        JTextField mealy = textRow(Messages.text("machine.properties.mealyOutputs"),
                MachineOutputsText.write(transition.outputs()),
                machineNow -> machineNow.transition(transition.id())
                        .map(found -> MachineOutputsText.write(found.outputs())),
                text -> applyMealyOutputs(transition.id(), text));
        add(hint(Messages.text("machine.properties.mealyOutputsHint")), WIDE);
        arrowLabel = Optional.of(ends);
        conditionField = Optional.of(condition);
        mealyOutputsField = Optional.of(mealy);
        refreshers.add(() -> canvas.machine().ifPresent(machineNow -> ends.setText(Messages.format(
                "machine.properties.ends", nameOf(machineNow, transition.fromId()),
                nameOf(machineNow, transition.toId())))));
        refreshers.add(() -> canvas.machine().flatMap(machineNow -> machineNow.transition(transition.id()))
                .ifPresent(current -> showProblem(MachineConditions.parse(current.condition()).problem()
                        .map(MachineInspectorRows::describe))));
        showProblem(MachineConditions.parse(transition.condition()).problem().map(MachineInspectorRows::describe));
    }

    private static String nameOf(StateMachine machine, String stateId) {
        return machine.state(stateId).map(MachineState::name).orElse(stateId);
    }

    private boolean applyName(String stateId, String text) {
        Optional<String> wrong = canvas.machine()
                .flatMap(machine -> MachineEdits.stateNameProblem(machine, stateId, text))
                .map(key -> Messages.format(key, text.strip()));
        if (wrong.isPresent()) {
            showProblem(wrong);
            return false;
        }
        apply(machine -> MachineEdits.withStateName(machine, stateId, text));
        return true;
    }

    /**
     * Renames the machine (its component) when the name is good, else says why; the rows stay (the field keeps its
     * focus), only what they show is brought up to date.
     */
    private boolean applyMachineName(String text) {
        Optional<String> wrong = MachineNaming.problem(canvas.place(), library, canvas.definitionId(), text);
        if (wrong.isPresent()) {
            showProblem(wrong);
            return false;
        }
        applying = true;
        try {
            MachineNaming.rename(canvas.place(), library, canvas.definitionId(), text);
        } finally {
            applying = false;
        }
        showProblem(Optional.empty());
        refreshers.forEach(Runnable::run);
        return true;
    }

    private boolean applyOutputs(String stateId, String text) {
        MachineOutputsText.Reading reading = MachineOutputsText.read(text);
        if (reading.problem().isPresent()) {
            MachineOutputsText.Problem wrong = reading.problem().get();
            showProblem(Optional.of(Messages.format(wrong.key(), wrong.arguments().toArray())));
            return false;
        }
        return applyWithinLimits(machine -> MachineEdits.withMooreOutputs(machine, stateId, reading.outputs()));
    }

    private boolean applyMealyOutputs(String transitionId, String text) {
        MachineOutputsText.Reading reading = MachineOutputsText.read(text);
        if (reading.problem().isPresent()) {
            MachineOutputsText.Problem wrong = reading.problem().get();
            showProblem(Optional.of(Messages.format(wrong.key(), wrong.arguments().toArray())));
            return false;
        }
        return applyWithinLimits(machine -> MachineEdits.withMealyOutputs(machine, transitionId, reading.outputs()));
    }

    /**
     * {@link #apply} when the machine after it still has pins for every name (16 inputs, 16 outputs); else says why
     * and changes nothing. Returns whether it applied.
     */
    private boolean applyWithinLimits(UnaryOperator<StateMachine> change) {
        Optional<String> overLimit = canvas.machine()
                .flatMap(machine -> MachineEdits.pinLimitProblem(machine, change.apply(machine)));
        if (overLimit.isPresent()) {
            showProblem(overLimit);
            return false;
        }
        apply(change);
        return true;
    }

    /**
     * Makes the edit, then updates what the rows show as it is after it: in place when they stay the same rows (the
     * field the user typed in keeps its focus), rebuilt when the edit changed what they are for.
     */
    private void apply(UnaryOperator<StateMachine> change) {
        applying = true;
        try {
            canvas.edit(change);
        } finally {
            applying = false;
        }
        showProblem(Optional.empty());
        refreshOrRebuild();
    }

    private void showProblem(Optional<String> text) {
        problem.setText(text.orElse(" "));
        problem.setVisible(text.isPresent());
    }

    /**
     * A field that applies on Enter, when it loses the focus or before the rows change for another selection;
     * {@code onApply} says whether it took the text. {@code modelText} says what the machine holds for it; after a
     * change made elsewhere the field takes it, unless it has the focus or typing not yet applied. A refused text stays
     * while the field has the focus, so it can be fixed; leaving the field puts back what the machine holds (the
     * reason stays under it), so the field never shows what the machine does not have.
     */
    private JTextField textRow(String caption, String text, Function<StateMachine, Optional<String>> modelText,
                               Predicate<String> onApply) {
        JTextField field = FragmentRefusingTransferHandler.install(new JTextField(text));
        String[] applied = {text};
        BooleanSupplier applyIfChanged = () -> {
            String typed = field.getText();
            if (typed.equals(applied[0])) {
                return true;
            }
            String before = applied[0];
            applied[0] = typed;
            boolean taken = onApply.test(typed);
            if (!taken) {
                applied[0] = before;
            }
            return taken;
        };
        field.addActionListener(event -> applyIfChanged.getAsBoolean());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                // not once the rows were rebuilt (another selection): they applied it then
                if (disposed || field.getParent() != MachineInspectorRows.this || applyIfChanged.getAsBoolean()) {
                    return;
                }
                String held = canvas.machine().flatMap(modelText).orElse(applied[0]);
                field.setText(held);
                applied[0] = held;
            }
        });
        pendingApplies.add(applyIfChanged::getAsBoolean);
        refreshers.add(() -> {
            boolean typing = !field.getText().equals(applied[0]);
            Optional<String> held = canvas.machine().flatMap(modelText);
            if (!typing && !field.hasFocus() && held.isPresent() && !held.get().equals(field.getText())) {
                field.setText(held.get());
                applied[0] = held.get();
            }
        });
        addRow(caption, field);
        return field;
    }

    private void addRow(String caption, java.awt.Component editor) {
        JLabel label = new JLabel(caption);
        label.setLabelFor(editor);
        add(label);
        add(editor, "wmin 0");
    }

    private void addHeader(String text) {
        add(boldLabel(text), WIDE + ", gaptop 6");
    }

    private static JLabel boldLabel(String text) {
        JLabel label = new JLabel(text);
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            label.setFont(font.deriveFont(Font.BOLD));
        }
        return label;
    }

    private static JTextArea hint(String text) {
        return new Hint(text, true);
    }

    /**
     * A sentence that wraps at the panel's width: quiet, in the theme's muted colour read at paint time, or in the
     * label's colour (a warning).
     */
    private static final class Hint extends JTextArea {

        private static final long serialVersionUID = 1L;
        private final boolean muted;

        private Hint(String text, boolean muted) {
            super(text);
            this.muted = muted;
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setBorder(null);
            setFont(UIManager.getFont("Label.font"));
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setFont(UIManager.getFont("Label.font"));
            setBorder(null);
        }

        @Override
        public Color getForeground() {
            Color colour = UIManager.getColor(muted ? "Label.disabledForeground" : "Label.foreground");
            return colour != null ? colour : super.getForeground();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, super.getPreferredSize().height);
        }
    }
}
