package cl.almejo.vsim.ui.project;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.SeePiece;

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

        /**
         * Shows the part where it is, as the place bar's «Ver la pieza» does: a part of the circuit in its main tab, a
         * nested one in the tab of the component that holds it. Without a window, a part of this view is shown here.
         */
        @Override
        public void seePiece(ProjectRows.Row row) {
            row.instance().ifPresent(use -> {
                if (!SeePiece.show(view.windowCommands(), circuit(), use.path()) && editsHere(row)) {
                    showHere(row);
                }
            });
        }

        @Override
        public void changeShape(ProjectRows.Row row) {
            showHere(row);
            componentOf(row).ifPresent(component -> view.changeShapeOf(component.id()));
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
            componentOf(row).ifPresent(component -> SeePiece.selectAndTravel(view, component.id()));
        }
    }
}
