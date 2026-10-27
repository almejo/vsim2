package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;

import cl.almejo.vsim.ui.paint.TextFonts;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

/**
 * Where everything of a sticky note goes, in document pixels (8 per grid unit): the paper, its folded corner, the ✕,
 * the fold button «–» at the top-left corner, the resize handle, the wrapped text lines and the checkboxes. One layout
 * feeds both the painter and the hit tester,
 * so what the user sees is what they click. Text wraps at the paper width (a word longer than a line breaks by
 * characters); what does not fit the height is clipped and the last visible line ends in «…». Below
 * {@link #MINIMUM_PIXELS_PER_GRID_FOR_DETAILS} screen pixels per grid unit the text and the checkboxes are left out,
 * as frame titles are: they would be a smear. A folded note is only its paper and its fold.
 */
public record StickyNoteLayout(Rectangle2D paper, Shape fold, Rectangle2D closeBox, Rectangle2D foldButton,
                               Rectangle2D resizeHandle,
                               List<Line> lines, List<Rectangle2D> checkboxes) {

    /**
     * One line of text: its string, the left end and baseline of where it is drawn, and the checklist item it belongs
     * to ({@link #NO_ITEM} for the text of a free-text note).
     */
    public record Line(String text, double x, double baseline, int itemIndex) {
        public static final int NO_ITEM = -1;
    }

    public static final double PADDING = 6;
    public static final double CLOSE_SIZE = 10;
    public static final double CLOSE_INSET = 3;
    public static final double RESIZE_SIZE = 10;
    public static final double FOLD_SIZE = 8;
    public static final double CHECKBOX_SIZE = 9;
    public static final double CHECKBOX_GAP = 4;
    public static final double MINIMUM_PIXELS_PER_GRID_FOR_DETAILS = 3;
    static final String ELLIPSIS = "…";

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    /** Where the text starts below the paper's top when the two buttons get a strip of their own. */
    private static final double CONTROL_STRIP = CLOSE_INSET + CLOSE_SIZE + 2;
    /** A paper lower than this has no room for the strip: the buttons then lie over the first line's ends. */
    private static final double TALL_ENOUGH_FOR_STRIP = 4 * UNIT;
    /** A paper narrower than this keeps no room for the ✕ in its first line: the line would be a letter. */
    private static final double WIDE_ENOUGH_FOR_CLOSE_ROOM = 6 * UNIT;
    private static final int LAST_CHARACTER_OFFSET = 1;
    private static volatile Measuring measuring;

    public StickyNoteLayout {
        lines = List.copyOf(lines);
        checkboxes = List.copyOf(checkboxes);
    }

    /** The paper of {@code note} (its visible size) in document pixels. */
    public static Rectangle2D paperOf(StickyNote note) {
        return new Rectangle2D.Double(note.position().x() * UNIT, note.position().y() * UNIT,
                note.visibleWidth() * UNIT, note.visibleHeight() * UNIT);
    }

    /** The folded corner of {@code note}: a triangle at the bottom-right of its paper. */
    public static Shape foldOf(StickyNote note) {
        Rectangle2D paper = paperOf(note);
        double size = note.folded() ? paper.getWidth() / 3 : FOLD_SIZE;
        Path2D.Double triangle = new Path2D.Double();
        triangle.moveTo(paper.getMaxX() - size, paper.getMaxY());
        triangle.lineTo(paper.getMaxX(), paper.getMaxY() - size);
        triangle.lineTo(paper.getMaxX() - size, paper.getMaxY() - size);
        triangle.closePath();
        return triangle;
    }

    /**
     * The font metrics every layout is measured with, whatever the zoom: fractional metrics of the canvas text font on
     * an untransformed graphics. Hinted metrics change with the graphics' scale (a text can wrap at 75 % and not at
     * 100 %), so painting with the canvas graphics' own metrics would draw the checkboxes where the hit tester, which
     * has no such graphics, does not look for them. The painter draws with fractional metrics too, so the glyphs land
     * where this layout puts them.
     */
    public static FontMetrics measuring() {
        Font font = TextFonts.labelFont();
        Measuring cached = measuring;
        if (cached == null || !cached.font().equals(font)) {
            Graphics2D scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            try {
                scratch.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                        RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                scratch.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                cached = new Measuring(font, scratch.getFontMetrics(font));
            } finally {
                scratch.dispose();
            }
            measuring = cached;
        }
        return cached.metrics();
    }

    private record Measuring(Font font, FontMetrics metrics) {
    }

    /** The layout of {@code note} at {@code pixelsPerGrid}, measured with {@link #measuring()}. */
    public static StickyNoteLayout of(StickyNote note, double pixelsPerGrid) {
        return of(note, measuring(), pixelsPerGrid);
    }

    /** The layout of {@code note} measured with {@code metrics} (the canvas text font) at {@code pixelsPerGrid}. */
    public static StickyNoteLayout of(StickyNote note, FontMetrics metrics, double pixelsPerGrid) {
        Rectangle2D paper = paperOf(note);
        Shape fold = foldOf(note);
        if (note.folded()) {
            Rectangle2D none = new Rectangle2D.Double(paper.getMaxX(), paper.getMaxY(), 0, 0);
            return new StickyNoteLayout(paper, fold, none, none, none, List.of(), List.of());
        }
        Rectangle2D closeBox = new Rectangle2D.Double(paper.getMaxX() - CLOSE_INSET - CLOSE_SIZE,
                paper.getY() + CLOSE_INSET, CLOSE_SIZE, CLOSE_SIZE);
        Rectangle2D foldButton = new Rectangle2D.Double(paper.getX() + CLOSE_INSET, paper.getY() + CLOSE_INSET,
                CLOSE_SIZE, CLOSE_SIZE);
        Rectangle2D resizeHandle = new Rectangle2D.Double(paper.getMaxX() - RESIZE_SIZE,
                paper.getMaxY() - RESIZE_SIZE, RESIZE_SIZE, RESIZE_SIZE);
        if (pixelsPerGrid < MINIMUM_PIXELS_PER_GRID_FOR_DETAILS) {
            return new StickyNoteLayout(paper, fold, closeBox, foldButton, resizeHandle, List.of(), List.of());
        }
        Text text = new Text(paper, closeBox, metrics);
        switch (note.content()) {
            case StickyText freeText -> text.addFreeText(freeText.text());
            case StickyChecklist checklist -> text.addChecklist(checklist.items());
        }
        return new StickyNoteLayout(paper, fold, closeBox, foldButton, resizeHandle, text.lines(),
                text.checkboxes());
    }

    /** The wrapping of one note's text: rows are filled until the paper's height is used up. */
    private static final class Text {

        private final Rectangle2D paper;
        private final FontMetrics metrics;
        private final double left;
        private final double top;
        private final double lineHeight;
        private final int capacity;
        private final double closeRoom;
        private final List<Line> rows = new ArrayList<>();
        private final List<Rectangle2D> boxes = new ArrayList<>();

        Text(Rectangle2D paper, Rectangle2D closeBox, FontMetrics metrics) {
            this.paper = paper;
            this.metrics = metrics;
            this.left = paper.getX() + PADDING;
            boolean strip = paper.getHeight() >= TALL_ENOUGH_FOR_STRIP;
            this.top = paper.getY() + (strip ? CONTROL_STRIP : PADDING);
            this.lineHeight = Math.max(1, metrics.getHeight());
            double room = paper.getHeight() - (top - paper.getY()) - PADDING;
            this.capacity = Math.max(1, (int) Math.floor(room / lineHeight));
            this.closeRoom = !strip && paper.getWidth() >= WIDE_ENOUGH_FOR_CLOSE_ROOM ? CLOSE_SIZE + CLOSE_INSET : 0;
        }

        List<Line> lines() {
            return rows;
        }

        List<Rectangle2D> checkboxes() {
            return boxes;
        }

        /** The width of row {@code row} counted from {@code indent}: the first row leaves room for the ✕. */
        private double widthOfRow(int row, double indent) {
            double width = paper.getWidth() - 2 * PADDING - indent;
            return row == 0 ? width - closeRoom : width;
        }

        private double baselineOfRow(int row) {
            return top + row * lineHeight + metrics.getAscent();
        }

        void addFreeText(String text) {
            if (text.isEmpty()) {
                return;
            }
            for (String paragraph : text.split("\n", -1)) {
                if (rows.size() > capacity) {
                    break;
                }
                for (String row : wrap(paragraph, rows.size(), 0)) {
                    rows.add(new Line(row, left, baselineOfRow(rows.size()), Line.NO_ITEM));
                }
            }
            clip(0);
        }

        void addChecklist(List<StickyItem> items) {
            double indent = CHECKBOX_SIZE + CHECKBOX_GAP;
            for (int itemIndex = 0; itemIndex < items.size() && rows.size() <= capacity; itemIndex++) {
                int firstRow = rows.size();
                for (String row : wrap(items.get(itemIndex).text(), firstRow, indent)) {
                    rows.add(new Line(row, left + indent, baselineOfRow(rows.size()), itemIndex));
                }
                if (firstRow < capacity) {
                    double boxTop = top + firstRow * lineHeight + (lineHeight - CHECKBOX_SIZE) / 2;
                    boxes.add(new Rectangle2D.Double(left, boxTop, CHECKBOX_SIZE, CHECKBOX_SIZE));
                }
            }
            clip(indent);
        }

        /** Keeps only the rows that fit the height; the last of them says there is more with «…». */
        private void clip(double indent) {
            if (rows.size() <= capacity) {
                return;
            }
            while (rows.size() > capacity) {
                rows.removeLast();
            }
            Line last = rows.removeLast();
            rows.add(new Line(ellipsized(last.text(), widthOfRow(rows.size(), indent)), last.x(), last.baseline(),
                    last.itemIndex()));
        }

        /** {@code paragraph} cut into rows, the first being row {@code firstRow} of the note; stops past capacity. */
        private List<String> wrap(String paragraph, int firstRow, double indent) {
            List<String> result = new ArrayList<>();
            String remaining = paragraph;
            if (remaining.isEmpty()) {
                result.add("");
                return result;
            }
            IntToDoubleFunction width = row -> widthOfRow(firstRow + row, indent);
            while (!remaining.isEmpty() && firstRow + result.size() <= capacity) {
                int fit = fittingCharacters(remaining, width.applyAsDouble(result.size()));
                if (fit >= remaining.length()) {
                    result.add(remaining);
                    break;
                }
                int cut = remaining.charAt(fit) == ' ' ? fit : remaining.lastIndexOf(' ', fit - LAST_CHARACTER_OFFSET);
                if (cut > 0) {
                    result.add(remaining.substring(0, cut));
                    remaining = remaining.substring(cut).stripLeading();
                } else {
                    result.add(remaining.substring(0, fit));
                    remaining = remaining.substring(fit);
                }
            }
            return result;
        }

        /** How many leading characters of {@code text} fit in {@code width}; at least one, so wrapping always ends. */
        private int fittingCharacters(String text, double width) {
            int low = 1;
            int high = text.length();
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                if (metrics.stringWidth(text.substring(0, middle)) <= width) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            return low;
        }

        private String ellipsized(String text, double width) {
            String kept = text.stripTrailing();
            while (!kept.isEmpty() && metrics.stringWidth(kept + ELLIPSIS) > width) {
                kept = kept.substring(0, kept.length() - 1);
            }
            return kept + ELLIPSIS;
        }
    }
}
