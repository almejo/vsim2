package cl.almejo.vsim.ui.window.tabs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TabStripModelTest {

    private static final Object COUNTER = "contador";
    private static final Object PROCESSOR = "lrv32im";

    private final TabStripModel model = new TabStripModel();

    private static TabStripModel.Tab main(String id, Object document) {
        return new TabStripModel.Tab(id, document, Optional.empty(), id, false);
    }

    private static TabStripModel.Tab component(String id, Object document, String definitionId) {
        return new TabStripModel.Tab(id, document, Optional.of(definitionId), id, false);
    }

    private List<String> visibleIds() {
        return model.visibleTabs().stream().map(TabStripModel.Tab::id).toList();
    }

    private List<String> idsOf(Object document) {
        return model.group(document).orElseThrow().tabs().stream().map(TabStripModel.Tab::id).toList();
    }

    @Test
    void addPutsAMainTabAfterTheLastMainTabOfItsGroup() {
        model.add(main("lrv", PROCESSOR));
        model.add(component("ram", PROCESSOR, "ram"));
        model.add(main("counter", COUNTER));
        model.add(main("lrv (2)", PROCESSOR));
        assertThat(idsOf(PROCESSOR)).containsExactly("lrv", "lrv (2)", "ram");
        assertThat(model.groups()).extracting(TabStripModel.Group::document).containsExactly(PROCESSOR, COUNTER);
    }

    @Test
    void addPutsAComponentTabAtTheEndOfItsGroup() {
        model.add(main("lrv", PROCESSOR));
        model.add(component("ram", PROCESSOR, "ram"));
        model.add(main("counter", COUNTER));
        model.add(component("alu", PROCESSOR, "alu"));
        assertThat(visibleIds()).containsExactly("lrv", "ram", "alu", "counter");
    }

    @Test
    void theFirstTabAddedIsActive() {
        model.add(main("lrv", PROCESSOR));
        model.add(main("counter", COUNTER));
        assertThat(model.active()).map(TabStripModel.Tab::id).contains("lrv");
    }

    @Test
    void removeOfTheLastMainTabRemovesTheGroup() {
        model.add(main("lrv", PROCESSOR));
        model.add(component("ram", PROCESSOR, "ram"));
        model.add(main("counter", COUNTER));
        List<Integer> heard = new ArrayList<>();
        model.addListener(() -> heard.add(model.groups().size()));
        model.remove("lrv");
        assertThat(model.group(PROCESSOR)).isEmpty();
        assertThat(visibleIds()).containsExactly("counter");
        assertThat(heard).isNotEmpty().last().isEqualTo(1);
        assertThat(model.active()).map(TabStripModel.Tab::id).contains("counter");
    }

    @Test
    void removeOfAComponentTabKeepsTheGroupAndActivatesItsNeighbour() {
        model.add(main("lrv", PROCESSOR));
        model.add(component("ram", PROCESSOR, "ram"));
        model.activate("ram");
        model.remove("ram");
        assertThat(idsOf(PROCESSOR)).containsExactly("lrv");
        assertThat(model.active()).map(TabStripModel.Tab::id).contains("lrv");
    }

    @Test
    void putGroupIntoAWindowThatHasTheDocumentMergesComponentTabsAndDropsTheMainOne() {
        model.add(main("lrv", PROCESSOR));
        model.add(component("ram", PROCESSOR, "ram"));
        TabStripModel.Group arriving = new TabStripModel.Group(PROCESSOR,
                List.of(main("lrv-other", PROCESSOR), component("alu", PROCESSOR, "alu")), false);
        model.putGroup(arriving, 0);
        assertThat(idsOf(PROCESSOR)).containsExactly("lrv", "ram", "alu");
        assertThat(model.groups()).hasSize(1);
    }

    @Test
    void putGroupOfANewDocumentGoesWhereItWasDropped() {
        model.add(main("lrv", PROCESSOR));
        TabStripModel.Group arriving = new TabStripModel.Group(COUNTER, List.of(main("counter", COUNTER)), true);
        model.putGroup(arriving, 0);
        assertThat(model.groups()).extracting(TabStripModel.Group::document).containsExactly(COUNTER, PROCESSOR);
        assertThat(model.isFolded(COUNTER)).isTrue();
    }

    @Test
    void takeGroupRemovesItAndHandsItBack() {
        model.add(main("lrv", PROCESSOR));
        model.add(component("ram", PROCESSOR, "ram"));
        model.add(main("counter", COUNTER));
        TabStripModel.Group taken = model.takeGroup(PROCESSOR);
        assertThat(taken.tabs()).extracting(TabStripModel.Tab::id).containsExactly("lrv", "ram");
        assertThat(model.groups()).extracting(TabStripModel.Group::document).containsExactly(COUNTER);
        assertThat(model.active()).map(TabStripModel.Tab::id).contains("counter");
    }

    @Test
    void foldHidesComponentTabsAndActivatesTheMainTabWhenAComponentWasActive() {
        model.add(main("lrv", PROCESSOR));
        model.add(component("ram", PROCESSOR, "ram"));
        model.add(component("alu", PROCESSOR, "alu"));
        model.activate("alu");
        model.fold(PROCESSOR, true);
        assertThat(visibleIds()).containsExactly("lrv");
        assertThat(model.active()).map(TabStripModel.Tab::id).contains("lrv");
        assertThat(model.componentTabCount(PROCESSOR)).isEqualTo(2);
        model.fold(PROCESSOR, false);
        assertThat(visibleIds()).containsExactly("lrv", "ram", "alu");
    }

    @Test
    void activatingAHiddenComponentTabUnfoldsItsGroup() {
        model.add(main("lrv", PROCESSOR));
        model.add(component("ram", PROCESSOR, "ram"));
        model.fold(PROCESSOR, true);
        model.activate("ram");
        assertThat(model.isFolded(PROCESSOR)).isFalse();
        assertThat(model.active()).map(TabStripModel.Tab::id).contains("ram");
    }

    @Test
    void nextAndPreviousWrapOverVisibleTabs() {
        model.add(main("lrv", PROCESSOR));
        model.add(component("ram", PROCESSOR, "ram"));
        model.add(main("counter", COUNTER));
        model.add(component("adder", COUNTER, "adder"));
        model.fold(PROCESSOR, true);
        model.activate("lrv");
        model.next();
        assertThat(model.active()).map(TabStripModel.Tab::id).contains("counter");
        model.next();
        model.next();
        assertThat(model.active()).map(TabStripModel.Tab::id).contains("lrv");
        model.previous();
        assertThat(model.active()).map(TabStripModel.Tab::id).contains("adder");
    }

    @Test
    void moveGroupReorders() {
        model.add(main("lrv", PROCESSOR));
        model.add(main("counter", COUNTER));
        model.add(main("alu", "alu"));
        model.moveGroup(PROCESSOR, 2);
        assertThat(model.groups()).extracting(TabStripModel.Group::document)
                .containsExactly(COUNTER, "alu", PROCESSOR);
        model.moveGroup(PROCESSOR, 0);
        assertThat(model.groups()).extracting(TabStripModel.Group::document)
                .containsExactly(PROCESSOR, COUNTER, "alu");
    }

    @Test
    void updateChangesTheTitleAndTheModifiedMarkOfATab() {
        model.add(main("lrv", PROCESSOR));
        model.update(new TabStripModel.Tab("lrv", PROCESSOR, Optional.empty(), "lrv32im", true));
        assertThat(model.tab("lrv")).hasValueSatisfying(tab -> {
            assertThat(tab.title()).isEqualTo("lrv32im");
            assertThat(tab.modified()).isTrue();
        });
    }
}
