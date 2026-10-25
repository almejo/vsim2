package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.SymbolPainter;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The «Formas ▾» popup of the shape editor: a three by three grid of thumbnails of the starting shapes, each with its
 * caption, drawn from the same paths the painter uses. Thumbnails read the colour scheme when they paint.
 */
public final class ShapeTemplatesPopup {
    private static final int COLUMNS = 3;
    private static final int THUMBNAIL = 64;
    private static final int THUMBNAIL_PADDING = 6;
    private static final int CELL_WIDTH = 108;
    private static final int CAPTION_WIDTH = 80;
    private static final int GAP = 4;
    private static final float THUMBNAIL_STROKE = 1.5f;
    private static final double DEFAULT_BOX_CORNER = 6;
    private static final String CAPTION_PREFIX = "shape.template.";

    private ShapeTemplatesPopup() { }

    /**
     * The popup for a box of {@code box} units; choosing a thumbnail closes it and gives {@code chosen} the template.
     * The caller shows it under its button.
     */
    public static JPopupMenu create(GridSize box, Supplier<ColorScheme> scheme, Consumer<SymbolTemplates.Template> chosen) {
        JPopupMenu popup = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(0, COLUMNS, GAP, GAP));
        grid.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
        grid.setOpaque(false);
        for (SymbolTemplates.Template template : SymbolTemplates.Template.values()) {
            grid.add(cell(template, box, scheme, chosen, popup));
        }
        popup.add(grid);
        return popup;
    }

    private static JButton cell(SymbolTemplates.Template template, GridSize box, Supplier<ColorScheme> scheme,
                                Consumer<SymbolTemplates.Template> chosen, JPopupMenu popup) {
        String caption = Messages.text(CAPTION_PREFIX + template.name().toLowerCase(Locale.ROOT));
        JButton cell = new JButton(wrapped(caption), new Thumbnail(template, box, scheme));
        cell.putClientProperty("JButton.buttonType", "toolBarButton");
        cell.setVerticalAlignment(SwingConstants.TOP);
        cell.setVerticalTextPosition(SwingConstants.BOTTOM);
        cell.setHorizontalTextPosition(SwingConstants.CENTER);
        cell.setPreferredSize(new Dimension(CELL_WIDTH, THUMBNAIL + 2 * GAP + 2 * cell.getFontMetrics(cell.getFont()).getHeight()
                + GAP));
        cell.getAccessibleContext().setAccessibleName(caption);
        cell.addActionListener(ignored -> {
            popup.setVisible(false);
            chosen.accept(template);
        });
        return cell;
    }

    /** The caption centred under its thumbnail, wrapping when it is longer than the cell. */
    private static String wrapped(String caption) {
        return "<html><div style='text-align: center; width: " + CAPTION_WIDTH + "px'>"
                + caption.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>";
    }

    /** One template drawn into a square, scaled to fit with its own stroke (the name text is left out). */
    private static final class Thumbnail implements Icon {
        private final Optional<SymbolDrawing> drawing;
        private final GridSize box;
        private final Supplier<ColorScheme> scheme;

        Thumbnail(SymbolTemplates.Template template, GridSize box, Supplier<ColorScheme> scheme) {
            this.drawing = SymbolTemplates.drawing(template, box);
            this.box = box;
            this.scheme = scheme;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            ColorScheme colors = scheme.get();
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                double width = box.width() * (double) SymbolPoint.EIGHTHS;
                double height = box.height() * (double) SymbolPoint.EIGHTHS;
                double scale = (THUMBNAIL - 2.0 * THUMBNAIL_PADDING) / Math.max(width, height);
                AffineTransform fit = new AffineTransform();
                fit.translate(x + (THUMBNAIL - width * scale) / 2, y + (THUMBNAIL - height * scale) / 2);
                fit.scale(scale, scale);
                canvas.setStroke(new BasicStroke(THUMBNAIL_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (drawing.isEmpty()) {
                    paintDefaultBox(canvas, colors, fit, width, height);
                    return;
                }
                for (SymbolPath path : drawing.get().paths()) {
                    Shape shape = fit.createTransformedShape(SymbolPainter.path(path));
                    if (path.filled()) {
                        canvas.setColor(colors.componentFill());
                        canvas.fill(shape);
                    }
                    canvas.setColor(colors.componentStroke());
                    canvas.draw(shape);
                }
            } finally {
                canvas.dispose();
            }
        }

        private static void paintDefaultBox(Graphics2D canvas, ColorScheme colors, AffineTransform fit, double width,
                                            double height) {
            Shape body = fit.createTransformedShape(
                    new RoundRectangle2D.Double(0, 0, width, height, DEFAULT_BOX_CORNER, DEFAULT_BOX_CORNER));
            canvas.setColor(colors.componentFill());
            canvas.fill(body);
            canvas.setColor(colors.componentStroke());
            canvas.draw(body);
            Shape titleBar = fit.createTransformedShape(
                    new Line2D.Double(width * 0.25, height * 0.12, width * 0.75, height * 0.12));
            canvas.setColor(colors.text());
            canvas.draw(titleBar);
        }

        @Override public int getIconWidth() { return THUMBNAIL; }
        @Override public int getIconHeight() { return THUMBNAIL; }
    }
}
