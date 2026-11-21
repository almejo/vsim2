package cl.almejo.vsim.ui.panels;

/** What the floating cards share (I6): one corner (unscaled; scale it with {@code UIScale}) and one close icon. */
public final class CardLook {

    /** The arc of the rounded corner of a card, in pixels at 100 %. */
    public static final int CORNER = 10;
    /** The icon of a card's close button (the find bar's cross, recoloured by FlatLaf in dark themes). */
    public static final String CLOSE_ICON = "find-close";

    private CardLook() {
    }
}
