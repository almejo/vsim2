package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.TestParts;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** «Convertir en componente…» from a frame (M6.3 spec §6) around an inverter drawn at (0, 0). */
class ConvertFrameTest {

    @TempDir
    Path directory;

    private TemplateFixture fixture;
    private Frame frame;

    @BeforeEach
    void create() {
        fixture = new TemplateFixture(directory.resolve("library"));
        fixture.drawInverter(0, 0);
        fixture.view.canvas().selection().set(java.util.List.of());
        frame = new Frame(fixture.openDocument.document().newFrameId(), new GridPoint(-2, -4), 14, 12, "Negador",
                FrameTint.GREEN, false);
        fixture.openDocument.execute(new CreateFrameCommand(frame));
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    void theDialogGetsTheContentsAndTheFrameTitleAsTheProposedName() {
        fixture.dialogs.cancelForm();
        fixture.view.templateActions().orElseThrow().createFromFrame(frame);
        assertThat(fixture.dialogs.forms).singleElement().satisfies(form -> {
            assertThat(form.name()).isEqualTo("Negador");
            assertThat(form.inputNames()).containsExactly("a");
            assertThat(form.outputNames()).containsExactly("y");
        });
        assertThat(fixture.openDocument.document().definitions()).isEmpty();
        assertThat(fixture.openDocument.document().frame(frame.id())).contains(frame);
    }

    @Test
    void replacingTheSelectionAlsoRemovesTheFrameInTheSameUndoStep() {
        CircuitDocument document = fixture.openDocument.document();
        List<DocumentElement> before = List.copyOf(document.elements());
        fixture.dialogs.acceptForm(form -> { });
        fixture.view.templateActions().orElseThrow().createFromFrame(frame);
        assertThat(document.frame(frame.id())).isEmpty();
        assertThat(document.components()).singleElement().satisfies(
                instance -> assertThat(instance.typeId()).isEqualTo("subcircuit:negador"));
        fixture.openDocument.history().undo();
        assertThat(document.frame(frame.id())).contains(frame);
        assertThat(document.elements()).containsExactlyElementsOf(before);
        assertThat(document.definitions()).isEmpty();
    }

    @Test
    void keepingTheSelectionKeepsTheFrame() {
        fixture.dialogs.acceptForm(form -> form.setReplaceSelection(false));
        fixture.view.templateActions().orElseThrow().createFromFrame(frame);
        CircuitDocument document = fixture.openDocument.document();
        assertThat(document.definition("negador")).isPresent();
        assertThat(document.frame(frame.id())).contains(frame);
        assertThat(document.components()).hasSize(3);
    }

    @Test
    void aFrameWithTestPartsOffersThemLikeASelection() {
        Component led = fixture.place(StandardComponentTypes.LED, 7, -2, "S");
        fixture.dialogs.cancelForm();
        fixture.view.templateActions().orElseThrow().createFromFrame(frame);
        assertThat(fixture.dialogs.forms).singleElement().satisfies(form -> {
            assertThat(form.testParts()).extracting(TestParts.TestPart::id).containsExactly(led.id());
            assertThat(form.outputNames()).containsExactly("S", "y");
        });
    }

    @Test
    void aFrameWithoutContentsDoesNothing() {
        Frame empty = new Frame(fixture.openDocument.document().newFrameId(), new GridPoint(40, 40), 8, 6, "Vacío",
                FrameTint.NONE, false);
        fixture.openDocument.execute(new CreateFrameCommand(empty));
        fixture.view.templateActions().orElseThrow().createFromFrame(empty);
        assertThat(fixture.dialogs.forms).isEmpty();
    }
}
