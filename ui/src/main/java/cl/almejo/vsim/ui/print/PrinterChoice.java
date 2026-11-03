package cl.almejo.vsim.ui.print;

import java.util.Objects;
import java.util.Optional;

/** What the user did in the printer's own dialog. */
public sealed interface PrinterChoice {

    /**
     * Accepted: the printer prints on {@code effective}, through {@code job} (copies, page range… are in it).
     * {@code printerDefault} is the paper the chosen printer itself defaults to, when it says and it is one of
     * {@link Paper}.
     */
    record Chosen(PrintSetup effective, PrinterAccess.Job job, Optional<Paper> printerDefault)
            implements PrinterChoice {

        public Chosen {
            Objects.requireNonNull(effective, "effective");
            Objects.requireNonNull(job, "job");
            Objects.requireNonNull(printerDefault, "printerDefault");
        }

        /** A choice of a printer that does not say its default paper. */
        public Chosen(PrintSetup effective, PrinterAccess.Job job) {
            this(effective, job, Optional.empty());
        }
    }

    /** Accepted, but on a paper that is none of {@link Paper}; {@code name} is how the printer calls it. */
    record UnknownPaper(String name) implements PrinterChoice {

        public UnknownPaper {
            Objects.requireNonNull(name, "name");
        }
    }

    /** Closed without printing. */
    record Declined() implements PrinterChoice {
    }
}
