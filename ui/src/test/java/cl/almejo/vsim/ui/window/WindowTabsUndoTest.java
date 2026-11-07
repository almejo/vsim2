package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Undo and redo across the tabs of a circuit (round C, item 5; the user's decision over spec §10.4): one history per
 * circuit, and a step made in another place of the group is undone or redone there — its tab comes to the front
 * first (opened again when it was closed, on the «Valores de» of the step) and what the step touched glows. Never
 * something the user is not looking at.
 */
class WindowTabsUndoTest {

    private static final String INVERTER = ExampleDefinitions.INVERTER;

    private final RecordingWindowHost host = new RecordingWindowHost();
    private final List<OpenDocument> documents = new ArrayList<>();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private WindowTabs tabs;

    /** The window as the views see it: places and steps are shown in {@link #tabs}. */
    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }

        @Override
        public Optional<EditorView> showPlace(OpenDocument document, Optional<String> definitionId,
                                              Optional<String> contextPath) {
            return Optional.of(tabs.showPlace(document, definitionId, contextPath));
        }

        @Override
        public Optional<EditorView> showPlaceOfStep(OpenDocument document, Optional<String> definitionId,
                                                    Optional<String> contextPath) {
            return Optional.of(tabs.showPlaceOfStep(document, definitionId, contextPath));
        }
    };

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new cl.almejo.vsim.core.library.ComponentLibrary(), List.of());
        tabs = new WindowTabs(host, place -> new EditorView(place, services, window),
                new SplitLayoutStore(preferences), () -> false);
    }

    @AfterEach
    void close() {
        tabs.disposeAll();
        documents.forEach(OpenDocument::close);
    }

    /** A circuit with the inverter placed twice. */
    private OpenDocument withTwoInverters() {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        documents.add(document);
        document.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        place(document, SubcircuitTypes.typeIdOf(INVERTER), 0, 0, Map.of());
        place(document, SubcircuitTypes.typeIdOf(INVERTER), 20, 0, Map.of());
        return document;
    }

    private static String instance(OpenDocument document, int index) {
        return document.document().components().stream()
                .filter(component -> component.typeId().equals(SubcircuitTypes.typeIdOf(INVERTER)))
                .toList().get(index).id().value();
    }

    private EditorView enter(OpenDocument document, int instanceIndex) {
        return tabs.enter(document, INVERTER, Optional.of(instance(document, instanceIndex)), Optional.empty());
    }

    private static boolean hasLed(OpenDocument place) {
        return place.document().components().stream()
                .anyMatch(component -> component.typeId().equals(StandardComponentTypes.LED));
    }

    private static Component addLed(OpenDocument place) {
        return place(place, StandardComponentTypes.LED, 30, 30, Map.of());
    }

    private static void undo(EditorView view) {
        perform(view.actions().undo());
    }

    private static void redo(EditorView view) {
        perform(view.actions().redo());
    }

    @Test
    void aStepMadeInAComponentTabIsUndoneThereFromTheCircuitsTab() {
        OpenDocument document = withTwoInverters();
        EditorView main = tabs.addTab(document, Optional.empty());
        EditorView inside = enter(document, 0);
        addLed(inside.openDocument());
        tabs.activate(main);

        undo(main);

        assertThat(tabs.activeView()).isSameAs(inside);
        assertThat(hasLed(inside.openDocument())).isFalse();
        assertThat(inside.canvas().isGlowing()).as("where the LED was glows").isTrue();
        assertThat(main.canvas().isGlowing()).isFalse();
    }

    @Test
    void redoGoesBackThereToo() {
        OpenDocument document = withTwoInverters();
        EditorView main = tabs.addTab(document, Optional.empty());
        EditorView inside = enter(document, 0);
        addLed(inside.openDocument());
        undo(inside);
        tabs.activate(main);

        redo(main);

        assertThat(tabs.activeView()).isSameAs(inside);
        assertThat(hasLed(inside.openDocument())).isTrue();
        assertThat(inside.canvas().isGlowing()).isTrue();
    }

    @Test
    void aStepMadeInTheCircuitIsUndoneInTheCircuitsTabFromAComponentTab() {
        OpenDocument document = withTwoInverters();
        EditorView main = tabs.addTab(document, Optional.empty());
        addLed(document);
        EditorView inside = enter(document, 0);

        undo(inside);

        assertThat(tabs.activeView()).isSameAs(main);
        assertThat(hasLed(document)).isFalse();
        assertThat(main.canvas().isGlowing()).isTrue();
    }

    @Test
    void aClosedTabOpensAgainOnTheValoresDeOfTheStep() {
        OpenDocument document = withTwoInverters();
        EditorView main = tabs.addTab(document, Optional.empty());
        EditorView inside = enter(document, 1);
        addLed(inside.openDocument());
        tabs.closeTab(inside);

        undo(main);

        EditorView reopened = tabs.activeView();
        assertThat(reopened).isNotSameAs(main);
        assertThat(reopened.openDocument().definitionId()).contains(INVERTER);
        assertThat(reopened.openDocument().contextPath()).contains(instance(document, 1));
        assertThat(hasLed(reopened.openDocument())).isFalse();
    }

    @Test
    void anOpenTabOfThePlaceKeepsItsOwnValoresDe() {
        OpenDocument document = withTwoInverters();
        EditorView main = tabs.addTab(document, Optional.empty());
        EditorView inside = enter(document, 0);
        addLed(inside.openDocument());
        inside.openDocument().setContextPath(Optional.of(instance(document, 1)));
        tabs.activate(main);

        undo(main);

        assertThat(tabs.activeView()).isSameAs(inside);
        assertThat(inside.openDocument().contextPath()).contains(instance(document, 1));
    }

    @Test
    void aStepWithADocumentEditTooIsUndoneInItsComponentTab() {
        OpenDocument document = withTwoInverters();
        EditorView main = tabs.addTab(document, Optional.empty());
        EditorView inside = enter(document, 0);
        OpenDocument body = inside.openDocument();
        Component led = body.registry().require(StandardComponentTypes.LED).newComponent(
                body.document().newComponentId(), new GridPoint(30, 30), Rotation.R0, Map.of(), "");
        body.executeInsideWith(new AddElementsCommand(List.of(led)), new CompositeCommand("nada", List.of()),
                "Agregar");
        tabs.activate(main);

        undo(main);

        assertThat(tabs.activeView()).isSameAs(inside);
        assertThat(hasLed(inside.openDocument())).isFalse();
    }

    @Test
    void aStepMadeHereIsUndoneHereWithoutAGlow() {
        OpenDocument document = withTwoInverters();
        EditorView main = tabs.addTab(document, Optional.empty());
        enter(document, 0);
        tabs.activate(main);
        addLed(document);

        undo(main);

        assertThat(tabs.activeView()).isSameAs(main);
        assertThat(hasLed(document)).isFalse();
        assertThat(main.canvas().isGlowing()).isFalse();
    }

    @Test
    void theItemsStillSayWhereTheStepWas() {
        OpenDocument document = withTwoInverters();
        EditorView main = tabs.addTab(document, Optional.empty());
        EditorView inside = enter(document, 0);
        addLed(inside.openDocument());
        tabs.activate(main);

        assertThat((String) main.actions().undo().getValue(Action.NAME)).contains("Inversor");
    }
}
