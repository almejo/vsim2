package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.terminal.TerminalFontSize;
import cl.almejo.vsim.core.terminal.TerminalFrame;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.Optional;

/**
 * A Terminal screen on the canvas at a moment of its animation, shared by every part that has one (the Terminal, a
 * UART 16550 with its integrated screen): drawn from the view's kept picture ({@link TerminalScreenRaster}) when it
 * may, else directly, and what of it will move without a new frame.
 */
final class LiveTerminalScreen {

    private LiveTerminalScreen() {
    }

    /**
     * Whether the screen of {@code setting} at {@code moment} is drawn from {@code raster}: only on the canvas (an
     * export has no effects and keeps vectors) and never in black and white.
     */
    static boolean drawnFromPicture(Optional<TerminalScreenRaster> raster, TerminalMoment moment,
                                    TerminalScreenPainter.Setting setting) {
        return raster.isPresent() && moment.effects() && !setting.colors().monochrome();
    }

    /** Draws the screen of {@code setting} with {@code frame} at {@code moment}, from {@code raster} when it may. */
    static void paint(Graphics2D graphics, TerminalScreenPainter.Setting setting, Optional<TerminalFrame> frame,
                      TerminalMoment moment, Optional<TerminalScreenRaster> raster) {
        if (!drawnFromPicture(raster, moment, setting) || !raster.get().paint(graphics, setting, frame, moment)) {
            TerminalScreenPainter.paint(graphics, setting, frame, moment);
        }
    }

    /**
     * The part of {@code screen} painted at {@code moment} that will look different later without a new frame: the
     * cursor's reach and the last character's cell, or the whole screen while the old picture fades (or before the
     * first frame); empty when nothing moves.
     */
    static Optional<Rectangle2D> movingArea(Rectangle2D screen, TerminalFontSize size, Optional<TerminalFrame> frame,
                                            TerminalMoment moment) {
        if (!moment.moving()) {
            return Optional.empty();
        }
        if (moment.ghost().isPresent() || frame.isEmpty()) {
            return Optional.of(screen);
        }
        Rectangle2D area = TerminalCursorPainter.reach(TerminalScreenPainter.cellBounds(screen, size,
                frame.get().cursorRow(), frame.get().cursorColumn()));
        if (moment.lastGlowAlpha() > 0) {
            frame.get().lastWritten().ifPresent(last -> area.add(TerminalScreenPainter.cellBounds(screen, size,
                    last.row(), last.column())));
        }
        return Optional.of(area);
    }
}
