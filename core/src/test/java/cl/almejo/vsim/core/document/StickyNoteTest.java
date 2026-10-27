package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.command.RotateElementsCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.frame;
import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

class StickyNoteTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private StickyNote note(int x, int y, StickyContent content) {
        StickyNote note = new StickyNote(document.newStickyNoteId(), new GridPoint(x, y), 8, 6, StickyColor.YELLOW,
                content, false);
        document.add(note);
        return note;
    }

    private static StickyChecklist list(String... lines) {
        return new StickyText(String.join("\n", lines)).toChecklist();
    }

    // --- content ---

    @Test
    void aTextBecomesAChecklistWithOneUndoneItemPerNonEmptyLine() {
        StickyChecklist checklist = new StickyText("uno\n\n  \ndos\n").toChecklist();

        assertThat(checklist.items()).containsExactly(new StickyItem("uno", false), new StickyItem("dos", false));
    }

    @Test
    void aChecklistBecomesTextWithDoneItemsPrefixed() {
        StickyChecklist checklist = list("a", "b").toggled(0);

        assertThat(checklist.toText()).isEqualTo(new StickyText("✓ a\nb"));
    }

    @Test
    void editedKeepsTheDoneStateOfUnchangedLinesMatchedInOrder() {
        StickyChecklist original = list("a", "b", "a").toggled(0);

        StickyChecklist edited = original.edited(List.of("a", "a", "c"));

        assertThat(edited.items()).containsExactly(new StickyItem("a", true), new StickyItem("a", false),
                new StickyItem("c", false));
    }

    @Test
    void editedDropsEmptyLines() {
        assertThat(list("a").edited(List.of("", "a", "", "b")).items())
                .containsExactly(new StickyItem("a", false), new StickyItem("b", false));
    }

    @Test
    void editedDropsBlankLinesToo() {
        assertThat(list("a").edited(List.of("a", "   ", "\t", "b")).items())
                .containsExactly(new StickyItem("a", false), new StickyItem("b", false));
    }

    @Test
    void toggledFlipsOneItemAndDoneCountCountsThem() {
        StickyChecklist toggled = list("a", "b", "c").toggled(1).toggled(2);

        assertThat(toggled.doneCount()).isEqualTo(2);
        assertThat(toggled.toggled(1).doneCount()).isEqualTo(1);
    }

    @Test
    void firstLineOfATextIsItsFirstNonEmptyLine() {
        assertThat(new StickyText("\n  \n hola \nchau").firstLine()).isEqualTo("hola");
        assertThat(new StickyText("").firstLine()).isEmpty();
    }

    @Test
    void firstLineOfAChecklistIsTheFirstPendingItemElseTheFirstElseEmpty() {
        assertThat(list("a", "b").toggled(0).firstLine()).isEqualTo("b");
        assertThat(list("a", "b").toggled(0).toggled(1).firstLine()).isEqualTo("a");
        assertThat(new StickyChecklist(List.of()).firstLine()).isEmpty();
    }

    @Test
    void searchableTextHoldsEveryLineOrItem() {
        assertThat(new StickyText("x\ny").searchableText()).isEqualTo("x\ny");
        assertThat(list("x", "y").searchableText()).isEqualTo("x\ny");
    }

    // --- element ---

    @Test
    void theConstructorClampsASizeUnderTheMinimum() {
        StickyNote note = new StickyNote(new ElementId("s1"), GridPoint.ORIGIN, 1, 1, StickyColor.PINK,
                new StickyText(""), false);

        assertThat(note.width()).isEqualTo(StickyNote.MINIMUM_WIDTH).isEqualTo(4);
        assertThat(note.height()).isEqualTo(StickyNote.MINIMUM_HEIGHT).isEqualTo(3);
        assertThat(note.withSize(2, 9).width()).isEqualTo(4);
    }

    @Test
    void aFoldedNoteIsTwoByTwoInItsBounds() {
        StickyNote note = note(3, 4, new StickyText("x"));

        assertThat(ElementBounds.of(note, registry)).isEqualTo(new Bounds(3, 4, 8, 6));
        assertThat(ElementBounds.of(note.withFolded(true), registry)).isEqualTo(new Bounds(3, 4, 2, 2));
        assertThat(note.withFolded(true).visibleWidth()).isEqualTo(StickyNote.FOLDED_SIZE);
    }

    @Test
    void translatingMovesTheAnchorOnly() {
        StickyNote moved = note(1, 1, new StickyText("x")).translate(4, -1);

        assertThat(moved.position()).isEqualTo(new GridPoint(5, 0));
        assertThat(moved.width()).isEqualTo(8);
    }

    @Test
    void theDocumentStoresStickyNotesInOrderAndGivesTheirIds() {
        StickyNote first = note(0, 0, new StickyText("a"));
        StickyNote second = note(10, 0, new StickyText("b"));

        assertThat(first.id().value()).startsWith("s");
        assertThat(document.stickyNotes()).containsExactly(first, second);
        assertThat(document.stickyNote(second.id())).contains(second);
        assertThat(document.elements()).contains(first, second);
        StickyNote changed = first.withColor(StickyColor.GREEN);
        document.replace(changed);
        assertThat(document.stickyNote(first.id())).contains(changed);
        assertThat(document.remove(first.id())).contains(changed);
        assertThat(document.stickyNotes()).containsExactly(second);
    }

    @Test
    void aRemovedNoteComesBackAtItsOldOrder() {
        StickyNote first = note(0, 0, new StickyText("a"));
        StickyNote second = note(10, 0, new StickyText("b"));
        long order = document.orderOf(first.id());

        document.remove(first.id());
        document.restore(first, order);

        assertThat(document.stickyNotes()).containsExactly(first, second);
    }

    @Test
    void aDocumentCopyKeepsTheNotes() {
        StickyNote first = note(0, 0, new StickyText("a"));

        assertThat(document.copy().stickyNotes()).containsExactly(first);
    }

    @Test
    void theNetlistIgnoresStickyNotes() {
        gate(registry, document, 0, 0);
        gate(registry, document, 10, 0);
        wire(document, 4, 2, 10, 2);
        NetlistCompiler compiler = new NetlistCompiler(registry);
        Netlist without = compiler.compile(document);

        note(2, 0, new StickyText("encima"));

        assertThat(compiler.compile(document)).isEqualTo(without);
    }

    // --- commands over notes ---

    @Test
    void rotatingMovesTheAnchorLikeANoteAndKeepsThePaperShape() {
        StickyNote note = note(10, 0, new StickyText("x"));
        Component pivot = gate(registry, document, 0, 0);

        RotateElementsCommand rotate = new RotateElementsCommand(Set.of(note.id(), pivot.id()), true, registry);
        rotate.apply(document);

        StickyNote rotated = document.stickyNote(note.id()).orElseThrow();
        assertThat(rotated.width()).isEqualTo(8);
        assertThat(rotated.height()).isEqualTo(6);
        assertThat(rotated.position()).isNotEqualTo(note.position());
        rotate.revert(document);
        assertThat(document.stickyNote(note.id())).contains(note);
    }

    @Test
    void setLabelChangesATextNoteAndRevertRestoresIt() {
        StickyNote note = note(0, 0, new StickyText("antes"));
        SetLabelCommand command = new SetLabelCommand(note.id(), "después");

        command.apply(document);

        assertThat(document.stickyNote(note.id()).orElseThrow().content()).isEqualTo(new StickyText("después"));
        assertThat(command.description()).isEqualTo("command.editStickyNote");
        command.revert(document);
        assertThat(document.stickyNote(note.id())).contains(note);
    }

    @Test
    void setLabelOnAChecklistKeepsTheTicksOfUnchangedLinesAndRevertRestoresThemAll() {
        StickyNote note = note(0, 0, list("a", "b").toggled(0));
        SetLabelCommand command = new SetLabelCommand(note.id(), "a\nc");

        command.apply(document);

        assertThat(document.stickyNote(note.id()).orElseThrow().content()).isEqualTo(
                new StickyChecklist(List.of(new StickyItem("a", true), new StickyItem("c", false))));
        command.revert(document);
        assertThat(document.stickyNote(note.id())).contains(note);
    }

    // --- frames ---

    @Test
    void aFrameContainsANoteWhoseAnchorIsInsideEvenIfThePaperSticksOut() {
        Frame block = frame(document, 0, 0, 10, 10);
        StickyNote inside = note(8, 8, new StickyText("x"));
        StickyNote outside = note(11, 0, new StickyText("y"));

        Set<ElementId> contents = FrameContents.of(document, registry, block);

        assertThat(contents).contains(inside.id()).doesNotContain(outside.id());
    }

    @Test
    void aLockedFrameWallsBlockANoteInsideLikeAPlainNote() {
        Frame block = frame(document, 0, 0, 10, 10);
        document.replace(block.withLocked(true));
        StickyNote inside = note(8, 2, new StickyText("x"));

        GridPoint limited = LockedFrameWalls.limit(document, registry, List.of(inside.id()), 5, 0);

        assertThat(limited).isEqualTo(new GridPoint(2, 0));
    }

    @Test
    void aLockedFrameBlockCarriesItsNote() {
        Frame block = frame(document, 0, 0, 10, 10);
        document.replace(block.withLocked(true));
        StickyNote inside = note(2, 2, new StickyText("x"));

        assertThat(FrameContents.withLockedBlocks(document, registry, List.of(block.id())))
                .contains(block.id(), inside.id());
    }
}
