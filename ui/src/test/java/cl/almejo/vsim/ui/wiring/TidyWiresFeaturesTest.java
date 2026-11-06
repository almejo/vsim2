package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.layout.CircuitTidy;
import cl.almejo.vsim.core.layout.CircuitTidyOptions;
import cl.almejo.vsim.core.layout.CircuitTidyPlan;
import cl.almejo.vsim.core.layout.CircuitTidyProgress;
import cl.almejo.vsim.core.layout.TextBoxes;
import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
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
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class TidyWiresFeaturesTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> chooserCalls = new ArrayList<>();
    private Optional<TidyChoice> answer = Optional.empty();
    private OpenDocument openDocument;
    private EditorView view;
    private TidyWiresFeatures features;
    private ElementId stub;
    private ElementId left;
    private ElementId right;

    private EditorView newView(OpenDocument document) {
        return new EditorView(document, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), preferences, new ComponentLibrary(), List.of()), NO_WINDOW);
    }

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        Component source = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Component target = place(openDocument, StandardComponentTypes.LED, 30, -2, Map.of());
        GridPoint from = openDocument.registry().typeOf(source).pinLocation(source, 0);
        GridPoint to = openDocument.registry().typeOf(target).pinLocation(target, 0);
        int middle = from.x() + 8;
        left = addWire(from, new GridPoint(middle, from.y()));
        right = addWire(new GridPoint(middle, from.y()), new GridPoint(to.x(), from.y()));
        if (to.y() != from.y()) {
            addWire(new GridPoint(to.x(), from.y()), to);
        }
        stub = addWire(new GridPoint(middle, from.y()), new GridPoint(middle, from.y() + 4));
        view = newView(openDocument);
        features = new TidyWiresFeatures(view, (parent, hasSelection, initial, planner) -> {
            chooserCalls.add(hasSelection + "|" + initial);
            return answer;
        }, directRunner(), directRunner(), (milliseconds, task) -> () -> { }, (parent, onCancel) -> null);
    }

    private static TidyRunner directRunner() {
        return new TidyRunner(Runnable::run, Runnable::run, (snapshot, registry, scope, options, cancelled, progress) ->
                CircuitTidy.plan(snapshot, registry, scope, options, TextBoxes.NONE, cancelled, progress));
    }

    private ElementId addWire(GridPoint start, GridPoint end) {
        ElementId id = openDocument.document().newWireId();
        openDocument.execute(new AddElementsCommand(List.of(new Wire(id, start, end))));
        return id;
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    private static List<JMenuItem> items(JMenu menu) {
        List<JMenuItem> items = new ArrayList<>();
        for (java.awt.Component child : menu.getMenuComponents()) {
            if (child instanceof JMenuItem item) {
                items.add(item);
            }
        }
        return items;
    }

    @Test
    void theTwoItemsEndTheEditMenuAfterASeparator() {
        List<String> texts = texts(view.menu(MenuId.EDIT));
        assertThat(texts.subList(texts.size() - 3, texts.size()))
                .containsExactly("---", "Ordenar circuito", "Ordenar circuito…");
    }

    @Test
    void theShortcutsAreRegisteredWithTheirDefaultStrokes() {
        assertThat(view.shortcutActions()).containsEntry("edit.tidyWires", features.tidyAction())
                .containsEntry("edit.tidyWiresOptions", features.tidyWithOptionsAction());
        // The Ctrl modifier is the menu mask, which is Ctrl when headless.
        KeyStroke tidy = KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
        KeyStroke options = KeyStroke.getKeyStroke(KeyEvent.VK_L,
                InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        assertThat(cl.almejo.vsim.ui.shortcuts.ShortcutCatalog.find("edit.tidyWires").orElseThrow().defaultStroke())
                .contains(tidy);
        assertThat(cl.almejo.vsim.ui.shortcuts.ShortcutCatalog.find("edit.tidyWiresOptions").orElseThrow()
                .defaultStroke()).contains(options);
    }

    @Test
    void theDirectActionRemovesTheStubInOneUndoStepAndSaysSo() {
        long historyBefore = openDocument.changeCount();
        perform(features.tidyAction());
        assertThat(openDocument.document().wire(stub)).isEmpty();
        assertThat(openDocument.changeCount()).isEqualTo(historyBefore + 1);
        assertThat(view.statusBar().text()).contains("Se borró 1 punta suelta");
        openDocument.history().undo();
        assertThat(openDocument.document().wire(stub)).isPresent();
        assertThat(openDocument.document().wire(left)).isPresent();
    }

    @Test
    void nothingToDoAddsNoHistoryEntryAndSaysTheCircuitWasTidy() {
        perform(features.tidyAction());
        long historyAfterFirst = openDocument.changeCount();
        perform(features.tidyAction());
        assertThat(openDocument.changeCount()).isEqualTo(historyAfterFirst);
        assertThat(view.statusBar().text()).contains("El circuito ya estaba ordenado");
    }

    @Test
    void withASelectionOnlySelectedWiresChange() {
        view.canvas().selection().set(List.of(left));
        perform(features.tidyAction());
        // Merging needs both halves selected and the stub is not selected: nothing changes.
        assertThat(openDocument.document().wire(stub)).isPresent();
        assertThat(openDocument.document().wire(right)).isPresent();
        view.canvas().selection().set(List.of(stub));
        perform(features.tidyAction());
        assertThat(openDocument.document().wire(stub)).isEmpty();
        assertThat(openDocument.document().wire(right)).isPresent();
    }

    @Test
    void aSelectedWireTheTidyMergesIntoAnEarlierOneLeavesTheSurvivorSelected() {
        view.canvas().selection().set(List.of(left, right, stub));

        perform(features.tidyAction());

        assertThat(openDocument.document().wire(right)).isEmpty();
        assertThat(openDocument.document().wire(left)).isPresent();
        assertThat(view.canvas().selection().ids()).containsExactly(left);
        openDocument.history().undo();
        assertThat(openDocument.document().wire(right)).isPresent();
    }

    @Test
    void aSelectionOfAPartOnlyTidiesThatPart() {
        view.canvas().selection().set(List.of(openDocument.document().components().get(0).id()));
        perform(features.tidyAction());
        assertThat(openDocument.document().wire(stub)).isPresent();
    }

    @Test
    void theDirectActionUsesTheSavedOptions() {
        preferences.setTidyOptions(new CircuitTidyOptions(true, new TidyOptions(false, true, false)));
        perform(features.tidyAction());
        assertThat(openDocument.document().wire(stub)).isPresent();
        assertThat(openDocument.document().wire(right)).isEmpty();
    }

    @Test
    void theDialogChoiceIsSavedAndApplied() {
        answer = Optional.of(new TidyChoice(new CircuitTidyOptions(true, new TidyOptions(true, false, false)), false));
        perform(features.tidyWithOptionsAction());
        assertThat(preferences.tidyOptions()).isEqualTo(new CircuitTidyOptions(true, new TidyOptions(true, false, false)));
        assertThat(openDocument.document().wire(stub)).isEmpty();
        assertThat(openDocument.document().wire(right)).isPresent();
        assertThat(chooserCalls).containsExactly("false|" + CircuitTidyOptions.ALL);
    }

    @Test
    void cancellingTheDialogChangesNothing() {
        answer = Optional.empty();
        long historyBefore = openDocument.changeCount();
        perform(features.tidyWithOptionsAction());
        assertThat(openDocument.changeCount()).isEqualTo(historyBefore);
        assertThat(preferences.tidyOptions()).isEqualTo(CircuitTidyOptions.ALL);
    }

    @Test
    void eachViewTakesBackItsOwnItemsAndShortcuts() {
        EditorView other = newView(OpenDocument.createEmpty(StandardComponentTypes.registry()));
        List<String> before = texts(other.menu(MenuId.EDIT));
        TidyWiresFeatures extra = new TidyWiresFeatures(other, (parent, selection, initial, planner) -> Optional.empty(),
                directRunner(), directRunner(), (milliseconds, task) -> () -> { }, (parent, onCancel) -> null);
        assertThat(other.shortcutActions()).containsKeys("edit.tidyWires", "edit.tidyWiresOptions");
        assertThat(texts(view.menu(MenuId.EDIT))).contains("Ordenar circuito");
        extra.dispose();
        assertThat(texts(other.menu(MenuId.EDIT))).isEqualTo(before);
        assertThat(other.shortcutActions()).doesNotContainKeys("edit.tidyWires", "edit.tidyWiresOptions");
        assertThat(view.shortcutActions()).containsKeys("edit.tidyWires", "edit.tidyWiresOptions");
        assertThat(items(view.menu(MenuId.EDIT)).stream().map(JMenuItem::getText)).contains("Ordenar circuito");
        other.dispose();
        other.openDocument().close();
    }

    @Test
    void theDialogPanelRecomputesItsSummaryAndTheSwitchBackedPlanMatches() {
        TidyPlanner planner = (options, selectionOnly, onResult) -> {
            onResult.accept(CircuitTidy.plan(openDocument.document(), openDocument.registry(),
                    java.util.Set.of(left, right, stub), options, TextBoxes.NONE, () -> false,
                    CircuitTidyProgress.NONE).orElseThrow());
            return () -> { };
        };
        TidyWiresPanel panel = new TidyWiresPanel(false, CircuitTidyOptions.DEFAULTS, planner);
        assertThat(panel.summaryText()).startsWith("Se borrará 1 punta suelta");
        assertThat(panel.canApply()).isTrue();
        panel.removeStubsBox().doClick();
        panel.mergeSegmentsBox().doClick();
        assertThat(panel.summaryText()).isEqualTo("El circuito ya está ordenado");
        assertThat(panel.canApply()).isFalse();
        panel.mergeSegmentsBox().doClick();
        assertThat(panel.summaryText()).contains("unirá");
        assertThat(panel.options()).isEqualTo(new CircuitTidyOptions(true, new TidyOptions(false, true, false)));
        assertThat(panel.selectionButton().isEnabled()).isFalse();
    }

    @Test
    void aFailedPreviewSaysSoAndNeverThatTheCircuitIsTidy() {
        TidyPlanner[] captured = new TidyPlanner[1];
        features.dispose();
        features = new TidyWiresFeatures(view, (parent, hasSelection, initial, planner) -> {
            captured[0] = planner;
            return Optional.empty();
        }, directRunner(), new TidyRunner(Runnable::run, Runnable::run,
                (snapshot, registry, scope, options, cancelled, progress) -> {
                    throw new IllegalStateException("sin memoria");
                }), (milliseconds, task) -> () -> { }, (parent, onCancel) -> null);
        perform(features.tidyWithOptionsAction());

        TidyWiresPanel panel = new TidyWiresPanel(false, CircuitTidyOptions.DEFAULTS, captured[0]);

        assertThat(panel.summaryText()).isEqualTo("No se pudo ordenar el circuito: sin memoria");
        assertThat(panel.isReady()).isTrue();
        assertThat(panel.canApply()).isFalse();
    }

    @Test
    void theDialogPanelOffersTheSelectionWhenThereIsOne() {
        TidyWiresPanel panel = new TidyWiresPanel(true, CircuitTidyOptions.DEFAULTS, (options, selection, onResult) -> {
            onResult.accept(new CircuitTidyPlan(new CircuitDocument(), 0, 0, java.util.Set.of(), TidyPlan.EMPTY));
            return () -> { };
        });
        assertThat(panel.selectionButton().isEnabled()).isTrue();
        assertThat(panel.selectionOnly()).isTrue();
        panel.everythingButton().doClick();
        assertThat(panel.selectionOnly()).isFalse();
    }

    @Test
    void panelShowsSeparateOverlapsCheckedByDefaultUnderPartes() {
        TidyWiresPanel panel = new TidyWiresPanel(false, CircuitTidyOptions.DEFAULTS,
                (options, selection, onResult) -> () -> { });
        assertThat(panel.separateOverlapsBox().isSelected()).isTrue();
        assertThat(panel.separateOverlapsBox().getText()).isEqualTo("Separar lo que se tapa");
        List<String> order = new ArrayList<>();
        for (java.awt.Component child : panel.getComponents()) {
            if (child instanceof javax.swing.JLabel label) {
                order.add(label.getText());
            } else if (child == panel.separateOverlapsBox()) {
                order.add("[separar]");
            } else if (child == panel.removeStubsBox()) {
                order.add("[puntas]");
            }
        }
        assertThat(order).containsSubsequence("Partes", "[separar]", "Cables", "[puntas]");
        panel.separateOverlapsBox().doClick();
        assertThat(panel.options().separateOverlaps()).isFalse();
    }

    @Test
    void aLongSummaryWrapsInsteadOfBeingCut() {
        CircuitTidyPlan many = new CircuitTidyPlan(new CircuitDocument(), 30, 32, java.util.Set.of(new ElementId("c1")),
                TidyPlan.EMPTY);
        TidyWiresPanel panel = new TidyWiresPanel(false, CircuitTidyOptions.DEFAULTS, (options, selection, onResult) -> {
            onResult.accept(many);
            return () -> { };
        });
        panel.showSummary("Se moverán 30 partes, 32 textos, se borrarán 34 puntas sueltas y se unirán 12 tramos; "
                + "no se pudieron separar 15 cosas");
        javax.swing.JLabel summary = null;
        for (java.awt.Component child : panel.getComponents()) {
            if (child instanceof javax.swing.JLabel label && label.getText().contains(panel.summaryText())) {
                summary = label;
            }
        }
        assertThat(summary).isNotNull();
        int oneLine = new javax.swing.JLabel("Xg").getPreferredSize().height;
        assertThat(summary.getPreferredSize().width).isLessThan(new javax.swing.JLabel(panel.summaryText()).getPreferredSize().width);
        assertThat(summary.getPreferredSize().height).isGreaterThan(oneLine);
    }

    private ElementId addOverlappingGates() {
        place(openDocument, StandardComponentTypes.AND, 0, 20, Map.of());
        return place(openDocument, StandardComponentTypes.AND, 2, 20, Map.of()).id();
    }

    @Test
    void tidyNowUsesTheSavedSeparateOverlaps() {
        ElementId second = addOverlappingGates();
        GridPoint before = openDocument.document().component(second).orElseThrow().position();
        preferences.setTidyOptions(new CircuitTidyOptions(false, TidyOptions.DEFAULTS));
        perform(features.tidyAction());
        assertThat(openDocument.document().component(second).orElseThrow().position()).isEqualTo(before);
        preferences.setTidyOptions(new CircuitTidyOptions(true, TidyOptions.DEFAULTS));
        perform(features.tidyAction());
        assertThat(openDocument.document().component(second).orElseThrow().position()).isNotEqualTo(before);
        assertThat(view.statusBar().text()).contains(" parte");
    }

    @Test
    void oneUndoStepRestoresTheCircuit() {
        ElementId second = addOverlappingGates();
        List<GridPoint> before = openDocument.document().components().stream().map(Component::position).toList();
        long historyBefore = openDocument.changeCount();
        perform(features.tidyAction());
        assertThat(openDocument.changeCount()).isEqualTo(historyBefore + 1);
        assertThat(openDocument.document().components().stream().map(Component::position).toList())
                .isNotEqualTo(before);
        openDocument.history().undo();
        assertThat(openDocument.document().components().stream().map(Component::position).toList())
                .isEqualTo(before);
        assertThat(openDocument.document().component(second)).isPresent();
    }

    @Test
    void guidesSurviveTheTidyItsUndoAndItsRedo() {
        ElementId second = addOverlappingGates();
        List<Guide> guides = List.of(new Guide(Guide.Orientation.VERTICAL, 10));
        openDocument.document().setGuides(guides);
        GridPoint before = openDocument.document().component(second).orElseThrow().position();
        perform(features.tidyAction());
        GridPoint after = openDocument.document().component(second).orElseThrow().position();
        assertThat(after).isNotEqualTo(before);
        assertThat(openDocument.document().guides()).isEqualTo(guides);
        openDocument.history().undo();
        assertThat(openDocument.document().component(second).orElseThrow().position()).isEqualTo(before);
        assertThat(openDocument.document().guides()).isEqualTo(guides);
        openDocument.history().redo();
        assertThat(openDocument.document().component(second).orElseThrow().position()).isEqualTo(after);
        assertThat(openDocument.document().guides()).isEqualTo(guides);
    }

    @Test
    void aSelectedFrameMeansWhatItHolds() {
        ElementId second = addOverlappingGates();
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(-4, 16), 30, 14, "Bloque",
                FrameTint.NONE, false);
        openDocument.execute(new AddElementsCommand(List.of(frame)));
        GridPoint before = openDocument.document().component(second).orElseThrow().position();
        view.canvas().selection().set(List.of(frame.id()));

        perform(features.tidyAction());

        assertThat(openDocument.document().component(second).orElseThrow().position()).isNotEqualTo(before);
        assertThat(openDocument.document().frame(frame.id())).contains(frame);
    }

    @Test
    void noticeCountsWhatCouldNotBeSeparatedAndKeepsTheSelection() {
        features.dispose();
        features = new TidyWiresFeatures(view, (parent, hasSelection, initial, planner) -> Optional.empty(),
                new TidyRunner(Runnable::run, Runnable::run,
                        (snapshot, registry, scope, options, cancelled, progress) -> Optional.of(
                                new CircuitTidyPlan(snapshot, 0, 0, java.util.Set.of(left), TidyPlan.EMPTY))),
                directRunner(), (milliseconds, task) -> () -> { }, (parent, onCancel) -> null);
        view.canvas().selection().set(List.of(left, stub));
        long historyBefore = openDocument.changeCount();
        perform(features.tidyAction());
        assertThat(view.statusBar().text()).contains("No se pudo separar 1 cosa");
        assertThat(view.canvas().selection().ids()).containsExactlyInAnyOrder(left, stub);
        assertThat(openDocument.changeCount()).isEqualTo(historyBefore);
    }

    @Test
    void theNewCheckboxesAreUnderPartesAfterSeparate() {
        TidyWiresPanel panel = new TidyWiresPanel(false, CircuitTidyOptions.ALL,
                (options, selection, onResult) -> () -> { });
        assertThat(panel.alignColumnsBox().getText()).isEqualTo("Alinear en columnas");
        assertThat(panel.straightenBox().getText()).isEqualTo("Enderezar cables moviendo partes");
        assertThat(panel.alignColumnsBox().isSelected()).isTrue();
        assertThat(panel.straightenBox().isSelected()).isTrue();
        List<Object> order = new ArrayList<>();
        for (java.awt.Component child : panel.getComponents()) {
            if (child instanceof javax.swing.JLabel label) {
                order.add(label.getText());
            } else {
                order.add(child);
            }
        }
        assertThat(order).containsSubsequence("Partes", panel.separateOverlapsBox(), panel.alignColumnsBox(),
                panel.straightenBox(), "Cables", panel.removeStubsBox());
        panel.alignColumnsBox().doClick();
        panel.straightenBox().doClick();
        assertThat(panel.options().alignColumns()).isFalse();
        assertThat(panel.options().straighten()).isFalse();
    }

    @Test
    void bothDefaultOnAndAreSaved() {
        assertThat(preferences.tidyOptions().alignColumns()).isTrue();
        assertThat(preferences.tidyOptions().straighten()).isTrue();
        preferences.setTidyOptions(new CircuitTidyOptions(true, false, true, TidyOptions.DEFAULTS));
        assertThat(preferences.tidyOptions().alignColumns()).isFalse();
        assertThat(preferences.tidyOptions().straighten()).isTrue();
    }
}
