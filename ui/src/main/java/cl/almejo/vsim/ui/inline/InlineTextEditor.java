package cl.almejo.vsim.ui.inline;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.LabelLayout;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.paint.TextFonts;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Edits a text right where the canvas draws it (M6.5): a one-line field over the text, scaled with the zoom, with ✓
 * and ✗ buttons after it and the validator's problem below it. Enter, ✓ or a mouse press anywhere else in the same
 * window confirm; Esc or ✗ cancel. With a problem, Enter and ✓ do nothing and a press outside cancels, so an invalid
 * text never sticks and the editor never traps the user. Losing the focus to another application changes nothing.
 * <p>
 * While the field has the focus it keeps every key to itself (only Alt combinations go on, for the menu mnemonics):
 * Supr, Ctrl+Z or the arrows edit the text and never reach the canvas bindings or the menu accelerators.
 * <p>
 * A request with suggestions shows the ones that fit what is typed in a list under the field (all of them until the
 * user types): a click on one, or Down/Up to pick one and Enter, puts it in the field and confirms.
 * <p>
 * A request for several lines (a note, IA.3) is edited in a text area instead: Shift+Enter adds a line break, Enter
 * still confirms, and the area keeps its top-left corner while it grows down and right with the text.
 * <p>
 * One editor per canvas, reused: opening a new request confirms (or, when invalid, cancels) the current one. EDT only.
 */
public final class InlineTextEditor {

    /** The field's font never gets smaller than this, however far the view is zoomed out. */
    public static final float MINIMUM_FONT_SIZE = 11f;
    /** The widest the field gets, in canvas pixels; longer texts scroll inside it. */
    public static final int MAXIMUM_WIDTH = 480;
    /** The narrowest text room, so an empty text (a new one) still gives somewhere to type. */
    static final int MINIMUM_TEXT_WIDTH = 48;
    private static final int CARET_ROOM = 2;
    private static final int GAP = 2;
    private static final String CONFIRM = "inline-confirm";
    private static final String CANCEL = "inline-cancel";
    private static final String NEXT_SUGGESTION = "inline-next-suggestion";
    private static final String PREVIOUS_SUGGESTION = "inline-previous-suggestion";
    private static final String NOTHING = "inline-nothing";
    /** The suggestion list shows at most this many rows; the user narrows longer lists by typing. */
    private static final int MAXIMUM_SUGGESTIONS = 6;
    private static final String OUTLINE = "JComponent.outline";
    private static final String OUTLINE_ERROR = "error";
    /** WCAG AA contrast for normal text. */

    /** Where a new edit opens: the window's active pane. */
    private final Supplier<InlineHost> paneForNewEdits;
    /** The pane the open (or the last) edit is shown in. */
    private InlineHost canvas;
    private final JPanel panel = new JPanel(null);
    private final Field field = new Field();
    private final Area area = new Area();
    private final JButton confirmButton;
    private final JButton cancelButton;
    private final JLabel problemText = new ProblemBadge();
    private final DefaultListModel<String> suggestionModel = new DefaultListModel<>();
    private final JList<String> suggestionList = new JList<>(suggestionModel);
    private final AWTEventListener pressListener = this::anywhereInTheApplication;
    private final List<Runnable> whileOpen = new ArrayList<>();
    private InlineEditRequest request;
    private BooleanSupplier stillValid = () -> true;
    private Optional<String> problem = Optional.empty();
    /** False until the user changes the text: the suggestions are not filtered by the initial text. */
    private boolean edited;

    /**
     * An editor for a window with several panes: each edit opens over the pane {@code paneForNewEdits} gives when it
     * opens (the active one) and stays there until it closes.
     */
    public InlineTextEditor(Supplier<CircuitCanvas> paneForNewEdits) {
        this(new Hosts(() -> InlineHost.of(paneForNewEdits.get())));
    }

    /** An editor for one surface that is not a circuit canvas (the shape editor's). */
    public static InlineTextEditor forHost(InlineHost host) {
        return new InlineTextEditor(new Hosts(() -> host));
    }

    private record Hosts(Supplier<InlineHost> supplier) { }

