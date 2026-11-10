package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.app.AppPreferences;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** What one window may show when VSim starts: now only this release's bubbles, once per version. */
class StartupNoticesTest {

    private final AppPreferences preferences = AppPreferences.inMemory();

    private StartupNotices notices(String running, boolean hasNews) {
        return new StartupNotices(preferences, running, version -> hasNews, StartupNotices.NewsRule.ONCE_PER_VERSION);
    }

    @Test
    void theDevelopmentSwitchShowsThemOnEveryLaunchButOnlyInTheFirstWindow() {
        preferences.setNewsShownVersion("0.8");
        StartupNotices always = new StartupNotices(preferences, "0.8", version -> true,
                StartupNotices.NewsRule.EVERY_LAUNCH);

        assertThat(always.claimForWindow()).contains(StartupNotices.Notice.NEWS_BUBBLES);
        assertThat(always.claimForWindow()).isEmpty();
    }

    @Test
    void theDevelopmentSwitchIsOffUnlessItsPropertySaysTrue() {
        assertThat(StartupNotices.NewsRule.of(null)).isEqualTo(StartupNotices.NewsRule.ONCE_PER_VERSION);
        assertThat(StartupNotices.NewsRule.of("false")).isEqualTo(StartupNotices.NewsRule.ONCE_PER_VERSION);
        assertThat(StartupNotices.NewsRule.of("true")).isEqualTo(StartupNotices.NewsRule.EVERY_LAUNCH);
    }

    @Test
    void aFirstInstallShowsTheBubblesOnceAndThenStoresTheVersion() {
        StartupNotices first = notices("0.8", true);
        assertThat(first.isFirstLaunch()).isTrue();
        assertThat(first.claimForWindow()).contains(StartupNotices.Notice.NEWS_BUBBLES);
        assertThat(preferences.newsShownVersion()).isEmpty();

        first.shown(StartupNotices.Notice.NEWS_BUBBLES);

        assertThat(preferences.newsShownVersion()).contains("0.8");
        StartupNotices next = notices("0.8", true);
        assertThat(next.isFirstLaunch()).isFalse();
        assertThat(next.claimForWindow()).isEmpty();
    }

    @Test
    void theFirstLaunchStaysTheFirstForTheWholeRunAfterTheBubblesWereShown() {
        StartupNotices first = notices("0.8", true);
        first.claimForWindow();
        first.shown(StartupNotices.Notice.NEWS_BUBBLES);

        assertThat(first.isFirstLaunch()).isTrue(); // the tip of the day asks this: never on a first launch
    }

    @Test
    void aNewerVersionShowsItsBubblesOnce() {
        preferences.setNewsShownVersion("0.7");
        StartupNotices updated = notices("0.8", true);

        assertThat(updated.isFirstLaunch()).isFalse();
        assertThat(updated.claimForWindow()).contains(StartupNotices.Notice.NEWS_BUBBLES);
        updated.shown(StartupNotices.Notice.NEWS_BUBBLES);

        assertThat(notices("0.8", true).claimForWindow()).isEmpty();
    }

    @Test
    void theSameVersionNeverShowsThemAgain() {
        preferences.setNewsShownVersion("0.8");

        assertThat(notices("0.8", true).claimForWindow()).isEmpty();
    }

    @Test
    void anOlderVersionThanTheStoredOneShowsNothing() {
        preferences.setNewsShownVersion("0.9");

        assertThat(notices("0.8", true).claimForWindow()).isEmpty();
    }

    @Test
    void onlyTheFirstWindowGetsThem() {
        preferences.setNewsShownVersion("0.7");
        StartupNotices running = notices("0.8", true);

        assertThat(running.claimForWindow()).contains(StartupNotices.Notice.NEWS_BUBBLES);
        assertThat(running.claimForWindow()).isEmpty();
    }

    @Test
    void claimedButNeverShownTheyComeAgainAtTheNextStart() {
        preferences.setNewsShownVersion("0.7");
        assertThat(notices("0.8", true).claimForWindow()).isPresent(); // the AI kept working until VSim closed

        assertThat(notices("0.8", true).claimForWindow()).contains(StartupNotices.Notice.NEWS_BUBBLES);
    }

    @Test
    void aVersionWithoutNewsShowsNothingAndRemembersItWasSeen() {
        preferences.setNewsShownVersion("0.7");

        assertThat(notices("0.8", false).claimForWindow()).isEmpty();

        assertThat(preferences.newsShownVersion()).contains("0.8");
    }

    @Test
    void aBuildWithoutAVersionShowsNothingAndStoresNothing() {
        assertThat(notices("unknown", true).claimForWindow()).isEmpty();

        assertThat(preferences.newsShownVersion()).isEmpty();
    }

    @Test
    void anUnreadableStoredVersionCountsAsAnOlderOne() {
        preferences.setNewsShownVersion("garbage");

        assertThat(notices("0.8", true).claimForWindow()).contains(StartupNotices.Notice.NEWS_BUBBLES);
    }
}
