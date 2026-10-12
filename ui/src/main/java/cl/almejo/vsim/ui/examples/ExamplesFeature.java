package cl.almejo.vsim.ui.examples;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Archivo ▸ Ejemplos (spec M6.1 §3.1), right after «Recientes»: one entry per bundled example, with its description as
 * tooltip. Choosing one reads a fresh copy of the example, names it after the example and asks the window to show it
 * as a new untitled document ({@link WindowCommands#openUntitled}); the bundled file is never written.
 */
public final class ExamplesFeature implements EditorExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExamplesFeature.class);
    /** The standard types only: an example brings its own definitions, and the window adds its document resolver. */
    private static final ComponentTypeRegistry BASE_REGISTRY = StandardComponentTypes.registry();

    /** Reads the example {@code id} into a new document. */
    @FunctionalInterface
    interface Loader {
        CircuitDocument load(String id) throws CircuitFormatException;
    }

    private final Loader loader;
    private final Function<Component, Optional<WindowCommands>> windowOf;
    private final BiConsumer<Component, String> failureNotice;

    public ExamplesFeature() {
        this(id -> BundledExamples.open(id, BASE_REGISTRY), ExamplesFeature::windowCommandsOf,
                ExamplesFeature::showFailure);
    }

    /**
     * @param windowOf      the window commands of the view (production: the window that contains it)
     * @param failureNotice told the view and the example's name when the example cannot be read
     */
    ExamplesFeature(Loader loader, Function<Component, Optional<WindowCommands>> windowOf,
                    BiConsumer<Component, String> failureNotice) {
        this.loader = loader;
        this.windowOf = windowOf;
        this.failureNotice = failureNotice;
    }

    @Override
    public Runnable install(EditorView view) {
        JMenu file = view.menu(MenuId.FILE);
        JMenu examples = new JMenu(Messages.text("menu.file.examples"));
        for (String id : BundledExamples.ids()) {
            JMenuItem item = new JMenuItem(Messages.text(nameKey(id)));
            item.setToolTipText(Messages.text(nameKey(id) + ".description"));
            item.addActionListener(event -> open(id, view));
            examples.add(item);
        }
        file.add(examples, position(file, view));
        return () -> file.remove(examples);
    }

    /** Right after «Recientes»; right after «Abrir…» when there is no «Recientes»; at the end without either. */
    static int position(JMenu file, EditorView view) {
        String recent = Messages.text("menu.file.recent");
        for (int index = 0; index < file.getMenuComponentCount(); index++) {
            if (file.getMenuComponent(index) instanceof JMenu menu && recent.equals(menu.getText())) {
                return index + 1;
            }
        }
        int open = Menus.indexOf(file, view.actions().open());
        return open < 0 ? file.getMenuComponentCount() : open + 1;
    }

    /** Reads the example, names it after the example in the current language and hands it to the window. */
    void open(String id, EditorView view) {
        String name = Messages.text(nameKey(id));
        CircuitDocument document;
        try {
            document = loader.load(id);
        } catch (CircuitFormatException | RuntimeException exception) {
            // A bundled file that does not load is a build defect (BundledCircuitsTest), never the user's doing.
            LOGGER.error("Could not open the example {}", id, exception);
            failureNotice.accept(view, name);
            return;
        }
        // Not a user edit: the document is not wrapped in an OpenDocument yet, so it has no history to record it.
        document.setName(name);
        windowOf.apply(view).ifPresent(window -> window.openUntitled(document));
    }

    private static String nameKey(String id) {
        return "example." + id;
    }

    /** The window commands of the window that contains {@code component}; package-private for the test. */
    static Optional<WindowCommands> windowCommandsOf(Component component) {
        return SwingUtilities.getWindowAncestor(component) instanceof WindowCommands commands
                ? Optional.of(commands) : Optional.empty();
    }

    private static void showFailure(Component parent, String name) {
        JOptionPane.showMessageDialog(parent, Messages.format("example.openFailed", name),
                Messages.text("error.open.title"), JOptionPane.ERROR_MESSAGE);
    }
}
