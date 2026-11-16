package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.panels.ColorSwatches;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A colour per state (plan 2026-11-14, hito 3, task 7): Propiedades of a state offers «sin color» and the eight
 * colours as swatches, each named; a pick is one undo step; with several states selected the row colours them all at
 * once, in one step.
 */
class MachineStateColorTest {

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
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
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        place = document.openBody(MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()));
        view = new EditorView(place, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        mode = MachineMode.of(view).orElseThrow();
    }

    @AfterEach
    void close() {
        view.dispose();
        place.close();
        document.close();
        service.close();
    }

    private StateMachine machine() {
        return mode.canvas().machine().orElseThrow();
    }

    private Optional<StateColor> colourOf(String stateId) {
        return machine().state(stateId).map(MachineState::color).orElseThrow();
    }

    private ColorSwatches<Optional<StateColor>> swatches() {
        return mode.inspectorRows().stateColor().orElseThrow();
    }

    private static int indexOf(StateColor color) {
        return color.ordinal() + 1;
    }

    @Test
    void aStateOffersNoColourFirstAndTheEightNamedColours() {
        mode.selection().selectState("s1");

        List<Optional<StateColor>> expected = new ArrayList<>();
        expected.add(Optional.empty());
        for (StateColor color : StateColor.values()) {
            expected.add(Optional.of(color));
        }
        assertThat(swatches().values()).containsExactlyElementsOf(expected);
        assertThat(swatches().selectedValue()).isEmpty();
        assertThat(swatches().swatch(0).getToolTipText()).isEqualTo("sin color");
        assertThat(swatches().swatch(indexOf(StateColor.PINK)).getToolTipText()).isEqualTo("Rosado");
        assertThat(swatches().swatch(indexOf(StateColor.TURQUOISE)).getAccessibleContext().getAccessibleName())
                .isEqualTo("Turquesa");
    }

    @Test
    void aPickColoursTheStateInOneUndoStep() {
        mode.selection().selectState("s1");

        swatches().swatch(indexOf(StateColor.BLUE)).doClick();
        assertThat(colourOf("s1")).contains(StateColor.BLUE);
        assertThat(colourOf("s0")).isEmpty();

        perform(view.actions().undo());
        assertThat(colourOf("s1")).isEmpty();
        assertThat(swatches().selectedValue()).isEmpty();

        perform(view.actions().redo());
        assertThat(colourOf("s1")).contains(StateColor.BLUE);
        assertThat(swatches().selectedValue()).contains(StateColor.BLUE);

        swatches().swatch(0).doClick();
        assertThat(colourOf("s1")).isEmpty();
    }

    @Test
    void withSeveralStatesSelectedTheRowColoursThemAllInOneStep() {
        mode.canvas().edit(current -> current.withState("s0",
                state -> state.withColor(Optional.of(StateColor.RED))));
        mode.selection().set(new LinkedHashSet<>(List.of("s0", "s1")), new LinkedHashSet<>(Set.of("t1")));

        assertThat(swatches().showsNoChoice()).as("two different colours").isTrue();
        swatches().swatch(indexOf(StateColor.GREEN)).doClick();

        assertThat(colourOf("s0")).contains(StateColor.GREEN);
        assertThat(colourOf("s1")).contains(StateColor.GREEN);
        assertThat(colourOf("s2")).isEmpty();
        perform(view.actions().undo());
        assertThat(colourOf("s0")).contains(StateColor.RED);
        assertThat(colourOf("s1")).isEmpty();
    }

    @Test
    void severalArrowsWithoutAStateHaveNoColourRow() {
        mode.selection().set(new LinkedHashSet<>(), new LinkedHashSet<>(List.of("t1", "t2")));

        assertThat(mode.inspectorRows().stateColor()).isEmpty();
    }
}
