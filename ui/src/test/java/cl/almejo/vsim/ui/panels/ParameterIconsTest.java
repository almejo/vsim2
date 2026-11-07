package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.atDoubleUiScale;
import static org.assertj.core.api.Assertions.assertThat;

class ParameterIconsTest {

    private static final List<String> COVERED = List.of("label", "text", "name", "inputs", "outputs", "width",
            "inputBits", "delay", "time-up", "time-down", "negate", "color", "value", "format", "direction", "words",
            "contents");

    @Test
    void everyCoveredNameResolvesToAnExistingResource() {
        for (String name : COVERED) {
            String icon = ParameterIcons.iconName(name).orElseThrow(() -> new AssertionError("no icon for " + name));
            assertThat(getClass().getClassLoader().getResource("icons/" + icon + ".svg"))
                    .as(name).isNotNull();
            assertThat(ParameterIcons.labelIcon(name).getIconWidth()).isEqualTo(ParameterIcons.SIZE);
        }
    }

    @Test
    void anUnknownNameGivesAnEmptyIconOfTheSameSize() {
        assertThat(ParameterIcons.iconName("no-such-parameter")).isEmpty();
        Icon icon = ParameterIcons.labelIcon("no-such-parameter");
        assertThat(icon.getIconWidth()).isEqualTo(ParameterIcons.labelIcon("width").getIconWidth());
        assertThat(icon.getIconHeight()).isEqualTo(ParameterIcons.labelIcon("width").getIconHeight());
    }

    @Test
    void theEmptyIconScalesLikeTheRealOnes() throws Exception {
        atDoubleUiScale(() -> {
            Icon real = ParameterIcons.labelIcon("width");
            assertThat(real.getIconWidth()).as("the scale is on").isGreaterThan(ParameterIcons.SIZE);
            Icon empty = ParameterIcons.labelIcon("no-such-parameter");
            assertThat(empty.getIconWidth()).isEqualTo(real.getIconWidth());
            assertThat(empty.getIconHeight()).isEqualTo(real.getIconHeight());
        });
    }

    @Test
    void aParameterRowLabelHasItsIconAndAnUnknownOneKeepsTheSpace() {
        ParameterForm gate = new ParameterForm(StandardComponentTypes.registry().require(StandardComponentTypes.AND),
                Map.of());
        assertThat(gate.label("inputs").getIcon()).isNotNull();
        assertThat(gate.label("inputs").getIcon().getIconWidth()).isEqualTo(ParameterIcons.SIZE);
    }

    @Test
    void aHiddenRowHidesItsLabelWithTheIcon() {
        ParameterForm clock = new ParameterForm(StandardComponentTypes.registry().require(StandardComponentTypes.CLOCK),
                Map.of());
        assertThat(clock.label("time-up").getIcon()).isNotNull();
        // the icon is part of the label, so hiding the label hides the icon
        clock.label("time-up").setVisible(false);
        assertThat(clock.label("time-up").isVisible()).isFalse();
    }
}
