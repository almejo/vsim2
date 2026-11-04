package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.io.logisim.ImportNote;
import cl.almejo.vsim.core.io.logisim.LogisimNotes;
import cl.almejo.vsim.core.io.logisim.LogisimPoint;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ImportReportTest {

    private static ImportReport report(ImportNote... notes) {
        return ImportReport.of(List.of(notes), id -> "pieza " + id.value());
    }

    private static ImportNote noDelay(String circuit, String partId) {
        return ImportNote.about(ImportNote.Kind.APPROXIMATED, circuit, LogisimNotes.NO_DELAY, "Bit Extender", "2")
                .withElement(Optional.of(new ElementId(partId)));
    }

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void restore() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void theReportIsGroupedByCircuitInTheOrderTheyAppearAndTheWholeProjectFirst() {
        ImportReport report = report(
                ImportNote.about(ImportNote.Kind.APPROXIMATED, "alu", LogisimNotes.DRAWING_TOO_BIG, "3"),
                ImportNote.about(ImportNote.Kind.INVALID, "main", LogisimNotes.DRAWING_TOO_BIG, "4"),
                ImportNote.about(ImportNote.Kind.INVALID, "", LogisimNotes.MISSING_MAIN, "x"));

        assertThat(report.circuits()).extracting(ImportReport.CircuitGroup::title)
                .containsExactly("Todo el proyecto", "Circuito «alu»", "Circuito «main»");
    }

    @Test
    void insideACircuitTheKindsComeAsRealDifferencesUnsupportedApproximatedInvalid() {
        ImportReport report = report(
                ImportNote.about(ImportNote.Kind.INVALID, "alu", LogisimNotes.DRAWING_TOO_BIG, "3"),
                ImportNote.about(ImportNote.Kind.APPROXIMATED, "alu", LogisimNotes.NO_DELAY, "Pin", "2"),
                ImportNote.about(ImportNote.Kind.UNSUPPORTED, "alu", LogisimNotes.UNSUPPORTED_PART, "Random"),
                ImportNote.about(ImportNote.Kind.UNSUPPORTED, "alu", LogisimNotes.UNSUPPORTED_PART, "Ram"),
                ImportNote.about(ImportNote.Kind.DIFFERENT, "alu", LogisimNotes.COUNTER_LOAD, "Counter"));

        assertThat(report.circuits()).hasSize(1);
        assertThat(report.circuits().get(0).kinds()).extracting(ImportReport.KindGroup::title)
                .containsExactly("Diferencias reales (1)", "Sin soporte (2)", "Aproximado (1)", "Inválido (1)");
    }

    @Test
    void aNoteRepeatedWithTheSameTextIsExplainedOnceFollowedByWhereItApplies() {
        ImportReport report = report(noDelay("alu", "c1"), noDelay("alu", "c2"), noDelay("alu", "c3"));

        List<ImportReport.Entry> entries = report.circuits().get(0).kinds().get(0).entries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).explanation()).isEqualTo(Messages.format(LogisimNotes.NO_DELAY, "Bit Extender", "2"));
        assertThat(entries.get(0).places()).extracting(ImportReport.Place::text)
                .containsExactly("pieza c1", "pieza c2", "pieza c3");
        assertThat(entries.get(0).places()).extracting(ImportReport.Place::element)
                .containsExactly(Optional.of(new ElementId("c1")), Optional.of(new ElementId("c2")),
                        Optional.of(new ElementId("c3")));
    }

    @Test
    void theSameKeyWithAnotherTextIsAnotherEntry() {
        ImportReport report = report(noDelay("alu", "c1"),
                ImportNote.about(ImportNote.Kind.APPROXIMATED, "alu", LogisimNotes.NO_DELAY, "Pin", "2")
                        .withElement(Optional.of(new ElementId("c2"))));

        assertThat(report.circuits().get(0).kinds().get(0).entries()).hasSize(2);
    }

    @Test
    void theSameTextInAnotherCircuitIsNotMerged() {
        ImportReport report = report(noDelay("alu", "c1"), noDelay("main", "c1"));

        assertThat(report.circuits()).hasSize(2);
        assertThat(report.circuits().get(0).kinds().get(0).entries().get(0).places()).hasSize(1);
    }

    @Test
    void aNoteWithoutAnElementHasNoClickablePlaceAndOneWithOnlyALogisimPointShowsIt() {
        ImportReport report = report(
                ImportNote.about(ImportNote.Kind.INVALID, "alu", LogisimNotes.DRAWING_TOO_BIG, "3"),
                ImportNote.at(ImportNote.Kind.UNSUPPORTED, "alu", new LogisimPoint(1200, 80),
                        LogisimNotes.UNSUPPORTED_PART, "Random"));

        ImportReport.KindGroup unsupported = report.circuits().get(0).kinds().get(0);
        assertThat(unsupported.entries().get(0).places()).hasSize(1);
        ImportReport.Place place = unsupported.entries().get(0).places().get(0);
        assertThat(place.element()).isEmpty();
        assertThat(place.text()).contains("1200").contains("80");
        ImportReport.KindGroup invalid = report.circuits().get(0).kinds().get(1);
        assertThat(invalid.entries().get(0).places()).isEmpty();
        assertThat(invalid.entries().get(0).explanation()).isEqualTo(Messages.format(LogisimNotes.DRAWING_TOO_BIG, "3"));
    }

    @Test
    void identicalNotesWithNowhereToPointAreCounted() {
        ImportReport report = report(
                ImportNote.about(ImportNote.Kind.INVALID, "alu", LogisimNotes.DRAWING_TOO_BIG, "3"),
                ImportNote.about(ImportNote.Kind.INVALID, "alu", LogisimNotes.DRAWING_TOO_BIG, "3"));

        ImportReport.Entry entry = report.circuits().get(0).kinds().get(0).entries().get(0);
        assertThat(entry.count()).isEqualTo(2);
        assertThat(entry.explanation()).endsWith("(×2)");
    }

    @Test
    void theCountOfNotesIsTheNotesNotTheEntries() {
        ImportReport report = report(noDelay("alu", "c1"), noDelay("alu", "c2"),
                ImportNote.about(ImportNote.Kind.INVALID, "", LogisimNotes.MISSING_MAIN, "x"));

        assertThat(report.noteCount()).isEqualTo(3);
        assertThat(report.isEmpty()).isFalse();
    }

    @Test
    void noNotesMeansNoGroupsAndNoReport() {
        ImportReport report = report();

        assertThat(report.isEmpty()).isTrue();
        assertThat(report.circuits()).isEmpty();
        assertThat(report.noteCount()).isZero();
    }

    @Test
    void theTextIsTranslated() {
        Messages.initialize(Locale.ENGLISH);
        ImportReport report = report(ImportNote.about(ImportNote.Kind.APPROXIMATED, "", LogisimNotes.NO_DELAY, "Pin", "2"));

        assertThat(report.circuits().get(0).title()).isEqualTo("Whole project");
        assertThat(report.circuits().get(0).kinds().get(0).title()).isEqualTo("Approximated (1)");
    }

    private static final ReportHost PATH_HOST = new ReportHost() {
        @Override
        public String describe(ElementId element) {
            return "pieza " + element.value();
        }

        @Override
        public String describeInside(ElementId element, String circuit) {
            return "pieza " + element.value() + " › " + circuit;
        }

        @Override
        public boolean goTo(ElementId element) {
            return true;
        }

        @Override
        public Optional<java.awt.Dimension> size() {
            return Optional.empty();
        }

        @Override
        public void rememberSize(java.awt.Dimension size) {
        }
    };

    @Test
    void aCustomCircuitLinksItsHoldingInstanceOnceInItsHeadingAndItsLinesDoNot() {
        ImportReport report = ImportReport.of(List.of(noDelay("main", "m1"), noDelay("main", "m2"),
                noDelay("Muldiv", "h1"), ImportNote.about(ImportNote.Kind.APPROXIMATED, "Muldiv",
                        LogisimNotes.UNSUPPORTED_PART, "Random").withElement(Optional.of(new ElementId("h1")))),
                PATH_HOST, "main");

        ImportReport.CircuitGroup main = report.circuits().get(0);
        assertThat(main.place()).isEmpty();
        assertThat(main.kinds().get(0).entries().get(0).places()).extracting(ImportReport.Place::text)
                .containsExactly("pieza m1", "pieza m2");
        ImportReport.CircuitGroup muldiv = report.circuits().get(1);
        assertThat(muldiv.place()).hasValueSatisfying(place -> {
            assertThat(place.text()).isEqualTo("pieza h1 › Muldiv");
            assertThat(place.element()).contains(new ElementId("h1"));
        });
        assertThat(muldiv.kinds().get(0).entries()).allSatisfy(entry -> assertThat(entry.places()).isEmpty());
        assertThat(report.noteCount()).isEqualTo(4);
    }
}
