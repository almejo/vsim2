package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.PlaceBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A machine's Propiedades in order, its palette for drawing only and its machine-wide actions in the top bar of its
 * tab (fix round 3, K).
 */
class MachinePropertiesOrderTest {

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<EditorView> views = new ArrayList<>();
    private LibraryService service;
    private OpenDocument document;
    private OpenDocument place;
    private EditorView view;
    private MachineMode mode;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        ComponentLibrary library = new ComponentLibrary();
        service = new LibraryService(preferences, library, registry, Runnable::run);
        List<EditorExtension> extensions = new ArrayList<>(
                Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE).stream()
                        .filter(extension -> !(extension instanceof MachineModeExtension))
                        .toList());
        extensions.add(new MachineModeExtension((snapshot, instancePath) -> Optional.empty()));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, library, extensions);
        document = OpenDocument.createEmpty(registry);
        document.execute(new PutDefinitionsCommand(List.of(
                MachineDefinitions.regenerated(MachineFixtures.definition(MachineProblemsOnDiagramTest.troubled())))));
        Component piece = place(document, SubcircuitTypes.typeIdOf(MachineFixtures.DEFINITION_ID), 0, 0, Map.of());
        place = document.openBody(MachineFixtures.DEFINITION_ID, Optional.of(piece.id().value()));
        view = new EditorView(place, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        views.add(view);
        mode = MachineMode.of(view).orElseThrow();
        mode.selection().clear();
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        place.close();
        document.close();
        service.close();
    }

    private static void collect(java.awt.Component component, List<java.awt.Component> into) {
        into.add(component);
        if (component instanceof Container container) {
            for (java.awt.Component child : container.getComponents()) {
                collect(child, into);
            }
        }
    }

    private static List<java.awt.Component> everythingIn(java.awt.Component root) {
        List<java.awt.Component> all = new ArrayList<>();
        collect(root, all);
        return all;
    }

    /** The texts of the labels, in the order they sit in the panel (depth first). */
    private List<String> labelTexts() {
        return everythingIn(mode.inspectorRows()).stream().filter(JLabel.class::isInstance)
                .map(label -> ((JLabel) label).getText()).filter(text -> text != null && !text.isBlank()).toList();
    }

    private int indexOfLabelStartingWith(String prefix) {
        List<String> texts = labelTexts();
        for (int index = 0; index < texts.size(); index++) {
            if (texts.get(index).startsWith(prefix)) {
                return index;
            }
        }
        return -1;
    }

    @Test
    void theSectionsComeInOrderMachineInputsOutputsTestsProblems() {
        List<String> order = List.of("Máquina", "Nombre", "Estados", "Flanco", "Tipo", "Entradas", "Salidas",
                "Pruebas", "Problemas");

        List<Integer> positions = order.stream().map(this::indexOfLabelStartingWith).toList();

        assertThat(positions).doesNotContain(-1).isSorted();
        assertThat(positions).doesNotHaveDuplicates();
    }

    @Test
    void theEdgeComboSitsInTheMachineSectionBeforeTheKind() {
        List<java.awt.Component> all = everythingIn(mode.inspectorRows());
        JComboBox<?> edge = (JComboBox<?>) all.stream().filter(JComboBox.class::isInstance).findFirst().orElseThrow();
        JLabel kindCaption = (JLabel) all.stream().filter(JLabel.class::isInstance)
                .filter(label -> "Tipo".equals(((JLabel) label).getText())).findFirst().orElseThrow();
        JLabel inputs = (JLabel) all.stream().filter(JLabel.class::isInstance)
                .filter(label -> ((JLabel) label).getText().startsWith("Entradas")).findFirst().orElseThrow();

        assertThat(all.indexOf(edge)).isLessThan(all.indexOf(kindCaption));
        assertThat(all.indexOf(kindCaption)).isLessThan(all.indexOf(inputs));
    }

    @Test
    void theHowToDrawAndPinRenamingParagraphsAreNotInThePanelAnymore() {
        List<String> texts = new ArrayList<>();
        everythingIn(mode.inspectorRows()).stream().filter(java.awt.Component::isVisible)
                .filter(JTextComponent.class::isInstance)
                .forEach(text -> texts.add(((JTextComponent) text).getText()));

        assertThat(texts).noneMatch(text -> text.startsWith("Doble clic en el vacío"));
        assertThat(texts).noneMatch(text -> text.startsWith("Renombrar un pin"));
    }

    @Test
    void thePinRenamingHelpIsInTheQuestionMarkNextToEachListsHeader() {
        List<AbstractButton> helps = everythingIn(mode.inspectorRows()).stream()
                .filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                .filter(button -> "?".equals(button.getText())).toList();

        assertThat(helps).hasSize(2);
        assertThat(helps).allSatisfy(help -> {
            assertThat(help.getToolTipText()).startsWith("Renombrar un pin");
            assertThat(help.getAccessibleContext().getAccessibleDescription()).startsWith("Renombrar un pin");
            assertThat(help.isFocusable()).isTrue();
        });
    }

    @Test
    void theProblemsHeaderCountsThemAndEachRowIsOneShortLineWithTheSentenceInItsTooltip() {
        int count = mode.inspectorRows().problemLines().size();

        assertThat(count).isPositive();
        assertThat(labelTexts()).contains("Problemas  " + count);
        assertThat(mode.inspectorRows().problemLines()).allSatisfy(line -> {
            assertThat(line).matches("[⚠✖] .*");
            assertThat(line.length()).isLessThan(60);
        });
        assertThat(mode.inspectorRows().problemTips()).hasSize(count).allSatisfy(tip ->
                assertThat(tip.length()).isGreaterThan(30));
        assertThat(mode.inspectorRows().problemLines()).anyMatch(line -> line.contains("A: falta qué pasa con x = 0"));
    }

    @Test
    void thePaletteOnlyDraws() {
        MachinePalette palette = mode.palette();
        List<String> texts = everythingIn(palette).stream().filter(AbstractButton.class::isInstance)
                .map(button -> ((AbstractButton) button).getText()).toList();
        List<String> labels = everythingIn(palette).stream().filter(JLabel.class::isInstance)
                .map(label -> ((JLabel) label).getText()).toList();

        assertThat(texts).containsExactly("Estado", "Transición", "Nota", "Texto");
        assertThat(labels).doesNotContain("Y después");
    }

    private List<AbstractButton> barButtons() {
        PlaceBar bar = PlaceBar.of(view).orElseThrow();
        return everythingIn(bar).stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                .filter(java.awt.Component::isVisible).toList();
    }

    private AbstractButton barButton(String text) {
        return barButtons().stream().filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    @Test
    void theTopBarOffersProbarConvertirAndAgregarAMisComponentesInThatOrder() {
        List<String> texts = barButtons().stream().map(AbstractButton::getText)
                .filter(text -> text.endsWith("…") || text.startsWith("Agregar")).toList();

        assertThat(texts).containsExactly("Probar…", "Convertir a circuito…", "Agregar a Mis componentes");
        assertThat(barButton("Convertir a circuito…").isEnabled()).isFalse();
        assertThat(barButton("Convertir a circuito…").getToolTipText()).isEqualTo(Messages.text("machine.comesLater"));
    }

    @Test
    void probarAtTheTopStartsTheTestAndSaysYaEstasProbandoUntilItEnds() {
        AbstractButton test = barButton("Probar…");
        assertThat(test.isEnabled()).isTrue();

        test.doClick(0);

        assertThat(mode.isTesting()).isTrue();
        assertThat(test.isEnabled()).isFalse();
        assertThat(test.getToolTipText()).isEqualTo("Ya estás probando");
        mode.testMode().orElseThrow().leave();
        assertThat(mode.isTesting()).isFalse();
        assertThat(test.isEnabled()).isTrue();
    }
}
