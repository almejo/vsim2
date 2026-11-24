package cl.almejo.vsim.ui.panels;

import net.miginfocom.swing.MigLayout;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import java.awt.Dimension;
import java.awt.Rectangle;

import static org.assertj.core.api.Assertions.assertThat;

/** The inspectors' panel that scrolls only up and down («Propiedades» and the machine's rows share it). */
class VerticalScrollPanelTest {

    @Test
    void itsScrollPaneScrollsOnlyVerticallyWithTheInspectorsStep() {
        VerticalScrollPanel panel = new VerticalScrollPanel(new MigLayout(), false);

        JScrollPane scroll = panel.inScrollPane();

        assertThat(scroll.getViewport().getView()).isSameAs(panel);
        assertThat(scroll.getVerticalScrollBarPolicy()).isEqualTo(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        assertThat(scroll.getHorizontalScrollBarPolicy()).isEqualTo(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        assertThat(scroll.getBorder()).isNull();
        assertThat(scroll.getVerticalScrollBar().getUnitIncrement()).isEqualTo(16);
    }

    @Test
    void itTakesThePaneWidthAndStepsByTheUnitOrAPageLessOneUnit() {
        VerticalScrollPanel panel = new VerticalScrollPanel(new MigLayout(), false);
        Rectangle visible = new Rectangle(0, 0, 200, 100);

        assertThat(panel.getScrollableTracksViewportWidth()).isTrue();
        assertThat(panel.getScrollableUnitIncrement(visible, SwingConstants.VERTICAL, 1)).isEqualTo(16);
        assertThat(panel.getScrollableBlockIncrement(visible, SwingConstants.VERTICAL, 1)).isEqualTo(84);
        assertThat(panel.getScrollableBlockIncrement(new Rectangle(0, 0, 200, 10), SwingConstants.VERTICAL, 1))
                .isEqualTo(16);
    }

    @Test
    void aShortPanelFillsThePaneOnlyWhenAskedTo() {
        VerticalScrollPanel filling = new VerticalScrollPanel(new MigLayout(), true);
        filling.add(new JLabel("corto"));
        JScrollPane fillingScroll = filling.inScrollPane();
        fillingScroll.getViewport().setSize(new Dimension(200, 400));
        VerticalScrollPanel keeping = new VerticalScrollPanel(new MigLayout(), false);
        keeping.add(new JLabel("corto"));
        JScrollPane keepingScroll = keeping.inScrollPane();
        keepingScroll.getViewport().setSize(new Dimension(200, 400));

        assertThat(filling.getScrollableTracksViewportHeight()).isTrue();
        assertThat(keeping.getScrollableTracksViewportHeight()).isFalse();
    }
}
