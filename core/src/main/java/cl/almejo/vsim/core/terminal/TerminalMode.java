package cl.almejo.vsim.core.terminal;

/** How a Terminal reads its bytes (spec §5, «Modo»). */
public enum TerminalMode {
    /** Exactly Logisim 2.7's TTY: CR and LF start a new line, BS erases, FF clears, ESC and tabs are ignored. */
    LOGISIM,
    /** A VT100 / xterm subset: a cursor that moves, escape sequences and colours (spec §6). */
    VT,
    /** VT100-xterm on paper: like {@link #VT} but nothing moves the cursor to an upper row (spec §7). */
    TELETYPE
}
