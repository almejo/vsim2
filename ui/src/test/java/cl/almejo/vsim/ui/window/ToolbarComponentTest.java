package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.Component;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/** Extensions hang their own components at the end of the toolbar; the animation point is on for now. */
class ToolbarComponentTest {

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    @Test
    void aToolbarComponentGoesLastAndTheReturnedRunnableTakesItBack() {
        JButton first = new JButton("one");
        JButton second = new JButton("two");
        Runnable removeFirst = view.addToolbarComponent(first);
        Runnable removeSecond = view.addToolbarComponent(second);
        Component[] children = view.toolBar().getComponents();
        assertThat(children[children.length - 1]).isSameAs(second);
        assertThat(children[children.length - 2]).isSameAs(first);
        removeSecond.run();
        removeFirst.run();
        assertThat(view.toolBar().getComponents()).doesNotContain(first, second);
    }

    @Test
    void animationsAreAlwaysOnUntilTheGlobalSettingExists() {
        assertThat(Animations.enabled(AppPreferences.inMemory())).isTrue();
    }
}
