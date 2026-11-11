package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.app.AppPreferences;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What one window may show when VSim starts: this release's bubbles, once per version, or else the tip of the day, at
 * most once a day and never on a first launch.
 */
class StartupNoticesTest {

    private final AppPreferences preferences = AppPreferences.inMemory();

    /** The bubbles alone: «Mostrar al iniciar» of the tip is off. */
    private StartupNotices notices(String running, boolean hasNews) {
        return bubblesOnly(running, hasNews, StartupNotices.NewsRule.ONCE_PER_VERSION);
    }

    private StartupNotices bubblesOnly(String running, boolean hasNews, StartupNotices.NewsRule rule) {
        preferences.setShowTipAtStart(false);
        return new StartupNotices(preferences, running, version -> hasNews, rule, StartupNotices.TipRule.ONCE_PER_DAY,
                () -> LocalDate.of(2026, 11, 10));
    }

    @Test
    void theDevelopmentSwitchShowsThemOnEveryLaunchButOnlyInTheFirstWindow() {
        preferences.setNewsShownVersion("0.8");
        StartupNotices always = bubblesOnly("0.8", true, StartupNotices.NewsRule.EVERY_LAUNCH);

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

    // --- the tip of the day ---

    private LocalDate today = LocalDate.of(2026, 11, 10);

    private StartupNotices withTip(boolean hasNews, StartupNotices.TipRule tipRule) {
        return new StartupNotices(preferences, "0.8", version -> hasNews, StartupNotices.NewsRule.ONCE_PER_VERSION,
                tipRule, () -> today);
    }

    private StartupNotices withTip(boolean hasNews) {
        return withTip(hasNews, StartupNotices.TipRule.ONCE_PER_DAY);
    }

    @Test
    void aLaterLaunchWithoutNewsShowsTheTip() {
        preferences.setNewsShownVersion("0.8");

        assertThat(withTip(true).claimForWindow()).contains(StartupNotices.Notice.TIP_OF_THE_DAY);
    }

    @Test
    void aFirstLaunchNeverShowsTheTip() {
        assertThat(withTip(false).claimForWindow()).isEmpty();
    }

    @Test
    void theBubblesWinOverTheTipOnTheSameStart() {
        preferences.setNewsShownVersion("0.7");

        assertThat(withTip(true).claimForWindow()).contains(StartupNotices.Notice.NEWS_BUBBLES);
    }

    @Test
    void theDayOfTheBubblesHasNoTipEvenAtALaterStart() {
        preferences.setNewsShownVersion("0.7");
        StartupNotices morning = withTip(true);
        assertThat(morning.claimForWindow()).contains(StartupNotices.Notice.NEWS_BUBBLES);
        morning.shown(StartupNotices.Notice.NEWS_BUBBLES);

        assertThat(withTip(true).claimForWindow()).isEmpty();
        today = today.plusDays(1);
        assertThat(withTip(true).claimForWindow()).contains(StartupNotices.Notice.TIP_OF_THE_DAY);
    }

    @Test
    void theTipShowsAtMostOnceACalendarDay() {
        preferences.setNewsShownVersion("0.8");
        StartupNotices morning = withTip(true);
        assertThat(morning.claimForWindow()).contains(StartupNotices.Notice.TIP_OF_THE_DAY);
        morning.shown(StartupNotices.Notice.TIP_OF_THE_DAY);

        assertThat(withTip(true).claimForWindow()).isEmpty();
        today = today.plusDays(1);
        assertThat(withTip(true).claimForWindow()).contains(StartupNotices.Notice.TIP_OF_THE_DAY);
    }

    @Test
    void aTipClaimedButNeverShownComesAgainTheSameDay() {
        preferences.setNewsShownVersion("0.8");
        assertThat(withTip(true).claimForWindow()).contains(StartupNotices.Notice.TIP_OF_THE_DAY);

        assertThat(withTip(true).claimForWindow()).contains(StartupNotices.Notice.TIP_OF_THE_DAY);
    }

    @Test
    void mostrarAlIniciarOffShowsNoTip() {
        preferences.setNewsShownVersion("0.8");
        preferences.setShowTipAtStart(false);

        assertThat(withTip(true).claimForWindow()).isEmpty();
    }

    @Test
    void theTipsDevelopmentSwitchShowsItAtEveryLaunchAlsoTheFirstButTheBubblesStillWin() {
        assertThat(withTip(false, StartupNotices.TipRule.EVERY_LAUNCH).claimForWindow())
                .contains(StartupNotices.Notice.TIP_OF_THE_DAY);
        StartupNotices shown = withTip(false, StartupNotices.TipRule.EVERY_LAUNCH);
        shown.claimForWindow();
        shown.shown(StartupNotices.Notice.TIP_OF_THE_DAY);
        assertThat(withTip(false, StartupNotices.TipRule.EVERY_LAUNCH).claimForWindow())
                .contains(StartupNotices.Notice.TIP_OF_THE_DAY);

        preferences.setNewsShownVersion("0.7");
        assertThat(withTip(true, StartupNotices.TipRule.EVERY_LAUNCH).claimForWindow())
                .contains(StartupNotices.Notice.NEWS_BUBBLES);
    }

    @Test
    void theTipsDevelopmentSwitchIsOffUnlessItsPropertySaysTrue() {
        assertThat(StartupNotices.TipRule.of(null)).isEqualTo(StartupNotices.TipRule.ONCE_PER_DAY);
        assertThat(StartupNotices.TipRule.of("false")).isEqualTo(StartupNotices.TipRule.ONCE_PER_DAY);
        assertThat(StartupNotices.TipRule.of("true")).isEqualTo(StartupNotices.TipRule.EVERY_LAUNCH);
    }

    @Test
    void theFirstWindowGetsTheSameAnswerEachTimeItAsksAndOtherWindowsNothing() {
        preferences.setNewsShownVersion("0.8");
        StartupNotices notices = withTip(true);
        Object first = new Object();

        assertThat(notices.claimForWindow(first)).contains(StartupNotices.Notice.TIP_OF_THE_DAY);
        assertThat(notices.claimForWindow(first)).contains(StartupNotices.Notice.TIP_OF_THE_DAY);
        assertThat(notices.claimForWindow(new Object())).isEmpty();
    }
}
