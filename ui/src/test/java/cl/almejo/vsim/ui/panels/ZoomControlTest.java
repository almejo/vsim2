package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.ReadableColors;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The zoom control of the status bar (zoom spec §6): − · slider · + · percentage · 100 % · Ver todo · selección. */
class ZoomControlTest {

    /** Stands in for the window's active pane: records what the control asks and tells the control back. */
    private final class FakePane implements ZoomControl.Target {
        double zoom = 1.0;
        final List<String> calls = new ArrayList<>();

        @Override
        public double zoom() {
            return zoom;
        }

        @Override
        public void zoomAroundCenter(double newZoom) {
            calls.add("instant " + newZoom);
            zoom = newZoom;
            control.showZoom(newZoom); // as the canvas's view listener does
        }

        @Override
        public void zoomTo(double newZoom) {
            calls.add("travel " + newZoom);
            zoom = newZoom;
            control.showZoom(newZoom);
        }

        @Override
        public void returnFocus() {
            calls.add("focus");
        }
    }

    private static final class CountingAction extends AbstractAction {
        int runs;

        CountingAction(String name) {
            super(name);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            runs++;
        }
    }

    private final FakePane pane = new FakePane();
    private final CountingAction zoomOut = new CountingAction("Alejar");
    private final CountingAction zoomIn = new CountingAction("Acercar");
    private final CountingAction zoomReset = new CountingAction("Zoom 100 %");
    private final CountingAction zoomToFit = new CountingAction("Ver todo el circuito");
    private final CountingAction zoomToSelection = new CountingAction("Zoom a la selección");
    private ZoomControl control;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        zoomToFit.putValue(Action.SHORT_DESCRIPTION, "Ver todo el circuito");
        control = new ZoomControl(pane,
                new ZoomControl.Actions(zoomOut, zoomIn, zoomReset, zoomToFit, zoomToSelection));
    }

    private JTextField editor() {
        return (JTextField) control.field().getEditor().getEditorComponent();
    }

    private void type(String text) {
        editor().setText(text);
        editor().postActionEvent(); // Enter
    }

    @Test
    void showingAZoomMovesTheSliderAndTheFieldWithoutZoomingThePane() {
        control.showZoom(1.56);

        assertThat(control.slider().getValue()).isEqualTo(ZoomScale.sliderValue(1.56));
        assertThat(control.text()).isEqualTo("156 %");
        assertThat(control.field().getSelectedItem()).isEqualTo("156 %");
        assertThat(pane.calls).as("no feedback loop").isEmpty();
    }

    @Test
    void movingTheSliderZoomsThePaneAroundItsCentreAtOnce() {
        control.slider().setValue(100);

        assertThat(pane.calls).containsExactly("instant 2.0");
        assertThat(control.text()).isEqualTo("200 %");
        assertThat(control.slider().getValue()).isEqualTo(100);
    }

    @Test
    void nearOneHundredPercentTheSliderSnapsToIt() {
        control.showZoom(2.0);

        control.slider().setValue(3);

        assertThat(pane.zoom).isEqualTo(1.0);
        assertThat(control.slider().getValue()).as("the thumb sits on the detent").isZero();
        assertThat(control.text()).isEqualTo("100 %");
    }

    @Test
    void whileTheUserDragsTheThumbTheCanvasDoesNotMoveIt() {
        control.slider().setValueIsAdjusting(true);
        control.slider().setValue(60);
        int dragged = control.slider().getValue();

        control.showZoom(1.25); // a canvas notification arriving mid-drag

        assertThat(control.slider().getValue()).isEqualTo(dragged);
        assertThat(control.text()).isEqualTo("125 %");
    }

    @Test
    void typingAPercentageAndEnterTravelsThereAndGivesTheFocusBackToThePane() {
        type("125");

        assertThat(pane.calls).containsExactly("travel 1.25", "focus");
        assertThat(control.text()).isEqualTo("125 %");
        assertThat(control.slider().getValue()).isEqualTo(ZoomScale.sliderValue(1.25));
        assertThat(control.field().getClientProperty("JComponent.outline")).isNull();
    }

    @Test
    void typingTooMuchIsClampedAndShownClamped() {
        type("1000");

        assertThat(pane.calls).containsExactly("travel 4.0", "focus");
        assertThat(control.text()).isEqualTo("400 %");
    }

    @Test
    void textThatIsNotANumberMarksAnErrorKeepsTheViewAndKeepsTheFocus() {
        control.showZoom(1.5);

        type("abc");

        assertThat(pane.calls).isEmpty();
        assertThat(pane.zoom).isEqualTo(1.0);
        assertThat(control.field().getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(control.field().getToolTipText()).isEqualTo("Escribe un porcentaje entre 25 y 400");
        assertThat(editor().getText()).isEqualTo("abc");

        type("150");

        assertThat(control.field().getClientProperty("JComponent.outline")).isNull();
        assertThat(control.field().getToolTipText()).isEqualTo("Escribe un porcentaje y Enter, o elige uno");
        assertThat(pane.calls).containsExactly("focus"); // already at 150 %: nothing to travel
    }

    @Test
    void escapeRestoresTheCurrentZoomAndGivesTheFocusBack() {
        control.showZoom(2.0);
        pane.zoom = 2.0;
        type("abc");
        editor().setText("3");

        Object name = editor().getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        assertThat(name).isNotNull();
        editor().getActionMap().get(name).actionPerformed(new ActionEvent(editor(), ActionEvent.ACTION_PERFORMED, ""));

        assertThat(editor().getText()).isEqualTo("200 %");
        assertThat(control.field().getClientProperty("JComponent.outline")).isNull();
        assertThat(pane.calls).containsExactly("focus");
    }

    @Test
    void whileTheUserTypesTheCanvasDoesNotOverwriteTheField() {
        control.showZoom(1.0);
        editor().setText("17");

        control.showZoom(1.25);

        assertThat(editor().getText()).isEqualTo("17");
        assertThat(control.slider().getValue()).isEqualTo(ZoomScale.sliderValue(1.25));
    }

    @Test
    void thePresetsAreListedAndPickingOneTravelsThere() {
        assertThat(control.field().isEditable()).isTrue();
        List<String> items = new ArrayList<>();
        for (int index = 0; index < control.field().getItemCount(); index++) {
            items.add(control.field().getItemAt(index));
        }
        assertThat(items).containsExactly("25 %", "50 %", "75 %", "100 %", "150 %", "200 %", "300 %", "400 %");

        control.field().setSelectedItem("50 %");

        assertThat(pane.calls).as("the focus stays where it was").containsExactly("travel 0.5");
        assertThat(control.text()).isEqualTo("50 %");
    }

    /** Review I1: a preset picked with the mouse travels once and the keyboard goes back to the canvas. */
    @Test
    void aPresetPickedFromTheOpenListTravelsWhenTheListClosesAndGivesTheFocusBack() {
        control.field().firePopupMenuWillBecomeVisible();

        control.field().setSelectedItem("300 %");
        assertThat(pane.calls).as("nothing while the list is open").isEmpty();
        control.field().firePopupMenuWillBecomeInvisible();

        assertThat(pane.calls).containsExactly("travel 3.0", "focus");
        assertThat(control.text()).isEqualTo("300 %");
    }

    /** Review I1 (known T4d item): the arrows only move through the open list; one travel at the end. */
    @Test
    void movingThroughTheOpenListWithTheArrowsDoesNotZoomOnEveryPress() {
        control.field().firePopupMenuWillBecomeVisible();

        control.field().setSelectedItem("150 %");
        control.field().setSelectedItem("200 %");
        control.field().setSelectedItem("300 %");
        assertThat(pane.calls).isEmpty();
        type(editor().getText()); // Enter on the preset reached

        assertThat(pane.calls).containsExactly("travel 3.0", "focus");
        control.field().firePopupMenuWillBecomeInvisible(); // Enter closes the list: nothing more
        assertThat(pane.calls).containsExactly("travel 3.0", "focus");
    }

    /** The user's check of the fix round: after using the field, a click on the slider or a button gives the keyboard back. */
    @Test
    void aClickOnTheSliderOrAButtonGivesTheKeyboardBackToThePane() {
        for (javax.swing.JComponent clickable : List.of(control.slider(), control.zoomOutButton(),
                control.zoomInButton(), control.resetButton(), control.fitButton(), control.selectionButton())) {
            pane.calls.clear();
            clickable.dispatchEvent(new java.awt.event.MouseEvent(clickable, java.awt.event.MouseEvent.MOUSE_PRESSED,
                    0L, 0, 2, 2, 1, false, java.awt.event.MouseEvent.BUTTON1));

            assertThat(pane.calls).as(clickable.getAccessibleContext().getAccessibleName()).contains("focus");
        }
    }

    @Test
    void closingTheListWithoutAPickLeavesTheZoomAndShowsItAgain() {
        control.field().firePopupMenuWillBecomeVisible();
        control.field().setSelectedItem("200 %");

        control.field().firePopupMenuCanceled();
        control.field().firePopupMenuWillBecomeInvisible();

        assertThat(pane.calls).isEmpty();
        assertThat(control.text()).isEqualTo("100 %");
    }

    @Test
    void escapeInTheOpenListClosesItWithoutZooming() {
        control.field().firePopupMenuWillBecomeVisible();
        control.field().setSelectedItem("200 %");

        Object name = editor().getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        editor().getActionMap().get(name).actionPerformed(new ActionEvent(editor(), ActionEvent.ACTION_PERFORMED, ""));
        control.field().firePopupMenuWillBecomeInvisible();

        assertThat(pane.calls).isEmpty();
        assertThat(control.text()).isEqualTo("100 %");
    }

    /** Review I1: dragging or clicking the slider leaves the keyboard on the canvas; Tab still reaches it. */
    @Test
    void aMousePressOnTheSliderDoesNotTakeTheKeyboardFromTheCanvas() {
        assertThat(control.slider().isRequestFocusEnabled()).isFalse();
        assertThat(control.slider().isFocusable()).isTrue();
    }

    @Test
    void theHundredPercentMarkStandsOutOnThePanelInTheLightAndTheDarkTheme() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel lookAndFeel : List.of(new FlatLightLaf(), new FlatDarkLaf())) {
                UIManager.setLookAndFeel(lookAndFeel);
                assertThat(ReadableColors.contrast(ZoomControl.centreMarkColor(), ReadableColors.panelBackground()))
                        .as(lookAndFeel.getName())
                        .isGreaterThanOrEqualTo(ZoomControl.MINIMUM_MARK_CONTRAST);
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void theArrowKeysStepThePageKeysDoubleAndHomeAndEndGoToTheLimits() {
        runSliderKey(KeyEvent.VK_RIGHT);
        assertThat(pane.zoom).isCloseTo(1.25, within(1e-12));
        runSliderKey(KeyEvent.VK_LEFT);
        assertThat(pane.zoom).isCloseTo(1.0, within(1e-12));
        runSliderKey(KeyEvent.VK_DOWN);
        assertThat(pane.zoom).isCloseTo(0.8, within(1e-12));
        runSliderKey(KeyEvent.VK_UP);
        assertThat(pane.zoom).isCloseTo(1.0, within(1e-12));
        runSliderKey(KeyEvent.VK_PAGE_UP);
        assertThat(pane.zoom).isCloseTo(2.0, within(1e-12));
        runSliderKey(KeyEvent.VK_PAGE_DOWN);
        assertThat(pane.zoom).isCloseTo(1.0, within(1e-12));
        runSliderKey(KeyEvent.VK_END);
        assertThat(pane.zoom).isEqualTo(4.0);
        runSliderKey(KeyEvent.VK_PAGE_UP);
        assertThat(pane.zoom).as("clamped").isEqualTo(4.0);
        runSliderKey(KeyEvent.VK_HOME);
        assertThat(pane.zoom).isEqualTo(0.25);
        assertThat(control.slider().getValue()).isEqualTo(-200);
        assertThat(pane.calls).allSatisfy(call -> assertThat(call).startsWith("instant"));
    }

    private void runSliderKey(int keyCode) {
        Object name = control.slider().getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(keyCode, 0));
        assertThat(name).as("binding of " + KeyEvent.getKeyText(keyCode)).isNotNull();
        control.slider().getActionMap().get(name)
                .actionPerformed(new ActionEvent(control.slider(), ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void theWheelOverTheSliderStepsOncePerNotch() {
        MouseWheelEvent up = new MouseWheelEvent(control.slider(), MouseWheelEvent.MOUSE_WHEEL, 0, 0, 5, 5, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1);
        for (MouseWheelListener listener : control.slider().getMouseWheelListeners()) {
            listener.mouseWheelMoved(up);
        }
        assertThat(pane.zoom).isCloseTo(1.25, within(1e-12));
    }

    @Test
    void theButtonsRunTheirActionsAndTheSelectionOneFollowsItsAction() {
        control.zoomOutButton().doClick();
        control.zoomInButton().doClick();
        control.resetButton().doClick();
        control.fitButton().doClick();
        zoomToSelection.setEnabled(false);
        assertThat(control.selectionButton().isEnabled()).isFalse();
        zoomToSelection.setEnabled(true);
        assertThat(control.selectionButton().isEnabled()).isTrue();
        control.selectionButton().doClick();

        assertThat(List.of(zoomOut.runs, zoomIn.runs, zoomReset.runs, zoomToFit.runs, zoomToSelection.runs))
                .containsOnly(1);
        assertThat(control.resetButton().getText()).isEqualTo("100 %");
        assertThat(control.resetButton().getToolTipText()).isEqualTo("Volver a 100 %");
        assertThat(control.fitButton().getToolTipText()).isEqualTo("Ver todo el circuito");
        assertThat(control.zoomOutButton().getToolTipText()).isEqualTo("Alejar");
    }

    @Test
    void everyControlIsNamedAndOnlyTheSliderAndTheFieldTakeTheFocus() {
        assertThat(control.slider().getAccessibleContext().getAccessibleName()).isEqualTo("Zoom");
        assertThat(control.slider().getToolTipText()).isEqualTo("Zoom: de 25 % a 400 %");
        assertThat(control.field().getAccessibleContext().getAccessibleName()).isEqualTo("Porcentaje de zoom");
        assertThat(editor().getAccessibleContext().getAccessibleName()).isEqualTo("Porcentaje de zoom");
        for (Component button : List.of(control.zoomOutButton(), control.zoomInButton(), control.resetButton(),
                control.fitButton(), control.selectionButton())) {
            assertThat(button.isFocusable()).isFalse();
            assertThat(button.getAccessibleContext().getAccessibleName()).isNotBlank();
        }
        assertThat(control.slider().isFocusable()).isTrue();
        assertThat(List.of(control.getComponents())).containsExactly(control.zoomOutButton(), control.slider(),
                control.zoomInButton(), control.field(), control.resetButton(), control.fitButton(),
                control.selectionButton());
    }

    @Test
    void escribirElZoomSelectsTheWholePercentage() {
        control.showZoom(1.5);
        Action typeZoom = control.typeZoomAction();
        assertThat(typeZoom.getValue(Action.NAME)).isEqualTo("Escribir el zoom…");

        typeZoom.actionPerformed(new ActionEvent(control, ActionEvent.ACTION_PERFORMED, ""));

        assertThat(editor().getSelectedText()).isEqualTo("150 %");
    }
}
