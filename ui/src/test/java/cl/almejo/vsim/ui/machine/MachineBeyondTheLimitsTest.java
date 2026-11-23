package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.MachinesBeyondTheLimits;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A hand-made file with a machine beyond its limits (more states, inputs, outputs or tests than the editor allows,
 * final review of hitos 1–4): it opens, paints and tests without failing, keeps everything, and Propiedades lists each
 * limit passed as a problem («Problems are never hidden»).
 */
class MachineBeyondTheLimitsTest {

    private OpenDocument document;
    private OpenDocument place;
    private EditorView view;
    private MachineMode mode;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(new MachineModeExtension()));
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        document.execute(new PutDefinitionsCommand(List.of(MachineDefinitions.regenerated(
                MachineFixtures.definition(MachinesBeyondTheLimits.everyLimit())))));
        UiTestSupport.place(document, SubcircuitTypes.typeIdOf(MachineFixtures.DEFINITION_ID), 0, 0, Map.of());
        place = document.openBody(MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()));
        view = new EditorView(place, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        mode = MachineMode.of(view).orElseThrow();
        mode.canvas().setBounds(0, 0, 900, 600);
    }

    @AfterEach
    void close() {
        view.dispose();
        place.close();
        document.close();
    }

    @Test
    void itPaintsKeepsEverythingAndPropertiesListsEachLimitPassed() {
        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            assertThatCode(() -> mode.canvas().paint(graphics)).doesNotThrowAnyException();
        } finally {
            graphics.dispose();
        }

        assertThat(mode.canvas().machine().orElseThrow()).isEqualTo(MachinesBeyondTheLimits.everyLimit());
        mode.selection().clear();
        assertThat(String.join("\n", mode.inspectorRows().problemLines())).contains(
                Messages.format("problem.machine.short.overStates", MachinesBeyondTheLimits.STATES,
                        MachineLimits.MAXIMUM_STATES),
                Messages.format("problem.machine.short.overInputs", MachinesBeyondTheLimits.INPUTS,
                        MachineLimits.MAXIMUM_INPUTS),
                Messages.format("problem.machine.short.overOutputs", MachinesBeyondTheLimits.OUTPUTS,
                        MachineLimits.MAXIMUM_OUTPUTS),
                Messages.format("problem.machine.short.overTests", MachinesBeyondTheLimits.TESTS,
                        MachineTests.MAXIMUM_TESTS));
    }

    @Test
    void theTestModeShowsEveryTestAndAddsNoMore() {
        mode.startTest();

        MachineTestTable table = (MachineTestTable) view.bottomDock().content().orElseThrow();
        assertThat(table.grid().model().tests()).hasSize(MachinesBeyondTheLimits.TESTS);
        assertThat(table.grid().model().canAddTest()).isFalse();
        assertThat(table.grid().model().rows()).filteredOn(row -> row.kind() == MachineTestTableModel.Kind.INPUT)
                .hasSize(MachinesBeyondTheLimits.INPUTS);
        mode.testMode().orElseThrow().end();
    }
}
