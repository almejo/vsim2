package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.search.TypeNames;
import cl.almejo.vsim.ui.window.EditorView;

import java.awt.Dimension;
import java.util.Optional;

/**
 * The report window's view of one editor: parts are named as the find bar names them, and going to one selects it and
 * takes the active pane there, as the find bar and the problems list do. With no editor ({@code view} empty: the
 * document found no window) parts are named by their id and there is nowhere to go.
 */
final class EditorReportHost implements ReportHost {

    private final Optional<EditorView> view;
    private final AppPreferences preferences;

    EditorReportHost(Optional<EditorView> view, AppPreferences preferences) {
        this.view = view;
        this.preferences = preferences;
    }

    @Override
    public String describe(ElementId element) {
        return describe(element, "");
    }

    @Override
    public String describeInside(ElementId element, String circuit) {
        return describe(element, circuit);
    }

    /** The part's words; with a non-blank {@code inside} that is not the part's own name, its path ends there. */
    private String describe(ElementId element, String inside) {
        Optional<DocumentElement> found = view.flatMap(editor -> editor.openDocument().document().element(element));
        if (found.isEmpty()) {
            return element.value();
        }
        return switch (found.get()) {
            case Component component -> {
                String name = TypeNames.of(component, view.get().openDocument().registry());
                String x = String.valueOf(component.position().x());
                String y = String.valueOf(component.position().y());
                boolean path = !inside.isBlank() && !inside.equals(name);
                if (path) {
                    yield component.label().isBlank()
                            ? Messages.format(LogisimImportTexts.PART_INSIDE, name, inside, x, y)
                            : Messages.format(LogisimImportTexts.PART_LABELED_INSIDE, name, component.label(), inside,
                            x, y);
                }
                yield component.label().isBlank() ? Messages.format(LogisimImportTexts.PART, name, x, y)
                        : Messages.format(LogisimImportTexts.PART_LABELED, name, component.label(), x, y);
            }
            case TextLabel text -> Messages.format(LogisimImportTexts.PART_TEXT, text.text());
            default -> element.value();
        };
    }

    @Override
    public boolean goTo(ElementId element) {
        if (view.isEmpty()) {
            return false;
        }
        EditorView editor = view.get();
        Optional<DocumentElement> found = editor.openDocument().document().element(element);
        if (found.isEmpty()) {
            return false;
        }
        editor.selection().select(element);
        editor.canvas().travelTo(DrawnBounds.of(found.get(), editor.openDocument().registry()));
        editor.canvas().requestFocusInWindow();
        return true;
    }

    @Override
    public Optional<Dimension> size() {
        return preferences.logisimReportSize();
    }

    @Override
    public void rememberSize(Dimension size) {
        preferences.setLogisimReportSize(size);
    }
}
