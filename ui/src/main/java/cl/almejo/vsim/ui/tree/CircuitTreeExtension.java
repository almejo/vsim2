package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/**
 * Puts the circuit tree («Circuito», spec §5.3) above the palette of every editor view, as the accordion's first
 * panel. Disposing the view takes the tree out and stops it listening.
 */
public final class CircuitTreeExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        CircuitTree tree = new CircuitTree(view);
        Runnable removal = view.addAbovePalette(Messages.text("tree.title"), tree);
        return () -> {
            try {
                removal.run();
            } finally {
                tree.dispose();
            }
        };
    }
}
