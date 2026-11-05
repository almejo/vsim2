package cl.almejo.vsim.ui.project;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.library.InstancePaths;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.Optional;

/**
 * Adds the «Proyecto» panel above the palette of every editor view (spec §5.2). Disposing the view takes the panel out
 * and stops it following the document.
 */
public final class ProjectPanelExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        ProjectPanel panel = new ProjectPanel(view.openDocument(), new ViewActions(view));
        Runnable removal = view.addAbovePalette(panel);
        return () -> {
            try {
                removal.run();
            } finally {
                panel.dispose();
            }
        };
    }

    /** What the rows do, in the view that shows the panel. */
    private record ViewActions(EditorView view) implements ProjectPanel.Actions {

        private OpenDocument circuit() {
            return view.openDocument().parent().orElse(view.openDocument());
        }

        @Override
        public void enter(ProjectRows.Row row) {
            row.instance().ifPresent(use -> view.windowCommands().enter(circuit(), use.definitionId(),
                    Optional.of(use.path())));
        }

        @Override
        public boolean editsHere(ProjectRows.Row row) {
            return !view.openDocument().isBody() && row.instance().map(use -> use.chain().size() == 1).orElse(false);
        }

        @Override
        public boolean canSee(ProjectRows.Row row) {
            return editsHere(row) || row.instance().map(use -> use.chain().size() > 1).orElse(false);
        }

        /**
         * A part of this view's own place is selected and shown; a nested one opens its parent's place (the tab that
         * holds it), where the part is one of the pieces.
         */
        @Override
        public void seePiece(ProjectRows.Row row) {
            if (editsHere(row)) {
                showHere(row);
                return;
            }
            row.instance().filter(use -> use.chain().size() > 1).ifPresent(use -> {
                String parentPath = use.path().substring(0, use.path().lastIndexOf(InstancePath.SEPARATOR));
                InstancePaths.of(circuit().document()).stream().filter(other -> other.path().equals(parentPath))
                        .findFirst().ifPresent(parent -> view.windowCommands().enter(circuit(),
                                parent.definitionId(), Optional.of(parentPath)));
            });
        }

        @Override
        public void changeShape(ProjectRows.Row row) {
            showHere(row);
            componentOf(row).ifPresent(component -> {
                JPopupMenu menu = view.contextMenu(Optional.of(component), new Point2D.Double(0, 0));
                String text = Messages.text("shape.change");
                for (java.awt.Component item : menu.getComponents()) {
                    if (item instanceof JMenuItem menuItem && text.equals(menuItem.getText())) {
                        menuItem.doClick();
                        return;
                    }
                }
            });
        }

        @Override
        public void properties(ProjectRows.Row row) {
            showHere(row);
            view.inspector().showTab(Messages.text("inspector.properties"));
        }

        private Optional<Component> componentOf(ProjectRows.Row row) {
            return row.instance().flatMap(use -> view.openDocument().document().component(use.chain().getFirst()));
        }

        /** Selects the part of this view's place and travels to it. */
        private void showHere(ProjectRows.Row row) {
            componentOf(row).ifPresent(component -> {
                view.selection().select(component.id());
                view.canvas().travelTo(DrawnBounds.of((DocumentElement) component, view.openDocument().registry()));
            });
        }
    }
}
