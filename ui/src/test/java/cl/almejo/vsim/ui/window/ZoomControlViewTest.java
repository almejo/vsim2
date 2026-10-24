package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.ZoomControl;
import cl.almejo.vsim.ui.panels.ZoomScale;
import cl.almejo.vsim.ui.shortcuts.Scope;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JTextField;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The status-bar zoom control in a window (zoom spec §6, §7): it shows and drives the active pane. */
class ZoomControlViewTest {

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, new WindowCommands() {
            @Override public void newDocument() { }
            @Override public void open() { }
            @Override public void save() { }
            @Override public void saveAs() { }
            @Override public void closeWindow() { }
            @Override public void exit() { }
        });
        view.setSize(1200, 800);
        view.doLayout();
        view.canvasPanes().setSize(800, 600);
        view.canvasPanes().doLayout();
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private ZoomControl control() {
        return view.zoomControl();
    }

    private static void press(CircuitCanvas canvas) {
        MouseEvent event = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, 20, 20, 1, false,
                MouseEvent.BUTTON1);
        for (var listener : canvas.getMouseListeners()) {
            listener.mousePressed(event);
        }
    }

    @Test
    void theControlIsInTheStatusBarAndFollowsTheZoom() {
        assertThat(view.statusBar().isAncestorOf(control())).isTrue();
        assertThat(control().text()).isEqualTo("100 %");

        view.actions().zoomIn().actionPerformed(null);

        assertThat(control().text()).isEqualTo("125 %");
        assertThat(control().slider().getValue()).isEqualTo(ZoomScale.sliderValue(1.25));
        assertThat(view.statusBar().text()).endsWith("125 %");
    }

    @Test
    void typingAPercentageZoomsTheActivePaneAroundItsCentre() {
        CircuitCanvas pane = view.canvas();
        pane.setSize(800, 600);
        pane.applyViewState(new ViewState(1, 30, 40));
        JTextField editor = (JTextField) control().field().getEditor().getEditorComponent();

        editor.setText("125");
        editor.postActionEvent();

        assertThat(pane.viewState().zoom()).isEqualTo(1.25);
        assertThat(pane.viewState().centerX()).isCloseTo(30, within(1e-9));
        assertThat(pane.viewState().centerY()).isCloseTo(40, within(1e-9));
        assertThat(control().text()).isEqualTo("125 %");
        assertThat(openDocument.history().canUndo()).as("a zoom is no undo step").isFalse();
    }

    @Test
    void invalidTextKeepsTheView() {
        ViewState before = new ViewState(2, 3, 4);
        view.canvas().applyViewState(before);
        JTextField editor = (JTextField) control().field().getEditor().getEditorComponent();

        editor.setText("mucho");
        editor.postActionEvent();

        assertThat(view.canvas().viewState()).isEqualTo(before);
        assertThat(control().field().getClientProperty("JComponent.outline")).isEqualTo("error");
    }

    @Test
    void theSliderZoomsTheActivePaneAroundItsCentreAtOnce() {
        CircuitCanvas pane = view.canvas();
        pane.setSize(800, 600);
        pane.applyViewState(new ViewState(1, 30, 40));

        control().slider().setValue(100);

        assertThat(pane.isTravelling()).isFalse();
        assertThat(pane.viewState().zoom()).isCloseTo(2.0, within(1e-12));
        assertThat(pane.viewState().centerX()).isCloseTo(30, within(1e-9));
        assertThat(control().text()).isEqualTo("200 %");
    }

    @Test
    void inASplitViewTheControlShowsAndDrivesTheActivePaneOnly() {
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        CircuitCanvas left = view.panes().get(0);
        CircuitCanvas right = view.panes().get(1);
        ViewState leftBefore = new ViewState(0.5, 1, 2);
        ViewState rightBefore = new ViewState(2, 5, 6);
        left.applyViewState(leftBefore);
        right.applyViewState(rightBefore);
        press(left);
        assertThat(control().text()).isEqualTo("50 %");

        press(right);

        assertThat(control().text()).isEqualTo("200 %");
        assertThat(control().slider().getValue()).isEqualTo(100);
        assertThat(left.viewState()).as("activating a pane changes no pane").isEqualTo(leftBefore);
        assertThat(right.viewState()).isEqualTo(rightBefore);

        control().slider().setValue(0);

        assertThat(right.viewState().zoom()).isEqualTo(1.0);
        assertThat(left.viewState()).isEqualTo(leftBefore);

        left.applyViewState(new ViewState(3, 1, 2));
        assertThat(control().text()).as("a pane that is not active does not speak").isEqualTo("100 %");
    }

    @Test
    void escribirElZoomIsInTheVerMenuAfterZoomALaSeleccionWithItsShortcutId() {
        List<String> texts = new ArrayList<>();
        List<JMenuItem> items = new ArrayList<>();
        for (int index = 0; index < view.menu(MenuId.VIEW).getItemCount(); index++) {
            JMenuItem item = view.menu(MenuId.VIEW).getItem(index);
            texts.add(item == null ? "---" : item.getText());
            items.add(item);
        }
        int position = texts.indexOf("Escribir el zoom…");
        assertThat(position).isEqualTo(texts.indexOf("Zoom a la selección") + 1);
        assertThat(items.get(position).getAction()).isSameAs(control().typeZoomAction());
        assertThat(ShortcutCatalog.find("view.zoomField")).contains(new ShortcutEntry("view.zoomField",
                "action.zoomField", MenuId.VIEW, Scope.WINDOW, Optional.empty()));
    }

    @Test
    void theOldZoomLabelAndItsClickAreGone() {
        for (java.awt.Component child : view.statusBar().getComponents()) {
            if (child instanceof javax.swing.JLabel label && label.getText() != null) {
                assertThat(label.getText()).doesNotStartWith("zoom");
            }
        }
        assertThat(control().resetButton().getText()).isEqualTo("100 %");
    }
}
