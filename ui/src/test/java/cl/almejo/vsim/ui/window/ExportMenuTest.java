package cl.almejo.vsim.ui.window;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class ExportMenuTest {

    private final JMenu file = new JMenu("Archivo");

    @BeforeEach
    void create() {
        file.add(new JMenuItem("Guardar como…"));
        file.addSeparator();
        file.add(new JMenuItem("Salir"));
    }

    private static Action action(String name) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent event) {
            }
        };
    }

    @Test
    void theFirstItemMakesTheSubmenuInAGroupOfItsOwnAndTheNextOnesJoinIt() {
        ExportMenu.add(file, new JMenuItem("Imagen…"));
        ExportMenu.add(file, new JMenuItem("PDF…"));

        assertThat(texts(file)).containsExactly("Guardar como…", "---", "Exportar", "---", "Salir");
        assertThat(texts(ExportMenu.find(file).orElseThrow())).containsExactly("Imagen…", "PDF…");
    }

    @Test
    void itSharesTheGroupAfterTheImportSubmenuWhicheverIsMadeFirst() {
        Runnable removeTable = ImportMenu.add(file, new JMenuItem("Tabla…"));
        Runnable removeImage = ExportMenu.add(file, new JMenuItem("Imagen…"));
        assertThat(texts(file)).containsExactly("Guardar como…", "---", "Importar", "Exportar", "---", "Salir");
        removeTable.run();
        removeImage.run();
        assertThat(texts(file)).containsExactly("Guardar como…", "---", "Salir");

        ExportMenu.add(file, new JMenuItem("Imagen…"));
        ImportMenu.add(file, new JMenuItem("Tabla…"));
        assertThat(texts(file)).containsExactly("Guardar como…", "---", "Importar", "Exportar", "---", "Salir");
    }

    @Test
    void theSubmenuIsFoundByItsMarkNotByItsText() {
        file.add(new JMenu("Exportar"));
        assertThat(ExportMenu.find(file)).isEmpty();
    }

    @Test
    void anItemGoesAfterItsAnchorInTheSubmenuElseAtTheEnd() {
        Action image = action("Imagen…");
        ExportMenu.add(file, new JMenuItem("Logisim…"));
        ExportMenu.add(file, new JMenuItem(image));
        assertThat(texts(ExportMenu.find(file).orElseThrow())).containsExactly("Logisim…", "Imagen…");
        ExportMenu.add(file, new JMenuItem("PDF…"), image);
        assertThat(texts(ExportMenu.find(file).orElseThrow())).containsExactly("Logisim…", "Imagen…", "PDF…");
    }

    @Test
    void takingTheItemsBackInAnyOrderLeavesTheMenuAsItWas() {
        List<String> before = texts(file);
        Runnable image = ExportMenu.add(file, new JMenuItem("Imagen…"));
        Runnable pdf = ExportMenu.add(file, new JMenuItem("PDF…"));
        image.run();
        assertThat(texts(ExportMenu.find(file).orElseThrow())).containsExactly("PDF…");
        pdf.run();
        assertThat(texts(file)).isEqualTo(before);
        assertThat(ExportMenu.find(file)).isEmpty();
    }
}
