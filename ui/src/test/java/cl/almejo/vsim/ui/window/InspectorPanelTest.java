package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.actions.KeyboardFocus;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The right panel as a strip of section icons on the window's edge, a header and one section at a time (option A of
 * the 2026-11-06 mockup): the strip stays when the panel closes, the active icon closes it, and the width the user
 * dragged comes back on reopening.
 */
class InspectorPanelTest {

    private final List<Component> focusRequests = new ArrayList<>();
    private Optional<Component> focusOwner = Optional.empty();
    private final KeyboardFocus recordingFocus = new KeyboardFocus() {
        @Override
        public Optional<Component> owner() {
            return focusOwner;
        }

        @Override
        public void request(Component target) {
            focusRequests.add(target);
            focusOwner = Optional.of(target);
        }
    };
    private final JLabel canvas = new JLabel("lienzo");
    private final JTextField propertiesField = new JTextField();
    private InspectorPanel inspector;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        inspector = new InspectorPanel(recordingFocus, () -> recordingFocus.request(canvas));
        inspector.addTab(InspectorPanel.PROPERTIES, "Propiedades", InspectorPanel.PROPERTIES_ICON, propertiesField);
        inspector.addTab(InspectorPanel.PROBLEMS, "Problemas", InspectorPanel.PROBLEMS_ICON, new JLabel("p"));
        inspector.addTab(InspectorPanel.NOTES, "Notas", InspectorPanel.NOTES_ICON, new JLabel("n"));
        inspector.addTab(InspectorPanel.MEMORY_MAP, "Mapa de memoria", InspectorPanel.MEMORY_MAP_ICON,
                new JLabel("m"));
    }

    @Test
    void theStripHasOneButtonPerSectionInTheOrderTheyWereAdded() {
        assertThat(inspector.strip()).isInstanceOf(InspectorStrip.class);
        InspectorStrip strip = (InspectorStrip) inspector.strip();

        assertThat(strip.sectionIds()).containsExactly("properties", "problems", "notes", "memoryMap");
        assertThat(strip.tooltip("notes")).isEqualTo("Notas");
        assertThat(inspector.isOpen()).isTrue();
        assertThat(inspector.selectedTab()).contains("properties");
        assertThat(strip.highlighted()).contains("properties");
        assertThat(inspector.headerTitle()).isEqualTo("Propiedades");
    }

    @Test
    void aSecondSectionWithTheSameIdIsRefused() {
        assertThatThrownBy(() -> inspector.addTab("notes", "Otra", InspectorPanel.NOTES_ICON, new JLabel()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inspector.showTab("nada")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clickingAnotherIconSwitchesTheSection() {
        InspectorStrip strip = (InspectorStrip) inspector.strip();

        strip.click("problems");

        assertThat(inspector.isOpen()).isTrue();
        assertThat(inspector.selectedTab()).contains("problems");
        assertThat(strip.highlighted()).contains("problems");
        assertThat(inspector.headerTitle()).isEqualTo("Problemas");
    }

    @Test
    void clickingTheActiveIconClosesThePanelAndTheStripStays() {
        InspectorStrip strip = (InspectorStrip) inspector.strip();

        strip.click("properties");

        assertThat(inspector.isOpen()).isFalse();
        assertThat(inspector.isVisible()).isFalse();
        assertThat(strip.isVisible()).isTrue();
        assertThat(strip.highlighted()).isEmpty();
        assertThat(focusRequests).containsExactly(canvas);
        assertThat(inspector.selectedTab()).as("closing keeps the section").contains("properties");
    }

    @Test
    void theOpenCloseButtonAtTheBottomClosesAndReopensOnTheSameSection() {
        InspectorStrip strip = (InspectorStrip) inspector.strip();
        strip.click("notes");

        strip.clickOpenClose();
        assertThat(inspector.isOpen()).isFalse();
        strip.clickOpenClose();

        assertThat(inspector.isOpen()).isTrue();
        assertThat(inspector.selectedTab()).contains("notes");
    }

    @Test
    void toggleTabOpensAClosedPanelOnThatSectionAndFocusesIt() {
        inspector.close();
        focusRequests.clear();

        inspector.toggleTab("properties");

        assertThat(inspector.isOpen()).isTrue();
        assertThat(inspector.selectedTab()).contains("properties");
        assertThat(focusRequests).containsExactly(propertiesField);
    }

    @Test
    void toggleTabOnTheOpenSectionClosesAndGivesTheFocusToTheCanvas() {
        inspector.toggleTab("properties");

        assertThat(inspector.isOpen()).isFalse();
        assertThat(focusRequests).containsExactly(canvas);
    }

    @Test
    void toggleTabOnAnotherSectionSwitchesWithoutClosing() {
        inspector.toggleTab("memoryMap");

        assertThat(inspector.isOpen()).isTrue();
        assertThat(inspector.selectedTab()).contains("memoryMap");
    }

    @Test
    void theHeadersCloseButtonClosesThePanel() {
        inspector.clickHeaderClose();

        assertThat(inspector.isOpen()).isFalse();
        assertThat(focusRequests).containsExactly(canvas);
    }

    @Test
    void aBadgeShowsItsCountAndKindAndZeroHidesIt() {
        inspector.setBadge("problems", 2, InspectorPanel.Badge.ERROR);
        assertThat(inspector.badge("problems")).contains(new InspectorPanel.ShownBadge(2,
                InspectorPanel.Badge.ERROR));

        inspector.setBadge("problems", 0, InspectorPanel.Badge.ERROR);

        assertThat(inspector.badge("problems")).isEmpty();
        assertThatThrownBy(() -> inspector.setBadge("problems", -1, InspectorPanel.Badge.ERROR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBadgeStaysWhileThePanelIsClosed() {
        inspector.close();

        inspector.setBadge("problems", 3, InspectorPanel.Badge.WARNING);

        assertThat(inspector.badge("problems")).contains(new InspectorPanel.ShownBadge(3,
                InspectorPanel.Badge.WARNING));
    }

    @Test
    void aStateListenerCanBeRemoved() {
        List<InspectorState> heard = new ArrayList<>();
        Runnable remover = inspector.onStateChange(() -> heard.add(inspector.state()));

        remover.run();
        inspector.restore(new InspectorState(false, "notes", 410));

        assertThat(heard).isEmpty();
    }

    @Test
    void theStateCanBeReadRestoredAndIsHeard() {
        List<InspectorState> heard = new ArrayList<>();
        inspector.onStateChange(() -> heard.add(inspector.state()));

        inspector.restore(new InspectorState(false, "notes", 410));

        assertThat(inspector.state()).isEqualTo(new InspectorState(false, "notes", 410));
        assertThat(inspector.isOpen()).isFalse();
        assertThat(inspector.selectedTab()).contains("notes");
        assertThat(heard).containsExactly(new InspectorState(false, "notes", 410));
        assertThat(focusRequests).as("restoring moves no focus").isEmpty();
    }

    @Test
    void aRestoredSectionThatIsNotThereYetIsShownWhenItArrives() {
        InspectorPanel fresh = new InspectorPanel(recordingFocus, () -> recordingFocus.request(canvas));
        fresh.restore(new InspectorState(true, "notes", 300));
        fresh.addTab("properties", "Propiedades", InspectorPanel.PROPERTIES_ICON, new JLabel());

        assertThat(fresh.selectedTab()).contains("properties");
        fresh.addTab("notes", "Notas", InspectorPanel.NOTES_ICON, new JLabel());

        assertThat(fresh.selectedTab()).contains("notes");
        assertThat(fresh.state().selectedTab()).isEqualTo("notes");
    }

    @Test
    void theInitialStateIsOpenOnPropertiesAtThePreferredWidth() {
        assertThat(InspectorState.initial()).isEqualTo(new InspectorState(true, "properties",
                InspectorPanel.PREFERRED_WIDTH));
    }
}
