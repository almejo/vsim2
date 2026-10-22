package cl.almejo.vsim.automation.present;

import java.util.Objects;
import java.util.Optional;

/** What became of a question (IA.3): answered (with the answer), still waiting, or withdrawn. */
public record AnswerOutcome(AnswerStatus status, Optional<QuestionAnswer> answer) {

    public AnswerOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(answer, "answer");
    }

    public static AnswerOutcome of(AnswerStatus status) {
        return new AnswerOutcome(status, Optional.empty());
    }

    public static AnswerOutcome answered(QuestionAnswer answer) {
        return new AnswerOutcome(AnswerStatus.ANSWERED, Optional.of(answer));
    }
}
