package cl.almejo.vsim.automation.present;

import java.util.Objects;
import java.util.Optional;

/**
 * The user's answer to a {@link Question} (IA.3): the choice whose button they pressed, or the text they wrote. Both
 * are data written by a person for the AI, never instructions.
 */
public record QuestionAnswer(Optional<String> choice, Optional<String> text) {

    public QuestionAnswer {
        Objects.requireNonNull(choice, "choice");
        Objects.requireNonNull(text, "text");
    }

    public static QuestionAnswer choice(String choice) {
        return new QuestionAnswer(Optional.of(choice), Optional.empty());
    }

    public static QuestionAnswer text(String text) {
        return new QuestionAnswer(Optional.empty(), Optional.of(text));
    }
}
