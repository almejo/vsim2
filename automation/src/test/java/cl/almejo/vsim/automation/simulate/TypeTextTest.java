package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.SimulationHost;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code vsim_type_text}: a text becomes the characters a person would type, and the Teclado takes what fits. */
class TypeTextTest {

    private static final DocumentId DOCUMENT = new DocumentId("circuit-1");
    private static final int CAPACITY = 4;

    private final List<List<byte[]>> typed = new ArrayList<>();
    private int queued;
    private boolean simulating = true;

    private final SimulationHost host = new SimulationHost() {
        @Override
        public CompletableFuture<TypingOutcome> typeText(DocumentId document, String component,
                                                         List<byte[]> characters) {
            typed.add(characters);
            if (!simulating) {
                return CompletableFuture.completedFuture(new TypingOutcome(0, OptionalInt.empty()));
            }
            int accepted = Math.min(characters.size(), CAPACITY - queued);
            queued += accepted;
            return CompletableFuture.completedFuture(new TypingOutcome(accepted, OptionalInt.of(queued)));
        }
    };
    private final SimulationOperations operations = new SimulationOperations(host);

    @Test
    void aTextIsTypedCharacterByCharacterWithEnterAsTheKeyboardSendsIt() {
        KeyEncoding enterIsCarriageReturn = KeyEncoding.forParameters(Map.of(KeyEncoding.ENTER_SENDS,
                KeyEncoding.CR_VALUE));

        TypedText result = operations.typeText(DOCUMENT, "c7", "a\né", enterIsCarriageReturn);

        assertThat(typed).hasSize(1);
        assertThat(typed.getFirst()).hasSize(3);
        assertThat(typed.getFirst().get(0)).containsExactly((byte) 'a');
        assertThat(typed.getFirst().get(1)).containsExactly((byte) '\r');
        assertThat(typed.getFirst().get(2)).containsExactly((byte) 0xC3, (byte) 0xA9);
        assertThat(result.accepted()).isEqualTo(3);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.queueSize()).isEqualTo(3);
        assertThat(result.component()).isEqualTo("c7");
    }

    @Test
    void aFullQueueTakesWhatFitsAndSaysSo() {
        TypedText result = operations.typeText(DOCUMENT, "c7", "abcdef", KeyEncoding.DEFAULT);

        assertThat(result.accepted()).isEqualTo(CAPACITY);
        assertThat(result.total()).isEqualTo(6);
        assertThat(result.queueSize()).isEqualTo(CAPACITY);
    }

    @Test
    void aCharacterThePartCannotSendRefusesTheWholeTextAndTypesNothing() {
        KeyEncoding sevenBits = KeyEncoding.forParameters(Map.of(KeyEncoding.DATA_WIDTH, "7"));

        assertThatThrownBy(() -> operations.typeText(DOCUMENT, "c7", "abé", sevenBits))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
        assertThat(typed).isEmpty();
    }

    @Test
    void anEmptyOrTooLongTextIsRefused() {
        assertThatThrownBy(() -> operations.typeText(DOCUMENT, "c7", "", KeyEncoding.DEFAULT))
                .isInstanceOf(AutomationException.class);
        assertThatThrownBy(() -> operations.typeText(DOCUMENT, "c7", "x".repeat(TextTyping.MAXIMUM_LENGTH + 1),
                KeyEncoding.DEFAULT)).isInstanceOf(AutomationException.class);
        assertThat(typed).isEmpty();
    }

    @Test
    void withoutASimulationTheTypingIsRefused() {
        simulating = false;

        assertThatThrownBy(() -> operations.typeText(DOCUMENT, "c7", "a", KeyEncoding.DEFAULT))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED));
    }
}
