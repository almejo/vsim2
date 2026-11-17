package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineBody;
import cl.almejo.vsim.core.machine.MachineChannels;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.machine.StateMachineTypes;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.Trace;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.machine.MachineFixtures;
import cl.almejo.vsim.ui.machine.MachineMode;
import cl.almejo.vsim.ui.machine.MachineModeExtension;
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

import javax.swing.AbstractButton;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The machine tab's wave strip (hito 2, task 6): under the diagram, the view's own waveform panel shows the copy
 * entered through, inputs, «estado» with the state names, then outputs, as in the mockup; «Sin valores» shows
 * nothing and says why; leaving machine mode gives the panel its probe rows back; a circuit tab never shows a machine's
 * own channels.
 */
class MachineWaveStripTest {

    /** A switch starts at 1 by default; the clock and {@code x} start at 0. */
    private static final Map<String, Object> SWITCH_OFF = Map.of("value", "0");
    private static final String WRAPPER = "envoltura";

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<EditorView> views = new ArrayList<>();
    private final List<OpenDocument> places = new ArrayList<>();
    private LibraryService service;
    private OpenDocument document;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        service = new LibraryService(preferences, new ComponentLibrary(), registry, Runnable::run);
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        places.forEach(OpenDocument::close);
        if (document != null) {
            document.close();
        }
        service.close();
    }

    private EditorView viewOf(OpenDocument shown, boolean machineMode) {
        List<EditorExtension> extensions = Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE)
                .stream()
                .filter(extension -> machineMode || !(extension instanceof MachineModeExtension))
                .toList();
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), extensions);
        EditorView view = new EditorView(shown, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        views.add(view);
        return view;
    }

    private OpenDocument placeOf(String definitionId, Optional<String> path) {
        OpenDocument place = document.openBody(definitionId, path);
        places.add(place);
        return place;
    }

    private static WaveformPanel panelOf(EditorView view) {
        WaveformPanel panel = WaveformFeatures.of(view).orElseThrow().panel();
        panel.overrideShowing(() -> true);
        return panel;
    }

    /** A switch at 0 wired to {@code pinName} of {@code piece} from 4 units to its left. */
    private Component switchOn(Component piece, ComponentDefinition machine, String pinName) {
        return switchOn(piece, machine, pinName, SWITCH_OFF);
    }

    /** A switch with {@code parameters} wired to {@code pinName} of {@code piece} from 4 units to its left. */
    private Component switchOn(Component piece, ComponentDefinition machine, String pinName,
                               Map<String, Object> parameters) {
        GridPoint pin = document.registry().typeOf(piece).pinLocation(piece,
                machine.pinIndexOf(pinName).orElseThrow());
        GridPoint end = pin.translate(-4, 0);
        ComponentType switchType = document.registry().require(StandardComponentTypes.SWITCH);
        Component probe = switchType.newComponent(new ElementId("probe"), GridPoint.ORIGIN, Rotation.R0, parameters,
                "");
        GridPoint offset = switchType.pinLocation(probe, 0);
        Component placed = UiTestSupport.place(document, StandardComponentTypes.SWITCH, end.x() - offset.x(),
                end.y() - offset.y(), parameters);
        UiTestSupport.addWire(document, pin.x(), pin.y(), end.x(), end.y());
        return placed;
    }

    private void pulse(Component clock) {
        document.interact(clock.id());
        document.interact(clock.id());
    }

    /** The texts of every value {@code channelId} took, oldest first, as the strip writes them. */
    private static List<String> history(WaveformPanel panel, String channelId) {
        Trace trace = panel.displayed().trace(channelId).orElseThrow();
        List<String> texts = new ArrayList<>();
        for (int index = 0; index < trace.eventCount(); index++) {
            Optional<Value> value = trace.eventValue(index);
            texts.add(ChannelRows.valueText(value, "hex", panel.valueNames().get(channelId)));
        }
        return texts;
    }

    @Test
    void theTabOfACopyShowsItsInputsStateAndOutputsUnderTheDiagram() {
        ComponentDefinition machine = MachineDefinitions.regenerated(
                MachineFixtures.definition(MachineFixtures.detector()));
        document = OpenDocument.createEmpty(registry);
        document.execute(new PutDefinitionsCommand(List.of(machine)));
        Component piece = UiTestSupport.place(document, SubcircuitTypes.typeIdOf(machine.id()), 20, 0, Map.of());
        Component input = switchOn(piece, machine, "x");
        Component clock = switchOn(piece, machine, MachineLimits.CLOCK);
        String copy = piece.id().value();
        EditorView view = viewOf(placeOf(machine.id(), Optional.of(copy)), true);
        WaveformPanel panel = panelOf(view);

        assertThat(view.bottomDock().isDockShowing()).as("the strip opens with the tab").isTrue();
        assertThat(panel.channelNames()).containsExactly("x", "estado", "Z");
        assertThat(panel.title()).contains("Onda de Detector 11");

        document.interact(input.id());
        pulse(clock);
        pulse(clock);
        pulse(clock);
        UiTestSupport.awaitSimulation(document);
        String state = MachineChannels.stateChannelId(copy);
        String output = MachineChannels.outputChannelId(copy, "Z");
        UiTestSupport.waitUntil(() -> document.latestWaveforms().trace(state)
                .filter(trace -> trace.eventCount() >= 4).isPresent()
                && document.latestWaveforms().trace(output)
                .filter(trace -> trace.eventValue(trace.eventCount() - 1).filter(Value.ONE::equals).isPresent())
                .isPresent());
        panel.timerTick();

        assertThat(history(panel, state)).containsSubsequence("S0", "S1", "S2");
        assertThat(history(panel, state).getLast()).isEqualTo("S2");
        assertThat(panel.names().values()).containsExactly("1", "S2", "1");
        long atS1 = timeOf(panel.displayed().trace(state).orElseThrow(), Value.of(2, 1));
        panel.axis().setCursor(atS1);
        assertThat(panel.names().values()).containsExactly("1", "S1", "0");

        int stateRowMiddle = WaveformPainter.ROW_HEIGHT + WaveformPainter.ROW_HEIGHT / 2;
        double atS1Pixel = panel.axis().xOf(atS1) + 1;
        assertThat(panel.hoverText(atS1Pixel, stateRowMiddle)).as("hovering a state names it in full")
                .isEqualTo("S1 · " + panel.hoverText(atS1Pixel));
        assertThat(panel.hoverText(atS1Pixel, WaveformPainter.ROW_HEIGHT / 2)).as("a plain row: only the time")
                .isEqualTo(panel.hoverText(atS1Pixel));
    }

    @Test
    void theStateRowTakesEachStatesColour() {
        StateMachine detector = MachineFixtures.detector()
                .withState("s1", state -> state.withColor(Optional.of(StateColor.TURQUOISE)));
        ComponentDefinition machine = MachineDefinitions.regenerated(MachineFixtures.definition(detector));
        document = OpenDocument.createEmpty(registry);
        document.execute(new PutDefinitionsCommand(List.of(machine)));
        Component piece = UiTestSupport.place(document, SubcircuitTypes.typeIdOf(machine.id()), 20, 0, Map.of());
        String copy = piece.id().value();
        WaveformPanel panel = panelOf(viewOf(placeOf(machine.id(), Optional.of(copy)), true));

        String state = MachineChannels.stateChannelId(copy);
        assertThat(panel.valueColors()).containsOnlyKeys(state);
        long codeOfS1 = StateMachineTypes.stateCode(detector, "s1").getAsInt();
        long codeOfS0 = StateMachineTypes.stateCode(detector, "s0").getAsInt();
        assertThat(panel.valueColors().get(state).apply(codeOfS1)).contains(StateColor.TURQUOISE);
        assertThat(panel.valueColors().get(state).apply(codeOfS0)).isEmpty();
    }

    @Test
    void withTheInputsAsABusTheStripStillShowsOneRowPerNameReadFromItsBit() {
        StateMachine detector = MachineFixtures.detector();
        ComponentDefinition machine = MachineDefinitions.regenerated(MachineFixtures.definition(
                detector.withPinNames(List.of("x", "y"), detector.outputNames()).withBus(true, false)));
        document = OpenDocument.createEmpty(registry);
        document.execute(new PutDefinitionsCommand(List.of(machine)));
        Component piece = UiTestSupport.place(document, SubcircuitTypes.typeIdOf(machine.id()), 20, 0, Map.of());
        // x = 1 (bit 0), y = 0 (bit 1)
        switchOn(piece, machine, MachineBody.BUS_INPUT_NAME, Map.of("value", "1", StandardComponentTypes.WIDTH, 2));
        Component clock = switchOn(piece, machine, MachineLimits.CLOCK);
        String copy = piece.id().value();
        WaveformPanel panel = panelOf(viewOf(placeOf(machine.id(), Optional.of(copy)), true));

        assertThat(panel.channelNames()).containsExactly("x", "y", "estado", "Z");
        pulse(clock);
        pulse(clock);
        UiTestSupport.awaitSimulation(document);
        String output = MachineChannels.outputChannelId(copy, "Z");
        UiTestSupport.waitUntil(() -> document.latestWaveforms().trace(output)
                .filter(trace -> trace.eventValue(trace.eventCount() - 1).filter(Value.ONE::equals).isPresent())
                .isPresent());
        panel.timerTick();

        assertThat(panel.names().values()).containsExactly("1", "0", "S2", "1");
        assertThat(history(panel, MachineChannels.inputChannelId(copy, "x"))).as("Z until the switch drives it")
                .endsWith("1").doesNotContain("0");
        assertThat(panel.displayed().trace(MachineChannels.inputChannelId(copy, "y")).orElseThrow().width())
                .isEqualTo(1);
    }

    private static long timeOf(Trace trace, Value value) {
        for (int index = 0; index < trace.eventCount(); index++) {
            if (trace.eventValue(index).filter(value::equals).isPresent()) {
                return trace.eventTime(index);
            }
        }
        throw new AssertionError("no " + value + " in the trace");
    }

    @Test
    void aMealyOutputChangesInTheStripBetweenTwoEdges() {
        StateMachine mealy = new StateMachine(
                List.of(new MachineState("s0", "S0", new DiagramPoint(0, 0), List.of())),
                List.of(new MachineTransition("t1", "s0", "s0", "x", List.of(new OutputAssignment("Z", true))),
                        new MachineTransition("t2", "s0", "s0", "", List.of())),
                Optional.of("s0"), List.of("x"), List.of("Z"), false, false, StateMachine.EDGE_FALLING, List.of());
        ComponentDefinition machine = MachineDefinitions.regenerated(MachineFixtures.definition(mealy));
        document = OpenDocument.createEmpty(registry);
        document.execute(new PutDefinitionsCommand(List.of(machine)));
        Component piece = UiTestSupport.place(document, SubcircuitTypes.typeIdOf(machine.id()), 20, 0, Map.of());
        Component input = switchOn(piece, machine, "x");
        String copy = piece.id().value();
        WaveformPanel panel = panelOf(viewOf(placeOf(machine.id(), Optional.of(copy)), true));

        document.interact(input.id());
        UiTestSupport.awaitSimulation(document);
        document.interact(input.id());
        UiTestSupport.awaitSimulation(document);
        String output = MachineChannels.outputChannelId(copy, "Z");
        UiTestSupport.waitUntil(() -> document.latestWaveforms().trace(output)
                .filter(trace -> trace.eventCount() >= 4).isPresent());
        panel.timerTick();

        assertThat(history(panel, output)).as("no clock edge at all").containsSubsequence("0", "1", "0");
        assertThat(history(panel, MachineChannels.stateChannelId(copy))).as("no state before S0, and no other")
                .containsOnly(ChannelRows.NO_NAME, "S0");
    }

    @Test
    void withSinValoresTheStripIsEmptyAndSaysHowToSeeAWave() {
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        EditorView view = viewOf(placeOf(MachineFixtures.DEFINITION_ID, Optional.empty()), true);
        WaveformPanel panel = panelOf(view);

        assertThat(panel.channelNames()).isEmpty();
        assertThat(panel.title()).contains("Entra por una copia para ver su onda");
        assertThat(panel.hasChannels()).isFalse();
    }

    /** A circuit whose only piece {@code w} wraps the detector as {@code m}; returns the tab entered at {@code w/m}. */
    private EditorView tabOfAWrappedCopy() {
        ComponentDefinition machine = MachineDefinitions.regenerated(
                MachineFixtures.definition(MachineFixtures.detector()));
        Component copy = new Component(new ElementId("m"), SubcircuitTypes.typeIdOf(machine.id()), GridPoint.ORIGIN,
                Rotation.R0, Map.of(), "");
        ComponentDefinition wrapper = new ComponentDefinition(WRAPPER, "Envoltura", "", new GridSize(6, 4), List.of(),
                new CircuitFragment(List.of(copy), List.of(), List.of()), Optional.empty(), 1);
        document = OpenDocument.createEmpty(registry);
        document.execute(new PutDefinitionsCommand(List.of(machine, wrapper)));
        document.execute(new AddElementsCommand(List.of(new Component(new ElementId("w"),
                SubcircuitTypes.typeIdOf(WRAPPER), new GridPoint(10, 10), Rotation.R0, Map.of(), ""))));
        return viewOf(placeOf(machine.id(), Optional.of("w/m")), true);
    }

    @Test
    void theStripsLinkAddsTheCopysStateAndOutputsToTheCircuitsPanel() {
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        String copy = MachineFixtures.copies(document).get(0);
        WaveformPanel panel = panelOf(viewOf(placeOf(MachineFixtures.DEFINITION_ID, Optional.of(copy)), true));
        AbstractButton link = panel.button(Messages.text("waveform.menu.showMachine"));
        assertThat(link.isEnabled()).isTrue();

        link.doClick(0);

        assertThat(document.document().probes()).extracting(probe -> probe.target()).containsExactly(
                new ProbeTarget.MachineStateTarget(new ElementId(copy)),
                new ProbeTarget.PinTarget(new ElementId(copy), document.registry()
                        .typeOf(document.document().component(new ElementId(copy)).orElseThrow())
                        .pinIndexOf("Z").orElseThrow()));
        document.history().undo();
        assertThat(document.document().probes()).as("one undo step of the circuit").isEmpty();
    }

    @Test
    void insideAComponentTheStripsLinkIsOffAndSaysWhy() {
        WaveformPanel panel = panelOf(tabOfAWrappedCopy());

        AbstractButton link = panel.button(Messages.text("waveform.menu.showMachine"));

        assertThat(link.isEnabled()).isFalse();
        assertThat(link.getToolTipText()).isEqualTo("Solo para máquinas puestas en el circuito principal");
    }

    @Test
    void aCopyInsideAComponentShowsItsOwnWave() {
        WaveformPanel panel = panelOf(tabOfAWrappedCopy());

        UiTestSupport.awaitSimulation(document);
        UiTestSupport.waitUntil(() -> document.latestWaveforms().trace(MachineChannels.stateChannelId("w/m"))
                .isPresent());
        panel.timerTick();

        assertThat(panel.channelIds()).containsExactly(MachineChannels.inputChannelId("w/m", "x"),
                MachineChannels.stateChannelId("w/m"), MachineChannels.outputChannelId("w/m", "Z"));
        assertThat(panel.title()).contains("Onda de Envoltura › Detector 11");
        assertThat(panel.names().values().get(1)).isEqualTo("S0");
    }

    @Test
    void leavingMachineModeGivesThePanelItsOwnRowsBack() {
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        String copy = MachineFixtures.copies(document).get(0);
        EditorView view = viewOf(placeOf(MachineFixtures.DEFINITION_ID, Optional.of(copy)), false);
        WaveformPanel panel = panelOf(view);
        assertThat(panel.channelNames()).as("a component tab's panel: no probes").isEmpty();

        Runnable uninstall = MachineMode.install(view);
        assertThat(panel.channelNames()).containsExactly("x", "estado", "Z");
        uninstall.run();

        assertThat(panel.channelNames()).isEmpty();
        assertThat(panel.title()).isEmpty();
        assertThat(panel.hasChannels()).isFalse();
    }

    @Test
    void aCircuitTabNeverShowsAMachinesOwnChannels() {
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        WaveformPanel panel = panelOf(viewOf(document, true));
        UiTestSupport.awaitSimulation(document);
        UiTestSupport.waitUntil(() -> !document.latestWaveforms().traces().isEmpty());

        assertThat(panel.channelIds()).isEmpty();
        assertThat(panel.hasChannels()).isFalse();
        assertThat(panel.title()).isEmpty();
    }
}
