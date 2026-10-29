package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.StageUndoOutcome;
import cl.almejo.vsim.automation.StageUndoOutcome.StoppedAt;
import cl.almejo.vsim.automation.check.TextMeasure;
import cl.almejo.vsim.automation.edit.EditOperation;
import cl.almejo.vsim.automation.edit.EditOperation.Connect;
import cl.almejo.vsim.automation.edit.EditOperation.CreateComponent;
import cl.almejo.vsim.automation.edit.EditOperation.PlaceInstance;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditReceipt;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.PinTarget;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.edit.TaskStep;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.SetSwitchValueCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** «Presentar»'s storyboard (IA.2 Task 2, spec §6; Review Focus 3): stage frames, origins, «Dejar solo el resultado». */
class StoryboardTest {

    private static final DocumentId DOCUMENT = new DocumentId("circuit-1");
    private static final List<TaskStage> STORY = List.of(
            new TaskStage("Sumador de 2 bits", List.of("Coloco las compuertas", "Creo el componente")),
            new TaskStage("Componente de 2 bits", List.of("Coloco una instancia")),
            new TaskStage("Prueba de 2 bits", List.of("Coloco dos instancias")),
            new TaskStage("Sumador de 4 bits", List.of("Coloco dos instancias", "Creo el componente")));

    private final FakePresentingHost host = new FakePresentingHost();
    private final FakePresentingHost.Circuit circuit = host.open(DOCUMENT.value());
    private final AutomationApi api = new AutomationApi(host);
    /** The version the AI knows: the one of its last receipt, never read again after a stage begins. */
    private DocumentVersion known = host.onEditor(circuit::version);
    private int nextOperation = 1;

    // --- the user's story: 2-bit adder → its component → two instances → 4-bit (Review Focus 3) ---

    @Test
    void theAdderStoryLeavesFourFramesLeftToRightWithOnlyEachStagesWork() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Sumador de 4 bits", STORY);
        Map<Integer, Set<String>> created = tellTheStory(task);

        api.endTask(task.taskId(), false).task();

