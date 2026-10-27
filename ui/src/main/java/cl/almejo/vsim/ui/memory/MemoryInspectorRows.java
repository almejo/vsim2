package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PropertiesPanel;
import cl.almejo.vsim.ui.theme.ErrorTextLabel;
import cl.almejo.vsim.ui.window.EditorView;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.util.Map;
import java.util.OptionalLong;

/**
 * The memory rows of the «Propiedades» tab (memory spec §4, §4.1): «Contenido» says whether the words live in the
 * circuit or in a linked file, with «Editar contenido…» and «Abrir en editor externo»; a linked file that cannot be
 * used says why; and contents beyond the last word or wider than the word are warned about («⚠ El contenido tiene
 * 300 palabras y la memoria 256…») with «Agrandar a 512» / «Ensanchar a 10 bits» (one undo step each) and «Ver en
 * el editor». Nothing is ever cut.
 */
final class MemoryInspectorRows implements PropertiesPanel.ComponentRows {

    private static final long MAXIMUM_WORDS = 1L << MemoryTypes.MAXIMUM_ADDRESS_BITS;

    private final EditorView view;
    private final MemoryEditorLauncher launcher;
    private final MemoryLinkActions links;
    private final LinkedMemoryWatcher watcher;

    MemoryInspectorRows(EditorView view, MemoryEditorLauncher launcher, MemoryLinkActions links,
                        LinkedMemoryWatcher watcher) {
        this.view = view;
        this.launcher = launcher;
        this.links = links;
        this.watcher = watcher;
    }

    @Override
    public void addRows(Component component, PropertiesPanel.Rows rows) {
        if (MemoryTypes.kindOf(component.typeId()).isEmpty()) {
            return;
        }
        ComponentType type = view.openDocument().registry().typeOf(component);
        Map<String, Object> resolved = type.resolveParameters(component.parameters());
        long words = MemoryTypes.words(resolved);
        int width = (Integer) resolved.get(MemoryTypes.WIDTH);
        MemoryContents contents = MemoryContents.decode((String) resolved.get(MemoryTypes.CONTENTS));
        addContentRows(component, rows);
        watcher.problem(component.id()).ifPresent(problem ->
                rows.addWide(named(new ErrorTextLabel(problem), "memory.inspector.problem")));
        if (contents.highestAddress() >= words) {
            addHiddenWordsRows(component, contents, words, rows);
        }
        if (contents.widestBits() > width) {
            addHiddenBitsRows(component, contents, width, rows);
        }
    }

    private void addContentRows(Component component, PropertiesPanel.Rows rows) {
        String link = MemoryLinks.linkOf(component);
        boolean linked = !link.isBlank();
        String where = linked
                ? Messages.format("memory.inspector.linked", MemoryLinks.displayPath(link, view.openDocument().path()))
                : Messages.text("memory.inspector.inCircuit");
        rows.add(Messages.text("memory.inspector.contents"), named(new JLabel(where), "memory.inspector.contents"));
        ElementId id = component.id();
        JButton edit = button("memory.inspector.edit", Messages.text("memory.menu.edit"),
                () -> launcher.open(view, id, OptionalLong.empty()));
        JButton external = button("memory.inspector.external", Messages.text("memory.inspector.external"),
                () -> links.openExternal(view, id));
        external.setEnabled(linked && links.available());
        rows.add("", line(edit, external));
    }

    private void addHiddenWordsRows(Component component, MemoryContents contents, long words,
                                    PropertiesPanel.Rows rows) {
        long stored = contents.highestAddress() + 1;
        rows.addWide(named(new ErrorTextLabel(Messages.format("memory.inspector.hiddenWords",
                String.valueOf(stored), String.valueOf(words), String.valueOf(stored - words))),
                "memory.inspector.hidden"));
        ElementId id = component.id();
        JButton see = button("memory.inspector.view", Messages.text("memory.inspector.view"),
                () -> launcher.open(view, id, OptionalLong.of(words - 1)));
        long grown = wordsToShow(contents);
        if (grown > words) {
            String grownText = String.valueOf(grown);
            JButton grow = button("memory.inspector.grow", Messages.format("memory.inspector.grow",
                    Messages.choiceName(MemoryTypes.WORDS, grownText)),
                    () -> change(id, MemoryTypes.WORDS, grownText, "command.growMemory"));
            rows.add("", line(grow, see));
        } else {
            rows.add("", line(see));
        }
    }

    private void addHiddenBitsRows(Component component, MemoryContents contents, int width,
                                   PropertiesPanel.Rows rows) {
        int widest = Math.min(MemoryTypes.MAXIMUM_WIDTH, contents.widestBits());
        rows.addWide(named(new ErrorTextLabel(Messages.format("memory.inspector.hiddenBits", widest, width)),
                "memory.inspector.hiddenBits"));
        ElementId id = component.id();
        rows.add("", line(button("memory.inspector.widen", Messages.format("memory.inspector.widen", widest),
                () -> change(id, MemoryTypes.WIDTH, widest, "command.widenMemory"))));
    }

    /** The words a memory needs to show every stored word: the next power of 2, at most 16M. */
    static long wordsToShow(MemoryContents contents) {
        return MemoryFileImport.wordsFor(contents);
    }

    private void change(ElementId id, String parameter, Object value, String descriptionKey) {
        MemoryLinks.memory(view.openDocument(), id).ifPresent(component -> view.openDocument().execute(
                MemoryLinks.changing(view.openDocument(), component, Map.of(parameter, value), descriptionKey)));
    }

    private static JButton button(String name, String text, Runnable action) {
        JButton button = new JButton(text);
        button.setName(name);
        button.addActionListener(event -> action.run());
        return button;
    }

    private static JPanel line(JButton... buttons) {
        JPanel line = new JPanel(new MigLayout("insets 0"));
        line.setOpaque(false);
        for (JButton button : buttons) {
            line.add(button);
        }
        return line;
    }

    private static <T extends java.awt.Component> T named(T component, String name) {
        component.setName(name);
        return component;
    }
}
