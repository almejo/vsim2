package cl.almejo.vsim.core.component.standard;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.GROUPS;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;
import static org.assertj.core.api.Assertions.assertThat;

/** A bus part's groups when its «Ancho» changes, and the text they are stored as: the same for Propiedades and the AI. */
class BusGroupsParametersTest {

    private static Map<String, Object> bus(Object width, String groups) {
        return Map.of(WIDTH, width, GROUPS, groups);
    }

    @Test
    void aNewWidthKeepsEqualPinsEqualWithTheSameBitsPerPin() {
        assertThat(StandardComponentTypes.regroupedForWidth(bus(8, "0-3|4-7"), bus(16, "0-3|4-7")))
                .containsEntry(GROUPS, "0-3|4-7|8-11|12-15").containsEntry(WIDTH, 16);
    }

    @Test
    void aNarrowerWidthDropsTheBitsOfCustomGroupsBeyondIt() {
        assertThat(StandardComponentTypes.regroupedForWidth(bus(32, "7-11,25-31|15-19"), bus(16, "7-11,25-31|15-19")))
                .containsEntry(GROUPS, "7-11|15");
    }

    @Test
    void aWidthTypedAsTextIsReadLikeANumber() {
        assertThat(StandardComponentTypes.regroupedForWidth(bus(8, "0-3|4-7"), bus("4", "0-3|4-7")))
                .containsEntry(GROUPS, "0-3");
    }

    @Test
    void theSameWidthOrAnUnreadableOneLeavesTheGroupsAlone() {
        assertThat(StandardComponentTypes.regroupedForWidth(bus(8, "0-3|4-7"), bus(8, "0-3|4-7")))
                .containsEntry(GROUPS, "0-3|4-7");
        assertThat(StandardComponentTypes.regroupedForWidth(bus(8, "0-3|4-7"), bus("x", "0-3|4-7")))
                .containsEntry(GROUPS, "0-3|4-7");
        assertThat(StandardComponentTypes.regroupedForWidth(bus(8, "0-3|4-7"), bus(40, "0-3|4-7")))
                .containsEntry(GROUPS, "0-3|4-7");
    }

    @Test
    void groupsOfOneBitPerPinAreStoredAsTheEmptyText() {
        assertThat(StandardComponentTypes.withCanonicalGroups(bus(4, "0|1|2|3"))).containsEntry(GROUPS, "");
        assertThat(StandardComponentTypes.withCanonicalGroups(bus(8, "0|1|2|3"))).containsEntry(GROUPS, "0|1|2|3");
    }

    @Test
    void invalidGroupsAreLeftForTheCheckToReport() {
        assertThat(StandardComponentTypes.withCanonicalGroups(bus(4, "0-7"))).containsEntry(GROUPS, "0-7");
    }
}
