package cl.almejo.vsim.core.terminal;

/** How bytes 128–255 become characters when the data is 8 bits wide («Codificación»). */
public enum TerminalEncoding {
    /** Multi-byte UTF-8; a bad sequence shows U+FFFD. */
    UTF8,
    /** Each byte is the character with the same number (ISO 8859-1). */
    LATIN1
}
