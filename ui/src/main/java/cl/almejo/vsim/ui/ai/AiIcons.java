package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.icons.Icons;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import java.util.List;

/**
 * The icons of the AI's buttons (IA.3, icon + text), by file name under {@code icons/}: drawn like VSim's other icons
 * with FlatLaf's action colours, which the dark themes recolour when painted. Some are VSim's own icons for the same
 * gesture: «Ver lo que hizo» is «Centrar»'s target, «Abrir carpeta» is «Abrir»'s folder, Cerrar the find bar's ×,
 * Reiniciar the simulation's circular arrow and «Copiar configuración» «Pegar»'s clipboard.
 */
public final class AiIcons {

    /** «Detener»: a square. */
    public static final String STOP = "ai-stop";
    /** «Deshacer lo que hizo la IA»: the undo arrow with a small AI mark. */
    public static final String UNDO = "ai-undo";
    /** Encender / Apagar. */
    public static final String POWER = "ai-power";
    public static final String RESTART = "restart";
    public static final String SHOW = "center";
    public static final String FOLDER = "open";
    public static final String CLOSE = "find-close";
    public static final String CLIPBOARD = "paste";

    private AiIcons() {
    }

    /** Every icon of the AI's panels and status line, once. */
    public static List<String> names() {
        return List.of(STOP, UNDO, POWER, RESTART, SHOW, FOLDER, CLOSE, CLIPBOARD);
    }

    /** The menu-sized icon {@code name}. */
    public static Icon icon(String name) {
        return Icons.get(name, Icons.MENU_SIZE);
    }

    /** Gives {@code button} the icon {@code name} next to its text; returns it. */
    public static <T extends AbstractButton> T on(T button, String name) {
        button.setIcon(icon(name));
        return button;
    }
}
