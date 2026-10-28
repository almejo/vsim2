package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.command.ChangeFrameCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.command.SetNoteStyleCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.FrameTintNames;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.i18n.NoteColorNames;
import cl.almejo.vsim.ui.i18n.StickyColorNames;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.sticky.StickyNoteEdits;
import cl.almejo.vsim.ui.sticky.StickyNoteLock;
import cl.almejo.vsim.ui.theme.ErrorTextLabel;
import cl.almejo.vsim.ui.tunnel.TunnelColorSwatches;
import cl.almejo.vsim.ui.tunnel.TunnelNames;
import cl.almejo.vsim.ui.wiring.SelectionMoves;
import net.miginfocom.swing.MigLayout;

import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * The "Propiedades" tab: what is selected, with editors that apply on Enter or focus loss. Each applied edit
 * is one undo step; invalid values only show an inline error.
 */
public final class PropertiesPanel extends JPanel {

    private final OpenDocument openDocument;
    private final Selection selection;
    private final SelectionMoves selectionMoves;
    private final DocumentListener documentListener = this::documentChanged;
    private final JLabel heading = new JLabel();
    private final JLabel error = new ErrorTextLabel(" ");
    private Set<ElementId> shown = Set.of();
    private JTextField textField;
    private NoteTextArea noteArea;
    private ParameterForm parameterForm;
    private final Supplier<ColorScheme> scheme;
    private ColorSwatches<FrameTint> frameColor;
    private JSpinner[] frameSpinners;
    private ColorSwatches<StickyColor> stickyColor;
    private JRadioButton[] stickyKind;
    private Map<NoteStyle.Role, ColorSwatches<NoteColor>> noteStyleRows = Map.of();
    private Runnable pendingEdits = () -> { };
    private final List<ComponentRows> componentRows = new ArrayList<>();
    private int generation;
    private boolean applying;
    private boolean disposed;
    private boolean refreshPending;
    private BooleanSupplier typingProbe = this::focusInside;

    public PropertiesPanel(OpenDocument openDocument, Selection selection) {
        this(openDocument, selection, SelectionMoves.detached());
    }

    /** {@code selectionMoves} makes the moves of X/Y (M6.7: the wires follow by the follow mode). */
    public PropertiesPanel(OpenDocument openDocument, Selection selection, SelectionMoves selectionMoves) {
        this(openDocument, selection, selectionMoves, TunnelColorSwatches.THEME_SCHEME);
    }

    /** {@code scheme} gives the colours the canvas paints with now: the colour swatches show the same ones. */
    public PropertiesPanel(OpenDocument openDocument, Selection selection, SelectionMoves selectionMoves,
                           Supplier<ColorScheme> scheme) {
        super(new MigLayout("wrap 2, insets dialog", "[right][grow, fill, 60::]"));
        this.scheme = scheme;
        this.openDocument = openDocument;
        this.selection = selection;
        this.selectionMoves = selectionMoves;
        heading.putClientProperty("FlatLaf.styleClass", "h4");
        selection.addListener(this::selectionChanged);
        openDocument.document().addListener(documentListener);
        rebuild();
    }

    /**
     * Rows an extension adds under a component's own rows (the memory's «Contenido», its buttons and warnings). They
     * are asked for each time the component is shown, so they show the document as it is then.
     */
    public interface ComponentRows {

        /** Adds the rows of {@code component} through {@code rows}; nothing for a component it does not know. */
        void addRows(Component component, Rows rows);
    }

    /** Where {@link ComponentRows} put their rows, in this panel's two columns. */
    public interface Rows {

        /** A row with {@code caption} on the left and {@code editor} on the right. */
        void add(String caption, java.awt.Component editor);

        /** A row as wide as the panel (a warning, a line of buttons). */
        void addWide(java.awt.Component content);
    }

