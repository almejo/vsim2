package cl.almejo.vsim.ui.lens;

import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.RecordingKeyDispatchers;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.toolbar.SplitButton;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToolBar;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The lens' toolbar button: its place, its ▾ zoom choice shared by the views and saved, and its tooltip. */
class LensToolbarTest {

    private final List<KeyEventDispatcher> dispatchers = new ArrayList<>();
    private AppPreferences preferences;
    private EditorFixture fixture;
    private EditorView view;
    private LensFeatures features;

    @BeforeEach
    void create() {
        preferences = AppPreferences.inMemory();
        fixture = EditorFixture.create(preferences);
        view = fixture.view();
        features = install(view);
    }

    private LensFeatures install(EditorView target) {
        return new LensFeatures(target, new RecordingKeyDispatchers(dispatchers), (watched, whenDeactivated) -> () -> {
        });
    }

    @AfterEach
    void close() {
        features.dispose();
        fixture.close();
    }

    private static List<Component> toolbarOf(EditorView target) {
        return List.of(((JToolBar) findToolBar(target)).getComponents());
    }

    private static Component findToolBar(EditorView target) {
        return target.toolBar();
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

    @Test
    void theButtonSitsRightAfterTheEyeOfLookInside() {
        EditorFixture other = EditorFixture.create(AppPreferences.inMemory());
        javax.swing.Action eye = new javax.swing.AbstractAction("eye") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
            }
        };
        other.view().addToolbarToggleAfterTools(new javax.swing.JToggleButton(eye));
        other.view().registerShortcutAction("view.magnifier", eye);
        LensFeatures installed = install(other.view());
        try {
            List<Component> components = toolbarOf(other.view());
            int eyeIndex = -1;
            for (int index = 0; index < components.size(); index++) {
                if (components.get(index) instanceof AbstractButton button && button.getAction() == eye) {
                    eyeIndex = index;
                }
            }
            assertThat(eyeIndex).isGreaterThan(0);
            assertThat(components.get(eyeIndex + 1)).isSameAs(installed.splitButton());
        } finally {
            installed.dispose();
            other.close();
        }
    }

    @Test
    void withoutTheEyeTheButtonStillHasAPlaceInTheToolbar() {
        assertThat(toolbarOf(view)).contains(features.splitButton());
    }

    @Test
    void theMainPartIsTheFixedToggleAndThreeTimesIsTheDefault() {
        SplitButton button = features.splitButton();
        assertThat(button.mainButton().getAction()).isSameAs(features.fixedAction());
        assertThat(button.mode().id()).isEqualTo("3");
        assertThat(features.state().factor()).isEqualTo(3.0);
        assertThat(radioItems(button.popupMenu())).extracting(AbstractButton::getText)
                .containsExactly("2×", "3×", "4×");
    }

    @Test
    void choosingFourTimesChangesTheFactorOfEveryViewAndSurvivesANewView() {
        EditorFixture otherFixture = EditorFixture.create(preferences);
        LensFeatures other = install(otherFixture.view());
        try {
            radioItems(features.splitButton().popupMenu()).get(2).doClick();
            assertThat(features.state().factor()).isEqualTo(4.0);
            assertThat(other.state().factor()).as("the other view follows").isEqualTo(4.0);
            assertThat(other.splitButton().mode().id()).isEqualTo("4");
            assertThat(features.state().isFixed()).as("choosing does not press it").isFalse();
        } finally {
            other.dispose();
            otherFixture.close();
        }
        EditorFixture later = EditorFixture.create(preferences);
        LensFeatures fresh = install(later.view());
        try {
            assertThat(fresh.state().factor()).isEqualTo(4.0);
        } finally {
            fresh.dispose();
            later.close();
        }
    }

    @Test
    void theFixedStateIsNotSavedButTheZoomIs() {
        features.fixedAction().actionPerformed(null);
        radioItems(features.splitButton().popupMenu()).get(0).doClick();
        features.dispose();
        features = install(view);
        assertThat(features.state().isFixed()).isFalse();
        assertThat(features.state().factor()).isEqualTo(2.0);
    }

    @Test
    void pressingTheMainPartKeepsTheLensOnAndOffAndTheMenuItemFollows() {
        features.splitButton().mainButton().doClick();
        assertThat(features.state().isFixed()).isTrue();
        assertThat(features.splitButton().mainButton().isSelected()).isTrue();
        features.splitButton().mainButton().doClick();
        assertThat(features.state().isFixed()).isFalse();
    }

    @Test
    void theTooltipNamesTheBoundKeyAndFollowsARebinding() {
        javax.swing.KeyStroke initial = new KeyBindings(preferences).strokeFor("view.lens").orElseThrow();
        assertThat(features.splitButton().mainButton().getToolTipText())
                .contains("Lupa fija: agranda el trocito bajo el puntero")
                .contains("(o mantén " + KeyNames.describe(initial) + ")");
        new KeyBindings(preferences).assign("view.lens",
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F9, 0));
        assertThat(features.splitButton().mainButton().getToolTipText()).contains("F9").doesNotContain("Mayús");
    }

    @Test
    void disposeTakesTheButtonBack() {
        SplitButton button = features.splitButton();
        features.dispose();
        assertThat(toolbarOf(view)).doesNotContain(button);
        features = install(view);
    }
}
