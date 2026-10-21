package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dividir la vista, review M1: a maximized window restored split still applies its initial view again at the
 * maximized size; the resizes of the panes the split made do not use up that wait.
 */
class InitialViewWatchTest {

    private static final WindowCommands WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
        @Override public void newView() { }
    };

    private final AtomicInteger applied = new AtomicInteger();
    private OpenDocument document;
    private EditorView view;
    private InitialView initialView;

    @BeforeEach
    void create() throws Exception {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        SwingUtilities.invokeAndWait(() -> {
            view = new EditorView(document, PainterRegistry.standard(), ColorScheme.LIGHT, WINDOW);
            view.setSize(1200, 800);
            view.doLayout();
            view.canvasPanes().setSize(800, 600);
            view.canvasPanes().doLayout();
            // the initial view splits the area, as applying the remembered split does (RememberedSplit)
            initialView = new InitialView(() -> {
                if (applied.incrementAndGet() == 1) {
                    view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
                }
                initialView.viewChanged();
            });
            CircuitWindow.watchForTheInitialView(view, initialView, () -> true);
        });
    }

    @AfterEach
    void dispose() throws Exception {
        SwingUtilities.invokeAndWait(() -> view.dispose());
        document.close();
    }

    private static void resized(Component component) {
        component.dispatchEvent(new ComponentEvent(component, ComponentEvent.COMPONENT_RESIZED));
    }

    @Test
    void theResizesOfTheSplitPanesDoNotUseUpTheWaitForTheMaximizedSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            initialView.opened(true);
            assertThat(view.canvasPanes().panes()).hasSize(2);
            view.canvasPanes().panes().forEach(InitialViewWatchTest::resized);
            assertThat(applied).as("a split resizes the panes, not the area").hasValue(1);

            resized(view.canvasPanes()); // the maximized layout arrives

            assertThat(applied).hasValue(2);
        });
    }

    @Test
    void aPressOnAPaneTheSplitMadeKeepsTheUsersView() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            initialView.opened(true);
            CircuitCanvas added = view.canvasPanes().panes().get(1);
            added.dispatchEvent(new MouseEvent(added, MouseEvent.MOUSE_PRESSED, 0, 0, 10, 10, 1, false,
                    MouseEvent.BUTTON1));

            resized(view.canvasPanes());

            assertThat(applied).hasValue(1);
        });
    }
}