    /** Shows {@code contributor}'s rows for every component from now on; the returned runnable takes them back. */
    public Runnable addComponentRows(ComponentRows contributor) {
        Objects.requireNonNull(contributor, "contributor");
        componentRows.add(contributor);
        return () -> componentRows.remove(contributor);
    }

    /**
     * Shows again what is selected, after applying what the editors hold: an extension's rows changed (a linked file
     * went missing).
     */
    public void refresh() {
        if (disposed) {
            return;
        }
        if (typingProbe.getAsBoolean()) {
            // Never commit or lose half-typed input because a file went missing: show the news once the focus leaves.
            refreshWhenFocusLeaves();
            return;
        }
        rebuild();
    }

    private void refreshWhenFocusLeaves() {
        if (refreshPending) {
            return;
        }
        refreshPending = true;
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        PropertyChangeListener listener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (!typingProbe.getAsBoolean()) {
                    manager.removePropertyChangeListener("permanentFocusOwner", this);
                    refreshPending = false;
                    SwingUtilities.invokeLater(PropertiesPanel.this::refresh);
                }
            }
        };
        manager.addPropertyChangeListener("permanentFocusOwner", listener);
    }

    /** Whether the user has the cursor in one of this panel's fields. */
    private boolean focusInside() {
        java.awt.Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return owner instanceof JTextComponent && SwingUtilities.isDescendingFrom(owner, this);
    }

    /** Replaces the test for «the user is typing here» (tests have no focus). */
    void typingProbe(BooleanSupplier probe) {
        typingProbe = probe;
    }

    /** Stops following the document and the selection. The selection has no removal, so the listener goes inert. */
    public void dispose() {
        disposed = true;
        openDocument.document().removeListener(documentListener);
    }

    String heading() {
        return heading.getText();
    }

    JTextField textField() {
        return textField;
    }

    /** The text of the note shown, of one or more lines; null when no note is shown. */
    NoteTextArea noteArea() {
        return noteArea;
    }

    ParameterForm parameterForm() {
        return parameterForm;
    }

    String error() {
        return error.getText();
    }

    JLabel errorLabel() {
        return error;
    }

    /** Applies what the editors hold now, as Enter or focus loss would. */
    void commitEdits() {
        pendingEdits.run();
    }

    private void selectionChanged() {
        if (disposed || selection.ids().equals(shown)) {
            return;
        }
        commitEdits();
        rebuild();
    }

    private void documentChanged(CircuitDocument document, DocumentChange change) {
        boolean touchesShown = shown.stream()
                .anyMatch(id -> change.changed().contains(id) || change.removed().contains(id));
        if (!touchesShown) {
            return;
        }
        if (applying) {
            renameShownComponent(); // the editors stay; «Negar salida» turns an AND into a NAND
        } else {
            rebuild();
        }
    }

    /** Names the one component shown again from its parameters now, leaving the editors as they are. */
    private void renameShownComponent() {
        if (shown.size() != 1) {
            return;
        }
        openDocument.document().component(shown.iterator().next()).ifPresent(component -> heading.setText(
                Messages.componentName(openDocument.registry().typeOf(component), component.parameters())));
    }

    private void rebuild() {
        generation++;
        removeAll();
        textField = null;
        noteArea = null;
        parameterForm = null;
        frameColor = null;
        frameSpinners = null;
        stickyColor = null;
        stickyKind = null;
        noteStyleRows = Map.of();
        pendingEdits = () -> { };
        error.setText(" ");
        shown = selection.ids();
        add(heading, "span 2, align left");
        if (shown.isEmpty()) {
            heading.setText(Messages.text("properties.none"));
        } else if (shown.size() > 1) {
            heading.setText(Messages.format("properties.several", shown.size()));
            List<TextLabel> notes = shownNotes();
            if (notes.size() == shown.size()) {
                addNoteStyleRows(notes);
            }
        } else {
            showElement(openDocument.document().element(shown.iterator().next()).orElse(null));
        }
        add(error, "span 2, align left");
        revalidate();
        repaint();
    }

    private void showElement(DocumentElement element) {
        switch (element) {
            case null -> heading.setText(Messages.text("properties.none"));
            case Component component -> showComponent(component);
            case TextLabel label -> showText(label);
            case Wire wire -> heading.setText(Messages.format("properties.wire", length(wire)));
            case Frame frame -> showFrame(frame);
            case StickyNote note -> showStickyNote(note);
        }
    }

    private void showComponent(Component component) {
        ComponentType type = openDocument.registry().typeOf(component);
        heading.setText(Messages.componentName(type, component.parameters()));
        JTextField field = addTextRow(Messages.text("properties.label"), component.label());
        ElementId id = component.id();
        int built = generation;
        Runnable applyLabel = () -> applyIfCurrent(built,
                () -> labelCommand(id, field).ifPresent(openDocument::execute));
        listenForCommit(field, applyLabel);
        textField = field;
        if (type.parameters().isEmpty()) {
            pendingEdits = applyLabel;
            addExtensionRows(component);
            return;
        }
        ParameterForm form = new ParameterForm(type, component.parameters(), scheme);
        // the parameter rows join this panel's grid, so «Etiqueta» and the parameters share the same columns
        form.addRowsTo(this);
        Runnable applyParameters = () -> applyIfCurrent(built, () -> TunnelNames.executeRemembering(openDocument, id,
                () -> parametersCommand(id, type, form).ifPresent(openDocument::execute)));
        form.addCommitListener(applyParameters);
        parameterForm = form;
        // Both editors still pending (selection change, focus leaving the panel) are one user action: one undo step.
        pendingEdits = () -> applyIfCurrent(built, () -> TunnelNames.executeRemembering(openDocument, id,
                () -> executeTogether(Stream.of(labelCommand(id, field), parametersCommand(id, type, form))
                        .flatMap(Optional::stream).toList())));
        addExtensionRows(component);
    }

    private void addExtensionRows(Component component) {
        Rows rows = new Rows() {
            @Override
            public void add(String caption, java.awt.Component editor) {
                addRow(caption, editor);
            }

            @Override
            public void addWide(java.awt.Component content) {
                PropertiesPanel.this.add(content, "span 2, growx, wmin 0");
            }
        };
        for (ComponentRows contributor : List.copyOf(componentRows)) {
            contributor.addRows(component, rows);
        }
    }

    private void showText(TextLabel label) {
        heading.setText(Messages.text("properties.textLabel"));
        // a note may hold several lines (IA.3): a one-line field would flatten them
        JLabel caption = new JLabel(Messages.text("properties.text"));
        NoteTextArea area = new NoteTextArea(label.text());
        caption.setLabelFor(area);
        add(caption);
        add(area);
        ElementId id = label.id();
        int built = generation;
        Runnable apply = () -> applyIfCurrent(built, () -> labelCommand(id, area).ifPresent(openDocument::execute));
        area.addConfirmListener(apply);
        area.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                apply.run();
            }
        });
        noteArea = area;
        pendingEdits = apply;
        addNoteStyleRows(List.of(label));
    }

    /** The notes among the elements shown, in document order. */
    private List<TextLabel> shownNotes() {
        return openDocument.document().labels().stream().filter(note -> shown.contains(note.id())).toList();
    }

    /**
     * «Fondo», «Letra» and «Borde» for {@code notes}: each row shows the colour the notes share, or no choice when
     * they differ; a pick paints that part of every note, one undo step.
     */
    private void addNoteStyleRows(List<TextLabel> notes) {
        int built = generation;
        List<ElementId> ids = notes.stream().map(TextLabel::id).toList();
        Map<NoteStyle.Role, ColorSwatches<NoteColor>> rows = new EnumMap<>(NoteStyle.Role.class);
        for (NoteStyle.Role role : NoteStyle.Role.values()) {
            List<NoteColor> colors = notes.stream().map(note -> note.style().color(role)).distinct().toList();
            ColorSwatches<NoteColor> swatches = NoteColorSwatches.of(role, List.of(NoteColor.values()), colors.getFirst(),
                    scheme);
            if (colors.size() > 1) {
                swatches.showNoChoice();
            }
            swatches.addChoiceListener(() -> applyIfCurrent(built, () -> SetNoteStyleCommand.setting(
                    openDocument.document(), ids, role, swatches.selectedValue()).ifPresent(openDocument::execute)));
            addRow(NoteColorNames.of(role), swatches);
            rows.put(role, swatches);
        }
        noteStyleRows = rows;
    }

    /** The swatches of {@code role} for the notes shown; null when no note is shown alone or with other notes only. */
    ColorSwatches<NoteColor> noteStyle(NoteStyle.Role role) {
        return noteStyleRows.get(role);
    }

    /**
     * A frame: title, colour and the rectangle in grid units (the lock is the padlock's and the ⋯ menu's). Each edit
     * is one undo step. X and Y move the frame, or the whole block when it is locked; the size is editable only while
     * unlocked.
     */
    private void showFrame(Frame frame) {
        heading.setText(Messages.text("properties.frame"));
        ElementId id = frame.id();
        int built = generation;
        JTextField field = addTextRow(Messages.text("properties.frameTitle"), frame.title());
        Runnable applyTitle = () -> applyIfCurrent(built, () -> editFrame(id, now -> now.withTitle(field.getText().strip())));
        listenForCommit(field, applyTitle);
        textField = field;
        pendingEdits = applyTitle;

        ColorSwatches<FrameTint> color = frameTintSwatches(frame.tint());
        addRow(Messages.text("properties.frameColor"), color);
        color.addChoiceListener(() -> applyIfCurrent(built, () -> editFrame(id,
                now -> now.withTint(color.selectedValue()))));
        frameColor = color;

        JSpinner x = spinner(frame.topLeft().x(), Integer.MIN_VALUE / 2);
        JSpinner y = spinner(frame.topLeft().y(), Integer.MIN_VALUE / 2);
        JSpinner width = spinner(frame.width(), Frame.MINIMUM_WIDTH);
        JSpinner height = spinner(frame.height(), Frame.MINIMUM_HEIGHT);
        addRow(Messages.text("properties.frameX"), x);
        addRow(Messages.text("properties.frameY"), y);
        addRow(Messages.text("properties.frameWidth"), width);
        addRow(Messages.text("properties.frameHeight"), height);
        x.addChangeListener(event -> applyIfCurrent(built, () -> moveFrame(id, (int) x.getValue(), null)));
        y.addChangeListener(event -> applyIfCurrent(built, () -> moveFrame(id, null, (int) y.getValue())));
        width.addChangeListener(event -> applyIfCurrent(built, () -> editFrame(id, now -> now.locked() ? now
                : now.withRectangle(now.topLeft(), (int) width.getValue(), now.height()))));
        height.addChangeListener(event -> applyIfCurrent(built, () -> editFrame(id, now -> now.locked() ? now
                : now.withRectangle(now.topLeft(), now.width(), (int) height.getValue()))));
        width.setEnabled(!frame.locked());
        height.setEnabled(!frame.locked());
        frameSpinners = new JSpinner[]{x, y, width, height};
    }

    /**
     * A sticky note: its paper colour and its kind («Texto» / «Lista»), each change one undo step (the text is edited
     * on the paper itself). While a locked frame holds the note both rows are disabled, as every edit of it is.
     */
    private void showStickyNote(StickyNote note) {
        heading.setText(Messages.text("properties.stickyNote"));
        ElementId id = note.id();
        int built = generation;
        boolean editable = !StickyNoteLock.isLocked(openDocument, id);
        StickyNoteEdits edits = new StickyNoteEdits(openDocument);

        ColorSwatches<StickyColor> color = new ColorSwatches<>(List.of(StickyColor.values()), note.color(),
                StickyColorNames::of, paper -> Optional.of(scheme.get().stickyPaper(paper)));
        color.addChoiceListener(() -> applyIfCurrent(built, () -> edits.setColor(id, color.selectedValue())));
        for (int index = 0; index < StickyColor.values().length; index++) {
            color.swatch(index).setEnabled(editable);
        }
        addRow(Messages.text("properties.stickyColor"), color);
        stickyColor = color;

        boolean checklist = note.content() instanceof StickyChecklist;
        JRadioButton text = new JRadioButton(Messages.text("stickyNotes.menu.text"), !checklist);
        JRadioButton list = new JRadioButton(Messages.text("stickyNotes.menu.list"), checklist);
        ButtonGroup kinds = new ButtonGroup();
        kinds.add(text);
        kinds.add(list);
        text.addActionListener(event -> applyIfCurrent(built, () -> edits.setKind(id, false)));
        list.addActionListener(event -> applyIfCurrent(built, () -> edits.setKind(id, true)));
        JPanel kind = new JPanel(new MigLayout("insets 0"));
        kind.setOpaque(false);
        kind.add(text);
        kind.add(list);
        text.setEnabled(editable);
        list.setEnabled(editable);
        addRow(Messages.text("properties.stickyKind"), kind);
        stickyKind = new JRadioButton[]{text, list};
    }

    /** The paper colour swatches of the sticky note shown; null when no sticky note is shown. */
    ColorSwatches<StickyColor> stickyColor() {
        return stickyColor;
    }

    /** «Texto» and «Lista» of the sticky note shown; null when no sticky note is shown. */
    JRadioButton[] stickyKind() {
        return stickyKind;
    }

    /**
     * One swatch per frame tint, painted opaque in the variant of the theme shown now (the frame itself is a veil of
     * it); «Sin color» is the crossed-out swatch.
     */
    private ColorSwatches<FrameTint> frameTintSwatches(FrameTint selected) {
        return new ColorSwatches<>(List.of(FrameTint.values()), selected, FrameTintNames::of, tint -> {
            if (tint == FrameTint.NONE) {
                return Optional.empty();
            }
            Color veil = scheme.get().frameTint(tint);
            return Optional.of(new Color(veil.getRed(), veil.getGreen(), veil.getBlue()));
        });
    }

    private static JSpinner spinner(int value, int minimum) {
        return new JSpinner(new SpinnerNumberModel(value, minimum, Integer.MAX_VALUE / 2, 1));
    }

    private void addRow(String caption, java.awt.Component editor) {
        JLabel label = new JLabel(caption);
        label.setLabelFor(editor);
        add(label);
        add(editor);
    }

    /** One undo step for {@code change} applied to the frame as it is now; nothing when it changes nothing. */
    private void editFrame(ElementId id, UnaryOperator<Frame> change) {
        openDocument.document().frame(id).ifPresent(current -> {
            Frame next = change.apply(current);
            if (!next.equals(current)) {
                openDocument.execute(new ChangeFrameCommand(current, next));
            }
        });
        refreshFrameFields(id);
    }

    /** New X and/or Y: the frame moves alone, or with its contents when it is locked (one undo step). */
    private void moveFrame(ElementId id, Integer newX, Integer newY) {
        openDocument.document().frame(id).ifPresent(current -> {
            int deltaX = newX == null ? 0 : newX - current.topLeft().x();
            int deltaY = newY == null ? 0 : newY - current.topLeft().y();
            if (deltaX == 0 && deltaY == 0) {
                return;
            }
            Set<ElementId> moved = FrameContents.withLockedBlocks(openDocument.document(), openDocument.registry(),
                    Set.of(id));
            // the canvas drag's command; the wires follow by the mode, as no key is held here
            selectionMoves.move(openDocument, selection, moved, deltaX, deltaY, selectionMoves.followMode());
        });
        refreshFrameFields(id);
    }

    /** Puts what the document holds into the editors (after our own edit nothing rebuilds the panel). */
    private void refreshFrameFields(ElementId id) {
        Optional<Frame> frame = openDocument.document().frame(id);
        if (frame.isEmpty() || frameSpinners == null) {
            return;
        }
        Frame now = frame.get();
        frameColor.select(now.tint());
        frameSpinners[0].setValue(now.topLeft().x());
        frameSpinners[1].setValue(now.topLeft().y());
        frameSpinners[2].setValue(now.width());
        frameSpinners[3].setValue(now.height());
        frameSpinners[2].setEnabled(!now.locked());
        frameSpinners[3].setEnabled(!now.locked());
    }

    ColorSwatches<FrameTint> frameColor() {
        return frameColor;
    }

    /** X, Y, Ancho, Alto. */
    JSpinner[] frameSpinners() {
        return frameSpinners;
    }

    private JTextField addTextRow(String caption, String value) {
        JLabel label = new JLabel(caption);
        JTextField field = FragmentRefusingTransferHandler.install(new JTextField(value, 16));
        label.setLabelFor(field);
        add(label);
        add(field);
        return field;
    }

    private static void listenForCommit(JTextField field, Runnable apply) {
        field.addActionListener(event -> apply.run());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                apply.run();
            }
        });
    }

    /** Runs {@code work} only for editors of the current build, never re-entrantly and never after dispose. */
    private void applyIfCurrent(int built, Runnable work) {
        if (disposed || built != generation || applying) {
            return;
        }
        applying = true;
        try {
            work.run();
        } finally {
            applying = false;
        }
    }

    /** The edit the text field asks for, if any; an empty free text only shows the error. */
    private Optional<Command> labelCommand(ElementId id, JTextComponent field) {
        Optional<DocumentElement> element = openDocument.document().element(id);
        if (element.isEmpty()) {
            return Optional.empty();
        }
        String text = field.getText().strip();
        String current = switch (element.get()) {
            case Component component -> component.label();
            case TextLabel label -> label.text();
            case Wire wire -> text;
            case Frame frame -> text;
            case StickyNote note -> text;
        };
        if (element.get() instanceof TextLabel && text.isEmpty()) {
            error.setText(Messages.text("properties.emptyText"));
            return Optional.empty();
        }
        error.setText(" ");
        return text.equals(current) ? Optional.empty() : Optional.of(new SetLabelCommand(id, text));
    }

    /** The edit the parameter form asks for, if any; an invalid value only shows the error. */
    private Optional<Command> parametersCommand(ElementId id, ComponentType type, ParameterForm form) {
        Optional<Component> component = openDocument.document().component(id);
        if (component.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> problem = form.validationError();
        if (problem.isPresent()) {
            error.setText(problem.get());
            return Optional.empty();
        }
        error.setText(" ");
        form.showNormalizedText();
        Map<String, Object> values = type.resolveParameters(form.rawValues());
        Map<String, Object> current = type.resolveParameters(component.get().parameters());
        if (values.equals(current)) {
            return Optional.empty();
        }
        // a tunnel whose name changed follows the rename rules (spec §7), as «Configurar…» does
        return Optional.of(TunnelNames.parametersCommand(openDocument, id, type, values));
    }

    /** One undo step for whatever the editors asked for. */
    private void executeTogether(List<Command> commands) {
        if (commands.size() == 1) {
            openDocument.execute(commands.get(0));
        } else if (commands.size() > 1) {
            openDocument.execute(new CompositeCommand("command.configure", commands));
        }
    }

    private static int length(Wire wire) {
        return wire.isHorizontal() ? wire.end().x() - wire.start().x() : wire.end().y() - wire.start().y();
    }
}
