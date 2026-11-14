package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.logic.ExpressionProblem;
import cl.almejo.vsim.core.machine.MachineConditions;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.karnaugh.ExpressionProblemText;
import cl.almejo.vsim.ui.theme.WrappedErrorText;
import net.miginfocom.swing.MigLayout;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The right panel's «Propiedades» in a machine's tab (spec 2026-11-14 §1 «The machine tab», the mockup's panel): for
 * a selected state its name, its Moore outputs written {@code Z=1, Y=0} and «Estado inicial»; for an arrow where it
 * goes and its condition, with what is wrong in it; with nothing selected, the machine's states and pins and how to
 * draw. A text applies on Enter or when the focus leaves it, each change one undo step; a wrong name or outputs text
 * only shows what is wrong, while a wrong condition is kept as written and says what is wrong (spec «a bad condition is
 * kept as text»). The Mealy outputs of an arrow and the pin list come in later milestones.
 */
final class MachineInspectorRows extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String WIDE = "span 2, growx, wmin 0";
    private static final int UNIT_INCREMENT = 16;

    private final transient MachineCanvas canvas;
    private final transient MachineSelection selection;
    private final transient Runnable stopFollowingSelection;
    private final transient Runnable stopFollowingMachine;
    private final WrappedErrorText problem = new WrappedErrorText(" ");
    private transient Optional<JTextField> nameField = Optional.empty();
    private transient Optional<JTextField> outputsField = Optional.empty();
    private transient Optional<JTextField> conditionField = Optional.empty();
    private transient Optional<JCheckBox> initialBox = Optional.empty();
    private transient Optional<JLabel> arrowLabel = Optional.empty();
    /** True while this panel's own edit runs: the change it makes does not rebuild the rows under the user. */
    private boolean applying;

    MachineInspectorRows(MachineCanvas canvas) {
        super(new MigLayout("wrap 2, insets dialog", "[left][grow, fill, 60::]"));
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.selection = canvas.selection();
        stopFollowingSelection = selection.addListener(this::rebuild);
        stopFollowingMachine = canvas.addChangeListener(this::machineChanged);
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

    Optional<JTextField> nameField() {
        return nameField;
    }

    Optional<JTextField> outputsField() {
        return outputsField;
    }

    Optional<JTextField> conditionField() {
        return conditionField;
    }

    Optional<JCheckBox> initialBox() {
        return initialBox;
    }

    /** Stops following the diagram (the tab leaves machine mode). */
    void dispose() {
        stopFollowingSelection.run();
        stopFollowingMachine.run();
    }

    private void machineChanged() {
        if (!applying) {
            rebuild();
        }
    }

    private void rebuild() {
        removeAll();
        nameField = Optional.empty();
        outputsField = Optional.empty();
        conditionField = Optional.empty();
        initialBox = Optional.empty();
        arrowLabel = Optional.empty();
        showProblem(Optional.empty());
        canvas.machine().ifPresent(this::showSelected);
        add(problem, WIDE);
        revalidate();
        repaint();
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
        addRow(Messages.text("machine.properties.states"), new JLabel(String.valueOf(machine.states().size())));
        addRow(Messages.text("machine.properties.inputs"), new JLabel(listed(machine.inputNames())));
        addRow(Messages.text("machine.properties.outputs"), new JLabel(listed(machine.outputNames())));
        add(hint(Messages.text("machine.properties.howToDraw")), WIDE);
    }

    private static String listed(List<String> names) {
        return names.isEmpty() ? Messages.text("machine.properties.none") : String.join(", ", names);
    }

    private void showState(StateMachine machine, MachineState state) {
        addHeader(Messages.text("machine.properties.state"));
        JTextField name = textRow(Messages.text("machine.properties.stateName"), state.name(),
                text -> applyName(state.id(), text));
        JTextField outputs = textRow(Messages.text("machine.properties.mooreOutputs"),
                MachineOutputsText.write(state.outputs()), text -> applyOutputs(state.id(), text));
        add(hint(Messages.text("machine.properties.mooreOutputsHint")), WIDE);
        JCheckBox initial = new JCheckBox(Messages.text("machine.properties.initial"),
                machine.initialStateId().filter(state.id()::equals).isPresent());
        initial.addActionListener(event -> apply(current ->
                MachineEdits.withInitial(current, state.id(), initial.isSelected())));
        add(initial, WIDE);
        nameField = Optional.of(name);
        outputsField = Optional.of(outputs);
        initialBox = Optional.of(initial);
    }

    private void showTransition(StateMachine machine, MachineTransition transition) {
        addHeader(Messages.text("machine.properties.transition"));
        JLabel ends = new JLabel(Messages.format("machine.properties.ends", nameOf(machine, transition.fromId()),
                nameOf(machine, transition.toId())));
        addRow(Messages.text("machine.properties.goes"), ends);
        JTextField condition = textRow(Messages.text("machine.properties.condition"), transition.condition(),
                text -> apply(current -> MachineEdits.withCondition(current, transition.id(), text)));
        add(hint(Messages.text(transition.isOtherwise() ? "machine.properties.otherwise"
                : "machine.properties.conditionHint")), WIDE);
        arrowLabel = Optional.of(ends);
        conditionField = Optional.of(condition);
        showProblem(MachineConditions.parse(transition.condition()).problem().map(MachineInspectorRows::describe));
    }

    private static String nameOf(StateMachine machine, String stateId) {
        return machine.state(stateId).map(MachineState::name).orElse(stateId);
    }

    private void applyName(String stateId, String text) {
        Optional<String> wrong = canvas.machine()
                .flatMap(machine -> MachineEdits.stateNameProblem(machine, stateId, text))
                .map(key -> Messages.format(key, text.strip()));
        if (wrong.isPresent()) {
            showProblem(wrong);
            return;
        }
        apply(machine -> MachineEdits.withStateName(machine, stateId, text));
    }

    private void applyOutputs(String stateId, String text) {
        MachineOutputsText.Reading reading = MachineOutputsText.read(text);
        if (reading.problem().isPresent()) {
            MachineOutputsText.Problem wrong = reading.problem().get();
            showProblem(Optional.of(Messages.format(wrong.key(), wrong.arguments().toArray())));
            return;
        }
        apply(machine -> MachineEdits.withMooreOutputs(machine, stateId, reading.outputs()));
    }

    /** Makes the edit, then shows the selection again as it is after it. */
    private void apply(UnaryOperator<StateMachine> change) {
        applying = true;
        try {
            canvas.edit(change);
        } finally {
            applying = false;
        }
        rebuild();
    }

    private void showProblem(Optional<String> text) {
        problem.setText(text.orElse(" "));
        problem.setVisible(text.isPresent());
    }

    private JTextField textRow(String caption, String text, Consumer<String> onApply) {
        JTextField field = new JTextField(text);
        String[] applied = {text};
        Runnable applyIfChanged = () -> {
            if (!field.getText().equals(applied[0])) {
                applied[0] = field.getText();
                onApply.accept(field.getText());
            }
        };
        field.addActionListener(event -> applyIfChanged.run());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                if (field.isShowing()) {
                    applyIfChanged.run();
                }
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
        JLabel header = new JLabel(text);
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            header.setFont(font.deriveFont(Font.BOLD));
        }
        add(header, WIDE + ", gaptop 6");
    }

    private static JTextArea hint(String text) {
        return new Hint(text);
    }

    /** A quiet sentence that wraps at the panel's width, in the theme's muted colour read at paint time. */
    private static final class Hint extends JTextArea {

        private static final long serialVersionUID = 1L;

        private Hint(String text) {
            super(text);
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
            Color muted = UIManager.getColor("Label.disabledForeground");
            return muted != null ? muted : super.getForeground();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, super.getPreferredSize().height);
        }
    }
}
