package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.VsimVersion;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class OpeningFilesTest {

    private record Shown(String title, String message, String details) {
    }

    @TempDir
    Path folder;

    private final List<Shown> shown = new ArrayList<>();
    private final OpeningFiles opening = new OpeningFiles(StandardComponentTypes.registry(),
            (parent, title, message, details) -> shown.add(new Shown(title, message, details)));

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(folder.resolve(name), content);
    }

    @Test
    void aNewerFileSaysBothVersions() throws IOException {
        Path file = write("futuro.vsim",
                "{\"format\":\"vsim2\",\"savedWith\":\"9.9\",\"formatVersion\":99,\"kind\":\"circuit\"}");
        assertThat(opening.read(file, null)).isEmpty();
        assertThat(shown).singleElement().satisfies(one -> {
            assertThat(one.message()).contains("futuro.vsim").contains("se guardó con VSim 9.9")
                    .contains("más nuevo que esta versión (" + VsimVersion.current() + ")")
                    .contains("Actualiza VSim para abrirlo.");
        });
    }

    @Test
    void aTextFileIsNotAVsimFile() throws IOException {
        Path file = write("notas.vsim", "hola, esto son mis apuntes\n");
        assertThat(opening.read(file, null)).isEmpty();
        assertThat(shown).singleElement().satisfies(one ->
                assertThat(one.message()).contains("notas.vsim").contains("no es un archivo de VSim"));
    }

    @Test
    void aJsonWithoutTheMarkerIsNotAVsimFile() throws IOException {
        Path file = write("otro.json", "{\"nombre\":\"x\"}");
        assertThat(opening.read(file, null)).isEmpty();
        assertThat(shown.get(0).message()).contains("otro.json").contains("no es un archivo de VSim");
    }

    @Test
    void aLogisimFileSaysWhatItSeems() throws IOException {
        Path file = write("alu.circ", "<?xml version=\"1.0\"?>\n<project source=\"3.8.0\" "
                + "version=\"1.0\">This file is intended to be loaded by Logisim</project>");
        assertThat(opening.read(file, null)).isEmpty();
        assertThat(shown.get(0).message()).contains("alu.circ").contains("Logisim").doesNotContain("no es un archivo");
    }

    @Test
    void aDamagedFileNamesTheElementAndKeepsTheTechnicalDetailApart() throws IOException {
        Path file = write("roto.vsim", "{\"format\":\"vsim2\",\"formatVersion\":" + CircuitFormat.FORMAT_VERSION
                + ",\"kind\":\"circuit\",\"components\":[{\"id\":\"c7\",\"type\":\""
                + StandardComponentTypes.INPUT_PIN + "\",\"x\":1,\"y\":1,\"parameters\":{\"value\":\"0x5A\"}}]}");
        assertThat(opening.read(file, null)).isEmpty();
        assertThat(shown).singleElement().satisfies(one -> {
            assertThat(one.message()).contains("roto.vsim").contains("dañado").contains("c7")
                    .contains(Messages.componentName(StandardComponentTypes.INPUT_PIN)).doesNotContain("0x5A");
            assertThat(one.details()).contains("0x5A");
        });
    }

    @Test
    void aTruncatedVsim2FileIsDamagedNotForeign() throws IOException {
        Path file = write("cortado.vsim", "{\"format\":\"vsim2\",\"formatVersion\":5,\"kind\":\"circ");
        assertThat(opening.read(file, null)).isEmpty();
        assertThat(shown.get(0).message()).contains("dañado");
    }

    @Test
    void aMissingFileIsReportedToo() {
        assertThat(opening.read(folder.resolve("no-esta.vsim"), null)).isEmpty();
        assertThat(shown.get(0).message()).contains("no-esta.vsim");
    }

    @Test
    void aGoodFileOpensAndShowsNothing() throws IOException {
        Path file = write("bien.vsim", "{\"format\":\"vsim2\",\"formatVersion\":" + CircuitFormat.FORMAT_VERSION
                + ",\"kind\":\"circuit\"}");
        Optional<OpenDocument> document = opening.read(file, null);
        assertThat(document).isPresent();
        assertThat(shown).isEmpty();
    }

    @Test
    void severalBadFilesWhileCollectingGiveOneMessage() throws IOException {
        Path first = write("uno.vsim", "no soy nada");
        Path second = write("dos.vsim",
                "{\"format\":\"vsim2\",\"savedWith\":\"9.9\",\"formatVersion\":99,\"kind\":\"circuit\"}");
        opening.collecting(() -> {
            opening.read(first, null);
            opening.read(second, null);
            assertThat(shown).isEmpty();
        });
        assertThat(shown).singleElement().satisfies(one -> {
            assertThat(one.message()).contains("uno.vsim").contains("dos.vsim");
            assertThat(one.title()).isEqualTo(Messages.text("fileproblem.title.many"));
        });
    }

    @Test
    void oneBadFileWhileCollectingKeepsTheSingleTitle() throws IOException {
        Path file = write("uno.vsim", "no soy nada");
        opening.collecting(() -> opening.read(file, null));
        assertThat(shown).singleElement().satisfies(one ->
                assertThat(one.title()).isEqualTo(Messages.text("fileproblem.title")));
    }

    @Test
    void nothingIsShownWhenCollectingFindsNoProblem() {
        opening.collecting(() -> {
        });
        assertThat(shown).isEmpty();
    }

    @Test
    void bothBundlesHaveEveryKey() {
        for (Locale locale : List.of(Messages.SPANISH, Locale.ENGLISH)) {
            ResourceBundle bundle = Messages.bundle(locale);
            for (String key : FileProblem.MESSAGE_KEYS) {
                assertThat(bundle.containsKey(key)).as("%s %s", locale, key).isTrue();
            }
        }
    }
}
