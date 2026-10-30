package cl.almejo.vsim.core.terminal;

import cl.almejo.vsim.core.terminal.ControlSequenceParser.Action;
import cl.almejo.vsim.core.terminal.ControlSequenceParser.ControlSequence;
import cl.almejo.vsim.core.terminal.ControlSequenceParser.Escape;
import cl.almejo.vsim.core.terminal.ControlSequenceParser.Execute;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The escape-sequence state machine on its own: what each sequence dispatches and when it gives up. */
class ControlSequenceParserTest {

    private static List<Action> feed(ControlSequenceParser parser, String text) {
        List<Action> actions = new ArrayList<>();
        text.codePoints().forEach(codePoint -> actions.add(parser.feed(codePoint)));
        return actions;
    }

    private static Action last(String text) {
        List<Action> actions = feed(new ControlSequenceParser(), text);
        return actions.get(actions.size() - 1);
    }

    @Test
    void startsInGroundAndLeavesItOnEscape() {
        ControlSequenceParser parser = new ControlSequenceParser();
        assertThat(parser.inGround()).isTrue();
        assertThat(parser.feed(0x1B)).isEqualTo(ControlSequenceParser.PENDING);
        assertThat(parser.inGround()).isFalse();
    }

    @Test
    void aControlSequenceCarriesItsParametersAbsentOnesIncluded() {
        ControlSequence sequence = (ControlSequence) last("\u001b[;12;H");
        assertThat(sequence.finalCharacter()).isEqualTo('H');
        assertThat(sequence.privateMarker()).isEqualTo(ControlSequence.NO_PRIVATE_MARKER);
        assertThat(sequence.parameterCount()).isEqualTo(3);
        assertThat(sequence.parameter(0, 1)).isEqualTo(1);
        assertThat(sequence.parameter(1, 1)).isEqualTo(12);
        assertThat(sequence.parameter(2, 7)).isEqualTo(7);
        assertThat(sequence.parameter(3, 9)).isEqualTo(9);
    }

    @Test
    void aSequenceWithoutParametersHasNone() {
        assertThat(((ControlSequence) last("\u001b[m")).parameterCount()).isZero();
    }

    @Test
    void thePrivateMarkerIsKept() {
        ControlSequence sequence = (ControlSequence) last("\u001b[?25l");
        assertThat(sequence.privateMarker()).isEqualTo('?');
        assertThat(sequence.parameter(0, 0)).isEqualTo(25);
    }

    @Test
    void anEscapeWithAFinalCharacterDispatches() {
        assertThat(last("\u001b7")).isEqualTo(new Escape('7'));
    }

    @Test
    void aControlByteInsideASequenceIsExecutedAndTheSequenceGoesOn() {
        List<Action> actions = feed(new ControlSequenceParser(), "\u001b[1\n2H");
        assertThat(actions.get(3)).isEqualTo(new Execute('\n'));
        assertThat(((ControlSequence) actions.get(5)).parameter(0, 0)).isEqualTo(12);
    }

    @Test
    void anOperatingSystemCommandIsConsumedUntilTheBell() {
        ControlSequenceParser parser = new ControlSequenceParser();
        assertThat(feed(parser, "\u001b]0;title\u0007")).containsOnly(ControlSequenceParser.PENDING);
        assertThat(parser.inGround()).isTrue();
    }

    @Test
    void aSequenceIsAbandonedPastSixtyFourBytes() {
        ControlSequenceParser parser = new ControlSequenceParser();
        feed(parser, "\u001b[" + "1".repeat(ControlSequenceParser.MAXIMUM_SEQUENCE_LENGTH - 2));
        assertThat(parser.inGround()).isFalse();
        feed(parser, "1");
        assertThat(parser.inGround()).isTrue();
    }

    @Test
    void anUnsupportedIntermediateMakesTheSequenceDrop() {
        assertThat(last("\u001b[1 q")).isEqualTo(ControlSequenceParser.PENDING);
        assertThat(last("\u001b[1:2m")).isEqualTo(ControlSequenceParser.PENDING);
    }

    @Test
    void resetReturnsToGround() {
        ControlSequenceParser parser = new ControlSequenceParser();
        feed(parser, "\u001b[12");
        parser.reset();
        assertThat(parser.inGround()).isTrue();
    }
}
