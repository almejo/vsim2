package cl.almejo.vsim.automation.export;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;

import java.util.Objects;

/**
 * What an export wrote: the circuit and the version exported, the format, the file's name, folder and full path, and
 * its size: pixels for a PNG or SVG (0 for a PDF), sheets for a PDF (1 for an image).
 */
public record ExportReceipt(DocumentId document, DocumentVersion version, ExportRequest.Format format,
                            String fileName, String folder, String path, int width, int height, int pages) {

    public ExportReceipt {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(path, "path");
    }
}
