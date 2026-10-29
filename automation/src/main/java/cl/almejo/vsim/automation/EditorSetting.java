package cl.almejo.vsim.automation;

import java.util.List;

/**
 * An editor setting the AI may change while it works (spec §7): only in a temporary layer of one window, over the
 * saved preferences, dropped when its task ends unless the user keeps it. Values travel as text, one of
 * {@link #allowedValues()}.
 */
public enum EditorSetting {
    /** «Ajustar»: the magnet as a whole. */
    SNAP(Values.BOOLEAN),
    /** «Ajustar a ▸ Piezas». */
    SNAP_TO_PARTS(Values.BOOLEAN),
    /** «Ajustar a ▸ Guías». */
    SNAP_TO_GUIDES(Values.BOOLEAN),
    /** «Ajustar a ▸ Líneas de la cuadrícula». */
    SNAP_TO_GRID_LINES(Values.BOOLEAN),
    /** «Mostrar guías»: the rulers and the guides. */
    SHOW_GUIDES(Values.BOOLEAN),
    /** «Valores de los buses»: the value written above each bus, on the canvas and in the exports. */
    SHOW_BUS_VALUES(Values.BOOLEAN),
    /** «Cuadrícula ▸»: dots, lines or nothing. */
    GRID_STYLE(List.of("DOTS", "LINES", "NONE")),
    /** «Cuadrícula ▸ Separación ▸», in grid units. */
    GRID_SPACING(List.of("2", "4", "5", "10")),
    /** «Los cables siguen» when parts move. */
    WIRES_FOLLOW(Values.BOOLEAN);

    private final List<String> allowedValues;

    EditorSetting(List<String> allowedValues) {
        this.allowedValues = allowedValues;
    }

    /** The texts this setting accepts, in the order a schema lists them. */
    public List<String> allowedValues() {
        return allowedValues;
    }

    private static final class Values {
        static final List<String> BOOLEAN = List.of("true", "false");
    }
}
