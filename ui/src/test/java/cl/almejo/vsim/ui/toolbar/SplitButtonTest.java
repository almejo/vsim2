package cl.almejo.vsim.ui.toolbar;

import cl.almejo.vsim.ui.app.AppPreferences;
import org.junit.jupiter.api.Test;

import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The split button: the remembered mode, the main press, the ▾ menu model and the tool variant. */
class SplitButtonTest {

    private static final Icon WINDOW_ICON = UIManager.getIcon("FileView.fileIcon");
    private static final Icon CIRCUIT_ICON = UIManager.getIcon("FileView.directoryIcon");
    private static final List<SplitButtonMode> MODES = List.of(
            new SplitButtonMode("window", WINDOW_ICON, "Window", "Copy the window"),
            new SplitButtonMode("circuit", CIRCUIT_ICON, "Circuit", "Copy the circuit"));

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> pressedModes = new ArrayList<>();

    private SplitButton action(String preferenceId) {
        return SplitButton.forAction(preferenceId, "Capture", MODES, preferences,
                mode -> pressedModes.add(mode.id()));
    }

    @Test
    void startsWithTheFirstModeWhenNothingIsStored() {
        assertThat(action("shot").mode().id()).isEqualTo("window");
    }

    @Test
    void startsWithTheFirstModeWhenTheStoredIdIsUnknown() {
        preferences.setSplitButtonMode("shot", "gone");
        assertThat(action("shot").mode().id()).isEqualTo("window");
    }

    @Test
    void theModeIsStoredAndRestoredPerPreferenceId() {
        action("shot").setMode("circuit");
        assertThat(preferences.splitButtonMode("shot")).contains("circuit");
        assertThat(action("shot").mode().id()).isEqualTo("circuit");
        assertThat(action("other").mode().id()).isEqualTo("window");
    }

    @Test
    void settingAnUnknownModeIsRefused() {
        assertThatThrownBy(() -> action("shot").setMode("nope")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pressingRunsTheActionWithTheCurrentMode() {
        SplitButton button = action("shot");
        button.mainButton().doClick();
        button.setMode("circuit");
        button.mainButton().doClick();
        assertThat(pressedModes).containsExactly("window", "circuit");
    }

    @Test
    void choosingAModeShowsItsIconAndTooltipAndTellsTheListeners() {
        SplitButton button = action("shot");
        assertThat(button.mainButton().getIcon()).isSameAs(WINDOW_ICON);
        assertThat(button.mainButton().getToolTipText()).isEqualTo("Copy the window");
        List<String> heard = new ArrayList<>();
        Runnable remove = button.addModeListener(mode -> heard.add(mode.id()));
        button.setMode("circuit");
        assertThat(button.mainButton().getIcon()).isSameAs(CIRCUIT_ICON);
        assertThat(button.mainButton().getToolTipText()).isEqualTo("Copy the circuit");
        remove.run();
        button.setMode("window");
        assertThat(heard).containsExactly("circuit");
    }

    @Test
    void choosingAModeInTheMenuOfAnActionButtonDoesNotPress() {
        SplitButton button = action("shot");
        radioItems(button.popupMenu()).get(1).doClick();
        assertThat(button.mode().id()).isEqualTo("circuit");
        assertThat(pressedModes).isEmpty();
    }

    @Test
    void theMenuHasACaptionAndOneRadioItemPerModeWithTheCurrentOneSelected() {
        SplitButton button = action("shot");
        button.setMode("circuit");
        JPopupMenu menu = button.popupMenu();
        assertThat(menu.getComponent(0)).hasFieldOrPropertyWithValue("text", "Capture");
        List<JRadioButtonMenuItem> items = radioItems(menu);
        assertThat(items).extracting(JRadioButtonMenuItem::getText).containsExactly("Window", "Circuit");
        assertThat(items).extracting(JRadioButtonMenuItem::getIcon).containsExactly(WINDOW_ICON, CIRCUIT_ICON);
        assertThat(items).extracting(JRadioButtonMenuItem::isSelected).containsExactly(false, true);
    }

    @Test
    void aNullTitleLeavesTheMenuWithoutCaption() {
        SplitButton button = SplitButton.forAction("shot", null, MODES, preferences, mode -> { });
        assertThat(button.popupMenu().getComponentCount()).isEqualTo(MODES.size());
    }

    @Test
    void theArrowIsFocusableAndOpensWithAltDown() {
        SplitButton button = action("shot");
        assertThat(button.arrowButton().isFocusable()).isTrue();
        assertThat(button.arrowButton().getInputMap(JComponent.WHEN_FOCUSED)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.ALT_DOWN_MASK))).isNotNull();
    }

