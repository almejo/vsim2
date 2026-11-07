package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterProfile;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.standard.StandardParameters;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Groups, profiles with «↺», multi-line text and conditional rows in the parameter form (Terminal, spec §7). */
class ParameterFormProfilesTest {

    static final String PROFILE = "profile";

    /** A Terminal-like type: a profile, parameters in groups, a row shown only with 8 bits and a long text. */
    static ComponentType profiledType() {
        return new ComponentType("screen", ComponentCategory.INPUT_OUTPUT, new GridSize(4, 4), List.of(),
                List.of(ParameterSpec.choice(PROFILE, "classic", List.of("classic", "modern")).asDisplayOnly()
                                .inGroup("piece"),
                        ParameterSpec.integer("rows", 24, 1, 100).inGroup("screen"),
                        ParameterSpec.integer("width", 7, 7, 8).inGroup("data"),
                        ParameterSpec.choice("encoding", "utf8", List.of("utf8", "latin1")).inGroup("data")
                                .visibleWhen("width", 8),
                        StandardParameters.EDGE.inGroup("data"),
                        ParameterSpec.integer("history", 0, 0, 10_000).inGroup("screen"),
                        ParameterSpec.bool("glow", true).asDisplayOnly().inGroup("appearance"),
                        ParameterSpec.text("expected", "").asDisplayOnly().asMultiline().inGroup("teaching")),
                parameters -> (context, pinIndex) -> { })
                .withProfiles(PROFILE, List.of(
                        new ParameterProfile("classic", Map.of("width", 7, "history", 0, "glow", true,
                                "edge", StandardParameters.RISING)),
                        new ParameterProfile("modern", Map.of("width", 8, "history", 1000, "glow", false,
                                "edge", StandardParameters.RISING))));
    }

    private final ComponentType type = profiledType();

    private ParameterForm form(Map<String, Object> current) {
        return new ParameterForm(type, current);
    }

    /** The descriptions the commits carried, in order. */
    private static List<String> recordCommits(ParameterForm form) {
        List<String> descriptions = new ArrayList<>();
        form.addCommitListener(() -> descriptions.add(form.commitDescription()));
        return descriptions;
    }

    @SuppressWarnings("unchecked")
    private static String shownText(JComboBox<?> box) {
        JComboBox<Object> typed = (JComboBox<Object>) box;
        Component shown = typed.getRenderer().getListCellRendererComponent(new JList<>(), typed.getSelectedItem(),
                -1, false, false);
        return ((JLabel) shown).getText();
    }

    @Test
    void inTheSectionedLayoutTheUngroupedParametersComeFirstUnderConfiguracionAndTheDialogHasNoSuchHeader() {
        ComponentType mixed = new ComponentType("mixed", ComponentCategory.INPUT_OUTPUT, new GridSize(2, 2),
                List.of(), List.of(ParameterSpec.integer("rows", 24, 1, 100).inGroup("screen"),
                ParameterSpec.integer("delay", 1, 0, 9)), parameters -> (context, pinIndex) -> { });
        javax.swing.JPanel host = new javax.swing.JPanel();
        new ParameterForm(mixed, Map.of()).addRowsTo(host);
        List<String> headers = new java.util.ArrayList<>();
        for (Component child : host.getComponents()) {
            if (child instanceof SectionHeader header) {
                headers.add(header.text());
            }
        }
        assertThat(headers).containsExactly("Configuración", "Pantalla");

        ParameterForm dialogForm = new ParameterForm(mixed, Map.of());
        assertThat(List.of(dialogForm.getComponents())).filteredOn(SectionHeader.class::isInstance)
                .extracting(child -> ((SectionHeader) child).text()).containsExactly("Pantalla");
    }

