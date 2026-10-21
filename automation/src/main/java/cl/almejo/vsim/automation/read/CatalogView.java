package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;

import java.util.List;

/** The component types the AI can place in one circuit: the built-in ones, then the circuit's own user components. */
public record CatalogView(DocumentId document, DocumentVersion version, List<ComponentTypeView> types) {

    public CatalogView {
        types = List.copyOf(types);
    }
}
