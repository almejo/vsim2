package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MemoryFile;
import cl.almejo.vsim.core.memory.MemoryHeader;
import cl.almejo.vsim.core.memory.MemoryKind;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.actions.Keys;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * The content editor of a RAM or ROM (memory spec §7): a hex grid over a copy of the initial contents, a toolbar
 * (import, export, dump, link, external editor, fill, go to, restore), Aceptar (one {@link EditMemoryContentsCommand})
 * and Cancelar. A linked memory shows a read-only grid with a bar to open the external editor or unlink; while the
 * simulation runs a RAM's grid shows the live words and the last access. Built headless (the content is a plain
 * panel); {@link #show} puts it in a modal dialog. Everything is on the EDT, and the engine is only reached through
 * {@link OpenDocument}'s queued requests.
 */
public final class MemoryEditorDialog {

    /** The live grid is refreshed at most this often (5 times a second). */
    static final int LIVE_INTERVAL_MILLISECONDS = 200;

    private static final int CELL_PADDING = 12;

    private final OpenDocument document;
    private final ElementId componentId;
    private final EditorView view;
    private final MemoryLinkActions linkActions;
    private final MemoryEditorPrompts prompts;
    private final MemoryKind kind;
    private final MemoryGridModel model;
    private final String name;
    private final JPanel content = new JPanel(new BorderLayout());
    private final JTable table;
    private final JPanel linkBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JLabel linkText = new JLabel();
    private final JLabel status = new JLabel(" ");
    private final JButton linkButton;
    private final JButton externalButton;
    private final JButton unlinkButton;
    private final Timer liveTimer;
    private String link = "";
    private boolean livePending;
    private Runnable closer = () -> { };

    /** The editor with the real windows and clipboard for its questions. */
    public MemoryEditorDialog(OpenDocument document, ElementId componentId, EditorView view,
                              MemoryLinkActions linkActions) {
        this(document, componentId, view, linkActions, null);
    }

    /**
     * The editor of the memory {@code componentId} of {@code document}. {@code view} is handed to
     * {@code linkActions} (a test without a window passes null); {@code prompts} asks the user.
     */
    public MemoryEditorDialog(OpenDocument document, ElementId componentId, EditorView view,
                              MemoryLinkActions linkActions, MemoryEditorPrompts prompts) {
        this.document = document;
        this.componentId = componentId;
        this.view = view;
        this.linkActions = linkActions;
        this.prompts = prompts != null ? prompts : new SwingMemoryEditorPrompts(() -> content);
        Component component = document.document().component(componentId).orElseThrow();
        ComponentType type = document.registry().typeOf(component);
        this.kind = MemoryTypes.kindOf(component.typeId()).orElseThrow();
        Map<String, Object> resolved = type.resolveParameters(component.parameters());
        long words = MemoryTypes.words(resolved);
        int width = (Integer) resolved.get(MemoryTypes.WIDTH);
        MemoryContents initialContents = MemoryContents.decode((String) resolved.get(MemoryTypes.CONTENTS));
        this.model = new MemoryGridModel(initialContents, width, words, MemoryRadix.HEX);
        this.name = nameOf(component);

        table = new JTable(model) {
            @Override
            public void setValueAt(Object value, int row, int column) {
                OptionalLong address = model.addressAt(row, column);
                if (address.isPresent()) {
                    editCell(address.getAsLong(), String.valueOf(value));
                }
            }
        };
        configureTable();

        linkButton = toolbarButton("memory.link", "memory.link", this::linkFile);
        externalButton = toolbarButton("memory.external", "memory.external", this::openExternal);
        unlinkButton = new JButton(Messages.text("memory.unlink"));
        unlinkButton.setName("memory.unlink");
        unlinkButton.addActionListener(event -> unlink());
        JButton barExternal = new JButton(Messages.text("memory.external"));
        barExternal.setName("memory.bar.external");
        barExternal.addActionListener(event -> openExternal());
        barExternal.setEnabled(linkActions.available());
        linkBar.setName("memory.linkBar");
        linkBar.add(linkText);
        linkBar.add(barExternal);
        linkBar.add(unlinkButton);
        linkBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JPanel north = new JPanel(new BorderLayout());
        north.add(buildToolbar(), BorderLayout.NORTH);
        north.add(linkBar, BorderLayout.SOUTH);
        content.add(north, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(buildSouth(), BorderLayout.SOUTH);
        Keys.bind(content, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke(KeyEvent.VK_G, shortcutMask()),
                "memory.goTo", this::askGoTo);

        liveTimer = new Timer(LIVE_INTERVAL_MILLISECONDS, event -> pollLive());
        liveTimer.setRepeats(true);
        refreshLink();
        updateStatus(Optional.empty());
    }

    // Parts

    public JComponent content() {
        return content;
    }

    public MemoryGridModel model() {
        return model;
    }

    public JTable table() {
        return table;
    }

    public JComponent linkBar() {
        return linkBar;
    }

    public JLabel status() {
        return status;
    }

    private static String nameOf(Component component) {
        return component.label().isBlank() ? Messages.componentName(component.typeId()) : component.label();
    }

    /** «RAM · 1K × 16». */
    private String summary() {
        return Messages.componentName(kind.id()) + " · " + Messages.choiceName(MemoryTypes.WORDS,
                String.valueOf(model.words())) + " × " + model.width();
    }

    private static int shortcutMask() {
        return GraphicsEnvironment.isHeadless() ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    private void configureTable() {
        table.setName("memory.grid");
        table.setCellSelectionEnabled(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setDefaultRenderer(Object.class, new MemoryCellRenderer(model));
        int digits = model.radix() == MemoryRadix.BINARY ? model.width() : Math.max(2, (model.width() + 3) / 4);
        int cellWidth = table.getFontMetrics(table.getFont()).charWidth('0') * digits + CELL_PADDING;
        for (int column = 0; column < model.getColumnCount(); column++) {
            int width = model.isAddressColumn(column)
                    ? table.getFontMetrics(table.getFont()).charWidth('0') * 8 + CELL_PADDING
                    : model.isAsciiColumn(column) ? table.getFontMetrics(table.getFont()).charWidth('0') * 17
                    : cellWidth;
            table.getColumnModel().getColumn(column).setPreferredWidth(width);
        }
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutMask()), "copy");
        table.getActionMap().put("copy", unnamed(this::copySelection));
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutMask()), "paste");
        table.getActionMap().put("paste", unnamed(this::pasteAtSelection));
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutMask()),
                "memory.undo");
        table.getActionMap().put("memory.undo", unnamed(this::undo));
        for (int modifiers : new int[]{shortcutMask(), shortcutMask() | InputEvent.SHIFT_DOWN_MASK}) {
            int key = (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0 ? KeyEvent.VK_Z : KeyEvent.VK_Y;
            table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, modifiers), "memory.redo");
        }
        table.getActionMap().put("memory.redo", unnamed(this::redo));
    }

    private static Action unnamed(Runnable behavior) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                behavior.run();
            }
        };
    }

    private JToolBar buildToolbar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.add(toolbarButton("memory.import", "memory.import", this::importFromFile));
        toolBar.add(toolbarButton("memory.export", "memory.export", this::exportToFile));
        if (kind == MemoryKind.RAM) {
            toolBar.add(toolbarButton("memory.dump", "memory.dump", this::dumpToFile));
        }
        toolBar.addSeparator();
        toolBar.add(linkButtonPlaceholder());
        toolBar.addSeparator();
        toolBar.add(toolbarButton("memory.fill", "memory.fill", this::askFill));
        toolBar.add(toolbarButton("memory.goTo", "memory.goTo", this::askGoTo));
        if (kind == MemoryKind.RAM) {
            toolBar.add(toolbarButton("memory.restore", "memory.restore", this::restore));
        }
        toolBar.addSeparator();
        JComboBox<MemoryRadix> radix = new JComboBox<>(MemoryRadix.values());
        radix.setName("memory.radix");
        radix.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                                   boolean selected, boolean focused) {
                String key = "memory.radix." + value;
                return super.getListCellRendererComponent(list, Messages.text(key), index, selected, focused);
            }
        });
        radix.addActionListener(event -> model.setRadix((MemoryRadix) radix.getSelectedItem()));
        toolBar.add(radix);
        return toolBar;
    }

    /** The «Enlazar» and «Editor externo» buttons (built first: they need the fields) as one group. */
    private JComponent linkButtonPlaceholder() {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        group.setOpaque(false);
        group.add(linkButton);
        group.add(externalButton);
        return group;
    }

    private JButton toolbarButton(String name, String key, Runnable behavior) {
        JButton button = new JButton(Icons.get(MemoryIcons.of(name), Icons.MENU_SIZE));
        button.setName(name);
        button.setToolTipText(Messages.text(key + ".tooltip"));
        button.setFocusable(false);
        button.addActionListener(event -> behavior.run());
        return button;
    }

    private JPanel buildSouth() {
        JPanel south = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        south.add(status, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton accept = new JButton(Messages.text("memory.editor.accept"));
        accept.setName("memory.accept");
        accept.addActionListener(event -> accept());
        JButton cancel = new JButton(Messages.text("memory.editor.cancel"));
        cancel.setName("memory.cancel");
        cancel.addActionListener(event -> cancel());
        buttons.add(accept);
        buttons.add(cancel);
        south.add(buttons, BorderLayout.EAST);
        return south;
    }

    // Link state

    /** Re-reads the link from the document (a link action changed it) and shows the bar and the grid mode. */
    private void refreshLink() {
        link = document.document().component(componentId)
                .map(component -> String.valueOf(component.parameters().getOrDefault(MemoryTypes.LINK, "")))
                .orElse("");
        boolean linked = !link.isEmpty();
        model.setReadOnly(linked);
        linkBar.setVisible(linked);
        linkText.setText(Messages.format("memory.linkBar", shownLink()));
        boolean available = linkActions.available();
        linkButton.setEnabled(available);
        externalButton.setEnabled(available && linked);
        unlinkButton.setEnabled(available);
    }

    /** The link as the user reads it: relative to the circuit when it is saved. */
    private String shownLink() {
        return link.isEmpty() ? "" : MemoryLinks.displayPath(link, document.path());
    }

    /** «Enlazar…»: once linked, the grid shows the file's words (read-only). */
    private void linkFile() {
        linkActions.link(view, componentId);
        refreshLink();
        if (!link.isEmpty()) {
            document.document().component(componentId).ifPresent(component -> model.replaceContents(
                    MemoryContents.decode(String.valueOf(component.parameters().get(MemoryTypes.CONTENTS)))));
            updateStatus(Optional.empty());
        }
    }

    private void openExternal() {
        linkActions.openExternal(view, componentId);
    }

    private void unlink() {
        if (prompts.confirm(Messages.format("memory.unlink.confirm", shownLink()))) {
            linkActions.unlink(view, componentId);
            refreshLink();
            updateStatus(Optional.empty());
        }
    }

    /** True when the memory is not linked any more, asking to unlink it when it is (for importing). */
    private boolean unlinkedForImport() {
        if (link.isEmpty()) {
            return true;
        }
        if (!linkActions.available()
                || !prompts.confirm(Messages.format("memory.import.unlinkFirst", shownLink()))) {
            return false;
        }
        linkActions.unlink(view, componentId);
        refreshLink();
        return link.isEmpty();
    }

    // Toolbar actions

    private void importFromFile() {
        if (!unlinkedForImport()) {
            return;
        }
        prompts.chooseImport().ifPresent(this::importFile);
    }

    void importFile(Path file) {
        try {
            MemoryFile read = MemoryFileIo.read(file);
            model.replaceContents(read.contents());
            updateStatus(Optional.empty());
        } catch (MemoryFileIo.Unreadable problem) {
            prompts.inform(problem.getMessage());
        }
    }

    private void exportToFile() {
        prompts.chooseSave(suggestedName()).ifPresent(choice -> {
            try {
                MemoryFileIo.write(MemoryFileIo.withExtension(choice.path(), choice.format()), choice.format(),
                        new MemoryFile(header(), model.edited()), model.width());
            } catch (MemoryFileIo.Unreadable problem) {
                prompts.inform(problem.getMessage());
            }
        });
    }

    void dumpToFile() {
        prompts.chooseSave(suggestedName()).ifPresent(this::dumpTo);
    }

    /**
     * «Volcar»: writes the words the simulation holds now (the initial ones when it has none) with a header. Refuses
     * the linked file: VSim never writes it. The words come through a queued request; the file is written off the
     * EDT and the future completes when it is done (or the user was told why not).
     */
    CompletableFuture<Void> dumpTo(MemoryEditorPrompts.SaveChoice choice) {
        Path target = MemoryFileIo.withExtension(choice.path(), choice.format());
        if (isLinkedFile(target)) {
            prompts.inform(Messages.format("memory.dump.linkedRefused", shownLink()));
            return CompletableFuture.completedFuture(null);
        }
        return document.requestMemoryContents(componentId)
                .thenAcceptAsync(contents -> {
                    try {
                        MemoryFileIo.write(target, choice.format(),
                                new MemoryFile(header(), contents.orElse(model.edited())), model.width());
                    } catch (MemoryFileIo.Unreadable problem) {
                        SwingUtilities.invokeLater(() -> prompts.inform(problem.getMessage()));
                    }
                }, ForkJoinPool.commonPool());
    }

    /** Whether {@code file} is the file this memory is linked to. */
    private boolean isLinkedFile(Path file) {
        return document.document().component(componentId)
                .flatMap(component -> MemoryLinks.linkedFile(component, document.path()))
                .filter(linked -> MemoryLinks.isSameFile(linked, file))
                .isPresent();
    }

    private MemoryHeader header() {
        Component component = document.document().component(componentId).orElseThrow();
        return new MemoryHeader(Optional.of(kind),
                component.label().isBlank() ? Optional.empty() : Optional.of(component.label()),
                OptionalInt.of((int) model.words()), OptionalInt.of(model.width()));
    }

    private String suggestedName() {
        return name.replaceAll("\\s+", "_");
    }

    private void restore() {
        document.restoreMemory(componentId);
    }

    private void askFill() {
        long first = 0;
        long last = model.words() - 1;
        long[] selected = selectedAddressRange();
        if (selected != null) {
            first = selected[0];
            last = selected[1];
        }
        prompts.askFill(Long.toHexString(first).toUpperCase(), Long.toHexString(last).toUpperCase())
                .ifPresent(request -> {
                    OptionalLong from = model.parseAddress(request.firstAddress());
                    OptionalLong to = model.parseAddress(request.lastAddress());
                    if (from.isEmpty() || to.isEmpty()) {
                        updateStatus(Optional.of(MemoryGridModel.OUT_OF_RANGE));
                        prompts.inform(Messages.text(MemoryGridModel.OUT_OF_RANGE));
                        return;
                    }
                    Optional<String> refusal = model.fill(from.getAsLong(), to.getAsLong(), request.value());
                    updateStatus(refusal);
                    refusal.ifPresent(key -> prompts.inform(Messages.text(key)));
                });
    }

    /** First and last address of the selected value cells, or null when none is selected. */
    private long[] selectedAddressRange() {
        long first = Long.MAX_VALUE;
        long last = -1;
        for (int row : table.getSelectedRows()) {
            for (int column : table.getSelectedColumns()) {
                OptionalLong address = model.addressAt(row, column);
                if (address.isPresent()) {
                    first = Math.min(first, address.getAsLong());
                    last = Math.max(last, address.getAsLong());
                }
            }
        }
        return last < 0 ? null : new long[]{first, last};
    }

    private void askGoTo() {
        prompts.askAddress().ifPresent(text -> {
            OptionalLong address = model.parseAddress(text);
            if (address.isPresent()) {
                goTo(address.getAsLong());
            } else {
                prompts.inform(Messages.format("memory.goTo.invalid", text.trim(),
                        Long.toHexString(model.words() - 1).toUpperCase()));
            }
        });
    }

    /** Selects the cell of {@code address} and scrolls to it. */
    public void goTo(long address) {
        if (address < 0 || address >= model.words()) {
            return;
        }
        int row = model.rowOf(address);
        int column = model.columnOf(address);
        table.changeSelection(row, column, false, false);
        table.scrollRectToVisible(table.getCellRect(row, column, true));
    }

    // Cell edits, clipboard, undo

    private void editCell(long address, String text) {
        updateStatus(model.setCell(address, text));
    }

    /** Ctrl+C: the selected cells as hex text, one line per row. */
    void copySelection() {
        int[] rows = table.getSelectedRows();
        int[] columns = table.getSelectedColumns();
        if (rows.length == 0 || columns.length == 0) {
            return;
        }
        prompts.copyToClipboard(model.copyText(rows[0], rows[rows.length - 1], columns[0],
                columns[columns.length - 1]));
    }

    /** Ctrl+V: hex text from the clipboard, from the selected cell on. */
    void pasteAtSelection() {
        int row = table.getSelectedRow();
        int column = table.getSelectedColumn();
        OptionalLong address = row < 0 ? OptionalLong.empty() : model.addressAt(row, column);
        Optional<String> text = prompts.clipboardText();
        if (address.isPresent() && text.isPresent()) {
            updateStatus(model.paste(address.getAsLong(), text.get()));
        }
    }

    void undo() {
        model.undo();
        updateStatus(Optional.empty());
    }

    void redo() {
        model.redo();
        updateStatus(Optional.empty());
    }

    // Status

    /** The status line: a refusal first, else the hidden-words warning, else the last access and the link state. */
    private void updateStatus(Optional<String> refusalKey) {
        if (refusalKey.isPresent()) {
            status.setText(Messages.text(refusalKey.get()));
            return;
        }
        long highest = model.edited().highestAddress();
        if (highest >= model.words()) {
            status.setText(Messages.format("memory.status.hidden", String.valueOf(model.words()),
                    Long.toHexString(model.words() - 1).toUpperCase()));
            return;
        }
        status.setText(link.isEmpty() ? " " : Messages.format("memory.status.linked", shownLink()));
    }

    private void showAccess(VisibleState.MemoryState state) {
        model.showAccess(state.lastAccess(), state.lastAddress());
        if (state.lastAccess() != VisibleState.MemoryAccess.NONE && state.lastAddress() >= 0) {
            status.setText(Messages.format(state.lastAccess() == VisibleState.MemoryAccess.READ
                            ? "memory.status.read" : "memory.status.write",
                    model.hexAddress(state.lastAddress())));
        }
    }

    // Live values

    /**
     * One live refresh: the last access from the snapshot, and (for a RAM, only while the simulation runs) the words
     * through a queued request, shown when the answer arrives on the EDT. The future completes then. Skipped while
     * the previous request is still pending, so the engine is never flooded.
     */
    CompletableFuture<Void> pollLive() {
        SimulationSnapshot snapshot = document.latestSnapshot();
        if (!snapshot.running()) {
            model.hideLive();
            model.showAccess(VisibleState.MemoryAccess.NONE, -1);
            updateStatus(Optional.empty());
            return CompletableFuture.completedFuture(null);
        }
        if (snapshot.componentState(componentId.value()) instanceof VisibleState.MemoryState state) {
            showAccess(state);
        }
        if (kind != MemoryKind.RAM || livePending) {
            return CompletableFuture.completedFuture(null);
        }
        livePending = true;
        CompletableFuture<Void> done = new CompletableFuture<>();
        document.requestMemoryContents(componentId).whenComplete((contents, failure) ->
                SwingUtilities.invokeLater(() -> {
                    livePending = false;
                    if (document.latestSnapshot().running() && contents != null) {
                        contents.ifPresent(model::showLive);
                    }
                    done.complete(null);
                }));
        return done;
    }

    // Closing

    /**
     * Aceptar: the edited contents become one undo step (nothing when they are what the circuit holds now, which a
     * link or an unlink made meanwhile may have changed); closes the dialog.
     */
    boolean accept() {
        Optional<Component> component = document.document().component(componentId);
        if (component.isEmpty()) {
            return false;
        }
        MemoryContents current = MemoryContents.decode(
                String.valueOf(component.get().parameters().getOrDefault(MemoryTypes.CONTENTS, "")));
        if (!model.isReadOnly() && !model.edited().equals(current)) {
            ComponentType type = document.registry().typeOf(component.get());
            document.execute(new EditMemoryContentsCommand(component.get(), type, model.edited()));
        }
        closer.run();
        return true;
    }

    /** Cancelar: nothing changes. */
    void cancel() {
        closer.run();
    }

    /** Shows the editor in a modal dialog over {@code parent}'s window, at {@code address} when given. */
    public void show(java.awt.Component parent, OptionalLong address) {
        JDialog dialog = new JDialog(parent == null ? null : SwingUtilities.getWindowAncestor(parent),
                Messages.format("memory.editor.title", name, summary()), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                cancel();
            }
        });
        dialog.setContentPane(content);
        dialog.getRootPane().registerKeyboardAction(event -> cancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        closer = () -> {
            liveTimer.stop();
            dialog.dispose();
        };
        dialog.setSize(Math.min(960, table.getPreferredSize().width + 80), 560);
        dialog.setLocationRelativeTo(parent);
        address.ifPresent(this::goTo);
        liveTimer.start();
        pollLive();
        dialog.setVisible(true);
    }
}
