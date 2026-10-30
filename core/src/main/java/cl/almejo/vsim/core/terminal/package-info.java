/**
 * The Terminal's screen model, free of any AWT or Swing type: the part's geometry, and {@link
 * cl.almejo.vsim.core.terminal.TerminalScreen} with its cells, cursor, UTF-8 decoder, escape-sequence parser, history,
 * transcript and byte log, written byte by byte on the simulation thread and read through immutable {@link
 * cl.almejo.vsim.core.terminal.TerminalFrame}s.
 */
package cl.almejo.vsim.core.terminal;