    @Test
    void eachGroupHasAHeaderAndItsRowsFollowItInTheOrderOfFirstAppearance() {
        ParameterForm form = form(Map.of());
        List<Component> children = List.of(form.getComponents());

        assertThat(form.groupHeader("piece").text()).isEqualTo("Pieza");
        assertThat(form.groupHeader("teaching").text()).isEqualTo("Para enseñar");
        int piece = children.indexOf(form.groupHeader("piece"));
        int screen = children.indexOf(form.groupHeader("screen"));
        int rows = children.indexOf(form.label("rows"));
        int history = children.indexOf(form.label("history"));
        int data = children.indexOf(form.groupHeader("data"));
        int appearance = children.indexOf(form.groupHeader("appearance"));
        assertThat(piece).isLessThan(screen);
        assertThat(screen).isLessThan(rows);
        assertThat(rows).isLessThan(history);
        assertThat(history).as("«history» joins its group, before «Datos»").isLessThan(data);
        assertThat(data).isLessThan(appearance);
    }

    @Test
    void aTypeWithoutGroupsHasNoHeaders() {
        ParameterForm form = new ParameterForm(cl.almejo.vsim.core.component.standard.StandardComponentTypes
                .registry().require(cl.almejo.vsim.core.component.standard.StandardComponentTypes.CLOCK), Map.of());

        assertThat(form.groupHeaders()).isEmpty();
    }

    @Test
    void theRowsJoinAHostGridWithTheirHeaders() {
        ParameterForm form = form(Map.of());
        JPanel host = new JPanel();

        form.addRowsTo(host);

        List<Component> children = List.of(host.getComponents());
        Component summary = form.profileSummary().getParent();
        assertThat(children).contains(form.groupHeader("screen"), form.label("rows"), summary);
        assertThat(children.indexOf(summary))
                .as("the summary is right under the profile row")
                .isGreaterThan(children.indexOf(form.label(PROFILE)))
                .isLessThan(children.indexOf(form.groupHeader("screen")));
    }

    @Test
    void aValueOtherThanTheProfilesIsBoldWithADotAndARestoreButton() {
        ParameterForm form = form(Map.of("history", 50));

        assertThat(form.label("history").getText()).startsWith("● ");
        assertThat(form.label("history").getFont().isBold()).isTrue();
        assertThat(form.restoreButton("history").isVisible()).isTrue();
        assertThat(form.restoreButton("history").getToolTipText()).isEqualTo("Volver al valor del perfil: 0");
        assertThat(form.label("rows").getText()).doesNotStartWith("●");
        assertThat(form.label("rows").getFont().isBold()).isFalse();
        assertThat(form.restoreButton("glow").isVisible()).isFalse();
        assertThat(form.restoreButton("rows")).as("a profile never sets the rows").isNull();
    }

    @Test
    void theRestoreTooltipNamesChoicesAndSwitchesAsTheUserReadsThem() {
        ParameterForm form = form(Map.of("glow", false, "edge", StandardParameters.FALLING));

        assertThat(form.restoreButton("glow").getToolTipText()).isEqualTo("Volver al valor del perfil: sí");
        assertThat(form.restoreButton("edge").getToolTipText()).isEqualTo("Volver al valor del perfil: Subida");
    }

    @Test
    void theProfileSaysModifiedAndCountsTheDifferencesUntilOneIsSetBackByHand() {
        ParameterForm form = form(Map.of("history", 50, "glow", false));
        JComboBox<?> profile = (JComboBox<?>) form.editor(PROFILE);

        assertThat(shownText(profile)).isEqualTo("classic (modificado)");
        assertThat(form.profileSummary().getText()).isEqualTo("2 propiedades distintas del perfil");
        assertThat(form.restoreProfileButton().isVisible()).isTrue();

        ((JCheckBox) form.editor("glow")).doClick();
        assertThat(form.profileSummary().getText()).isEqualTo("1 propiedad distinta del perfil");

        ((JSpinner) form.editor("history")).setValue(0);
        assertThat(shownText(profile)).isEqualTo("classic");
        assertThat(form.profileSummary().getText()).isEqualTo("Igual al perfil");
        assertThat(form.restoreProfileButton().isVisible()).isFalse();
        assertThat(form.label("history").getText()).doesNotStartWith("●");
    }

