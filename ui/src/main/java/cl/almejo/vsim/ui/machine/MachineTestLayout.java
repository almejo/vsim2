package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.waveform.WaveformPainter;
import com.formdev.flatlaf.util.UIScale;

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.function.Function;

/**
 * The sizes of the test table's pieces, in pixels (fix round 2 of hito 4): every one from the interface scale
 * ({@link UIScale}) and the font's own metrics, never a literal pixel count, so the table grows with the interface
 * (100 % to 200 %) and with a bigger letter. The wave strip's rows are {@link WaveformPainter#rowHeight()}, which
 * follows the interface size too; when the table's letter asks for a taller row, its waves are drawn scaled by
 * {@link #waveScale()} to it.
 *
 * @param columnWidth  one column (one edge): wide enough for «64» and its edge arrow
 * @param ghostWidth   the column after the last edge, with the room «→ S…» needs (at most four columns)
 * @param rowHeight    a row of a wave, a value or a verdict
 * @param groupHeight  the «Entradas» / «Salidas» bars and a bus's header
 * @param headerHeight the edge numbers' row
 * @param namesWidth   the names column
 * @param padding      the air at the sides of a text
 */
record MachineTestLayout(int columnWidth, int ghostWidth, int rowHeight, int groupHeight, int headerHeight,
                         int namesWidth, int padding) {

    /** The ghost column's most: its name is cut with «…» beyond it, and the whole name is in its tooltip. */
    static final int GHOST_MAXIMUM_COLUMNS = 4;
    /** The smallest sizes at 100 % (the wave strip's own and a little more); the font may ask for more. */
    private static final int MINIMUM_COLUMN = 44;
    private static final int MINIMUM_GROUP = 18;
    private static final int MINIMUM_HEADER = 22;
    private static final int NAMES = 150;
    private static final int PADDING = 6;
    private static final int ROW_AIR = 4;
    /** The group bars and bus headers' letter, relative to the table's own. */
    private static final float GROUP_FONT_SHARE = 0.85f;

    /** The sizes before a font is known: the interface scale alone. */
    static MachineTestLayout basic() {
        int column = UIScale.scale(MINIMUM_COLUMN);
        return new MachineTestLayout(column, column, WaveformPainter.rowHeight(),
                UIScale.scale(MINIMUM_GROUP), UIScale.scale(MINIMUM_HEADER), UIScale.scale(NAMES),
                UIScale.scale(PADDING));
    }

    /**
     * The sizes for the table's {@code font} (metrics from {@code metricsOf}) and the states of {@code machine}: each
     * is the interface-scaled minimum or what the font needs, the larger.
     */
    static MachineTestLayout of(Font font, Function<Font, FontMetrics> metricsOf, StateMachine machine) {
        FontMetrics metrics = metricsOf.apply(font);
        FontMetrics group = metricsOf.apply(groupFont(font));
        int padding = UIScale.scale(PADDING);
        int air = UIScale.scale(ROW_AIR);
        int column = Math.max(UIScale.scale(MINIMUM_COLUMN),
                metrics.stringWidth("64") + metrics.stringWidth("↓") + 3 * padding);
        int ghost = column;
        for (MachineState state : machine.states()) {
            ghost = Math.max(ghost, metrics.stringWidth(Messages.format("machine.test.ghost", state.name()))
                    + 2 * air);
        }
        return new MachineTestLayout(column, Math.min(ghost, GHOST_MAXIMUM_COLUMNS * column),
                Math.max(WaveformPainter.rowHeight(), metrics.getHeight() + 2 * air),
                Math.max(UIScale.scale(MINIMUM_GROUP), group.getHeight() + air),
                Math.max(UIScale.scale(MINIMUM_HEADER), metrics.getHeight() + 2 * air),
                UIScale.scale(NAMES), padding);
    }

    /** The letter of the group bars and bus headers: a little smaller than the table's, bold for the bars. */
    static Font groupFont(Font font) {
        return font.deriveFont(font.getSize2D() * GROUP_FONT_SHARE);
    }

    /** How much the wave strip's drawing (made for {@link WaveformPainter#rowHeight()}) is scaled to fit a row. */
    double waveScale() {
        return rowHeight / (double) WaveformPainter.rowHeight();
    }
}
