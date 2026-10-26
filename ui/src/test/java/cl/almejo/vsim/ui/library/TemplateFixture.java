package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;

/**
 * One editor view of {@code openDocument} with the M4-A features installed over a library kept in
 * {@code libraryDirectory}. The library service is not started (no watcher thread); call {@code service.reload()}.
 */
final class TemplateFixture implements AutoCloseable {

    final AppPreferences preferences;
    final ComponentLibrary library;
    final LibraryService service;
    final FakeLibraryDialogs dialogs = new FakeLibraryDialogs();
    final OpenDocument openDocument;
    final EditorView view;
    final TemplateFeatures features;
    private final boolean ownsService;

    TemplateFixture(Path libraryDirectory) {
        this(libraryDirectory, OpenDocument.createEmpty(StandardComponentTypes.registry()));
    }

    TemplateFixture(Path libraryDirectory, OpenDocument openDocument) {
        Messages.initialize(Messages.SPANISH);
        this.preferences = AppPreferences.inMemory();
        preferences.setLibraryDirectory(Optional.of(libraryDirectory));
        this.library = new ComponentLibrary();
        this.service = new LibraryService(preferences, library, StandardComponentTypes.registry(), Runnable::run);
        this.ownsService = true;
        this.openDocument = openDocument;
        this.view = new EditorView(openDocument, services(), NO_WINDOW);
        this.features = TemplateFeatures.install(view, service, dialogs);
    }

    /** Another window: its own document and view, sharing {@code shared}'s library and service. */
    TemplateFixture(TemplateFixture shared, OpenDocument openDocument) {
        this.preferences = shared.preferences;
        this.library = shared.library;
        this.service = shared.service;
        this.ownsService = false;
        this.openDocument = openDocument;
        this.view = new EditorView(openDocument, services(), NO_WINDOW);
        this.features = TemplateFeatures.install(view, service, dialogs);
    }

    EditorServices services() {
        return new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences, library,
                List.of());
    }

    Component place(String typeId, int x, int y, String label) {
        return add(typeId, x, y, Map.of(), label);
    }

    Component place(String typeId, int x, int y, Map<String, Object> parameters) {
        return add(typeId, x, y, parameters, "");
    }

    void select(Component... components) {
        view.canvas().selection().set(Arrays.stream(components).map(Component::id).toList());
    }

    /** input-pin "a" (x, y+1) → NOT (x+2, y) → output-pin "y" (x+6, y+1), touching pin to pin; selects them. */
    List<Component> drawInverter(int x, int y) {
        Component input = place(InterfacePins.INPUT_PIN, x, y + 1, "a");
        Component gate = place(StandardComponentTypes.NOT, x + 2, y, "");
        Component output = place(InterfacePins.OUTPUT_PIN, x + 6, y + 1, "y");
        select(input, gate, output);
        return List.of(input, gate, output);
    }

    /** input-pin "a" (x, y+1) touching output-pin "y" (x+2, y+1): a wire-less pass-through; selects both. */
    List<Component> drawPassThrough(int x, int y) {
        Component input = place(InterfacePins.INPUT_PIN, x, y + 1, "a");
        Component output = place(InterfacePins.OUTPUT_PIN, x + 2, y + 1, "y");
        select(input, output);
        return List.of(input, output);
    }

    Component single(String typeId) {
        return openDocument.document().components().stream()
                .filter(component -> component.typeId().equals(typeId))
                .findFirst()
                .orElseThrow();
    }

    VisibleState stateOf(Component component) {
        return openDocument.latestSnapshot().componentState(component.id().value());
    }

    @Override
    public void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
        if (ownsService) {
            service.close();
        }
    }

    private Component add(String typeId, int x, int y, Map<String, Object> parameters, String label) {
        ComponentType type = openDocument.registry().require(typeId);
        Component component = type.newComponent(openDocument.document().newComponentId(), new GridPoint(x, y),
                Rotation.R0, parameters, label);
        openDocument.execute(new AddElementsCommand(List.of(component)));
        return component;
    }
}
