package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.UartRegisters;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.component.standard.UartTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The UART 16550's look (spec §12, mockup «uart0»): the register table, the chip, the integrated screen, capture. */
class UartPainterTest {

    /** The integrated screen's top-left corner inside the part: after the left names and the table, below the title. */
    private static final int SCREEN_LEFT = 17 * 8;
    private static final int SCREEN_TOP = 3 * 8;
    private static final Map<String, Object> SMALL = Map.of(TerminalTypes.ROWS, 4, TerminalTypes.COLUMNS, 20);
    /** After a QEMU driver's set-up, with «a» waiting and «?» sent: the mockup's table. */
    private static final UartRegisters REGISTERS = new UartRegisters('a', '?', 0x00, 0xC1, 0x07, 0x03, 0x00, 0x61,
            0xB0, 0x00, 0x01, 0x00);

    private final PainterRegistry painters = PainterRegistry.standard();
    private final UartPainter painter = (UartPainter) painters.painterFor(StandardComponentTypes.UART_16550);

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private static Map<String, Object> small(Object... keysAndValues) {
        Map<String, Object> parameters = new HashMap<>(SMALL);
        for (int index = 0; index < keysAndValues.length; index += 2) {
            parameters.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return parameters;
    }

    private static ComponentType uart(Map<String, Object> parameters) {
        return StandardComponentTypes.registry().require(StandardComponentTypes.UART_16550)
                .specializedFor(parameters);
    }

    private static VisibleState.UartState stateWith(String text) {
        return new VisibleState.UartState(REGISTERS, 1, Optional.of(TerminalPainterTest.screenWith(text)));
    }

    private BufferedImage draw(Map<String, Object> parameters, VisibleState state, boolean capturing) {
        BufferedImage image = PaintersTest.blankImage(320, 160);
        Graphics2D graphics = image.createGraphics();
        painter.paintWithCapture(graphics, uart(parameters), parameters, state, PaintersTest.SCHEME,
                Optional.of("uart0"), capturing);
        graphics.dispose();
        return image;
    }

    @Test
    void theTableNamesEveryRegisterAtItsOffset() {
        assertThat(UartPainter.rows(REGISTERS, 1)).extracting(UartPainter.RegisterRow::name).containsExactly(
                "0 RBR", "0 THR", "1 IER", "2 IIR", "2 FCR", "3 LCR", "4 MCR", "5 LSR", "6 MSR", "7 SCR",
                "0/1 DLL/DLM");
    }

    @Test
    void aStrideOfFourShowsTheByteOffsetsInHexadecimal() {
        assertThat(UartPainter.rows(REGISTERS, 4)).extracting(UartPainter.RegisterRow::name).containsExactly(
                "0 RBR", "0 THR", "4 IER", "8 IIR", "8 FCR", "C LCR", "10 MCR", "14 LSR", "18 MSR", "1C SCR",
                "0/4 DLL/DLM");
    }

    @Test
    void theValuesAreHexWithTheCharacterTheFlagsAndTheLineFormat() {
        assertThat(UartPainter.rows(REGISTERS, 1)).extracting(UartPainter.RegisterRow::value).containsExactly(
                "61 'a'", "3F '?'", "00", "C1 FIFO", "07", "03 8N1", "00", "61 DR THRE TEMT", "B0", "00", "01 00");
    }

    @Test
    void anEmptyReceiverAndDlabAndACauseAreShown() {
        UartRegisters registers = new UartRegisters(-1, -1, 0x01, 0x04, 0x00, 0x9F, 0x00, 0x61, 0xB0, 0x00, 0x0C,
                0x00);

        assertThat(UartPainter.rows(registers, 1)).extracting(UartPainter.RegisterRow::value).containsExactly(
                "--", "--", "01", "04 dato recibido", "00", "9F 8E2 DLAB", "00", "61 DR THRE TEMT", "B0", "00",
                "0C 00");
    }

    @Test
    void theChipSaysTheChipAndItsFifo() {
        assertThat(UartPainter.chip(Map.of(UartTypes.FIFO, "16"))).isEqualTo("16550 · FIFO 16");
        assertThat(UartPainter.chip(Map.of(UartTypes.FIFO, "1"))).isEqualTo("16450 · sin FIFO");
    }

    @Test
    void theIntegratedScreenShowsTheTextSent() {
        BufferedImage empty = draw(SMALL, stateWith(""), false);
        BufferedImage written = draw(SMALL, stateWith("HHHH"), false);

        assertThat(new Color(empty.getRGB(SCREEN_LEFT + 20, SCREEN_TOP + 20))).as("the screen's background")
                .isEqualTo(new Color(0x101010));
        int differences = 0;
        for (int x = SCREEN_LEFT; x < SCREEN_LEFT + 4 * 8; x++) {
            for (int y = SCREEN_TOP; y < SCREEN_TOP + 16; y++) {
                if (empty.getRGB(x, y) != written.getRGB(x, y)) {
                    differences++;
                }
            }
        }
        assertThat(differences).isGreaterThan(20);
    }

    @Test
    void withPinsThereIsNoScreen() {
        BufferedImage image = draw(small(UartTypes.SCREEN, UartTypes.SCREEN_PINS),
                new VisibleState.UartState(REGISTERS, 1, Optional.empty()), false);

        assertThat(new Color(image.getRGB(SCREEN_LEFT + 20, SCREEN_TOP + 20))).isNotEqualTo(new Color(0x101010));
    }

    @Test
    void capturingTheKeyboardChangesTheTitleBar() {
        BufferedImage idle = draw(SMALL, stateWith(""), false);
        BufferedImage capturing = draw(SMALL, stateWith(""), true);

        assertThat(PainterTesting.differentPixels(idle, capturing)).isGreaterThan(50);
    }

    @Test
    void itDrawsInsideItsBoundsWhenTurned() {
        Map<String, Object> parameters = small(UartTypes.SCREEN, UartTypes.SCREEN_PINS);
        ComponentType type = uart(parameters);
        for (Rotation rotation : Rotation.values()) {
            Component component = type.newComponent(new ElementId("c1"), new GridPoint(2, 2), rotation, parameters,
                    "");
            BufferedImage image = PaintersTest.blankImage(300, 300);
            Graphics2D graphics = image.createGraphics();
            ComponentDrawing.paint(graphics, type, component,
                    new VisibleState.UartState(REGISTERS, 1, Optional.empty()), PaintersTest.SCHEME, painters);
            graphics.dispose();
            Rectangle2D allowed = ComponentDrawing.documentBounds(type, component);
            allowed.setRect(allowed.getX() - 8, allowed.getY() - 8, allowed.getWidth() + 16, allowed.getHeight() + 16);
            int background = PaintersTest.SCHEME.background().getRGB();
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    if (image.getRGB(x, y) != background) {
                        assertThat(allowed.contains(x, y)).as(rotation + " pixel " + x + "," + y).isTrue();
                    }
                }
            }
        }
    }
}
