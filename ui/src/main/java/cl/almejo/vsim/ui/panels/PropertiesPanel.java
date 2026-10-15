package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.command.ChangeFrameCommand;
import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
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
import cl.almejo.vsim.ui.frames.FrameMenu;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * The "Propiedades" tab: what is selected, with editors that apply on Enter or focus loss. Each applied edit
 * is one undo step; invalid values only show an inline error.
 */
public final class PropertiesPanel extends JPanel {

    private final OpenDocument openDocument;
    private final Selection selection;
    private final DocumentListener documentListener = this::documentChanged;
    private final JLabel heading = new JLabel();
    private final JLabel error = new JLabel(" ");
    private Set<ElementId> shown = Set.of();
    private JTextField textField;
    private ParameterForm parameterForm;
    private JComboBox<FrameTint> frameColor;
    private JCheckBox frameLocked;
    private JSpinner[] frameSpinners;
    private Runnable pendingEdits = () -> { };
    private int generation;
    private boolean applying;
    private boolean disposed;

    public PropertiesPanel(OpenDocument openDocument, Selection selection) {
        super(new MigLayout("wrap 2, insets dialog", "[right][grow, fill]"));
        this.openDocument = openDocument;
        this.selection = selection;
        heading.putClientProperty("FlatLaf.styleClass", "h4");
        Color errorColor = UIManager.getColor("Component.error.focusedBorderColor");
        error.setForeground(errorColor != null ? errorColor : Color.RED);
        selection.addListener(this::selectionChanged);
        openDocument.document().addListener(documentListener);
        rebuild();
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

    ParameterForm parameterForm() {
        return parameterForm;
    }

    String error() {
        return error.getText();
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
        if (applying) {
            return;
        }
        boolean touchesShown = shown.stream()
                .anyMatch(id -> change.changed().contains(id) || change.removed().contains(id));
        if (touchesShown) {
            rebuild();
        }
    }

    private void rebuild() {
        generation++;
        removeAll();
        textField = null;
        parameterForm = null;
        frameColor = null;
        frameLocked = null;
        frameSpinners = null;
        pendingEdits = () -> { };
        error.setText(" ");
        shown = selection.ids();
        add(heading, "span 2, align left");
        if (shown.isEmpty()) {
            heading.setText(Messages.text("properties.none"));
        } else if (shown.size() > 1) {
            heading.setText(Messages.format("properties.several", shown.size()));
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
        }
    }

    private void showComponent(Component component) {
        ComponentType type = openDocument.registry().typeOf(component);
        heading.setText(Messages.componentName(type));
        JTextField field = addTextRow(Messages.text("properties.label"), component.label());
        ElementId id = component.id();
        int built = generation;
        Runnable applyLabel = () -> applyIfCurrent(built,
                () -> labelCommand(id, field).ifPresent(openDocument::execute));
        listenForCommit(field, applyLabel);
        textField = field;
        if (type.parameters().isEmpty()) {
            pendingEdits = applyLabel;
            return;
        }
        ParameterForm form = new ParameterForm(type, component.parameters());
        add(form, "span 2, growx");
        Runnable applyParameters = () -> applyIfCurrent(built,
                () -> parametersCommand(id, type, form).ifPresent(openDocument::execute));
        form.addCommitListener(applyParameters);
        parameterForm = form;
        // Both editors still pending (selection change, focus leaving the panel) are one user action: one undo step.
        pendingEdits = () -> applyIfCurrent(built, () -> executeTogether(Stream.of(labelCommand(id, field),
                parametersCommand(id, type, form)).flatMap(Optional::stream).toList()));
    }

    private void showText(TextLabel label) {
        heading.setText(Messages.text("properties.textLabel"));
        JTextField field = addTextRow(Messages.text("properties.text"), label.text());
        ElementId id = label.id();
        int built = generation;
        Runnable apply = () -> applyIfCurrent(built, () -> labelCommand(id, field).ifPresent(openDocument::execute));
        listenForCommit(field, apply);
        textField = field;
        pendingEdits = apply;
    }

    /**
     * A frame: title, colour, lock and the rectangle in grid units. Each edit is one undo step. X and Y move the
     * frame, or the whole block when it is locked; the size is editable only while unlocked.
     */
    private void showFrame(Frame frame) {
        heading.setText(Messages.text("properties.frame"));
        ElementId id = frame.id();
        int built = generation;
        JTextField field = addTextRow(Messages.text("properties.frameTitle"), frame.title());
        Runnable applyTitle = () -> applyIfCurrent(built, () -> editFrame(id, now -> now.withTitle(field.getText())));
        listenForCommit(field, applyTitle);
        textField = field;
        pendingEdits = applyTitle;

        JComboBox<FrameTint> color = new JComboBox<>(FrameTint.values());
        color.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                   boolean selected, boolean focused) {
                return super.getListCellRendererComponent(list,
                        value instanceof FrameTint tint ? FrameMenu.tintName(tint) : value, index, selected,
                        focused);
            }
        });
        color.setSelectedItem(frame.tint());
        addRow(Messages.text("properties.frameColor"), color);
        color.addActionListener(event -> applyIfCurrent(built, () -> editFrame(id,
                now -> now.withTint((FrameTint) color.getSelectedItem()))));
        frameColor = color;

        JCheckBox locked = new JCheckBox(Messages.text("properties.frameLocked"), frame.locked());
        addRow(Messages.text("properties.frameLock"), locked);
        locked.addActionListener(event -> applyIfCurrent(built, () -> editFrame(id,
                now -> now.withLocked(locked.isSelected()))));
        frameLocked = locked;

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

    private static JSpinner spinner(int value, int minimum) {
        return new JSpinner(new SpinnerNumberModel(value, minimum, Integer.MAX_VALUE / 2, 1));
    }

    private void addRow(String caption, java.awt.Component editor) {
        JLabel label = new JLabel(caption);
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
            if (current.locked()) {
                openDocument.execute(new MoveElementsCommand(
                        FrameContents.block(openDocument.document(), openDocument.registry(), current),
                        deltaX, deltaY));
            } else {
                openDocument.execute(new ChangeFrameCommand(current, current.translate(deltaX, deltaY)));
            }
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
        frameColor.setSelectedItem(now.tint());
        frameLocked.setSelected(now.locked());
        frameSpinners[0].setValue(now.topLeft().x());
        frameSpinners[1].setValue(now.topLeft().y());
        frameSpinners[2].setValue(now.width());
        frameSpinners[3].setValue(now.height());
        frameSpinners[2].setEnabled(!now.locked());
        frameSpinners[3].setEnabled(!now.locked());
    }

    JComboBox<FrameTint> frameColor() {
        return frameColor;
    }

    JCheckBox frameLocked() {
        return frameLocked;
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
    private Optional<Command> labelCommand(ElementId id, JTextField field) {
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
        Map<String, Object> values = type.resolveParameters(form.rawValues());
        if (values.equals(type.resolveParameters(component.get().parameters()))) {
            return Optional.empty();
        }
        return Optional.of(new ChangeParametersCommand(id, type, values));
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
