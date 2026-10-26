package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.ImportedGrid;
import cl.almejo.vsim.core.logic.TableImport;
import org.junit.jupiter.api.Test;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

class TablePreviewTest {

    @Test
    void theColumnNamesStayUnderTheBarOnceTheTableIsShownInAWindow() {
        TablePreviewModel model = new TablePreviewModel();
        TablePreview preview = new TablePreview(model);
        ImportedGrid grid = TableImport.readCsv("A,B,F\n0,0,0\n0,1,1\n1,0,1\n1,1,1\n");
        model.show(grid, TableImport.columns(grid), 2, TableImport.toSpec(grid, 2, "tabla").problems());
        preview.refresh();

        // A real window calls addNotify, and JTable then puts its bare header in the scroll pane's column header.
        preview.addNotify();

        JTable table = preview.table();
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
        assertThat(table.getTableHeader().getParent())
                .as("the names row is inside the column header with the «Entradas» · «Salidas» bar")
                .isSameAs(scroll.getColumnHeader().getView());
    }
}
