package cl.almejo.vsim.ui.resize;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ResizeSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.KeyboardPainter;

import java.util.Map;

/**
 * The text of the temporary tag above the tab while it is dragged: «Ancho 24 · 14 teclas» for a Teclado, «80×24»
 * (columns × rows) for a Terminal, the first parameter's name and value («Ancho 24») for other parts.
 */
final class ResizeTag {

    private ResizeTag() {
    }

    /** The tag of a part of {@code type} with {@code parameters} (the values being dragged to). */
    static String text(ComponentType type, Map<String, Object> parameters) {
        ResizeSpec resize = type.resize().orElseThrow();
        int value = resize.valueOf(type, parameters);
        if (StandardComponentTypes.TERMINAL.equals(type.id())) {
            Map<String, Object> resolved = type.resolveParameters(parameters);
            return Messages.format("resize.terminal.tag", resolved.get(TerminalTypes.COLUMNS),
                    resolved.get(TerminalTypes.ROWS));
        }
        if (StandardComponentTypes.KEYBOARD.equals(type.id())) {
            return Messages.format("resize.keyboard.tag", value,
                    KeyboardPainter.visibleKeys(resize.extentOf(type, parameters)));
        }
        return Messages.format("resize.tag", Messages.parameterName(resize.parameter()), value);
    }
}
