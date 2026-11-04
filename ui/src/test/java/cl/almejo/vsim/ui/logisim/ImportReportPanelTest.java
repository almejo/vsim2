package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.io.logisim.ImportNote;
import cl.almejo.vsim.core.io.logisim.LogisimNotes;
import cl.almejo.vsim.core.io.logisim.LogisimPoint;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ImportReportPanelTest {

    private final List<ElementId> visited = new ArrayList<>();

    private final ReportHost host = new ReportHost() {
        @Override
        public String describe(ElementId element) {
            return "pieza " + element.value();
        }

        @Override
        public boolean goTo(ElementId element) {
            visited.add(element);
            return true;
        }

        @Override
        public Optional<Dimension> size() {
            return Optional.empty();
        }

        @Override
        public void rememberSize(Dimension size) {
        }
    };

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private static ImportNote noDelay(String partId) {
        return ImportNote.about(ImportNote.Kind.APPROXIMATED, "alu", LogisimNotes.NO_DELAY, "Bit Extender", "2")
                .withElement(Optional.of(new ElementId(partId)));
    }

    private static <T extends Component> List<T> all(Container container, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container inner) {
                found.addAll(all(inner, type));
            }
        }
        return found;
    }

    @Test
    void everyPlaceThatIsAPartIsAButtonThatGoesToItInTheOrderListed() {
        ImportReport report = ImportReport.of(List.of(noDelay("c1"), noDelay("c2")), host::describe);
        ImportReportPanel panel = new ImportReportPanel("resumen", report, host);

        assertThat(panel.placeButtons()).extracting(JButton::getText).containsExactly("pieza c1", "pieza c2");
        panel.placeButtons().get(1).doClick();
        panel.placeButtons().get(0).doClick();
        assertThat(visited).containsExactly(new ElementId("c2"), new ElementId("c1"));
    }

    @Test
    void aPlaceWithoutAPartIsPlainTextAndTheExplanationShowsOnce() {
        ImportReport report = ImportReport.of(List.of(noDelay("c1"), noDelay("c2"),
                ImportNote.at(ImportNote.Kind.UNSUPPORTED, "alu", new LogisimPoint(1200, 80),
                        LogisimNotes.UNSUPPORTED_PART, "Random")), host::describe);
        ImportReportPanel panel = new ImportReportPanel("resumen", report, host);

        assertThat(panel.placeButtons()).hasSize(2);
        assertThat(all(panel, JLabel.class)).extracting(JLabel::getText).anyMatch(text -> text.contains("1200"));
        List<String> texts = all(panel, JTextArea.class).stream().map(JTextArea::getText).toList();
        assertThat(texts).contains("resumen", Messages.format(LogisimNotes.NO_DELAY, "Bit Extender", "2"));
        assertThat(texts.stream().filter(text -> text.equals(Messages.format(LogisimNotes.NO_DELAY, "Bit Extender", "2"))))
                .hasSize(1);
    }

    @Test
    void theCircuitAndKindHeadingsAreShown() {
        ImportReport report = ImportReport.of(List.of(noDelay("c1")), host::describe);
        ImportReportPanel panel = new ImportReportPanel("resumen", report, host);

        assertThat(all(panel, JLabel.class)).extracting(JLabel::getText)
                .contains("Circuito «alu»", "Aproximado (1)");
    }
}
