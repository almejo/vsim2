package cl.almejo.vsim.core.io.logisim;

import java.io.InputStream;

/** Reads the hand-written fixtures next to these tests. */
final class LogisimTestFiles {

    private LogisimTestFiles() {
    }

    static LogisimProject read(String fixture) throws Exception {
        try (InputStream input = LogisimTestFiles.class.getResourceAsStream(fixture)) {
            if (input == null) {
                throw new IllegalArgumentException("no fixture " + fixture);
            }
            return new LogisimReader().read(input);
        }
    }
}
