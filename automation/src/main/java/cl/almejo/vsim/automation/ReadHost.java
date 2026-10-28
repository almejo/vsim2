package cl.almejo.vsim.automation;

import cl.almejo.vsim.automation.read.ReadSource;
import cl.almejo.vsim.automation.read.RegionPicture;
import cl.almejo.vsim.automation.read.WindowPicture;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.memory.MemoryContents;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The editor-side services for reading the catalogue, the circuit, its problems and pictures (IA.1 Task 2). An unknown
 * circuit fails the future with an {@link AutomationException} ({@link AutomationError#DOCUMENT_CLOSED} for one that
 * was closed, {@link AutomationError#INVALID_REFERENCE} for an id VSim never gave). The defaults refuse, so a host that
 * serves no reads (a test's) writes none.
 */
public interface ReadHost {

    /**
     * Runs {@code work} on the editor thread over what the AI may read of circuit {@code document}; the source is
     * valid only during that call. Reading never changes the circuit, the selection, the focus or the view.
     */
    default <T> CompletableFuture<T> reading(DocumentId document, Function<ReadSource, T> work) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("this host serves no reads"));
    }

    /**
     * A PNG of {@code region} (grid units; empty: the whole circuit) within {@link RegionPicture#MAXIMUM_WIDTH} ×
     * {@link RegionPicture#MAXIMUM_HEIGHT}, never shown and never saved (spec §6). {@code asOnScreen}: the active pane
     * as the user sees it (theme, grid, guides, selection) instead of the export rendering.
     */
    default CompletableFuture<RegionPicture> pictureOf(DocumentId document, Optional<Bounds> region,
                                                       boolean asOnScreen) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("this host paints no pictures"));
    }

    /**
     * A PNG of the whole window that shows circuit {@code document} (menus, toolbar, palette, panes, inspector, status
     * bar and its open dialogs), painted from the window's own components, never a capture of the screen, within
     * {@link WindowPicture#MAXIMUM_WIDTH} × {@link WindowPicture#MAXIMUM_HEIGHT}, never saved.
     */
    default CompletableFuture<WindowPicture> pictureOfWindow(DocumentId document) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("this host paints no pictures"));
    }

    /**
     * The words RAM or ROM {@code component} of circuit {@code document} holds in the running simulation, writes
     * included, once the earlier requests ran; empty when the simulation has no such memory (stopped or not built
     * yet). Called off the editor thread; completes off it.
     */
    default CompletableFuture<Optional<MemoryContents>> currentMemory(DocumentId document, String component) {
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
