package cl.almejo.vsim.ui.theme;

import org.junit.jupiter.api.Test;

import java.awt.Component;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentedSwitchTest {
    @Test
    void aSwitchWithNaturalWidthsGivesEachSegmentItsOwnWidth() {
        SegmentedSwitch.Segment shortOne = new SegmentedSwitch.Segment("Solo esta pieza (U3)");
        SegmentedSwitch.Segment longOne = new SegmentedSwitch.Segment("Todas las piezas (3 en este circuito)");
        SegmentedSwitch switchBar = SegmentedSwitch.withNaturalWidths(shortOne, longOne);
        switchBar.setSize(switchBar.getPreferredSize());
        switchBar.doLayout();

        assertThat(shortOne.getWidth()).isEqualTo(shortOne.getPreferredSize().width);
        assertThat(longOne.getWidth()).isEqualTo(longOne.getPreferredSize().width);
        assertThat(longOne.getWidth()).isGreaterThan(shortOne.getWidth());
        assertThat(switchBar.getComponents()).extracting(Component::getHeight).doesNotContain(0);
    }

    @Test
    void theDefaultSwitchKeepsEqualWidths() {
        SegmentedSwitch.Segment shortOne = new SegmentedSwitch.Segment("De corrido");
        SegmentedSwitch.Segment longOne = new SegmentedSwitch.Segment("Paso a paso, con pausa");
        SegmentedSwitch switchBar = new SegmentedSwitch(shortOne, longOne);
        switchBar.setSize(switchBar.getPreferredSize());
        switchBar.doLayout();

        assertThat(shortOne.getWidth()).isEqualTo(longOne.getWidth());
    }
}
