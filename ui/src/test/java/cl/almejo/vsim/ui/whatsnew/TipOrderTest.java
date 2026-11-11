package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.app.AppPreferences;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** VSim remembers which tips were seen, not an index: the next one is the first unseen in file order. */
class TipOrderTest {

    private final AppPreferences preferences = AppPreferences.inMemory();

    @Test
    void theFirstTipComesFirstOnAFreshInstall() {
        TipOrder order = new TipOrder(List.of("look-inside", "tidy-circuit", "split-view"), preferences);

        assertThat(order.chooseFirst()).isZero();
    }

    @Test
    void theFirstUnseenTipComesNextSoATipAddedInANewerVersionComesFirst() {
        preferences.setSeenTips(Set.of("look-inside", "split-view"));
        TipOrder order = new TipOrder(List.of("look-inside", "new-in-0-9", "split-view"), preferences);

        assertThat(order.chooseFirst()).isEqualTo(1);
    }

    @Test
    void aTipCountsAsSeenWhenShown() {
        TipOrder order = new TipOrder(List.of("look-inside", "tidy-circuit"), preferences);

        order.shown(0);

        assertThat(preferences.seenTips()).containsExactly("look-inside");
        assertThat(order.chooseFirst()).isEqualTo(1);
    }

    @Test
    void onceEveryTipWasSeenTheRoundStartsAgainFromTheTopForgettingTheSet() {
        preferences.setSeenTips(Set.of("look-inside", "tidy-circuit", "a-removed-tip"));
        TipOrder order = new TipOrder(List.of("look-inside", "tidy-circuit"), preferences);

        assertThat(order.chooseFirst()).isZero();
        assertThat(preferences.seenTips()).isEmpty();
    }

    @Test
    void nextAndPreviousWalkTheFileOrderAndWrapAround() {
        TipOrder order = new TipOrder(List.of("look-inside", "tidy-circuit", "split-view"), preferences);

        assertThat(order.after(0)).isEqualTo(1);
        assertThat(order.after(2)).isZero();
        assertThat(order.before(0)).isEqualTo(2);
        assertThat(order.before(2)).isEqualTo(1);
    }
}
