package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.present.Question;
import cl.almejo.vsim.automation.present.QuestionAnswer;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The card of a question the AI asks in VSim (IA.3 Task 8): a button per choice and a one-line answer. */
class QuestionCardTest {

    private static final DocumentId CIRCUIT = new DocumentId("circuit-1");

    private final List<QuestionAnswer> answers = new ArrayList<>();

    private QuestionCard card(List<String> choices, boolean allowText) {
        return new QuestionCard(new Question("question-1", "task-1", CIRCUIT, "¿De cuántos bits hago el contador?",
                choices, allowText), answers::add);
    }

    @Test
    void itShowsTheQuestionAsPlainWrappedTextWithAButtonPerChoice() {
        QuestionCard card = card(List.of("2 bits", "<html><b>3</b>"), true);

        assertThat(card.title().getText()).isEqualTo("La IA pregunta");
        assertThat(card.questionText().getText()).isEqualTo("¿De cuántos bits hago el contador?");
        assertThat(card.questionText().getLineWrap()).isTrue();
        assertThat(card.questionText().isEditable()).isFalse();
        assertThat(card.choiceButtons()).extracting(JButton::getText).containsExactly("2 bits", "<html><b>3</b>");
        assertThat(card.choiceButtons()).allSatisfy(button ->
                assertThat(button.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE));
        assertThat(card.sendButton().getText()).isEqualTo("Enviar");
        assertThat(card.field().isVisible()).isTrue();
    }

    @Test
    void aChoiceButtonAnswersOnceAndTheCardSaysWhatWasSent() {
        QuestionCard card = card(List.of("2 bits", "3 bits"), true);

        card.choiceButtons().get(1).doClick();
        card.choiceButtons().get(0).doClick();

        assertThat(answers).containsExactly(QuestionAnswer.choice("3 bits"));
        assertThat(card.choiceButtons()).noneMatch(JButton::isEnabled);
        assertThat(card.field().isEnabled()).isFalse();
        assertThat(card.sentText().getText()).isEqualTo("Enviaste: 3 bits");
    }

    @Test
    void aWrittenAnswerIsSentByEnviarOrEnterButNeverEmpty() {
        QuestionCard card = card(List.of(), true);

        card.sendButton().doClick();
        assertThat(answers).isEmpty();

        card.field().setText("  Contador módulo 6 ");
        card.field().postActionEvent(); // Enter in the field
        assertThat(answers).containsExactly(QuestionAnswer.text("Contador módulo 6"));
        assertThat(card.sendButton().isEnabled()).isFalse();
    }

    @Test
    void withoutAllowTextThereIsNoField() {
        QuestionCard card = card(List.of("Sí", "No"), false);

        assertThat(card.field().isVisible()).isFalse();
        assertThat(card.sendButton().isVisible()).isFalse();
    }
}
