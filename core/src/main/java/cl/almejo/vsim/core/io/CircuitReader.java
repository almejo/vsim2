package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens any circuit file VSim 2 understands: format v2 or VSim 1 JSON. */
public final class CircuitReader {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CircuitFormat format;
    private final VsimV1Importer importer;

    public CircuitReader(ComponentTypeRegistry registry) {
        this.format = new CircuitFormat(registry);
        this.importer = new VsimV1Importer(registry);
    }

    public CircuitDocument read(String content) throws CircuitFormatException {
        JsonNode root;
        try {
            root = mapper.readTree(content);
        } catch (JsonProcessingException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_JSON, exception.getOriginalMessage(), exception);
        }
        if (root != null && root.isObject() && root.has("formatVersion")) {
            return format.read(root);
        }
        if (root != null && root.isObject() && root.path("gates").isArray()) {
            return importer.importDocument(root);
        }
        throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, "not a VSim circuit");
    }

    public CircuitDocument read(Path path) throws CircuitFormatException {
        String content;
        try {
            content = Files.readString(path);
        } catch (IOException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.READ_ERROR, path.toString(), exception);
        }
        CircuitDocument document = read(content);
        if (document.name().isBlank()) {
            String fileName = path.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            document.setName(dot > 0 ? fileName.substring(0, dot) : fileName);
        }
        return document;
    }
}
