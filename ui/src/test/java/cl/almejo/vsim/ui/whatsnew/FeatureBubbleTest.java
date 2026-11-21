package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.app.RecordingKeyDispatchers;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.whatsnew.NewsTargets.Side;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import java.awt.BorderLayout;
import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static org.assertj.core.api.Assertions.assertThat;

/** A bubble for one feature, outside a release's news (the machine test mode's first time): one bubble, one target. */
class FeatureBubbleTest {

    private static final String TARGET = "test.feature";
    private static final String TITLE_KEY = "whatsNew.lens.title";
    private static final String TEXT_KEY = "whatsNew.lens.text";

    private final List<KeyEventDispatcher> dispatchers = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorView view;
    private JRootPane root;
    private BubbleTours tours;
    private int ended;

    @BeforeEach
    void aWindowWithATarget() {
        Messages.initialize(Messages.SPANISH);
        AppPreferences preferences = AppPreferences.inMemory();
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of());
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, services, NO_WINDOW);
        JLabel target = new JLabel("la tabla");
        NewsTargets.register(target, TARGET, Side.ABOVE);
        view.add(target, BorderLayout.SOUTH);
        root = new JRootPane();
        JPanel content = new JPanel(new BorderLayout());
        content.add(view, BorderLayout.CENTER);
        root.setContentPane(content);
        root.setSize(1000, 700);
        layOut(root);
        tours = new BubbleTours(new BubbleTours.Seams(new RecordingKeyDispatchers(dispatchers),
                ManualFrameTicker::new, () -> 0L, candidate -> false));
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private BubbleTour open() {
        return FeatureBubble.start(tours, view, TARGET, TITLE_KEY, TEXT_KEY, () -> ended++);
    }

    @Test
    void oneBubblePointsAtItsTargetWithItsTitleAndAlone() {
        BubbleTour tour = open();

        assertThat(tour.isOpen()).isTrue();
        assertThat(tour.title()).isEqualTo(Messages.text(TITLE_KEY));
        assertThat(tour.count()).isEqualTo("1 de 1");
        assertThat(tour.previousEnabled()).isFalse();
        assertThat(tour.side()).contains(Side.ABOVE);
    }

    @Test
    void theUserEndingItRunsEnded() {
        BubbleTour tour = open();

        tour.done();

        assertThat(tour.isOpen()).isFalse();
        assertThat(ended).isEqualTo(1);
    }

    @Test
    void escapeEndsItToo() {
        open();
        KeyEvent escape = new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);

        assertThat(List.copyOf(dispatchers)).anyMatch(dispatcher -> dispatcher.dispatchKeyEvent(escape));

        assertThat(ended).isEqualTo(1);
    }

    @Test
    void takingItAwayDoesNotEndItSoItComesAgain() {
        BubbleTour tour = open();

        tour.close();

        assertThat(tour.isOpen()).isFalse();
        assertThat(ended).isZero();
    }

    @Test
    void withoutItsTargetNothingShows() {
        BubbleTour tour = FeatureBubble.start(tours, view, "nobody.here", TITLE_KEY, TEXT_KEY, () -> ended++);

        assertThat(tour.isOpen()).isFalse();
        assertThat(Optional.of(ended)).contains(0);
    }
}
