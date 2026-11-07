package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.canvas.ViewState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A window of the last session: its tabs, the active one and the folded groups (spec §4.7). */
class WindowSessionTest {

    private static final ViewState VIEW = new ViewState(1.5, 10, -4);
    private static final WindowState BOUNDS = new WindowState(0, 0, 960, 1080, false, VIEW);
    private static final Path COUNTER = Path.of("/circuitos/contador.vsim");
    private static final Path ADDER = Path.of("/circuitos/sumador.vsim");

    private static final TabState COUNTER_MAIN = TabState.main(COUNTER, VIEW);
    private static final TabState ADDER_MAIN = TabState.main(ADDER, VIEW);
    private static final TabState COUNTER_RAM = TabState.component(COUNTER, "ram", "Ram", Optional.of("c5"), VIEW);

    @Test
    void aWindowNeedsATab() {
        assertThatThrownBy(() -> new WindowSession(BOUNDS, List.of(), 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anActiveTabOutOfRangeIsTheFirst() {
        assertThat(new WindowSession(BOUNDS, List.of(COUNTER_MAIN, ADDER_MAIN), 7, List.of()).activeTab()).isZero();
        assertThat(new WindowSession(BOUNDS, List.of(COUNTER_MAIN, ADDER_MAIN), -1, List.of()).activeTab()).isZero();
        assertThat(new WindowSession(BOUNDS, List.of(COUNTER_MAIN, ADDER_MAIN), 1, List.of()).activeTab()).isOne();
    }

    @Test
    void documentsAreEachFileOnceInTheOrderOfItsFirstTab() {
        WindowSession window = new WindowSession(BOUNDS, List.of(COUNTER_MAIN, ADDER_MAIN, COUNTER_RAM), 0,
                List.of());
        assertThat(window.documents()).containsExactly(COUNTER, ADDER);
    }

    @Test
    void keepingDropsTheOtherFilesTabsAndFoldsAndFollowsTheActiveTab() {
        WindowSession window = new WindowSession(BOUNDS, List.of(ADDER_MAIN, COUNTER_MAIN, COUNTER_RAM), 2,
                List.of(ADDER, COUNTER));

        assertThat(window.keeping(COUNTER::equals)).contains(
                new WindowSession(BOUNDS, List.of(COUNTER_MAIN, COUNTER_RAM), 1, List.of(COUNTER)));
        assertThat(window.keeping(ADDER::equals)).contains(
                new WindowSession(BOUNDS, List.of(ADDER_MAIN), 0, List.of(ADDER)));
        assertThat(window.keeping(path -> false)).isEmpty();
    }
}
