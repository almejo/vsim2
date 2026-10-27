package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyContent;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.paint.TextFonts;
import org.junit.jupiter.api.Test;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class StickyNoteLayoutTest {

    private static final double FULL_ZOOM = 8;

    private final FontMetrics metrics = metrics();

    private static FontMetrics metrics() {
        Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            return graphics.getFontMetrics(TextFonts.labelFont());
        } finally {
            graphics.dispose();
        }
    }

    private static StickyNote note(int width, int height, StickyContent content) {
        return new StickyNote(new ElementId("s1"), new GridPoint(10, 20), width, height, StickyColor.YELLOW, content,
                false);
    }

    private StickyNoteLayout layout(StickyNote note, double pixelsPerGrid) {
        return StickyNoteLayout.of(note, metrics, pixelsPerGrid);
    }

    @Test
    void thePaperIsTheNoteRectangleInDocumentPixels() {
        StickyNoteLayout layout = layout(note(8, 6, new StickyText("hola")), FULL_ZOOM);
        assertThat(layout.paper().getX()).isEqualTo(80);
        assertThat(layout.paper().getY()).isEqualTo(160);
        assertThat(layout.paper().getWidth()).isEqualTo(64);
        assertThat(layout.paper().getHeight()).isEqualTo(48);
    }

    @Test
    void textWrapsAtThePaperWidth() {
        String sentence = "uno dos tres cuatro cinco seis siete ocho nueve diez once doce trece catorce";
        StickyNoteLayout layout = layout(note(8, 40, new StickyText(sentence)), FULL_ZOOM);
        assertThat(layout.lines().size()).isGreaterThan(1);
        for (StickyNoteLayout.Line line : layout.lines()) {
            assertThat(line.x() + metrics.stringWidth(line.text())).isLessThanOrEqualTo(layout.paper().getMaxX());
        }
        String joined = String.join(" ", layout.lines().stream().map(StickyNoteLayout.Line::text).toList());
        assertThat(joined).isEqualTo(sentence);
    }

    @Test
    void aTwoHundredCharacterWordWrapsByCharacters() {
        String word = "x".repeat(200);
        StickyNoteLayout layout = layout(note(8, 60, new StickyText(word)), FULL_ZOOM);
        assertThat(layout.lines().size()).isGreaterThan(1);
        String joined = String.join("", layout.lines().stream().map(StickyNoteLayout.Line::text).toList());
        assertThat(joined).isEqualTo(word);
        for (StickyNoteLayout.Line line : layout.lines()) {
            assertThat(line.x() + metrics.stringWidth(line.text())).isLessThanOrEqualTo(layout.paper().getMaxX());
        }
    }

    @Test
    void thirtyLinesInTheSmallestNoteClipAndTheLastVisibleLineEndsInAnEllipsis() {
        List<String> lines = new ArrayList<>();
        for (int number = 1; number <= 30; number++) {
            lines.add("linea " + number);
        }
        StickyNoteLayout layout = layout(note(4, 3, new StickyText(String.join("\n", lines))), FULL_ZOOM);
        assertThat(layout.lines()).isNotEmpty().hasSizeLessThan(30);
        StickyNoteLayout.Line last = layout.lines().getLast();
        assertThat(last.text()).endsWith("…");
        assertThat(last.x() + metrics.stringWidth(last.text())).isLessThanOrEqualTo(layout.paper().getMaxX());
        assertThat(last.baseline()).isLessThanOrEqualTo(layout.paper().getMaxY());
    }

    @Test
    void emptyTextHasNoLines() {
        assertThat(layout(note(8, 6, new StickyText("")), FULL_ZOOM).lines()).isEmpty();
    }

    @Test
    void anEmptyChecklistHasNoCheckboxes() {
        StickyNoteLayout layout = layout(note(8, 6, new StickyChecklist(List.of())), FULL_ZOOM);
        assertThat(layout.checkboxes()).isEmpty();
        assertThat(layout.lines()).isEmpty();
    }

    @Test
    void aChecklistHasOneCheckboxPerVisibleItemAndTheTextBesideIt() {
        StickyChecklist items = new StickyChecklist(List.of(new StickyItem("a", true), new StickyItem("b", false),
                new StickyItem("c", false)));
        StickyNoteLayout layout = layout(note(8, 8, items), FULL_ZOOM);
        assertThat(layout.checkboxes()).hasSize(3);
        assertThat(layout.lines()).extracting(StickyNoteLayout.Line::text).containsExactly("a", "b", "c");
        assertThat(layout.lines()).extracting(StickyNoteLayout.Line::itemIndex).containsExactly(0, 1, 2);
        assertThat(layout.lines().getFirst().x()).isGreaterThan(layout.checkboxes().getFirst().getMaxX());
        assertThat(layout.checkboxes().get(1).getY()).isGreaterThan(layout.checkboxes().get(0).getY());
    }

    @Test
    void aLongChecklistShowsOnlyTheItemsThatFit() {
        List<StickyItem> many = new ArrayList<>();
        for (int number = 0; number < 40; number++) {
            many.add(new StickyItem("item " + number, false));
        }
        StickyNoteLayout layout = layout(note(4, 3, new StickyChecklist(many)), FULL_ZOOM);
        assertThat(layout.checkboxes()).isNotEmpty().hasSizeLessThan(40);
        assertThat(layout.lines().getLast().text()).endsWith("…");
    }

    @Test
    void theControlsSitInsideThePaper() {
        StickyNoteLayout layout = layout(note(8, 6, new StickyText("x")), FULL_ZOOM);
        assertThat(layout.paper().contains(layout.closeBox())).isTrue();
        assertThat(layout.closeBox().getMaxX()).isGreaterThan(layout.paper().getMaxX() - 16);
        assertThat(layout.paper().contains(layout.resizeHandle())).isTrue();
        assertThat(layout.resizeHandle().getMaxX()).isEqualTo(layout.paper().getMaxX());
        assertThat(layout.resizeHandle().getMaxY()).isEqualTo(layout.paper().getMaxY());
        assertThat(layout.fold().getBounds2D().getMaxX()).isEqualTo(layout.paper().getMaxX());
    }

    @Test
    void aFoldedNoteIsTheFoldedSquareWithOnlyTheFold() {
        StickyNote folded = note(8, 6, new StickyText("hola")).withFolded(true);
        StickyNoteLayout layout = layout(folded, FULL_ZOOM);
        assertThat(layout.paper().getWidth()).isEqualTo(StickyNote.FOLDED_SIZE * 8);
        assertThat(layout.paper().getHeight()).isEqualTo(StickyNote.FOLDED_SIZE * 8);
        assertThat(layout.fold().getBounds2D().isEmpty()).isFalse();
        assertThat(layout.lines()).isEmpty();
        assertThat(layout.checkboxes()).isEmpty();
        assertThat(layout.closeBox().isEmpty()).isTrue();
        assertThat(layout.resizeHandle().isEmpty()).isTrue();
    }

    @Test
    void nothingThrowsAtQuarterZoomOrForDegenerateText() {
        StickyContent[] contents = {new StickyText(""), new StickyText("\n\n\n"), new StickyText("x".repeat(500)),
                new StickyText("a b c ".repeat(100)), new StickyChecklist(List.of()),
                new StickyChecklist(List.of(new StickyItem("", false), new StickyItem("y".repeat(300), true)))};
        for (StickyContent content : contents) {
            for (double zoom : new double[]{0.5, 2, 8, 32}) {
                for (boolean folded : new boolean[]{false, true}) {
                    assertThatCode(() -> layout(note(4, 3, content).withFolded(folded), zoom))
                            .doesNotThrowAnyException();
                    assertThatCode(() -> layout(note(30, 20, content).withFolded(folded), zoom))
                            .doesNotThrowAnyException();
                }
            }
        }
    }
}
