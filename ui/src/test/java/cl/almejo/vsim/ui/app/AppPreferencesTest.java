package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.print.PageOrientation;
import cl.almejo.vsim.ui.print.Paper;
import cl.almejo.vsim.ui.print.PrintOptions;
import cl.almejo.vsim.ui.print.PrintScope;
import cl.almejo.vsim.ui.print.ScaleMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.AbstractPreferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppPreferencesTest {

    private final MemoryPreferences node = new MemoryPreferences();
    private final AppPreferences preferences = new AppPreferences(node);
    private final AtomicInteger changes = new AtomicInteger();
    private final Path home = Path.of("/home/ana");

    @Test
    void defaultsFollowTheSystem() {
        assertThat(preferences.theme()).isEqualTo(AppPreferences.Theme.SYSTEM);
        assertThat(preferences.colorScheme()).isEmpty();
        assertThat(preferences.language()).isEmpty();
        assertThat(preferences.usesDefaultLibraryDirectory()).isTrue();
    }

    @Test
    void theTextFontIsSansSerifTenUntilChosenAndOneChangeNotifiesOnce() {
        preferences.addListener(changes::incrementAndGet);
        assertThat(preferences.textFont()).isEqualTo(TextFont.DEFAULT);

        preferences.setTextFont(new TextFont("Serif", 14));
        assertThat(preferences.textFont()).isEqualTo(new TextFont("Serif", 14));
        assertThat(node.get("textFontFamily", null)).isEqualTo("Serif");
        assertThat(node.get("textFontSize", null)).isEqualTo("14");
        assertThat(changes).hasValue(1);

        preferences.setTextFont(new TextFont("Serif", 14));
        assertThat(changes).hasValue(1);
    }

    @Test
    void anUnreadableStoredTextFontSizeFallsBackToTheDefaultAndAMissingFamilyIsKept() {
        AppPreferences stored = new AppPreferences(new FixedPreferences(
                Map.of("textFontFamily", "Fuente De Otro Computador", "textFontSize", "99")));
        assertThat(stored.textFont()).isEqualTo(new TextFont("Fuente De Otro Computador", 10));
        AppPreferences garbage = new AppPreferences(new FixedPreferences(Map.of("textFontSize", "grande")));
        assertThat(garbage.textFont()).isEqualTo(TextFont.DEFAULT);
    }

    @Test
    void aStoredLibraryDirectoryThatIsNotAPathFallsBackToTheDefault() {
        AppPreferences unparseable = new AppPreferences(new FixedPreferences(
                Map.of(AppPreferences.LIBRARY_DIRECTORY, "components\u0000broken")));
        AppPreferences unset = new AppPreferences(new MemoryPreferences());
        assertThat(unparseable.libraryDirectory()).isEqualTo(unset.libraryDirectory());
    }

    @Test
    void theFindOptionsAreOffByDefaultRoundTripAndOnlyRealChangesNotify() {
        preferences.addListener(changes::incrementAndGet);
        assertThat(preferences.findMatchCase()).isFalse();
        assertThat(preferences.findRegex()).isFalse();
        preferences.setFindMatchCase(false);
        preferences.setFindRegex(false);
        assertThat(changes).hasValue(0);

        preferences.setFindMatchCase(true);
        preferences.setFindRegex(true);

        assertThat(changes).hasValue(2);
        AppPreferences reread = new AppPreferences(node);
        assertThat(reread.findMatchCase()).isTrue();
        assertThat(reread.findRegex()).isTrue();
    }

    @Test
    void simplifyingExpressionsIsOnByDefaultAndOnlyRealChangesNotify() {
        preferences.addListener(changes::incrementAndGet);
        assertThat(preferences.simplifyExpressions()).isTrue();
        preferences.setSimplifyExpressions(true);
        assertThat(changes).hasValue(0);

        preferences.setSimplifyExpressions(false);

        assertThat(changes).hasValue(1);
        assertThat(new AppPreferences(node).simplifyExpressions()).isFalse();
    }

    @Test
    void animatingViewMovesIsOnByDefaultAndOnlyRealChangesNotify() {
        preferences.addListener(changes::incrementAndGet);
        assertThat(preferences.animateViewMoves()).isTrue();
        preferences.setAnimateViewMoves(true);
        assertThat(changes).hasValue(0);

        preferences.setAnimateViewMoves(false);
        assertThat(preferences.animateViewMoves()).isFalse();
        assertThat(node.get("animateViewMoves", null)).isEqualTo("false");
        assertThat(changes).hasValue(1);
        assertThat(new AppPreferences(node).animateViewMoves()).isFalse();

        preferences.setAnimateViewMoves(true);
        assertThat(preferences.animateViewMoves()).isTrue();
        assertThat(changes).hasValue(2);
    }

    @Test
    void whenTheSystemAsksForReducedMotionViewMovesStartOffUntilTheUserChooses() {
        AppPreferences reduced = new AppPreferences(new MemoryPreferences(), () -> true);
        assertThat(reduced.animateViewMoves()).isFalse();
        reduced.setAnimateViewMoves(true);
        assertThat(reduced.animateViewMoves()).isTrue();
    }

    @Test
    void reducedMotionReadsTheDesktopAnimationsSetting() {
        assertThat(ReducedMotion.requested(name -> 0)).isTrue();
        assertThat(ReducedMotion.requested(name -> 1)).isFalse();
        assertThat(ReducedMotion.requested(name -> Boolean.FALSE)).isTrue();
        assertThat(ReducedMotion.requested(name -> null)).isFalse();
        assertThat(ReducedMotion.requested(name -> "whatever")).isFalse();
    }

    @Test
    void restoringTheSessionIsOnByDefaultAndOnlyRealChangesNotify() {
        preferences.addListener(changes::incrementAndGet);
        assertThat(preferences.restoreSession()).isTrue();
        preferences.setRestoreSession(true);
        assertThat(changes).hasValue(0);

        preferences.setRestoreSession(false);
        assertThat(preferences.restoreSession()).isFalse();
        assertThat(node.get("restoreSession", null)).isEqualTo("false");
        assertThat(changes).hasValue(1);

        preferences.setRestoreSession(true);
        assertThat(preferences.restoreSession()).isTrue();
        assertThat(changes).hasValue(2);
    }

    @Test
    void theStepSizeIsTenByDefaultAndTakesOneToABillion() {
        preferences.addListener(changes::incrementAndGet);
        assertThat(preferences.stepUnits()).isEqualTo(AppPreferences.DEFAULT_STEP_UNITS).isEqualTo(10);

        preferences.setStepUnits(1_000);
        assertThat(preferences.stepUnits()).isEqualTo(1_000);
        assertThat(node.get("simulation.stepUnits", null)).isEqualTo("1000");
        assertThat(changes).hasValue(1);
        preferences.setStepUnits(1_000);
        assertThat(changes).as("only real changes notify").hasValue(1);

        assertThatThrownBy(() -> preferences.setStepUnits(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> preferences.setStepUnits(AppPreferences.MAXIMUM_STEP_UNITS + 1))
                .isInstanceOf(IllegalArgumentException.class);
        node.put("simulation.stepUnits", "many");
        assertThat(preferences.stepUnits()).as("unreadable").isEqualTo(10);
        node.put("simulation.stepUnits", "0");
        assertThat(preferences.stepUnits()).as("out of range").isEqualTo(10);
    }

    @Test
    void theDataDirectoryIsTheParentOfTheDefaultLibraryOnEveryPlatform() {
        assertThat(AppPreferences.dataDirectory("Linux", Map.of(), home)).isEqualTo(home.resolve(".local/share/vsim2"));
        assertThat(AppPreferences.dataDirectory("Linux", Map.of("XDG_DATA_HOME", "/data"), home))
                .isEqualTo(Path.of("/data/vsim2"));
        assertThat(AppPreferences.dataDirectory("Mac OS X", Map.of(), home))
                .isEqualTo(home.resolve("Library/Application Support/VSim2"));
        assertThat(AppPreferences.dataDirectory("Windows 11", Map.of("APPDATA", "/appdata"), home))
                .isEqualTo(Path.of("/appdata/VSim2"));
    }

    @Test
    void theDataDirectoryIgnoresACustomLibraryFolder() {
        Path before = preferences.dataDirectory();
        preferences.setLibraryDirectory(Optional.of(Path.of("/elsewhere/components")));
        assertThat(preferences.dataDirectory()).isEqualTo(before);
        assertThat(before).isEqualTo(AppPreferences.dataDirectory(System.getProperty("os.name", ""), System.getenv(),
                Path.of(System.getProperty("user.home"))));
    }

    @Test
    void recentFilesAndTheSessionLiveInChildNodes() throws Exception {
        preferences.recentNode().node("0").put("path", "/a.vsim");
        preferences.sessionNode().node("0").node("0").put("x", "1");
        assertThat(node.childrenNames()).contains("recent", "session");
        assertThat(node.node("session").node("0").node("0").get("x", null)).isEqualTo("1");
    }

    @Test
    void settersRejectNull() {
        preferences.addListener(changes::incrementAndGet);
        assertThatThrownBy(() -> preferences.setTheme(null)).isInstanceOf(NullPointerException.class)
                .hasMessage("theme");
        assertThatThrownBy(() -> preferences.setColorScheme(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> preferences.setLanguage(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> preferences.setLibraryDirectory(null)).isInstanceOf(NullPointerException.class);
        assertThat(preferences.theme()).isEqualTo(AppPreferences.Theme.SYSTEM);
        assertThat(changes).hasValue(0);
    }

    /** Read-only values that {@link java.util.prefs.Preferences#put} would refuse to store (it rejects U+0000). */
    private static final class FixedPreferences extends AbstractPreferences {
        private final Map<String, String> values;

        FixedPreferences(Map<String, String> values) {
            super(null, "");
            this.values = values;
        }

        @Override protected void putSpi(String key, String value) { throw new UnsupportedOperationException(); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void removeSpi(String key) { throw new UnsupportedOperationException(); }
        @Override protected void removeNodeSpi() { throw new UnsupportedOperationException(); }
        @Override protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { throw new UnsupportedOperationException(); }
        @Override protected void syncSpi() { }
        @Override protected void flushSpi() { }
    }

    @Test
    void removedListenersAreCountedOutAndHearNothing() {
        Runnable listener = changes::incrementAndGet;
        preferences.addListener(listener);
        assertThat(preferences.listenerCount()).isEqualTo(1);
        preferences.removeListener(listener);
        assertThat(preferences.listenerCount()).isZero();
        preferences.setTheme(AppPreferences.Theme.DARK);
        assertThat(changes).hasValue(0);
    }

    @Test
    void aFailingListenerDoesNotKeepTheOthersFromHearingTheChange() {
        preferences.addListener(() -> {
            throw new IllegalStateException("broken listener");
        });
        preferences.addListener(changes::incrementAndGet);

        preferences.setTheme(AppPreferences.Theme.DARK);

        assertThat(changes).hasValue(1);
        assertThat(new AppPreferences(node).theme()).isEqualTo(AppPreferences.Theme.DARK);
    }

    @Test
    void valuesAreStoredAndListenersHearOnlyRealChanges() {
        preferences.addListener(changes::incrementAndGet);
        preferences.setTheme(AppPreferences.Theme.DARK);
        preferences.setTheme(AppPreferences.Theme.DARK);
        preferences.setColorScheme(Optional.of("classic"));
        preferences.setLanguage(Optional.of("en"));
        preferences.setLibraryDirectory(Optional.of(Path.of("/data/components")));

        AppPreferences reread = new AppPreferences(node);
        assertThat(reread.theme()).isEqualTo(AppPreferences.Theme.DARK);
        assertThat(reread.colorScheme()).contains("classic");
        assertThat(reread.language()).contains("en");
        assertThat(reread.libraryDirectory()).isEqualTo(Path.of("/data/components"));
        assertThat(reread.usesDefaultLibraryDirectory()).isFalse();
        assertThat(changes).hasValue(4);

        preferences.setColorScheme(Optional.empty());
        preferences.setLibraryDirectory(Optional.empty());
        assertThat(preferences.colorScheme()).isEmpty();
        assertThat(preferences.usesDefaultLibraryDirectory()).isTrue();
        assertThat(changes).hasValue(6);
    }

    @Test
    void settingTheCurrentThemeStoresNothingAndNotifiesNoListener() {
        preferences.addListener(changes::incrementAndGet);
        preferences.setTheme(AppPreferences.Theme.SYSTEM);
        assertThat(node.get(AppPreferences.THEME, null)).isNull();
        assertThat(changes).hasValue(0);
    }

    @Test
    void anUnknownStoredThemeFallsBackToTheSystemOne() {
        node.put(AppPreferences.THEME, "NEON");
        assertThat(preferences.theme()).isEqualTo(AppPreferences.Theme.SYSTEM);
    }

    @Test
    void defaultLibraryDirectoryFollowsEachPlatform() {
        assertThat(AppPreferences.defaultLibraryDirectory("Linux", Map.of(), home))
                .isEqualTo(Path.of("/home/ana/.local/share/vsim2/components"));
        assertThat(AppPreferences.defaultLibraryDirectory("Linux", Map.of("XDG_DATA_HOME", "/xdg"), home))
                .isEqualTo(Path.of("/xdg/vsim2/components"));
        assertThat(AppPreferences.defaultLibraryDirectory("Mac OS X", Map.of(), home))
                .isEqualTo(Path.of("/home/ana/Library/Application Support/VSim2/components"));
        assertThat(AppPreferences.defaultLibraryDirectory("Windows 11", Map.of("APPDATA", "/appdata"), home))
                .isEqualTo(Path.of("/appdata/VSim2/components"));
        assertThat(AppPreferences.defaultLibraryDirectory("Windows 11", Map.of(), home))
                .isEqualTo(Path.of("/home/ana/AppData/Roaming/VSim2/components"));
        assertThat(AppPreferences.defaultLibraryDirectory("Darwin", Map.of("APPDATA", "/appdata"), home))
                .isEqualTo(Path.of("/home/ana/.local/share/vsim2/components"));
    }

    @Test
    void shortcutOverridesLiveInTheirOwnNodeAndAChangeNotifiesOnce() throws Exception {
        preferences.addListener(changes::incrementAndGet);
        preferences.shortcutsNode().put("file.save", "ctrl pressed G");
        assertThat(changes).hasValue(0);
        preferences.shortcutsChanged();
        assertThat(changes).hasValue(1);
        assertThat(node.nodeExists("shortcuts")).isTrue();
        assertThat(node.node("shortcuts").get("file.save", null)).isEqualTo("ctrl pressed G");
        assertThat(preferences.theme()).isEqualTo(AppPreferences.Theme.SYSTEM);
    }

    @Test
    void snapAndGuidePreferencesHaveTheirDefaultsAndRoundTripAndNotify() {
        preferences.addListener(changes::incrementAndGet);
        assertThat(preferences.snapToAlignments()).isTrue();
        assertThat(preferences.snapToParts()).isTrue();
        assertThat(preferences.snapToGuides()).isTrue();
        assertThat(preferences.snapToGridLines()).isFalse();
        assertThat(preferences.showGuides()).isTrue();
        assertThat(preferences.gridStyle()).isEqualTo(AppPreferences.GridStyle.DOTS);

        preferences.setSnapToAlignments(false);
        preferences.setSnapToAlignments(false);
        preferences.setSnapToParts(false);
        preferences.setSnapToGuides(false);
        preferences.setSnapToGridLines(true);
        preferences.setShowGuides(false);
        preferences.setGridStyle(AppPreferences.GridStyle.LINES);
        preferences.setGridStyle(AppPreferences.GridStyle.LINES);
        assertThat(changes).hasValue(6);

        AppPreferences reloaded = new AppPreferences(node);
        assertThat(reloaded.snapToAlignments()).isFalse();
        assertThat(reloaded.snapToParts()).isFalse();
        assertThat(reloaded.snapToGuides()).isFalse();
        assertThat(reloaded.snapToGridLines()).isTrue();
        assertThat(reloaded.showGuides()).isFalse();
        assertThat(reloaded.gridStyle()).isEqualTo(AppPreferences.GridStyle.LINES);

        node.put("grid.style", "SQUARES");
        assertThat(preferences.gridStyle()).isEqualTo(AppPreferences.GridStyle.DOTS);
    }

    @Test
    void wiresFollowIsOnAndTheInvertKeyIsShiftByDefaultAndBothRoundTripAndNotify() {
        preferences.addListener(changes::incrementAndGet);
        assertThat(preferences.wiresFollow()).isTrue();
        assertThat(preferences.invertModifier()).isEqualTo(AppPreferences.InvertModifier.SHIFT);

        preferences.setWiresFollow(false);
        preferences.setWiresFollow(false);
        preferences.setInvertModifier(AppPreferences.InvertModifier.ALT);
        assertThat(changes).hasValue(2);
        assertThat(new AppPreferences(node).wiresFollow()).isFalse();
        assertThat(new AppPreferences(node).invertModifier()).isEqualTo(AppPreferences.InvertModifier.ALT);

        preferences.setInvertModifier(AppPreferences.InvertModifier.NONE);
        assertThat(preferences.invertModifier()).isEqualTo(AppPreferences.InvertModifier.NONE);
    }

    @Test
    void anUnknownStoredInvertKeyFallsBackToShift() {
        node.put("invertModifier", "HYPER");
        assertThat(preferences.invertModifier()).isEqualTo(AppPreferences.InvertModifier.SHIFT);
    }

    @Test
    void theInvertKeyIsHeldOnlyWhenItsOwnKeyIsDown() {
        AppPreferences.InvertModifier shift = AppPreferences.InvertModifier.SHIFT;
        AppPreferences.InvertModifier alt = AppPreferences.InvertModifier.ALT;
        AppPreferences.InvertModifier none = AppPreferences.InvertModifier.NONE;
        assertThat(shift.isHeld(true, false)).isTrue();
        assertThat(shift.isHeld(false, true)).isFalse();
        assertThat(shift.isHeld(false, false)).isFalse();
        assertThat(alt.isHeld(false, true)).isTrue();
        assertThat(alt.isHeld(true, false)).isFalse();
        assertThat(none.isHeld(true, true)).isFalse();
    }

    // --- Exportar a PDF e imprimir ---

    @Test
    void thePdfOptionsStartAsTheDefaultsWithTheGivenTitle() {
        assertThat(preferences.printOptions("Sumador")).isEqualTo(PrintOptions.defaults("Sumador"));
        assertThat(preferences.printDirectory()).isEmpty();
    }

    @Test
    void thePdfOptionsAreRememberedButNotTheScopeNorTheTitle() {
        preferences.setPrintOptions(new PrintOptions(new PrintScope.Selection(java.util.Set.of()), Paper.OFICIO,
                PageOrientation.LANDSCAPE, new ScaleMode.Percentage(75), false, true, true, "Mi título"));
        preferences.setPrintDirectory(home.resolve("pdf"));
        AppPreferences reread = new AppPreferences(node);
        assertThat(reread.printOptions("Otro")).isEqualTo(new PrintOptions(new PrintScope.WholeCircuit(),
                Paper.OFICIO, PageOrientation.LANDSCAPE, new ScaleMode.Percentage(75), false, true, true, "Otro"));
        assertThat(reread.printDirectory()).contains(home.resolve("pdf").toAbsolutePath());
        assertThat(node.get("print.paper", null)).isEqualTo("OFICIO");
        assertThat(node.get("print.scale", null)).isEqualTo("75");
    }

    @Test
    void fitToOnePageIsStoredAsFit() {
        preferences.setPrintOptions(new PrintOptions(new PrintScope.WholeCircuit(), Paper.A3,
                PageOrientation.PORTRAIT, new ScaleMode.FitToOnePage(), true, false, false, ""));
        assertThat(node.get("print.scale", null)).isEqualTo("fit");
        assertThat(preferences.printOptions("").scaleMode()).isEqualTo(new ScaleMode.FitToOnePage());
    }

    @Test
    void unknownOrCorruptPdfValuesFallBackToTheDefaults() {
        node.put("print.paper", "TABLOID");
        node.put("print.orientation", "SIDEWAYS");
        node.put("print.scale", "9000");
        node.put("print.withValues", "maybe");
        node.put("print.blackAndWhite", "maybe");
        node.put("print.headerFooter", "maybe");
        assertThat(preferences.printOptions("C")).isEqualTo(PrintOptions.defaults("C"));
        node.put("print.scale", "many");
        assertThat(preferences.printOptions("C").scaleMode()).isEqualTo(new ScaleMode.FitToOnePage());
    }
}
