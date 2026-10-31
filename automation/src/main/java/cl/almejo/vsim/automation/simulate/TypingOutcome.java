package cl.almejo.vsim.automation.simulate;

import java.util.OptionalInt;

/** What the simulation answers to typed characters: how many the part took and how full its queue is now. */
public record TypingOutcome(int accepted, OptionalInt queueSize) {
}
