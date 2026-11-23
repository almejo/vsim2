package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.ui.waveform.PinToggle;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The tests of a machine in «Modo prueba» (hito 4, task 5), where the wave strip goes: a tab per test (✓ or ✗ in its
 * title and an «×» that deletes it; double click renames it, its menu duplicates or deletes it; a «+» tab after the last
 * one adds a test, as in a browser), a summary line, and a grid shaped like the wave strip
 * ({@link MachineTestTableGrid}): the inputs you give, the state, each output with what you expected of it, and ✓ or
 * ✗, edge by edge; a «+» after its last column adds a column.
 * <p>
 * It only reads and edits through its {@link MachineTestSession}: every change is one named step of the test mode's
 * draft ({@link MachineTestSession#editTests}), and choosing a test or a column is no step. While the machine has no
 * test it shows an empty «Prueba 1» that is written only when something in it changes (decision 8).
 * <p>
 * Install it in the bottom dock with {@code BottomDock.contentInstead(table)}, and call {@link #close()} when the mode
 * ends so it stops following the session.
 */
public final class MachineTestTable extends JPanel {

    private final transient MachineTestSession session;
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final PinToggle pin = new PinToggle();
    private final JLabel summary = new JLabel();
    /** The grey line beside the summary; its colour is the theme's muted text, read each time it is painted. */
    private final JLabel hint = new JLabel(Messages.text("machine.test.hint")) {
        @Override
        public Color getForeground() {
            return ReadableColors.mutedText();
        }
    };
    private final MachineTestTableGrid grid;
    /** The grid's scroll pane, whose steps are a column and a row. */
    private JScrollPane scrollBars;
    private final transient Runnable stopListening;
    /** True while {@link #refreshTabs} sets the tabs, so their change is not taken for the user's choice. */
    private boolean updatingTabs;

    /**
     * A table of {@code session}'s tests, painted with {@code scheme}'s colours read on every paint; it follows the
     * session until {@link #close()}.
     */
    public MachineTestTable(MachineTestSession session, Supplier<ColorScheme> scheme) {
        super(new BorderLayout());
        this.session = session;
        this.grid = new MachineTestTableGrid(this, scheme, currentModel());
        getAccessibleContext().setAccessibleName(Messages.text("machine.test.table.name"));
        add(top(), BorderLayout.NORTH);
        add(scrolled(), BorderLayout.CENTER);
        stopListening = session.addListener(this::refresh);
        refresh();
    }

    /** Stops following the session (the test mode ended). */
    public void close() {
        stopListening.run();
    }

    private JComponent top() {
        tabs.putClientProperty("JTabbedPane.showContentSeparator", false);
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, true);
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSE_TOOLTIPTEXT,
                Messages.text("machine.test.tab.close"));
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSE_CALLBACK,
                (BiConsumer<JTabbedPane, Integer>) (pane, index) -> deleteTest(index));
        tabs.addChangeListener(event -> tabChosen());
        tabs.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                tabPopupIfTrigger(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                tabPopupIfTrigger(event);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                int index = tabs.indexAtLocation(event.getX(), event.getY());
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2 && isTestTab(index)) {
                    startRenaming(index);
                }
            }
        });
        // the verdict is the useful part: the grey hint gives way first, both cut with «…» and whole in their tooltip
        JPanel line = new JPanel(new MigLayout("insets 2lp 6lp 2lp 6lp, gap 12lp",
                "[shrinkprio 50][grow, fill, shrinkprio 200]", "[center]"));
        line.add(summary, "wmin 0");
        line.add(hint, "wmin 0");

        JPanel top = new JPanel(new BorderLayout());
        JPanel tabRow = new JPanel(new BorderLayout());
        tabRow.add(tabs, BorderLayout.CENTER);
        tabRow.add(pin, BorderLayout.EAST); // shown only in presentation mode
        top.add(tabRow, BorderLayout.NORTH);
        top.add(line, BorderLayout.SOUTH);
        return top;
    }

    private JComponent scrolled() {
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setRowHeaderView(grid.names());
        scroll.setColumnHeaderView(grid.header());
        scroll.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, grid.corner());
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scrollBars = scroll;
        return scroll;
    }

    private MachineTestTableModel currentModel() {
        return MachineTestTableModel.of(session.machine(), session.chosenTest(), session.chosenColumn());
    }

    /** Runs the tests again and shows them: after every change of the draft, its tests or the choices. */
    private void refresh() {
        MachineTestTableModel model = MachineTestTableModel.of(session.machine(), session.chosenTest(),
                session.chosenColumn(), grid.model());
        grid.setModel(model);
        scrollBars.getHorizontalScrollBar().setUnitIncrement(grid.model().layout().columnWidth());
        scrollBars.getVerticalScrollBar().setUnitIncrement(grid.model().layout().rowHeight());
        refreshTabs(grid.model());
        summary.setText(model.summary());
        summary.setToolTipText(summary.getText());
        hint.setText(Messages.text(model.unwritten() ? "machine.test.hint.first" : "machine.test.hint"));
        hint.setToolTipText(hint.getText()); // a narrow window cuts it with «…»: the whole line is here
    }

    private void refreshTabs(MachineTestTableModel model) {
        updatingTabs = true;
        try {
            int count = model.tests().size();
            while (tabs.getTabCount() > count + 1) {
                tabs.removeTabAt(tabs.getTabCount() - 1);
            }
            while (tabs.getTabCount() < count + 1) {
                tabs.addTab("", emptyPage(false));
            }
            for (int index = 0; index < count; index++) {
                tabs.setTitleAt(index, model.tabTitle(index));
                tabs.setToolTipTextAt(index, null);
                // the untouched «Prueba 1» has nothing to delete: no «×»
                closable(index, !model.unwritten());
            }
            // the «+» after the last test: a tab that is never chosen, it adds one (grey when no more fit)
            closable(count, false);
            tabs.setTitleAt(count, Messages.text("machine.test.tab.new"));
            tabs.setEnabledAt(count, model.canAddTest());
            tabs.setToolTipTextAt(count, model.canAddTest() ? Messages.text("machine.test.tab.newTip")
                    : Messages.format("machine.test.tab.full", MachineTests.MAXIMUM_TESTS));
            tabs.setSelectedIndex(model.testIndex());
        } finally {
            updatingTabs = false;
        }
    }

    private static JPanel emptyPage(boolean closable) {
        JPanel nothing = new JPanel();
        nothing.setPreferredSize(new Dimension(0, 0));
        nothing.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, closable);
        return nothing;
    }

    /** Whether tab {@code index} shows the «×» (FlatLaf reads it from the tab's page). */
    private void closable(int index, boolean closable) {
        ((JComponent) tabs.getComponentAt(index)).putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE,
                closable);
    }

    /** Whether tab {@code index} is a test's tab (not the «+» after them, and not none). */
    private boolean isTestTab(int index) {
        return index >= 0 && index < grid.model().tests().size();
    }

    private void tabChosen() {
        int index = tabs.getSelectedIndex();
        if (updatingTabs) {
            return;
        }
        if (index >= 0 && index == grid.model().tests().size()) {
            // the «+»: never stays chosen; the new test's tab does
            updatingTabs = true;
            try {
                tabs.setSelectedIndex(grid.model().testIndex());
            } finally {
                updatingTabs = false;
            }
            addTest();
            return;
        }
        if (index >= 0 && index != grid.model().testIndex()) {
            session.chooseTest(index);
            int last = grid.model().columnCount() - 1;
            if (session.chosenColumn() > last) {
                session.chooseColumn(last);
            }
        }
    }

    private void tabPopupIfTrigger(MouseEvent event) {
        int index = tabs.indexAtLocation(event.getX(), event.getY());
        if (event.isPopupTrigger() && isTestTab(index) && tabs.isShowing()) {
            tabMenu(index).show(tabs, event.getX(), event.getY());
        }
    }

    MachineTestTableGrid grid() {
        return grid;
    }

    JComponent header() {
        return grid.header();
    }

    JTabbedPane tabs() {
        return tabs;
    }

    /**
     * The pin of presentation mode, as the wave strip's: shown only while the mode is on (the mode wires it with
     * {@code WaveformFeatures.addPin}).
     */
    public PinToggle pinButton() {
        return pin;
    }

    /** The grey line beside the summary. */
    JLabel hint() {
        return hint;
    }

    /** The verdict line («2 ✓, 1 ✗ en el flanco 6 salió Z = 0»). */
    JLabel summary() {
        return summary;
    }

    String summaryText() {
        return summary.getText();
    }

    // ---- choices (no step) ----

    void chooseColumn(int column) {
        if (column != session.chosenColumn()) {
            session.chooseColumn(column);
        }
    }

    // ---- cells (one step each) ----

    /** A click: an input toggles, an «esperado» cell goes · → 0 → 1 → ·. */
    void toggle(MachineTestTableModel.Row row, int column) {
        String pinId = row.pinId().orElseThrow();
        if (row.kind() == MachineTestTableModel.Kind.INPUT) {
            editChosen(test -> MachineTests.withInput(test, column, pinId, !test.columns().get(column).input(pinId)),
                    "machine.test.step.input");
        } else if (row.kind() == MachineTestTableModel.Kind.EXPECTED) {
            editChosen(test -> MachineTests.withExpected(test, column, pinId,
                    test.columns().get(column).expected(pinId).next()), "machine.test.step.expected");
        }
    }

    /** 0 or 1 typed: every cell of {@code block} takes it. */
    void fill(MachineTestCellPainter.Block block, boolean value) {
        editCells(block, (test, row, column) -> row.kind() == MachineTestTableModel.Kind.INPUT
                ? MachineTests.withInput(test, column, row.pinId().orElseThrow(), value)
                : MachineTests.withExpected(test, column, row.pinId().orElseThrow(),
                value ? LogicValue.ONE : LogicValue.ZERO));
    }

    /** Espacio: every cell of {@code block} changes as a click would change it. */
    void advance(MachineTestCellPainter.Block block) {
        editCells(block, (test, row, column) -> {
            String pinId = row.pinId().orElseThrow();
            return row.kind() == MachineTestTableModel.Kind.INPUT
                    ? MachineTests.withInput(test, column, pinId, !test.columns().get(column).input(pinId))
                    : MachineTests.withExpected(test, column, pinId,
                    test.columns().get(column).expected(pinId).next());
        });
    }

    /** Supr: the «esperado» cells of {@code block} become «no importa»; an input has no such value. */
    void clearExpected(MachineTestCellPainter.Block block) {
        editCells(block, (test, row, column) -> row.kind() == MachineTestTableModel.Kind.EXPECTED
                ? MachineTests.withExpected(test, column, row.pinId().orElseThrow(), LogicValue.DONT_CARE)
                : test);
    }

    /** What a key does to one editable cell. */
    private interface CellEdit {
        MachineTest apply(MachineTest test, MachineTestTableModel.Row row, int column);
    }

    private void editCells(MachineTestCellPainter.Block block, CellEdit edit) {
        List<MachineTestTableModel.Row> rows = grid.model().rows();
        editChosen(test -> {
            MachineTest edited = test;
            int lastColumn = Math.min(block.lastColumn(), test.columns().size() - 1);
            for (int row = Math.max(0, block.firstRow()); row <= block.lastRow() && row < rows.size(); row++) {
                if (!rows.get(row).kind().editable()) {
                    continue;
                }
                for (int column = Math.max(0, block.firstColumn()); column <= lastColumn; column++) {
                    edited = edit.apply(edited, rows.get(row), column);
                }
            }
            return edited;
        }, "machine.test.step.fill");
    }

    // ---- columns ----

    /** The «+» after the last column: a column at the end holding the last column's inputs. */
    void addColumn() {
        if (grid.model().canAddColumn()) {
            editChosen(test -> MachineTests.withColumnInserted(test, test.columns().size()),
                    "machine.test.step.addColumn");
        }
    }

    void insertColumnBefore(int column) {
        if (grid.model().canAddColumn()) {
            editChosen(test -> MachineTests.withColumnInserted(test, column), "machine.test.step.insertColumn");
        }
    }

    void deleteColumn(int column) {
        if (!grid.model().canDeleteColumn()) {
            return;
        }
        editChosen(test -> MachineTests.withColumnRemoved(test, column), "machine.test.step.deleteColumn");
        int last = grid.model().columnCount() - 1;
        if (session.chosenColumn() > last) {
            session.chooseColumn(last);
        }
    }

    /** The menu of a column's header or cells: «Insertar columna antes», «Borrar columna». */
    JPopupMenu columnMenu(int column) {
        MachineTestTableModel model = grid.model();
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("machine.test.column.insertBefore", () -> insertColumnBefore(column), model.canAddColumn(),
                Messages.format("machine.test.column.full", MachineTests.MAXIMUM_COLUMNS)));
        menu.add(item("machine.test.column.delete", () -> deleteColumn(column), model.canDeleteColumn(),
                Messages.text("machine.test.column.onlyOne")));
        return menu;
    }

    // ---- tests ----

    /** The «+» tab: an empty test of 8 columns after the others, chosen. */
    void addTest() {
        if (!grid.model().canAddTest()) {
            return;
        }
        editTests(tests -> {
            List<MachineTest> added = new ArrayList<>(tests);
            added.add(MachineTest.empty(MachineTests.freeId(tests), MachineTestTableModel.defaultName(tests),
                    MachineTests.DEFAULT_COLUMNS));
            return added;
        }, "machine.test.step.addTest");
        session.chooseTest(session.machine().tests().size() - 1);
    }

    void renameTest(int index, String name) {
        renameTestWithId(grid.model().tests().get(index).id(), name);
    }

    /**
     * Renames the test {@code testId} wherever it is now; nothing when it is gone (an undo took it away while its tab
     * was being renamed, review finding 6) or {@code name} is blank.
     */
    private void renameTestWithId(String testId, String name) {
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        editTests(tests -> tests.stream()
                .map(test -> test.id().equals(testId) ? test.withName(trimmed) : test).toList(),
                "machine.test.step.renameTest");
    }

    void duplicateTest(int index) {
        if (!grid.model().canAddTest()) {
            return;
        }
        editTests(tests -> {
            List<MachineTest> copied = new ArrayList<>(tests);
            MachineTest original = tests.get(index);
            copied.add(index + 1, original.withId(MachineTests.freeId(tests))
                    .withName(Messages.format("machine.test.copyName", original.name())));
            return copied;
        }, "machine.test.step.duplicateTest");
        session.chooseTest(index + 1);
    }

    void deleteTest(int index) {
        if (grid.model().unwritten() || !isTestTab(index)) {
            return;
        }
        int chosen = session.chosenTest();
        editTests(tests -> {
            List<MachineTest> kept = new ArrayList<>(tests);
            kept.remove(index);
            return kept;
        }, "machine.test.step.deleteTest");
        int remaining = Math.max(1, session.machine().tests().size());
        int next = index < chosen ? chosen - 1 : chosen;
        session.chooseTest(Math.max(0, Math.min(next, remaining - 1)));
    }

    /** The menu of a test's tab: «Renombrar», «Duplicar», «Borrar». */
    JPopupMenu tabMenu(int index) {
        MachineTestTableModel model = grid.model();
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("machine.test.menu.rename", () -> startRenaming(index), true, ""));
        menu.add(item("machine.test.menu.duplicate", () -> duplicateTest(index), model.canAddTest(),
                Messages.format("machine.test.tab.full", MachineTests.MAXIMUM_TESTS)));
        menu.add(item("machine.test.menu.delete", () -> deleteTest(index), !model.unwritten(),
                Messages.text("machine.test.menu.deleteEmpty")));
        return menu;
    }

    /**
     * Puts a text field on the tab of test {@code index}: Intro or leaving it renames the test (one step), Escape
     * leaves the name as it was. The name goes to that test wherever it is by then, and nowhere when it is gone.
     */
    JTextField startRenaming(int index) {
        MachineTest renamed = grid.model().tests().get(index);
        JTextField field = new JTextField(renamed.name(), 12);
        boolean[] finished = {false};
        Runnable commit = () -> {
            if (!finished[0]) {
                finished[0] = true;
                closeEditor(field);
                renameTestWithId(renamed.id(), field.getText());
            }
        };
        field.addActionListener(event -> commit.run());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                commit.run();
            }
        });
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                "cancelRename");
        field.getActionMap().put("cancelRename", Actions.unnamed(() -> {
            finished[0] = true;
            closeEditor(field);
        }));
        tabs.setTabComponentAt(index, field);
        field.selectAll();
        field.requestFocusInWindow();
        return field;
    }

    /** Takes {@code field} off whichever tab holds it now (none, when its tab went). */
    private void closeEditor(JTextField field) {
        for (int index = 0; index < tabs.getTabCount(); index++) {
            if (tabs.getTabComponentAt(index) == field) {
                tabs.setTabComponentAt(index, null);
            }
        }
    }

    // ---- edits ----

    private void editChosen(UnaryOperator<MachineTest> change, String stepKey) {
        int index = grid.model().testIndex();
        editTests(tests -> replaced(tests, index, change), stepKey);
    }

    /**
     * One step of the draft: {@code edit} applied to the tests shown (the unwritten «Prueba 1» while there are none);
     * nothing when it changes nothing, so an untouched empty test is never written.
     */
    private void editTests(UnaryOperator<List<MachineTest>> edit, String stepKey) {
        List<MachineTest> shown = MachineTestTableModel.shown(session.machine().tests());
        if (edit.apply(shown).equals(shown)) {
            return;
        }
        session.editTests(current -> edit.apply(MachineTestTableModel.shown(current)), Messages.text(stepKey));
    }

    private static List<MachineTest> replaced(List<MachineTest> tests, int index, UnaryOperator<MachineTest> change) {
        List<MachineTest> edited = new ArrayList<>(tests);
        edited.set(index, change.apply(tests.get(index)));
        return edited;
    }

    private static JMenuItem item(String key, Runnable behavior, boolean enabled, String reason) {
        JMenuItem item = new JMenuItem(Messages.text(key));
        item.addActionListener(event -> behavior.run());
        item.setEnabled(enabled);
        if (!enabled && !reason.isEmpty()) {
            item.setToolTipText(reason);
        }
        return item;
    }
}
