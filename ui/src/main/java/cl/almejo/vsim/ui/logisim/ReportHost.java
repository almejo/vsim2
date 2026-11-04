package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.core.document.ElementId;

import java.awt.Dimension;
import java.util.Optional;

/**
 * What the report window needs from the editor it reports on: the words for a part, going to it, and the size the
 * user gave the window. Tests give their own; {@link EditorReportHost} is the real one.
 */
interface ReportHost {

    /** The part {@code element} stands for in the document, in words ({@code NOT «U1» (12, 40)}). */
    String describe(ElementId element);

    /**
     * The part {@code element} stands for, as the holder of the custom circuit {@code circuit} that is inside it
     * (at any depth): {@code Arith Log Unit › Muldiv (77, 45)}. Plain {@link #describe} by default.
     */
    default String describeInside(ElementId element, String circuit) {
        return describe(element);
    }

    /** Selects the part and centres the canvas on it; false, with nothing changed, when it is not there any more. */
    boolean goTo(ElementId element);

    /** The size the user last gave the report window. */
    Optional<Dimension> size();

    void rememberSize(Dimension size);
}
