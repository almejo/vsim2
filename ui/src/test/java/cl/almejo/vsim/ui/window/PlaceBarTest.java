package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.InsideEditRefusedException;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The place bar of a component tab (spec §5.1), built over a body place and laid out, never shown: route crumbs, the
 * «Valores de» list, the use chip, the library buttons and the locked-interface line.
 */
class PlaceBarTest {

    private static final String INVERTER = ExampleDefinitions.INVERTER;

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final ComponentLibrary library = new ComponentLibrary();
    private final List<String> calls = new ArrayList<>();
    private final List<OpenDocument> places = new ArrayList<>();
    private final List<PlaceBar> bars = new ArrayList<>();

    private final PlaceBar.Callbacks callbacks = new PlaceBar.Callbacks() {
        @Override
        public void showMain() {
            calls.add("main");
        }

        @Override
        public void enter(String definitionId, Optional<String> contextPath) {
            calls.add("enter " + definitionId + " " + contextPath.orElse("-"));
        }

        @Override
        public void seePiece() {
            calls.add("see");
        }

        @Override
        public void saveToLibrary(DefinitionBundle bundle) {
            calls.add("save " + bundle.root().id());
            library.put(bundle);
        }

        @Override
        public void addToLibrary(DefinitionBundle bundle) {
            calls.add("add " + bundle.root().id());
            library.put(bundle);
        }
    };

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void close() {
        bars.forEach(PlaceBar::dispose);
        places.forEach(OpenDocument::close);
        document.close();
    }

    /** The inverter on its own (c1) and inside a placed double inverter (c2, holding c2/c2 and c2/c3). */
    private void inverterInThreePlaces() {
        document.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter())));
        place(document, SubcircuitTypes.typeIdOf(INVERTER), 0, 0, Map.of());
        place(document, SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 20, 0, Map.of());
    }

    private PlaceBar barOf(String definitionId, Optional<String> context) {
        OpenDocument body = document.openBody(definitionId, context);
        places.add(body);
        PlaceBar bar = new PlaceBar(body, library, callbacks);
        bars.add(bar);
        layOut(bar);
        return bar;
    }

    @Test
    void listsEveryPathOfADefinitionUsedInThreePlaces() {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.empty());

        List<String> items = bar.valuesItems();

        assertThat(items).hasSize(4);
        assertThat(items.get(0)).isEqualTo("c1 en " + document.displayName());
        assertThat(items.get(1)).isEqualTo("c2 en Doble inversor (c2)");
        assertThat(items.get(2)).isEqualTo("c3 en Doble inversor (c2)");
        assertThat(items.getLast()).isEqualTo("Sin valores (no se simula)");
        assertThat(bar.selectedValuesItem()).isEqualTo(3);
    }

    @Test
    void choosingAPathChangesTheContext() {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.empty());

        bar.chooseValuesItem(2);

        assertThat(places.getFirst().contextPath()).contains("c2/c3");
        assertThat(bar.selectedValuesItem()).isEqualTo(2);

        bar.chooseValuesItem(3);

        assertThat(places.getFirst().contextPath()).isEmpty();
    }

    @Test
    void showsTheUseCountChip() {
        inverterInThreePlaces();
        PlaceBar three = barOf(INVERTER, Optional.empty());
        assertThat(three.useChipText()).isEqualTo("Se usa en 3 lugares");
        assertThat(three.useChipIsWarning()).isTrue();

        // a definition nothing places (review focus 1): «Sin valores» is the only context
        document.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.byteBuffer())));
        PlaceBar none = barOf(ExampleDefinitions.BYTE_BUFFER, Optional.empty());
        assertThat(none.useChipText()).isEqualTo("No se usa en ninguna pieza");
        assertThat(none.valuesItems()).containsExactly("Sin valores (no se simula)");
        assertThat(none.useChipIsWarning()).isFalse();

        place(document, SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER), 40, 0, Map.of());
        assertThat(none.useChipText()).isEqualTo("Se usa en 1 lugar");
        assertThat(none.valuesItems()).hasSize(2);
    }

    @Test
    void routeCrumbsFollowTheContext() {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.of("c2/c3"));

        assertThat(bar.crumbs()).containsExactly(document.displayName(), "Doble inversor (c2)", "Inversor (c3)");
        assertThat(bar.currentCrumb()).isEqualTo(2);

        bar.clickCrumb(0);
        bar.clickCrumb(1);
        bar.clickCrumb(2);

        assertThat(calls).containsExactly("main", "enter double-inverter c2");

        PlaceBar withoutValues = barOf(INVERTER, Optional.empty());
        assertThat(withoutValues.crumbs()).containsExactly(document.displayName(), "Inversor");
    }

    @Test
    void offersSaveToLibraryOnlyWhenTheCopyDiffers() {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.of("c1"));
        assertThat(bar.libraryButtonText()).contains("Agregar a mi librería");

        bar.clickLibraryButton();
        assertThat(calls).containsExactly("add inverter");
        assertThat(bar.libraryButtonText()).isEmpty();

        // the library's entry changes elsewhere: the circuit's copy now differs from it
        library.put(DefinitionBundle.of(ExampleDefinitions.inverter().withDescription("otra"), id -> Optional.empty()));
        assertThat(bar.libraryButtonText()).contains("Guardar en mi librería");

        bar.clickLibraryButton();
        assertThat(calls).containsExactly("add inverter", "save inverter");
        assertThat(bar.libraryButtonText()).isEmpty();
    }

    @Test
    void showsTheLockedExplanationWhenUsedInsideAnother() {
        inverterInThreePlaces();
        PlaceBar inner = barOf(INVERTER, Optional.of("c1"));
        assertThat(inner.lockedText()).contains(
                "Este componente se usa dentro de Doble inversor: sus pines no se pueden cambiar todavía");

        document.execute(new RemoveElementsCommand(List.of(document.document().components().getLast().id())));
        assertThat(inner.lockedText()).isEmpty();
    }

    @Test
    void aRefusalIsShownInTheBar() {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.of("c1"));

        bar.showRefusal("No se puede");

        assertThat(bar.refusalText()).contains("No se puede");
    }

    @Test
    void aRefusedEditInsideIsExplainedInTheBarOnce() {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.of("c2/c2"));
        String locked = "Este componente se usa dentro de Doble inversor: sus pines no se pueden cambiar todavía";
        assertThat(bar.lockedText()).contains(locked);

        // the inverter's input pin part «a»: its interface cannot change while a double inverter uses it
        places.getFirst().execute(new RemoveElementsCommand(List.of(new ElementId("c1"))));

        assertThat(bar.refusalText()).contains(locked);
        assertThat(bar.lockedText()).as("the same sentence is not shown twice").isEmpty();
        assertThat(places.getFirst().document().component(new ElementId("c1"))).isPresent();
    }

    @Test
    void aDisposedBarNoLongerExplainsRefusals() {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.of("c2/c2"));

        bar.dispose();

        assertThatThrownBy(() -> places.getFirst().execute(new RemoveElementsCommand(
                List.of(new ElementId("c1"))))).isInstanceOf(InsideEditRefusedException.class);
    }

    @Test
    void seePieceCallsBack() {
        inverterInThreePlaces();
        barOf(INVERTER, Optional.of("c1")).clickSeePiece();
        assertThat(calls).containsExactly("see");
    }
}
