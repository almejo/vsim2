package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The «Atajos de teclado» table (spec §5.2): one row per menu followed by its actions, filtered by the search text
 * (case and accents ignored), and the flow behind «Cambiar…»: capture, refusal of reserved strokes and of plain keys for menu actions, the conflict
 * question and its answers. Strokes are read live from {@link KeyBindings}, so a change made elsewhere shows on the
 * next repaint. EDT only.
 */
public final class ShortcutsTableModel extends AbstractTableModel {

    /** What happened to an offered stroke. */
    public enum Outcome { ASSIGNED, CONFLICT, REFUSED }

    static final int ACTION_COLUMN = 0;

    private sealed interface Row permits GroupRow, ActionRow {
    }

    private record GroupRow(MenuId group) implements Row {
    }

    private record ActionRow(ShortcutEntry entry) implements Row {
    }

    /** A stroke offered for {@code entry} that {@code conflict} holds, waiting for «Reasignar» / «Cancelar». */
    private record PendingReassign(ShortcutEntry entry, KeyStroke stroke, ShortcutEntry conflict) {
    }

    private final KeyBindings bindings;
    private List<Row> rows = List.of();
    private String filter = "";
    private String capturingId;
    private PendingReassign pending;
    private String message = "";

    public ShortcutsTableModel(KeyBindings bindings) {
        this.bindings = bindings;
        rebuild();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int column) {
        return Messages.text(column == ACTION_COLUMN ? "shortcuts.column.action" : "shortcuts.column.shortcut");
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public Object getValueAt(int row, int column) {
        return switch (rows.get(row)) {
            case GroupRow group -> column == ACTION_COLUMN ? groupName(group.group()) : "";
            case ActionRow action -> column == ACTION_COLUMN ? Messages.text(action.entry().nameKey())
                    : shortcutText(action.entry());
        };
    }

    public boolean isGroupRow(int row) {
        return rows.get(row) instanceof GroupRow;
    }

    public Optional<ShortcutEntry> entryAt(int row) {
        return row >= 0 && row < rows.size() && rows.get(row) instanceof ActionRow action
                ? Optional.of(action.entry()) : Optional.empty();
    }

    /** The row showing {@code id}, or -1 when the search hides it. */
    public int rowOf(String id) {
        for (int row = 0; row < rows.size(); row++) {
            if (rows.get(row) instanceof ActionRow action && action.entry().id().equals(id)) {
                return row;
            }
        }
        return -1;
    }

    /** A search, like the Quitar / Restaurar buttons, abandons a capture in progress. */
    public void setFilter(String text) {
        capturingId = null;
        filter = normalized(text.strip());
        rebuild();
        fireTableDataChanged();
    }

    /** «Cambiar…»: the row of {@code id} reads «Presiona la combinación…» until {@link #offer} or a cancel. */
    public void beginCapture(String id) {
        pending = null;
        message = "";
        capturingId = id;
        rowsChanged();
    }

    public boolean isCapturing() {
        return capturingId != null;
    }

    public void cancelCapture() {
        capturingId = null;
        rowsChanged();
    }

    /** The stroke pressed while capturing: assigned when free, refused when reserved, a question when taken. */
    public Outcome offer(KeyStroke stroke) {
        if (capturingId == null) {
            throw new IllegalStateException("no shortcut is waiting for a key");
        }
        ShortcutEntry entry = ShortcutCatalog.find(capturingId).orElseThrow();
        capturingId = null;
        Outcome outcome;
        if (KeyBindings.isReserved(stroke)) {
            message = Messages.format("shortcuts.reserved", KeyBindings.describe(stroke));
            outcome = Outcome.REFUSED;
        } else if (KeyBindings.needsModifier(entry, stroke)) {
            message = Messages.format("shortcuts.needsModifier", KeyBindings.describe(stroke));
            outcome = Outcome.REFUSED;
        } else {
            Optional<ShortcutEntry> conflict = bindings.conflictFor(stroke, entry.id());
            if (conflict.isPresent()) {
                pending = new PendingReassign(entry, stroke, conflict.get());
                message = Messages.format("shortcuts.conflict", KeyBindings.describe(stroke),
                        Messages.text(conflict.get().nameKey()));
                outcome = Outcome.CONFLICT;
            } else {
                bindings.assign(entry.id(), stroke);
                message = "";
                outcome = Outcome.ASSIGNED;
            }
        }
        rowsChanged();
        return outcome;
    }

    /** The action that holds the stroke waiting for «Reasignar» / «Cancelar». */
    public Optional<ShortcutEntry> pendingConflict() {
        return Optional.ofNullable(pending).map(PendingReassign::conflict);
    }

    /** «Reasignar»: the stroke moves; the other action is left without a shortcut. */
    public void confirmReassign() {
        PendingReassign confirmed = pending;
        settle(() -> {
            if (confirmed != null) {
                bindings.assign(confirmed.entry().id(), confirmed.stroke());
            }
        });
    }

    public void cancelReassign() {
        settle(() -> {
        });
    }

    public void clear(String id) {
        edit(() -> bindings.clear(id));
    }

    public void reset(String id) {
        edit(() -> bindings.reset(id));
    }

    public void resetAll() {
        edit(bindings::resetAll);
    }

    /** The text the tab shows under the table: a conflict question, a refusal, or empty. */
    public String message() {
        return message;
    }

    public static String groupName(MenuId group) {
        return Messages.text(switch (group) {
            case FILE -> "menu.file";
            case EDIT -> "menu.edit";
            case VIEW -> "menu.view";
            case SIMULATION -> "menu.simulation";
            case TOOLS -> "menu.tools";
            case WINDOW -> "menu.window";
            case HELP -> "menu.help";
        });
    }

    private String shortcutText(ShortcutEntry entry) {
        if (entry.id().equals(capturingId)) {
            return Messages.text("shortcuts.prompt");
        }
        return bindings.strokeFor(entry.id()).map(KeyBindings::describe).orElse("");
    }

    private void rebuild() {
        List<Row> result = new ArrayList<>();
        MenuId currentGroup = null;
        for (ShortcutEntry entry : ShortcutCatalog.entries()) {
            if (!matches(entry)) {
                continue;
            }
            if (entry.group() != currentGroup) {
                currentGroup = entry.group();
                result.add(new GroupRow(currentGroup));
            }
            result.add(new ActionRow(entry));
        }
        rows = List.copyOf(result);
    }

    private boolean matches(ShortcutEntry entry) {
        if (filter.isEmpty() || normalized(Messages.text(entry.nameKey())).contains(filter)) {
            return true;
        }
        return bindings.strokeFor(entry.id())
                .map(stroke -> normalized(KeyBindings.describe(stroke)).contains(filter))
                .orElse(false);
    }

    private static String normalized(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** A row button (Quitar, Restaurar, Restaurar todos): abandons a capture in progress, then {@link #settle}. */
    private void edit(Runnable change) {
        capturingId = null;
        settle(change);
    }

    /** Forgets the conflict question and the message, applies {@code change}, then repaints the rows. */
    private void settle(Runnable change) {
        pending = null;
        message = "";
        change.run();
        rowsChanged();
    }

    /** Values changed but not the rows: keeps the table's selection (a data-changed event would clear it). */
    private void rowsChanged() {
        if (!rows.isEmpty()) {
            fireTableRowsUpdated(0, rows.size() - 1);
        }
    }
}
