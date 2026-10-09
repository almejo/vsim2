package cl.almejo.vsim.ui.library;

import java.util.List;

/** The icons of the template actions, by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class TemplateIcons {

    public static final String CREATE_COMPONENT = "component-create";
    public static final String IMPORT_COMPONENT = "component-import";
    public static final String UPDATE_COMPONENT = "component-update";
    public static final String DISASSEMBLE = "component-disassemble";
    public static final String UPDATE_FROM_LIBRARY = "component-update-from-library";

    private TemplateIcons() {
    }

    /** Every template icon, once. */
    public static List<String> names() {
        return List.of(CREATE_COMPONENT, IMPORT_COMPONENT, UPDATE_COMPONENT, DISASSEMBLE, UPDATE_FROM_LIBRARY);
    }
}
