package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.icons.ActionIcons;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.groups.GroupsExtension;
import cl.almejo.vsim.ui.memory.MemoryEditorExtension;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.Scope;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.stepping.StepIcons;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.ImportMenu;
import cl.almejo.vsim.ui.window.ExportMenu;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

/** The production extensions (Application.editorExtensions) installed together on two views of one document. */
class ApplicationExtensionsTest {

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

    @Test
    void theBusGroupsRowsAreAmongTheExtensions() {
        assertThat(Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE))
                .hasAtLeastOneElementOfType(GroupsExtension.class);
    }

    @Test
    void theMemoryContentEditorIsAmongTheExtensionsBeforeTheIcons() {
        List<EditorExtension> extensions = Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE);
        int editor = -1;
        int icons = -1;
        for (int index = 0; index < extensions.size(); index++) {
            if (extensions.get(index) instanceof MemoryEditorExtension) {
                editor = index;
            }
            if (extensions.get(index) instanceof ActionIcons) {
                icons = index;
            }
        }
        assertThat(editor).isNotNegative().isLessThan(icons);
    }

    @Test
    void theFileMenuHasEveryExtensionsEntryInPlace() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.FILE))).containsExactly("Nuevo", "Abrir…", "Recientes", "Ejemplos",
                    "Guardar", "Guardar como…", "Imprimir…", "---", "Importar", "Exportar", "---",
                    "Preferencias…", "---",
                    "Cerrar pestaña", "Cerrar ventana", "Salir");
            assertThat(texts(ExportMenu.find(view.menu(MenuId.FILE)).orElseThrow())).containsExactly("Imagen…",
                    "Imagen de la ventana…",
                    "PDF…", "Circuito de Logisim (.circ) (beta)…");
            JMenu importMenu = ImportMenu.find(view.menu(MenuId.FILE)).orElseThrow();
            assertThat(texts(importMenu)).containsExactly("Componente…", "Tabla de verdad desde Excel/CSV…",
                    "Memoria desde archivo…", "Circuito de Logisim (.circ) (beta)…");
            assertThat(view.shortcutActions().get("file.importTruthTable"))
                    .isSameAs(importMenu.getItem(1).getAction());
            assertThat(texts(view.menu(MenuId.EDIT))).containsSubsequence("Deshacer", "Rehacer", "Cortar", "Copiar", "Copiar captura",
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
    void theViewMenuEndsWithTheWaveformsTogglePresentarAndThePresentationMode() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.VIEW))).endsWith("---", "Formas de onda", "---",
                    "Presentar lo que hace la IA", "Ritmo de Presentar", "---",
                    "Modo presentación (pantalla completa)", "Mostrar los atajos que se usan");
            assertThat(view.shortcutActions()).containsKeys("view.aiPresent", "view.presentationMode",
                    "view.showUsedShortcuts");
            assertThat(view.bottomDock().content()).isPresent();
        } finally {
            view.dispose();
        }
    }

    @Test
    void theViewMenuHasTheGridSubmenuTheSnapItemsAndTheGuideItemsAfterTheZoomGroup() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.VIEW))).containsExactly("Árbol del circuito", "Paleta", "Propiedades", "Problemas", "Notas", "Mapa de memoria", "---",
                    "Selección", "Cable", "Marco", "Nota adhesiva",
                    "Texto",
                    "Mover vista", "Zoom", "---", "Acercar", "Alejar", "Zoom 100 %", "Ver todo el circuito",
                    "Zoom a la selección", "Escribir el zoom…",
                    "Centrar circuito", "---", "Cuadrícula",
                    "Ajustar", "Ajustar a", "Mostrar guías", "Agregar guía horizontal", "Agregar guía vertical",
                    "Borrar guías", "Mostrar hojas de impresión", "Valores de los buses", "Mirar adentro", "---",
                    "Siguiente problema", "Problema anterior", "---",
                    "Formas de onda", "---",
                    "Presentar lo que hace la IA", "Ritmo de Presentar", "---",
                    "Modo presentación (pantalla completa)", "Mostrar los atajos que se usan");
            assertThat(view.shortcutActions()).containsKeys("view.cycleGrid", "view.snap", "view.snapToParts",
                    "view.snapToGuides", "view.snapToGridLines", "view.showGuides", "view.addHorizontalGuide",
                    "view.addVerticalGuide", "view.clearGuides", "view.showSheets", "view.busValues", "view.magnifier");
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
                    "Avanzar al próximo flanco de reloj", "Avanzar un ciclo de reloj", "---",
                    "Pausar al detectar cortocircuito", "---", "Tabla de verdad…");
            assertThat(view.shortcutActions().get("simulation.advance").getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
            assertThat(view.shortcutActions().get("simulation.advanceToClockEdge").getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.SHIFT_DOWN_MASK));
            assertThat(view.shortcutActions().get("simulation.advanceClockCycle").getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F7, ShortcutCatalog.menuMask()));
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
            Action clockCycle = view.shortcutActions().get("simulation.advanceClockCycle");
            assertThat(stepMode.getValue(Action.LARGE_ICON_KEY))
                    .isSameAs(Icons.get(StepIcons.MODE, Icons.TOOLBAR_SIZE));
            assertThat(advance.getValue(Action.LARGE_ICON_KEY))
                    .isSameAs(Icons.get(StepIcons.ADVANCE, Icons.TOOLBAR_SIZE));
            assertThat(clockEdge.getValue(Action.LARGE_ICON_KEY))
                    .isSameAs(Icons.get(StepIcons.CLOCK_EDGE, Icons.TOOLBAR_SIZE));
            assertThat(clockCycle.getValue(Action.LARGE_ICON_KEY))
                    .isSameAs(Icons.get(StepIcons.CLOCK_CYCLE, Icons.TOOLBAR_SIZE));
            for (Action action : List.of(stepMode, advance, clockEdge, clockCycle)) {
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

    /**
     * The toolbar buttons of the newer features sit grouped by meaning (the user, 2026-11-08): the capture button right
     * after Guardar, «Formas de onda» right after the speed selector, and «Presentar lo que hace la IA» then «Modo
     * presentación» at the right edge, after a horizontal glue.
     */
    @Test
    void theNewToolbarButtonsAreGroupedByMeaning() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            List<java.awt.Component> bar = Arrays.asList(view.toolBar().getComponents());
            AbstractButton save = buttonOn(view, view.actions().save());
            AbstractButton undo = buttonOn(view, view.actions().undo());
            java.awt.Component capture = bar.stream()
                    .filter(cl.almejo.vsim.ui.toolbar.SplitButton.class::isInstance).findFirst().orElseThrow();
            java.awt.Component speed = bar.stream().filter(javax.swing.JComboBox.class::isInstance)
                    .findFirst().orElseThrow();
            AbstractButton waveforms = buttonOn(view, view.shortcutActions().get("waveforms.toggle"));
            AbstractButton present = buttonOn(view, view.shortcutActions().get("view.aiPresent"));
            AbstractButton presentation = Arrays.stream(view.toolBar().getComponents())
                    .filter(javax.swing.JToggleButton.class::isInstance).map(AbstractButton.class::cast)
                    .filter(button -> button.getAction() == null && button.getToolTipText() != null
                            && button.getToolTipText().contains("F11"))
                    .findFirst().orElseThrow();
            assertThat(bar.indexOf(capture)).as("capture after Guardar").isEqualTo(bar.indexOf(save) + 1);
            assertThat(bar.indexOf(undo)).as("capture before the separator before Deshacer")
                    .isEqualTo(bar.indexOf(capture) + 2);
            assertThat(bar.indexOf(waveforms)).as("waveforms after the speed").isEqualTo(bar.indexOf(speed) + 1);
            assertThat(bar.get(bar.indexOf(waveforms) + 1)).as("then the glue")
                    .isInstanceOf(javax.swing.Box.Filler.class);
            assertThat(bar.indexOf(present)).as("Presentar after the glue").isEqualTo(bar.indexOf(waveforms) + 2);
            assertThat(bar.indexOf(presentation)).as("Modo presentación last, after Presentar")
                    .isEqualTo(bar.indexOf(present) + 1).isEqualTo(bar.size() - 1);
        } finally {
            view.dispose();
        }
    }

    @Test
    void theWaveformsToggleFollowsTheDock() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            Action toggle = view.shortcutActions().get("waveforms.toggle");
            AbstractButton waveforms = buttonOn(view, toggle);
            assertThat(waveforms).isInstanceOf(javax.swing.JToggleButton.class);
            assertThat(waveforms.getIcon()).isNotNull();
            assertThat(waveforms.getHideActionText()).isTrue();
            assertThat(waveforms.getToolTipText()).isEqualTo("Formas de onda");
            assertThat(toggle.getValue(Action.ACCELERATOR_KEY)).as("no shortcut").isNull();
            assertThat(waveforms.isSelected()).isFalse();
            waveforms.doClick(0);
            assertThat(view.bottomDock().isDockShowing()).isTrue();
            assertThat(waveforms.isSelected()).isTrue();
            view.bottomDock().hideDock();
            assertThat(waveforms.isSelected()).as("follows the dock").isFalse();
        } finally {
            view.dispose();
        }
    }

    private static AbstractButton buttonOn(EditorView view, Action action) {
        assertThat(action).isNotNull();
        return Arrays.stream(view.toolBar().getComponents())
                .filter(AbstractButton.class::isInstance)
                .map(AbstractButton.class::cast)
                .filter(candidate -> candidate.getAction() == action)
                .findFirst().orElseThrow();
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
            assertThat(texts(view.menu(MenuId.WINDOW))).containsExactly("Nueva pestaña del mismo circuito", "Nueva ventana con este circuito",
                    "Mover el grupo a una ventana nueva", "Plegar / desplegar el grupo", "---", "Pestaña siguiente",
                    "Pestaña anterior", "Cerrar pestaña", "Cerrar los componentes de este circuito", "---",
                    "Dividir", "Siguiente panel");
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
    void theFavoritesAreSharedByTheWindows() {
        EditorView first = new EditorView(openDocument, services, NO_WINDOW);
        EditorView second = new EditorView(openDocument, services, NO_WINDOW);
        try {
            preferences.setPaletteFavorites(List.of("xor", "led"));
            assertThat(first.palette().visibleSections()).startsWith("Favoritos");
            assertThat(second.palette().visibleTypeIds()).startsWith("xor", "led");
        } finally {
            second.dispose();
            first.dispose();
        }
    }

    @Test
    void theRecentComponentsAreSharedByTheWindowsAndComeRightBelowFavorites() {
        EditorView first = new EditorView(openDocument, services, NO_WINDOW);
        EditorView second = new EditorView(openDocument, services, NO_WINDOW);
        try {
            first.canvas().setSize(800, 600);
            first.palette().choose("xor");
            CanvasMouseEvent click = new CanvasMouseEvent(new Point2D.Double(10, 10), new GridPoint(10, 10),
                    first.canvas().viewport().gridToScreen(10, 10), CanvasMouseEvent.PRIMARY, false, false, 1);
            first.canvas().tool().mouseMoved(first.canvas(), click);
            first.canvas().tool().mousePressed(first.canvas(), click);

            assertThat(second.palette().visibleSections()).startsWith("Favoritos", "Recientes");
            assertThat(second.palette().recentEntries()).extracting(entry -> entry.type().id())
                    .containsExactly("xor");
        } finally {
            second.dispose();
            first.dispose();
        }
    }

    @Test
    void quickInsertTakesInsertOnlyOnTheCanvas() {
        EditorView first = new EditorView(openDocument, services, NO_WINDOW);
        try {
            Object binding = first.canvas().getInputMap(JComponent.WHEN_FOCUSED)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0));
            assertThat(first.canvas().getActionMap().get(binding))
                    .isSameAs(first.shortcutActions().get("edit.quickInsert"));
            assertThat(ShortcutCatalog.find("edit.quickInsert").orElseThrow().scope())
                    .isEqualTo(Scope.CANVAS);
        } finally {
            first.dispose();
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
