package cl.almejo.vsim.ui.inline;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.paint.TextFonts;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Edits a text right where the canvas draws it (M6.5): a one-line field over the text, scaled with the zoom, with ✓
 * and ✗ buttons after it and the validator's problem below it. Enter, ✓ or a mouse press anywhere else in the same
 * window confirm; Esc or ✗ cancel. With a problem, Enter and ✓ do nothing and a press outside cancels, so an invalid
 * text never sticks and the editor never traps the user. Losing the focus to another application changes nothing.
 * <p>
 * While the field has the focus it keeps every key to itself (only Alt combinations go on, for the menu mnemonics):
 * Supr, Ctrl+Z or the arrows edit the text and never reach the canvas bindings or the menu accelerators.
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
    private static final String OUTLINE = "JComponent.outline";
    private static final String OUTLINE_ERROR = "error";
    /** WCAG AA contrast for normal text. */
    private static final double MINIMUM_PROBLEM_CONTRAST = 4.5;

    private final CircuitCanvas canvas;
    private final JPanel panel = new JPanel(null);
    private final Field field = new Field();
    private final JButton confirmButton;
    private final JButton cancelButton;
    private final JLabel problemText = new ProblemLabel();
    private final AWTEventListener pressListener = this::anywhereInTheApplication;
    private final DocumentListener documentListener = this::documentChanged;
    private final List<Runnable> whileOpen = new ArrayList<>();
    private InlineEditRequest request;
    private BooleanSupplier stillValid = () -> true;
    private Optional<String> problem = Optional.empty();

    public InlineTextEditor(CircuitCanvas canvas) {
        this.canvas = canvas;
        panel.setOpaque(false);
        // a press on the empty parts of the editor stops here instead of reaching the canvas tool, and the
        // canvas tool's cursor (a resize arrow on a frame's edge) never shows through
        panel.addMouseListener(new MouseAdapter() { });
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        problemText.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        field.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        field.setFocusTraversalKeysEnabled(false); // Tab does nothing: the focus stays in the field
        confirmButton = iconButton(InlineIcons.CONFIRM, "inline.confirm", this::confirm);
        cancelButton = iconButton(InlineIcons.CANCEL, "inline.cancel", this::cancel);
        problemText.setOpaque(true);
        problemText.setBorder(new EmptyBorder(1, 4, 1, 4));
        problemText.setVisible(false);
        panel.add(field);
        panel.add(confirmButton);
        panel.add(cancelButton);
        panel.add(problemText);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), CONFIRM);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CANCEL);
        field.getActionMap().put(CONFIRM, action(this::confirm));
        field.getActionMap().put(CANCEL, action(this::cancel));
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
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
        });
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
        request = newRequest;
        stillValid = newStillValid;
        // the components live detached while closed, so a theme change did not reach them
        SwingUtilities.updateComponentTreeUI(panel);
        field.setHorizontalAlignment(switch (newRequest.anchor()) {
            case LEFT -> SwingConstants.LEADING;
            case CENTER -> SwingConstants.CENTER;
            case RIGHT -> SwingConstants.RIGHT;
        });
        field.setText(newRequest.initialText());
        field.selectAll();
        whileOpen.add(canvas.addFloatingComponent(panel));
        whileOpen.add(canvas.addViewportListener(this::layOut));
        CircuitDocument document = canvas.openDocument().document();
        document.addListener(documentListener);
        whileOpen.add(() -> document.removeListener(documentListener));
        Toolkit.getDefaultToolkit().addAWTEventListener(pressListener, AWTEvent.MOUSE_EVENT_MASK);
        whileOpen.add(() -> Toolkit.getDefaultToolkit().removeAWTEventListener(pressListener));
        // the request carries the font and area it was opened with: a font change (Preferencias) ends the edit
        Runnable fontChanged = this::confirmOrCancel;
        TextFonts.addListener(fontChanged);
        whileOpen.add(() -> TextFonts.removeListener(fontChanged));
        check();
        field.requestFocusInWindow();
    }

    public boolean isOpen() {
        return request != null;
    }

    /** Hands the text to the request and closes; does nothing while the text has a problem. */
    public void confirm() {
        if (!isOpen()) {
            return;
        }
        String text = field.getText();
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

    public JTextField field() {
        return field;
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

    /**
     * Confirms the open edit when its text is valid and cancels it otherwise; nothing when closed. The window calls it
     * before it closes (M6.8), so «¿Guardar cambios?» sees a half-typed edit instead of losing it.
     */
    public void confirmOrCancel() {
        if (!isOpen()) {
            return;
        }
        if (request.validator().apply(field.getText()).isPresent()) {
            cancel();
        } else {
            confirm();
        }
    }

    private void close() {
        boolean hadFocus = field.isFocusOwner();
        request = null;
        stillValid = () -> true;
        List<Runnable> removals = new ArrayList<>(whileOpen);
        whileOpen.clear();
        for (int index = removals.size() - 1; index >= 0; index--) {
            removals.get(index).run();
        }
        if (hadFocus) {
            canvas.requestFocusInWindow();
        }
    }

    private void textChanged() {
        if (isOpen()) {
            check();
        }
    }

    /** Runs the validator on the current text and shows what it says. */
    private void check() {
        problem = request.validator().apply(field.getText());
        field.putClientProperty(OUTLINE, problem.isPresent() ? OUTLINE_ERROR : null);
        problemText.setText(problem.orElse(""));
        problemText.setVisible(problem.isPresent());
        confirmButton.setEnabled(problem.isEmpty());
        layOut();
    }

    private void documentChanged(CircuitDocument document, DocumentChange change) {
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
        Component canvasRoot = rootOf(canvas);
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
        Viewport viewport = canvas.viewport();
        Rectangle2D area = viewport.documentTransform().createTransformedShape(request.documentArea()).getBounds2D();
        float size = (float) Math.max(MINIMUM_FONT_SIZE, request.font().getSize2D() * viewport.zoom());
        Font font = request.font().deriveFont(size);
        if (!font.equals(field.getFont())) {
            field.setFont(font);
        }
        Insets insets = field.getInsets();
        int horizontalInsets = insets.left + insets.right;
        int verticalInsets = insets.top + insets.bottom;
        int areaX = (int) Math.round(area.getX());
        int areaY = (int) Math.round(area.getY());
        int areaWidth = (int) Math.round(area.getWidth());
        int areaHeight = (int) Math.round(area.getHeight());
        int textWidth = field.getFontMetrics(font).stringWidth(field.getText()) + CARET_ROOM;
        int fieldWidth = Math.min(MAXIMUM_WIDTH,
                Math.max(Math.max(areaWidth, MINIMUM_TEXT_WIDTH), textWidth) + horizontalInsets);
        int fittedHeight = areaHeight + verticalInsets;
        int fieldHeight = Math.max(fittedHeight, field.getPreferredSize().height);
        int fieldX = switch (request.anchor()) {
            case LEFT -> areaX - insets.left;
            case CENTER -> (int) Math.round(area.getCenterX() - fieldWidth / 2.0);
            case RIGHT -> areaX + areaWidth + insets.right - fieldWidth;
        };
        Rectangle fieldBounds = new Rectangle(fieldX, areaY - insets.top - (fieldHeight - fittedHeight) / 2,
                fieldWidth, fieldHeight);

        Dimension buttonSize = confirmButton.getPreferredSize();
        int side = Math.max(buttonSize.width, buttonSize.height);
        int buttonY = fieldBounds.y + (fieldHeight - side) / 2;
        Rectangle confirmBounds = new Rectangle(fieldBounds.x + fieldWidth + GAP, buttonY, side, side);
        Rectangle cancelBounds = new Rectangle(confirmBounds.x + side, buttonY, side, side);
        Rectangle all = fieldBounds.union(confirmBounds).union(cancelBounds);
        Rectangle problemBounds = new Rectangle(fieldBounds.x, all.y + all.height + GAP, 0, 0);
        if (problemText.isVisible()) {
            problemBounds.setSize(problemText.getPreferredSize());
            all = all.union(problemBounds);
        }
        panel.setBounds(all);
        field.setBounds(relative(fieldBounds, all));
        confirmButton.setBounds(relative(confirmBounds, all));
        cancelButton.setBounds(relative(cancelBounds, all));
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

    private static AbstractAction action(Runnable whenPerformed) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                whenPerformed.run();
            }
        };
    }

    /** White on the solid red badge of {@link #problemBackground()}. */
    static Color problemForeground() {
        return Color.WHITE;
    }

    /**
     * The problem's badge: the theme's error red (read every time: the theme may change while it shows), darkened
     * until white text on it reaches the WCAG AA contrast for normal text in light and dark themes alike.
     */
    static Color problemBackground() {
        Color color = themeErrorColor();
        while (contrastWithWhite(color) < MINIMUM_PROBLEM_CONTRAST) {
            color = new Color((int) (color.getRed() * 0.9), (int) (color.getGreen() * 0.9),
                    (int) (color.getBlue() * 0.9));
        }
        return color;
    }

    private static Color themeErrorColor() {
        for (String key : List.of("Actions.Red", "Component.error.focusedBorderColor")) {
            Color color = UIManager.getColor(key);
            if (color != null) {
                return color;
            }
        }
        return new Color(0xDB5860);
    }

    private static double contrastWithWhite(Color color) {
        return 1.05 / (relativeLuminance(color) + 0.05);
    }

    private static double relativeLuminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double fraction = channel / 255.0;
        return fraction <= 0.03928 ? fraction / 12.92 : Math.pow((fraction + 0.055) / 1.055, 2.4);
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

        private static boolean keepsToItself(KeyEvent event) {
            int modifiers = event.getModifiersEx();
            boolean alt = (modifiers & InputEvent.ALT_DOWN_MASK) != 0;
            boolean controlOrMeta = (modifiers & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
            return !alt || controlOrMeta;
        }
    }

    private static final class ProblemLabel extends JLabel {

        @Override
        public Color getForeground() {
            return problemForeground();
        }

        @Override
        public Color getBackground() {
            return problemBackground();
        }
    }
}
