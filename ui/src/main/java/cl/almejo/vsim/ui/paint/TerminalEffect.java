package cl.almejo.vsim.ui.paint;

/**
 * «Efecto de pantalla» of a Terminal (spec §8.2, mockup §4): scanlines (a dark 1 px line every 3 px), a glow around
 * the glyphs, a vignette for «Fuerte», and the phosphor persistence after a scroll or a clear.
 */
public enum TerminalEffect {
    NONE("none", 0, 0, false, 0),
    /** «Suave»: scanlines at 16 %, a soft glow, 250 ms of persistence. */
    SOFT("soft", 0.16f, 1, false, 250),
    /** «Fuerte»: scanlines at 30 %, a stronger glow, dark corners, 480 ms of persistence. */
    STRONG("strong", 0.30f, 2, true, 480);

    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    private final String value;
    private final float scanlineAlpha;
    private final int glowRings;
    private final boolean vignette;
    private final long persistenceMilliseconds;

    TerminalEffect(String value, float scanlineAlpha, int glowRings, boolean vignette, long persistenceMilliseconds) {
        this.value = value;
        this.scanlineAlpha = scanlineAlpha;
        this.glowRings = glowRings;
        this.vignette = vignette;
        this.persistenceMilliseconds = persistenceMilliseconds;
    }

    /** The effect saved as {@code value}; none for anything else. */
    public static TerminalEffect of(Object value) {
        for (TerminalEffect effect : values()) {
            if (effect.value.equals(String.valueOf(value))) {
                return effect;
            }
        }
        return NONE;
    }

    /** How dark the scanlines are, 0 for none. */
    float scanlineAlpha() {
        return scanlineAlpha;
    }

    /** How many rings of low-alpha copies make the glow around each glyph, 0 for none. */
    int glowRings() {
        return glowRings;
    }

    /** Whether the corners darken. */
    boolean vignette() {
        return vignette;
    }

    /** How long the old picture takes to fade after a scroll or a clear, in nanoseconds; 0 for no persistence. */
    public long persistenceNanos() {
        return persistenceMilliseconds * NANOS_PER_MILLISECOND;
    }
}
