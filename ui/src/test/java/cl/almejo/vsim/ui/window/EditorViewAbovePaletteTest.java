package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static org.assertj.core.api.Assertions.assertThat;

/** The left column's slot above the palette (the Proyecto panel's), with its divider remembered. */
class EditorViewAbovePaletteTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AppPreferences preferences = AppPreferences.inMemory();
    private EditorView view;

    @BeforeEach
    void create() {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new cl.almejo.vsim.core.library.ComponentLibrary(), java.util.List.of());
        view = new EditorView(openDocument, services, NO_WINDOW);
        view.setSize(900, 600);
        layOut(view);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    @Test
    void theComponentGoesAboveThePalette() {
        JLabel header = new JLabel("Proyecto");

        view.addAbovePalette(header);
        layOut(view);

        assertThat(SwingUtilities.isDescendingFrom(header, view)).isTrue();
        assertThat(SwingUtilities.isDescendingFrom(view.palette(), view)).isTrue();
        int headerTop = SwingUtilities.convertPoint(header, 0, 0, view).y;
        int paletteTop = SwingUtilities.convertPoint(view.palette(), 0, 0, view).y;
        assertThat(headerTop).isLessThan(paletteTop);
    }

    @Test
    void theRemovalBringsBackThePaletteAlone() {
        JLabel header = new JLabel("Proyecto");
        Runnable removal = view.addAbovePalette(header);

        removal.run();
        layOut(view);

        assertThat(SwingUtilities.isDescendingFrom(header, view)).isFalse();
        assertThat(SwingUtilities.isDescendingFrom(view.palette(), view)).isTrue();
        removal.run();
    }

    @Test
    void theDividerIsRememberedInThePreferences() {
        JLabel header = new JLabel("Proyecto");
        view.addAbovePalette(header);
        layOut(view);
        JSplitPane split = (JSplitPane) SwingUtilities.getAncestorOfClass(JSplitPane.class, header);

        split.setDividerLocation(123);

        assertThat(preferences.projectSplit()).hasValue(123);
    }

    @Test
    void aRememberedDividerIsUsedWhenTheComponentIsAdded() {
        preferences.setProjectSplit(150);
        JLabel header = new JLabel("Proyecto");

        view.addAbovePalette(header);
        layOut(view);

        JSplitPane split = (JSplitPane) SwingUtilities.getAncestorOfClass(JSplitPane.class, header);
        assertThat(split.getDividerLocation()).isEqualTo(150);
    }
}
