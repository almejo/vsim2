package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.theme.FlatLafInstaller;
import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyCardTest {

    private static final Supplier<ColorScheme> SCHEME = () -> ColorSchemes.forTheme(false);

    private static void paintInto(java.awt.Component component) {
        component.setSize(new Dimension(260, Math.max(40, component.getPreferredSize().height)));
        component.doLayout();
        BufferedImage image = new BufferedImage(260, component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void aComponentCardHasTheLabelOrTheTypeAsTitleAndTheTypeAsSubtitle() {
        var type = StandardComponentTypes.registry().require(StandardComponentTypes.AND);
        PropertyCard labelled = PropertyCard.forComponent(type, "AND", "Q1", SCHEME);
        assertThat(labelled.title()).isEqualTo("Q1");
        assertThat(labelled.subtitle()).isEqualTo("AND");
        PropertyCard plain = PropertyCard.forComponent(type, "AND", "", SCHEME);
        assertThat(plain.title()).isEqualTo("AND");
        assertThat(plain.subtitle()).isEmpty();
    }

    @Test
    void aLabelEqualToTheTypeNameShowsNoSubtitle() {
        var type = StandardComponentTypes.registry().require(StandardComponentTypes.XOR);
        PropertyCard card = PropertyCard.forComponent(type, "XOR", "XOR", SCHEME);
        assertThat(card.title()).isEqualTo("XOR");
        assertThat(card.subtitle()).isEmpty();
    }

    @Test
    void aCircuitCardShowsItsCountsAndSetCountsChangesThem() {
        PropertyCard card = PropertyCard.forCircuit("Sumador", 3, 7);
        assertThat(card.title()).isEqualTo("Sumador");
        assertThat(card.parts()).isEqualTo(3);
        assertThat(card.wires()).isEqualTo(7);
        card.setCounts(4, 9);
        assertThat(card.parts()).isEqualTo(4);
        assertThat(card.wires()).isEqualTo(9);
    }

    @Test
    void anUnnamedCircuitShowsAPlaceholderTitle() {
        assertThat(PropertyCard.forCircuit("", 0, 0).title()).isEqualTo("Circuito sin nombre");
    }

    @Test
    void paintingInLightAndDarkDoesNotThrow() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            for (boolean dark : new boolean[]{false, true}) {
                UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", dark));
                paintInto(PropertyCard.forComponent(
                        StandardComponentTypes.registry().require(StandardComponentTypes.AND), "AND", "", SCHEME));
                paintInto(PropertyCard.forCircuit("x", 1, 2));
                paintInto(PropertyCard.forElement("Marco", "frame-tool"));
                paintInto(new SectionHeader("Configuración"));
            }
        } finally {
            UIManager.setLookAndFeel(original);
        }
    }

    @Test
    void aSectionHeaderKeepsItsTextAsGiven() {
        assertThat(new SectionHeader("Configuración").text()).isEqualTo("Configuración");
    }

    @Test
    void aCircuitCardShowsAThirdStatWithItsProblemsOnlyWhenThereAreAny() {
        PropertyCard card = PropertyCard.forCircuit("Sumador", 3, 7);
        assertThat(card.problemsText()).isEmpty();
        card.setProblems(2, 5);
        assertThat(card.problemErrors()).isEqualTo(2);
        assertThat(card.problemWarnings()).isEqualTo(5);
        assertThat(card.problemsText()).isEqualTo("2 problemas");
        card.setProblems(0, 1);
        assertThat(card.problemsText()).isEqualTo("1 problema");
        card.setProblems(0, 0);
        assertThat(card.problemsText()).isEmpty();
    }

    @Test
    void theProblemsStatIsColouredLikeTheBadgeAtPaintTime() {
        PropertyCard card = PropertyCard.forCircuit("Sumador", 3, 7);
        card.setProblems(1, 3);
        java.awt.Color forErrors = card.problemsColor();
        card.setProblems(0, 3);
        java.awt.Color forWarnings = card.problemsColor();
        assertThat(forErrors).isNotEqualTo(forWarnings);
        assertThat(forErrors).isEqualTo(cl.almejo.vsim.ui.window.InspectorPanel.badgeColor(
                cl.almejo.vsim.ui.window.InspectorPanel.Badge.ERROR));
    }
}
