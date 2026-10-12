package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.List;

/**
 * One badge on the canvas (M6.1-E): the grid point it hangs from (the probed pin, or the middle of a probed wire), the
 * side of the component's box that pin is on (the badge goes outward, away from the body), and the channels watching
 * that point, in channel order, with the names their rows show.
 */
public record ProbeMarker(GridPoint anchor, Side side, List<String> probeIds, List<String> names) {

    /** The edge of the component's box the anchor pin is on; {@code RIGHT} for a wire channel. */
    public enum Side { RIGHT, LEFT, TOP, BOTTOM }

    public ProbeMarker {
        probeIds = List.copyOf(probeIds);
        names = List.copyOf(names);
    }
}
