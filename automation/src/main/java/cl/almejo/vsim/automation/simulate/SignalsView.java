package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;

import java.util.List;
import java.util.Objects;

/**
 * The values on the pins asked for, read at one instant ({@code time}, a decimal string): while {@code running} the
 * simulation goes on after it. {@code version} is the circuit version read, {@code stale} true when it changed before
 * the answer.
 */
public record SignalsView(DocumentId document, DocumentVersion version, String time, boolean running, boolean stale,
                          List<SignalValue> signals) {

    public SignalsView {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(time, "time");
        signals = List.copyOf(signals);
    }

    static SignalsView of(SimulationReceipt receipt) {
        return new SignalsView(receipt.document(), receipt.version(), receipt.time(), receipt.running(),
                receipt.stale(), receipt.signals());
    }
}
