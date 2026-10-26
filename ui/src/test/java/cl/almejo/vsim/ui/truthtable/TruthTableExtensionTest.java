package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

class TruthTableExtensionTest {

    /** A window as the feature drives it: what it was told, and its controls to press. */
    private static final class FakeDisplay implements TruthTableDisplay {
        final String title;
        final Controls controls;
        final List<Integer> progress = new ArrayList<>();
        final List<String> notices = new ArrayList<>();
        TruthTable table;
        OptionalInt currentRow = OptionalInt.empty();
        boolean tryingAllowed;
        boolean closed;
        final List<Optional<String>> wizardOffers = new ArrayList<>();

        FakeDisplay(String title, Controls controls) {
            this.title = title;
            this.controls = controls;
        }

        @Override public void showProgress(int percent) { progress.add(percent); }
        @Override public void showTable(TruthTable shown) { table = shown; }
        @Override public void showMessage(String message) { notices.add(message); }
        @Override public void showCurrentRow(OptionalInt row) { currentRow = row; }
        @Override public void allowTryingRows(boolean allowed) { tryingAllowed = allowed; }
        @Override public void offerWizard(Optional<String> refusal) { wizardOffers.add(refusal); }
        @Override public void close() { closed = true; }
    }

    private final List<FakeDisplay> displays = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();

