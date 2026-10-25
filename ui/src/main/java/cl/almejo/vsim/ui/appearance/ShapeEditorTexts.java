package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.DraftProblem;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;

import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The sentences of the shape editor worked out from its state (mockup screens 2–9): the scope line, the status next
 * to «Aceptar», the hint under the canvas and the tool tooltips. No Swing state, so each rule is tested on its own.
 */
final class ShapeEditorTexts {
    private static final String TOOL_KEY_PREFIX = "shape.tool.";

    private ShapeEditorTexts() { }

    /**
     * What the editor is looking at when the hint under the canvas is chosen; {@code boxHandle} while the box's
     * handle is pointed at or dragged.
     */
    record HintState(ShapeTool tool, boolean pinSelected, boolean pinsLocked, int selectedFigures,
                     boolean nodeSelected, boolean boxHandle) { }

    /** The bold fixed text of the scope line when it is not a switch (opened from the palette). */
    static String scopeTitle(ShapeEditScope scope) {
        return switch (scope) {
            case INSTANCE -> "";
            case CIRCUIT_DEFINITION -> Messages.text("shape.scope.circuit");
            case LIBRARY_DEFINITION -> Messages.text("shape.scope.library");
        };
    }

    /** The grey sentence after the switch or the title: what else changes. */
    static String scopeDetail(ShapeEditRequest request, ShapeEditScope scope) {
        return switch (scope) {
            case INSTANCE -> Messages.text("shape.scope.instance.detail");
            case CIRCUIT_DEFINITION -> request.savesToLibrary()
                    ? pieces(request) + " " + Messages.text("shape.scope.alsoLibrary") : pieces(request);
            case LIBRARY_DEFINITION -> request.circuitUsesOtherVersion()
                    ? Messages.text("shape.scope.otherVersion") : pieces(request);
        };
    }

    private static String pieces(ShapeEditRequest request) {
        return Messages.format("shape.scope.pieces", request.affectedInstances());
    }

    /** The status next to «Aceptar»: what blocks it, else how many warnings, else whether there is anything to accept. */
    static String status(List<DraftProblem> problems, boolean changed) {
        Optional<DraftProblem> blocking = problems.stream().filter(DraftProblem::blocksAccept).findFirst();
        if (blocking.isPresent()) {
            return problem(blocking.get().kind(), blocking.get().pins());
        }
        long warnings = problems.stream().filter(problem -> !problem.blocksAccept()).count();
        if (warnings > 0) {
            return Messages.format("shape.status.warnings", warnings);
        }
        return Messages.text(changed ? "shape.status.ready" : "shape.status.unchanged");
    }

    /** One problem as a sentence; off-edge pins are told together («Los pines F y Cout no están en el borde.»). */
    static String problem(DraftProblem.Kind kind, List<String> pins) {
        return switch (kind) {
            case COINCIDING_PINS -> Messages.format("shape.problem.coinciding_pins", pins.size(), names(pins));
            case PIN_OFF_EDGE -> Messages.format("shape.problem.pin_off_edge", pins.size(), names(pins));
            case EMPTY_DRAWING -> Messages.text("shape.problem.empty_drawing");
        };
    }

    /** «A», «A y B», «A, B y C». */
    static String names(List<String> names) {
        if (names.isEmpty()) {
            return "";
        }
        if (names.size() == 1) {
            return names.getFirst();
        }
        String allButLast = String.join(", ", names.subList(0, names.size() - 1));
        return Messages.format("shape.list.and", allButLast, names.getLast());
    }

    /** The «ⓘ» line under the canvas: what the current tool or selection does. */
    static String hint(HintState state) {
        return switch (state.tool()) {
            case SELECT -> selectHint(state);
            case NODES -> Messages.text(state.nodeSelected() ? "shape.hint.nodeSelected" : "shape.nodes.hint");
            case PAN -> Messages.text("shape.pan.hint");
            case ADD_NODE -> Messages.text("shape.nodeAdd.hint");
            case REMOVE_NODE -> Messages.text("shape.nodeRemove.hint");
            case CURVE -> Messages.text("shape.hint.curve");
            case LINE -> Messages.text("shape.hint.line");
            case MULTILINE -> Messages.format("shape.hint.multiline", shortcut(KeyEvent.VK_Z));
            case RECTANGLE -> Messages.text("shape.hint.rectangle");
            case ELLIPSE -> Messages.text("shape.hint.ellipse");
            case TRIANGLE -> Messages.text("shape.hint.triangle");
            case POLYGON -> Messages.format("shape.hint.polygon", shortcut(KeyEvent.VK_Z));
            case TEXT -> Messages.text("shape.hint.text");
        };
    }

    private static String selectHint(HintState state) {
        if (state.boxHandle()) {
            return Messages.text("shape.hint.box");
        }
        if (state.pinSelected()) {
            return Messages.text(state.pinsLocked() ? "shape.hint.lockedPin" : "shape.hint.pin");
        }
        if (state.selectedFigures() > 0) {
            return Messages.format("shape.hint.figures", state.selectedFigures(), shortcut(KeyEvent.VK_D));
        }
        return Messages.text("shape.hint.select");
    }

    /** The visible name of a tool («Rectángulo»). */
    static String toolName(ShapeTool tool) {
        return Messages.text(switch (tool) {
            case ADD_NODE -> "shape.nodeAdd";
            case REMOVE_NODE -> "shape.nodeRemove";
            default -> tool.messageKey();
        });
    }

    /** «Rectángulo (R)» and, on a second line, what Shift or a double click does there. */
    static String toolTip(ShapeTool tool) {
        return twoLines(Messages.format("shape.tool.tooltip", toolName(tool), tool.key()),
                Messages.text(TOOL_KEY_PREFIX + tool.name().toLowerCase(Locale.ROOT) + ".hint"));
    }

    /** A tooltip with a name and a quieter second line. */
    static String twoLines(String first, String second) {
        return "<html>" + first + "<br>" + second + "</html>";
    }

    /** «Quitar nodo — elige un nodo»: the name of a disabled button and why it is disabled. */
    static String disabled(String name, String reasonKey) {
        return Messages.format("shape.disabled", name, Messages.text(reasonKey));
    }

    /** The platform text of the menu modifier with {@code keyCode} («Ctrl+Z», «⌘Z»). */
    static String shortcut(int keyCode) {
        return KeyBindings.describe(KeyStroke.getKeyStroke(keyCode, ShortcutCatalog.menuMask()));
    }
}
