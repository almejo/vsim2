package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.Netlist;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StepHighlightTest {

    private static final long MILLISECOND = TimeUnit.MILLISECONDS.toNanos(1);
    private static final ElementId FIRST = new ElementId("w1");
    private static final ElementId SECOND = new ElementId("w2");
    private static final ElementId OTHER = new ElementId("w3");

    private static Net net(String id, ElementId... wires) {
        return new Net(new NetId(id), Set.of(), Set.of(wires), Set.of(), 1, false);
    }

    private final Netlist netlist = Netlist.of(List.of(net("n1", FIRST, SECOND), net("n2", OTHER), net("n3")));

    @Test
    void theWiresOfTheChangedTopLevelNetsGlowAndOtherIdsAreSkipped() {
        StepHighlight highlight = StepHighlight.of(Set.of("n1", "n3", "c4/n7", "p:c1#0"), netlist, 7, 0);

        assertThat(highlight.wires()).containsExactlyInAnyOrder(FIRST, SECOND);
        assertThat(StepHighlight.of(Set.of(), netlist, 7, 0).wires()).isEmpty();
    }

    @Test
    void fullForOneHundredFiftyMillisecondsThenFadingToNothingAtEightHundred() {
        long start = 1_000 * MILLISECOND;
        StepHighlight highlight = StepHighlight.of(Set.of("n2"), netlist, 7, start);

        assertThat(highlight.alpha(start)).isEqualTo(1);
        assertThat(highlight.alpha(start + 150 * MILLISECOND)).isEqualTo(1);
        assertThat(highlight.alpha(start + 475 * MILLISECOND)).isCloseTo(0.5, within(1e-9));
        assertThat(highlight.alpha(start + 800 * MILLISECOND)).isZero();
        assertThat(highlight.alpha(start + 5_000 * MILLISECOND)).isZero();
        assertThat(highlight.isOver(start + 799 * MILLISECOND)).isFalse();
        assertThat(highlight.isOver(start + 800 * MILLISECOND)).isTrue();
    }

    @Test
    void itBelongsToTheNetlistItWasBuiltFrom() {
        StepHighlight highlight = StepHighlight.of(Set.of("n1"), netlist, 7, 0);

        assertThat(highlight.matches(7)).isTrue();
        assertThat(highlight.matches(8)).isFalse();
    }
}