    /** The questions asked before a table of more than 1 024 rows, and the answer the fake user gives. */
    private final List<String> questions = new ArrayList<>();
    private boolean answer = true;
    private OpenDocument openDocument;
    private EditorServices services;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        TruthTableExtension extension = new TruthTableExtension((view, title, controls) -> {
            FakeDisplay display = new FakeDisplay(title, controls);
            displays.add(display);
            return display;
        }, (parent, message) -> messages.add(message), this::confirm,
                () -> new TruthTableRunner(Runnable::run, Runnable::run));
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(extension));
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private boolean confirm(java.awt.Component parent, String title, String question) {
        questions.add(question);
        return answer;
    }

    /** A feature on {@code bare} with fake windows, a direct runner and a progress line that never comes due. */
    private TruthTableFeatures features(EditorView bare) {
        return new TruthTableFeatures(bare, (view, title, controls) -> {
            FakeDisplay display = new FakeDisplay(title, controls);
            displays.add(display);
            return display;
        }, (parent, message) -> messages.add(message), this::confirm,
                new TruthTableRunner(Runnable::run, Runnable::run), (milliseconds, task) -> () -> { });
    }

    /** The live snapshot once the simulation runs the current circuit and {@code condition} holds. */
    private SimulationSnapshot liveSnapshot(Predicate<SimulationSnapshot> condition) {
        waitUntil(() -> openDocument.latestSnapshot().token() == openDocument.netlistToken()
                && condition.test(openDocument.latestSnapshot()));
        return openDocument.latestSnapshot();
    }

    private static Predicate<SimulationSnapshot> switchIs(Component toggle, Value value) {
        return snapshot -> snapshot.componentState(toggle.id().value()).equals(new VisibleState.SwitchState(value));
    }

    private static List<String> texts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getMenuComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    private static JMenuItem last(JMenu menu) {
        return (JMenuItem) menu.getMenuComponent(menu.getMenuComponentCount() - 1);
    }

    private static void perform(JMenuItem item) {
        item.getAction().actionPerformed(new ActionEvent(item, ActionEvent.ACTION_PERFORMED, ""));
    }

    private static List<String> texts(JPopupMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item) {
                texts.add(item.getText());
            }
        }
        return texts;
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no item " + text);
    }

    /** Switch «A» touching LED «Y»: Y = A. */
    private void identityCircuit() {
        place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.LED, 3, -2, Map.of());
    }

    @Test
    void theSimulationMenuEndsWithTheItemAndDisposeTakesEverythingBack() {
        EditorView bare = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        List<String> before = texts(bare.menu(MenuId.SIMULATION));
        int listeners = openDocument.changeListenerCount();
        TruthTableFeatures features = new TruthTableFeatures(bare, (view, title, controls) -> {
            FakeDisplay display = new FakeDisplay(title, controls);
            displays.add(display);
            return display;
        }, (parent, message) -> messages.add(message), this::confirm,
                new TruthTableRunner(Runnable::run, Runnable::run));
        try {
            assertThat(texts(bare.menu(MenuId.SIMULATION))).endsWith("---", "Tabla de verdad…");
            assertThat(bare.shortcutActions()).containsEntry("simulation.truthTable", features.action());
            identityCircuit();
            perform(last(bare.menu(MenuId.SIMULATION)));
            assertThat(features.openWindowCount()).isEqualTo(1);

            features.dispose();

            assertThat(texts(bare.menu(MenuId.SIMULATION))).isEqualTo(before);
            assertThat(bare.shortcutActions()).doesNotContainKey("simulation.truthTable");
            assertThat(openDocument.changeListenerCount()).isEqualTo(listeners);
            assertThat(displays.get(0).closed).isTrue();
            assertThat(features.openWindowCount()).isZero();
            assertThat(bare.contextMenu(Optional.empty(), new Point2D.Double(1, 1)).getComponents())
                    .as("the contributor adds nothing after dispose")
                    .noneMatch(child -> child instanceof JMenuItem menuItem
                            && menuItem.getText().equals("Tabla de verdad…"));
        } finally {
            features.dispose();
            bare.dispose();
        }
    }

    @Test
    void theMenuItemShowsTheWholeCircuitsTableTitledWithTheDocumentName() {
        identityCircuit();
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            perform(last(view.menu(MenuId.SIMULATION)));

            assertThat(displays).hasSize(1);
            FakeDisplay display = displays.get(0);
            assertThat(display.title).isEqualTo("Tabla de verdad — Sin título");
            assertThat(display.table.inputs()).extracting(column -> column.name()).containsExactly("A");
            assertThat(display.table.rows()).hasSize(2);
            assertThat(display.progress).as("finished long before 300 ms: no progress line").isEmpty();
            assertThat(messages).isEmpty();
        } finally {
            view.dispose();
        }
    }

    @Test
    void aRefusalIsAMessageAndOpensNoWindow() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            perform(last(view.menu(MenuId.SIMULATION)));

            assertThat(messages).containsExactly("No hay entradas: usa interruptores o pines de entrada");
            assertThat(displays).isEmpty();
        } finally {
            view.dispose();
        }
    }

    @Test
    void theContextMenuOfOneInstanceNamesItsComponentAndShowsItsTable() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component instance = new Component(openDocument.document().newComponentId(),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), new GridPoint(10, 0), Rotation.R0, Map.of(),
                "");
        openDocument.execute(new AddElementsCommand(List.of(instance)));
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.contextMenu(Optional.empty(), new Point2D.Double(1, 1))))
                    .as("the whole circuit has no switch or LED: nothing to offer on the empty canvas")
                    .doesNotContain("Tabla de verdad…");
            view.canvas().selection().select(instance.id());

            perform(item(view.contextMenu(Optional.of(instance), new Point2D.Double(11, 1)),
                    "Tabla de verdad de «Inversor»…"));

            assertThat(displays).hasSize(1);
            assertThat(displays.get(0).title).isEqualTo("Tabla de verdad — Inversor");
            assertThat(displays.get(0).table.outputs()).extracting(column -> column.name()).containsExactly("y");
        } finally {
            view.dispose();
        }
    }

    @Test
    void theContextMenuOffersTheTableOnlyForAScopeWithAnInputAndAnOutput() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());   // out (4,1)
        Component gate = place(openDocument, StandardComponentTypes.NOT, 10, -1, Map.of());     // (10,1) → (14,1)
        Component led = place(openDocument, StandardComponentTypes.LED, 13, -2, Map.of());      // in (14,1)
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.contextMenu(Optional.empty(), new Point2D.Double(30, 30))))
                    .as("empty canvas, whole circuit with a switch and a LED").contains("Tabla de verdad…");

            view.canvas().selection().select(gate.id());
            assertThat(texts(view.contextMenu(Optional.of(gate), new Point2D.Double(11, 1))))
                    .as("a lone gate could only be refused").doesNotContain("Tabla de verdad…");

            view.canvas().selection().set(List.of(toggle.id(), led.id()));
            assertThat(texts(view.contextMenu(Optional.of(led), new Point2D.Double(14, 0))))
                    .contains("Tabla de verdad…");
        } finally {
            view.dispose();
        }
    }

    @Test
    void aSelectionIsTitledSeleccionAndRecalculateFollowsEdits() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.LED, 3, -2, Map.of());
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            view.canvas().selection().selectAll(openDocument.document());
            perform(last(view.menu(MenuId.SIMULATION)));
            FakeDisplay display = displays.get(0);
            assertThat(display.title).isEqualTo("Tabla de verdad — selección");
            assertThat(display.table.rows()).hasSize(2);
            TruthTable first = display.table;
            assertThat(first.inputs()).extracting(column -> column.name()).containsExactly("A");

            openDocument.execute(new SetLabelCommand(toggle.id(), "E"));
            place(openDocument, StandardComponentTypes.SWITCH, 0, 10, Map.of());
            display.controls.recalculate();
            assertThat(display.table).as("«Recalcular» computed a new table").isNotSameAs(first);
            assertThat(display.table.inputs()).extracting(column -> column.name())
                    .as("the relabelled switch is read again; the new switch is not in the saved selection")
                    .containsExactly("E");
            assertThat(display.table.rows()).hasSize(2);

            view.canvas().selection().clear();
            perform(last(view.menu(MenuId.SIMULATION)));
            assertThat(displays.get(1).table.rows()).hasSize(4);
        } finally {
            view.dispose();
        }
    }

    @Test
    void progressAndCancelAppearOnlyOnceTheWorkHasRunForThreeHundredMilliseconds() {
        identityCircuit();
        EditorView bare = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        List<Runnable> work = new ArrayList<>();
        List<Runnable> due = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        TruthTableFeatures features = new TruthTableFeatures(bare, (view, title, controls) -> {
            FakeDisplay display = new FakeDisplay(title, controls);
            displays.add(display);
            return display;
        }, (parent, message) -> messages.add(message), this::confirm, new TruthTableRunner(work::add, Runnable::run),
                (milliseconds, task) -> {
                    delays.add(milliseconds);
                    due.add(task);
                    return () -> due.remove(task);
                });
        try {
            perform(last(bare.menu(MenuId.SIMULATION)));
            FakeDisplay display = displays.get(0);
            assertThat(delays).containsExactly(TruthTableFeatures.PROGRESS_DELAY_MILLISECONDS);
            assertThat(display.progress).as("nothing before 300 ms").isEmpty();

            due.get(0).run(); // 300 ms later the two-row table is still computing: the row count does not matter
            assertThat(display.progress).containsExactly(0);

            work.remove(0).run();
            assertThat(display.progress).containsExactly(0, 50, 100);
            assertThat(display.table.rows()).hasSize(2);
            assertThat(due).as("the finished computation stopped its delay").isEmpty();
        } finally {
            features.dispose();
            bare.dispose();
        }
    }

    private static final String CANCELLED_NOTICE = "Cálculo cancelado: usa «Recalcular» para empezar de nuevo";

    /** A feature whose worker runs only when the test takes work from {@code work}, and whose delays are in {@code due}. */
    private TruthTableFeatures manualFeatures(EditorView bare, List<Runnable> work, List<Runnable> due) {
        return new TruthTableFeatures(bare, (view, title, controls) -> {
            FakeDisplay display = new FakeDisplay(title, controls);
            displays.add(display);
            return display;
        }, (parent, message) -> messages.add(message), this::confirm, new TruthTableRunner(work::add, Runnable::run),
                (milliseconds, task) -> {
                    due.add(task);
                    return () -> due.remove(task);
                });
    }

    @Test
    void cancelInTheWindowStopsTheComputationAndSaysSo() {
        identityCircuit();
        EditorView bare = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        List<Runnable> work = new ArrayList<>();
        List<Runnable> due = new ArrayList<>();
        TruthTableFeatures features = manualFeatures(bare, work, due);
        try {
            perform(last(bare.menu(MenuId.SIMULATION)));
            FakeDisplay display = displays.get(0);

            display.controls.cancel();

            assertThat(display.notices).containsExactly(CANCELLED_NOTICE);
            assertThat(due).as("the progress delay was stopped").isEmpty();
            work.remove(0).run();
            assertThat(display.table).as("the cancelled result is dropped").isNull();
            assertThat(display.notices).containsExactly(CANCELLED_NOTICE);
        } finally {
            features.dispose();
            bare.dispose();
        }
    }

    @Test
    void aWindowWhoseComputationIsReplacedByAnotherWindowSaysItWasCancelled() {
        identityCircuit();
        EditorView bare = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        List<Runnable> work = new ArrayList<>();
        TruthTableFeatures features = manualFeatures(bare, work, new ArrayList<>());
        try {
            perform(last(bare.menu(MenuId.SIMULATION)));
            perform(last(bare.menu(MenuId.SIMULATION)));
            assertThat(displays.get(0).notices).containsExactly(CANCELLED_NOTICE);
            assertThat(displays.get(1).notices).isEmpty();

            work.forEach(Runnable::run);

            assertThat(displays.get(0).table).isNull();
            assertThat(displays.get(1).table.rows()).hasSize(2);
        } finally {
            features.dispose();
            bare.dispose();
        }
    }

    @Test
    void recalcularOnARunningComputationRestartsItWithoutACancelledNotice() {
        identityCircuit();
        EditorView bare = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        List<Runnable> work = new ArrayList<>();
        TruthTableFeatures features = manualFeatures(bare, work, new ArrayList<>());
        try {
            perform(last(bare.menu(MenuId.SIMULATION)));
            FakeDisplay display = displays.get(0);

            display.controls.recalculate();
            work.forEach(Runnable::run);

            assertThat(display.notices).as("a restart is not a cancellation").isEmpty();
            assertThat(display.table.rows()).hasSize(2);
        } finally {
            features.dispose();
            bare.dispose();
        }
    }

    @Test
    void disposingTheViewClosesItsWindowsAndAClosedWindowIsForgotten() {
        identityCircuit();
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        perform(last(view.menu(MenuId.SIMULATION)));
        perform(last(view.menu(MenuId.SIMULATION)));
        displays.get(0).controls.closed();

        view.dispose();

        assertThat(displays.get(0).closed).as("closed by the user, not again").isFalse();
        assertThat(displays.get(1).closed).isTrue();
    }

    @Test
    void moreThanTenInputBitsAskFirstAndADeclinedQuestionComputesNothing() {
        for (int index = 0; index < 10; index++) {
            place(openDocument, StandardComponentTypes.SWITCH, 0, index * 4, Map.of());
        }
        place(openDocument, StandardComponentTypes.LED, 20, 0, Map.of());
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            perform(last(view.menu(MenuId.SIMULATION)));
            assertThat(questions).as("1 024 rows are computed at once").isEmpty();
            FakeDisplay display = displays.get(0);
            assertThat(display.table.rows()).hasSize(1024);

            place(openDocument, StandardComponentTypes.SWITCH, 0, 40, Map.of());
            answer = false;
            display.controls.recalculate();
            assertThat(questions).containsExactly("La tabla tendrá 2 048 filas y puede tardar. ¿Calcular igual?");
            assertThat(display.table.rows()).as("declined: the old table stays").hasSize(1024);

            answer = true;
            display.controls.recalculate();
            assertThat(display.table.rows()).hasSize(2048);
            display.controls.recalculate();
            assertThat(questions).as("this window already accepted 2 048 rows").hasSize(2);

            answer = false;
            perform(last(view.menu(MenuId.SIMULATION)));
            assertThat(questions).hasSize(3);
            assertThat(displays).as("declined before opening: no window").hasSize(1);
            assertThat(messages).isEmpty();
        } finally {
            view.dispose();
        }
    }

    @Test
    void aTableOfTwoToFourOneBitInputsCanBeOpenedInTheKarnaughWizard() {
        place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.LED, 3, -2, Map.of());
        place(openDocument, StandardComponentTypes.SWITCH, 0, 10, Map.of());
        EditorView bare = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        List<TruthTableSpec> wizards = new ArrayList<>();
        bare.setTableWizard(wizards::add);
        TruthTableFeatures features = features(bare);
        try {
            perform(last(bare.menu(MenuId.SIMULATION)));
            FakeDisplay display = displays.get(0);
            assertThat(display.wizardOffers).containsExactly(Optional.empty());

            display.controls.openInWizard();

            assertThat(wizards).singleElement().satisfies(spec -> {
                assertThat(spec.inputCount()).isEqualTo(2);
                assertThat(spec.outputCount()).isEqualTo(1);
                assertThat(spec.title()).isEqualTo(openDocument.displayName());
                assertThat(spec.outputs().getFirst()).containsExactly(LogicValue.ZERO, LogicValue.ZERO,
                        LogicValue.ONE, LogicValue.ONE);
            });
        } finally {
            features.dispose();
            bare.dispose();
        }
    }

    @Test
    void aTableTheWizardCannotTakeDisablesTheButtonWithTheReason() {
        identityCircuit();
        EditorView bare = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        List<TruthTableSpec> wizards = new ArrayList<>();
        bare.setTableWizard(wizards::add);
        TruthTableFeatures features = features(bare);
        try {
            perform(last(bare.menu(MenuId.SIMULATION)));
            FakeDisplay display = displays.get(0);
            assertThat(display.wizardOffers)
                    .containsExactly(Optional.of("Karnaugh necesita al menos 2 entradas de 1 bit."));
            display.controls.openInWizard();
            assertThat(wizards).isEmpty();
        } finally {
            features.dispose();
            bare.dispose();
        }
    }

    @Test
    void withoutTheWizardTheButtonIsNeverOffered() {
        identityCircuit();
        EditorView bare = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        TruthTableFeatures features = features(bare);
        try {
            perform(last(bare.menu(MenuId.SIMULATION)));
            assertThat(displays.get(0).table).isNotNull();
            assertThat(displays.get(0).wizardOffers).isEmpty();
        } finally {
            features.dispose();
            bare.dispose();
        }
    }

    @Test
    void theRowOfTheLiveInputsIsHighlightedAndFollowsAClickOnTheSwitch() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.LED, 3, -2, Map.of());
        EditorView bare = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        int listeners = bare.canvas().snapshotListenerCount();
        TruthTableFeatures features = features(bare);
        try {
            assertThat(bare.canvas().snapshotListenerCount()).as("no window, nothing to follow").isEqualTo(listeners);
            perform(last(bare.menu(MenuId.SIMULATION)));
            FakeDisplay display = displays.get(0);
            assertThat(bare.canvas().snapshotListenerCount()).isEqualTo(listeners + 1);

            features.followSnapshot(liveSnapshot(switchIs(toggle, Value.ONE)));
            assertThat(display.currentRow).as("a new switch is on: the row A = 1").hasValue(1);

            openDocument.interact(toggle.id());
            features.followSnapshot(liveSnapshot(switchIs(toggle, Value.ZERO)));
            assertThat(display.currentRow).hasValue(0);

            perform(last(bare.menu(MenuId.SIMULATION)));
            assertThat(bare.canvas().snapshotListenerCount()).as("one listener for all the windows")
                    .isEqualTo(listeners + 1);
            display.controls.closed();
            displays.get(1).controls.closed();
            assertThat(bare.canvas().snapshotListenerCount()).isEqualTo(listeners);

            perform(last(bare.menu(MenuId.SIMULATION)));
            features.dispose();
            assertThat(bare.canvas().snapshotListenerCount()).isEqualTo(listeners);
        } finally {
            features.dispose();
            bare.dispose();
        }
    }

    @Test
    void anEditDropsTheHighlightUntilRecalcular() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.LED, 3, -2, Map.of());
        EditorView bare = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        TruthTableFeatures features = features(bare);
        try {
            perform(last(bare.menu(MenuId.SIMULATION)));
            FakeDisplay display = displays.get(0);
            features.followSnapshot(liveSnapshot(switchIs(toggle, Value.ONE)));
            assertThat(display.currentRow).hasValue(1);

            place(openDocument, StandardComponentTypes.LED, 20, 20, Map.of());
            features.followSnapshot(liveSnapshot(switchIs(toggle, Value.ONE)));
            assertThat(display.currentRow).as("the table no longer maps to the circuit").isEmpty();

            display.controls.recalculate();
            assertThat(display.table.outputs()).hasSize(2);
            assertThat(display.currentRow).as("the new table follows the live values at once").hasValue(1);
        } finally {
            features.dispose();
            bare.dispose();
        }
    }

    @Test
    void aDoubleClickedRowSetsTheSwitchesWithoutEditingAndItsRowLightsUp() {
        Component first = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.LED, 3, -2, Map.of());
        Component second = place(openDocument, StandardComponentTypes.SWITCH, 0, 10, Map.of());
        Component third = place(openDocument, StandardComponentTypes.SWITCH, 0, 20, Map.of());
        EditorView bare = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        TruthTableFeatures features = features(bare);
        try {
            perform(last(bare.menu(MenuId.SIMULATION)));
            FakeDisplay display = displays.get(0);
            assertThat(display.table.inputs()).extracting(column -> column.name()).containsExactly("A", "B", "C");
            assertThat(display.tryingAllowed).as("every input is a switch of the document").isTrue();
            long changes = openDocument.changeCount();
            long revision = openDocument.document().revision();

            display.controls.tryRow(5); // A B C = 1 0 1; every new switch starts at 1

            SimulationSnapshot snapshot = liveSnapshot(switchIs(second, Value.ZERO));
            features.followSnapshot(snapshot);
            assertThat(display.currentRow).hasValue(5);
            assertThat(snapshot.componentState(first.id().value()))
                    .isEqualTo(new VisibleState.SwitchState(Value.ONE));
            assertThat(snapshot.componentState(third.id().value()))
                    .isEqualTo(new VisibleState.SwitchState(Value.ONE));
            assertThat(openDocument.changeCount()).as("no undo step").isEqualTo(changes);
            assertThat(openDocument.document().revision()).as("not a document change").isEqualTo(revision);
            assertThat(openDocument.document().component(second.id()).orElseThrow().parameters())
                    .as("the saved initial value is untouched").isEqualTo(second.parameters());
        } finally {
            features.dispose();
            bare.dispose();
        }
    }

    @Test
    void theRowsOfASingleInstanceCannotBeTried() {
        // a live switch with the id of the instance's body pin («c1»): without the guard the row would be applied to it
        Component decoy = place(openDocument, StandardComponentTypes.SWITCH, 0, 20, Map.of());
        assertThat(decoy.id().value()).isEqualTo("c1");
        Component marker = place(openDocument, StandardComponentTypes.SWITCH, 0, 30, Map.of());
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component instance = new Component(openDocument.document().newComponentId(),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), new GridPoint(10, 0), Rotation.R0, Map.of(),
                "");
        openDocument.execute(new AddElementsCommand(List.of(instance)));
        EditorView bare = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        TruthTableFeatures features = features(bare);
        try {
            bare.canvas().selection().select(instance.id());
            perform(last(bare.menu(MenuId.SIMULATION)));
            FakeDisplay display = displays.get(0);
            assertThat(display.table.inputs()).extracting(column -> column.name()).containsExactly("a");
            assertThat(display.table.inputs().get(0).componentId()).isEqualTo("c1");
            assertThat(display.tryingAllowed).as("no hint: the input is the instance's pin").isFalse();

            display.controls.tryRow(0); // would set the decoy switch to 0
            openDocument.setInputs(Map.of(marker.id(), Value.ZERO)); // queued after it: once seen, the first is done

            SimulationSnapshot snapshot = liveSnapshot(switchIs(marker, Value.ZERO));
            assertThat(snapshot.componentState(decoy.id().value())).isEqualTo(new VisibleState.SwitchState(Value.ONE));
            assertThat(messages).isEmpty();
        } finally {
            features.dispose();
            bare.dispose();
        }
    }
}
