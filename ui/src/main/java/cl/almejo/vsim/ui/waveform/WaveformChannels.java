package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;

import javax.swing.Action;
import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;

/**
 * What a {@link WaveformPanel} shows: its rows, the recording they are read from, and how they may be used. The
 * document's probes ({@link ProbeChannels}) in a circuit or component tab; a machine copy's own channels in the tab of
 * a «Máquina de estados» (its wave strip). The panel reads it on the EDT.
 */
public interface WaveformChannels {

    /** The rows, top to bottom. */
    List<ChannelRows.ChannelRow> rows();

    /**
     * What {@link #rows()} and {@link #title()} are made from: the panel asks for them again only when it changes
     * ({@code equals}). Cheap to compute: it is read on every timer step.
     */
    Object rowsKey();

    /** The latest recording the rows are read from. */
    WaveformSnapshot latest();

    /** Whether there is anything to show: only then the panel's timer runs and presentation mode's edge wakes. */
    boolean hasChannels();

    /**
     * The names that channel {@code channelId}'s values stand for (a machine's state codes: «S0», «S1»), asked for at
     * paint time so they follow edits; empty for a plain row, written as bits or in its format.
     */
    Optional<LongFunction<String>> valueNames(String channelId);

    /**
     * The colours the user gave the values of channel {@code channelId} (a machine's states, hito 3), each value's
     * own or none, asked for at paint time so they follow edits; the painter turns them into the scheme's shades.
     * Empty for a row whose values have no colours.
     */
    default Optional<LongFunction<Optional<StateColor>>> valueColors(String channelId) {
        return Optional.empty();
    }

    /** Whether the rows can be renamed, moved, removed, given a format and shown in the circuit (the row menu). */
    boolean editable();

    /** What the panel's toolbar says first («Onda de Detector 11»); empty says nothing. */
    Optional<String> title();

    /** Links in the panel's toolbar, after the title. */
    List<Action> headerActions();
}
