package cl.almejo.vsim.ui.window;

import java.util.Objects;
import java.util.Optional;

/**
 * Where each entry of Ver, Herramientas and Lógica goes (menus of release 0.9, mockup 2026-11-13-menus v2), in menu
 * order: siblings are sorted by their declaration here, whatever the order the features install them, and a
 * separator goes between two neighbours of different {@link #group()}. A slot with a {@link #titleKey()} is a
 * submenu, made when its first entry arrives and removed when its last one leaves ({@link MenuSlots}). A slot may
 * hold several items of one feature, kept in the order they were added.
 */
public enum MenuSlot {
    // Ver
    PANELS(MenuId.VIEW, 0, "menu.view.panels"),
    ZOOM(MenuId.VIEW, 1, "menu.view.zoom"),
    GRID_AND_GUIDES(MenuId.VIEW, 1, "menu.view.gridAndGuides"),
    SHOW(MenuId.VIEW, 1, "menu.view.show"),
    /** «Lupa», then «Mirar adentro». */
    LENS(MenuId.VIEW, 2, null),
    LOOK_INSIDE(MenuId.VIEW, 2, null),
    PRESENTATION_MODE(MenuId.VIEW, 3, null),
    PRESENT(MenuId.VIEW, 3, "menu.view.present"),
    // Ver ▸ Paneles: the six panels (Alt+1 … Alt+6), then the waveforms
    PANEL_TOGGLES(PANELS, 0),
    WAVEFORMS(PANELS, 1),
    // Ver ▸ Zoom
    ZOOM_STEPS(ZOOM, 0),
    ZOOM_FIELD(ZOOM, 1),
    CENTER_VIEW(ZOOM, 1),
    // Ver ▸ Cuadrícula y guías
    GRID(GRID_AND_GUIDES, 0),
    SNAP(GRID_AND_GUIDES, 0),
    GUIDES(GRID_AND_GUIDES, 1),
    // Ver ▸ Mostrar
    BUS_VALUES(SHOW, 0),
    HIGHLIGHT_NET(SHOW, 0),
    SHEETS(SHOW, 0),
    // Ver ▸ Presentar
    AI_PRESENT(PRESENT, 0),
    SHOW_USED_SHORTCUTS(PRESENT, 1),
    // Herramientas: the editor's tools
    SELECT_TOOL(MenuId.TOOLS, 0, null),
    FRAME_TOOL(MenuId.TOOLS, 0, null),
    STICKY_NOTE_TOOL(MenuId.TOOLS, 0, null),
    TEXT_TOOL(MenuId.TOOLS, 0, null),
    PAN_TOOL(MenuId.TOOLS, 0, null),
    ZOOM_TOOL(MenuId.TOOLS, 0, null),
    // Lógica
    TRUTH_TABLE(MenuId.LOGIC, 0, null),
    CIRCUIT_FROM_TRUTH_TABLE(MenuId.LOGIC, 1, null),
    CIRCUIT_FROM_EXPRESSIONS(MenuId.LOGIC, 1, null),
    KARNAUGH_PRACTICE(MenuId.LOGIC, 2, null),
    /** «Probar la máquina…» (a state machine's test mode). */
    TEST_MACHINE(MenuId.LOGIC, 3, null);

    private final MenuId menu;
    private final MenuSlot parent;
    private final int group;
    private final String titleKey;

    MenuSlot(MenuId menu, int group, String titleKey) {
        this.menu = menu;
        this.parent = null;
        this.group = group;
        this.titleKey = titleKey;
    }

    MenuSlot(MenuSlot parent, int group) {
        this.menu = Objects.requireNonNull(parent, "parent").menu;
        this.parent = parent;
        this.group = group;
        this.titleKey = null;
    }

    /** The menu of the bar this slot lives in, directly or inside its submenus. */
    public MenuId menu() {
        return menu;
    }

    /** The submenu holding this slot, or empty when it sits in the bar's menu itself. */
    public Optional<MenuSlot> parent() {
        return Optional.ofNullable(parent);
    }

    /** Neighbours of different groups get a separator between them. */
    public int group() {
        return group;
    }

    /** The bundle key of the submenu's title, or empty when this slot holds items. */
    public Optional<String> titleKey() {
        return Optional.ofNullable(titleKey);
    }

    public boolean isSubmenu() {
        return titleKey != null;
    }
}
