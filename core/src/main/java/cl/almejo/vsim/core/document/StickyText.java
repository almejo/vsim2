package cl.almejo.vsim.core.document;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Free text on a sticky note (one or more lines). */
public record StickyText(String text) implements StickyContent {

    public StickyText {
        text = Objects.requireNonNullElse(text, "");
    }

    @Override
    public String searchableText() {
        return text;
    }

    @Override
    public String firstLine() {
        return Arrays.stream(text.split("\n", -1)).map(String::strip).filter(line -> !line.isEmpty())
                .findFirst().orElse("");
    }

    /** Each non-empty line becomes an item, not done. */
    @Override
    public StickyChecklist toChecklist() {
        List<StickyItem> items = Arrays.stream(text.split("\n", -1)).filter(line -> !line.isBlank())
                .map(line -> new StickyItem(line, false)).toList();
        return new StickyChecklist(items);
    }

    @Override
    public StickyText toText() {
        return this;
    }
}
