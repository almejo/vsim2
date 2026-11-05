package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** «Ver la pieza ↗»: shows the place that holds the context instance and selects it there. */
class SeePieceTest {

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private WindowTabs tabs;

    /** What a window does for a view: the tabs' own {@code goToPart}. */
    private WindowCommands windowOver(WindowTabs windowTabs) {
        return new WindowCommands() {
            @Override public void newDocument() { }
            @Override public void open() { }
            @Override public void save() { }
            @Override public void saveAs() { }
            @Override public void closeWindow() { }
            @Override public void exit() { }

            @Override
            public boolean goToPart(OpenDocument target, Optional<String> definitionId, Optional<String> contextPath,
                                    ElementId part) {
                windowTabs.goToPart(target, definitionId, contextPath, part);
                return true;
            }
        };
    }

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        AppPreferences preferences = AppPreferences.inMemory();
        WindowCommands[] window = new WindowCommands[1];
        tabs = new WindowTabs(new RecordingWindowHost(),
                place -> new EditorView(place, PainterRegistry.standard(), ColorScheme.LIGHT, window[0]),
                new SplitLayoutStore(preferences), () -> false);
        window[0] = windowOver(tabs);
        document.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter())));
        place(document, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 0, 0, Map.of());
        place(document, SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 30, 0, Map.of());
    }

    @AfterEach
    void close() {
        tabs.disposeAll();
        document.close();
    }

    @Test
    void anInstanceOnTheCircuitIsSelectedOnTheMainTab() {
        EditorView main = tabs.addTab(document, Optional.empty());
        EditorView inside = tabs.enter(document, ExampleDefinitions.INVERTER, Optional.of("c1"), Optional.empty());
        assertThat(tabs.activeView()).isSameAs(inside);

        SeePiece.show(inside);

        assertThat(tabs.activeView()).isSameAs(main);
        assertThat(main.canvas().selection().ids()).containsExactly(new ElementId("c1"));
    }

    @Test
    void aNestedInstanceOpensTheContainersTabWithTheRouteAboveIt() {
        tabs.addTab(document, Optional.empty());
        EditorView inside = tabs.enter(document, ExampleDefinitions.INVERTER, Optional.of("c2/c3"),
                Optional.empty());

        SeePiece.show(inside);

        EditorView container = tabs.activeView();
        assertThat(container.openDocument().definitionId()).contains(ExampleDefinitions.DOUBLE_INVERTER);
        assertThat(container.openDocument().contextPath()).contains("c2");
        assertThat(container.canvas().selection().ids()).containsExactly(new ElementId("c3"));
    }

    @Test
    void withoutValuesThereIsNoPieceToSee() {
        EditorView main = tabs.addTab(document, Optional.empty());
        EditorView inside = tabs.enter(document, ExampleDefinitions.INVERTER, Optional.empty(), Optional.empty());

        SeePiece.show(inside);

        assertThat(tabs.activeView()).isSameAs(inside);
        assertThat(main.canvas().selection().ids()).isEmpty();
    }
}
