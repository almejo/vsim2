package cl.almejo.vsim.core.document;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitDocumentProbesTest {

    private final CircuitDocument document = new CircuitDocument();

    private static Probe pin(String id, String componentId, int pinIndex, String name) {
        return new Probe(id, new ProbeTarget.PinTarget(new ElementId(componentId), pinIndex), name);
    }

    @Test
    void probesStartEmptyAndChangingThemNotifiesOnlyProbeListeners() {
        List<String> calls = new ArrayList<>();
        document.addListener((changed, change) -> calls.add("document"));
        document.addProbeListener(() -> calls.add("probes"));
        long revision = document.revision();
        assertThat(document.probes()).isEmpty();

        List<Probe> probes = List.of(pin("p1", "c1", 0, "U1.out"),
                new Probe("p2", new ProbeTarget.WireTarget(new ElementId("w2")), "cable w2"));
        document.setProbes(probes);

        assertThat(document.probes()).containsExactlyElementsOf(probes);
        assertThat(calls).containsExactly("probes");
        assertThat(document.revision()).isEqualTo(revision);

        document.setProbes(List.copyOf(probes));
        assertThat(calls).as("an equal list notifies nobody").containsExactly("probes");
    }

    @Test
    void duplicateIdsAreRefusedAndChangeNothing() {
        document.setProbes(List.of(pin("p1", "c1", 0, "")));
        assertThatThrownBy(() -> document.setProbes(List.of(pin("p2", "c1", 0, ""), pin("p2", "c1", 1, ""))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(document.probes()).containsExactly(pin("p1", "c1", 0, ""));
    }

    @Test
    void newProbeIdsCountUpAndSkipIdsAlreadyInUse() {
        assertThat(document.newProbeId()).isEqualTo("p1");
        document.setProbes(List.of(pin("p7", "c1", 0, "")));
        assertThat(document.newProbeId()).isEqualTo("p8");
        document.setProbes(List.of(pin("x", "c1", 0, ""), pin("p", "c1", 1, ""), pin("p2", "c1", 2, "")));
        assertThat(document.newProbeId()).isEqualTo("p9");
    }

    @Test
    void probeListenersCanBeRemoved() {
        List<String> calls = new ArrayList<>();
        Runnable listener = () -> calls.add("probes");
        document.addProbeListener(listener);
        assertThat(document.probeListenerCount()).isEqualTo(1);
        document.removeProbeListener(listener);
        assertThat(document.probeListenerCount()).isZero();
        document.setProbes(List.of(pin("p1", "c1", 0, "")));
        assertThat(calls).isEmpty();
    }

    @Test
    void probesAndTargetsCheckTheirParts() {
        assertThatThrownBy(() -> new ProbeTarget.PinTarget(new ElementId("c1"), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Probe(" ", new ProbeTarget.WireTarget(new ElementId("w1")), ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Probe("p1", new ProbeTarget.WireTarget(new ElementId("w1")), null).name()).isEmpty();
        assertThat(pin("p1", "c1", 0, "a").withName("b")).isEqualTo(pin("p1", "c1", 0, "b"));
    }
}
