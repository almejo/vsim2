package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.ui.paint.ColorScheme;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The icons of the machine palette's rows, drawn in the diagram's own style from the colour scheme at every paint (so
 * a theme change shows at once; nothing is cached): a circle for «Estado», an arrow for «Transición», a small yellow
 * sticky note for «Nota» and a letter for «Texto». They are as big as the circuit palette's icons.
 */
final class MachinePaletteIcons {

    /** The circuit palette's icon size, before UI scaling. */
    static final int SIZE = 24;
    private static final double DRAWING_UNITS = 24;

    /** What an icon draws. */
    enum Kind {
        STATE, TRANSITION, NOTE, TEXT
    }

    private MachinePaletteIcons() {
    }

    /** The icon of {@code kind}, in the colours {@code scheme} gives at each paint. */
    static Icon of(Kind kind, Supplier<ColorScheme> scheme) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scheme, "scheme");
        return new DrawnIcon(kind, scheme);
    }

    private record DrawnIcon(Kind kind, Supplier<ColorScheme> scheme) implements Icon {

        @Override
        public int getIconWidth() {
            return UIScale.scale(SIZE);
        }

        @Override
        public int getIconHeight() {
            return UIScale.scale(SIZE);
        }

        @Override
        public void paintIcon(Component owner, Graphics graphics, int x, int y) {
            Graphics2D local = (Graphics2D) graphics.create();
            try {
                local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                local.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                local.translate(x, y);
                double factor = getIconWidth() / DRAWING_UNITS;
                local.scale(factor, factor);
                ColorScheme colours = scheme.get();
                switch (kind) {
                    case STATE -> paintState(local, colours);
                    case TRANSITION -> paintTransition(local, colours);
                    case NOTE -> paintNote(local);
                    case TEXT -> paintText(local, colours);
                }
            } finally {
                local.dispose();
            }
        }

        private static void paintState(Graphics2D graphics, ColorScheme colours) {
            Ellipse2D circle = new Ellipse2D.Double(3.5, 3.5, 17, 17);
            graphics.setColor(MachineColors.stateFill(colours));
            graphics.fill(circle);
            graphics.setColor(MachineColors.stateOutline(colours));
            graphics.setStroke(new BasicStroke(1.75f));
            graphics.draw(circle);
        }

        private static void paintTransition(Graphics2D graphics, ColorScheme colours) {
            graphics.setColor(MachineColors.stateOutline(colours));
            graphics.setStroke(new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(new Line2D.Double(3.5, 19, 19.5, 5.5));
            Path2D head = new Path2D.Double();
            head.moveTo(20.5, 4.5);
            head.lineTo(13.5, 6.5);
            head.lineTo(18.5, 11.5);
            head.closePath();
            graphics.fill(head);
        }

        private void paintNote(Graphics2D graphics) {
            ColorScheme colours = scheme.get();
            Color paper = colours.stickyPaper(StickyColor.YELLOW);
            Path2D sheet = new Path2D.Double();
            sheet.moveTo(4, 4);
            sheet.lineTo(20, 4);
            sheet.lineTo(20, 15);
            sheet.lineTo(15, 20);
            sheet.lineTo(4, 20);
            sheet.closePath();
            graphics.setColor(paper);
            graphics.fill(sheet);
            Path2D fold = new Path2D.Double();
            fold.moveTo(20, 15);
            fold.lineTo(15, 15);
            fold.lineTo(15, 20);
            fold.closePath();
            graphics.setColor(colours.stickyFold(StickyColor.YELLOW));
            graphics.fill(fold);
            graphics.setColor(colours.stickyText(StickyColor.YELLOW));
            graphics.setStroke(new BasicStroke(1.25f));
            graphics.draw(new Line2D.Double(7, 8.5, 17, 8.5));
            graphics.draw(new Line2D.Double(7, 12, 14, 12));
        }

        private static void paintText(Graphics2D graphics, ColorScheme colours) {
            graphics.setColor(MachineColors.text(colours));
            graphics.setStroke(new BasicStroke(2.25f));
            graphics.draw(new Line2D.Double(5.5, 5.5, 18.5, 5.5));
            graphics.draw(new Line2D.Double(12, 5.5, 12, 19));
            graphics.setStroke(new BasicStroke(1.5f));
            graphics.draw(new Line2D.Double(9, 19, 15, 19));
        }
    }
}
