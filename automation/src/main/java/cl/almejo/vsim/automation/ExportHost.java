package cl.almejo.vsim.automation;

import cl.almejo.vsim.automation.export.ExportReceipt;
import cl.almejo.vsim.automation.export.ExportRequest;

import java.util.concurrent.CompletableFuture;

/**
 * The editor-side export (spec §7, Exports row): the menus' PDF, PNG and SVG exports without their dialogs, only into
 * the remembered export folder, under a new name, never overwriting. Called off the editor thread; the default
 * refuses, for hosts that export nothing (tests).
 */
public interface ExportHost {

    /**
     * Writes the export and completes with what it wrote; a refusal (closed circuit, unknown frame or element, nothing
     * to export) fails the future with an {@link AutomationException}.
     */
    default CompletableFuture<ExportReceipt> export(ExportRequest request) {
        return CompletableFuture.failedFuture(new AutomationException(AutomationError.NOT_ALLOWED,
                "This editor does not export"));
    }
}
