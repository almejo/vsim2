package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.document.CircuitDocument;

/** A circuit read from disk. VSim 1 imports should be saved under a new name, in format v2. */
public record OpenedCircuit(CircuitDocument document, boolean importedFromVsim1) {
}
