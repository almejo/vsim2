package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.stepping.StepIcons;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.ImportMenu;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The production extensions (Application.editorExtensions) installed together on two views of one document. */
class ApplicationExtensionsTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ComponentLibrary library = new ComponentLibrary();
    private final Appearance appearance = new Appearance(ColorScheme.LIGHT);
    private LibraryService service;
    private EditorServices services;
    private OpenDocument openDocument;

    /** Listener counts on every shared object an extension registers with, in a fixed order. */
    private record Counts(int library, int libraryProblems, int preferences, int appearance, int document) {
        Counts plus(Counts other, int times) {
            return new Counts(library + times * other.library, libraryProblems + times * other.libraryProblems,
                    preferences + times * other.preferences, appearance + times * other.appearance,
                    document + times * other.document);
        }

        Counts minus(Counts other) {
            return plus(other, -1);
        }
    }

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        service = new LibraryService(preferences, library, registry, Runnable::run);
        services = new EditorServices(PainterRegistry.standard(), appearance, preferences, library,
                Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE));
        openDocument = OpenDocument.createEmpty(registry);
    }

    @AfterEach
    void close() {
        openDocument.close();
        service.close();
    }

    private Counts counts() {
        return new Counts(library.listenerCount(), service.problemsListenerCount(), preferences.listenerCount(),
                appearance.listenerCount(), openDocument.changeListenerCount());
    }

    private static List<String> texts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getMenuComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    @Test
    void theFileMenuHasEveryExtensionsEntryInPlace() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.FILE))).containsExactly("Nuevo", "Abrir…", "Recientes", "Ejemplos",
                    "Guardar", "Guardar como…", "Exportar imagen…", "Exportar a PDF…", "---", "Importar", "---",
                    "Preferencias…", "---",
                    "Cerrar ventana", "Salir");
            JMenu importMenu = ImportMenu.find(view.menu(MenuId.FILE)).orElseThrow();
            assertThat(texts(importMenu)).containsExactly("Componente…", "Tabla de verdad desde Excel/CSV…");
            assertThat(view.shortcutActions().get("file.importTruthTable"))
                    .isSameAs(importMenu.getItem(1).getAction());
            assertThat(texts(view.menu(MenuId.EDIT))).containsSubsequence("Deshacer", "Rehacer", "Cortar", "Copiar",
                    "Pegar", "Crear componente desde selección…", "Actualizar componente…");
        } finally {
            view.dispose();
        }
    }

    @Test
    void theEditMenuOffersTheFindItemsWithCtrlFAndF3() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.EDIT))).containsSubsequence("Seleccionar todo", "---", "Buscar…",
                    "Buscar siguiente", "Buscar anterior");
            assertThat(view.shortcutActions().get("edit.find").getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F, ShortcutCatalog.menuMask()));
            assertThat(view.shortcutActions().get("edit.findNext").getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
            assertThat(view.shortcutActions().get("edit.findPrevious").getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK));
            assertThat(view.shortcutActions().get("edit.find").getValue(Action.SMALL_ICON)).isNotNull();
        } finally {
            view.dispose();
        }
    }

    @Test
    void theViewMenuEndsWithTheWaveformsToggleAndPresentar() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.VIEW))).endsWith("---", "Formas de onda", "---",
                    "Presentar lo que hace la IA", "Ritmo de Presentar");
            assertThat(view.shortcutActions()).containsKey("view.aiPresent");
            assertThat(view.bottomDock().content()).isPresent();
        } finally {
            view.dispose();
        }
    }

    @Test
    void theViewMenuHasTheGridSubmenuTheSnapItemsAndTheGuideItemsAfterTheZoomGroup() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.VIEW))).containsExactly("Selección", "Cable", "Marco",
                    "Mover vista", "---", "Acercar", "Alejar", "Zoom 100 %", "Centrar circuito", "---", "Cuadrícula",
                    "Ajustar", "Ajustar a", "Mostrar guías", "Agregar guía horizontal", "Agregar guía vertical",
                    "Borrar guías", "---", "Siguiente problema", "Problema anterior", "---", "Formas de onda", "---",
                    "Presentar lo que hace la IA", "Ritmo de Presentar");
            assertThat(view.shortcutActions()).containsKeys("view.cycleGrid", "view.snap", "view.snapToParts",
                    "view.snapToGuides", "view.snapToGridLines", "view.showGuides", "view.addHorizontalGuide",
                    "view.addVerticalGuide", "view.clearGuides");
            assertThat(view.shortcutActions().get("view.snap").getValue(Action.ACCELERATOR_KEY)).isEqualTo(
                    KeyStroke.getKeyStroke(KeyEvent.VK_M, ShortcutCatalog.menuMask() | InputEvent.SHIFT_DOWN_MASK));
            assertThat(view.canvas().edgeHandles()).hasSize(6); // the rulers, corner and grab; the two split grips
        } finally {
            view.dispose();
        }
    }

    @Test
    void theSimulationMenuHasRestartTheFixedStepAndTheStepByStepItemsInOrder() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.SIMULATION))).containsExactly("Iniciar", "Pausar", "Iniciar o pausar", "Reiniciar",
                    "Avanzar 10 t", "Tamaño del avance…", "Paso a paso", "Avanzar al próximo evento",
                    "Avanzar al próximo flanco de reloj", "---", "Pausar al detectar cortocircuito", "---",
                    "Tabla de verdad…");
            assertThat(view.shortcutActions().get("simulation.advance").getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
            assertThat(view.shortcutActions().get("simulation.advanceToClockEdge").getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.SHIFT_DOWN_MASK));
            assertThat(view.shortcutActions().get("simulation.stepMode").getValue(Action.ACCELERATOR_KEY)).isNull();
        } finally {
            view.dispose();
        }
    }

    @Test
    void theToolsMenuOpensTheKarnaughWizardTheExpressionsAndTheAiPanel() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.TOOLS))).containsExactly("Circuito desde tabla de verdad…",
                    "Practicar Karnaugh…", "Circuito desde expresiones…", "---", "IA (MCP)…");
            assertThat(view.shortcutActions()).containsKeys("tools.circuitFromTruthTable",
                    "tools.karnaughPractice", "tools.circuitFromExpressions", "tools.aiPanel");
        } finally {
            view.dispose();
        }
    }

    @Test
    void theStepModeControlsAreIconOnlyButtonsNamedByTheirTooltip() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            Action stepMode = view.shortcutActions().get("simulation.stepMode");
            Action advance = view.shortcutActions().get("simulation.advance");
            Action clockEdge = view.shortcutActions().get("simulation.advanceToClockEdge");
            assertThat(stepMode.getValue(Action.LARGE_ICON_KEY))
                    .isSameAs(Icons.get(StepIcons.MODE, Icons.TOOLBAR_SIZE));
            assertThat(advance.getValue(Action.LARGE_ICON_KEY))
                    .isSameAs(Icons.get(StepIcons.ADVANCE, Icons.TOOLBAR_SIZE));
            assertThat(clockEdge.getValue(Action.LARGE_ICON_KEY))
                    .isSameAs(Icons.get(StepIcons.CLOCK_EDGE, Icons.TOOLBAR_SIZE));
            for (Action action : List.of(stepMode, advance, clockEdge)) {
                AbstractButton button = Arrays.stream(view.toolBar().getComponents())
                        .filter(AbstractButton.class::isInstance)
                        .map(AbstractButton.class::cast)
                        .filter(candidate -> candidate.getAction() == action)
                        .findFirst().orElseThrow();
                String name = (String) action.getValue(Action.NAME);
                assertThat(button.getHideActionText()).as(name + ": installed before ActionIcons").isTrue();
                assertThat(button.getIcon()).as(name).isNotNull();
                // E Task 14 appends the shortcut: «Avanzar al próximo evento (F7)».
                assertThat(button.getToolTipText()).startsWith(name);
            }
        } finally {
            view.dispose();
        }
    }

    @Test
    void theFixedStepTakesF6AndItsSizeFromThePreferences() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            Action step = view.actions().step();
            AbstractButton button = Arrays.stream(view.toolBar().getComponents())
                    .filter(AbstractButton.class::isInstance)
                    .map(AbstractButton.class::cast)
                    .filter(candidate -> candidate.getAction() == step)
                    .findFirst().orElseThrow();
            assertThat(step.getValue(Action.ACCELERATOR_KEY)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
            assertThat(button.getToolTipText()).isEqualTo("Avanza la simulación 10 unidades de tiempo (F6)");

            preferences.setStepUnits(100);

            assertThat(step.getValue(Action.NAME)).isEqualTo("Avanzar 100 t");
            assertThat(button.getToolTipText()).isEqualTo("Avanza la simulación 100 unidades de tiempo (F6)");
            assertThat(view.shortcutActions().get("simulation.stepSize").getValue(Action.ACCELERATOR_KEY)).isNull();
        } finally {
            view.dispose();
        }
    }

    @Test
    void theShortcutsExtensionBindsTheWindowMenuAcceleratorsAndCanvasKeys() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.WINDOW))).containsExactly("Nueva vista", "Dividir", "Siguiente panel");
            assertThat(view.actions().save().getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_S, ShortcutCatalog.menuMask()));
            assertThat(view.canvas().getInputMap(JComponent.WHEN_FOCUSED)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)))
                    .isEqualTo("shortcut.edit.delete");
        } finally {
            view.dispose();
        }
    }

    @Test
    void disposingEachOfTwoViewsTakesBackExactlyItsListeners() {
        Counts baseline = counts();

        EditorView first = new EditorView(openDocument, services, NO_WINDOW);
        Counts perView = counts().minus(baseline);
        EditorView second = new EditorView(openDocument, services, NO_WINDOW);

        assertThat(perView.library()).as("the extensions follow the library").isPositive();
        assertThat(perView.libraryProblems()).isPositive();
        assertThat(perView.document()).isPositive();
        assertThat(counts()).isEqualTo(baseline.plus(perView, 2));

        second.dispose();
        assertThat(counts()).isEqualTo(baseline.plus(perView, 1));
        first.dispose();
        assertThat(counts()).isEqualTo(baseline);
    }
}
