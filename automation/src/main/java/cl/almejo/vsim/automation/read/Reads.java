package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.FutureWait;
import cl.almejo.vsim.automation.ReadHost;
import cl.almejo.vsim.automation.check.LayoutCheck;
import cl.almejo.vsim.automation.check.StageCheck;
import cl.almejo.vsim.core.geometry.Bounds;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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

    /** The layout faults of circuit {@code document} that involve {@code elementIds} (empty: the whole circuit). */
    public StageCheck.LayoutView layout(DocumentId document, Set<String> elementIds) {
        Set<String> scope = Set.copyOf(elementIds);
        return await(host.reading(document, source -> new StageCheck.LayoutView(source.id(), source.version(),
                LayoutCheck.faults(source.document(), source.registry(), source.textMeasure(), scope))));
    }

    public RegionPicture pictureOf(DocumentId document, Optional<Bounds> region, boolean asOnScreen) {
        Objects.requireNonNull(region, "region");
        return await(host.pictureOf(document, region, asOnScreen));
    }

    public WindowPicture pictureOfWindow(DocumentId document) {
        return await(host.pictureOfWindow(document));
    }

    private <T> T await(CompletableFuture<T> result) {
        return FutureWait.get(result, editorTimeout,
                "The editor did not answer within " + editorTimeout.toSeconds() + " s; try again");
    }
}
