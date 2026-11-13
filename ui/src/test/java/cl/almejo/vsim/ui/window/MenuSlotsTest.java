package cl.almejo.vsim.ui.window;

import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class MenuSlotsTest {

    private final JMenu view = new JMenu("Ver");

    @Test
    void itemsGoInTheOrderOfTheirSlotsWhateverTheOrderTheyArrive() {
        MenuSlots.add(view, MenuSlot.PRESENTATION_MODE, new JMenuItem("Modo presentación"));
        MenuSlots.add(view, MenuSlot.LOOK_INSIDE, new JMenuItem("Mirar adentro"));
        MenuSlots.add(view, MenuSlot.LENS, new JMenuItem("Lupa"));
        MenuSlots.add(view, MenuSlot.SHEETS, new JMenuItem("Hojas"));
        MenuSlots.add(view, MenuSlot.BUS_VALUES, new JMenuItem("Valores"));

        assertThat(texts(view)).containsExactly("Mostrar", "---", "Lupa", "Mirar adentro", "---", "Modo presentación");
        assertThat(texts(MenuSlots.find(view, MenuSlot.SHOW).orElseThrow())).containsExactly("Valores", "Hojas");
    }

    @Test
    void severalItemsOfOneSlotKeepTheirOrderAndAGroupChangeAddsASeparator() {
        MenuSlots.add(view, MenuSlot.CENTER_VIEW, new JMenuItem("Centrar"));
        MenuSlots.add(view, MenuSlot.ZOOM_STEPS, new JMenuItem("Acercar"));
        MenuSlots.add(view, MenuSlot.ZOOM_STEPS, new JMenuItem("Alejar"));
        MenuSlots.add(view, MenuSlot.ZOOM_FIELD, new JMenuItem("Escribir"));

        assertThat(texts(MenuSlots.find(view, MenuSlot.ZOOM).orElseThrow()))
                .containsExactly("Acercar", "Alejar", "---", "Escribir", "Centrar");
    }

    @Test
    void takingTheLastItemBackRemovesTheSubmenuAndItsSeparator() {
        Runnable lens = MenuSlots.add(view, MenuSlot.LOOK_INSIDE, new JMenuItem("Mirar adentro"));
        Runnable guides = MenuSlots.add(view, MenuSlot.GUIDES, new JMenuItem("Mostrar guías"));
        Runnable grid = MenuSlots.add(view, MenuSlot.GRID, new JMenuItem("Cuadrícula"));

        guides.run();
        assertThat(texts(MenuSlots.find(view, MenuSlot.GRID_AND_GUIDES).orElseThrow())).containsExactly("Cuadrícula");
        grid.run();
        grid.run();

        assertThat(MenuSlots.find(view, MenuSlot.GRID_AND_GUIDES)).isEmpty();
        assertThat(texts(view)).containsExactly("Mirar adentro");
        lens.run();
        assertThat(view.getMenuComponentCount()).isZero();
    }

    @Test
    void anItemWithoutASlotStaysWhereItWasPut() {
        view.add(new JMenuItem("Otro"));
        MenuSlots.add(view, MenuSlot.LOOK_INSIDE, new JMenuItem("Mirar adentro"));
        MenuSlots.add(view, MenuSlot.PANEL_TOGGLES, new JMenuItem("Paleta"));

        assertThat(texts(view)).containsExactly("Otro", "Paneles", "---", "Mirar adentro");
    }
}
