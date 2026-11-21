package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The free texts of a machine's diagram: the record, its place in the machine, and the crossing with a circuit. */
class MachineTextsTest {

    private static final NoteStyle BOXED = new NoteStyle(NoteColor.YELLOW, NoteColor.NONE, NoteColor.NONE);

    @Test
    void aTextHasAnIdAPlaceItsLinesAndAStyle() {
        MachineText text = new MachineText("t1", new DiagramPoint(10, 20), "hola\nmundo", BOXED);

        assertThat(text.id()).isEqualTo("t1");
        assertThat(text.text()).isEqualTo("hola\nmundo");
        assertThat(text.style()).isEqualTo(BOXED);
        assertThat(text.withText("otro").text()).isEqualTo("otro");
        assertThat(text.withStyle(NoteStyle.PLAIN).style()).isEqualTo(NoteStyle.PLAIN);
        assertThat(text.withPosition(new DiagramPoint(1, 2)).position()).isEqualTo(new DiagramPoint(1, 2));
        MachineText placed = text.placed("t7", new DiagramPoint(5, 5));
        assertThat(placed.id()).isEqualTo("t7");
        assertThat(placed.text()).isEqualTo("hola\nmundo");
        assertThat(placed.style()).isEqualTo(BOXED);
    }

    @Test
    void aTextWithoutLinesOrStyleIsEmptyAndPlain() {
        MachineText text = new MachineText("t1", DiagramPoint.ORIGIN, null, NoteStyle.PLAIN);

        assertThat(text.text()).isEmpty();
        assertThat(new MachineText("t2", DiagramPoint.ORIGIN, "x").style()).isEqualTo(NoteStyle.PLAIN);
    }

    @Test
    void aTextIdIsASlug() {
        assertThatThrownBy(() -> new MachineText("T 1", DiagramPoint.ORIGIN, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMachineHasNoTextsUntilItIsGivenSomeAndKeepsThemThroughEveryOtherChange() {
        StateMachine machine = StateMachine.initial();
        assertThat(machine.texts()).isEmpty();
        MachineText text = new MachineText("t1", new DiagramPoint(3, 4), "hola");

        StateMachine withText = machine.withTexts(List.of(text));

        assertThat(withText.withPinNames(List.of("x"), List.of("Z")).texts()).containsExactly(text);
        assertThat(withText.withStates(withText.states()).texts()).containsExactly(text);
        assertThat(withText.withNotes(List.of()).texts()).containsExactly(text);
        assertThat(withText.withEdge(StateMachine.EDGE_RISING).texts()).containsExactly(text);
        assertThat(withText.withText("t1", found -> found.withText("chau")).texts().getFirst().text())
                .isEqualTo("chau");
    }

    @Test
    void twoTextsCannotShareAnId() {
        MachineText first = new MachineText("t1", DiagramPoint.ORIGIN, "a");
        MachineText second = new MachineText("t1", DiagramPoint.ORIGIN, "b");

        assertThatThrownBy(() -> StateMachine.initial().withTexts(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("t1");
    }

    @Test
    void aCircuitTextBecomesAMachineTextKeepingItsStyleAndTheDistanceBetweenTexts() {
        TextLabel first = new TextLabel(new ElementId("l1"), new GridPoint(10, 10), "uno", BOXED);
        TextLabel second = new TextLabel(new ElementId("l2"), new GridPoint(30, 20), "dos");

        List<MachineText> texts = NoteTransfer.toMachineTexts(List.of(first, second));

        assertThat(texts).extracting(MachineText::text).containsExactly("uno", "dos");
        assertThat(texts.get(0).style()).isEqualTo(BOXED);
        assertThat(texts.get(0).position()).isEqualTo(DiagramPoint.ORIGIN);
        assertThat(texts.get(1).position().x()).isEqualTo(160);
        assertThat(texts.get(1).position().y()).isEqualTo(80);
    }

    @Test
    void aMachineTextBecomesACircuitTextAtTheGridAndBack() {
        MachineText text = new MachineText("t1", new DiagramPoint(80, 40), "hola", BOXED);
        MachineText other = new MachineText("t2", new DiagramPoint(160, 120), "chau");

        List<TextLabel> labels = NoteTransfer.toTextLabels(List.of(text, other));

        assertThat(labels).extracting(TextLabel::text).containsExactly("hola", "chau");
        assertThat(labels.get(0).style()).isEqualTo(BOXED);
        assertThat(labels.get(0).position()).isEqualTo(new GridPoint(0, 0));
        assertThat(labels.get(1).position()).isEqualTo(new GridPoint(10, 10));
    }
}