    @Test
    void restoringOneValueSetsItBackAndCommitsOnceAsARestore() {
        ParameterForm form = form(Map.of("history", 50, "glow", false));
        List<String> commits = recordCommits(form);

        form.restoreButton("history").doClick();

        assertThat(form.rawValues()).containsEntry("history", 0).containsEntry("glow", false);
        assertThat(commits).containsExactly("command.restoreParameter");
        assertThat(form.restoreButton("history").isVisible()).isFalse();
        assertThat(form.commitDescription()).as("back to a plain edit").isEqualTo("command.configure");
    }

    @Test
    void choosingAProfileSetsAllItsValuesInOneCommit() {
        ParameterForm form = form(Map.of("rows", 30));
        List<String> commits = recordCommits(form);

        ((JComboBox<?>) form.editor(PROFILE)).setSelectedItem("modern");

        assertThat(form.rawValues()).containsEntry(PROFILE, "modern").containsEntry("width", 8)
                .containsEntry("history", 1000).containsEntry("glow", false).containsEntry("rows", 30);
        assertThat(commits).containsExactly("command.applyProfile");
        assertThat(form.profileSummary().getText()).isEqualTo("Igual al perfil");
    }

    @Test
    void backToTheProfileSetsEveryDifferentValueInOneCommit() {
        ParameterForm form = form(Map.of("history", 50, "glow", false, "rows", 30));
        List<String> commits = recordCommits(form);

        form.restoreProfileButton().doClick();

        assertThat(form.rawValues()).containsEntry("history", 0).containsEntry("glow", true)
                .containsEntry("rows", 30);
        assertThat(commits).containsExactly("command.applyProfile");
    }

    @Test
    void aMultilineTextIsAnAreaInAScrollPaneThatCommitsOnFocusLossAndCtrlEnter() {
        ParameterForm form = form(Map.of("expected", "hola\nmundo"));
        List<String> commits = recordCommits(form);
        JTextArea area = (JTextArea) form.editor("expected");

        assertThat(area.getRows()).isEqualTo(3);
        assertThat(SwingUtilities.getAncestorOfClass(JScrollPane.class, area)).isNotNull();
        assertThat(area.getText()).isEqualTo("hola\nmundo");

        area.setText("=120\n");
        assertThat(form.rawValues()).containsEntry("expected", "=120\n");
        loseFocus(area);
        assertThat(commits).hasSize(1);

        press(area, new KeyEvent(area, KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_ENTER,
                KeyEvent.CHAR_UNDEFINED));
        assertThat(commits).hasSize(2);
        press(area, new KeyEvent(area, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n'));
        assertThat(commits).as("a plain Enter is a new line").hasSize(2);
    }

    /** Headless, nothing has the focus: the listeners hear what the focus manager would hand them. */
    private static void loseFocus(Component component) {
        FocusEvent event = new FocusEvent(component, FocusEvent.FOCUS_LOST);
        for (FocusListener listener : component.getFocusListeners()) {
            listener.focusLost(event);
        }
    }

    private static void press(Component component, KeyEvent event) {
        for (KeyListener listener : component.getKeyListeners()) {
            listener.keyPressed(event);
        }
    }

    @Test
    void aConditionalRowShowsOnlyWhileItsConditionHolds() {
        ParameterForm form = form(Map.of());

        assertThat(form.rowShown("encoding")).isFalse();
        ((JSpinner) form.editor("width")).setValue(8);
        assertThat(form.rowShown("encoding")).isTrue();
        ((JSpinner) form.editor("width")).setValue(7);
        assertThat(form.rowShown("encoding")).isFalse();
        assertThat(form.rawValues()).as("a hidden row keeps its value").containsEntry("encoding", "utf8");
    }

    @Test
    void aConditionalRowFollowsAProfileThatChangesItsCondition() {
        ParameterForm form = form(Map.of());

        ((JComboBox<?>) form.editor(PROFILE)).setSelectedItem("modern");

        assertThat(form.rowShown("encoding")).isTrue();
    }
}
