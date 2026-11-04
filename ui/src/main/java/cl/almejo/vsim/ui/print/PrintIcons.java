package cl.almejo.vsim.ui.print;

import java.util.List;

/** The icons of the print dialog, by file name under {@code icons/}: its notices and the preview's zoom. */
public final class PrintIcons {

    public static final String NOTICE_INFO = "notice-info";
    public static final String NOTICE_WARNING = "notice-warning";
    public static final String PREVIEW_ZOOM = "preview-zoom";

    private PrintIcons() {
    }

    /** Every icon of the print dialog, once. */
    public static List<String> names() {
        return List.of(NOTICE_INFO, NOTICE_WARNING, PREVIEW_ZOOM);
    }
}
