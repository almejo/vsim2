package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentPreset;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One available palette choice: a type, a user component (with the definition needed to place it) or a preset, a
 * shortcut to a type with some parameters already set («NAND» is an AND with «Negar salida»).
 */
public record PaletteEntry(ComponentType type, Optional<PalettePanel.UserComponent> userComponent,
                           Optional<ComponentPreset> preset) {

    public PaletteEntry {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(userComponent, "userComponent");
        Objects.requireNonNull(preset, "preset");
    }

    public PaletteEntry(ComponentType type, Optional<PalettePanel.UserComponent> userComponent) {
        this(type, userComponent, Optional.empty());
    }

    /** The entry of a standard type. */
    static PaletteEntry of(ComponentType type) {
        return new PaletteEntry(type, Optional.empty(), Optional.empty());
    }

    /** The entry of {@code preset}, whose type is {@code type}. */
    static PaletteEntry of(ComponentType type, ComponentPreset preset) {
        return new PaletteEntry(type, Optional.empty(), Optional.of(preset));
    }

    /**
     * The entry of {@code preset} when {@code registry} has its type. A preset names a type, not a placed part, so
     * there are no parameters to look the type up with.
     */
    static Optional<PaletteEntry> of(ComponentPreset preset, ComponentTypeRegistry registry) {
        String presetTypeId = preset.typeId();
        return registry.find(presetTypeId).map(type -> of(type, preset));
    }

    /**
     * The standard entry called {@code id} (a type of {@code registry} or a preset of a type there), as a drop or a
     * stored favourite names it; empty when nothing has that id.
     */
    public static Optional<PaletteEntry> forId(String id, ComponentTypeRegistry registry) {
        Optional<PaletteEntry> preset = StandardComponentTypes.preset(id).flatMap(found -> of(found, registry));
        return preset.or(() -> registry.find(id).map(PaletteEntry::of));
    }

    /** What favourites, recents and drags store: the preset's id ({@code nand}) or the type id. */
    public String id() {
        return preset.map(ComponentPreset::id).orElseGet(type::id);
    }

    /** The parameters a placed part starts with on top of the defaults: the preset's, none for a type. */
    public Map<String, Object> parameters() {
        return preset.map(ComponentPreset::parameters).orElseGet(Map::of);
    }

    /** The name the palette shows: «NAND» for the preset, the definition name for a user component. */
    public String name() {
        return preset.map(found -> Messages.componentName(found.id())).orElseGet(() -> Messages.componentName(type));
    }

    /** The section is also shown in quick-insert results to distinguish similarly named components. */
    public String category() {
        return userComponent.map(component -> Messages.text(component.inLibrary()
                ? "palette.section.library" : "palette.section.document"))
                .orElseGet(() -> Messages.categoryName(type.category()));
    }

    /** What the palette tree holds for this entry: the user component itself, otherwise the entry. */
    Object treeItem() {
        return userComponent.<Object>map(component -> component).orElse(this);
    }
}
