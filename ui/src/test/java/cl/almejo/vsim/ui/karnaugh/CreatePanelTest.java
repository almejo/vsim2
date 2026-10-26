package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.TableCircuits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CreatePanelTest {

    private final FakeCreationTarget target = new FakeCreationTarget();
    private final List<Runnable> queued = new ArrayList<>();
    private KarnaughModel model;
    private KarnaughWizard wizard;
    private int closed;
    private int asked;
    private boolean discard;

    @BeforeEach
    void create() {
        model = new KarnaughModel();
        model.load(KarnaughModelTest.bcd());
        model.setTitle("BCD");
        open(Background.inline());
    }

    private void open(Background background) {
        wizard = new KarnaughWizard(model, KarnaughCost.GENERATOR, target, background);
        wizard.setOnClose(() -> closed++);
        wizard.setDiscardConfirmation(parent -> {
            asked++;
            return discard;
        });
        assertThat(wizard.goTo(KarnaughWizard.Step.CREATE)).isTrue();
    }

    private CreationControls controls() {
        return wizard.createPanel().controls();
    }

    private static List<String> typesOf(CircuitFragment fragment) {
        return fragment.components().stream().map(Component::typeId).toList();
    }

    @Test
    void createOpensANewCircuitWithTheGatesAndSaysItMatchesTheTable() {
        assertThat(controls().destinationButton(CreationForm.Destination.NEW_CIRCUIT).isSelected()).isTrue();
        controls().createButton().doClick();

        assertThat(target.newCircuits).singleElement().satisfies(created -> {
            assertThat(created.name()).isEqualTo("BCD");
            assertThat(typesOf(created.fragment())).contains("and2", "or3", "not",
                    StandardComponentTypes.INPUT_PIN, StandardComponentTypes.OUTPUT_PIN);
            assertThat(created.fragment().frames()).singleElement()
                    .satisfies(frame -> assertThat(frame.title()).isEqualTo("BCD"));
            assertThat(TableCircuits.check(model.spec(), created.fragment(), StandardComponentTypes.registry())
                    .passed()).isTrue();
            assertThat(created.notice()).as("in the new window's status bar").isEqualTo("✓ El circuito cumple la tabla.");
        });
        assertThat(controls().resultText()).isEqualTo("✓ El circuito cumple la tabla.");
        assertThat(target.floating).isEmpty();
        assertThat(target.saved).isEmpty();
        assertThat(closed).as("the work is done: the dialog closes").isEqualTo(1);
        assertThat(asked).isZero();
    }

    @Test
    void insertFloatsTheGatesOverTheCurrentCircuitAndClosesTheDialog() {
        controls().destinationButton(CreationForm.Destination.INSERT).doClick();
        controls().createButton().doClick();

        assertThat(target.newCircuits).isEmpty();
        assertThat(target.floating).singleElement()
                .satisfies(fragment -> assertThat(typesOf(fragment)).contains("and2", "or3"));
        assertThat(closed).isEqualTo(1);
        assertThat(target.notices).containsExactly(
                "✓ El circuito cumple la tabla. Haz clic en el circuito para dejarlo.");
    }

    @Test
    void insertIsOffWithoutACircuitToInsertInto() {
        target.insertable = false;
        open(Background.inline());
        assertThat(controls().destinationButton(CreationForm.Destination.INSERT).isEnabled()).isFalse();
        wizard.createPanel().form().setDestination(CreationForm.Destination.INSERT);
        assertThat(wizard.createPanel().form().destination()).isEqualTo(CreationForm.Destination.NEW_CIRCUIT);
    }

    @Test
    void theComponentGoesToTheLibraryWithTheTablesPinsAndTheNewCircuitStillShowsTheGates() {
        controls().componentCheckBox().doClick();
        assertThat(controls().nameField().getText()).isEqualTo("BCD");
        controls().createButton().doClick();

        assertThat(target.saved).singleElement().satisfies(bundle -> {
            assertThat(bundle.root().name()).isEqualTo("BCD");
            assertThat(bundle.root().id()).isEqualTo("bcd");
            assertThat(bundle.root().pinout()).extracting(pin -> pin.spec().name())
                    .containsExactly("A", "B", "C", "D", "P", "M");
        });
        assertThat(typesOf(target.newCircuits.getFirst().fragment())).contains("and2");
        assertThat(controls().resultText()).isEqualTo(
                "✓ El circuito cumple la tabla. El componente «BCD» quedó en «Mis componentes».");
    }

    @Test
    void insertingWithTheComponentFloatsOneInstanceThatCarriesItsDefinition() {
        target.takenIds.add("bcd");
        controls().destinationButton(CreationForm.Destination.INSERT).doClick();
        controls().componentCheckBox().doClick();
        controls().createButton().doClick();

        DefinitionBundle saved = target.saved.getFirst();
        assertThat(saved.root().id()).isEqualTo("bcd-2");
        assertThat(target.floating).singleElement().satisfies(fragment -> {
            assertThat(typesOf(fragment)).containsExactly(SubcircuitTypes.typeIdOf("bcd-2"));
            assertThat(fragment.definitions()).containsExactlyElementsOf(saved.all());
        });
    }

    @Test
    void aTakenOrBlankNameTurnsCreateOffWithTheReason() {
        target.takenNames.add("bcd");
        controls().componentCheckBox().doClick();
        assertThat(controls().createButton().isEnabled()).isFalse();
        assertThat(controls().reasonText()).isEqualTo("Ya existe un componente llamado «BCD».");

        controls().nameField().setText("  ");
        assertThat(controls().createButton().isEnabled()).isFalse();
        assertThat(controls().reasonText()).isEqualTo("Escribe un nombre para el componente.");

        controls().nameField().setText("Decodificador");
        assertThat(controls().createButton().isEnabled()).isTrue();
        assertThat(controls().reasonText()).isEmpty();

        controls().componentCheckBox().doClick();
        controls().nameField().setText("");
        assertThat(controls().createButton().isEnabled()).as("no component wanted: the name does not matter")
                .isTrue();
    }

    @Test
    void theNameFollowsTheTitleUntilTheUserTypesOne() {
        controls().componentCheckBox().doClick();
        wizard.goTo(KarnaughWizard.Step.TABLE);
        model.setTitle("Decodificador");
        wizard.goTo(KarnaughWizard.Step.CREATE);
        assertThat(controls().nameField().getText()).isEqualTo("Decodificador");

        controls().nameField().setText("Mío");
        wizard.goTo(KarnaughWizard.Step.TABLE);
        model.setTitle("Otro");
        wizard.goTo(KarnaughWizard.Step.CREATE);
        assertThat(controls().nameField().getText()).isEqualTo("Mío");
    }

    @Test
    void whileTheCircuitIsBuiltEveryButtonIsOffAndABusyIndicatorShows() {
        open(new Background(queued::add, Runnable::run));
        controls().createButton().doClick();

        assertThat(queued).hasSize(1);
        assertThat(controls().busyIndicatorShown()).isTrue();
        assertThat(controls().createButton().isEnabled()).isFalse();
        assertThat(controls().componentCheckBox().isEnabled()).isFalse();
        assertThat(wizard.backAction().isEnabled()).isFalse();
        assertThat(wizard.closeAction().isEnabled()).as("closing cancels the creation").isTrue();
        assertThat(wizard.closeAction().getValue(Action.NAME)).isEqualTo("Cancelar");
        assertThat(wizard.stepButton(KarnaughWizard.Step.TABLE).isEnabled()).isFalse();
        assertThat(wizard.goTo(KarnaughWizard.Step.TABLE)).isFalse();
        assertThat(target.newCircuits).as("the EDT only applies the result").isEmpty();

        queued.removeFirst().run();

        assertThat(target.newCircuits).hasSize(1);
        assertThat(controls().busyIndicatorShown()).isFalse();
        assertThat(controls().createButton().isEnabled()).isTrue();
        assertThat(wizard.backAction().isEnabled()).isTrue();
        assertThat(wizard.closeAction().getValue(Action.NAME)).isEqualTo("Cerrar");
    }

    @Test
    void closingWhileTheCircuitIsBuiltCancelsItAndTheLateResultIsIgnored() {
        open(new Background(queued::add, Runnable::run));
        controls().componentCheckBox().doClick();
        controls().createButton().doClick();

        wizard.closeAction().actionPerformed(null);

        assertThat(closed).isEqualTo(1);
        assertThat(controls().isBusy()).isFalse();
        queued.removeFirst().run();
        assertThat(target.saved).as("nothing goes to the library").isEmpty();
        assertThat(target.newCircuits).isEmpty();
        assertThat(target.floating).isEmpty();
        assertThat(target.notices).isEmpty();
        assertThat(controls().resultText()).isEmpty();
        assertThat(closed).isEqualTo(1);
    }

    @Test
    void escapeOrTheCloseBoxWhileInsertingCancelsTooAndAFailureArrivingLateIsIgnored() {
        List<Runnable> edt = new ArrayList<>();
        open(new Background(queued::add, edt::add));
        controls().destinationButton(CreationForm.Destination.INSERT).doClick();
        controls().createButton().doClick();

        wizard.requestClose();
        queued.removeFirst().run();
        edt.removeFirst().run();

        assertThat(target.floating).isEmpty();
        assertThat(target.notices).isEmpty();
        assertThat(closed).isEqualTo(1);
        assertThat(controls().resultText()).isEmpty();
    }

    @Test
    void closingWhenIdleJustCloses() {
        wizard.requestClose();
        assertThat(closed).isEqualTo(1);
        assertThat(asked).as("the table is as it opened").isZero();
        assertThat(target.newCircuits).isEmpty();
    }

    @Test
    void closingAnEditedTableAsksAndSeguirEditandoKeepsTheDialogAndTheTable() {
        wizard.goTo(KarnaughWizard.Step.TABLE);
        model.cycle(0, 0);

        wizard.closeAction().actionPerformed(null);

        assertThat(asked).isEqualTo(1);
        assertThat(closed).isZero();
        assertThat(model.value(0, 0)).isEqualTo(LogicValue.ONE);

        discard = true;
        wizard.requestClose();
        assertThat(asked).isEqualTo(2);
        assertThat(closed).isEqualTo(1);
    }

    @Test
    void everyKindOfEditCountsAndUndoingItByHandDoesNot() {
        wizard.goTo(KarnaughWizard.Step.TABLE);
        model.setTitle("Otra");
        wizard.requestClose();
        model.setTitle("BCD");
        model.setInputName(0, "X");
        wizard.requestClose();
        model.setInputName(0, "A");
        model.setOutputCount(3);
        wizard.requestClose();
        model.setOutputCount(2);
        assertThat(asked).isEqualTo(3);
        assertThat(closed).isZero();

        wizard.requestClose();
        assertThat(asked).as("back to how it opened").isEqualTo(3);
        assertThat(closed).isEqualTo(1);
    }

    @Test
    void afterCreatingTheTableClosingDoesNotAskUntilItIsEditedAgain() {
        wizard.goTo(KarnaughWizard.Step.TABLE);
        model.cycle(0, 0);
        wizard.goTo(KarnaughWizard.Step.CREATE);
        wizard.createButton().doClick();
        assertThat(target.newCircuits).hasSize(1);
        assertThat(closed).isEqualTo(1);

        wizard.requestClose();
        assertThat(asked).isZero();
        assertThat(closed).isEqualTo(2);

        wizard.goTo(KarnaughWizard.Step.TABLE);
        model.cycle(0, 1);
        wizard.requestClose();
        assertThat(asked).isEqualTo(1);
        assertThat(closed).isEqualTo(2);
    }

    @Test
    void aFailedCreationStillCountsAsUnsaved() {
        CreationTarget failing = new FakeCreationTarget() {
            @Override
            public void openNewCircuit(String name, CircuitFragment fragment, String notice) {
                throw new IllegalStateException("could not route channel 3");
            }
        };
        wizard = new KarnaughWizard(model, KarnaughCost.GENERATOR, failing, Background.inline());
        wizard.setOnClose(() -> closed++);
        wizard.setDiscardConfirmation(parent -> {
            asked++;
            return discard;
        });
        model.cycle(0, 0);
        wizard.goTo(KarnaughWizard.Step.CREATE);
        wizard.createButton().doClick();

        wizard.requestClose();
        assertThat(asked).isEqualTo(1);
        assertThat(closed).isZero();
    }

    @Test
    void closingWhileAnEditedTableIsBuiltCancelsFirstThenAsks() {
        open(new Background(queued::add, Runnable::run));
        wizard.goTo(KarnaughWizard.Step.TABLE);
        model.cycle(0, 0);
        wizard.goTo(KarnaughWizard.Step.CREATE);
        wizard.createButton().doClick();
        assertThat(controls().isBusy()).isTrue();

        wizard.requestClose();

        assertThat(controls().isBusy()).as("cancelled before asking").isFalse();
        assertThat(asked).isEqualTo(1);
        assertThat(closed).isZero();
        assertThat(wizard.createButton().isEnabled()).as("the user can create again").isTrue();
        assertThat(wizard.closeAction().getValue(Action.NAME)).isEqualTo("Cerrar");
        queued.removeFirst().run();
        assertThat(target.newCircuits).as("the cancelled result is ignored").isEmpty();
    }

    @Test
    void anErrorInTheWorkerIsReportedLikeAFailure() {
        List<String> failures = new ArrayList<>();
        Background.inline().<String>run(() -> {
            throw new StackOverflowError("deep");
        }, result -> failures.add("done " + result), failure -> failures.add(failure.getMessage()));
        assertThat(failures).containsExactly("deep");
    }

    @Test
    void aFailureWhileApplyingEndsTheCreationWithAReadableMessage() {
        CreationTarget failing = new FakeCreationTarget() {
            @Override
            public void openNewCircuit(String name, CircuitFragment fragment, String notice) {
                throw new IllegalStateException("could not route channel 3");
            }
        };
        wizard = new KarnaughWizard(model, KarnaughCost.GENERATOR, failing, Background.inline());
        wizard.goTo(KarnaughWizard.Step.CREATE);
        controls().createButton().doClick();

        assertThat(controls().isBusy()).isFalse();
        assertThat(controls().createButton().isEnabled()).isTrue();
        assertThat(controls().resultText())
                .isEqualTo("No se pudo crear el circuito. Es un error de VSim: por favor, avísanos.")
                .doesNotContain("route");
    }

    @Test
    void aLibraryWriteFailureIsShownAndTheCircuitIsStillCreated() {
        target.saveError = Optional.of("No se pudo escribir");
        controls().componentCheckBox().doClick();
        controls().createButton().doClick();
        assertThat(controls().resultText()).isEqualTo("✓ El circuito cumple la tabla. No se pudo escribir");
        assertThat(target.newCircuits).hasSize(1);
        assertThat(closed).as("the dialog stays to show what failed").isZero();

        controls().destinationButton(CreationForm.Destination.INSERT).doClick();
        controls().createButton().doClick();
        assertThat(target.floating).hasSize(1);
        assertThat(closed).as("an «Insertar» whose component failed stays too").isZero();
        assertThat(target.notices).isEmpty();
    }

    @Test
    void workThatFailsIsReportedOnTheEdtAndNeverThrowsThere() {
        List<String> failures = new ArrayList<>();
        List<Runnable> edt = new ArrayList<>();
        new Background(Runnable::run, edt::add).<String>run(() -> {
            throw new IllegalStateException("boom");
        }, result -> failures.add("done " + result), exception -> failures.add(exception.getMessage()));
        assertThat(failures).isEmpty();
        edt.removeFirst().run();
        assertThat(failures).containsExactly("boom");
    }
}
