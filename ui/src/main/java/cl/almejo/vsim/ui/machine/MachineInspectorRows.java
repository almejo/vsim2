package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.logic.ExpressionProblem;
import cl.almejo.vsim.core.machine.MachineConditions;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.MachineKind;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineText;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.simulation.MachineProblem;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.ui.i18n.NoteColorNames;
import cl.almejo.vsim.ui.i18n.StickyColorNames;
import cl.almejo.vsim.ui.karnaugh.ExpressionProblemText;
import cl.almejo.vsim.ui.panels.ColorSwatches;
import cl.almejo.vsim.ui.panels.NoteColorSwatches;
import cl.almejo.vsim.ui.panels.NoteTextArea;
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
import javax.swing.Scrollable;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The right panel's «Propiedades» in a machine's tab (spec 2026-11-14 §1 «The machine tab», the mockup's panel): for
 * a selected state its name, its Moore outputs written {@code Z=1, Y=0} and «Estado inicial»; for an arrow where it
 * goes and its condition, with what is wrong in it; with nothing selected, the machine's own rows in order (Máquina, Entradas, Salidas, Pruebas, Problemas). A text applies on Enter or when the focus leaves it, each change one undo step; a wrong name or outputs text
 * only shows what is wrong, while a wrong condition is kept as written and says what is wrong (spec «a bad condition is
 * kept as text»). An arrow also has its Mealy outputs, and the machine's own rows say whether it is Moore, Mealy or
 * both. A change of the machine rebuilds the rows only when what they show changed (another element, another kind of
 * rows); otherwise it updates, in place, the fields that have no focus and no typing waiting for Enter, so what the
 * user is typing is never lost and the focus stays in the field after Enter. With nothing selected the machine's
 * rows also hold its pin list, «Como un bus» and «Flanco» ({@link MachinePinRows}); they refresh in place too.
 * A selected note shows its colour and «Editar texto». A state has a «Color» row of swatches («sin color» and the
 * eight colours, hito 3), which with several items selected colours all their states at once, in one undo step.
 * Under the rows, the problems of what is selected (what makes it glow), or with nothing selected the machine's
 * problems, each one with a «Ver» that shows it on the diagram; they follow every change and the running copy.
 */
final class MachineInspectorRows extends JPanel implements Scrollable {

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
    /** Why the machine's name cannot change now (the test mode); empty while it can. */
    private transient Optional<String> nameLock = Optional.empty();
    private transient Optional<JTextField> outputsField = Optional.empty();
    private transient Optional<JTextField> conditionField = Optional.empty();
    private transient Optional<JTextField> mealyOutputsField = Optional.empty();
    private transient Optional<JLabel> kindLabel = Optional.empty();
    private transient Optional<JLabel> statesLabel = Optional.empty();
    private transient Optional<JTextArea> testsLabel = Optional.empty();
    private transient Optional<JButton> testButton = Optional.empty();
    private transient Runnable startTest = () -> { };
    private boolean testing;
    private transient Optional<MachinePinRows> pinRows = Optional.empty();
    /** The status line: a pin edit says there whose wires came loose. */
    private final transient Consumer<String> notices;
    /** Whether «Animaciones» is on now: a pin's uses then blink when shown. */
    private final transient BooleanSupplier animations;
    private transient Optional<ColorSwatches<StickyColor>> noteColor = Optional.empty();
    private transient Optional<NoteTextArea> textArea = Optional.empty();
    private transient Map<NoteStyle.Role, ColorSwatches<NoteColor>> textStyle = Map.of();
    private transient Optional<JButton> editNoteText = Optional.empty();
    private transient Optional<ColorSwatches<Optional<StateColor>>> stateColor = Optional.empty();
    /** Opens the inline editor on a note's text (its id). */
    private final transient Consumer<String> editNote;
    /** The name of the key that deletes on the diagram now («Supr» unless Atajos de teclado changed it). */
    private final transient Supplier<String> deleteKey;
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
            "[grow, fill, 60lp::][]"));
    private transient List<MachineFinding> listedProblems = List.of();
    private final transient List<String> problemLines = new ArrayList<>();
    private final transient List<String> problemTips = new ArrayList<>();
    private final transient List<AbstractButton> problemLinks = new ArrayList<>();

    /**
     * The rows of {@code canvas}'s machine; a problem's «Ver» calls {@code reveal}. {@code library} is «Mis
     * componentes»: its names are taken for the machine's, and a machine in it is renamed there too. A pin edit says
     * on {@code notices} (the status line) whose wires came loose; a pin's uses blink when {@code animations} says so.
     * A note's «Editar texto» calls {@code editNote} with its id. The hints of a note and of a text name the key that
     * deletes on the diagram, {@code deleteKey}, read when they are shown (it follows Atajos de teclado).
     */
    MachineInspectorRows(MachineCanvas canvas, Consumer<MachineFinding> reveal, MachineLibrary library,
                         Consumer<String> notices, BooleanSupplier animations, Consumer<String> editNote,
                         Supplier<String> deleteKey) {
        super(new MigLayout("wrap 2, insets dialog", "[left][grow, fill, 60lp::]"));
        this.notices = Objects.requireNonNull(notices, "notices");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.editNote = Objects.requireNonNull(editNote, "editNote");
        this.deleteKey = Objects.requireNonNull(deleteKey, "deleteKey");
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

    // --- Scrollable: the rows take the panel's width (fix round 4: at 150 % and 200 % they stuck out on the right,
    // cut, since the pane never scrolls sideways); only the height scrolls ---

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return UNIT_INCREMENT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(UNIT_INCREMENT, visible.height - UNIT_INCREMENT);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
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

    /** The problems listed under the rows, as they read («✖ …» an error, «⚠ …» a warning: one short line each). */
    List<String> problemLines() {
        return List.copyOf(problemLines);
    }

    /** The full sentence in each listed problem's tooltip, in the order of {@link #problemLines()}. */
    List<String> problemTips() {
        return List.copyOf(problemTips);
    }

    /** The «Ver» of each listed problem, in the order of {@link #problemLines()}. */
    List<AbstractButton> problemLinks() {
        return List.copyOf(problemLinks);
    }

    Optional<JTextField> nameField() {
        return nameField;
    }

    /**
     * Disables the machine's «Nombre» with {@code reason} as its tooltip (the test mode: the name is the circuit's,
     * not the draft's), until the returned runnable runs.
     */
    Runnable lockMachineName(String reason) {
        Optional<String> lock = Optional.of(reason);
        nameLock = lock;
        applyNameLock();
        return () -> {
            if (nameLock == lock) {
                nameLock = Optional.empty();
                applyNameLock();
            }
        };
    }

    private void applyNameLock() {
        machineNameField.ifPresent(field -> {
            field.setEnabled(nameLock.isEmpty());
            field.setToolTipText(nameLock.orElse(null));
        });
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

    /** The paper colours of the selected note; empty when no note alone is selected. */
    Optional<ColorSwatches<StickyColor>> noteColor() {
        return noteColor;
    }

    /** The words of the selected free text; empty when no text alone is selected. */
    Optional<NoteTextArea> textArea() {
        return textArea;
    }

    /** The «Fondo», «Letra» or «Borde» swatches of the selected free texts; empty when only texts are not selected. */
    Optional<ColorSwatches<NoteColor>> textStyle(NoteStyle.Role role) {
        return Optional.ofNullable(textStyle.get(role));
    }

    /** The «Color» of the selected states («sin color» first); empty when no state is selected. */
    Optional<ColorSwatches<Optional<StateColor>>> stateColor() {
        return stateColor;
    }

    /** «Editar texto» of the selected note; empty when no note alone is selected. */
    Optional<JButton> editNoteTextButton() {
        return editNoteText;
    }

    Optional<JCheckBox> initialBox() {
        return initialBox;
    }

    /** The machine's pin list, «Como un bus» and «Flanco», shown with nothing selected. */
    Optional<MachinePinRows> pinRows() {
        return pinRows;
    }

    /** «3 de 64» in the machine's own rows; empty when another element is selected. */
    Optional<String> statesText() {
        return statesLabel.map(JLabel::getText);
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
        // the machine's own rows refresh in place, so a pin being renamed or moved keeps its focus
        if (!builtFor.equals(shownFor()) || builtFor.startsWith(SEVERAL_ROWS)
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
        int count = selectedCount();
        if (count == 0) {
            return MACHINE_ROWS;
        }
        if (count > 1) {
            // the states and texts too: the «Color» row colours the states selected when it was built, and the style
            // rows the texts
            return SEVERAL_ROWS + count + ":" + String.join(",", selection.stateIds()) + ":"
                    + String.join(",", selection.textIds());
        }
        if (!selection.stateIds().isEmpty()) {
            String stateId = selection.stateIds().iterator().next();
            return machine.get().state(stateId).isPresent() ? "state:" + stateId : NO_ROWS;
        }
        if (!selection.noteIds().isEmpty()) {
            String noteId = selection.noteIds().iterator().next();
            return noteOf(machine.get(), noteId).isPresent() ? "note:" + noteId : NO_ROWS;
        }
        if (!selection.textIds().isEmpty()) {
            String textId = selection.textIds().iterator().next();
            return textOf(machine.get(), textId).isPresent() ? "text:" + textId : NO_ROWS;
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
        statesLabel = Optional.empty();
        testsLabel = Optional.empty();
        testButton = Optional.empty();
        pinRows = Optional.empty();
        noteColor = Optional.empty();
        textArea = Optional.empty();
        textStyle = Map.of();
        editNoteText = Optional.empty();
        stateColor = Optional.empty();
        initialBox = Optional.empty();
        arrowLabel = Optional.empty();
        showProblem(Optional.empty());
        builtFor = shownFor();
        canvas.machine().ifPresent(this::showSelected);
        applyNameLock();
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
        problemTips.clear();
        problemLinks.clear();
        problemList.setVisible(!current.isEmpty());
        if (!current.isEmpty()) {
            StateMachine machine = canvas.machine().orElseThrow();
            JLabel header = boldLabel(Messages.format("machine.properties.problemsCount", current.size()));
            problemList.add(header, "span 2, gaptop 6lp");
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
        int count = selectedCount();
        if (count == 0) {
            return canvas.problems().stream().map(MachineProblem::finding).toList();
        }
        if (count > 1) {
            return List.of();
        }
        if (!selection.noteIds().isEmpty() || !selection.textIds().isEmpty()) {
            return List.of(); // a note or a free text is never a problem
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
                : "machine.properties.problem.error", MachineProblemText.shortSentence(finding, machine));
        JTextArea text = finding.isWarning() ? new Hint(line, false) : new WrappedErrorText(line);
        text.setToolTipText(sentence);
        JButton show = new MachineLinkButton(Messages.text("machine.properties.problem.show"));
        show.setToolTipText(Messages.text("machine.properties.problem.showTooltip"));
        show.getAccessibleContext().setAccessibleDescription(sentence);
        show.addActionListener(event -> reveal.accept(finding));
        if (!hasSomethingToShow(machine, finding)) {
            show.setEnabled(false);
            show.setToolTipText(Messages.text("machine.properties.problem.showNothing"));
        }
        problemList.add(text, "wmin 0");
        problemList.add(show, "top");
        problemLines.add(line);
        problemTips.add(sentence);
        problemLinks.add(show);
    }

    /** Whether {@code finding} names a state or an arrow of {@code machine} that «Ver» can select and light. */
    private static boolean hasSomethingToShow(StateMachine machine, MachineFinding finding) {
        return MachineGlows.statesOf(finding).stream().anyMatch(stateId -> machine.state(stateId).isPresent())
                || MachineGlows.transitionsOf(finding).stream()
                .anyMatch(transitionId -> machine.transition(transitionId).isPresent());
    }

    private void showSelected(StateMachine machine) {
        int count = selectedCount();
        if (count == 0) {
            showMachine(machine);
        } else if (count > 1) {
            addHeader(Messages.format("machine.properties.several", count));
            List<String> stateIds = selection.stateIds().stream()
                    .filter(stateId -> machine.state(stateId).isPresent()).toList();
            if (!stateIds.isEmpty()) {
                addStateColorRow(stateIds);
            }
            if (selection.count() == selection.textIds().size()) {
                addTextStyleRows(selection.textIds().stream().filter(id -> textOf(machine, id).isPresent()).toList());
            }
        } else if (!selection.stateIds().isEmpty()) {
            machine.state(selection.stateIds().iterator().next()).ifPresent(state -> showState(machine, state));
        } else if (!selection.noteIds().isEmpty()) {
            noteOf(machine, selection.noteIds().iterator().next()).ifPresent(this::showNote);
        } else if (!selection.textIds().isEmpty()) {
            textOf(machine, selection.textIds().iterator().next()).ifPresent(this::showText);
        } else {
            machine.transition(selection.transitionIds().iterator().next())
                    .ifPresent(transition -> showTransition(machine, transition));
        }
    }

    private int selectedCount() {
        return selection.count();
    }

    private static Optional<MachineNote> noteOf(StateMachine machine, String noteId) {
        return machine.notes().stream().filter(note -> note.id().equals(noteId)).findFirst();
    }

    /**
     * A note: its paper colour (the circuit notes' five), each choice one undo step, and «Editar texto», which opens
     * its text on the paper (a double click on the note does the same).
     */
    private void showNote(MachineNote note) {
        addHeader(Messages.text("machine.properties.note"));
        String noteId = note.id();
        ColorSwatches<StickyColor> color = new ColorSwatches<>(List.of(StickyColor.values()), note.color(),
                StickyColorNames::of, paper -> Optional.of(canvas.colours().stickyPaper(paper)));
        color.addChoiceListener(() -> apply(machine -> machine.withNote(noteId,
                found -> found.withColor(color.selectedValue()))));
        refreshers.add(() -> canvas.machine().flatMap(machineNow -> noteOf(machineNow, noteId))
                .filter(found -> found.color() != color.selectedValue())
                .ifPresent(found -> color.select(found.color())));
        addRow(Messages.text("machine.properties.noteColor"), color);
        JButton edit = new JButton(Messages.text("machine.properties.editNoteText"));
        edit.addActionListener(event -> editNote.accept(noteId));
        add(edit, "span 2, left");
        add(hint(Messages.format("machine.properties.noteHint", deleteKey.get())), WIDE);
        noteColor = Optional.of(color);
        editNoteText = Optional.of(edit);
    }

    private static Optional<MachineText> textOf(StateMachine machine, String textId) {
        return machine.texts().stream().filter(text -> text.id().equals(textId)).findFirst();
    }

    /**
     * A free text, as the circuit's «Texto» shows in Propiedades: its words (several lines; Enter confirms, Mayús+Enter
     * adds a line, a blank text is ignored) and its «Fondo», «Letra» and «Borde» colours; each change is one undo step.
     */
    private void showText(MachineText text) {
        addHeader(Messages.text("machine.properties.text"));
        String textId = text.id();
        NoteTextArea area = new NoteTextArea(text.text());
        String[] applied = {text.text()};
        BooleanSupplier applyIfChanged = () -> {
            String typed = area.getText().strip();
            if (typed.equals(applied[0])) {
                return true;
            }
            if (typed.isEmpty()) {
                area.setText(applied[0]); // a blank text is ignored, as the circuit's is
                return true;
            }
            applied[0] = typed;
            apply(machine -> MachineEdits.withTextWords(machine, textId, typed));
            return true;
        };
        area.addConfirmListener(applyIfChanged::getAsBoolean);
        area.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                if (!disposed && area.getParent() == MachineInspectorRows.this) {
                    applyIfChanged.getAsBoolean();
                }
            }
        });
        pendingApplies.add(applyIfChanged::getAsBoolean);
        refreshers.add(() -> {
            Optional<String> held = canvas.machine().flatMap(machineNow -> textOf(machineNow, textId))
                    .map(MachineText::text);
            boolean typing = !area.getText().strip().equals(applied[0]);
            if (!typing && !area.hasFocus() && held.isPresent() && !held.get().equals(area.getText())) {
                area.setText(held.get());
                applied[0] = held.get();
            }
        });
        addRow(Messages.text("properties.text"), area);
        textArea = Optional.of(area);
        addTextStyleRows(List.of(textId));
        add(hint(Messages.format("machine.properties.textHint", deleteKey.get())), WIDE);
    }

    /**
     * «Fondo», «Letra» and «Borde» for the texts {@code textIds}: each row shows the colour they share, or no choice
     * when they differ; a pick paints that part of every one of them, one undo step.
     */
    private void addTextStyleRows(List<String> textIds) {
        StateMachine machine = canvas.machine().orElse(null);
        if (machine == null || textIds.isEmpty()) {
            return;
        }
        List<MachineText> texts = textIds.stream().map(id -> textOf(machine, id)).flatMap(Optional::stream).toList();
        Map<NoteStyle.Role, ColorSwatches<NoteColor>> rows = new EnumMap<>(NoteStyle.Role.class);
        for (NoteStyle.Role role : NoteStyle.Role.values()) {
            List<NoteColor> colors = texts.stream().map(text -> text.style().color(role)).distinct().toList();
            ColorSwatches<NoteColor> swatches = NoteColorSwatches.of(role, List.of(NoteColor.values()),
                    colors.getFirst(), canvas::colours);
            if (colors.size() > 1) {
                swatches.showNoChoice();
            }
            swatches.addChoiceListener(() -> apply(machineNow -> withStyled(machineNow, textIds, role,
                    swatches.selectedValue())));
            refreshers.add(() -> canvas.machine().ifPresent(machineNow -> {
                List<NoteColor> now = textIds.stream().map(id -> textOf(machineNow, id)).flatMap(Optional::stream)
                        .map(text -> text.style().color(role)).distinct().toList();
                if (now.size() == 1 && swatches.selectedValue() != now.getFirst()) {
                    swatches.select(now.getFirst());
                }
            }));
            addRow(NoteColorNames.of(role), swatches);
            rows.put(role, swatches);
        }
        textStyle = rows;
    }

    /** {@code machine} with {@code role} of the texts {@code textIds} painted {@code color}. */
    private static StateMachine withStyled(StateMachine machine, List<String> textIds, NoteStyle.Role role,
                                           NoteColor color) {
        return machine.withTexts(machine.texts().stream()
                .map(text -> textIds.contains(text.id()) ? text.withStyle(text.style().with(role, color)) : text)
                .toList());
    }

    private void showMachine(StateMachine machine) {
        addHeader(Messages.text("machine.properties.machine"));
        machineNameField = Optional.of(textRow(Messages.text("machine.properties.machineName"),
                MachineNaming.nameOf(canvas.place(), canvas.definitionId()).orElse(""),
                machineNow -> MachineNaming.nameOf(canvas.place(), canvas.definitionId()),
                this::applyMachineName));
        JLabel states = new JLabel(statesCount(machine));
        addRow(Messages.text("machine.properties.states"), states);
        statesLabel = Optional.of(states);
        refreshers.add(() -> canvas.machine().ifPresent(machineNow -> states.setText(statesCount(machineNow))));
        MachinePinRows pins = new MachinePinRows(canvas, notices, animations);
        JLabel edgeCaption = new JLabel(Messages.text("parameter.edge"));
        edgeCaption.setLabelFor(pins.edgeBox());
        add(edgeCaption);
        add(pins.edgeBox(), "growx 0");
        JLabel kind = new JLabel(kindOf(machine));
        addRow(Messages.text("machine.properties.kind"), kind);
        kindLabel = Optional.of(kind);
        refreshers.add(() -> canvas.machine().ifPresent(machineNow -> kind.setText(kindOf(machineNow))));
        add(pins, WIDE);
        pinRows = Optional.of(pins);
        refreshers.add(pins::refresh);
        addHeader(Messages.text("machine.tests.row"));
        // wrapped: «2 pruebas: 2 sin valores esperados» was cut at 150 % and 200 % (fix round 4)
        JTextArea tests = new Hint(MachineTestsText.of(machine), false);
        add(tests, WIDE);
        testsLabel = Optional.of(tests);
        refreshers.add(() -> canvas.machine().ifPresent(machineNow -> tests.setText(MachineTestsText.of(machineNow))));
        JButton test = new JButton(Messages.text("machine.tests.start"));
        test.setToolTipText(Messages.text(testing ? "machine.test.alreadyTesting" : "machine.palette.testHint"));
        test.addActionListener(event -> startTest.run());
        test.setEnabled(!testing);
        add(test, "span 2, left");
        testButton = Optional.of(test);
    }

    /** «1 prueba: falla ✗» in the machine's own rows; empty when another element is selected. */
    Optional<String> testsText() {
        return testsLabel.map(JTextArea::getText);
    }

    /** «Probar…» in the machine's own rows; empty when another element is selected. */
    Optional<JButton> testButton() {
        return testButton;
    }

    /**
     * Wires «Probar…» of the machine's rows: {@code start} is the tab's test entry ({@code MachineMode.startTest}).
     * Returns the call to say the mode started (the button is disabled) or ended (enabled again).
     */
    Consumer<Boolean> enterTestWith(Runnable start) {
        startTest = Objects.requireNonNull(start, "start");
        return nowTesting -> {
            testing = nowTesting;
            testButton.ifPresent(button -> {
                button.setEnabled(!nowTesting);
                button.setToolTipText(Messages.text(nowTesting ? "machine.test.alreadyTesting"
                        : "machine.palette.testHint"));
            });
        };
    }

    /** Where the machine assigns outputs: in its states (Moore), on its arrows (Mealy) or both; Moore when nowhere. */
    static String kindOf(StateMachine machine) {
        MachineKind kind = MachineKind.of(machine);
        if (kind.mixed()) {
            return Messages.text("machine.properties.kind.both");
        }
        return Messages.text(kind.mealy() ? "machine.properties.kind.mealy" : "machine.properties.kind.moore");
    }

    /** «12 de 64». */
    private static String statesCount(StateMachine machine) {
        return Messages.format("machine.pins.count", machine.states().size(), MachineLimits.MAXIMUM_STATES);
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
        addStateColorRow(List.of(state.id()));
        nameField = Optional.of(name);
        outputsField = Optional.of(outputs);
        initialBox = Optional.of(initial);
    }

    /**
     * «Color»: «sin color» and the eight colours, each named in its tooltip and for screen readers; a pick colours
     * every state of {@code stateIds} in one undo step. With states of different colours none is shown chosen.
     */
    private void addStateColorRow(List<String> stateIds) {
        List<Optional<StateColor>> values = new ArrayList<>();
        values.add(Optional.empty());
        for (StateColor color : StateColor.values()) {
            values.add(Optional.of(color));
        }
        Optional<Optional<StateColor>> shared = canvas.machine().flatMap(machine -> sharedColor(machine, stateIds));
        ColorSwatches<Optional<StateColor>> swatches = new ColorSwatches<>(values, shared.orElse(Optional.empty()),
                MachineInspectorRows::colorName, color -> color.map(found -> canvas.colours().stateFill(found)));
        if (shared.isEmpty()) {
            swatches.showNoChoice();
        }
        swatches.addChoiceListener(() -> {
            Optional<StateColor> chosen = swatches.selectedValue();
            apply(machine -> {
                StateMachine coloured = machine;
                for (String stateId : stateIds) {
                    if (coloured.state(stateId).isPresent()) {
                        coloured = coloured.withState(stateId, found -> found.withColor(chosen));
                    }
                }
                return coloured;
            });
        });
        refreshers.add(() -> canvas.machine().ifPresent(machineNow -> {
            Optional<Optional<StateColor>> now = sharedColor(machineNow, stateIds);
            if (now.isEmpty()) {
                swatches.showNoChoice();
            } else if (swatches.showsNoChoice() || !swatches.selectedValue().equals(now.get())) {
                swatches.select(now.get());
            }
        }));
        addRow(Messages.text("machine.properties.stateColor"), swatches);
        stateColor = Optional.of(swatches);
    }

    /** The colour every state of {@code stateIds} has (none is a colour too); empty when they differ. */
    private static Optional<Optional<StateColor>> sharedColor(StateMachine machine, List<String> stateIds) {
        List<Optional<StateColor>> colors = stateIds.stream().map(machine::state).flatMap(Optional::stream)
                .map(MachineState::color).distinct().toList();
        return colors.size() == 1 ? Optional.of(colors.getFirst()) : Optional.empty();
    }

    /** The visible name of a state colour: «sin color», «Rojo», … */
    static String colorName(Optional<StateColor> color) {
        return Messages.text(color.map(found -> switch (found) {
            case RED -> "machine.stateColor.red";
            case PINK -> "machine.stateColor.pink";
            case YELLOW -> "machine.stateColor.yellow";
            case GREEN -> "machine.stateColor.green";
            case TURQUOISE -> "machine.stateColor.turquoise";
            case BLUE -> "machine.stateColor.blue";
            case VIOLET -> "machine.stateColor.violet";
            case GREY -> "machine.stateColor.grey";
        }).orElse("machine.stateColor.none"));
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
        add(boldLabel(text), WIDE + ", gaptop 6lp");
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
    static final class Hint extends JTextArea {

        private static final long serialVersionUID = 1L;
        private final boolean muted;

        Hint(String text, boolean muted) {
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
            Color colour = muted ? ReadableColors.mutedText() : UIManager.getColor("Label.foreground");
            return colour != null ? colour : super.getForeground();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, super.getPreferredSize().height);
        }
    }
}