    @Test
    void theToolVariantJoinsAButtonGroupAndRunsOnPressAndOnChoosingAMode() {
        SplitButton button = SplitButton.forTool("cable", "Cable", MODES, preferences,
                mode -> pressedModes.add(mode.id()));
        assertThat(button.mainButton()).isInstanceOf(JToggleButton.class);
        JToggleButton other = new JToggleButton();
        ButtonGroup group = new ButtonGroup();
        group.add(other);
        group.add(button.mainButton());
        button.mainButton().doClick();
        assertThat(button.mainButton().isSelected()).isTrue();
        other.doClick();
        assertThat(button.mainButton().isSelected()).as("selection follows the group").isFalse();
        radioItems(button.popupMenu()).get(1).doClick();
        assertThat(pressedModes).containsExactly("window", "circuit");
    }

    @Test
    void theActionVariantIsAPlainButton() {
        assertThat(action("shot").mainButton()).isNotInstanceOf(JToggleButton.class);
    }

    @Test
    void theToggleVariantIsAToggleOnTheActionAndChoosingAModeDoesNotPressIt() {
        java.util.concurrent.atomic.AtomicBoolean on = new java.util.concurrent.atomic.AtomicBoolean();
        javax.swing.Action toggle = new javax.swing.AbstractAction("Fixed") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                on.set(!on.get());
                putValue(SELECTED_KEY, on.get());
                pressedModes.add("toggled");
            }
        };
        toggle.putValue(javax.swing.Action.SELECTED_KEY, false);
        SplitButton button = SplitButton.forToggle("zoom", "Zoom", MODES, preferences, toggle);
        assertThat(button.mainButton()).isInstanceOf(JToggleButton.class);
        assertThat(button.mainButton().getAction()).isSameAs(toggle);
        radioItems(button.popupMenu()).get(1).doClick();
        assertThat(button.mode().id()).isEqualTo("circuit");
        assertThat(pressedModes).as("the mode choice did not toggle").isEmpty();
        assertThat(button.mainButton().isSelected()).isFalse();
        button.mainButton().doClick();
        assertThat(pressedModes).containsExactly("toggled");
        assertThat(button.mainButton().isSelected()).as("state follows the action").isTrue();
        toggle.putValue(javax.swing.Action.SELECTED_KEY, false);
        assertThat(button.mainButton().isSelected()).isFalse();
    }

    @Test
    void theToggleVariantKeepsTheActionsTooltipAndAnExplicitDefaultModeNeedNotBeTheFirst() {
        javax.swing.Action toggle = new javax.swing.AbstractAction("Fixed") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
            }
        };
        toggle.putValue(javax.swing.Action.SHORT_DESCRIPTION, "the action's tip");
        SplitButton button = SplitButton.forToggle("zoom", null, MODES, preferences, toggle, "circuit");
        assertThat(button.mode().id()).isEqualTo("circuit");
        assertThat(button.mainButton().getToolTipText()).isEqualTo("the action's tip");
        preferences.setSplitButtonMode("zoom", "gone");
        assertThat(SplitButton.forToggle("zoom", null, MODES, preferences, toggle, "circuit").mode().id())
                .as("an unknown stored id falls back to the default").isEqualTo("circuit");
    }

    private static List<JRadioButtonMenuItem> radioItems(JPopupMenu menu) {
        List<JRadioButtonMenuItem> items = new ArrayList<>();
        for (Component component : menu.getComponents()) {
            if (component instanceof JRadioButtonMenuItem item) {
                items.add(item);
            }
        }
        return items;
    }
}
