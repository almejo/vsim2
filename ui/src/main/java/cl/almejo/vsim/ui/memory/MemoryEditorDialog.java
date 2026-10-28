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
import cl.almejo.vsim.core.memory.MemoryLinkPaths;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.actions.Keys;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.MemoryLights;
import cl.almejo.vsim.ui.search.FindIcons;
import cl.almejo.vsim.ui.search.SearchField;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ToolTipManager;
import javax.swing.text.AbstractDocument;
import javax.swing.BoxLayout;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.LongSupplier;

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
    private static final int TOOLBAR_GAP = 8;
    private static final int LINK_TEXT_WIDTH = 260;
    private static final int STATUS_WIDTH = 160;
    private static final int DEFAULT_HEIGHT = 560;

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
    private final JPanel linkBar = new JPanel(new BorderLayout(8, 0));
    private final ElidedLabel linkText = new ElidedLabel(LINK_TEXT_WIDTH);
    private final ElidedLabel status = new ElidedLabel(STATUS_WIDTH);
    private final JPanel findBar = new JPanel(new BorderLayout(6, 0));
    private final SearchField findField = new SearchField(Messages.text("memory.find.placeholder"));
    private final JLabel findCounter = new JLabel();
    private final JPanel north = new JPanel();
    private final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, TOOLBAR_GAP, 6));
    private MemorySearch search;
    private OptionalLong searchValue = OptionalLong.empty();
    private long searchVersion = -1;
    private long countedVersion = -1;
    private final JButton linkButton;
    private final JButton externalButton;
    private final JButton unlinkButton;
    private final Timer liveTimer;
    private String link = "";
    private boolean livePending;
    /** The snapshot revision whose words were last asked for (while paused, each revision is asked once). */
    private long liveRevision = -1;
    private Runnable closer = () -> { };
    /** Which accesses the grid highlights: each kept on a little while after it happened. */
    private MemoryActivity activity = new MemoryActivity();

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
        this.name = MemoryNames.displayName(document.document(), component);

        table = new JTable(model) {
            @Override
            public void setValueAt(Object value, int row, int column) {
                OptionalLong address = model.addressAt(row, column);
                if (address.isPresent()) {
                    editCell(address.getAsLong(), String.valueOf(value));
                }
            }

            @Override
            public String getToolTipText(java.awt.event.MouseEvent event) {
                int row = rowAtPoint(event.getPoint());
                int column = columnAtPoint(event.getPoint());
                return row < 0 || column < 0 ? null : model.cellToolTip(row, convertColumnIndexToModel(column));
            }
        };
        ToolTipManager.sharedInstance().registerComponent(table);
        configureTable();

        linkButton = toolbarButton("memory.link", "memory.link", this::linkFile);
        externalButton = toolbarButton("memory.external", "memory.external", this::openExternal);
        unlinkButton = new JButton(Messages.text("memory.unlink"));
        unlinkButton.setName("memory.unlink");
        unlinkButton.addActionListener(event -> unlink());
        JButton barExternal = new JButton(Messages.text("memory.external"));
        barExternal.setName("memory.bar.external");
        barExternal.addActionListener(event -> openExternal());
        linkBar.setName("memory.linkBar");
        linkText.setName("memory.linkBar.text");
        linkText.setIcon(linkIcon());
        JPanel barButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, TOOLBAR_GAP, 0));
        barButtons.setOpaque(false);
        barButtons.add(barExternal);
        barButtons.add(unlinkButton);
        linkBar.add(linkText, BorderLayout.CENTER);
        linkBar.add(barButtons, BorderLayout.EAST);
        linkBar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        buildToolbar();
        buildFindBar();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        for (JComponent part : new JComponent[]{toolbar, linkBar, findBar}) {
            part.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            north.add(part);
        }
        content.add(north, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(buildSouth(), BorderLayout.SOUTH);
        Keys.bind(content, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke(KeyEvent.VK_G, shortcutMask()),
                "memory.goTo", this::askGoTo);
        Keys.bind(content, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcutMask()),
                "memory.find", this::openFind);
        Keys.bind(content, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0),
                "memory.find.next", this::findNextOpeningTheBar);
        Keys.bind(content, JComponent.WHEN_IN_FOCUSED_WINDOW,
                KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK),
                "memory.find.previous", this::findPreviousOpeningTheBar);
        model.addTableModelListener(event -> refreshFindCount());

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

    public JComponent findBar() {
        return findBar;
    }

    public JTextField findField() {
        return findField;
    }

    public JLabel findCounter() {
        return findCounter;
    }

    /** The size the dialog opens at: wide enough for the whole toolbar and link bar (at most the screen), 560 high. */
    Dimension defaultSize() {
        int screenWidth = GraphicsEnvironment.isHeadless() ? 1600
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width;
        int gridWidth = Math.min(960, table.getPreferredSize().width + 80);
        int needed = Math.max(gridWidth, Math.max(toolbar.getPreferredSize().width, north.getPreferredSize().width)
                + 24);
        return new Dimension(Math.min(needed, Math.max(600, screenWidth - 40)), DEFAULT_HEIGHT);
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
        table.setDefaultRenderer(Object.class, new MemoryCellRenderer(model, this::currentScheme));
        JTextField cellField = new JTextField();
        ((AbstractDocument) cellField.getDocument()).setDocumentFilter(new MemoryDigitFilter(model));
        cellField.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, table.getFont().getSize()));
        cellField.setHorizontalAlignment(JTextField.CENTER);
        table.setDefaultEditor(Object.class, new DefaultCellEditor(cellField) {
            @Override
            public java.awt.Component getTableCellEditorComponent(JTable owner, Object value, boolean selected,
                                                                  int row, int column) {
                java.awt.Component component = super.getTableCellEditorComponent(owner, value, selected, row, column);
                cellField.selectAll(); // a typed digit replaces the word instead of finding it full
                return component;
            }
        });
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

    /** The toolbar: framed buttons in groups (file, link, edit, restore) and the base selector last. */
    private void buildToolbar() {
        toolbar.setName("memory.toolbar");
        toolbar.add(toolbarButton("memory.import", "memory.import", this::importFromFile));
        toolbar.add(toolbarButton("memory.export", "memory.export", this::exportToFile));
        if (kind == MemoryKind.RAM) {
            toolbar.add(toolbarButton("memory.dump", "memory.dump", this::dumpToFile));
        }
        toolbar.add(groupSeparator());
        toolbar.add(linkButton);
        toolbar.add(externalButton);
        toolbar.add(groupSeparator());
        toolbar.add(toolbarButton("memory.fill", "memory.fill", this::askFill));
        toolbar.add(toolbarButton("memory.goTo", "memory.goTo", this::askGoTo));
        toolbar.add(findButton());
        if (kind == MemoryKind.RAM) {
            toolbar.add(groupSeparator());
            toolbar.add(toolbarButton("memory.restore", "memory.restore", this::restore));
        }
        toolbar.add(groupSeparator());
        JComboBox<MemoryRadix> radix = new JComboBox<>(MemoryRadix.values());
        radix.setName("memory.radix");
        radix.setToolTipText(Messages.text("memory.radix.tooltip"));
        radix.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                                   boolean selected, boolean focused) {
                String key = "memory.radix." + value;
                return super.getListCellRendererComponent(list, Messages.text(key), index, selected, focused);
            }
        });
        radix.addActionListener(event -> {
            model.setRadix((MemoryRadix) radix.getSelectedItem());
            refreshFindAfterRadixChange();
        });
        toolbar.add(radix);
        toolbar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
    }

    private static JComponent groupSeparator() {
        JSeparator separator = new JSeparator(JSeparator.VERTICAL);
        separator.setPreferredSize(new Dimension(8, 24));
        return separator;
    }

    private JButton findButton() {
        JButton button = new JButton(Messages.text("memory.find"), Icons.get(FindIcons.FIND, Icons.MENU_SIZE));
        styleToolbarButton(button, "memory.find", "memory.find");
        button.addActionListener(event -> openFind());
        return button;
    }

    private static Icon linkIcon() {
        return Icons.get(MemoryIcons.of("memory.link"), Icons.MENU_SIZE);
    }

    private static void styleToolbarButton(JButton button, String name, String key) {
        button.setName(name);
        button.setToolTipText(Messages.text(key + ".tooltip"));
        button.setFocusable(false);
        button.setMargin(new Insets(4, 10, 4, 10));
        button.setIconTextGap(6);
    }

    private JButton toolbarButton(String name, String key, Runnable behavior) {
        JButton button = new JButton(Messages.text(key), Icons.get(MemoryIcons.of(name), Icons.MENU_SIZE));
        styleToolbarButton(button, name, key);
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
        linkText.setText(Messages.format("memory.linkBar", linkedFileName()));
        linkText.setDetail(fullLinkPath());
        externalButton.setEnabled(linked);
    }

    /** The link as the user reads it: relative to the circuit when it is saved. */
    private String shownLink() {
        return link.isEmpty() ? "" : MemoryLinkPaths.displayPath(link, document.path());
    }

    /** The file the memory is linked to, as an absolute path (what the tooltips show). */
    private String fullLinkPath() {
        if (link.isEmpty()) {
            return null;
        }
        return document.document().component(componentId)
                .flatMap(component -> MemoryLinkPaths.linkedFile(component, document.path()))
                .map(Path::toString).orElse(shownLink());
    }

    /** Just the file name of the link (the bars have little room; the tooltip has the whole path). */
    private String linkedFileName() {
        String full = fullLinkPath();
        if (full == null) {
            return "";
        }
        try {
            Path name = Path.of(full).getFileName();
            return name == null ? full : name.toString();
        } catch (java.nio.file.InvalidPathException unusable) {
            return full;
        }
    }

    /** «Enlazar…»: once linked, the grid shows the file's words (read-only). */
    private void linkFile() {
        if (model.canUndo() && !prompts.confirm(Messages.text("memory.link.discardEdits"))) {
            return;
        }
        linkActions.link(view, componentId);
        refreshLink();
        if (!link.isEmpty()) {
            document.document().component(componentId).ifPresent(component -> model.replaceContents(
                    MemoryContents.decode(String.valueOf(component.parameters().get(MemoryTypes.CONTENTS)))));
            model.resetEditedMarks();
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
        if (!prompts.confirm(Messages.format("memory.import.unlinkFirst", shownLink()))) {
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
        prompts.chooseSave(suggestedName(), "memory.export.title", this::linkedRefusal).ifPresent(choice -> {
            Path target = MemoryFileIo.withExtension(choice.path(), choice.format());
            Optional<String> refusal = linkedRefusal(target);
            if (refusal.isPresent()) {
                prompts.inform(refusal.get());
                return;
            }
            try {
                MemoryFileIo.write(target, choice.format(), new MemoryFile(header(), model.edited()), model.width());
            } catch (MemoryFileIo.Unreadable problem) {
                prompts.inform(problem.getMessage());
            }
        });
    }

    /** The scheme of the editor window the dialog opened from (the light one without a window, in tests). */
    private ColorScheme currentScheme() {
        return view == null ? ColorScheme.LIGHT : view.services().appearance().scheme();
    }

    void dumpToFile() {
        prompts.chooseSave(suggestedName(), "memory.dump.title", this::linkedRefusal).ifPresent(this::dumpTo);
    }

    /**
     * «Volcar»: writes the words the simulation holds now (the initial ones when it has none) with a header. Refuses
     * any file a memory of the document is linked to: VSim never writes those. The words come through a queued request; the file is written off the
     * EDT and the future completes when it is done (or the user was told why not).
     */
    CompletableFuture<Void> dumpTo(MemoryEditorPrompts.SaveChoice choice) {
        Path target = MemoryFileIo.withExtension(choice.path(), choice.format());
        Optional<String> refusal = linkedRefusal(target);
        if (refusal.isPresent()) {
            prompts.inform(refusal.get());
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

    /** Why {@code file} must not be written (a memory of the document is linked to it), when so. */
    private Optional<String> linkedRefusal(Path file) {
        return MemoryLinks.isLinkedByAny(document, file)
                ? Optional.of(Messages.format("memory.dump.linkedRefused", MemoryLinkPaths.displayPath(
                        MemoryLinkPaths.storedLinkPath(file.toAbsolutePath(), Optional.empty()), document.path())))
                : Optional.empty();
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

    /** Above this many words «Llenar…» asks before it fills. */
    static final long FILL_CONFIRM_WORDS = 1L << 16;

    private void askFill() {
        long[] range = selectedAddressRange();
        if (range == null) {
            range = visibleAddressRange();
        }
        prompts.askFill(Long.toHexString(range[0]).toUpperCase(), Long.toHexString(range[1]).toUpperCase())
                .ifPresent(request -> {
                    OptionalLong from = model.parseAddress(request.firstAddress());
                    OptionalLong to = model.parseAddress(request.lastAddress());
                    if (from.isEmpty() || to.isEmpty()) {
                        updateStatus(Optional.of(MemoryGridModel.OUT_OF_RANGE));
                        prompts.inform(Messages.text(MemoryGridModel.OUT_OF_RANGE));
                        return;
                    }
                    long count = to.getAsLong() - from.getAsLong() + 1;
                    if (count > FILL_CONFIRM_WORDS && !prompts.confirm(
                            Messages.format("memory.fill.large", String.valueOf(count)))) {
                        return;
                    }
                    Optional<String> refusal = model.fill(from.getAsLong(), to.getAsLong(), request.value());
                    updateStatus(refusal);
                    refusal.ifPresent(key -> prompts.inform(Messages.text(key)));
                });
    }

    /** The addresses of the rows on screen (the first rows when nothing is laid out yet): never the whole memory. */
    private long[] visibleAddressRange() {
        int rowCount = table.getRowCount();
        java.awt.Rectangle visible = table.getVisibleRect();
        int firstRow = 0;
        int lastRow = Math.min(rowCount, 16) - 1;
        if (visible.height > 0) {
            firstRow = Math.max(0, table.rowAtPoint(new java.awt.Point(0, visible.y)));
            int bottom = table.rowAtPoint(new java.awt.Point(0, visible.y + visible.height - 1));
            lastRow = bottom < 0 ? rowCount - 1 : bottom;
        }
        long first = (long) firstRow * model.columns();
        long last = Math.min(model.words() - 1, (long) (lastRow + 1) * model.columns() - 1);
        return new long[]{first, Math.max(first, last)};
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
            setStatus(Messages.text(refusalKey.get()), null);
            return;
        }
        long highest = model.edited().highestAddress();
        if (highest >= model.words()) {
            setStatus(Messages.format("memory.status.hidden", String.valueOf(model.words()),
                    Long.toHexString(model.words() - 1).toUpperCase()), null);
            return;
        }
        if (link.isEmpty()) {
            setStatus(" ", null);
        } else {
            setStatus(Messages.format("memory.status.linked", linkedFileName()), fullLinkPath());
            status.setIcon(linkIcon());
        }
    }

    private void setStatus(String text, String detail) {
        status.setIcon(null);
        status.setText(text);
        status.setDetail(detail);
    }

    /** For tests: the highlights' hold follows {@code nanoClock} instead of the system's clock. */
    void useActivityClock(LongSupplier nanoClock) {
        activity = new MemoryActivity(nanoClock);
    }

    /**
     * One frame of the live accesses, from a snapshot at {@code simulationTime}: the word read and the word written
     * since the frame before highlighted, each kept a little while (see {@link MemoryActivity}), and the status line
     * telling the last read and, on a RAM, the last write.
     */
    void showAccesses(VisibleState.MemoryState state, long simulationTime) {
        model.showAccesses(activity.lights(componentId.value(), state, simulationTime));
        List<String> parts = new ArrayList<>();
        if (state.lastRead().address() >= 0) {
            parts.add(Messages.format("memory.status.read", model.hexAddress(state.lastRead().address())));
        }
        if (kind == MemoryKind.RAM && state.lastWrite().address() >= 0) {
            parts.add(Messages.format("memory.status.write", model.hexAddress(state.lastWrite().address())));
        }
        if (parts.size() == 2) {
            setStatus(Messages.format("memory.status.accesses", parts.get(0), parts.get(1)), null);
        } else if (parts.size() == 1) {
            setStatus(parts.get(0), null);
        }
    }

    // Live values

    /**
     * One live refresh: the last access from the snapshot, and (for a RAM) the words through a queued request, shown
     * when the answer arrives on the EDT. While the simulation runs it asks every time; paused or stepping, once per
     * new snapshot revision. The future completes then. Skipped while the previous request is still pending, so
     * the engine is never flooded.
     */
    CompletableFuture<Void> pollLive() {
        SimulationSnapshot snapshot = document.latestSnapshot();
        if (!(snapshot.componentState(componentId.value()) instanceof VisibleState.MemoryState state)) {
            model.hideLive();
            model.showAccesses(MemoryLights.IDLE);
            updateStatus(Optional.empty());
            return CompletableFuture.completedFuture(null);
        }
        showAccesses(state, snapshot.time());
        if (kind != MemoryKind.RAM || livePending
                || (!snapshot.running() && snapshot.revision() == liveRevision)) {
            return CompletableFuture.completedFuture(null);
        }
        liveRevision = snapshot.revision();
        livePending = true;
        CompletableFuture<Void> done = new CompletableFuture<>();
        document.requestMemoryContents(componentId).whenComplete((contents, failure) ->
                SwingUtilities.invokeLater(() -> {
                    livePending = false;
                    if (contents != null) {
                        contents.ifPresent(model::showLive);
                        if (findBar.isVisible()) {
                            refreshFindCount();
                        }
                    }
                    done.complete(null);
                }));
        return done;
    }

    // Find

    private void buildFindBar() {
        findBar.setName("memory.findBar");
        findBar.setVisible(false);
        findBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        findField.setName("memory.find.field");
        ((AbstractDocument) findField.getDocument()).setDocumentFilter(new MemoryDigitFilter(model));
        findField.onTextChange(this::findTextChanged);
        findField.setColumns(16);
        JButton previous = findIconButton(FindIcons.PREVIOUS, "memory.find.previous", this::findPrevious);
        JButton next = findIconButton(FindIcons.NEXT, "memory.find.next", this::findNext);
        JButton closeButton = findIconButton(FindIcons.CLOSE, "memory.find.close", this::closeFind);
        findCounter.setName("memory.find.counter");
        findCounter.setPreferredSize(new Dimension(
                findCounter.getFontMetrics(findCounter.getFont()).stringWidth(
                        Messages.format("memory.find.counter", 9999, 9999)) + 12, 20));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(findField);
        left.add(findCounter);
        left.add(previous);
        left.add(next);
        findBar.add(left, BorderLayout.CENTER);
        findBar.add(closeButton, BorderLayout.EAST);
        Keys.bindWhenFocused(findField, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "memory.find.next",
                this::findNext);
        Keys.bindWhenFocused(findField, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                "memory.find.previous", this::findPrevious);
        Keys.bindWhenFocused(findField, KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "memory.find.next", this::findNext);
        Keys.bindWhenFocused(findField, KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK),
                "memory.find.previous", this::findPrevious);
        Keys.bindWhenFocused(findField, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "memory.find.close",
                this::closeFind);
    }

    private static JButton findIconButton(String icon, String key, Runnable behavior) {
        JButton button = new JButton(Icons.get(icon, Icons.MENU_SIZE));
        button.setName(key);
        button.setToolTipText(Messages.text(key));
        button.getAccessibleContext().setAccessibleName(Messages.text(key));
        button.setFocusable(false);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.addActionListener(event -> behavior.run());
        return button;
    }

    /** Ctrl+F / the toolbar's «Buscar»: shows the bar and puts the cursor in its field. */
    void openFind() {
        findBar.setVisible(true);
        content.revalidate();
        findField.requestFocusInWindow();
        findField.selectAll();
        if (!findField.getText().isEmpty()) {
            findTextChanged();
        }
    }

    /** Esc / the close button: hides the bar and gives the grid the keys back. */
    void closeFind() {
        findBar.setVisible(false);
        content.revalidate();
        table.requestFocusInWindow();
    }

    private void findNextOpeningTheBar() {
        boolean wasOpen = findBar.isVisible();
        openFind();
        if (wasOpen) {
            findNext();
        }
    }

    private void findPreviousOpeningTheBar() {
        boolean wasOpen = findBar.isVisible();
        openFind();
        if (wasOpen) {
            findPrevious();
        }
    }

    /** The address of the selected value cell, -1 when none is selected. */
    long selectedAddress() {
        int row = table.getSelectedRow();
        int column = table.getSelectedColumn();
        return row < 0 || column < 0 ? -1 : model.addressAt(row, column).orElse(-1);
    }

    /** The search for the typed value over the words shown now (rebuilt only when either changed), null when none. */
    private MemorySearch currentSearch() {
        OptionalLong value = model.parseSearch(findField.getText());
        if (value.isEmpty()) {
            search = null;
            searchValue = OptionalLong.empty();
            return null;
        }
        if (search == null || !value.equals(searchValue) || searchVersion != model.dataVersion()) {
            search = model.search(value.getAsLong());
            searchValue = value;
            searchVersion = model.dataVersion();
        }
        return search;
    }

    /** Typing: go to the first match from the selected cell on. */
    private void findTextChanged() {
        long selected = selectedAddress();
        step(true, selected < 0 ? -1 : selected - 1);
    }

    void findNext() {
        step(true, selectedAddress());
    }

    void findPrevious() {
        long selected = selectedAddress();
        step(false, selected < 0 ? model.words() : selected);
    }

    private void step(boolean forward, long from) {
        MemorySearch current = currentSearch();
        showFindCounter(current);
        if (current == null) {
            return;
        }
        OptionalLong found = forward ? current.next(from) : current.previous(from);
        if (found.isPresent()) {
            goTo(found.getAsLong());
            showFindCounter(current);
        }
    }

    /** Counts again after the words changed (live values, edits) without moving the selection. */
    private void refreshFindCount() {
        if (findBar.isVisible() && countedVersion != model.dataVersion()) {
            showFindCounter(currentSearch());
        }
    }

    private void refreshFindAfterRadixChange() {
        findField.setText(findField.getText());
        if (findBar.isVisible()) {
            showFindCounter(currentSearch());
        }
    }

    private void showFindCounter(MemorySearch current) {
        countedVersion = model.dataVersion();
        boolean invalid = current == null && !findField.getText().isBlank();
        findField.showError(invalid);
        if (current == null) {
            findCounter.setText(invalid ? Messages.text("memory.find.invalid") : "");
            return;
        }
        long count = current.count();
        long rank = current.rank(selectedAddress());
        findCounter.setText(count == 0 ? Messages.text("memory.find.none")
                : rank > 0 ? Messages.format("memory.find.counter", rank, count)
                : Messages.format("memory.find.total", count));
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
        dialog.setSize(defaultSize());
        dialog.setLocationRelativeTo(parent);
        address.ifPresent(this::goTo);
        liveTimer.start();
        pollLive();
        dialog.setVisible(true);
    }
}
