package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.simulation.TerminalRequest;
import cl.almejo.vsim.core.simulation.TerminalWindowFrame;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.Objects;
import java.util.Optional;

/**
 * Where the frames of a document's Terminals come from: the simulation thread through {@link OpenDocument}, or a fake
 * in tests. One request per Terminal ({@link TerminalRequest#componentId()}).
 */
public interface TerminalFrameSource {

    /** Sets or replaces the request of its Terminal. */
    void request(TerminalRequest request);

    /** Stops the frames of {@code componentId}. */
    void stopRequest(String componentId);

    /** The latest frame of {@code componentId}, if it is requested and is a Terminal. */
    Optional<TerminalWindowFrame> latest(String componentId);

    /** The reader has the bytes of {@code componentId} up to {@code sequence} of screen {@code owner}. */
    void acknowledge(String componentId, long owner, long sequence);

    /** The frames of {@code document}'s simulation. */
    static TerminalFrameSource of(OpenDocument document) {
        Objects.requireNonNull(document, "document");
        return new TerminalFrameSource() {
            @Override
            public void request(TerminalRequest request) {
                document.setTerminalRequest(request);
            }

            @Override
            public void stopRequest(String componentId) {
                document.removeTerminalRequest(componentId);
            }

            @Override
            public Optional<TerminalWindowFrame> latest(String componentId) {
                return document.latestTerminalFrame(componentId);
            }

            @Override
            public void acknowledge(String componentId, long owner, long sequence) {
                document.acknowledgeTerminalBytes(componentId, owner, sequence);
            }
        };
    }
}