        List<Frame> frames = framesLeftToRight();
        assertThat(frames).extracting(Frame::title)
                .containsExactly("Sumador de 2 bits", "Componente de 2 bits", "Prueba de 2 bits", "Sumador de 4 bits");
        for (int index = 1; index < frames.size(); index++) {
            Frame previous = frames.get(index - 1);
            Frame next = frames.get(index);
            assertThat(next.topLeft().x()).isGreaterThanOrEqualTo(previous.topLeft().x() + previous.width()
                    + Storyboard.GAP);
            assertThat(next.topLeft().y()).isEqualTo(frames.getFirst().topLeft().y());
        }
        for (int stage = 0; stage < frames.size(); stage++) {
            Set<String> contents = values(FrameContents.of(circuit.document(), circuit.registry(), frames.get(stage)));
            assertThat(contents).as("stage %d", stage).isEqualTo(created.get(stage));
        }
        // The stages a component was made of without replacing them stay as «cómo se hizo»: locked.
        assertThat(frames).extracting(Frame::locked).containsExactly(true, false, false, true);
    }

    @Test
    void keepOnlyTheResultLeavesTheFourBitFrameAndEveryDefinitionAsOneUndoStep() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Sumador de 4 bits", STORY);
        Map<Integer, Set<String>> created = tellTheStory(task);
        api.endTask(task.taskId(), false).task();
        Set<String> before = elementIds();

        api.keepOnlyResult(task.taskId());

        assertThat(circuit.document().frames()).singleElement()
                .satisfies(frame -> assertThat(frame.title()).isEqualTo("Sumador de 4 bits"));
        Set<String> result = new HashSet<>(created.get(3));
        result.add(circuit.document().frames().getFirst().id().value());
        assertThat(elementIds()).isEqualTo(result);
        assertThat(circuit.document().definitions()).extracting(definition -> definition.name())
                .containsExactlyInAnyOrder("Sumador 2 bits", "Sumador 4 bits");
        host.onEditor(() -> {
            circuit.history().undo();
            return null;
        });
        assertThat(elementIds()).isEqualTo(before);
    }

    @Test
    void keepOnlyTheResultNeverRemovesTheUsersParts() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", STORY);
        api.beginStage(task.taskId(), 0, Optional.empty());
        apply(task, 0, place("first", "led", 2, 4));
        StageOrigin second = api.beginStage(task.taskId(), 1, Optional.empty());
        apply(task, 1, place("second", "led", second.x(), second.y()));
        String users = userPlaces("switch", 100, 100);
        api.endTask(task.taskId(), false).task();

        api.keepOnlyResult(task.taskId());

        assertThat(elementIds()).contains(users);
        assertThat(circuit.document().components()).hasSize(2);
    }

    @Test
    void keepOnlyTheResultIsRefusedWhileAStepWaitsAndWhileTheTaskRuns() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", STORY);
        api.beginStage(task.taskId(), 0, Optional.empty());
        api.editStep(request(task, 0, place("first", "led", 2, 4)), Optional.empty());

        assertRefused(() -> api.keepOnlyResult(task.taskId()), "waits for «Siguiente»");
        api.nextStep(task.taskId());
        assertRefused(() -> api.keepOnlyResult(task.taskId()), "has ended");
        assertThat(circuit.document().components()).hasSize(1);
    }

    // --- frames never take in what the stage did not add (Review Focus 3) ---

    /** The user's first look: a «Prueba: …» note's frame was a tiny box at the note's top-left corner. */
    @Test
    void aStageWithAMultiLineNoteGetsAFrameAroundEveryLineOfIt() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Una nota", STORY);
        StageOrigin origin = api.beginStage(task.taskId(), 0, Optional.empty());
        String text = "Prueba: haz clic en A0–A3, B0–B3 y Cin\npara elegir los números (A0 y B0 son las unidades).\n"
                + "Lee la suma en S3 S2 S1 S0 y el acarreo en Cout.";
        EditReceipt note = apply(task, 0, new EditOperation.AddNote(Optional.of("note"), text, origin.x(),
                origin.y()));

        api.endTask(task.taskId(), false).task();

        TextLabel label = (TextLabel) circuit.document().element(new ElementId(note.aliases().get("note")))
                .orElseThrow();
        TextMeasure.TextBox box = host.textMeasure().freeText(label);
        assertThat(box.height()).isGreaterThan(3); // three lines, not one
        Frame frame = circuit.document().frames().getFirst();
        assertThat(frame.topLeft().x()).isLessThanOrEqualTo((int) Math.floor(box.left()));
        assertThat(frame.topLeft().y() + Storyboard.TITLE_ROOM).isLessThanOrEqualTo((int) Math.floor(box.top()));
        assertThat(frame.topLeft().x() + frame.width()).isGreaterThanOrEqualTo(
                (int) Math.ceil(box.left() + box.width()));
        assertThat(frame.topLeft().y() + frame.height()).isGreaterThanOrEqualTo(
                (int) Math.ceil(box.top() + box.height()));
    }

    @Test
    void aUserPartNextToAStageIsNeverFramed() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Una etapa", STORY);
        api.beginStage(task.taskId(), 0, Optional.empty());
        EditReceipt stage = apply(task, 0, place("lamp", "led", 10, 10), place("other", "led", 20, 10));
        Bounds lamp = boundsOf(stage.aliases().get("lamp"));
        String users = userPlaces("led", lamp.x() - 4, lamp.y());

        api.endTask(task.taskId(), false).task();

        Frame frame = circuit.document().frames().getFirst();
        assertThat(values(FrameContents.of(circuit.document(), circuit.registry(), frame)))
                .containsExactlyInAnyOrder(stage.aliases().get("lamp"), stage.aliases().get("other"))
                .doesNotContain(users);
    }

    @Test
    void aStageAroundAUserPartGetsNoFrameAtAll() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Una etapa", STORY);
        api.beginStage(task.taskId(), 0, Optional.empty());
        apply(task, 0, place("left", "led", 0, 10), place("right", "led", 40, 10));
        userPlaces("switch", 20, 10);

        api.endTask(task.taskId(), false).task();

        assertThat(circuit.document().frames()).isEmpty();
    }

    // --- frames that line up, fit their labels and never nest (IA.3, Review Focus 5) ---

    @Test
    void framesLineUpOnTopWhateverRowTheAiStartsOn() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Cuatro etapas", STORY);
        int[] rowsBelowTheOrigin = {0, 3, 1, 5};
        for (int stage = 0; stage < rowsBelowTheOrigin.length; stage++) {
            StageOrigin origin = api.beginStage(task.taskId(), stage, Optional.empty());
            apply(task, stage, place("lamp" + stage, "led", origin.x(), origin.y() + rowsBelowTheOrigin[stage]));
        }

        api.endTask(task.taskId(), false).task();

        List<Frame> frames = framesLeftToRight();
        assertThat(frames).hasSize(4);
        assertThat(frames).extracting(frame -> frame.topLeft().y()).containsOnly(frames.getFirst().topLeft().y());
    }

    @Test
    void aLongPartLabelStaysInsideItsFrame() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Una etapa", STORY);
        StageOrigin origin = api.beginStage(task.taskId(), 0, Optional.empty());
        EditReceipt receipt = apply(task, 0, new PlacePart(Optional.of("carry"), "input-pin", origin.x(),
                origin.y() + 2, Rotation.R0, Map.of(), "Acarreo que llega de la etapa anterior"));

        api.endTask(task.taskId(), false).task();

        Component part = (Component) circuit.document().element(new ElementId(receipt.aliases().get("carry")))
                .orElseThrow();
        TextMeasure.TextBox label = host.textMeasure().componentLabel(
                circuit.registry().findTypeOf(part).orElseThrow(), part).orElseThrow();
        Frame frame = circuit.document().frames().getFirst();
        assertThat(frame.topLeft().x()).isLessThanOrEqualTo((int) Math.floor(label.left()));
        assertThat(frame.topLeft().x() + frame.width()).isGreaterThanOrEqualTo(
                (int) Math.ceil(label.left() + label.width()));
        assertThat(frame.topLeft().y()).isLessThanOrEqualTo((int) Math.floor(label.top()));
    }

    @Test
    void aUserPartBetweenTheOriginAndTheStagesPartsIsNeverFramed() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Una etapa", STORY);
        StageOrigin origin = api.beginStage(task.taskId(), 0, Optional.empty());
        EditReceipt stage = apply(task, 0, place("lamp", "led", origin.x(), origin.y() + 10));
        String users = userPlaces("led", origin.x(), origin.y());

        api.endTask(task.taskId(), false).task();

        Frame frame = circuit.document().frames().getFirst();
        assertThat(values(FrameContents.of(circuit.document(), circuit.registry(), frame)))
                .containsExactly(stage.aliases().get("lamp"))
                .doesNotContain(users);
    }

    /** The user's second run (2026-10-22): a second task's fix inside the first task's frame got a frame of its own. */
    @Test
    void aSecondTasksStageInsideAnEarlierFrameIsNotFramedAgain() {
        presenting();
        TaskInfo first = api.beginTask(DOCUMENT, "Nota de prueba", STORY);
        StageOrigin origin = api.beginStage(first.taskId(), 0, Optional.empty());
        apply(first, 0, place("left", "led", origin.x(), origin.y()), place("right", "led", origin.x() + 20,
                origin.y() + 8));
        api.endTask(first.taskId(), false).task();
        Frame outer = circuit.document().frames().getFirst();

        TaskInfo second = api.beginTask(DOCUMENT, "Ordeno la nota de prueba", STORY);
        apply(second, 0, new EditOperation.AddNote(Optional.of("note"), "Prueba: enciende A", origin.x() + 4,
                origin.y() + 4));
        api.endTask(second.taskId(), false).task();

        assertThat(circuit.document().frames()).containsExactly(outer);
    }

    @Test
    void aStageInsideTheUsersOwnFrameIsNotFramedAgain() {
        presenting();
        Frame users = host.onEditor(() -> {
            Frame frame = new Frame(circuit.document().newFrameId(), new GridPoint(100, 0), 60, 40, "Mío",
                    cl.almejo.vsim.core.document.FrameTint.NONE, false);
            circuit.history().execute(new AddElementsCommand(List.of(frame)));
            return frame;
        });
        known = host.onEditor(circuit::version);
        TaskInfo task = api.beginTask(DOCUMENT, "Dentro", STORY);
        apply(task, 0, place("lamp", "led", 110, 10));

        api.endTask(task.taskId(), false).task();

        assertThat(circuit.document().frames()).containsExactly(users);
    }

    @Test
    void aStagesFrameUndoesAsMarcoDeEtapa() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Una etapa", STORY);
        StageOrigin origin = api.beginStage(task.taskId(), 0, Optional.empty());
        apply(task, 0, place("lamp", "led", origin.x(), origin.y()));

        api.endTask(task.taskId(), false).task();

        assertThat(circuit.history().undoDescription()).contains("command.aiStageFrame");
        assertThat(cl.almejo.vsim.automation.edit.EditService.STAGE_FRAME_DESCRIPTION)
                .isEqualTo("command.aiStageFrame");
    }

    // --- origins ---

    @Test
    void theNextStagesOriginIsRightOfThePreviousFrameAndTheViewTravelsThere() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", STORY);
        StageOrigin first = api.beginStage(task.taskId(), 0, Optional.empty());
        apply(task, 0, place("lamp", "led", first.x(), first.y()));

        StageOrigin second = api.beginStage(task.taskId(), 1, Optional.empty());

        Frame frame = circuit.document().frames().getFirst();
        assertThat(second.x()).isGreaterThanOrEqualTo(frame.topLeft().x() + frame.width() + Storyboard.GAP);
        assertThat(host.travels()).containsExactly(new GridPoint(first.x(), first.y()),
                new GridPoint(second.x(), second.y()));
    }

    @Test
    void theNextStagesOriginNeverOverlapsWhatIsThere() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", STORY);
        StageOrigin first = api.beginStage(task.taskId(), 0, Optional.empty());
        apply(task, 0, place("lamp", "led", first.x(), first.y()));
        // The user's part lies just where the next stage would begin.
        String users = userPlaces("switch", first.x() + 20, first.y());
        Bounds blocking = boundsOf(users);

        StageOrigin second = api.beginStage(task.taskId(), 1, Optional.empty());

        Bounds stageArea = new Bounds(second.x() - 2, second.y() - 4, Storyboard.STAGE_WIDTH,
                Storyboard.STAGE_HEIGHT);
        assertThat(stageArea.intersects(blocking)).isFalse();
        for (DocumentElement element : circuit.document().elements()) {
            assertThat(stageArea.intersects(ElementBounds.of(element, circuit.registry()))).isFalse();
        }
    }

    @Test
    void theFirstStageBeginsRightOfTheUsersCircuit() {
        presenting();
        String users = userPlaces("switch", 0, 0);
        TaskInfo task = api.beginTask(DOCUMENT, "Una etapa", STORY);

        StageOrigin first = api.beginStage(task.taskId(), 0, Optional.empty());

        assertThat(first.x()).isGreaterThan(boundsOf(users).maximumX() + Storyboard.GAP);
    }

    @Test
    void inWorkingModeAStageBeginsWithoutFramesOrTravel() {
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", STORY);
        StageOrigin first = api.beginStage(task.taskId(), 0, Optional.empty());
        known = api.editStep(request(task, 0, place("lamp", "led", first.x(), first.y())), Optional.empty()).receipt()
                .version();

        StageOrigin second = api.beginStage(task.taskId(), 1, Optional.empty());
        api.endTask(task.taskId(), false).task();

        assertThat(second.x()).isGreaterThan(first.x());
        assertThat(circuit.document().frames()).isEmpty();
        assertThat(host.travels()).isEmpty();
    }

    @Test
    void aStageThatIsNotInThePlanOrBeginsWhileAStepWaitsIsRefused() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", STORY);

        assertThatThrownBy(() -> api.beginStage(task.taskId(), STORY.size(), Optional.empty()))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
        api.beginStage(task.taskId(), 0, Optional.empty());
        api.editStep(request(task, 0, place("first", "led", 2, 4)), Optional.empty());
        assertRefused(() -> api.beginStage(task.taskId(), 1, Optional.empty()), "waits for «Siguiente»");
    }

    // --- frames and the AI's version ---

    @Test
    void aStagesFrameNeverMakesTheAisNextStepStale() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", STORY);
        api.beginStage(task.taskId(), 0, Optional.empty());
        apply(task, 0, place("first", "led", 2, 4));
        DocumentVersion beforeTheFrame = known;

        StageOrigin second = api.beginStage(task.taskId(), 1, Optional.empty());
        EditReceipt receipt = apply(task, 1, place("second", "led", second.x(), second.y()));

        assertThat(receipt.applied()).isTrue();
        assertThat(receipt.version().value()).isGreaterThan(beforeTheFrame.value() + 1);
    }

    @Test
    void aUserEditAfterTheFrameStillMakesTheAisStepStale() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", STORY);
        api.beginStage(task.taskId(), 0, Optional.empty());
        apply(task, 0, place("first", "led", 2, 4));
        api.beginStage(task.taskId(), 1, Optional.empty());
        userPlaces("switch", 200, 200);

        assertThatThrownBy(() -> api.editStep(request(task, 1, place("second", "led", 100, 4)), Optional.empty()))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.STALE_DOCUMENT));
    }

    @Test
    void aStepOfALaterStageFramesTheStageBeforeEvenWithoutBeginStage() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", STORY);
        apply(task, 0, place("first", "led", 2, 4));

        EditReceipt receipt = apply(task, 1, place("second", "led", 60, 4));

        assertThat(receipt.applied()).isTrue();
        assertThat(circuit.document().frames()).singleElement()
                .satisfies(frame -> assertThat(frame.title()).isEqualTo("Sumador de 2 bits"));
    }

    // --- «Deshacer etapa» (Global Constraints) ---

    @Test
    void undoStageUndoesTheStagesStepsAndItsFrame() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", STORY);
        api.beginStage(task.taskId(), 0, Optional.empty());
        apply(task, 0, place("first", "led", 2, 4));
        apply(task, 0, place("second", "led", 12, 4));
        api.beginStage(task.taskId(), 1, Optional.empty());

        StageUndoOutcome outcome = api.undoStage(task.taskId(), 0);

        assertThat(outcome).isEqualTo(new StageUndoOutcome(0, 3, StoppedAt.NOTHING, false));
        assertThat(circuit.document().elements()).isEmpty();
    }

    @Test
    void undoStageStopsAtTheUsersEdit() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Una etapa", STORY);
        api.beginStage(task.taskId(), 0, Optional.empty());
        apply(task, 0, place("first", "led", 2, 4));
        String users = userPlaces("switch", 100, 100);

        StageUndoOutcome outcome = api.undoStage(task.taskId(), 0);

        assertThat(outcome).isEqualTo(new StageUndoOutcome(0, 0, StoppedAt.USER_EDIT, true));
        assertThat(elementIds()).contains(users).hasSize(2);
    }

    @Test
    void undoStagePassesOverTheUsersSwitchClicksOnTopOfItsSteps() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Una etapa", STORY);
        api.beginStage(task.taskId(), 0, Optional.empty());
        String switchId = apply(task, 0, place("input", "switch", 2, 4)).aliases().get("input");
        host.onEditor(() -> {
            circuit.history().execute(SetSwitchValueCommand.click(new ElementId(switchId), "0"));
            return null;
        });

        StageUndoOutcome outcome = api.undoStage(task.taskId(), 0);

        assertThat(outcome).isEqualTo(new StageUndoOutcome(0, 1, StoppedAt.NOTHING, false, 1));
        assertThat(circuit.document().elements()).isEmpty();
    }

    @Test
    void undoStageStopsAtAnotherStagesStep() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", STORY);
        api.beginStage(task.taskId(), 0, Optional.empty());
        apply(task, 0, place("first", "led", 2, 4));
        StageOrigin second = api.beginStage(task.taskId(), 1, Optional.empty());
        apply(task, 1, place("second", "led", second.x(), second.y()));

        StageUndoOutcome outcome = api.undoStage(task.taskId(), 0);

        // Another stage's step is on top: said so, never blamed on the user.
        assertThat(outcome).isEqualTo(new StageUndoOutcome(0, 0, StoppedAt.ANOTHER_STAGE, true));
        assertThat(api.undoStage(task.taskId(), 1)).isEqualTo(new StageUndoOutcome(1, 1, StoppedAt.NOTHING, true));
        assertThat(api.undoStage(task.taskId(), 0)).isEqualTo(new StageUndoOutcome(0, 2, StoppedAt.NOTHING, false));
        assertThat(circuit.document().elements()).isEmpty();
    }

    // --- the review's findings (IA.2 fix round) ---

    /** I1: a later stage that wires an earlier stage's parts is not a storyboard: nothing is removed. */
    @Test
    void keepOnlyTheResultIsRefusedWhenTheLastStageWiresTheFirstStagesParts() {
        presenting();
        List<TaskStage> plan = List.of(new TaskStage("Coloco las piezas", List.of("Coloco")),
                new TaskStage("Conecto", List.of("Conecto")));
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", plan);
        api.beginStage(task.taskId(), 0, Optional.empty());
        EditReceipt parts = applyIn(task, "Coloco las piezas",
                new PlacePart(Optional.of("a"), "input-pin", 2, 4, Rotation.R0, Map.of(), "A"),
                new PlacePart(Optional.of("s"), "output-pin", 20, 4, Rotation.R0, Map.of(), "S"));
        applyIn(task, "Conecto", connect(parts.aliases().get("a"), "pin", parts.aliases().get("s"), "pin"));
        api.endTask(task.taskId(), false).task();
        Set<String> before = elementIds();

        assertThat(api.storyboardState(task.taskId()).canKeepOnlyResult()).isFalse();
        assertRefused(() -> api.keepOnlyResult(task.taskId()), "not separate");
        assertThat(elementIds()).isEqualTo(before);
        assertThat(circuit.document().components()).hasSize(2);
    }

    /** I1: a stage that got no frame (the user's part lies among its parts) is not a storyboard either. */
    @Test
    void keepOnlyTheResultIsRefusedWhenAnEarlierStageHasNoFrame() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Dos etapas", STORY);
        api.beginStage(task.taskId(), 0, Optional.empty());
        apply(task, 0, place("left", "led", 0, 10), place("right", "led", 40, 10));
        userPlaces("switch", 20, 10);
        known = host.onEditor(circuit::version);
        StageOrigin second = api.beginStage(task.taskId(), 1, Optional.empty());
        apply(task, 1, place("second", "led", second.x(), second.y()));
        api.endTask(task.taskId(), false).task();
        assertThat(circuit.document().frames()).singleElement()
                .satisfies(frame -> assertThat(frame.title()).isEqualTo("Componente de 2 bits"));

        assertThat(api.storyboardState(task.taskId()).canKeepOnlyResult()).isFalse();
        assertRefused(() -> api.keepOnlyResult(task.taskId()), "not separate");
        assertThat(circuit.document().components()).hasSize(4);
    }

    @Test
    void theStoryCanKeepOnlyTheResultOnceItEnded() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Sumador de 4 bits", STORY);
        tellTheStory(task);

        assertThat(api.storyboardState(task.taskId()).canKeepOnlyResult()).as("not while it runs").isFalse();
        api.endTask(task.taskId(), false).task();

        assertThat(api.storyboardState(task.taskId())).isEqualTo(new StoryboardState(true, true));
    }

    /** I3: VSim's lock of the first stage's frame belongs to the stage that made the component of it. */
    @Test
    void undoStageUndoesTheLockItsComponentCausedAndNeverBlamesTheUser() {
        presenting();
        List<TaskStage> plan = List.of(new TaskStage("Sumador", List.of("Coloco")),
                new TaskStage("Componente", List.of("Creo", "Coloco")),
                new TaskStage("Prueba", List.of("Coloco")));
        TaskInfo task = api.beginTask(DOCUMENT, "Tres etapas", plan);
        StageOrigin first = api.beginStage(task.taskId(), 0, Optional.empty());
        EditReceipt gates = applyIn(task, "Sumador",
                new PlacePart(Optional.of("a"), "input-pin", first.x(), first.y() + 1, Rotation.R0, Map.of(), "A"),
                new PlacePart(Optional.of("s"), "output-pin", first.x() + 10, first.y() + 1, Rotation.R0, Map.of(),
                        "S"));
        StageOrigin second = api.beginStage(task.taskId(), 1, Optional.empty());
        applyIn(task, "Componente", new CreateComponent(Optional.empty(), "Comp", addedBy(gates), Map.of(), false));
        applyIn(task, "Componente", new PlaceInstance(Optional.empty(), "Comp", second.x(), second.y(),
                Rotation.R0));
        api.beginStage(task.taskId(), 2, Optional.empty());
        assertThat(frameTitled("Sumador").locked()).isTrue();

        StageUndoOutcome outcome = api.undoStage(task.taskId(), 1);

        // the stage's frame, the lock, the instance and the component
        assertThat(outcome).isEqualTo(new StageUndoOutcome(1, 4, StoppedAt.NOTHING, true));
        assertThat(circuit.document().definitions()).isEmpty();
        assertThat(frameTitled("Sumador").locked()).isFalse();
        assertThat(circuit.document().components()).hasSize(2);
    }

    /** I2: «Deshacer etapa» goes back stage by stage, passing over a stage that only checked. */
    @Test
    void undoTheLatestStageGoesBackStageByStageAndSkipsAStageWithoutSteps() {
        presenting();
        List<TaskStage> plan = List.of(new TaskStage("Uno", List.of("Coloco")),
                new TaskStage("Dos", List.of("Coloco")),
                new TaskStage("Comprobar", List.of("Compruebo la tabla")));
        TaskInfo task = api.beginTask(DOCUMENT, "Tres etapas", plan);
        api.beginStage(task.taskId(), 0, Optional.empty());
        applyIn(task, "Uno", place("first", "led", 2, 4));
        StageOrigin second = api.beginStage(task.taskId(), 1, Optional.empty());
        applyIn(task, "Dos", place("second", "led", second.x(), second.y()));
        api.beginStage(task.taskId(), 2, Optional.empty());
        api.endTask(task.taskId(), false).task();
        assertThat(api.storyboardState(task.taskId()).canUndoStage()).isTrue();

        assertThat(api.undoLatestStage(task.taskId()))
                .isEqualTo(new StageUndoOutcome(1, 2, StoppedAt.NOTHING, true));
        assertThat(circuit.document().components()).hasSize(1);
        assertThat(api.undoLatestStage(task.taskId()))
                .isEqualTo(new StageUndoOutcome(0, 2, StoppedAt.NOTHING, false));
        assertThat(circuit.document().elements()).isEmpty();
        assertThat(api.storyboardState(task.taskId()).canUndoStage()).isFalse();
        assertThat(api.undoLatestStage(task.taskId())).isEqualTo(StageUndoOutcome.NOTHING_TO_UNDO);
    }

    /** I2: the step that takes the layout-only guides away at the end never stops «Deshacer etapa». */
    @Test
    void undoTheLatestStageTakesBackTheLayoutGuidesCleanupWithIt() {
        presenting();
        TaskInfo task = api.beginTask(DOCUMENT, "Una etapa", STORY);
        api.beginStage(task.taskId(), 0, Optional.empty());
        known = api.editStep(request(task, 0, new EditOperation.AddGuide(Guide.Orientation.VERTICAL, 2, true)),
                Optional.empty()).receipt()
                .version();
        apply(task, 0, place("first", "led", 2, 4));
        api.endTask(task.taskId(), false).task();
        assertThat(circuit.document().guides()).isEmpty();

        StageUndoOutcome outcome = api.undoLatestStage(task.taskId());

        assertThat(outcome.stoppedAt()).isEqualTo(StoppedAt.NOTHING);
        assertThat(outcome.moreToUndo()).isFalse();
        assertThat(circuit.document().elements()).isEmpty();
    }

    // --- the story ---

    /** The four stages of the story, each built at its origin; returns what each stage added. */
    private Map<Integer, Set<String>> tellTheStory(TaskInfo task) {
        Map<Integer, Set<String>> created = new java.util.HashMap<>();
        StageOrigin first = api.beginStage(task.taskId(), 0, Optional.empty());
        int x = first.x();
        int y = first.y();
        EditReceipt gates = apply(task, 0,
                new PlacePart(Optional.of("a"), "input-pin", x, y + 1, Rotation.R0, Map.of(), "A"),
                new PlacePart(Optional.of("b"), "input-pin", x, y + 7, Rotation.R0, Map.of(), "B"),
                part("xor", "xor", x + 10, y + 2),
                new PlacePart(Optional.of("s"), "output-pin", x + 20, y + 3, Rotation.R0, Map.of(), "S"),
                connect("new:a", "pin", "new:xor", "in0"),
                connect("new:b", "pin", "new:xor", "in1"),
                connect("new:xor", "out", "new:s", "pin"));
        List<String> adder = addedBy(gates);
        apply(task, 0, new CreateComponent(Optional.empty(), "Sumador 2 bits", adder, Map.of(), false));
        created.put(0, new HashSet<>(adder));

        StageOrigin second = api.beginStage(task.taskId(), 1, Optional.empty());
        EditReceipt instance = apply(task, 1,
                new PlaceInstance(Optional.empty(), "Sumador 2 bits", second.x(), second.y(), Rotation.R0));
        created.put(1, new HashSet<>(addedBy(instance)));

        StageOrigin third = api.beginStage(task.taskId(), 2, Optional.empty());
        EditReceipt pair = apply(task, 2,
                new PlaceInstance(Optional.empty(), "Sumador 2 bits", third.x(), third.y(), Rotation.R0),
                new PlaceInstance(Optional.empty(), "Sumador 2 bits", third.x(), third.y() + 12, Rotation.R0));
        created.put(2, new HashSet<>(addedBy(pair)));

        StageOrigin fourth = api.beginStage(task.taskId(), 3, Optional.empty());
        EditReceipt wide = apply(task, 3,
                new PlacePart(Optional.of("a"), "input-pin", fourth.x(), fourth.y() + 1, Rotation.R0, Map.of(), "A"),
                new PlaceInstance(Optional.of("low"), "Sumador 2 bits", fourth.x() + 8, fourth.y(), Rotation.R0),
                new PlaceInstance(Optional.of("high"), "Sumador 2 bits", fourth.x() + 8, fourth.y() + 12,
                        Rotation.R0),
                new PlacePart(Optional.of("s"), "output-pin", fourth.x() + 24, fourth.y() + 1, Rotation.R0, Map.of(),
                        "S"));
        List<String> fourBits = addedBy(wide);
        apply(task, 3, new CreateComponent(Optional.empty(), "Sumador 4 bits", fourBits, Map.of(), false));
        created.put(3, new HashSet<>(fourBits));
        return created;
    }

    // --- helpers ---

    private void presenting() {
        host.present(DOCUMENT.value(), new PresentationSettings(true, PresentationPace.STEP_BY_STEP,
                Duration.ofSeconds(1)));
    }

    /** One step in «Paso a paso»: proposed, «Siguiente», applied; the AI then knows the receipt's version. */
    private EditReceipt apply(TaskInfo task, int stage, EditOperation... operations) {
        PresentedEdit edit = api.editStep(request(task, stage, operations), Optional.empty());
        EditReceipt receipt = edit.receipt();
        if (edit.proposed()) {
            api.nextStep(task.taskId());
            StepOutcome outcome = api.awaitStep(task.taskId(), Duration.ZERO);
            assertThat(outcome.status()).isEqualTo(StepStatus.APPLIED);
            receipt = outcome.receipt().orElseThrow();
        }
        known = receipt.version();
        return receipt;
    }

    /** One step of the stage titled {@code stage} (a plan other than {@link #STORY}), as {@link #apply} does. */
    private EditReceipt applyIn(TaskInfo task, String stage, EditOperation... operations) {
        String text = "Paso " + nextOperation;
        PresentedEdit edit = api.editStep(new EditRequest(DOCUMENT, known, "op-" + nextOperation++,
                Optional.of(new TaskStep(task.taskId(), stage, text, text)), List.of(operations), false),
                        Optional.empty());
        EditReceipt receipt = edit.receipt();
        if (edit.proposed()) {
            api.nextStep(task.taskId());
            StepOutcome outcome = api.awaitStep(task.taskId(), Duration.ZERO);
            assertThat(outcome.status()).isEqualTo(StepStatus.APPLIED);
            receipt = outcome.receipt().orElseThrow();
        }
        known = receipt.version();
        return receipt;
    }

    private Frame frameTitled(String title) {
        return circuit.document().frames().stream().filter(frame -> frame.title().equals(title)).findFirst()
                .orElseThrow();
    }

    private EditRequest request(TaskInfo task, int stage, EditOperation... operations) {
        String text = "Paso " + nextOperation;
        return new EditRequest(DOCUMENT, known, "op-" + nextOperation++,
                Optional.of(new TaskStep(task.taskId(), STORY.get(stage).title(), text, text)), List.of(operations),
                false);
    }

    private String userPlaces(String typeId, int x, int y) {
        return host.onEditor(() -> {
            Component part = new Component(circuit.document().newComponentId(), typeId, new GridPoint(x, y),
                    Rotation.R0, Map.of(), "");
            circuit.history().execute(new AddElementsCommand(List.of(part)));
            return part.id().value();
        });
    }

    private static List<String> addedBy(EditReceipt receipt) {
        return receipt.changes().added();
    }

    private Bounds boundsOf(String id) {
        return ElementBounds.of(circuit.document().element(new ElementId(id)).orElseThrow(), circuit.registry());
    }

    private List<Frame> framesLeftToRight() {
        List<Frame> frames = new ArrayList<>(circuit.document().frames());
        frames.sort(Comparator.comparingInt(frame -> frame.topLeft().x()));
        return frames;
    }

    private Set<String> elementIds() {
        Set<String> ids = new HashSet<>();
        circuit.document().elements().forEach(element -> ids.add(element.id().value()));
        return ids;
    }

    private static Set<String> values(Set<ElementId> ids) {
        Set<String> values = new HashSet<>();
        ids.forEach(id -> values.add(id.value()));
        return values;
    }

    private static PlacePart place(String alias, String typeId, int x, int y) {
        return part(alias, typeId, x, y);
    }

    private static PlacePart part(String alias, String typeId, int x, int y) {
        return new PlacePart(Optional.of(alias), typeId, x, y, Rotation.R0, Map.of(), "");
    }

    private static Connect connect(String from, String fromPin, String to, String toPin) {
        return new Connect(PinTarget.named(from, fromPin), PinTarget.named(to, toPin), List.of());
    }

    private static void assertRefused(Runnable call, String saying) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AutomationException.class, refusal -> {
            assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED);
            assertThat(refusal.getMessage()).contains(saying);
        });
    }
}
