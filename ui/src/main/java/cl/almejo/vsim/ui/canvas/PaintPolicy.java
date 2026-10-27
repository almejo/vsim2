package cl.almejo.vsim.ui.canvas;

import java.awt.BasicStroke;

/**
 * The paint decisions that differ between the screen and paper (printing spec §2, review I3), carried by
 * {@link RenderState} so they never depend on the destination transform: a preview, a thumbnail and the PDF make the
 * same choices at any scale. {@code alwaysShowFrameTitles} paints frame titles however small one grid unit is (the
 * canvas leaves them out below 3 px, {@code FramePainter.showsDetails}); {@code paintFrameButtons} false lets the
 * title use the room the padlock and ⋯ buttons would keep; {@code monochrome} is «Blanco y negro» (every colour grey,
 * frames without fill, wire states by grey level and dash pattern); {@code minimumStrokePixels} is the thinnest
 * stroke of wires and frame borders, in document pixels (the renderer's own units, 8 per grid unit), 0 for none;
 * {@code paintStickyNotes} false leaves the user's sticky notes out (paper: they are reminders, not part of the
 * figure, unless the user asks for them); {@code paintStickyControls} false leaves the ✕ and the fold button of the
 * notes out (they are screen chrome, like the buttons of a frame, and do not belong in a figure).
 */
public record PaintPolicy(boolean alwaysShowFrameTitles, boolean paintFrameButtons, boolean monochrome,
                          double minimumStrokePixels, boolean paintStickyNotes, boolean paintStickyControls) {

    /** The canvas: what VSim always painted. */
    public static final PaintPolicy CANVAS = new PaintPolicy(false, true, false, 0, true, true);

    /** The PNG/SVG image export: the canvas without the controls of the sticky notes. */
    public static final PaintPolicy IMAGE = CANVAS.withStickyControls(false);

    /** The thinnest wire or frame border on paper, in points: about 0.18 mm, still visible on any printer. */
    public static final double PAPER_MINIMUM_STROKE_POINTS = 0.5;

    /** A policy whose sticky notes show their controls where it paints the frame buttons. */
    public PaintPolicy(boolean alwaysShowFrameTitles, boolean paintFrameButtons, boolean monochrome,
                       double minimumStrokePixels, boolean paintStickyNotes) {
        this(alwaysShowFrameTitles, paintFrameButtons, monochrome, minimumStrokePixels, paintStickyNotes,
                paintFrameButtons);
    }

    /** A policy that paints sticky notes (every policy before sticky notes existed). */
    public PaintPolicy(boolean alwaysShowFrameTitles, boolean paintFrameButtons, boolean monochrome,
                       double minimumStrokePixels) {
        this(alwaysShowFrameTitles, paintFrameButtons, monochrome, minimumStrokePixels, true);
    }

    public PaintPolicy {
        if (!(minimumStrokePixels >= 0) || Double.isInfinite(minimumStrokePixels)) {
            throw new IllegalArgumentException("minimumStrokePixels must be finite and not negative: "
                    + minimumStrokePixels);
        }
    }

    /**
     * Paper: titles always, no buttons, no sticky notes, and a minimum stroke of {@link #PAPER_MINIMUM_STROKE_POINTS} at 100 % (one
     * document pixel is one point); a page painted at another scale uses {@link #atPageScale}.
     */
    public static PaintPolicy print(boolean monochrome) {
        return new PaintPolicy(true, false, monochrome, PAPER_MINIMUM_STROKE_POINTS, false, false);
    }

    /**
     * This policy for a page where one document pixel is {@code pointsPerDocumentPixel} points: the minimum stroke
     * (given in points, as {@link #print} gives it) turned into document pixels. Apply it once, to the policy of
     * {@link #print}; the result does not depend on how the page is then scaled (preview, thumbnail, PDF).
     */
    public PaintPolicy atPageScale(double pointsPerDocumentPixel) {
        if (!(pointsPerDocumentPixel > 0) || Double.isInfinite(pointsPerDocumentPixel)) {
            throw new IllegalArgumentException("pointsPerDocumentPixel must be positive: " + pointsPerDocumentPixel);
        }
        return new PaintPolicy(alwaysShowFrameTitles, paintFrameButtons, monochrome,
                minimumStrokePixels / pointsPerDocumentPixel, paintStickyNotes, paintStickyControls);
    }

    /** This policy with sticky notes painted or left out. */
    public PaintPolicy withStickyNotes(boolean paint) {
        return new PaintPolicy(alwaysShowFrameTitles, paintFrameButtons, monochrome, minimumStrokePixels, paint,
                paintStickyControls);
    }

    /** This policy with the ✕ and the fold button of the sticky notes painted or left out. */
    public PaintPolicy withStickyControls(boolean paint) {
        return new PaintPolicy(alwaysShowFrameTitles, paintFrameButtons, monochrome, minimumStrokePixels,
                paintStickyNotes, paint);
    }

    /** {@code stroke}, or the same stroke at {@link #minimumStrokePixels} wide when it is thinner than that. */
    public BasicStroke widened(BasicStroke stroke) {
        if (stroke.getLineWidth() >= minimumStrokePixels) {
            return stroke;
        }
        return new BasicStroke((float) minimumStrokePixels, stroke.getEndCap(), stroke.getLineJoin(),
                stroke.getMiterLimit(), stroke.getDashArray(), stroke.getDashPhase());
    }
}
