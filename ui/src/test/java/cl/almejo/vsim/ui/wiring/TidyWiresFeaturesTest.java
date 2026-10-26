package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
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
        Component target = place(openDocument, StandardComponentTypes.LED, 30, 0, Map.of());
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
        return new TidyRunner(Runnable::run, Runnable::run, cl.almejo.vsim.core.wiring.WireTidy::plan);
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
                .containsExactly("---", "Ordenar cables", "Ordenar cables…");
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
    void nothingToDoAddsNoHistoryEntryAndSaysTheWiresWereTidy() {
        perform(features.tidyAction());
        long historyAfterFirst = openDocument.changeCount();
        perform(features.tidyAction());
        assertThat(openDocument.changeCount()).isEqualTo(historyAfterFirst);
        assertThat(view.statusBar().text()).contains("Los cables ya estaban ordenados");
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
    void aSelectionWithoutWiresTidiesTheWholeCircuit() {
        view.canvas().selection().set(List.of(openDocument.document().components().get(0).id()));
        perform(features.tidyAction());
        assertThat(openDocument.document().wire(stub)).isEmpty();
    }

    @Test
    void theDirectActionUsesTheSavedOptions() {
        preferences.setTidyOptions(new TidyOptions(false, true, false));
        perform(features.tidyAction());
        assertThat(openDocument.document().wire(stub)).isPresent();
        assertThat(openDocument.document().wire(right)).isEmpty();
    }

    @Test
    void theDialogChoiceIsSavedAndApplied() {
        answer = Optional.of(new TidyChoice(new TidyOptions(true, false, false), false));
        perform(features.tidyWithOptionsAction());
        assertThat(preferences.tidyOptions()).isEqualTo(new TidyOptions(true, false, false));
        assertThat(openDocument.document().wire(stub)).isEmpty();
        assertThat(openDocument.document().wire(right)).isPresent();
        assertThat(chooserCalls).containsExactly("false|" + TidyOptions.DEFAULTS);
    }

    @Test
    void cancellingTheDialogChangesNothing() {
        answer = Optional.empty();
        long historyBefore = openDocument.changeCount();
        perform(features.tidyWithOptionsAction());
        assertThat(openDocument.changeCount()).isEqualTo(historyBefore);
        assertThat(preferences.tidyOptions()).isEqualTo(TidyOptions.DEFAULTS);
    }

    @Test
    void eachViewTakesBackItsOwnItemsAndShortcuts() {
        EditorView other = newView(OpenDocument.createEmpty(StandardComponentTypes.registry()));
        List<String> before = texts(other.menu(MenuId.EDIT));
        TidyWiresFeatures extra = new TidyWiresFeatures(other, (parent, selection, initial, planner) -> Optional.empty(),
                directRunner(), directRunner(), (milliseconds, task) -> () -> { }, (parent, onCancel) -> null);
        assertThat(other.shortcutActions()).containsKeys("edit.tidyWires", "edit.tidyWiresOptions");
        assertThat(texts(view.menu(MenuId.EDIT))).contains("Ordenar cables");
        extra.dispose();
        assertThat(texts(other.menu(MenuId.EDIT))).isEqualTo(before);
        assertThat(other.shortcutActions()).doesNotContainKeys("edit.tidyWires", "edit.tidyWiresOptions");
        assertThat(view.shortcutActions()).containsKeys("edit.tidyWires", "edit.tidyWiresOptions");
        assertThat(items(view.menu(MenuId.EDIT)).stream().map(JMenuItem::getText)).contains("Ordenar cables");
        other.dispose();
        other.openDocument().close();
    }

    @Test
    void theDialogPanelRecomputesItsSummaryAndTheSwitchBackedPlanMatches() {
        TidyPlanner planner = (options, selectionOnly, onResult) -> {
            onResult.accept(cl.almejo.vsim.core.wiring.WireTidy.plan(openDocument.document(),
                    openDocument.registry(), java.util.Set.of(left, right, stub), options));
            return () -> { };
        };
        TidyWiresPanel panel = new TidyWiresPanel(false, TidyOptions.DEFAULTS, planner);
        assertThat(panel.summaryText()).startsWith("Se borrará 1 punta suelta");
        assertThat(panel.canApply()).isTrue();
        panel.removeStubsBox().doClick();
        panel.mergeSegmentsBox().doClick();
        assertThat(panel.summaryText()).isEqualTo("Los cables ya están ordenados");
        assertThat(panel.canApply()).isFalse();
        panel.mergeSegmentsBox().doClick();
        assertThat(panel.summaryText()).contains("unirá");
        assertThat(panel.options()).isEqualTo(new TidyOptions(false, true, false));
        assertThat(panel.selectionButton().isEnabled()).isFalse();
    }

    @Test
    void theDialogPanelOffersTheSelectionWhenThereIsOne() {
        TidyWiresPanel panel = new TidyWiresPanel(true, TidyOptions.DEFAULTS, (options, selection, onResult) -> {
            onResult.accept(TidyPlan.EMPTY);
            return () -> { };
        });
        assertThat(panel.selectionButton().isEnabled()).isTrue();
        assertThat(panel.selectionOnly()).isTrue();
        panel.everythingButton().doClick();
        assertThat(panel.selectionOnly()).isFalse();
    }
}
