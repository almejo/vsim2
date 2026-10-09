package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.Point;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A drop or a paste from another circuit keeps this circuit's version of a nested definition (R4) and says so with
 * the palette's message (KeptVersions), after the drop finishes.
 */
class KeptVersionsDropTest {

    @TempDir
    Path directory;

    private final ComponentDefinition inverter = ExampleDefinitions.inverter();
    private TemplateFixture fixture;

    @BeforeEach
    void create() {
        fixture = new TemplateFixture(directory.resolve("library"));
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(inverter)));
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    /** One double inverter whose nested inverter is {@code nestedInverter}, as another circuit would carry it. */
    private static String doubleInverterFragment(ComponentDefinition nestedInverter) {
        ComponentDefinition doubleInverter = ExampleDefinitions.doubleInverter();
        Component instance = SubcircuitTypes.typeOf(doubleInverter).newComponent(new ElementId("c1"),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        return new FragmentFormat(StandardComponentTypes.registry()).write(new CircuitFragment(List.of(instance),
                List.of(), List.of(), List.of(nestedInverter, doubleInverter)));
    }

    private static void flushEventQueue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private String keptNegador() {
        return Messages.format("library.place.keptVersion", Messages.format("library.place.componentName", "Negador"));
    }

    @Test
    void droppingAFragmentWithAnotherNestedVersionSaysTheCircuitKeepsItsOwn() throws Exception {
        String json = doubleInverterFragment(inverter.withName("Negador"));

        SwingUtilities.invokeAndWait(() -> {
            assertThat(fixture.view.canvas().dropFragment(json, new Point(40, 40))).isTrue();
            assertThat(fixture.dialogs.messages).as("no dialog inside the drop").isEmpty();
        });
        flushEventQueue();

        assertThat(fixture.dialogs.messages).containsExactly(keptNegador());
    }

    @Test
    void pastingAFragmentWithAnotherNestedVersionSaysTheCircuitKeepsItsOwn() throws Exception {
        FragmentClipboard.system().put(doubleInverterFragment(inverter.withName("Negador")), GridPoint.ORIGIN);

        fixture.view.selectionActions().pasteAt(Optional.of(new GridPoint(5, 5)));
        flushEventQueue();

        assertThat(fixture.dialogs.messages).containsExactly(keptNegador());
    }

    @Test
    void theSameNestedVersionSaysNothing() throws Exception {
        fixture.view.canvas().dropFragment(doubleInverterFragment(inverter), new Point(40, 40));
        flushEventQueue();

        assertThat(fixture.dialogs.messages).isEmpty();
    }
}
