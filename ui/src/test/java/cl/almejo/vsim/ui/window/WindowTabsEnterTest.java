package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entering a user component (spec §4.4, §4.5): a component tab of its definition in the circuit's group, focused and
 * re-contexted on re-entry, released (its body place closed) whenever it goes, and closed with a notice when the
 * definition leaves the circuit. A frame cannot be built headless, so the window's rules are tested on
 * {@link WindowTabs} with a recording host.
 */
class WindowTabsEnterTest {

    private static final String INVERTER = ExampleDefinitions.INVERTER;

    private final RecordingWindowHost host = new RecordingWindowHost();
    private final RecordingWindowHost otherHost = new RecordingWindowHost();
    private final List<OpenDocument> documents = new ArrayList<>();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private WindowTabs tabs;
    private WindowTabs other;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        tabs = newTabs(host);
        other = newTabs(otherHost);
    }

    @AfterEach
    void close() {
        tabs.disposeAll();
        other.disposeAll();
        documents.forEach(OpenDocument::close);
    }

    private WindowTabs newTabs(RecordingWindowHost recording) {
        return new WindowTabs(recording,
                place -> new EditorView(place, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW),
                new SplitLayoutStore(preferences), () -> false);
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
        List<Component> uses = document.document().components().stream()
                .filter(component -> component.typeId().equals(SubcircuitTypes.typeIdOf(INVERTER)))
                .toList();
        return uses.get(index).id().value();
    }

    private EditorView enter(OpenDocument document, int instanceIndex) {
        return tabs.enter(document, INVERTER, Optional.of(instance(document, instanceIndex)), Optional.empty());
    }

    @Test
    void goToPartEntersWithTheContextAndSelectsThePart() {
        OpenDocument document = withTwoInverters();
        tabs.addTab(document, Optional.empty());

        EditorView shown = tabs.goToPart(document, Optional.of(INVERTER), Optional.of(instance(document, 1)),
                new ElementId("c2"));

        assertThat(tabs.activeView()).isSameAs(shown);
        assertThat(shown.openDocument().definitionId()).contains(INVERTER);
        assertThat(shown.openDocument().contextPath()).contains(instance(document, 1));
        assertThat(shown.selection().ids()).containsExactly(new ElementId("c2"));
    }

    @Test
    void goToPartOfTheCircuitActivatesItsMainTab() {
        OpenDocument document = withTwoInverters();
        EditorView main = tabs.addTab(document, Optional.empty());
        enter(document, 0);
        ElementId piece = new ElementId(instance(document, 1));

        EditorView shown = tabs.goToPart(document, Optional.empty(), Optional.empty(), piece);

        assertThat(shown).isSameAs(main);
        assertThat(tabs.activeView()).isSameAs(main);
        assertThat(main.selection().ids()).containsExactly(piece);
    }

    @Test
    void enterOpensOneTabPerDefinitionAndFocusesOnReentry() {
        OpenDocument document = withTwoInverters();
        tabs.addTab(document, Optional.empty());

        EditorView entered = enter(document, 0);

        assertThat(tabs.viewsOf(document)).hasSize(2);
        assertThat(tabs.activeView()).isSameAs(entered);
        OpenDocument place = entered.openDocument();
        assertThat(place.isBody()).isTrue();
        assertThat(place.parent()).containsSame(document);
        assertThat(place.contextPath()).contains(instance(document, 0));
        assertThat(tabs.model().visibleTabs()).extracting(tab -> tab.title()).containsExactly("Sin título", "Inversor");

        tabs.activate(tabs.viewsOf(document).get(0));
        EditorView reentered = enter(document, 1);

        assertThat(reentered).isSameAs(entered);
        assertThat(tabs.viewsOf(document)).hasSize(2);
        assertThat(tabs.activeView()).isSameAs(entered);
        assertThat(place.contextPath()).contains(instance(document, 1));
    }

    @Test
    void reentryReContextsTheMostRecentlyActiveTabOfTheDefinitionAndTheOtherKeepsItsOwn() {
        OpenDocument document = withTwoInverters();
        EditorView main = tabs.addTab(document, Optional.empty());
        EditorView first = enter(document, 0);
        EditorView second = tabs.newViewOfActivePlace();
        tabs.activate(first);
        tabs.activate(main);

        EditorView reentered = enter(document, 1);

        assertThat(reentered).isSameAs(first);
        assertThat(first.openDocument().contextPath()).contains(instance(document, 1));
        assertThat(second.openDocument().contextPath()).contains(instance(document, 0));
    }

    @Test
    void enterUnfoldsTheGroup() {
        OpenDocument document = withTwoInverters();
        tabs.addTab(document, Optional.empty());
        tabs.foldGroup(document, true);

        enter(document, 0);
        assertThat(tabs.isFolded(document)).isFalse();

        tabs.foldGroup(document, true);
        enter(document, 1);
        assertThat(tabs.isFolded(document)).isFalse();
    }

    @Test
    void enterBringsTheCircuitsGroupWhenTheWindowHasNone() {
        OpenDocument document = withTwoInverters();
        tabs.addTab(withTwoInverters(), Optional.empty());

        EditorView entered = enter(document, 0);

        assertThat(tabs.viewsOf(document)).hasSize(2);
        assertThat(tabs.mainTabCount(document)).isEqualTo(1);
        assertThat(tabs.activeView()).isSameAs(entered);
    }

    @Test
    void newViewOfABodyTabMakesASecondTabOfTheDefinitionWithItsOwnPlace() {
        OpenDocument document = withTwoInverters();
        tabs.addTab(document, Optional.empty());
        EditorView first = enter(document, 0);

        EditorView second = tabs.newViewOfActivePlace();

        assertThat(second.openDocument()).isNotSameAs(first.openDocument());
        assertThat(second.openDocument().document()).isSameAs(first.openDocument().document());
        assertThat(second.openDocument().contextPath()).contains(instance(document, 0));
        assertThat(tabs.model().visibleTabs()).extracting(tab -> tab.title())
                .containsExactly("Sin título", "Inversor (1)", "Inversor (2)"); // numbered as main tabs are
        second.openDocument().setContextPath(Optional.of(instance(document, 1)));
        assertThat(first.openDocument().contextPath()).contains(instance(document, 0));
    }

    @Test
    void closingAComponentTabReleasesItsBodyPlaceAndAsksNothing() {
        OpenDocument document = withTwoInverters();
        tabs.addTab(document, Optional.empty());
        int baseline = document.changeListenerCount();
        EditorView entered = enter(document, 0);
        OpenDocument place = entered.openDocument();
        assertThat(document.changeListenerCount()).isGreaterThan(baseline);

        tabs.closeTab(entered);

        assertThat(document.changeListenerCount()).isEqualTo(baseline);
        assertThat(place.changeListenerCount()).isZero();
        assertThat(place.refreshListenerCount()).isZero();
        assertThat(host.saveQuestions).isEmpty();
        assertThat(tabs.viewsOf(document)).hasSize(1);
    }

    @Test
    void closeComponentTabsReleasesEveryBodyPlace() {
        OpenDocument document = withTwoInverters();
        tabs.addTab(document, Optional.empty());
        int baseline = document.changeListenerCount();
        enter(document, 0);
        tabs.newViewOfActivePlace();

        tabs.closeComponentTabs(document);

        assertThat(document.changeListenerCount()).isEqualTo(baseline);
    }

    @Test
    void closingTheGroupReleasesItsBodyPlaces() {
        OpenDocument document = withTwoInverters();
        int baseline = document.changeListenerCount();
        EditorView main = tabs.addTab(document, Optional.empty());
        enter(document, 0);
        tabs.addTab(withTwoInverters(), Optional.empty());

        tabs.closeTab(main);

        assertThat(host.closedGroups).containsExactly(document);
        assertThat(document.changeListenerCount()).isEqualTo(baseline);
    }

    @Test
    void closingTheWindowReleasesItsBodyPlaces() {
        OpenDocument document = withTwoInverters();
        int baseline = document.changeListenerCount();
        tabs.addTab(document, Optional.empty());
        enter(document, 0);

        tabs.disposeAll();

        assertThat(document.changeListenerCount()).isEqualTo(baseline);
    }

    @Test
    void movingAGroupGivesTheTargetItsOwnBodyPlacesAndReleasesTheSourcesOnes() {
        OpenDocument document = withTwoInverters();
        tabs.addTab(document, Optional.empty());
        OpenDocument sourcePlace = enter(document, 1).openDocument();
        tabs.addTab(withTwoInverters(), Optional.empty());
        int before = document.changeListenerCount();

        tabs.moveGroupTo(document, other, 0);

        OpenDocument targetPlace = other.viewsOf(document).get(1).openDocument();
        assertThat(targetPlace).isNotSameAs(sourcePlace);
        assertThat(targetPlace.isBody()).isTrue();
        assertThat(targetPlace.definitionId()).contains(INVERTER);
        assertThat(targetPlace.contextPath()).contains(instance(document, 1));
        assertThat(sourcePlace.changeListenerCount()).isZero();
        assertThat(document.changeListenerCount()).isEqualTo(before);
    }

    @Test
    void aRemovedDefinitionClosesItsTabsWithANoticeOnTheMainTab() {
        OpenDocument document = withTwoInverters();
        EditorView main = tabs.addTab(document, Optional.empty());
        int baseline = document.changeListenerCount();
        enter(document, 0);
        tabs.newViewOfActivePlace();

        document.history().undo(); // the second instance
        document.history().undo(); // the first instance
        assertThat(tabs.viewsOf(document)).hasSize(3); // the definition is still in the circuit
        document.history().undo(); // the definition

        assertThat(tabs.viewsOf(document)).containsExactly(main);
        assertThat(tabs.activeView()).isSameAs(main);
        assertThat(main.statusBar().text())
                .contains("El componente Inversor ya no existe en el circuito; se cerró su pestaña");
        assertThat(document.changeListenerCount()).isEqualTo(baseline);
    }

    @Test
    void aRemovedDefinitionClosesItsTabsInEveryWindow() {
        OpenDocument document = withTwoInverters();
        tabs.addTab(document, Optional.empty());
        other.addTab(document, Optional.empty());
        enter(document, 0);
        other.enter(document, INVERTER, Optional.empty(), Optional.empty());

        document.history().undo();
        document.history().undo();
        document.history().undo();

        assertThat(tabs.viewsOf(document)).hasSize(1);
        assertThat(other.viewsOf(document)).hasSize(1);
    }

    @Test
    void showPlaceActivatesTheMainTabOrEntersTheDefinition() {
        OpenDocument document = withTwoInverters();
        EditorView main = tabs.addTab(document, Optional.empty());
        EditorView component = enter(document, 0);
        assertThat(tabs.activeView()).isSameAs(component);

        assertThat(tabs.showPlace(document, Optional.empty(), Optional.empty())).isSameAs(main);
        assertThat(tabs.activeView()).isSameAs(main);

        EditorView again = tabs.showPlace(document, Optional.of(INVERTER), Optional.of(instance(document, 1)));
        assertThat(again).isSameAs(component);
        assertThat(tabs.activeView()).isSameAs(component);
        assertThat(component.openDocument().contextPath()).contains(instance(document, 1));
    }

    @Test
    void showPlaceBringsTheCircuitsGroupToAWindowThatHasNone() {
        OpenDocument document = withTwoInverters();

        EditorView main = tabs.showPlace(document, Optional.empty(), Optional.empty());

        assertThat(tabs.viewsOf(document)).containsExactly(main);
        assertThat(tabs.activeView()).isSameAs(main);
    }
}
