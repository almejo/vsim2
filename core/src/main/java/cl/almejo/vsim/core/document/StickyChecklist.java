package cl.almejo.vsim.core.document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** A checklist on a sticky note: items in order, each done or not. */
public record StickyChecklist(List<StickyItem> items) implements StickyContent {

    /** Prefix that marks a done item when the list is turned into free text. */
    public static final String DONE_PREFIX = "✓ ";

    public StickyChecklist {
        items = List.copyOf(items);
    }

    @Override
    public String searchableText() {
        return items.stream().map(StickyItem::text).collect(Collectors.joining("\n"));
    }

    @Override
    public String firstLine() {
        return items.stream().filter(item -> !item.done()).findFirst()
                .or(() -> items.stream().findFirst()).map(StickyItem::text).orElse("");
    }

    @Override
    public StickyChecklist toChecklist() {
        return this;
    }

    /** One line per item, done items prefixed with {@value #DONE_PREFIX}. */
    @Override
    public StickyText toText() {
        return new StickyText(items.stream().map(item -> (item.done() ? DONE_PREFIX : "") + item.text())
                .collect(Collectors.joining("\n")));
    }

    /**
     * The list after the user edited its lines: empty lines are dropped; a line equal to the next unmatched old item
     * (matched in order) keeps that item's done state, the others start not done.
     */
    public StickyChecklist edited(List<String> lines) {
        List<StickyItem> result = new ArrayList<>();
        int nextOld = 0;
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            int match = indexOfText(line, nextOld);
            if (match >= 0) {
                result.add(new StickyItem(line, items.get(match).done()));
                nextOld = match + 1;
            } else {
                result.add(new StickyItem(line, false));
            }
        }
        return new StickyChecklist(result);
    }

    private int indexOfText(String text, int fromIndex) {
        for (int index = fromIndex; index < items.size(); index++) {
            if (items.get(index).text().equals(text)) {
                return index;
            }
        }
        return -1;
    }

    /** The list with the done flag of item {@code index} flipped. */
    public StickyChecklist toggled(int index) {
        if (index < 0 || index >= items.size()) {
            throw new IllegalArgumentException("no item " + index + " in a list of " + items.size());
        }
        List<StickyItem> copy = new ArrayList<>(items);
        StickyItem item = copy.get(index);
        copy.set(index, new StickyItem(item.text(), !item.done()));
        return new StickyChecklist(copy);
    }

    public int doneCount() {
        return (int) items.stream().filter(StickyItem::done).count();
    }
}
