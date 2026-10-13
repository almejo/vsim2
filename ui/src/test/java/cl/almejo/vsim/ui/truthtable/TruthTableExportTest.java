package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Row;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TruthTableExportTest {

    @TempDir
    Path directory;

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void theSuggestedNameIsTheDocumentNameWithTablaAndCsv() {
        assertThat(TruthTableExport.suggestedName("sumador")).isEqualTo("sumador-tabla.csv");
        assertThat(TruthTableExport.suggestedName("a/b:c")).isEqualTo("a_b_c-tabla.csv");
    }

    @Test
    void theCsvExtensionIsAddedOnlyWhenMissing() {
        assertThat(TruthTableExport.withCsvExtension(directory.resolve("tabla")))
                .isEqualTo(directory.resolve("tabla.csv"));
        assertThat(TruthTableExport.withCsvExtension(directory.resolve("tabla.CSV")))
                .isEqualTo(directory.resolve("tabla.CSV"));
    }

    @Test
    void theFileIsTheCsvTextInUtf8() throws Exception {
        TruthTable table = new TruthTable(List.of(new Column("Señal", "c1", 1)), List.of(new Column("Y", "c2", 1)),
                List.of(new Row(List.of(Value.ZERO), List.of(Value.ONE), true)), false);
        Path target = directory.resolve("tabla.csv");
        Files.writeString(target, "old");

        TruthTableExport.write(target, table);

        assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo("Señal,Y\n0,1\n");
        try (Stream<Path> files = Files.list(directory)) {
            assertThat(files).containsExactly(target);
        }
    }
}
