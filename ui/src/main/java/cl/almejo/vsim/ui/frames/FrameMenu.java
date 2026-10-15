package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.command.ChangeFrameCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The items of the frame menu (the ⋯ button and the right click on the title bar, spec §5). They act on the frame as
 * it is in the document when the item is chosen. Built as a model: tests inspect it and never show it.
 */
public final class FrameMenu {

    private final OpenDocument openDocument;
    private final Consumer<Frame> titleEditor;
    private final Consumer<Frame> componentConverter;

    public FrameMenu(OpenDocument openDocument, Consumer<Frame> titleEditor, Consumer<Frame> componentConverter) {
        this.openDocument = openDocument;
        this.titleEditor = titleEditor;
        this.componentConverter = componentConverter;
    }

    /** The visible name of {@code tint}. */
    public static String tintName(FrameTint tint) {
        return Messages.text(switch (tint) {
            case NONE -> "frames.tint.none";
            case BLUE -> "frames.tint.blue";
            case GREEN -> "frames.tint.green";
            case YELLOW -> "frames.tint.yellow";
            case RED -> "frames.tint.red";
            case PURPLE -> "frames.tint.purple";
            case GRAY -> "frames.tint.gray";
        });
    }

    /** A popup holding only the frame items, for tests. */
    public JPopupMenu build(Frame frame) {
        JPopupMenu menu = new JPopupMenu();
        addItems(menu, frame);
        return menu;
    }

    /** Adds the frame items to {@code menu}. */
    public void addItems(JPopupMenu menu, Frame frame) {
        menu.add(item(Messages.text("frames.menu.title"), () -> current(frame).ifPresent(titleEditor)));
        JMenu color = new JMenu(Messages.text("frames.menu.color"));
        ButtonGroup group = new ButtonGroup();
        for (FrameTint tint : FrameTint.values()) {
            JRadioButtonMenuItem radio = new JRadioButtonMenuItem(tintName(tint), frame.tint() == tint);
            radio.addActionListener(event -> current(frame).ifPresent(
                    now -> change(now, now.withTint(tint))));
            group.add(radio);
            color.add(radio);
        }
        menu.add(color);
        menu.add(item(Messages.text(frame.locked() ? "frames.menu.unlock" : "frames.menu.lock"),
                () -> current(frame).ifPresent(now -> change(now, now.withLocked(!now.locked())))));
        JMenuItem convert = item(Messages.text("frames.menu.convert"),
                () -> current(frame).ifPresent(componentConverter));
        convert.setEnabled(!FrameContents.of(openDocument.document(), openDocument.registry(), frame).isEmpty());
        menu.add(convert);
        menu.add(item(Messages.text("frames.menu.remove"),
                () -> current(frame).ifPresent(now -> openDocument.execute(
                        new RemoveElementsCommand(List.of(now.id()))))));
    }

    private Optional<Frame> current(Frame frame) {
        return openDocument.document().frame(frame.id());
    }

    private void change(Frame before, Frame after) {
        if (!before.equals(after)) {
            openDocument.execute(new ChangeFrameCommand(before, after));
        }
    }

    private static JMenuItem item(String text, Runnable behavior) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(event -> behavior.run());
        return item;
    }
}
