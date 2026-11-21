package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WaveformScalingTest {

    private Font originalFont;
    private Color originalFocusColor;
    private OpenDocument openDocument;
    private WaveformPanel panel;

    @BeforeEach
    void create() {
        originalFont = UIManager.getFont("Label.font");
        originalFocusColor = UIManager.getColor("Component.focusColor");
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        panel = new WaveformPanel(openDocument, new ProbeActions(openDocument), () -> ColorScheme.LIGHT,
                (parent, title, message, initialText) -> Optional.empty(), probeId -> { }, () -> { });
    }

    @AfterEach
    void close() {
        UIManager.put("Label.font", originalFont);
        UIManager.put("Component.focusColor", originalFocusColor);
        panel.dispose();
        openDocument.close();
    }

    private static void interfaceFont(float size) {
        UIManager.put("Label.font", new Font(Font.SANS_SERIF, Font.PLAIN, 12).deriveFont(size));
    }

    private BufferedImage paintPlot() {
        panel.plot().setSize(400, 120);
        BufferedImage image = new BufferedImage(400, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.plot().paint(graphics);
        graphics.dispose();
        return image;
    }

    @Test
    void rowHeightRulerHeightAndNameColumnGrowWithTheInterfaceSize() {
        interfaceFont(13);
        int rowAtNormal = WaveformPainter.rowHeight();
        int rulerAtNormal = TimeRuler.rulerHeight();
        int columnAtNormal = ChannelNameColumn.columnWidth();
        interfaceFont(26);
        assertThat(WaveformPainter.rowHeight()).isGreaterThanOrEqualTo(rowAtNormal * 2 - 1);
        assertThat(TimeRuler.rulerHeight()).isGreaterThanOrEqualTo(rulerAtNormal * 2 - 1);
        assertThat(ChannelNameColumn.columnWidth()).isGreaterThanOrEqualTo(columnAtNormal * 2 - 1);
        assertThat(panel.plot().getScrollableUnitIncrement(null, 1, 1)).isEqualTo(WaveformPainter.rowHeight());
    }

    @Test
    void theBusTextFollowsThePanelFont() {
        interfaceFont(13);
        float normal = WaveformPainter.busTextSize();
        interfaceFont(26);
        assertThat(WaveformPainter.busTextSize()).isGreaterThan(normal * 1.9f);
    }

    @Test
    void theEmptyPlotTellsHowToAddASignal() {
        assertThat(panel.plot().emptyHint()).isEqualTo(Messages.format("waveform.empty",
                Messages.text("waveform.menu.show")));
        BufferedImage image = paintPlot();
        int background = ColorScheme.LIGHT.diagramBackground().getRGB() & 0xFFFFFF;
        boolean inked = false;
        for (int x = 0; x < 400 && !inked; x++) {
            for (int y = 30; y < 90; y++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != background) {
                    inked = true;
                    break;
                }
            }
        }
        assertThat(inked).isTrue();
    }

    @Test
    void theFocusRingShowsOnlyWhileThePlotHasTheFocus() {
        Color ring = new Color(0x12, 0x34, 0x56);
        UIManager.put("Component.focusColor", ring);
        assertThat(paintPlot().getRGB(0, 60) & 0xFFFFFF).isNotEqualTo(ring.getRGB() & 0xFFFFFF);
        for (FocusListener listener : panel.plot().getFocusListeners()) {
            listener.focusGained(new FocusEvent(panel.plot(), FocusEvent.FOCUS_GAINED));
        }
        assertThat(paintPlot().getRGB(0, 60) & 0xFFFFFF).isEqualTo(ring.getRGB() & 0xFFFFFF);
        for (FocusListener listener : panel.plot().getFocusListeners()) {
            listener.focusLost(new FocusEvent(panel.plot(), FocusEvent.FOCUS_LOST));
        }
        assertThat(paintPlot().getRGB(0, 60) & 0xFFFFFF).isNotEqualTo(ring.getRGB() & 0xFFFFFF);
    }
}
