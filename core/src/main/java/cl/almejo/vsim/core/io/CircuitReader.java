package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens any circuit file VSim 2 understands: a VSim 2 file (see {@link FileHeader}) or VSim 1 JSON; other JSON is
 * {@code NOT_VSIM_FILE}.
 */
public final class CircuitReader {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CircuitFormat format;
    private final VsimV1Importer importer;

    public CircuitReader(ComponentTypeRegistry registry) {
        this.format = new CircuitFormat(registry);
        this.importer = new VsimV1Importer(registry);
    }

    public CircuitDocument read(String content) throws CircuitFormatException {
        return parse(content).document();
    }

    public CircuitDocument read(Path path) throws CircuitFormatException {
        return open(path).document();
    }

    public OpenedCircuit open(Path path) throws CircuitFormatException {
        String content;
        try {
            content = Files.readString(path);
        } catch (IOException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.READ_ERROR, path.toString(), exception);
        }
        OpenedCircuit opened = parse(content);
        CircuitDocument document = opened.document();
        if (document.name().isBlank()) {
            String fileName = path.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            document.setName(dot > 0 ? fileName.substring(0, dot) : fileName);
        }
        return opened;
    }

    private OpenedCircuit parse(String content) throws CircuitFormatException {
        if (content == null || content.isBlank()) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_JSON, "empty content");
        }
        JsonNode root;
        try {
            root = mapper.readTree(content);
        } catch (JsonProcessingException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_JSON, exception.getOriginalMessage(), exception);
        }
        if (root == null || root.isMissingNode()) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_JSON, "empty content");
        }
        if (FileHeader.claimsVsim2(root)) {
            return new OpenedCircuit(format.read(root), false);
        }
        if (root.isObject() && root.path("gates").isArray()) {
            return new OpenedCircuit(importer.importDocument(root), true);
        }
        throw new CircuitFormatException(CircuitFormatException.Reason.NOT_VSIM_FILE, "not a VSim circuit");
    }
}
