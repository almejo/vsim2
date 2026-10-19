package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportMenuTest {

    private final JMenu file = new JMenu("Archivo");

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        file.add(new JMenuItem("Guardar"));
        file.addSeparator();
        file.add(new JMenuItem("Salir"));
    }

    @Test
    void theFirstItemMakesTheSubmenuBeforeTheFirstSeparatorAndTheNextOnesJoinIt() {
        ImportMenu.add(file, new JMenuItem("Componente…"));
        ImportMenu.add(file, new JMenuItem("Tabla…"));

        assertThat(texts(file)).containsExactly("Guardar", "---", "Importar", "---", "Salir");
        JMenu importMenu = ImportMenu.find(file).orElseThrow();
        assertThat(texts(importMenu)).containsExactly("Componente…", "Tabla…");
    }

    @Test
    void theSubmenuIsFoundByItsMarkNotByItsText() {
        file.add(new JMenu("Importar"));
        assertThat(ImportMenu.find(file)).isEmpty();
        ImportMenu.add(file, new JMenuItem("Componente…"));
        assertThat(ImportMenu.find(file)).hasValueSatisfying(menu ->
                assertThat(menu.getClientProperty(ImportMenu.MARK)).isEqualTo(Boolean.TRUE));
    }

    @Test
    void takingTheItemsBackInAnyOrderLeavesTheMenuAsItWas() {
        List<String> before = texts(file);
        Runnable component = ImportMenu.add(file, new JMenuItem("Componente…"));
        Runnable table = ImportMenu.add(file, new JMenuItem("Tabla…"));
        component.run();
        assertThat(texts(ImportMenu.find(file).orElseThrow())).containsExactly("Tabla…");
        table.run();
        assertThat(texts(file)).isEqualTo(before);
        assertThat(ImportMenu.find(file)).isEmpty();

        component = ImportMenu.add(file, new JMenuItem("Componente…"));
        table = ImportMenu.add(file, new JMenuItem("Tabla…"));
        table.run();
        component.run();
        assertThat(texts(file)).isEqualTo(before);
    }

    @Test
    void aMenuWithoutSeparatorsGetsTheSubmenuAtTheEnd() {
        JMenu bare = new JMenu("Archivo");
        bare.add(new JMenuItem("Guardar"));
        Runnable remover = ImportMenu.add(bare, new JMenuItem("Tabla…"));
        assertThat(texts(bare)).containsExactly("Guardar", "---", "Importar");
        remover.run();
        assertThat(texts(bare)).containsExactly("Guardar");
    }

    private static List<String> texts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (Component child : menu.getMenuComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }
}
