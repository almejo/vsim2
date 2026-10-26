package cl.almejo.vsim.ui;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;

/**
 * An open document, the standard editor services (standard painters, light scheme, an empty library, no extensions)
 * and an editor view on them, for the tests that need nothing special. {@link #close()} disposes the views in the
 * order they were created and then closes the document, which is what those tests did by hand; a test that has
 * features of its own disposes them first.
 */
public final class EditorFixture implements AutoCloseable {

    private final OpenDocument openDocument;
    private final EditorServices services;
    private final EditorView view;
    private final List<EditorView> extraViews = new ArrayList<>();

    private EditorFixture(OpenDocument openDocument, AppPreferences preferences) {
        this.openDocument = openDocument;
        this.services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                new ComponentLibrary(), List.of());
        this.view = new EditorView(openDocument, services, NO_WINDOW);
    }

    /** A new empty document, in-memory preferences. */
    public static EditorFixture create() {
        return create(AppPreferences.inMemory());
    }

    /** A new empty document with the given preferences. */
    public static EditorFixture create(AppPreferences preferences) {
        return around(OpenDocument.createEmpty(StandardComponentTypes.registry()), preferences);
    }

    /** An editor on {@code openDocument}, which {@link #close()} closes; in-memory preferences. */
    public static EditorFixture around(OpenDocument openDocument) {
        return around(openDocument, AppPreferences.inMemory());
    }

    /** An editor on {@code openDocument}, which {@link #close()} closes, with the given preferences. */
    public static EditorFixture around(OpenDocument openDocument, AppPreferences preferences) {
        return new EditorFixture(openDocument, preferences);
    }

    public OpenDocument openDocument() {
        return openDocument;
    }

    public EditorServices services() {
        return services;
    }

    public EditorView view() {
        return view;
    }

    /** Another view on the same document and services; {@link #close()} disposes it after the first one. */
    public EditorView otherView() {
        EditorView other = new EditorView(openDocument, services, NO_WINDOW);
        extraViews.add(other);
        return other;
    }

    @Override
    public void close() {
        view.dispose();
        extraViews.forEach(EditorView::dispose);
        openDocument.close();
    }
}
