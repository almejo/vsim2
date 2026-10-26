package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.ImportedGrid;
import cl.almejo.vsim.core.logic.TableImport;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TablePreviewModelTest {

    private final TablePreviewModel preview = new TablePreviewModel();

    private void show(String text, int inputCount) {
        ImportedGrid grid = TableImport.readCsv(text);
        preview.show(grid, TableImport.columns(grid), inputCount,
                TableImport.toSpec(grid, inputCount, "tabla").problems());
    }

    private List<String> column(int column) {
        List<String> values = new ArrayList<>();
        for (int row = 0; row < preview.getRowCount(); row++) {
            values.add(preview.shown(row, column));
        }
        return values;
    }

    @Test
    void beforeAnyTableThePreviewIsEmpty() {
        assertThat(preview.isEmpty()).isTrue();
        assertThat(preview.getRowCount()).isZero();
        assertThat(preview.getColumnCount()).isZero();

        show("A,B,F\n0,0,1\n", 2);
        assertThat(preview.isEmpty()).isFalse();
        preview.clear();
        assertThat(preview.isEmpty()).isTrue();
        assertThat(preview.getColumnCount()).isZero();
    }

    @Test
    void cellsShowTheValueTheyMeanAndTheTextOfTheFileInTheTooltip() {
        show("A;B;F\n0;0;1,0\n0;1;VERDADERO\n1;0;-\n1;1;\n", 2);

        assertThat(preview.getColumnCount()).isEqualTo(4);
        assertThat(preview.getColumnName(0)).isEqualTo("Fila");
        assertThat(preview.getColumnName(1)).isEqualTo("A");
        assertThat(preview.getColumnName(3)).isEqualTo("F");
        assertThat(column(1)).containsExactly("0", "0", "1", "1");
        assertThat(column(3)).containsExactly("1", "1", "X", "X");
        assertThat(preview.tooltip(0, 3)).isEqualTo("En el archivo: «1,0»");
        assertThat(preview.tooltip(1, 3)).isEqualTo("En el archivo: «VERDADERO»");
        assertThat(preview.tooltip(2, 3)).isEqualTo("En el archivo: «-»");
        assertThat(preview.tooltip(3, 3)).isEqualTo("La celda está vacía: se toma como X.");
        assertThat(preview.isBlankCell(3, 3)).as("a muted X").isTrue();
        assertThat(preview.isBlankCell(2, 3)).isFalse();
        assertThat(preview.tooltip(0, 1)).as("written as shown").isNull();
        assertThat(preview.isProblem(0, 3)).isFalse();
    }

    @Test
    void aLowerCaseXOrTrueAreShownAsTheirValue() {
        show("A,F,G\n0,x,TRUE\n1,X,0.0\n", 1);
        assertThat(column(2)).containsExactly("X", "X");
        assertThat(column(3)).containsExactly("1", "0");
        assertThat(preview.tooltip(0, 2)).as("only the case differs").isNull();
        assertThat(preview.tooltip(0, 3)).isEqualTo("En el archivo: «TRUE»");
        assertThat(preview.tooltip(1, 3)).isEqualTo("En el archivo: «0.0»");
    }

    @Test
    void theInputsAreTheColumnsBeforeTheSplit() {
        show("Cin,A,B,Cout,Suma\n0,0,0,0,0\n", 3);
        assertThat(preview.isInput(0)).as("the row number").isFalse();
        assertThat(preview.isOutput(0)).isFalse();
        assertThat(preview.isInput(1)).isTrue();
        assertThat(preview.isInput(3)).isTrue();
        assertThat(preview.isOutput(4)).isTrue();
        assertThat(preview.isFirstOutput(4)).isTrue();
        assertThat(preview.isFirstOutput(5)).isFalse();

        show("Cin,A,B,Cout,Suma\n0,0,0,0,0\n", 1);
        assertThat(preview.isInput(2)).isFalse();
        assertThat(preview.isFirstOutput(2)).isTrue();
    }

    @Test
    void aBlankSpacerColumnIsLeftOut() {
        show("A,,B,F\n0,,0,0\n0,,1,1\n", 2);
        assertThat(preview.getColumnCount()).isEqualTo(4);
        assertThat(preview.getColumnName(2)).isEqualTo("B");
        assertThat(column(2)).containsExactly("0", "1");
    }

    @Test
    void cellsAndRowsWithAProblemAreFlaggedWithTheProblemAsTooltip() {
        show("A,B,Y\n0,0,0\n0,2,1\n1,0,7\n1,1,0,1\n", 2);

        assertThat(preview.isProblem(1, 2)).isTrue();
        assertThat(preview.tooltip(1, 2)).isEqualTo("Celda B3: «2» no sirve como entrada: debe ser 0 o 1.");
        assertThat(preview.shown(1, 2)).as("what is written, when it means nothing").isEqualTo("2");
        assertThat(preview.isProblem(1, 1)).isFalse();
        assertThat(preview.isProblem(2, 3)).isTrue();
        assertThat(preview.shown(2, 3)).isEqualTo("7");
        for (int column = 0; column < 4; column++) {
            assertThat(preview.isProblem(3, column)).as("the whole row 5").isTrue();
        }
        assertThat(preview.tooltip(3, 1)).isEqualTo("Fila 5: La fila tiene 4 celdas, pero la tabla tiene 3 columnas.");
        assertThat(preview.isProblem(0, 1)).isFalse();
        assertThat(preview.firstProblemRow()).isEqualTo(1);
    }

    @Test
    void anEmptyInputIsAProblemNotAnX() {
        show("A,B,Y\n0,,1\n", 2);
        assertThat(preview.shown(0, 2)).isEmpty();
        assertThat(preview.isProblem(0, 2)).isTrue();
        assertThat(preview.tooltip(0, 2)).isEqualTo("Celda B2: Falta el valor de la entrada (debe ser 0 o 1).");
    }

    @Test
    void bothRowsOfAConflictingCombinationAreFlagged() {
        show("A,B,C,F,G\n0,0,0,0,1\n0,1,1,1,0\n1,0,1,1,1\n0,1,1,0,0\n", 3);

        assertThat(preview.fileRow(1)).isEqualTo(3);
        assertThat(preview.fileRow(3)).isEqualTo(5);
        for (int column = 0; column < 6; column++) {
            assertThat(preview.isProblem(1, column)).isTrue();
            assertThat(preview.isProblem(3, column)).isTrue();
            assertThat(preview.isProblem(0, column)).isFalse();
            assertThat(preview.isProblem(2, column)).isFalse();
        }
        assertThat(preview.tooltip(3, 4))
                .isEqualTo("La combinación 0 1 1 aparece dos veces con F distinta (filas 3 y 5).");
        assertThat(preview.firstProblemRow()).isEqualTo(1);
    }

    @Test
    void aRepeatedOrBlankNameFlagsItsColumnHeader() {
        show("A,a,,F\n0,0,1,1\n", 2);
        assertThat(preview.isHeaderProblem(2)).isTrue();
        assertThat(preview.headerTooltip(2)).isEqualTo("Celda B1: Hay más de una columna llamada «a».");
        assertThat(preview.isHeaderProblem(3)).isTrue();
        assertThat(preview.isHeaderProblem(1)).isFalse();
        assertThat(preview.headerTooltip(1)).isEqualTo("Entrada");
        assertThat(preview.headerTooltip(4)).isEqualTo("Salida");
    }

    @Test
    void rowNumbersAreTheFileRowsSkippingBlankLines() {
        show("\n\nA,B,F\n0,0,0\n\n0,1,1\n,,\n1,1,1\n", 2);
        assertThat(preview.getRowCount()).isEqualTo(3);
        assertThat(preview.getValueAt(0, 0)).isEqualTo(4);
        assertThat(preview.getValueAt(1, 0)).isEqualTo(6);
        assertThat(preview.getValueAt(2, 0)).isEqualTo(8);
    }

    @Test
    void aLargeTableIsAModelOverTheGrid() {
        StringBuilder csv = new StringBuilder("A,B,C,D,E,F,G,H,P,Q,R,S,T,U,V,W\n");
        for (int row = 0; row < 256; row++) {
            List<String> cells = new ArrayList<>();
            for (int bit = 15; bit >= 0; bit--) {
                cells.add(String.valueOf(row >> bit % 8 & 1));
            }
            csv.append(String.join(",", cells)).append('\n');
        }
        show(csv.toString(), 8);
        assertThat(preview.getRowCount()).isEqualTo(256);
        assertThat(preview.getColumnCount()).isEqualTo(17);
        assertThat(preview.getValueAt(255, 0)).isEqualTo(257);
        assertThat(preview.shown(255, 16)).isEqualTo("1");
    }

    @Test
    void theTintsKeepTheTextReadableInLightAndDark() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : List.of(new FlatLightLaf(), new FlatDarkLaf())) {
                UIManager.setLookAndFeel(theme);
                Color text = WizardColors.foreground();
                for (Color background : List.of(WizardColors.previewInput(), WizardColors.previewOutput(),
                        WizardColors.previewProblem())) {
                    assertThat(WizardColors.contrast(text, background)).as(theme.getName()).isGreaterThan(4.5);
                }
                assertThat(WizardColors.contrast(text, WizardColors.hint())).as("hint " + theme.getName())
                        .isGreaterThan(4.5);
                assertThat(WizardColors.hint()).isNotEqualTo(WizardColors.background());
                assertThat(WizardColors.previewInput()).isNotEqualTo(WizardColors.previewOutput());
                assertThat(WizardColors.previewProblem()).isNotEqualTo(WizardColors.cellBackground());
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }
}
