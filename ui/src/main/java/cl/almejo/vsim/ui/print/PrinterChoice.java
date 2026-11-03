package cl.almejo.vsim.ui.print;

import java.util.Objects;

/** What the user did in the printer's own dialog. */
public sealed interface PrinterChoice {

    /** Accepted: the printer prints on {@code effective}, through {@code job} (copies, page range… are in it). */
    record Chosen(PrintSetup effective, PrinterAccess.Job job) implements PrinterChoice {

        public Chosen {
            Objects.requireNonNull(effective, "effective");
            Objects.requireNonNull(job, "job");
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
