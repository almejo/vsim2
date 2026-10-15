package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FramesInFilesTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat circuitFormat = new CircuitFormat(registry);
    private final FragmentFormat fragmentFormat = new FragmentFormat(registry);

    private final Frame frame = new Frame(new ElementId("f1"), new GridPoint(2, 3), 10, 6, "ALU", FrameTint.BLUE, true);

    @Test
    void aCircuitWithFramesRoundTrips() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(frame);
        String json = circuitFormat.write(document);
        assertThat(json).contains("\"frames\"").contains("\"tint\" : \"BLUE\"");
        assertThat(circuitFormat.read(json).frames()).containsExactly(frame);
    }

    @Test
    void aFileWithoutFramesLoadsAndWritesNoFramesKey() throws CircuitFormatException {
        CircuitDocument document = circuitFormat.read("{\"formatVersion\":2,\"kind\":\"circuit\"}");
        assertThat(document.frames()).isEmpty();
        assertThat(circuitFormat.write(document)).doesNotContain("frames");
    }

    @Test
    void anUnknownTintReadsAsNone() throws CircuitFormatException {
        CircuitDocument document = circuitFormat.read("""
                {"formatVersion":2,"kind":"circuit","frames":[
                {"id":"f1","x":0,"y":0,"width":8,"height":5,"title":"x","tint":"MAUVE","locked":false}]}
                """);
        assertThat(document.frames().get(0).tint()).isEqualTo(FrameTint.NONE);
    }

    @Test
    void anUndersizedFrameIsClampedToTheMinimumInsteadOfFailingTheFile() throws CircuitFormatException {
        CircuitDocument document = circuitFormat.read("""
                {"formatVersion":2,"kind":"circuit","frames":[
                {"id":"f1","x":3,"y":4,"width":2,"height":-1,"title":"x","tint":"BLUE","locked":true}]}
                """);
        assertThat(document.frames()).singleElement().satisfies(frame -> {
            assertThat(frame.width()).isEqualTo(Frame.MINIMUM_WIDTH);
            assertThat(frame.height()).isEqualTo(Frame.MINIMUM_HEIGHT);
            assertThat(frame.topLeft()).isEqualTo(new GridPoint(3, 4));
        });
    }

    @Test
    void aFragmentWithFramesRoundTripsAndStartsAtTheOrigin() throws CircuitFormatException {
        CircuitFragment fragment = new CircuitFragment(List.of(), List.of(),
                List.of(new TextLabel(new ElementId("l2"), new GridPoint(4, 4), "n")), List.of(), List.of(frame));
        CircuitFragment read = fragmentFormat.read(fragmentFormat.write(fragment));
        assertThat(read.frames()).containsExactly(frame.translate(-2, -3));
        assertThat(read.isEmpty()).isFalse();
    }

    @Test
    void copyingAFrameFromADocumentSelectsIt() {
        CircuitDocument document = new CircuitDocument();
        document.add(frame);
        CircuitFragment fragment = CircuitFragment.of(document, List.of(frame.id()), registry);
        assertThat(fragment.frames()).containsExactly(frame.translate(-2, -3));
    }

    @Test
    void pastingGivesFramesNewIdsAndOffsetsThem() {
        CircuitDocument document = new CircuitDocument();
        document.add(frame);
        CircuitFragment fragment = CircuitFragment.of(document, List.of(frame.id()), registry);
        PasteCommand paste = new PasteCommand(fragment, new GridPoint(20, 20));
        paste.apply(document);
        assertThat(document.frames()).hasSize(2);
        Frame pasted = document.frames().get(1);
        assertThat(pasted.id()).isNotEqualTo(frame.id());
        assertThat(pasted.topLeft()).isEqualTo(new GridPoint(20, 20));
        assertThat(pasted.title()).isEqualTo("ALU");
        assertThat(paste.pastedIds()).containsExactly(pasted.id());
        paste.revert(document);
        assertThat(document.frames()).containsExactly(frame);
    }
}
