package cl.almejo.vsim.ui.print;

/** How big the circuit is printed: fitted to one sheet, or at a fixed percentage tiled over several sheets. */
public sealed interface ScaleMode permits ScaleMode.FitToOnePage, ScaleMode.Percentage {

    int MINIMUM_PERCENT = 10;
    int MAXIMUM_PERCENT = 400;

    /** «Ajustar a una hoja»: one sheet, never bigger than 100 %. */
    record FitToOnePage() implements ScaleMode {
    }

    /** A fixed scale; 100 % = one canvas pixel at zoom 100 % per point. */
    record Percentage(int percent) implements ScaleMode {

        public Percentage {
            if (percent < MINIMUM_PERCENT || percent > MAXIMUM_PERCENT) {
                throw new IllegalArgumentException("percent out of " + MINIMUM_PERCENT + ".." + MAXIMUM_PERCENT
                        + ": " + percent);
            }
        }
    }
}
