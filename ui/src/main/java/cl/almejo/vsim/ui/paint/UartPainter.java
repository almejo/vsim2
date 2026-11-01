package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.UartRegisters;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.component.standard.UartTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.terminal.GlyphCache;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import cl.almejo.vsim.ui.i18n.Messages;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * «UART 16550» (spec §12, mockup «uart0»): a title bar with the part's label (its name without one) and a chip with
 * the chip and its FIFO («16550 · FIFO 16»), the register table with each register's offset (as «Separación de
 * registros» says), value and meaning (the character of RBR and THR, LSR's flags, LCR's line format, IIR's cause),
 * and, with the integrated screen, that screen in the Terminal's look, still. While the keyboard is captured for it
 * ({@link #paintWithCapture}) the border and the title bar take the selection colour and the title says how to stop
 * typing, like a Teclado. Colours come from the scheme at paint time.
 */
public final class UartPainter implements ComponentPainter {

    /** One line of the register table: the offsets and names, and the value with its meaning. */
    record RegisterRow(String name, String value) {
    }

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final double CORNER = 4;
    private static final double TITLE_HEIGHT = UartTypes.TITLE_BAR_UNITS * UNIT;
    private static final double TABLE_LEFT = UartTypes.LEFT_PANEL_UNITS * UNIT;
    private static final double TABLE_WIDTH = UartTypes.REGISTER_PANEL_UNITS * UNIT;
    /** Where the values start, right of the longest name («0/4 DLL/DLM»). */
    private static final double VALUE_OFFSET = 40;
    private static final double FIRST_ROW_MIDDLE = TITLE_HEIGHT + 6;
    private static final double ROW_STEP = 7.5;
    private static final float TABLE_TEXT_SIZE = 5.5f;
    private static final double TITLE_PADDING = 6;
    private static final double TITLE_GAP = 8;
    private static final float TITLE_TEXT_SIZE = 8f;
    private static final int CHIP_ALPHA = 170;
    private static final int NAME_ALPHA = 170;
    private static final int RULE_ALPHA = 40;
    private static final float PIN_NAME_SIZE = 5f;
    private static final double PIN_NAME_INSET = 4.5;
    private static final BasicStroke DIVIDER = new BasicStroke(1f);
    private static final float CAPTURE_BORDER_WIDTH = 2f;
    private static final float HALO_WIDTH = 6f;
    private static final int HALO_ALPHA = 64;

    private static final int NONE = -1;
    private static final int BYTE_MASK = 0xFF;
    private static final int FIRST_PRINTABLE = 0x20;
    private static final int LAST_PRINTABLE = 0x7E;
    private static final int DATA_READY = 0x01;
    private static final int OVERRUN = 0x02;
    private static final int TRANSMITTER_HOLDING_EMPTY = 0x20;
    private static final int TRANSMITTER_EMPTY = 0x40;
    private static final int FIFO_BITS = 0xC0;
    private static final int CAUSE_MASK = 0x0F;
    private static final int CAUSE_LINE_STATUS = 0x06;
    private static final int CAUSE_RECEIVED = 0x04;
    private static final int CAUSE_TRANSMITTER_EMPTY = 0x02;
    private static final int WORD_LENGTH_MASK = 0x03;
    private static final int SHORTEST_WORD = 5;
    private static final int TWO_STOP_BITS = 0x04;
    private static final int PARITY_ENABLE = 0x08;
    private static final int EVEN_PARITY = 0x10;
    private static final int DIVISOR_LATCH_ACCESS = 0x80;
    /** The registers in the table's order, with their address (0–7); the divisor latch is the last row. */
    private static final List<String> REGISTERS = List.of("rbr", "thr", "ier", "iir", "fcr", "lcr", "mcr", "lsr",
            "msr", "scr");
    private static final List<Integer> ADDRESSES = List.of(0, 0, 1, 2, 2, 3, 4, 5, 6, 7);
    private static final int DIVISOR_HIGH_ADDRESS = 1;
    private static final String REGISTER_KEY_PREFIX = "uart.register.";

    /**
     * How the canvas shows a UART besides its state: {@code label} in its title bar (empty: the part's name), and
     * {@code capturing} when the keyboard is captured for its integrated keyboard.
     */
    public record Look(Optional<String> label, boolean capturing) {

        public Look {
            Objects.requireNonNull(label, "label");
        }
    }

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        paintWithCapture(graphics, type, parameters, state, scheme, Optional.empty(), false);
    }

    /**
     * Draws the UART with {@code label} in its title bar (empty: the part's name), its integrated screen still and
     * without effects, as an export shows it; {@code capturing} when the keyboard is captured for its integrated
     * keyboard.
     */
    public void paintWithCapture(Graphics2D graphics, ComponentType baseOrPlacedType, Map<String, Object> parameters,
                                 VisibleState state, ColorScheme scheme, Optional<String> label, boolean capturing) {
        paintLive(graphics, baseOrPlacedType, parameters, state, scheme, new Look(label, capturing),
                TerminalMoment.EXPORTED, Optional.empty());
    }

    /**
     * {@link #paintWithCapture} with the integrated screen at {@code moment} of the view's animation (blink, Automan's
     * star, last character's glow, persistence, screen effect), as a Terminal's, kept as a picture in {@code raster}.
     */
    public void paintLive(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                          ColorScheme scheme, Look look, TerminalMoment moment, TerminalScreenRaster raster) {
        paintLive(graphics, type, parameters, state, scheme, look, moment, Optional.of(raster));
    }

    /**
     * The part of a UART painted at {@code moment} that will look different later without a new frame, in the part's
     * coordinates: what moves on its integrated screen (see {@link TerminalPainter#movingArea}); empty with TX and RX
     * pins or when nothing moves.
     */
    public Optional<Rectangle2D> movingArea(ComponentType baseOrPlacedType, Map<String, Object> parameters,
                                            VisibleState state, TerminalMoment moment) {
        ComponentType type = baseOrPlacedType.specializedFor(parameters);
        Map<String, Object> resolved = type.resolveParameters(parameters);
        if (!UartTypes.isIntegrated(resolved)) {
            return Optional.empty();
        }
        return LiveTerminalScreen.movingArea(screen(type, resolved), TerminalTypes.fontSize(resolved),
                frameOf(state), moment);
    }

    private void paintLive(Graphics2D graphics, ComponentType baseOrPlacedType, Map<String, Object> parameters,
                           VisibleState state, ColorScheme scheme, Look look, TerminalMoment moment,
                           Optional<TerminalScreenRaster> raster) {
        Optional<String> label = look.label();
        boolean capturing = look.capturing();
        // the screen and «Pantalla» change the size: the palette and the ghost pass the type of the defaults
        ComponentType type = baseOrPlacedType.specializedFor(parameters);
        Map<String, Object> resolved = type.resolveParameters(parameters);
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        Shape outline = new RoundRectangle2D.Double(0, 0, width, height, CORNER, CORNER);
        Color accent = scheme.selection();
        if (capturing) {
            graphics.setColor(PainterSupport.withAlpha(accent, HALO_ALPHA));
            graphics.setStroke(new BasicStroke(HALO_WIDTH));
            graphics.draw(outline);
        }
        PainterSupport.body(graphics, outline, scheme);
        paintTitleBar(graphics, outline, width, resolved, label.orElseGet(() -> Messages.componentName(type.id())),
                scheme, capturing);
        if (capturing) {
            graphics.setColor(accent);
            graphics.setStroke(new BasicStroke(CAPTURE_BORDER_WIDTH));
            graphics.draw(outline);
        }
        Optional<VisibleState.UartState> uart = state instanceof VisibleState.UartState known ? Optional.of(known)
                : Optional.empty();
        paintTable(graphics, uart.map(VisibleState.UartState::registers).orElse(resetRegisters()),
                UartTypes.registerStride(resolved), scheme);
        if (UartTypes.isIntegrated(resolved)) {
            paintScreen(graphics, type, resolved, frameOf(state), scheme, moment, raster);
        }
        PainterSupport.pinDots(graphics, type, scheme);
        paintPinNames(graphics, type, scheme);
    }

    /** The table's rows for {@code registers}, with offsets {@code stride} bytes apart. */
    static List<RegisterRow> rows(UartRegisters registers, int stride) {
        List<String> values = List.of(byteWithCharacter(registers.receiver()),
                byteWithCharacter(registers.transmitter()), hex(registers.interruptEnable()),
                interruptIdentification(registers.interruptIdentification()), hex(registers.fifoControl()),
                lineControl(registers.lineControl()), hex(registers.modemControl()),
                lineStatus(registers.lineStatus()), hex(registers.modemStatus()), hex(registers.scratch()));
        List<RegisterRow> rows = new ArrayList<>();
        for (int index = 0; index < REGISTERS.size(); index++) {
            rows.add(new RegisterRow(offset(ADDRESSES.get(index), stride) + " "
                    + Messages.text(REGISTER_KEY_PREFIX + REGISTERS.get(index)), values.get(index)));
        }
        rows.add(new RegisterRow(offset(0, stride) + "/" + offset(DIVISOR_HIGH_ADDRESS, stride) + " "
                + Messages.text("uart.register.divisor"),
                hex(registers.divisorLow()) + " " + hex(registers.divisorHigh())));
        return rows;
    }

    /** The chip's text: «16550 · FIFO 16», or «16450 · sin FIFO» with a one-byte FIFO. */
    static String chip(Map<String, Object> resolved) {
        int depth = UartTypes.fifoDepth(resolved);
        return depth > 1 ? Messages.format("uart.painter.chip", depth) : Messages.text("uart.painter.chipNoFifo");
    }

    /** What a UART shows without a simulation: the registers after power-on. */
    private static UartRegisters resetRegisters() {
        return new UartRegisters(NONE, NONE, 0, 1, 0, 0, 0, TRANSMITTER_HOLDING_EMPTY | TRANSMITTER_EMPTY, 0, 0, 0, 0);
    }

    private static String offset(int address, int stride) {
        return Integer.toHexString(address * stride).toUpperCase(Locale.ROOT);
    }

    private static String hex(int value) {
        return String.format(Locale.ROOT, "%02X", value & BYTE_MASK);
    }

    /** «61 'a'», only the hexadecimal for a byte that is not printable ASCII, «--» for none. */
    private static String byteWithCharacter(int value) {
        if (value == NONE) {
            return Messages.text("uart.painter.noByte");
        }
        if (value >= FIRST_PRINTABLE && value <= LAST_PRINTABLE) {
            return hex(value) + " '" + (char) value + "'";
        }
        return hex(value);
    }

    /** «C1 FIFO», «04 dato recibido»: the cause when there is one, else whether the FIFOs are on. */
    private static String interruptIdentification(int value) {
        String meaning = switch (value & CAUSE_MASK) {
            case CAUSE_LINE_STATUS -> Messages.text("uart.cause.lineStatus");
            case CAUSE_RECEIVED -> Messages.text("uart.cause.received");
            case CAUSE_TRANSMITTER_EMPTY -> Messages.text("uart.cause.transmitterEmpty");
            default -> (value & FIFO_BITS) == FIFO_BITS ? Messages.text("uart.flag.fifo") : "";
        };
        return meaning.isEmpty() ? hex(value) : hex(value) + " " + meaning;
    }

    /** «03 8N1», «9F 8E2 DLAB»: data bits, parity and stop bits, and whether DLAB is on. */
    private static String lineControl(int value) {
        String parity = (value & PARITY_ENABLE) == 0 ? Messages.text("uart.parity.none")
                : (value & EVEN_PARITY) != 0 ? Messages.text("uart.parity.even") : Messages.text("uart.parity.odd");
        String format = Messages.format("uart.painter.lineFormat", SHORTEST_WORD + (value & WORD_LENGTH_MASK),
                parity, (value & TWO_STOP_BITS) != 0 ? 2 : 1);
        String text = hex(value) + " " + format;
        return (value & DIVISOR_LATCH_ACCESS) != 0 ? text + " " + Messages.text("uart.flag.dlab") : text;
    }

    /** «61 DR THRE TEMT»: the value and the names of its flags that are on. */
    private static String lineStatus(int value) {
        StringBuilder text = new StringBuilder(hex(value));
        appendFlag(text, value, DATA_READY, "uart.flag.dataReady");
        appendFlag(text, value, OVERRUN, "uart.flag.overrun");
        appendFlag(text, value, TRANSMITTER_HOLDING_EMPTY, "uart.flag.holdingEmpty");
        appendFlag(text, value, TRANSMITTER_EMPTY, "uart.flag.transmitterEmpty");
        return text.toString();
    }

    private static void appendFlag(StringBuilder text, int value, int flag, String key) {
        if ((value & flag) != 0) {
            text.append(' ').append(Messages.text(key));
        }
    }

    private static void paintTable(Graphics2D graphics, UartRegisters registers, int stride, ColorScheme scheme) {
        if (PainterSupport.turnedSideways(graphics)) {
            // upright text cannot fit the table's rows turned into narrow vertical strips
            return;
        }
        Font font = TerminalFonts.font(TerminalFontFamily.PLEX_MONO, false);
        boolean upsideDown = Math.abs(PainterSupport.quarterTurns(graphics)) % 4 == 2;
        List<RegisterRow> rows = rows(registers, stride);
        for (int index = 0; index < rows.size(); index++) {
            double middle = FIRST_ROW_MIDDLE + index * ROW_STEP;
            graphics.setColor(PainterSupport.withAlpha(scheme.componentStroke(), RULE_ALPHA));
            graphics.setStroke(DIVIDER);
            graphics.draw(new Line2D.Double(TABLE_LEFT, middle + ROW_STEP / 2, TABLE_LEFT + TABLE_WIDTH,
                    middle + ROW_STEP / 2));
            double nameX = upsideDown ? TABLE_LEFT + VALUE_OFFSET : TABLE_LEFT;
            double valueX = upsideDown ? TABLE_LEFT : TABLE_LEFT + VALUE_OFFSET;
            double alignment = upsideDown ? PainterSupport.ALIGN_RIGHT : PainterSupport.ALIGN_LEFT;
            PainterSupport.uprightText(graphics, rows.get(index).name(), nameX, middle, TABLE_TEXT_SIZE,
                    PainterSupport.withAlpha(scheme.text(), NAME_ALPHA), alignment, font);
            PainterSupport.uprightText(graphics, rows.get(index).value(), valueX, middle, TABLE_TEXT_SIZE,
                    scheme.text(), alignment, font);
        }
    }

    private static void paintScreen(Graphics2D graphics, ComponentType type, Map<String, Object> resolved,
                                    Optional<TerminalFrame> frame, ColorScheme scheme, TerminalMoment moment,
                                    Optional<TerminalScreenRaster> raster) {
        TerminalLook look = TerminalLook.of(resolved);
        TerminalScreenPainter.Setting setting = new TerminalScreenPainter.Setting(screen(type, resolved),
                GlyphCache.EMPTY, (Integer) resolved.get(TerminalTypes.ROWS),
                (Integer) resolved.get(TerminalTypes.COLUMNS), TerminalTypes.fontSize(resolved), look,
                TerminalPainter.colorsOf(look, scheme));
        LiveTerminalScreen.paint(graphics, setting, frame, moment, raster);
    }

    /** The integrated screen, in the part's coordinates: right of the register table, one margin below the bar. */
    private static Rectangle2D screen(ComponentType type, Map<String, Object> resolved) {
        int rows = (Integer) resolved.get(TerminalTypes.ROWS);
        double left = UartTypes.SCREEN_LEFT_UNITS * UNIT;
        double right = (type.size().width() - UartTypes.RIGHT_PANEL_UNITS) * UNIT;
        double screenHeight = Math.ceil(rows * TerminalTypes.fontSize(resolved).cellHeight() / UNIT) * UNIT;
        return new Rectangle2D.Double(left, TITLE_HEIGHT + UartTypes.MARGIN_UNITS * UNIT, right - left,
                screenHeight);
    }

    private static Optional<TerminalFrame> frameOf(VisibleState state) {
        return VisibleState.terminalScreen(state).map(VisibleState.TerminalState::frame);
    }

    private static void paintTitleBar(Graphics2D graphics, Shape outline, double width, Map<String, Object> resolved,
                                      String name, ColorScheme scheme, boolean capturing) {
        Color accent = scheme.selection();
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(outline);
            clipped.setColor(capturing ? accent : scheme.componentShade());
            clipped.fill(new Rectangle2D.Double(0, 0, width, TITLE_HEIGHT));
            clipped.setColor(scheme.componentStroke());
            clipped.setStroke(DIVIDER);
            clipped.draw(new Line2D.Double(0, TITLE_HEIGHT, width, TITLE_HEIGHT));
        } finally {
            clipped.dispose();
        }
        if (PainterSupport.turnedSideways(graphics)) {
            // upright text cannot fit the title bar turned into a narrow vertical strip
            return;
        }
        double middle = TITLE_HEIGHT / 2;
        boolean upsideDown = Math.abs(PainterSupport.quarterTurns(graphics)) % 4 == 2;
        double start = upsideDown ? PainterSupport.ALIGN_RIGHT : PainterSupport.ALIGN_LEFT;
        if (capturing) {
            PainterSupport.uprightText(graphics, Messages.text("keyboard.painter.capturing"), TITLE_PADDING, middle,
                    TITLE_TEXT_SIZE, KeyboardPainter.textOn(accent), start);
            return;
        }
        String chip = chip(resolved);
        FontMetrics metrics = graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(TITLE_TEXT_SIZE));
        double nameRoom = width - 2 * TITLE_PADDING - metrics.stringWidth(chip) - TITLE_GAP;
        PainterSupport.uprightText(graphics, PainterSupport.ellipsized(name, metrics, nameRoom), TITLE_PADDING, middle,
                TITLE_TEXT_SIZE, scheme.text(), start);
        PainterSupport.uprightText(graphics, chip, width - TITLE_PADDING, middle, TITLE_TEXT_SIZE,
                PainterSupport.withAlpha(scheme.text(), CHIP_ALPHA),
                upsideDown ? PainterSupport.ALIGN_LEFT : PainterSupport.ALIGN_RIGHT);
    }

    /** Each pin's name inside the part, next to its stub. */
    private static void paintPinNames(Graphics2D graphics, ComponentType type, ColorScheme scheme) {
        int width = type.size().width();
        int height = type.size().height();
        for (PinSpec pin : type.pins()) {
            double pinX = pin.position().x() * UNIT;
            double pinY = pin.position().y() * UNIT;
            if (pin.position().x() == 0) {
                PainterSupport.uprightText(graphics, pin.name(), pinX + PIN_NAME_INSET, pinY, PIN_NAME_SIZE,
                        scheme.text(), PainterSupport.ALIGN_LEFT);
            } else if (pin.position().x() == width && pin.position().y() != height) {
                PainterSupport.uprightText(graphics, pin.name(), pinX - PIN_NAME_INSET, pinY, PIN_NAME_SIZE,
                        scheme.text(), PainterSupport.ALIGN_RIGHT);
            } else {
                PainterSupport.uprightText(graphics, pin.name(), pinX, pinY - PIN_NAME_INSET, PIN_NAME_SIZE,
                        scheme.text(), PainterSupport.ALIGN_CENTER);
            }
        }
    }
}
