package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.standard.BitGroups;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.theme.WrappedErrorText;
import cl.almejo.vsim.ui.theme.ReadableColors;
import net.miginfocom.layout.LC;
import net.miginfocom.layout.PlatformDefaults;
import net.miginfocom.layout.UnitValue;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The «Grupos del separador» / «Grupos de la unión» window (spec §2.3, mockup sections 6-7): the table of pins (each
 * with its «tramos», «+ tramo», «quitar», «quitar pin», and «+ Agregar pin» under it), the bit strip, the problems
 * and unused bits, the groups as editable text and a preview of the part. Everything edits a {@link GroupsDraft};
 * Aceptar (disabled while there is a problem; it reads the text typed first), or Enter in the text, applies it as one
 * undo step; the buttons sit in a footer of their own so that the content changing above never moves them.
 * Cancelar, Esc and the window's close button leave the document untouched. Modal, like the memory editor; tests
 * build it without showing it.
 */
final class GroupsWindow {

    private static final int TABLE_HEIGHT = 200;
    private static final int SMALLEST_WIDTH = 520;
    private static final int SMALLEST_HEIGHT = 300;
    private static final double LARGEST_SHARE = 0.9;
    private static final int TEXT_COLUMNS = 40;
    private static final int RANGE_COLUMNS = 2;
    private static final int HINT_COLUMNS = 60;
    private static final String LINK_BUTTON = "JButton.buttonType";
    private static final String BORDERLESS = "borderless";
    /** A problem wraps within the window already shown (it cannot grow for a long sentence). */
    private static final String PROBLEM_CONSTRAINTS = "growx, wmin 0";

    private final OpenDocument openDocument;
    private final ElementId componentId;
    private final boolean joiner;
    private final GroupsDraft draft;
    /** The whole window: {@link #content} in the centre and the buttons in a footer that never moves. */
    private final JPanel body = new JPanel(new BorderLayout());
    /** Everything above the buttons; it grows and shrinks as the pins change. */
    private final JPanel content = new JPanel(new MigLayout("wrap 1, insets dialog, fillx", "[grow, fill]"));
    private final JPanel table = new JPanel(new MigLayout("insets 0, wrap 4, gapy 2", "[][grow, fill][right][]"));
    private final List<JLabel> widthLabels = new ArrayList<>();
    private final JPanel problems = new JPanel(new MigLayout("insets 0, wrap 1, gapy 2", "[grow, fill]"));
    private final JTextField text = new JTextField(TEXT_COLUMNS);
    private final JButton accept = new JButton(Messages.text("groups.window.accept"));
    private final JPanel footer = new JPanel();
    private final Supplier<ColorScheme> scheme;
    private final BitStrip strip;
    private final GroupsPreview preview;
    /** True while the text field holds text that cannot be read: it stays as typed and blocks Aceptar. */
    private boolean unreadableText;
    /** The unreadable text as it was read, to tell it from a new text typed over it. */
    private String unreadableTyped = "";
    /** Why the last Aceptar was refused, shown among the problems until the pins change; empty when it was not. */
    private String refusal = "";
    private Runnable closer = () -> { };
    private Supplier<Dimension> screen;
    private Consumer<Dimension> resizer;
    /** What the dialog puts around the body (title bar, borders), added to every size given to {@link #resizer}. */
    private Insets decorations = new Insets(0, 0, 0, 0);
    /** The size last given to {@link #resizer}, so that it hears only of changes. */
    private Dimension lastSize;

    GroupsWindow(OpenDocument openDocument, ElementId componentId, PainterRegistry painters,
                 Supplier<ColorScheme> scheme) {
        this.openDocument = openDocument;
        this.componentId = componentId;
        this.scheme = scheme;
        Component component = openDocument.document().component(componentId).filter(GroupsEdit::hasGroups)
                .orElseThrow(() -> new IllegalArgumentException("not a bus splitter or joiner: " + componentId));
        this.joiner = GroupsEdit.isJoiner(component);
        BitGroups groups = GroupsEdit.groupsOf(openDocument, component);
        this.draft = GroupsDraft.of(groups);
        this.strip = new BitStrip(draft, scheme, bit -> edited(true));
        this.preview = new GroupsPreview(openDocument.registry().typeOf(component),
                painters.painterFor(component.typeId()), scheme, groups);
        build();
        refresh(true);
    }

