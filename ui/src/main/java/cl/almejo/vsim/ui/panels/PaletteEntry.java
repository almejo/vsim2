package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Objects;
import java.util.Optional;

/** One available palette choice, including the definition needed to place a user component. */
public record PaletteEntry(ComponentType type, Optional<PalettePanel.UserComponent> userComponent) {

    public PaletteEntry {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(userComponent, "userComponent");
    }

    /** The section is also shown in quick-insert results to distinguish similarly named components. */
    public String category() {
        return userComponent.map(component -> Messages.text(component.inLibrary()
                ? "palette.section.library" : "palette.section.document"))
                .orElseGet(() -> Messages.categoryName(type.category()));
    }

    Object treeItem() {
        return userComponent.<Object>map(component -> component).orElse(type);
    }
}
