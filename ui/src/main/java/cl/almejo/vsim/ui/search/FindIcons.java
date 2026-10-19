package cl.almejo.vsim.ui.search;

import java.util.List;

/** The icons of the find bar and the tree's filter, by file name under {@code icons/}. */
public final class FindIcons {

    public static final String FIND = "find";
    public static final String PREVIOUS = "find-previous";
    public static final String NEXT = "find-next";
    public static final String MATCH_CASE = "find-match-case";
    public static final String REGEX = "find-regex";
    /** «Viaje suave»: a curved arrow. */
    public static final String ANIMATE = "find-animate";
    public static final String CLOSE = "find-close";

    private FindIcons() {
    }

    /** Every find icon, once. */
    public static List<String> names() {
        return List.of(FIND, PREVIOUS, NEXT, MATCH_CASE, REGEX, ANIMATE, CLOSE);
    }
}