    /** The window's body, with its buttons. */
    JComponent content() {
        return body;
    }

    /** «Grupos del separador» or «Grupos de la unión». */
    String title() {
        return Messages.text(joiner ? "groups.window.joinerTitle" : "groups.window.splitterTitle");
    }

    /** What closing the window does (disposing the dialog); tests count it. */
    void onClose(Runnable close) {
        closer = Objects.requireNonNull(close, "close");
    }

    /**
     * Aceptar: the groups go to the document as one undo step and the window closes; nothing with a problem. Groups
     * the document refuses ({@link GroupsEdit#refusal}) stay in the window with the reason among the problems. Text
     * typed in «Como texto» and not read yet is read first: a click on Aceptar must not depend on whether the field
     * lost the focus before it.
     */
    void accept() {
        readText();
        if (!canAccept()) {
            return;
        }
        BitGroups groups = draft.groups().orElseThrow();
        Optional<String> refused = GroupsEdit.refusal(openDocument, componentId, groups);
        if (refused.isPresent()) {
            refusal = refused.get();
            refresh(false);
            return;
        }
        GroupsEdit.command(openDocument, componentId, groups).ifPresent(openDocument::execute);
        closer.run();
    }

    /** Cancelar, Esc or the close button: nothing changes. */
    void cancel() {
        closer.run();
    }

    private boolean canAccept() {
        return !unreadableText && draft.groups().isPresent();
    }

    /** True when «Como texto» holds something typed and not read yet (Aceptar will read it). */
    private boolean pendingText() {
        return !text.getText().equals(unreadableText ? unreadableTyped : draft.text());
    }

    private void updateAccept() {
        accept.setEnabled(canAccept() || pendingText());
    }

    /**
     * The dialog's size on a screen of {@code screen}: what the content asks for, at least a usable size and at most
     * {@link #LARGEST_SHARE} of the screen (the table and the preview scroll inside when it is not enough).
     */
    Dimension preferredDialogSize(Dimension screen) {
        return preferredDialogSize(screen, new Insets(0, 0, 0, 0));
    }

    /**
     * The same for a dialog whose title bar and borders take {@code decorations} around the body: a dialog's size
     * counts them, so the body gets its whole height only when they are added (without them it came out some 40 px
     * short and the table, the strip and the preview were cut).
     */
    Dimension preferredDialogSize(Dimension screen, Insets decorations) {
        int sides = decorations.left + decorations.right;
        int ends = decorations.top + decorations.bottom;
        int width = Math.clamp(body.getPreferredSize().width + (long) sides, Math.min(SMALLEST_WIDTH, screen.width),
                (int) (screen.width * LARGEST_SHARE));
        int height = Math.clamp(wantedHeightAt(width - sides) + (long) ends,
                Math.min(SMALLEST_HEIGHT, screen.height), (int) (screen.height * LARGEST_SHARE));
        return new Dimension(width, height);
    }

    /**
     * The body's height laid out {@code width} wide. The paragraphs wrap, so their height depends on the width they
     * get: asked before any layout (or after one at no width, as when the window is built) they wrap at a few words
     * per line and the dialog came out hundreds of pixels too tall. The body is laid out at that width, measured, and
     * put back as it was.
     */
    private int wantedHeightAt(int width) {
        Dimension current = body.getSize();
        body.setSize(width, Short.MAX_VALUE);
        layOut(body);
        invalidateAll(body);
        int height = body.getPreferredSize().height;
        body.setSize(current);
        invalidateAll(body);
        return height;
    }

