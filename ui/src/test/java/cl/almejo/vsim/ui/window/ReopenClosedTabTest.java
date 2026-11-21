package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.session.WindowState;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.window.closed.ClosedTabs;
import cl.almejo.vsim.ui.window.closed.ClosedWindow;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ventana ▸ «Reabrir pestaña cerrada» (Ctrl+Mayús+T, card «ctrl-t»): what comes back for each kind of close, where,
 * and the notices when something cannot come back as it was. A frame cannot be built headless, so the windows are
 * {@link WindowTabs} with recording hosts and the application's part is a recording {@link ClosedTabReopener.Windows}
 * that reads the files for real.
 */
class ReopenClosedTabTest {

    private static final String INVERTER = ExampleDefinitions.INVERTER;
    private static final ViewState MAIN_VIEW = new ViewState(1.5, 40, -20);
    private static final ViewState INSIDE_VIEW = new ViewState(2.0, 10, 5);
    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final ZonedDateTime SAVED_AT = ZonedDateTime.of(2026, 11, 21, 14, 32, 0, 0, ZONE);
    private static final KeyStroke REOPEN_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_T,
            InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<OpenDocument> documents = new ArrayList<>();
    private final ClosedTabs closedTabs = new ClosedTabs();
    private final RecordingWindows windows = new RecordingWindows();
    private final RecordingWindowHost host = new RecordingWindowHost();
    private final RecordingWindowHost otherHost = new RecordingWindowHost();
    private Clock clock = Clock.fixed(SAVED_AT.plusHours(1).toInstant(), ZONE);
    private Optional<KeyStroke> reopenKey = Optional.of(REOPEN_KEY);
    private WindowTabs tabs;
    private WindowTabs other;

