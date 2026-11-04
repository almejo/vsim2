package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.JComponent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The bit strip of the «Grupos» window (spec §2.3): one cell per bus bit, bit 31 on the left, in its pin's colour
 * (no fill, a dashed outline and a dimmed number when unused, outlined in the error colour when it is in two pins). A click on a cell, or Space on the cell
 * chosen with the arrow keys, moves the bit to the selected pin or takes it out ({@link GroupsDraft#toggleBit}); the
 * tooltip says where the bit goes. Colours are read from the scheme and the theme at every paint.
 */
@SuppressWarnings("serial")
final class BitStrip extends JComponent {

    private static final int CELL_WIDTH = 22;
    private static final int CELL_HEIGHT = 30;
    private static final int CELL_GAP = 2;
    private static final double CORNER = 4;
    private static final float CONFLICT_STROKE = 2f;
    private static final float FOCUS_STROKE = 1.5f;
    private static final float UNUSED_STROKE = 1f;
    private static final float[] UNUSED_DASH = {3f, 3f};

    private final GroupsDraft draft;
    private final Supplier<ColorScheme> scheme;
    private final IntConsumer toggled;
    /** The bit Space toggles, moved with the arrow keys. */
    private int focusedBit;

    /** {@code toggled} hears the bit a click or Space toggled, after the draft changed. */
    BitStrip(GroupsDraft draft, Supplier<ColorScheme> scheme, IntConsumer toggled) {
        this.draft = draft;
        this.scheme = scheme;
        this.toggled = toggled;
        this.focusedBit = draft.width() - 1;
        setName("groups.strip");
        setFocusable(true);
        setToolTipText("");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                bitAt(event.getX()).ifPresent(bit -> {
                    focusedBit = bit;
                    toggle(bit);
                });
            }
        });
        bindKey(KeyEvent.VK_LEFT, "groups.strip.higher", () -> moveFocus(1));
        bindKey(KeyEvent.VK_RIGHT, "groups.strip.lower", () -> moveFocus(-1));
        bindKey(KeyEvent.VK_SPACE, "groups.strip.toggle", () -> toggle(focusedBit));
    }

    private void bindKey(int keyCode, String name, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private void moveFocus(int towardsHigher) {
        focusedBit = Math.clamp(focusedBit + towardsHigher, 0, draft.width() - 1);
        repaint();
    }

    private void toggle(int bit) {
        draft.toggleBit(bit);
        toggled.accept(bit);
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(draft.width() * (CELL_WIDTH + CELL_GAP), CELL_HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    /** Where bit {@code bit}'s cell is drawn now: the highest bit on the left. */
    Rectangle cellOf(int bit) {
        double step = (double) getWidth() / draft.width();
        int column = draft.width() - 1 - bit;
        int left = (int) Math.round(column * step);
        int right = (int) Math.round((column + 1) * step);
        return new Rectangle(left, 0, Math.max(1, right - left - CELL_GAP), Math.max(1, getHeight()));
    }

    private OptionalInt bitAt(int x) {
        if (getWidth() <= 0 || x < 0 || x >= getWidth()) {
            return OptionalInt.empty();
        }
        int column = (int) (x * (long) draft.width() / getWidth());
        return OptionalInt.of(draft.width() - 1 - column);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        OptionalInt bit = bitAt(event.getX());
        if (bit.isEmpty()) {
            return null;
        }
        List<Integer> owners = draft.ownersOf(bit.getAsInt());
        return switch (owners.size()) {
            case 0 -> Messages.format("groups.window.bitUnused", bit.getAsInt());
            case 1 -> Messages.format("groups.window.bitInPin", bit.getAsInt(), owners.getFirst() + 1);
            default -> Messages.format("groups.window.bitInPins", bit.getAsInt(),
                    owners.stream().map(pin -> String.valueOf(pin + 1)).collect(Collectors.joining(", ")));
        };
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            local.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            ColorScheme colors = scheme.get();
            local.setFont(font());
            FontMetrics metrics = local.getFontMetrics();
            for (int bit = 0; bit < draft.width(); bit++) {
                paintCell(local, colors, metrics, bit);
            }
        } finally {
            local.dispose();
        }
    }

    /** The panel's font, or the theme's label font before the strip is in a window. */
    private Font font() {
        return getFont() != null ? getFont() : UIManager.getFont("Label.font");
    }

    private void paintCell(Graphics2D graphics, ColorScheme colors, FontMetrics metrics, int bit) {
        Rectangle cell = cellOf(bit);
        RoundRectangle2D shape = new RoundRectangle2D.Double(cell.x + 1, cell.y + 1, cell.width - 2, cell.height - 2,
                CORNER, CORNER);
        List<Integer> owners = draft.ownersOf(bit);
        if (owners.isEmpty()) {
            // not told by colour alone: no fill, a dashed outline and a dimmed number
            graphics.setColor(colors.bitGroupUnusedText());
            graphics.setStroke(new BasicStroke(UNUSED_STROKE, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
                    UNUSED_DASH, 0f));
            graphics.draw(shape);
        } else {
            graphics.setColor(colors.bitGroupColor(owners.getFirst()));
            graphics.fill(shape);
        }
        if (owners.size() > 1) {
            graphics.setColor(ReadableColors.errorText());
            graphics.setStroke(new BasicStroke(CONFLICT_STROKE));
            graphics.draw(shape);
        } else if (bit == focusedBit && isFocusOwner()) {
            graphics.setColor(colors.bitGroupText());
            graphics.setStroke(new BasicStroke(FOCUS_STROKE));
            graphics.draw(shape);
        }
        String number = String.valueOf(bit);
        graphics.setColor(owners.isEmpty() ? colors.bitGroupUnusedText() : colors.bitGroupText());
        graphics.drawString(number, cell.x + (cell.width - metrics.stringWidth(number)) / 2,
                cell.y + (cell.height + metrics.getAscent() - metrics.getDescent()) / 2);
    }
}