    private static void layOut(Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layOut(nested);
            }
        }
    }

    private static void invalidateAll(Container container) {
        container.invalidate();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                invalidateAll(nested);
            } else {
                child.invalidate();
            }
        }
    }

    /**
     * Puts the window in {@code rootPane}: the body as its content, Aceptar as the default button (painted as the
     * primary one; Enter accepts wherever a field does not take it) and Esc as Cancelar.
     */
    void installOn(JRootPane rootPane) {
        rootPane.setContentPane(body);
        rootPane.setDefaultButton(accept);
        rootPane.registerKeyboardAction(event -> cancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * From now on, when a change makes the content want another size, {@code resize} gets the dialog's new size on a
     * screen of {@code screen} (the window grows or shrinks with the pins; the buttons stay at its bottom).
     */
    void onResize(Supplier<Dimension> screen, Consumer<Dimension> resize) {
        onResize(screen, new Insets(0, 0, 0, 0), resize);
    }

    /** The same for a dialog whose title bar and borders take {@code decorations} around the body. */
    void onResize(Supplier<Dimension> screen, Insets decorations, Consumer<Dimension> resize) {
        this.screen = Objects.requireNonNull(screen, "screen");
        this.decorations = (Insets) Objects.requireNonNull(decorations, "decorations").clone();
        this.resizer = Objects.requireNonNull(resize, "resize");
        lastSize = preferredDialogSize(screen.get(), this.decorations);
    }

    /** What {@code dialog} (packed) puts around the body: its title bar, its borders and any root-pane decoration. */
    private Insets decorationsOf(JDialog dialog) {
        java.awt.Point corner = SwingUtilities.convertPoint(body, 0, 0, dialog);
        return new Insets(corner.y, corner.x, Math.max(0, dialog.getHeight() - corner.y - body.getHeight()),
                Math.max(0, dialog.getWidth() - corner.x - body.getWidth()));
    }

    /** Shows the window over {@code parent}'s window (modal, on the EDT). */
    void show(java.awt.Component parent) {
        JDialog dialog = new JDialog(parent == null ? null : SwingUtilities.getWindowAncestor(parent), title(),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                cancel();
            }
        });
        installOn(dialog.getRootPane());
        closer = dialog::dispose;
        dialog.pack();
        Insets decorations = decorationsOf(dialog);
        dialog.setSize(preferredDialogSize(Toolkit.getDefaultToolkit().getScreenSize(), decorations));
        // the width stays as the user leaves it; the height follows the content
        onResize(() -> Toolkit.getDefaultToolkit().getScreenSize(), decorations, size -> {
            if (dialog.isShowing()) {
                dialog.setSize(dialog.getWidth(), size.height);
                dialog.validate();
            }
        });
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private void build() {
        body.add(content, BorderLayout.CENTER);
        body.add(buttons(), BorderLayout.SOUTH);
        content.add(hint(Messages.text("groups.window.intro")), "wmin 0");
        // the table, the strip and the preview each show their whole view (up to a cap) and never shrink below it
        content.add(new WholeHeightScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, TABLE_HEIGHT));
        JButton addPin = link("groups.addPin", Messages.text("groups.window.addPin"), () -> {
            draft.addPin();
            edited(true);
        });
        content.add(addPin, "growx 0");
        content.add(hint(Messages.text("groups.window.strip")), "gaptop 8, wmin 0");
        content.add(new WholeHeightScrollPane(strip, JScrollPane.VERTICAL_SCROLLBAR_NEVER, Integer.MAX_VALUE));
        problems.setName("groups.problems");
        problems.setOpaque(false);
        content.add(problems, "wmin 0");
        content.add(textRow(), "gaptop 8");
        content.add(new JLabel(Messages.text("groups.window.preview")), "gaptop 8");
        content.add(new WholeHeightScrollPane(preview, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                GroupsPreview.TALLEST));
    }

    private JPanel textRow() {
        JPanel row = new JPanel(new MigLayout("insets 0, wrap 2", "[][grow, fill]"));
        row.setOpaque(false);
        JLabel caption = new JLabel(Messages.text("groups.window.text"));
        caption.setLabelFor(text);
        row.add(caption);
        text.setName("groups.text");
        text.addActionListener(event -> accept());
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateAccept();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateAccept();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateAccept();
            }
        });
        text.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                // a press on Aceptar or Cancelar takes the focus: reading now would change the layout under the
                // mouse and lose the click; Aceptar reads the text itself and Cancelar drops it
                if (!isButton(event.getOppositeComponent())) {
                    readText();
                }
            }
        });
        row.add(text);
        row.add(new JLabel(""));
        row.add(hint(Messages.text("groups.window.textHint")), "wmin 0");
        return row;
    }

    private boolean isButton(java.awt.Component component) {
        return component != null && SwingUtilities.isDescendingFrom(component, footer);
    }

    private JPanel buttons() {
        // the dialog's insets on the sides and at the bottom; above, the content's own bottom inset is the gap
        LC footerLayout = new LC();
        footerLayout.setInsets(new UnitValue[] {new UnitValue(0), PlatformDefaults.getDialogInsets(1),
                PlatformDefaults.getDialogInsets(2), PlatformDefaults.getDialogInsets(3)});
        MigLayout layout = new MigLayout(footerLayout);
        layout.setColumnConstraints("push[][]");
        JPanel buttons = footer;
        buttons.setLayout(layout);
        buttons.setName("groups.buttons");
        buttons.setOpaque(false);
        JButton cancel = new JButton(Messages.text("groups.window.cancel"));
        cancel.setName("groups.cancel");
        cancel.addActionListener(event -> cancel());
        accept.setName("groups.accept");
        accept.addActionListener(event -> accept());
        buttons.add(cancel, "sg buttons");
        buttons.add(accept, "sg buttons");
        return buttons;
    }

    /** The text field was left: readable text replaces the pins, unreadable text stays and is a problem. */
    private void readText() {
        if (text.getText().equals(draft.text()) && !unreadableText) {
            return;
        }
        refusal = "";
        if (draft.readText(text.getText())) {
            unreadableText = false;
            refresh(true);
        } else {
            unreadableText = true;
            unreadableTyped = text.getText();
            refresh(false);
        }
    }

    /** The draft changed from the table or the strip: the text follows it again. */
    private void edited(boolean structural) {
        unreadableText = false;
        refusal = "";
        refresh(structural);
    }

    /** Shows the draft: the table when its rows changed, and always the widths, strip, problems, text and preview. */
    private void refresh(boolean structural) {
        if (structural) {
            rebuildTable();
        }
        for (int pin = 0; pin < widthLabels.size() && pin < draft.pinCount(); pin++) {
            widthLabels.get(pin).setText(String.valueOf(draft.bitsOf(pin).size()));
        }
        if (!unreadableText) {
            text.setText(draft.text());
        }
        showProblems();
        draft.groups().ifPresent(preview::show);
        updateAccept();
        strip.repaint();
        body.revalidate();
        body.repaint();
        followTheContent();
    }

    /** Tells {@link #resizer} the dialog's new size when the content wants another one. */
    private void followTheContent() {
        if (resizer == null) {
            return;
        }
        Dimension wanted = preferredDialogSize(screen.get(), decorations);
        if (!wanted.equals(lastSize)) {
            lastSize = wanted;
            resizer.accept(wanted);
        }
    }

    private void rebuildTable() {
        table.removeAll();
        widthLabels.clear();
        table.add(header("groups.window.pin"));
        table.add(header("groups.window.ranges"));
        table.add(header("groups.window.width"));
        table.add(new JLabel(""));
        ButtonGroup selection = new ButtonGroup();
        for (int pin = 0; pin < draft.pinCount(); pin++) {
            addPinRow(pin, selection);
        }
        table.revalidate();
        table.repaint();
    }

    private void addPinRow(int pin, ButtonGroup selection) {
        JRadioButton choose = new JRadioButton(String.valueOf(pin + 1), pin == draft.selectedPin());
        choose.setName("groups.pin." + pin);
        choose.setToolTipText(Messages.text("groups.window.selectPin"));
        choose.addActionListener(event -> draft.select(pin));
        selection.add(choose);
        JPanel chooser = new JPanel(new MigLayout("insets 0, gap 4", "[][]"));
        chooser.setOpaque(false);
        chooser.add(new JLabel(new GroupSwatch(pin, scheme)));
        chooser.add(choose);
        table.add(chooser);
        table.add(rangesOf(pin), "wmin 0");
        JLabel width = new JLabel();
        width.setName("groups.width." + pin);
        widthLabels.add(width);
        table.add(width);
        if (draft.canRemovePin()) {
            table.add(link("groups.removePin." + pin, Messages.text("groups.window.removePin"), () -> {
                draft.removePin(pin);
                edited(true);
            }));
        } else {
            table.add(new JLabel(""));
        }
    }

    private JPanel rangesOf(int pin) {
        JPanel ranges = new JPanel(new MigLayout("insets 0, gap 4 2"));
        ranges.setOpaque(false);
        List<BitRange> pinRanges = draft.ranges(pin);
        for (int index = 0; index < pinRanges.size(); index++) {
            JSpinner from = rangeEnd("groups.from." + pin + "." + index, pinRanges.get(index).first());
            JSpinner to = rangeEnd("groups.to." + pin + "." + index, pinRanges.get(index).last());
            int rangeIndex = index;
            Runnable changed = () -> {
                draft.setRange(pin, rangeIndex, new BitRange((Integer) from.getValue(), (Integer) to.getValue()));
                edited(false);
            };
            from.addChangeListener(event -> changed.run());
            to.addChangeListener(event -> changed.run());
            ranges.add(from);
            ranges.add(new JLabel("–"));
            ranges.add(to);
            if (pinRanges.size() > 1) {
                ranges.add(link("groups.removeRange." + pin + "." + index,
                        Messages.text("groups.window.removeRange"), () -> {
                            draft.removeRange(pin, rangeIndex);
                            edited(true);
                        }), "gapright 8");
            }
        }
        ranges.add(link("groups.addRange." + pin, Messages.text("groups.window.addRange"), () -> {
            draft.addRange(pin);
            edited(true);
        }));
        return ranges;
    }

    private JSpinner rangeEnd(String name, int bit) {
        int highest = Math.max(draft.width() - 1, bit);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(bit, 0, highest, 1));
        spinner.setName(name);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setColumns(RANGE_COLUMNS);
        }
        return spinner;
    }

    private void showProblems() {
        problems.removeAll();
        if (unreadableText) {
            problems.add(new WrappedErrorText(Messages.text("groups.invalidText")), PROBLEM_CONSTRAINTS);
        }
        if (!refusal.isEmpty()) {
            problems.add(new WrappedErrorText(refusal), PROBLEM_CONSTRAINTS);
        }
        for (ParameterProblem problem : draft.problems()) {
            problems.add(new WrappedErrorText(Messages.format(problem.messageKey(), problem.arguments().toArray())),
                    PROBLEM_CONSTRAINTS);
        }
        List<Integer> unused = draft.unusedBits();
        if (!unused.isEmpty()) {
            problems.add(new MutedLabel(Messages.format(joiner ? "groups.window.unusedJoiner"
                    : "groups.window.unused", BitGroups.label(unused))), "wmin 0");
        }
        problems.revalidate();
    }

    private static JLabel header(String key) {
        return new MutedLabel(Messages.text(key));
    }

    private static JButton link(String name, String label, Runnable action) {
        JButton button = new JButton(label);
        button.setName(name);
        button.putClientProperty(LINK_BUTTON, BORDERLESS);
        button.addActionListener(event -> action.run());
        return button;
    }

    /** A quiet paragraph that wraps to the window's width. */
    private static JTextArea hint(String message) {
        JTextArea area = new JTextArea(message) {
            @Override
            public Color getForeground() {
                return ReadableColors.mutedText();
            }
        };
        // a width to wrap at: without it the window would be as wide as the longest unwrapped paragraph
        area.setColumns(HINT_COLUMNS);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setFont(UIManager.getFont("Label.font"));
        area.setBorder(BorderFactory.createEmptyBorder());
        return area;
    }
}