    @TempDir
    Path folder;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        tabs = newWindow(host);
        other = newWindow(otherHost);
    }

    @AfterEach
    void close() {
        windows.open.forEach(WindowTabs::disposeAll);
        windows.created.forEach(WindowTabs::disposeAll);
        documents.forEach(OpenDocument::close);
    }

    /** A window whose closes go to the stack, and whose last close of a circuit closes it, as the application does. */
    private WindowTabs newWindow(RecordingWindowHost recording) {
        WindowTabs window = new WindowTabs(recording,
                place -> new EditorView(place, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW),
                new SplitLayoutStore(preferences), () -> false);
        recording.onClosedByUser = closedTabs::push;
        recording.onGroupClosed = document -> {
            if (windows.open.stream().noneMatch(open -> open != window && open.documents().contains(document))) {
                closeDocument(document);
            }
        };
        windows.open.add(window);
        return window;
    }

    private void closeDocument(OpenDocument document) {
        closedTabs.documentClosed(document);
        document.close();
    }

    private void reopen(WindowTabs requester) {
        new ClosedTabReopener(closedTabs, windows, clock, () -> reopenKey).reopen(requester);
    }

    private OpenDocument newDocument() {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        documents.add(document);
        return document;
    }

    /** Another circuit of the window, with something drawn (an untouched one would make way, as for Abrir). */
    private OpenDocument otherCircuit() {
        OpenDocument document = newDocument();
        modify(document);
        return document;
    }

    /** A saved circuit with the inverter placed twice, its file dated {@link #SAVED_AT}. */
    private OpenDocument savedWithInverters(String name) throws Exception {
        OpenDocument document = newDocument();
        document.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        place(document, SubcircuitTypes.typeIdOf(INVERTER), 0, 0, Map.of());
        place(document, SubcircuitTypes.typeIdOf(INVERTER), 20, 0, Map.of());
        document.save(folder.resolve(name));
        Files.setLastModifiedTime(folder.resolve(name), FileTime.from(SAVED_AT.toInstant()));
        return document;
    }

    private static String instance(OpenDocument document, int index) {
        List<Component> uses = document.document().components().stream()
                .filter(component -> component.typeId().equals(SubcircuitTypes.typeIdOf(INVERTER)))
                .toList();
        return uses.get(index).id().value();
    }

    private static void modify(OpenDocument document) {
        place(document, StandardComponentTypes.BUFFER, 40, 40, Map.of(StandardComponentTypes.NEGATE, true));
    }

    private EditorView componentTab(WindowTabs window, OpenDocument document, int instance) {
        return window.addComponentTab(document, INVERTER,
                document.openBody(INVERTER, Optional.of(instance(document, instance))), Optional.of(INSIDE_VIEW));
    }

    private static Optional<ViewState> initialView(EditorView view) {
        return Optional.of(view.canvas().viewState());
    }

    // --- a tab ---

    @Test
    void aClosedComponentTabComesBackInItsPlaceWithItsViewAndItsInstance() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        EditorView first = componentTab(tabs, counter, 0);
        EditorView second = componentTab(tabs, counter, 1);
        tabs.closeTab(first);
        tabs.activate(second);

        reopen(tabs);

        List<EditorView> group = tabs.viewsOf(counter);
        assertThat(group).hasSize(3);
        EditorView back = group.get(1);
        assertThat(tabs.activeView()).isSameAs(back);
        assertThat(back.openDocument().definitionId()).contains(INVERTER);
        assertThat(back.openDocument().contextPath()).contains(instance(counter, 0));
        assertThat(tabs.session(WindowState.of(new java.awt.Rectangle(0, 0, 800, 600), false, MAIN_VIEW))
                .orElseThrow().tabs().get(1).view()).isEqualTo(INSIDE_VIEW);
        assertThat(closedTabs.isEmpty()).isTrue();
        assertThat(windows.notices).isEmpty();
    }

    @Test
    void aTabWhoseWindowClosedComesBackInTheWindowWherePressed() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        other.addTab(counter, Optional.of(MAIN_VIEW));
        tabs.closeTab(componentTab(tabs, counter, 1));
        windows.open.remove(tabs);
        tabs.disposeAll();

        reopen(other);

        assertThat(other.viewsOf(counter)).hasSize(2);
        assertThat(other.activeView().openDocument().contextPath()).contains(instance(counter, 1));
    }

    @Test
    void aComponentTabWhoseCircuitClosedOpensTheCircuitWithThatTabActive() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        Path file = counter.path().orElseThrow();
        tabs.addTab(otherCircuit(), Optional.empty());
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        tabs.closeTab(componentTab(tabs, counter, 1));
        tabs.closeGroup(counter);
        closedTabs.pop(); // the group's own entry: only the component tab is left

        reopen(tabs);

        OpenDocument reopened = tabs.activeView().openDocument().parent().orElseThrow();
        assertThat(reopened).isNotSameAs(counter);
        assertThat(reopened.path()).contains(file);
        assertThat(tabs.viewsOf(reopened)).hasSize(2);
        assertThat(tabs.activeView().openDocument().definitionId()).contains(INVERTER);
        assertThat(tabs.activeView().openDocument().contextPath()).contains(instance(reopened, 1));
        assertThat(windows.read).containsExactly(file);
    }

    // --- a group ---

    @Test
    void aClosedSavedGroupComesBackWholeInItsPlaceInTheStrip() throws Exception {
        OpenDocument first = otherCircuit();
        OpenDocument counter = savedWithInverters("contador.vsim");
        OpenDocument last = otherCircuit();
        tabs.addTab(first, Optional.empty());
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        componentTab(tabs, counter, 0);
        EditorView second = componentTab(tabs, counter, 1);
        tabs.addTab(last, Optional.empty());
        tabs.activate(second);
        tabs.closeGroup(counter);
        tabs.activate(tabs.viewsOf(last).getFirst());

        reopen(tabs);

        assertThat(tabs.documents()).hasSize(3);
        OpenDocument reopened = tabs.documents().get(1);
        assertThat(reopened.path()).isEqualTo(counter.path());
        List<EditorView> group = tabs.viewsOf(reopened);
        assertThat(group).hasSize(3);
        assertThat(group.get(1).openDocument().contextPath()).contains(instance(reopened, 0));
        assertThat(group.get(2).openDocument().contextPath()).contains(instance(reopened, 1));
        assertThat(tabs.activeView()).isSameAs(group.get(2));
        assertThat(windows.notices).isEmpty();
    }

    @Test
    void aGroupClosedWithNoGuardarComesBackAsSavedAndSaysTheChangesWereLost() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        int savedParts = counter.document().components().size();
        tabs.addTab(otherCircuit(), Optional.empty());
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        modify(counter);
        host.answerToSave = true; // «No guardar»: the question answers yes without saving
        tabs.closeGroup(counter);

        reopen(tabs);

        OpenDocument reopened = tabs.documents().get(1);
        assertThat(reopened.document().components()).hasSize(savedParts);
        assertThat(reopened.isModified()).isFalse();
        assertThat(windows.notices).containsExactly("«contador» volvió como estaba guardado (hoy, 14:32). "
                + "Los cambios sin guardar se perdieron al cerrarlo.");
        assertThat(windows.noticeDurations).containsExactly(ClosedTabReopener.LONG_NOTICE);
    }

    @Test
    void theSavedTimeOfAnotherDayCarriesItsDate() throws Exception {
        clock = Clock.fixed(SAVED_AT.plusDays(3).toInstant(), ZONE);
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(otherCircuit(), Optional.empty());
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        modify(counter);
        tabs.closeGroup(counter);

        reopen(tabs);

        assertThat(windows.notices).containsExactly("«contador» volvió como estaba guardado (21/11, 14:32). "
                + "Los cambios sin guardar se perdieron al cerrarlo.");
    }

    @Test
    void aNeverSavedCircuitCannotComeBackAndTheNextPressReopensThePreviousEntry() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        OpenDocument untitled = newDocument();
        tabs.addTab(otherCircuit(), Optional.empty());
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        tabs.closeTab(componentTab(tabs, counter, 0));
        tabs.addTab(untitled, Optional.empty());
        modify(untitled);
        tabs.closeGroup(untitled);
        List<OpenDocument> before = tabs.documents();

        reopen(tabs);

        assertThat(tabs.documents()).isEqualTo(before);
        assertThat(windows.notices).containsExactly("«Sin título» nunca se guardó, así que no puede volver. "
                + KeyNames.describe(REOPEN_KEY) + " otra vez reabre la anterior.");
        assertThat(windows.noticeDurations).containsExactly(ClosedTabReopener.LONG_NOTICE);

        reopen(tabs);

        assertThat(tabs.viewsOf(counter)).hasSize(2);
        assertThat(tabs.activeView().openDocument().contextPath()).contains(instance(counter, 0));
    }

    @Test
    void withoutAKeyTheNeverSavedNoticeNamesTheAction() {
        reopenKey = Optional.empty();
        OpenDocument untitled = newDocument();
        tabs.addTab(otherCircuit(), Optional.empty());
        tabs.addTab(untitled, Optional.empty());
        modify(untitled);
        tabs.closeGroup(untitled);

        reopen(tabs);

        assertThat(windows.notices).containsExactly("«Sin título» nunca se guardó, así que no puede volver. "
                + "Reabrir pestaña cerrada otra vez reabre la anterior.");
    }

    @Test
    void aMissingFileIsToldAndItsEntryDropped() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(otherCircuit(), Optional.empty());
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        tabs.closeGroup(counter);
        Files.delete(folder.resolve("contador.vsim"));

        reopen(tabs);

        assertThat(tabs.documents()).hasSize(1);
        assertThat(windows.notices).containsExactly("No encuentro «contador.vsim» en " + folder
                + ". No se puede reabrir.");
        assertThat(closedTabs.isEmpty()).isTrue();
        assertThat(windows.read).isEmpty();
    }

    @Test
    void aGroupWhoseCircuitIsStillOpenInAnotherWindowComesBackOnTheOpenCircuit() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(otherCircuit(), Optional.empty());
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        componentTab(tabs, counter, 1);
        other.addTab(counter, Optional.of(MAIN_VIEW));
        modify(counter);
        tabs.closeGroup(counter); // another window holds it: no question, it stays open

        reopen(tabs);

        assertThat(tabs.documents()).containsExactly(tabs.documents().getFirst(), counter);
        assertThat(tabs.viewsOf(counter)).hasSize(2);
        assertThat(windows.read).isEmpty();
        assertThat(windows.notices).isEmpty();
    }

    @Test
    void aComponentGoneFromTheFileIsToldAndTheCircuitStillOpens() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(otherCircuit(), Optional.empty());
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        tabs.closeTab(componentTab(tabs, counter, 1));
        counter.history().undo();
        counter.history().undo();
        counter.history().undo(); // the definition goes
        counter.save(counter.path().orElseThrow());
        tabs.closeGroup(counter);
        closedTabs.pop(); // the group's own entry

        reopen(tabs);

        assertThat(tabs.documents()).hasSize(2);
        assertThat(tabs.viewsOf(tabs.documents().get(1))).hasSize(1);
        assertThat(windows.notices).containsExactly("«Inversor» ya no está en contador.");
    }

    @Test
    void anEmptyStackSaysSo() {
        tabs.addTab(otherCircuit(), Optional.empty());

        reopen(tabs);

        assertThat(windows.notices).containsExactly("No hay pestañas cerradas para reabrir.");
        assertThat(windows.noticeWindows).containsExactly(tabs);
    }

    // --- a window ---

    @Test
    void aClosedWindowComesBackAsANewWindowWithItsGroupsWhereItWas() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        OpenDocument adder = savedWithInverters("sumador.vsim");
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        componentTab(tabs, counter, 1);
        EditorView adderView = tabs.addTab(adder, Optional.of(MAIN_VIEW));
        tabs.activate(adderView);
        WindowState bounds = new WindowState(10, 20, 1000, 700, false, MAIN_VIEW);
        closedTabs.push(new ClosedWindow(tabs.closedGroups(), tabs.activeGroupIndex(), bounds));
        windows.open.remove(tabs);
        tabs.disposeAll();
        closeDocument(counter);
        closeDocument(adder);

        reopen(other);

        assertThat(windows.created).hasSize(1);
        WindowTabs created = windows.created.getFirst();
        assertThat(created.documents()).extracting(document -> document.path().orElseThrow())
                .containsExactly(folder.resolve("contador.vsim"), folder.resolve("sumador.vsim"));
        assertThat(created.viewsOf(created.documents().getFirst())).hasSize(2);
        assertThat(created.activeDocument()).isSameAs(created.documents().get(1));
        assertThat(windows.shown).containsExactly(bounds);
    }

    @Test
    void aReopenedWindowShowsItsActiveTabsOwnViewEvenWhenAnUntouchedGroupWasActive() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(counter, Optional.of(INSIDE_VIEW));
        tabs.addTab(newDocument(), Optional.of(MAIN_VIEW)); // active, untouched: it does not come back
        WindowState bounds = new WindowState(10, 20, 1000, 700, false, MAIN_VIEW);
        closedTabs.push(new ClosedWindow(tabs.closedGroups(), tabs.activeGroupIndex(), bounds));
        windows.open.remove(tabs);
        tabs.disposeAll();
        closeDocument(counter);

        reopen(other);

        assertThat(windows.shown).containsExactly(new WindowState(10, 20, 1000, 700, false, INSIDE_VIEW));
    }

    @Test
    void reopeningLeavesTheWindowsEmptyUntitledTabsAlone() throws Exception {
        // the user opened them on purpose (Ctrl+T): unlike Abrir, reopening replaces nothing (hand test 2026-11-21)
        OpenDocument counter = savedWithInverters("contador.vsim");
        OpenDocument firstEmpty = newDocument();
        OpenDocument secondEmpty = newDocument();
        other.addTab(counter, Optional.of(MAIN_VIEW));
        other.addTab(otherCircuit(), Optional.empty());
        other.closeGroup(counter);
        windows.open.remove(other);
        other.disposeAll();
        tabs.addTab(firstEmpty, Optional.empty());
        tabs.addTab(secondEmpty, Optional.empty());

        reopen(tabs);

        assertThat(tabs.documents()).hasSize(3).contains(firstEmpty, secondEmpty);
    }

    @Test
    void aReopenedCircuitKeepsTheColourItsGroupHad() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(otherCircuit(), Optional.empty());
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        int colour = cl.almejo.vsim.ui.window.tabs.GroupColors.ordinalOf(counter);
        tabs.closeGroup(counter);
        tabs.addTab(newDocument(), Optional.empty()); // takes a colour of its own meanwhile

        reopen(tabs);

        OpenDocument reopened = tabs.documents().get(1);
        assertThat(reopened.path()).contains(folder.resolve("contador.vsim"));
        assertThat(reopened).isNotSameAs(counter);
        assertThat(cl.almejo.vsim.ui.window.tabs.GroupColors.ordinalOf(reopened)).isEqualTo(colour);
    }

    @Test
    void aWindowThatFailsToComeBackIsDiscardedAndItsEntryKept() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        tabs.addTab(otherCircuit(), Optional.empty());
        modify(tabs.documents().get(1));
        closedTabs.push(new ClosedWindow(tabs.closedGroups(), 0, new WindowState(0, 0, 800, 600, false, MAIN_VIEW)));
        windows.failingWindow = true;

        assertThatThrownBy(() -> reopen(other)).isInstanceOf(IllegalStateException.class);

        assertThat(windows.discarded).hasSize(1);
        assertThat(windows.shown).isEmpty();
        assertThat(closedTabs.entries()).hasSize(1).first().isInstanceOf(ClosedWindow.class);
    }

    @Test
    void anUntitledCopyWithContentSaysItWasNeverSaved() throws Exception {
        OpenDocument source = savedWithInverters("ejemplo.vsim");
        OpenDocument copy = new OpenDocument(
                new cl.almejo.vsim.core.io.CircuitReader(StandardComponentTypes.registry())
                        .read(source.path().orElseThrow()), StandardComponentTypes.registry(), null, false);
        documents.add(copy);
        tabs.addTab(otherCircuit(), Optional.empty());
        tabs.addTab(copy, Optional.empty());
        tabs.closeGroup(copy);

        reopen(tabs);

        assertThat(windows.notices).singleElement().asString().startsWith("«ejemplo» nunca se guardó");
    }

    /** The application's part, recorded: files are read for real. */
    private final class RecordingWindows implements ClosedTabReopener.Windows {
        final List<WindowTabs> open = new ArrayList<>();
        final List<WindowTabs> created = new ArrayList<>();
        final List<Path> read = new ArrayList<>();
        final List<String> notices = new ArrayList<>();
        final List<Duration> noticeDurations = new ArrayList<>();
        final List<WindowTabs> noticeWindows = new ArrayList<>();
        final List<WindowState> shown = new ArrayList<>();
        final List<WindowTabs> discarded = new ArrayList<>();
        boolean failingWindow;

        @Override
        public boolean isOpen(WindowTabs window) {
            return open.contains(window);
        }

        @Override
        public Optional<OpenDocument> openDocumentOf(Path path) {
            return open.stream().flatMap(window -> window.documents().stream())
                    .filter(document -> document.path().filter(path::equals).isPresent()).findFirst();
        }

        @Override
        public Optional<OpenDocument> read(Path path, WindowTabs target) {
            read.add(path);
            try {
                OpenDocument document = OpenDocument.open(path, StandardComponentTypes.registry());
                documents.add(document);
                return Optional.of(document);
            } catch (Exception exception) {
                return Optional.empty();
            }
        }

        @Override
        public WindowTabs newWindow() {
            if (failingWindow) {
                WindowTabs broken = new WindowTabs(new RecordingWindowHost(), place -> {
                    throw new IllegalStateException("an extension failed");
                }, new SplitLayoutStore(preferences), () -> false);
                created.add(broken);
                return broken;
            }
            WindowTabs window = ReopenClosedTabTest.this.newWindow(new RecordingWindowHost());
            open.remove(window);
            created.add(window);
            return window;
        }

        @Override
        public void show(WindowTabs window, WindowState bounds) {
            shown.add(bounds);
            open.add(window);
        }

        @Override
        public void focus(WindowTabs window) {
        }

        @Override
        public void discard(WindowTabs window) {
            discarded.add(window);
            window.disposeAll();
        }


        @Override
        public void notice(WindowTabs window, String text, Duration duration) {
            notices.add(text);
            noticeDurations.add(duration);
            noticeWindows.add(window);
        }
    }
}
