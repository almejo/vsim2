package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a «Qué hay de nuevo» bubble can point at: a component registered under a stable id (a client property), with
 * the side of it where its bubble goes first. The owner of each piece registers it when it builds it; a news file
 * names the ids ({@code NewsFileTest} checks every id it names is registered in a view with every extension).
 */
public final class NewsTargets {

    /** Where a bubble goes first, beside its target. */
    public enum Side { BELOW, ABOVE, LEFT, RIGHT }

    public static final String TOOLBAR_SELECT = "toolbar.select";
    public static final String TOOLBAR_CAPTURE = "toolbar.capture";
    public static final String TOOLBAR_LENS = "toolbar.lens";
    public static final String TOOLBAR_WAVEFORMS = "toolbar.waveforms";
    public static final String TOOLBAR_PRESENTATION_MODE = "toolbar.presentationMode";
    /** The prefix of a menu title's id: {@code menu.file}, {@code menu.view}, … (the {@code MenuId} in lower case). */
    public static final String MENU_PREFIX = "menu.";
    public static final String LEFT_STRIP = "strip.left";
    public static final String RIGHT_STRIP = "strip.right";
    public static final String STATUS_BAR = "statusBar";

    private static final String ID_PROPERTY = "vsim.whatsNew.target";
    private static final String SIDE_PROPERTY = "vsim.whatsNew.side";

    private NewsTargets() {
    }

    /** Makes {@code component} the target {@code id}, its bubble going first to {@code side}. */
    public static void register(JComponent component, String id, Side side) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(side, "side");
        component.putClientProperty(ID_PROPERTY, id);
        component.putClientProperty(SIDE_PROPERTY, side);
    }

    /** The side where {@code target}'s bubble goes first (below when it says none). */
    static Side sideOf(JComponent target) {
        return target.getClientProperty(SIDE_PROPERTY) instanceof Side side ? side : Side.BELOW;
    }

    /** The target {@code id} of {@code view}: in its menu bar, its toolbar or the view itself. */
    static Optional<JComponent> find(EditorView view, String id) {
        for (Container root : List.<Container>of(view.menuBar(), view.toolBar(), view)) {
            Optional<JComponent> found = findIn(root, id);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<JComponent> findIn(Component component, String id) {
        if (component instanceof JComponent candidate && id.equals(candidate.getClientProperty(ID_PROPERTY))) {
            return Optional.of(candidate);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Optional<JComponent> found = findIn(child, id);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
