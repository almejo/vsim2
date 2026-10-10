package cl.almejo.vsim.core.waveform;

import cl.almejo.vsim.core.value.Value;

/**
 * Up to {@link ChannelTrace#CHUNK_SIZE} events of one channel, oldest first; a null value is a gap. Once a
 * {@link Trace} holds a chunk its arrays are never written again, so snapshots can share it across threads.
 */
record Chunk(long[] times, Value[] values, int size) {
}
