package cl.almejo.vsim.ui.window.tabs;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroupColorsTest {

    @Test
    void sixDocumentsGetSixDifferentColorsThenRepeat() {
        List<Object> documents = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            documents.add(new Object());
        }
        List<Color> colors = documents.stream()
                .map(document -> GroupColors.colorFor(GroupColors.ordinalOf(document), false)).toList();
        assertThat(new HashSet<>(colors.subList(0, 6))).hasSize(6);
        assertThat(colors.get(6)).isEqualTo(colors.get(0));
    }

    @Test
    void aDocumentKeepsItsOrdinal() {
        Object document = new Object();
        int first = GroupColors.ordinalOf(document);
        GroupColors.ordinalOf(new Object());
        assertThat(GroupColors.ordinalOf(document)).isEqualTo(first);
    }

    @Test
    void theDarkVariantDiffersAndIsLighter() {
        for (int ordinal = 0; ordinal < 6; ordinal++) {
            Color light = GroupColors.colorFor(ordinal, false);
            Color dark = GroupColors.colorFor(ordinal, true);
            assertThat(brightness(dark)).as("ordinal " + ordinal).isGreaterThan(brightness(light));
        }
    }

    private static int brightness(Color color) {
        return color.getRed() + color.getGreen() + color.getBlue();
    }
}
