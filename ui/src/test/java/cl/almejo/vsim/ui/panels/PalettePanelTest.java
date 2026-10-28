package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.clipboard.FileListTransferable;
import cl.almejo.vsim.ui.clipboard.FragmentTransferable;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.AppearanceTestSupport;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentIcon;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.TransferHandler;
import java.awt.Color;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PalettePanelTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final List<String> chosen = new ArrayList<>();
    private final DefinitionBundle inverterBundle = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
    private final DefinitionBundle doubleInverterBundle = DefinitionBundle.of(ExampleDefinitions.doubleInverter(),
            id -> Optional.of(ExampleDefinitions.inverter()));
    private PalettePanel palette;

    @BeforeEach
    void create() {
        palette = new PalettePanel(registry, PainterRegistry.standard(), ColorScheme.LIGHT,
                type -> chosen.add(type.id()));
    }

    /**
     * Bug 2026-10-18: with dragging enabled the tree takes the focus back when the mouse is released, after the
     * choice asked for the canvas. Releasing the mouse on the chosen entry hands the focus over again, later.
     */
    @Test
    void releasingTheMouseOnTheChosenEntryHandsTheFocusOverAfterTheTreeTookIt() throws Exception {
        java.util.concurrent.atomic.AtomicInteger handOvers = new java.util.concurrent.atomic.AtomicInteger();
        palette.setFocusAfterMouseChoice(handOvers::incrementAndGet);
        javax.swing.JTree tree = palette.tree();
        tree.setSize(300, 2000);
        palette.choose(StandardComponentTypes.AND);
        java.awt.Rectangle entry = tree.getPathBounds(tree.getSelectionPath());

        release(tree, entry.x + 2, entry.y + 2);
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        assertThat(handOvers).hasValue(1);

        java.awt.Rectangle category = tree.getRowBounds(0);
        release(tree, category.x + 2, category.y + 2);
        palette.clearSelection();
        release(tree, entry.x + 2, entry.y + 2);
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        assertThat(handOvers).as("a category, or nothing chosen, keeps the focus in the palette").hasValue(1);
    }

    private static void release(java.awt.Component target, int x, int y) {
        target.dispatchEvent(new java.awt.event.MouseEvent(target, java.awt.event.MouseEvent.MOUSE_RELEASED, 0, 0, x,
                y, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    @Test
    void listsEveryStandardTypeGroupedByCategoryAndTheGateShortcuts() {
        List<String> presetIds = StandardComponentTypes.presets().stream().map(preset -> preset.id()).toList();
        List<String> shown = palette.visibleTypeIds();
        assertThat(shown.stream().filter(id -> !presetIds.contains(id)).toList()).containsExactlyElementsOf(
                registry.all().stream()
                        .sorted(java.util.Comparator.comparing(ComponentType::category))
                        .map(ComponentType::id).toList());
        assertThat(shown).containsAll(presetIds);
    }

    @Test
    void theConstantSitsRightAfterTheSwitchInInputOutput() {
        List<String> ids = palette.visibleTypeIds();
        assertThat(ids.indexOf(StandardComponentTypes.CONSTANT))
                .isEqualTo(ids.indexOf(StandardComponentTypes.SWITCH) + 1);
        assertThat(Messages.componentName(registry.require(StandardComponentTypes.CONSTANT)))
                .isEqualTo("Constante");
    }

    @Test
    void theBlocksHaveTheirOwnSectionAfterInputOutputWithAHelpTooltip() {
        assertThat(palette.visibleSections()).containsSubsequence("Entrada/Salida", "Bloques", "Depuración");
        List<String> ids = palette.visibleTypeIds();
        assertThat(ids.indexOf(StandardComponentTypes.ADDER)).isGreaterThan(ids.indexOf(StandardComponentTypes.LED));
        assertThat(ids.indexOf(StandardComponentTypes.ADDER))
                .isLessThan(ids.indexOf(StandardComponentTypes.TIME_DIAGRAM));
        assertThat(Messages.componentName(registry.require(StandardComponentTypes.ADDER))).isEqualTo("Sumador");
        assertThat(palette.toolTipFor(StandardComponentTypes.ADDER)).hasValueSatisfying(text -> assertThat(text)
                .contains("Cin").contains("Cout"));
    }

    @Test
    void filterMatchesIdsAndLocalizedNames() {
        palette.setFilter("and");
        assertThat(palette.visibleTypeIds()).containsExactly("and", "nand");
        palette.setFilter("RELOJ");
        assertThat(palette.visibleTypeIds()).containsExactly(StandardComponentTypes.CLOCK);
        palette.setFilter("zzz");
        assertThat(palette.visibleTypeIds()).isEmpty();
        palette.setFilter("  ");
        assertThat(palette.visibleTypeIds())
                .hasSize(registry.all().size() + StandardComponentTypes.presets().size());
    }

    @Test
    void choosingATypeCallsBackAndCanBeRepeatedAfterClearing() {
        palette.choose(StandardComponentTypes.LED);
        palette.clearSelection();
        palette.choose(StandardComponentTypes.LED);
        assertThat(chosen).containsExactly(StandardComponentTypes.LED, StandardComponentTypes.LED);
    }

    @Test
    void filterHelperIgnoresCase() {
        ComponentType clock = registry.require(StandardComponentTypes.CLOCK);
        assertThat(PaletteFilter.matches(clock, "")).isTrue();
        assertThat(PaletteFilter.matches(clock, "CLO")).isTrue();
        assertThat(PaletteFilter.matches(clock, "reL")).isTrue();
        assertThat(PaletteFilter.matches(clock, "led")).isFalse();
    }

    @Test
    void searchFieldConsumesUndoAndRedoKeystrokes() {
        int mask = java.awt.GraphicsEnvironment.isHeadless() ? java.awt.event.InputEvent.CTRL_DOWN_MASK
                : java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.JTextField field = palette.searchField();
        for (int modifiers : new int[]{mask, mask | java.awt.event.InputEvent.SHIFT_DOWN_MASK}) {
            Object actionKey = field.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, modifiers));
            assertThat(actionKey).isNotNull();
            assertThat(field.getActionMap().get(actionKey)).isNotNull();
        }
    }

    @Test
    void searchFieldKeepsItsOwnClipboardKeys() {
        int mask = java.awt.GraphicsEnvironment.isHeadless() ? java.awt.event.InputEvent.CTRL_DOWN_MASK
                : java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.JTextField field = palette.searchField();
        for (int keyCode : new int[]{java.awt.event.KeyEvent.VK_X, java.awt.event.KeyEvent.VK_C,
                java.awt.event.KeyEvent.VK_V, java.awt.event.KeyEvent.VK_A}) {
            Object actionKey = field.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke(keyCode, mask));
            assertThat(actionKey).as("binding for key code %d", keyCode).isNotNull();
            assertThat(field.getActionMap().get(actionKey)).isNotNull();
            assertThat(field.getActionMap().get(actionKey).isEnabled()).isTrue();
        }
    }

    /** Review I1: a drag out released over the search box must not fill it with fragment JSON. */
    @Test
    void theSearchBoxRefusesCircuitFragmentsButAcceptsPlainText() {
        JTextField search = palette.searchField();
        TransferHandler handler = search.getTransferHandler();
        FragmentTransferable fragment = new FragmentTransferable("{\"formatVersion\":2,\"kind\":\"fragment\"}");
        assertThat(handler.canImport(new TransferHandler.TransferSupport(search, fragment))).isFalse();
        assertThat(handler.importData(new TransferHandler.TransferSupport(search, fragment))).isFalse();
        assertThat(search.getText()).isEmpty();
        assertThat(handler.canImport(new TransferHandler.TransferSupport(search, new StringSelection("not"))))
                .isTrue();
    }

    @Test
    void aSchemeChangeRedrawsIconsWithTheNewScheme() {
        Appearance appearance = new Appearance(ColorScheme.LIGHT);
        PalettePanel themed = new PalettePanel(registry, PainterRegistry.standard(), appearance, type -> {
        });
        ComponentType not = registry.require(StandardComponentTypes.BUFFER);
        assertThat(((ComponentIcon) themed.icon(not)).scheme()).isSameAs(ColorScheme.LIGHT);

        ColorScheme magenta = AppearanceTestSupport.withBackground(ColorScheme.LIGHT, Color.MAGENTA);
        appearance.setScheme(magenta);
        assertThat(((ComponentIcon) themed.icon(not)).scheme()).isSameAs(magenta);

        themed.dispose();
        assertThat(appearance.listenerCount()).isZero();
    }

    @Test
    void userComponentsFollowTheCategoriesInTheirOwnSections() {
        List<String> chosenUser = new ArrayList<>();
        palette.setUserComponentChooser(component -> chosenUser.add(component.bundle().root().id()));
        palette.setUserComponents(List.of(inverterBundle), List.of(doubleInverterBundle));

        assertThat(palette.visibleSections()).containsExactly("Básicas", "Entrada/Salida", "Bloques", "Memoria",
                "Depuración", "Pines de componente", "Mis componentes", "Componentes de este circuito");
        assertThat(palette.visibleTypeIds()).endsWith("subcircuit:inverter", "subcircuit:double-inverter");
        palette.setFilter("inversor");
        assertThat(palette.visibleTypeIds()).containsExactly("subcircuit:inverter", "subcircuit:double-inverter");
        palette.setFilter("doble inv");
        assertThat(palette.visibleTypeIds()).containsExactly("subcircuit:double-inverter");

        palette.choose("subcircuit:double-inverter");

        assertThat(chosenUser).containsExactly(ExampleDefinitions.DOUBLE_INVERTER);
        assertThat(chosen).isEmpty();
    }

    @Test
    void draggingAUserComponentCarriesItsDefinitions() throws Exception {
        palette.setUserComponents(List.of(doubleInverterBundle), List.of());
        palette.choose("subcircuit:double-inverter");

        Transferable transferable = palette.dragTransferable();

        String json = (String) transferable.getTransferData(FragmentTransferable.FRAGMENT_FLAVOR);
        CircuitFragment fragment = new FragmentFormat(registry).read(json);
        assertThat(fragment.definitions())
                .containsExactly(ExampleDefinitions.inverter(), ExampleDefinitions.doubleInverter());
        assertThat(fragment.components()).singleElement()
                .extracting(Component::typeId).isEqualTo("subcircuit:double-inverter");
    }

    /** Preflight S6: an edit that changes no definition must not rebuild the tree (collapsed rows, highlight). */
    @Test
    void theSameUserComponentsKeepTheTreeAndOnlyChangedDefinitionsRedrawTheirIcons() {
        palette.setUserComponents(List.of(inverterBundle), List.of(doubleInverterBundle));
        Object model = palette.treeModel();
        Icon inverterIcon = palette.icon(SubcircuitTypes.typeOf(ExampleDefinitions.inverter()));
        Icon doubleInverterIcon = palette.icon(SubcircuitTypes.typeOf(ExampleDefinitions.doubleInverter()));

        palette.setUserComponents(List.of(new DefinitionBundle(ExampleDefinitions.inverter(), List.of())),
                List.of(doubleInverterBundle));
        assertThat(palette.treeModel()).isSameAs(model);

        ComponentDefinition original = ExampleDefinitions.inverter();
        ComponentDefinition renamed = new ComponentDefinition(original.id(), "Negador", "", original.size(),
                original.pinout(), original.body());
        palette.setUserComponents(List.of(new DefinitionBundle(renamed, List.of())), List.of(doubleInverterBundle));

        assertThat(palette.treeModel()).isNotSameAs(model);
        assertThat(palette.icon(SubcircuitTypes.typeOf(renamed))).isNotSameAs(inverterIcon);
        assertThat(palette.icon(SubcircuitTypes.typeOf(ExampleDefinitions.doubleInverter())))
                .isSameAs(doubleInverterIcon);
    }

    @Test
    void droppedCircuitAndComponentFilesGoToTheHandler() {
        List<List<Path>> received = new ArrayList<>();
        FileListTransferable mixed = new FileListTransferable(List.of(new File("a.vsimcomponent.json"),
                new File("notes.txt"), new File("circuit.vsim")));
        assertThat(palette.importFiles(mixed)).isFalse();

        palette.setFileDropHandler(received::add);

        assertThat(palette.importFiles(mixed)).isTrue();
        assertThat(received).containsExactly(List.of(Path.of("a.vsimcomponent.json"), Path.of("circuit.vsim")));
        assertThat(palette.importFiles(new FileListTransferable(List.of(new File("circuit.vsim"))))).isTrue();
        assertThat(palette.importFiles(new FileListTransferable(List.of(new File("notes.txt"))))).isFalse();
        assertThat(received).hasSize(2);
    }

    @Test
    void theMenuProviderGetsTheEntryUnderTheMouse() {
        palette.setUserComponents(List.of(inverterBundle), List.of());
        List<Optional<String>> asked = new ArrayList<>();
        palette.setUserMenuProvider(entry -> {
            asked.add(entry.map(component -> component.bundle().root().id()));
            return Optional.of(new JPopupMenu());
        });

        assertThat(palette.popupFor(palette.pathOf("subcircuit:inverter"))).isPresent();
        palette.popupFor(null);
        palette.popupFor(palette.pathOf(StandardComponentTypes.BUFFER));

        assertThat(asked).containsExactly(Optional.of("inverter"), Optional.empty(), Optional.empty());
    }

    @Test
    void onlyComponentPinsHaveHelpAndItFollowsTheLanguage() {
        palette.setUserComponents(List.of(inverterBundle), List.of());
        assertThat(palette.toolTipFor(InterfacePins.INPUT_PIN)).hasValueSatisfying(text -> assertThat(text)
                .contains("interruptor").contains("entradas"));
        assertThat(palette.toolTipFor(InterfacePins.OUTPUT_PIN)).hasValueSatisfying(text -> assertThat(text)
                .contains("LED").contains("salidas"));
        assertThat(palette.toolTipFor(StandardComponentTypes.AND)).isEmpty();
        assertThat(palette.toolTipFor(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER))).isEmpty();
        assertThat(palette.toolTipFor("no-such-type")).isEmpty();

        Messages.initialize(Locale.ENGLISH);
        try {
            PalettePanel english = new PalettePanel(registry, PainterRegistry.standard(), ColorScheme.LIGHT,
                    type -> { });
            assertThat(english.toolTipFor(InterfacePins.INPUT_PIN)).hasValueSatisfying(text -> assertThat(text)
                    .contains("switch").contains("inputs"));
        } finally {
            Messages.initialize(Messages.SPANISH);
        }
    }
}
