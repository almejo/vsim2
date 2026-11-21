package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.whatsnew.FeatureBubble;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.util.Objects;

/**
 * The help bubble of a machine's test mode (hito 4, fix round 2): the first time the user tests a machine, a bubble over
 * the test table says what it is for: write the inputs of each edge and what you EXPECT to come out; the ✓ / ✗ row says
 * whether the machine does it; fix the diagram, not what you expected, until everything is ✓; it is a draft until
 * «Aplicar». It is the same bubble as «Qué hay de nuevo» ({@link FeatureBubble}), shown once (it counts as seen when
 * the user ends it, {@code AppPreferences.machineTestHelpSeen}) and shown again by Ayuda ▸ «Ayuda del modo prueba»
 * ({@link #installMenuItem}) while the machine is being tested. One per machine tab. EDT only.
 */
final class MachineTestHelp {

    /** The id the test table registers as a bubble target ({@code NewsTargets}). */
    static final String TARGET_ID = "machine.testTable";

    /** Opens the bubble over {@code view}; returns what takes it away; {@code ended} runs when the user ends it. */
    interface Bubble {
        Runnable open(EditorView view, Runnable ended);
    }

    private final EditorView view;
    private Bubble bubble = standard();
    private Runnable takeAway = () -> { };

    MachineTestHelp(EditorView view) {
        this.view = view;
    }

    /**
     * The real bubble: opened once the window has laid out what the test mode just put in it (the table is not shown
     * until then), and taken away before that when asked.
     */
    private static Bubble standard() {
        return (shownIn, ended) -> {
            boolean[] cancelled = {false};
            Runnable[] closer = {() -> { }};
            SwingUtilities.invokeLater(() -> {
                if (!cancelled[0]) {
                    closer[0] = FeatureBubble.open(shownIn, TARGET_ID, "machine.test.help.title",
                            "machine.test.help.text", ended);
                }
            });
            return () -> {
                cancelled[0] = true;
                closer[0].run();
            };
        };
    }

    /** The bubble opens through {@code replacement} from now on (tests). */
    void use(Bubble replacement) {
        bubble = Objects.requireNonNull(replacement, "replacement");
    }

    /** A test just began: the bubble, when the user never ended it. */
    void firstTime() {
        if (!view.services().preferences().machineTestHelpSeen()) {
            show();
        }
    }

    /** The bubble, from the start (Ayuda ▸ «Ayuda del modo prueba»). */
    void show() {
        takeAway.run();
        takeAway = bubble.open(view, this::ended);
    }

    /** The test ended, or its tab goes: the bubble goes, unseen if the user did not end it. */
    void close() {
        Runnable closing = takeAway;
        takeAway = () -> { };
        closing.run();
    }

    private void ended() {
        takeAway = () -> { };
        view.services().preferences().setMachineTestHelpSeen(true);
    }

    /**
     * Ayuda ▸ «Ayuda del modo prueba» in {@code view}: on while the view tests a machine, off otherwise (a circuit's
     * tab too), saying why. Right after «Consejo del día» or «Qué hay de nuevo» when they are there. Returns what
     * removes it.
     */
    static Runnable installMenuItem(EditorView view) {
        Action action = Actions.of(Messages.text("action.machineTestHelp"),
                () -> MachineMode.of(view).ifPresent(mode -> mode.help().show()));
        Actions.disabledBecause(action, () -> Messages.text("machine.test.help.onlyTesting"));
        JMenuItem item = new JMenuItem(action);
        item.putClientProperty(Actions.SEARCH_TERMS, Messages.text("machine.test.help.searchTerms"));
        Runnable refresh = () -> action.setEnabled(MachineMode.of(view).map(MachineMode::isTesting).orElse(false));
        refresh.run();
        JMenu help = view.menu(MenuId.HELP);
        help.add(item, indexAfterTheTips(help));
        Runnable stopFollowing = MachineMode.of(view).map(mode -> mode.addTestModeListener(refresh))
                .orElse(() -> { });
        return () -> {
            stopFollowing.run();
            help.remove(item);
        };
    }

    /** Right after «Consejo del día», else after «Qué hay de nuevo», else first. */
    private static int indexAfterTheTips(JMenu help) {
        int after = 0;
        for (int index = 0; index < help.getMenuComponentCount(); index++) {
            if (help.getMenuComponent(index) instanceof JMenuItem candidate
                    && (Messages.text("action.tipOfTheDay").equals(candidate.getText())
                    || Messages.text("action.whatsNew").equals(candidate.getText()))) {
                after = Math.max(after, index + 1);
            }
        }
        return after;
    }
}