    private InlineTextEditor(Hosts hosts) {
        this.paneForNewEdits = hosts.supplier();
        this.canvas = paneForNewEdits.get();
        panel.setOpaque(false);
        // a press on the empty parts of the editor stops here instead of reaching the canvas tool, and the
        // canvas tool's cursor (a resize arrow on a frame's edge) never shows through
        panel.addMouseListener(new MouseAdapter() { });
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        problemText.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        field.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        field.setFocusTraversalKeysEnabled(false); // Tab does nothing: the focus stays in the field
        area.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        area.setFocusTraversalKeysEnabled(false);
        area.setVisible(false);
        confirmButton = iconButton(InlineIcons.CONFIRM, "inline.confirm", this::confirm);
        cancelButton = iconButton(InlineIcons.CANCEL, "inline.cancel", this::cancel);
        problemText.setVisible(false);
        suggestionList.setVisible(false);
        suggestionList.setFocusable(false); // the field keeps the focus, and its keys
        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                int index = suggestionList.locationToIndex(event.getPoint());
                if (index >= 0) {
                    chooseSuggestion(suggestionModel.getElementAt(index));
                }
            }
        });
        panel.add(field);
        panel.add(area);
        panel.add(confirmButton);
        panel.add(cancelButton);
        panel.add(suggestionList);
        panel.add(problemText);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), CONFIRM);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CANCEL);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), NEXT_SUGGESTION);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
                PREVIOUS_SUGGESTION);
        field.getActionMap().put(CONFIRM, Actions.unnamed(this::confirmOrTakeSuggestion));
        field.getActionMap().put(CANCEL, Actions.unnamed(this::cancel));
        field.getActionMap().put(NEXT_SUGGESTION, Actions.unnamed(() -> moveSuggestion(1)));
        field.getActionMap().put(PREVIOUS_SUGGESTION, Actions.unnamed(() -> moveSuggestion(-1)));
        area.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), CONFIRM);
        area.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), DefaultEditorKit.insertBreakAction);
        area.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CANCEL);
        area.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), NOTHING);
        area.getActionMap().put(CONFIRM, Actions.unnamed(this::confirm));
        area.getActionMap().put(CANCEL, Actions.unnamed(this::cancel));
        area.getActionMap().put(NOTHING, Actions.unnamed(() -> { }));
        javax.swing.event.DocumentListener typing = new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                textChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                textChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                textChanged();
            }
        };
        field.getDocument().addDocumentListener(typing);
        area.getDocument().addDocumentListener(typing);
    }

    /** Opens {@code newRequest}; the editor stays open whatever happens to the document. */
    public void open(InlineEditRequest newRequest) {
        open(newRequest, () -> true);
    }

    /**
     * Opens {@code newRequest}, first confirming (or, when its text is invalid, cancelling) an open one. After every
     * document change the editor asks {@code newStillValid} and closes without applying when it says false: the
     * element being edited was removed (an undo, another view) or the document went away.
     */
    public void open(InlineEditRequest newRequest, BooleanSupplier newStillValid) {
        if (isOpen()) {
            confirmOrCancel();
        }
        canvas = paneForNewEdits.get();
        request = newRequest;
        stillValid = newStillValid;
        // the components live detached while closed, so a theme change did not reach them
        SwingUtilities.updateComponentTreeUI(panel);
        field.setHorizontalAlignment(switch (newRequest.anchor()) {
            case LEFT -> SwingConstants.LEADING;
            case CENTER -> SwingConstants.CENTER;
            case RIGHT -> SwingConstants.RIGHT;
        });
        // a text area has no field border: lend it the field's, so both look alike
        area.setBorder(UIManager.getBorder("TextField.border"));
        field.setVisible(!newRequest.multiLine());
        area.setVisible(newRequest.multiLine());
        JTextComponent text = textComponent();
        text.setText(newRequest.initialText());
        text.selectAll();
        edited = false;
        whileOpen.add(canvas.addFloatingComponent(panel));
        whileOpen.add(canvas.addViewportListener(this::layOut));
        whileOpen.add(canvas.addChangeListener(this::documentChanged));
        Toolkit.getDefaultToolkit().addAWTEventListener(pressListener, AWTEvent.MOUSE_EVENT_MASK);
        whileOpen.add(() -> Toolkit.getDefaultToolkit().removeAWTEventListener(pressListener));
        // the request carries the font and area it was opened with: a font change (Preferencias) ends the edit
        Runnable fontChanged = this::confirmOrCancel;
        TextFonts.addListener(fontChanged);
        whileOpen.add(() -> TextFonts.removeListener(fontChanged));
        check();
        text.requestFocusInWindow();
    }

    public boolean isOpen() {
        return request != null;
    }

    /** The pane the open edit is shown in; empty while closed. */
    public Optional<CircuitCanvas> openIn() {
        return isOpen() && canvas instanceof InlineHost.CanvasHost host ? Optional.of(host.canvas()) : Optional.empty();
    }

    /** Closes without applying when the open edit is shown in {@code pane} (the pane is going away). */
    public void cancelIfOpenIn(CircuitCanvas pane) {
        if (isOpen() && canvas instanceof InlineHost.CanvasHost host && host.canvas() == pane) {
            cancel();
        }
    }

    /** Hands the text to the request and closes; does nothing while the text has a problem. */
    public void confirm() {
        if (!isOpen()) {
            return;
        }
        String text = textComponent().getText();
        if (request.validator().apply(text).isPresent()) {
            return;
        }
        Consumer<String> onConfirm = request.onConfirm();
        close();
        onConfirm.accept(text);
    }

    /** Closes without applying anything. */
    public void cancel() {
        if (isOpen()) {
            close();
        }
    }

    /** Closes without applying (the view or the document goes away); the editor can still be opened again. */
    public void dispose() {
        cancel();
    }

    /** The one-line field (component labels, names, values). */
    public JTextField field() {
        return field;
    }

    /** The text area of a request of several lines (a note). */
    public JTextArea area() {
        return area;
    }

    /** What the open (or the last) request is typed in: {@link #area} for several lines, else {@link #field}. */
    public JTextComponent textComponent() {
        return request != null && request.multiLine() ? area : field;
    }

    public JButton confirmButton() {
        return confirmButton;
    }

    public JButton cancelButton() {
        return cancelButton;
    }

    public JLabel problemText() {
        return problemText;
    }

    /** The suggestions that fit the text now; hidden when there are none. */
    public JList<String> suggestionList() {
        return suggestionList;
    }

    /** Puts {@code suggestion} in the field and confirms it, as a click on it in the list does. */
    public void chooseSuggestion(String suggestion) {
        if (!isOpen()) {
            return;
        }
        field.setText(suggestion);
        confirm();
    }

    /** Enter: the suggestion picked with Down/Up when there is one, else the text as typed. */
    private void confirmOrTakeSuggestion() {
        String picked = suggestionList.isVisible() ? suggestionList.getSelectedValue() : null;
        if (picked != null) {
            chooseSuggestion(picked);
        } else {
            confirm();
        }
    }

    /** Down/Up: picks the next or previous suggestion, wrapping around; nothing without suggestions. */
    private void moveSuggestion(int step) {
        int size = suggestionModel.getSize();
        if (!suggestionList.isVisible() || size == 0) {
            return;
        }
        int selected = suggestionList.getSelectedIndex();
        int next = selected < 0 ? (step > 0 ? 0 : size - 1) : Math.floorMod(selected + step, size);
        suggestionList.setSelectedIndex(next);
    }

    /**
     * Confirms the open edit when its text is valid and cancels it otherwise; nothing when closed. The window calls it
     * before it closes (M6.8), so «¿Guardar cambios?» sees a half-typed edit instead of losing it.
     */
    public void confirmOrCancel() {
        if (!isOpen()) {
            return;
        }
        if (request.validator().apply(textComponent().getText()).isPresent()) {
            cancel();
        } else {
            confirm();
        }
    }

    private void close() {
        boolean hadFocus = field.isFocusOwner() || area.isFocusOwner();
        request = null;
        stillValid = () -> true;
        List<Runnable> removals = new ArrayList<>(whileOpen);
        whileOpen.clear();
        for (int index = removals.size() - 1; index >= 0; index--) {
            removals.get(index).run();
        }
        if (hadFocus) {
            canvas.component().requestFocusInWindow();
        }
    }

    private void textChanged() {
        if (isOpen()) {
            edited = true;
            check();
        }
    }

    /**
     * The suggestions that fit {@code text}: before the user types, every one but the text itself; after, those that
     * start with it, ignoring case (and not the text itself).
     */
    private void showSuggestions(String text) {
        String typed = text.toLowerCase(Locale.ROOT);
        List<String> fitting = request.suggestions().stream()
                .filter(suggestion -> !suggestion.equals(text))
                .filter(suggestion -> !edited || suggestion.toLowerCase(Locale.ROOT).startsWith(typed))
                .limit(MAXIMUM_SUGGESTIONS)
                .toList();
        if (!fitting.equals(currentSuggestions())) {
            suggestionModel.clear();
            suggestionModel.addAll(fitting);
        }
        suggestionList.setVisible(!fitting.isEmpty());
    }

    private List<String> currentSuggestions() {
        List<String> shown = new ArrayList<>();
        for (int index = 0; index < suggestionModel.getSize(); index++) {
            shown.add(suggestionModel.getElementAt(index));
        }
        return shown;
    }

    /** Runs the validator on the current text and shows what it says. */
    private void check() {
        JTextComponent text = textComponent();
        problem = request.validator().apply(text.getText());
        text.putClientProperty(OUTLINE, problem.isPresent() ? OUTLINE_ERROR : null);
        problemText.setText(problem.orElse(""));
        problemText.setVisible(problem.isPresent());
        confirmButton.setEnabled(problem.isEmpty());
        showSuggestions(text.getText());
        layOut();
    }

    private void documentChanged() {
        if (!isOpen()) {
            return;
        }
        if (stillValid.getAsBoolean()) {
            check(); // what the document says may have changed the validator's answer (a Constante's width)
        } else {
            cancel();
        }
    }

    /**
     * Any mouse press in the application: one in the canvas's window (or a window it owns, like a popup) outside the
     * editor confirms, or cancels when the text has a problem. Presses in other windows change nothing.
     */
    private void anywhereInTheApplication(AWTEvent event) {
        if (!isOpen() || event.getID() != MouseEvent.MOUSE_PRESSED || !(event instanceof MouseEvent press)
                || !(press.getSource() instanceof Component source)) {
            return;
        }
        if (!sameWindow(source) || insideTheEditor(source, press.getPoint())) {
            return;
        }
        confirmOrCancel();
    }

    private boolean sameWindow(Component source) {
        Component canvasRoot = rootOf(canvas.component());
        for (Component root = rootOf(source); root != null;
             root = root instanceof Window window ? window.getOwner() : null) {
            if (root == canvasRoot) {
                return true;
            }
        }
        return false;
    }

    /** The topmost ancestor: the window, or the outermost component when there is none (headless). */
    private static Component rootOf(Component component) {
        Component root = component;
        while (!(root instanceof Window) && root.getParent() != null) {
            root = root.getParent();
        }
        return root;
    }

    private boolean insideTheEditor(Component source, Point point) {
        if (source == panel || SwingUtilities.isDescendingFrom(source, panel)) {
            return true;
        }
        if (!SwingUtilities.isDescendingFrom(panel, source)) {
            return false;
        }
        Point inPanel = SwingUtilities.convertPoint(source, point, panel);
        return panel.contains(inPanel);
    }

    /** Places the field over the text as the viewport shows it now, the buttons after it and the problem below. */
    private void layOut() {
        if (!isOpen()) {
            return;
        }
        Rectangle2D area = canvas.documentTransform().createTransformedShape(request.documentArea()).getBounds2D();
        float size = (float) Math.max(MINIMUM_FONT_SIZE, request.font().getSize2D() * canvas.zoom());
        Font font = request.font().deriveFont(size);
        JTextComponent text = textComponent();
        if (!font.equals(text.getFont())) {
            text.setFont(font);
        }
        Insets insets = text.getInsets();
        int horizontalInsets = insets.left + insets.right;
        int verticalInsets = insets.top + insets.bottom;
        int areaX = (int) Math.round(area.getX());
        int areaY = (int) Math.round(area.getY());
        int areaWidth = (int) Math.round(area.getWidth());
        int areaHeight = (int) Math.round(area.getHeight());
        FontMetrics metrics = text.getFontMetrics(font);
        int textWidth = 0;
        for (String line : request.multiLine() ? LabelLayout.noteLines(text.getText()) : List.of(text.getText())) {
            textWidth = Math.max(textWidth, metrics.stringWidth(line));
        }
        textWidth += CARET_ROOM;
        int fieldWidth = Math.min(MAXIMUM_WIDTH,
                Math.max(Math.max(areaWidth, MINIMUM_TEXT_WIDTH), textWidth) + horizontalInsets);
        int fittedHeight = areaHeight + verticalInsets;
        int fieldHeight = Math.max(fittedHeight, text.getPreferredSize().height);
        int fieldX = switch (request.anchor()) {
            case LEFT -> areaX - insets.left;
            case CENTER -> (int) Math.round(area.getCenterX() - fieldWidth / 2.0);
            case RIGHT -> areaX + areaWidth + insets.right - fieldWidth;
        };
        // a note keeps its top as it grows down with every line; a one-line field is centred on the painted text
        int fieldY = request.multiLine() ? areaY - insets.top
                : areaY - insets.top - (fieldHeight - fittedHeight) / 2;
        Rectangle fieldBounds = new Rectangle(fieldX, fieldY, fieldWidth, fieldHeight);

        Dimension buttonSize = confirmButton.getPreferredSize();
        int side = Math.max(buttonSize.width, buttonSize.height);
        // beside the first line of a note, beside the middle of a field
        int besideHeight = request.multiLine() ? metrics.getHeight() + verticalInsets : fieldHeight;
        int buttonY = fieldBounds.y + (besideHeight - side) / 2;
        Rectangle confirmBounds = new Rectangle(fieldBounds.x + fieldWidth + GAP, buttonY, side, side);
        Rectangle cancelBounds = new Rectangle(confirmBounds.x + side, buttonY, side, side);
        Rectangle all = fieldBounds.union(confirmBounds).union(cancelBounds);
        Rectangle suggestionBounds = new Rectangle(fieldBounds.x, all.y + all.height + GAP, 0, 0);
        if (suggestionList.isVisible()) {
            suggestionList.setFont(font);
            Dimension listSize = suggestionList.getPreferredSize();
            suggestionBounds.setSize(Math.max(fieldWidth, listSize.width), listSize.height);
            all = all.union(suggestionBounds);
        }
        Rectangle problemBounds = new Rectangle(fieldBounds.x, all.y + all.height + GAP, 0, 0);
        if (problemText.isVisible()) {
            problemBounds.setSize(problemText.getPreferredSize());
            all = all.union(problemBounds);
        }
        panel.setBounds(all);
        text.setBounds(relative(fieldBounds, all));
        confirmButton.setBounds(relative(confirmBounds, all));
        cancelButton.setBounds(relative(cancelBounds, all));
        suggestionList.setBounds(relative(suggestionBounds, all));
        problemText.setBounds(relative(problemBounds, all));
        panel.revalidate();
        panel.repaint();
    }

    private static Rectangle relative(Rectangle bounds, Rectangle origin) {
        return new Rectangle(bounds.x - origin.x, bounds.y - origin.y, bounds.width, bounds.height);
    }

    private static JButton iconButton(String icon, String toolTipKey, Runnable whenPressed) {
        JButton button = new JButton(Icons.get(icon, Icons.MENU_SIZE));
        button.setToolTipText(Messages.text(toolTipKey));
        button.setFocusable(false); // the field keeps the focus, and its keys
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setMargin(new Insets(2, 2, 2, 2));
        button.addActionListener(event -> whenPressed.run());
        return button;
    }

    /** White on the solid red badge of {@link #problemBackground()}. */
    static Color problemForeground() {
        return ProblemBadge.foreground();
    }

    /** The problem's badge: see {@link ProblemBadge#background()}. */
    static Color problemBackground() {
        return ProblemBadge.background();
    }

    /**
     * The text field. A key it has no binding for stops here instead of going on to the canvas, the window's
     * ancestors and the menu accelerators; only Alt combinations (menu mnemonics) go on.
     */
    static final class Field extends JTextField {

        /**
         * For tests: handles {@code event} as Swing does once the focus manager hands it to the focused field (a
         * headless field never owns the focus, so {@code dispatchEvent} would drop it).
         */
        void deliver(KeyEvent event) {
            processKeyEvent(event);
        }

        @Override
        protected boolean processKeyBinding(KeyStroke stroke, KeyEvent event, int condition, boolean pressed) {
            if (super.processKeyBinding(stroke, event, condition, pressed)) {
                return true;
            }
            // Swing first tries the stroke of the extended key code, then the plain one: swallow on the last try.
            return condition == WHEN_FOCUSED && stroke.getKeyCode() == event.getKeyCode() && keepsToItself(event);
        }

    }

    /** A key the editor keeps from the canvas, the window and the menu accelerators: all but Alt combinations. */
    private static boolean keepsToItself(KeyEvent event) {
        int modifiers = event.getModifiersEx();
        boolean alt = (modifiers & InputEvent.ALT_DOWN_MASK) != 0;
        boolean controlOrMeta = (modifiers & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
        return !alt || controlOrMeta;
    }

    /** The text area of a note (IA.3): like {@link Field}, it keeps every key but Alt combinations to itself. */
    static final class Area extends JTextArea {

        /** For tests: see {@link Field#deliver}. */
        void deliver(KeyEvent event) {
            processKeyEvent(event);
        }

        @Override
        protected boolean processKeyBinding(KeyStroke stroke, KeyEvent event, int condition, boolean pressed) {
            if (super.processKeyBinding(stroke, event, condition, pressed)) {
                return true;
            }
            return condition == WHEN_FOCUSED && stroke.getKeyCode() == event.getKeyCode() && keepsToItself(event);
        }
    }
}
