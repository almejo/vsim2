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

import cl.almejo.vsim.ui.theme.FlatLafInstaller;
import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.ui.window.tabs.GroupColors;

import javax.swing.AbstractButton;
import javax.swing.JMenuItem;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.REFUSED_INSIDE_TEXT;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.refusedInside;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The place bar of a component tab (spec §5.1), built over a body place and laid out, never shown: route crumbs, the
 * «Valores de» list, the use chip, the library buttons and the explanation of a refused edit.
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
        assertThat(items.get(0)).isEqualTo("Inversor en " + document.displayName());
        assertThat(items.get(1)).isEqualTo("Inversor · copia 1 en Doble inversor");
        assertThat(items.get(2)).isEqualTo("Inversor · copia 2 en Doble inversor");
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

        assertThat(bar.crumbs()).containsExactly(document.displayName(), "Doble inversor", "Inversor · copia 2");
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
        assertThat(bar.libraryButtonText()).contains("Agregar a Mis componentes");

        bar.clickLibraryButton();
        assertThat(calls).containsExactly("add inverter");
        assertThat(bar.libraryButtonText()).isEmpty();

        // the library's entry changes elsewhere: the circuit's copy now differs from it
        library.put(DefinitionBundle.of(ExampleDefinitions.inverter().withDescription("otra"), id -> Optional.empty()));
        assertThat(bar.libraryButtonText()).contains("Guardar en Mis componentes");

        bar.clickLibraryButton();
        assertThat(calls).containsExactly("add inverter", "save inverter");
        assertThat(bar.libraryButtonText()).isEmpty();
    }

    @Test
    void anInterfaceEditInsideAComponentUsedInsideAnotherApplies() {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.of("c2/c2"));

        // the inverter's input pin part «a»: the double inverter's body follows (spec §6.2 c)
        places.getFirst().execute(new RemoveElementsCommand(List.of(new ElementId("c1")),
                places.getFirst().registry()));

        assertThat(places.getFirst().document().component(new ElementId("c1"))).isEmpty();
        assertThat(bar.refusalText()).isEmpty();
    }

    @Test
    void aRefusalIsShownInTheBar() {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.of("c1"));

        bar.showRefusal("No se puede");

        assertThat(bar.refusalText()).contains("No se puede");
    }

    @Test
    void aRefusedEditInsideIsExplainedInTheBar() {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.of("c2/c2"));

        places.getFirst().execute(refusedInside());

        assertThat(bar.refusalText()).contains(REFUSED_INSIDE_TEXT);
        assertThat(places.getFirst().document().component(new ElementId("c1"))).isPresent();
    }

    @Test
    void aDisposedBarNoLongerExplainsRefusals() {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.of("c2/c2"));

        bar.dispose();

        assertThatThrownBy(() -> places.getFirst().execute(refusedInside()))
                .isInstanceOf(InsideEditRefusedException.class);
    }

    @Test
    void seePieceCallsBack() {
        inverterInThreePlaces();
        barOf(INVERTER, Optional.of("c1")).clickSeePiece();
        assertThat(calls).containsExactly("see");
    }

    // --- fix round 4 (M): the bar's room and its contrast ---

    /** The inverter's bar with a machine's «Probar…», laid out {@code width} wide. */
    private PlaceBar machineLikeBarAt(ToIntFunction<PlaceBar> width) {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.of("c1"));
        bar.showMachineActions(() -> calls.add("test"));
        bar.invalidate(); // a bar without a parent: its revalidate() does nothing, so its layout is told here
        bar.setSize(width.applyAsInt(bar), bar.getPreferredSize().height);
        layOut(bar);
        return bar;
    }

    private PlaceBar machineLikeBarAt(int width) {
        return machineLikeBarAt(bar -> width);
    }

    @Test
    void theBarAsksForLittleRoomSoTheSidePanelsCanWiden() {
        PlaceBar bar = machineLikeBarAt(1600);

        assertThat(bar.getPreferredSize().width).as("everything side by side").isGreaterThan(900);
        assertThat(bar.getMinimumSize().width).as("what does not fit goes behind «»»").isLessThan(300);
    }

    @Test
    void aWideBarShowsEverythingAndNoOverflowButton() {
        PlaceBar bar = machineLikeBarAt(laidOut -> laidOut.getPreferredSize().width + 40);

        assertThat(bar.overflowShown()).isFalse();
        assertThat(bar.overflowTexts()).isEmpty();
        assertThat(bar.shownActionTexts()).containsSubsequence("Ver la pieza ↗", "Probar…",
                "Agregar a Mis componentes");
        assertThat(bar.shownActionTexts()).doesNotContain("Convertir a circuito…");
    }

    @Test
    void aNarrowBarPutsWhatDoesNotFitInItsOverflowMenuInOrderWithTheirReasons() {
        PlaceBar bar = machineLikeBarAt(560);

        assertThat(bar.overflowShown()).isTrue();
        assertThat(bar.crumbsShown()).as("the route stays on the bar").isTrue();
        assertThat(bar.overflowTexts()).containsSubsequence("Probar…",
                "Agregar a Mis componentes");
        assertThat(bar.overflowTexts()).doesNotContain("Convertir a circuito…");
        JMenuItem test = bar.overflowItems().stream().filter(item -> item.getText().equals("Probar…"))
                .findFirst().orElseThrow();

        test.doClick(0);

        assertThat(calls).containsExactly("test");
    }

    @Test
    void theValuesChoiceKeepsItsCaptionInTheOverflowMenu() {
        PlaceBar bar = machineLikeBarAt(200);

        assertThat(bar.overflowTexts()).anyMatch(text -> text.startsWith("Valores de: "));
        assertThat(bar.shownActionTexts()).doesNotContain("Valores de");
    }

    @Test
    void aVeryNarrowBarKeepsTheRouteAndCutsIt() {
        PlaceBar bar = machineLikeBarAt(120);

        assertThat(bar.crumbsShown()).isTrue();
        assertThat(bar.overflowShown()).isTrue();
        assertThat(bar.shownActionTexts()).isEmpty();
    }

    @Test
    void theCurrentCrumbAndTheUseChipReadOnTheTintedBarInBothThemesAndEveryGroupColour() throws Exception {
        inverterInThreePlaces();
        PlaceBar several = barOf(INVERTER, Optional.of("c1"));
        document.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.byteBuffer())));
        place(document, SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER), 40, 0, Map.of());
        PlaceBar one = barOf(ExampleDefinitions.BYTE_BUFFER, Optional.empty());
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            for (boolean dark : new boolean[]{false, true}) {
                UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", dark));
                for (int ordinal = 0; ordinal < 6; ordinal++) {
                    GroupColors.adopt(document, ordinal);
                    for (PlaceBar bar : List.of(several, one)) {
                        SwingUtilities.updateComponentTreeUI(bar);
                        Color background = bar.getBackground();
                        String where = (dark ? "dark" : "light") + " group " + ordinal;
                        assertThat(ReadableColors.contrast(bar.useChipForeground(), background))
                                .as("«%s» on %s", bar.useChipText(), where).isGreaterThanOrEqualTo(4.5);
                        java.awt.Component current = bar.currentCrumbComponent();
                        assertThat(current.isEnabled()).as("the current place is no disabled control").isTrue();
                        assertThat(ReadableColors.contrast(current.getForeground(), background))
                                .as("current crumb on %s", where).isGreaterThanOrEqualTo(4.5);
                    }
                }
            }
        } finally {
            UIManager.setLookAndFeel(original);
        }
    }

    @Test
    void theCurrentCrumbDoesNothingWhenPressed() {
        inverterInThreePlaces();
        PlaceBar bar = barOf(INVERTER, Optional.of("c2/c3"));

        if (bar.currentCrumbComponent() instanceof AbstractButton button) {
            button.doClick(0);
        }

        assertThat(calls).isEmpty();
    }
}
