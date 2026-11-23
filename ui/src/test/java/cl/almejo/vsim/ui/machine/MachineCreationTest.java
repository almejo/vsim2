package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.CreateMachineCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.PaletteEntry;
import cl.almejo.vsim.ui.panels.PaletteTransfer;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/** Palette ▸ Memoria ▸ «Máquina de estados»: one undo step makes the definition and its piece. */
class MachineCreationTest {

    /** A machine's id: {@code maquina-} and six base-36 characters, unique beyond its circuit. */
    private static final String MACHINE_ID = "maquina-[0-9a-z]{6}";

    private final TestToolContext context = TestToolContext.withNewDocument();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void theToolMakesTheDefinitionAndThePieceInOneUndoStep() {
        PlaceMachineTool tool = new PlaceMachineTool(Set::of);
        context.tool = tool;

        tool.mousePressed(context, TestToolContext.at(3, 4));

        ComponentDefinition definition = context.openDocument().document().definitions().getFirst();
        assertThat(definition.id()).matches(MACHINE_ID);
        assertThat(definition.name()).isEqualTo("Máquina 1");
        assertThat(definition.isMachine()).isTrue();
        Component piece = context.openDocument().document().components().get(0);
        assertThat(piece.typeId()).isEqualTo(SubcircuitTypes.typeIdOf(definition.id()));
        assertThat(piece.position()).isEqualTo(new GridPoint(3, 4));
        assertThat(context.selection.ids()).containsExactly(piece.id());
        assertThat(context.tool).isSameAs(context.defaultTool);
        assertThat(context.openDocument().document().definitions()).hasSize(1);

        context.openDocument().history().undo();

        assertThat(context.openDocument().document().components()).isEmpty();
        assertThat(context.openDocument().document().definitions()).isEmpty();
    }

    @Test
    void aSecondMachineGetsANewIdAndTheNextName() {
        MachineCreation.create(context.openDocument(), new GridPoint(0, 0), Set.of());
        MachineCreation.create(context.openDocument(), new GridPoint(20, 0), Set.of());

        assertThat(context.openDocument().document().definitions()).extracting(ComponentDefinition::id)
                .doesNotHaveDuplicates().allMatch(id -> id.matches(MACHINE_ID));
        assertThat(context.openDocument().document().definitions()).extracting(ComponentDefinition::name)
                .containsExactly("Máquina 1", "Máquina 2");
    }

    @Test
    void theMachinesOfTwoCircuitsHaveDifferentIdsSoAPasteOrTheLibraryNeverMixesThem() {
        OpenDocument other = OpenDocument.createEmpty(StandardComponentTypes.registry());
        try {
            ComponentDefinition here = MachineCreation.create(context.openDocument(), GridPoint.ORIGIN, Set.of())
                    .definition();
            ComponentDefinition there = MachineCreation.create(other, GridPoint.ORIGIN, Set.of()).definition();

            assertThat(there.name()).isEqualTo(here.name());
            assertThat(there.id()).isNotEqualTo(here.id());
        } finally {
            other.close();
        }
    }

    @Test
    void anIdOfTheLibraryOrTheCircuitIsNeverTaken() {
        RandomGenerator sequence = new RandomGenerator() {
            private long next;

            @Override
            public long nextLong() {
                return next++;
            }
        };
        CircuitDocument circuit = new CircuitDocument();
        circuit.putDefinition(MachineDefinitions.create("maquina-000001", "Otra"));

        ComponentDefinition next = MachineCreation.nextDefinition(circuit, Set.of("maquina-000000"), sequence);

        assertThat(next.id()).isEqualTo("maquina-000002");
    }

