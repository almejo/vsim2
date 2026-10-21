package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.ReadHost;
import cl.almejo.vsim.core.geometry.Bounds;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The reading half of {@code AutomationApi}: asks the {@link ReadHost} for the circuit on the editor thread, runs the
 * readers there (the document is only consistent on that thread) and waits for the answer off it, at most the
 * editor timeout.
 */
public final class Reads {

    private final ReadHost host;
    private final Duration editorTimeout;

    public Reads(ReadHost host, Duration editorTimeout) {
        this.host = Objects.requireNonNull(host, "host");
        this.editorTimeout = Objects.requireNonNull(editorTimeout, "editorTimeout");
    }

    public CatalogView describeComponents(DocumentId document, Optional<String> typeFilter) {
        Objects.requireNonNull(typeFilter, "typeFilter");
        return await(host.reading(document, source -> CatalogReader.describeAll(source, typeFilter)));
    }

    public ComponentTypeView describeComponent(DocumentId document, String typeId, Map<String, Object> parameters) {
        Objects.requireNonNull(typeId, "typeId");
        Map<String, Object> copy = Map.copyOf(parameters);
        return await(host.reading(document, source -> CatalogReader.describe(source, typeId, copy)));
    }

    public CircuitView readCircuit(DocumentId document, ReadFilter filter, Optional<String> cursor) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(cursor, "cursor");
        return await(host.reading(document, source -> CircuitReader.read(source, filter, cursor)));
    }

    public ProblemsView readProblems(DocumentId document) {
        return await(host.reading(document, ProblemsReader::read));
    }

    public RegionPicture pictureOf(DocumentId document, Optional<Bounds> region, boolean asOnScreen) {
        Objects.requireNonNull(region, "region");
        return await(host.pictureOf(document, region, asOnScreen));
    }

    public WindowPicture pictureOfWindow(DocumentId document) {
        return await(host.pictureOfWindow(document));
    }

    private <T> T await(CompletableFuture<T> result) {
        try {
            return result.get(editorTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN,
                    "The editor did not answer within " + editorTimeout.toSeconds() + " s; try again");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN, "The request was interrupted");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("The editor failed the request", cause);
        }
    }
}