    @Test
    void aMachineCreatedInsideAComponentIsNewAndTakesTheCircuitsNextName() {
        OpenDocument circuit = context.openDocument();
        ComponentDefinition first = MachineCreation.create(circuit, new GridPoint(40, 40), Set.of()).definition();
        ComponentDefinition wrapper = new ComponentDefinition("envoltura", "Envoltura", "", new GridSize(6, 4),
                List.of(), new CircuitFragment(List.of(), List.of(), List.of()), Optional.empty(), 1);
        circuit.execute(new PutDefinitionsCommand(List.of(wrapper)));
        UiTestSupport.place(circuit, SubcircuitTypes.typeIdOf("envoltura"), 0, 0, Map.of());
        OpenDocument inside = circuit.openBody("envoltura", Optional.empty());
        try {
            ComponentDefinition second = MachineCreation.create(inside, new GridPoint(10, 10), Set.of()).definition();

            assertThat(second.id()).isNotEqualTo(first.id());
            assertThat(second.name()).isEqualTo("Máquina 2");
            assertThat(circuit.document().definitions()).extracting(ComponentDefinition::id)
                    .contains(first.id(), second.id());
            assertThat(circuit.document().definition("envoltura").orElseThrow().usedDefinitionIds())
                    .containsExactly(second.id());
        } finally {
            inside.close();
        }
    }

    @Test
    void createReturnsTheExecutedCommandWithItsPiece() {
        CreateMachineCommand command = MachineCreation.create(context.openDocument(), new GridPoint(1, 2), Set.of());

        assertThat(command.instanceId()).contains(context.openDocument().document().components().get(0).id());
    }

    @Test
    void doubleClickingTheCreatedMachineEnters() {
        MachineCreation.create(context.openDocument(), new GridPoint(0, 0), Set.of());
        Component piece = context.openDocument().document().components().get(0);
        SelectTool select = new SelectTool();
        context.tool = select;

        select.mousePressed(context, TestToolContext.event(2, 1, CanvasMouseEvent.PRIMARY, 1, false));
        select.mouseReleased(context, TestToolContext.event(2, 1, CanvasMouseEvent.PRIMARY, 1, false));
        select.mousePressed(context, TestToolContext.event(2, 1, CanvasMouseEvent.PRIMARY, 2, false));

        assertThat(context.entered).isEqualTo(piece);
    }

    @Test
    void thePaletteListsItInMemoryAndChoosingItArmsTheCreationTool() {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorView view = new EditorView(document, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        try {
            PaletteEntry entry = view.palette().entries().stream()
                    .filter(candidate -> candidate.id().equals(MachineCreation.PALETTE_ID)).findFirst()
                    .orElseThrow();
            assertThat(entry.name()).isEqualTo("Máquina de estados");
            assertThat(entry.category()).isEqualTo("Memoria");
            assertThat(view.palette().visibleSections()).doesNotContain("Máquinas");

            view.palette().chooseEntry(entry);

            assertThat(view.canvas().tool()).isInstanceOf(PlaceMachineTool.class);
        } finally {
            view.dispose();
            document.close();
        }
    }

    @Test
    void droppingTheEntryOnACircuitCanvasCreatesTheMachineThereInOneUndoStepAndSelectsIt() {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorView view = new EditorView(document, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        try {
            view.canvas().setSize(800, 600);
            java.awt.geom.Point2D spot = view.canvas().viewport().gridToScreen(10, 6);
            java.awt.Point where = new java.awt.Point((int) Math.round(spot.getX()), (int) Math.round(spot.getY()));

            boolean dropped = view.canvas().dropComponent(PaletteTransfer.encode(MachineCreation.PALETTE_ID), where);

            assertThat(dropped).isTrue();
            ComponentDefinition definition = document.document().definitions().getFirst();
            assertThat(definition.id()).matches(MACHINE_ID);
            assertThat(definition.name()).isEqualTo("Máquina 1");
            assertThat(document.document().components()).hasSize(1);
            Component piece = document.document().components().getFirst();
            assertThat(piece.typeId()).isEqualTo(SubcircuitTypes.typeIdOf(definition.id()));
            assertThat(piece.position()).isEqualTo(new GridPoint(10, 6));
            assertThat(view.canvas().selection().ids()).containsExactly(piece.id());

            document.history().undo();

            assertThat(document.document().components()).isEmpty();
            assertThat(document.document().definitions()).isEmpty();
        } finally {
            view.dispose();
            document.close();
        }
    }
}
