package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.memory.MemoryContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads what the AI may know of a memory's words, from the circuit or from the running simulation. */
public final class MemoryReader {

    /** The most words one read returns. */
    public static final int MAXIMUM_COUNT = 256;

    /** A memory of the circuit: its initial words and its shape. */
    public record Described(String component, MemoryContents initial, int width, long totalWords, boolean linked,
                            ReadSource source) {
    }

    private MemoryReader() {
    }

    /** The memory {@code componentId} of the circuit; INVALID_REFERENCE when it is not a RAM or ROM. */
    public static Described describe(ReadSource source, String componentId) {
        Component component = source.document().component(idOf(componentId)).orElseThrow(() ->
                new AutomationException(AutomationError.INVALID_REFERENCE, "There is no part " + componentId
                        + "; read the circuit again"));
        if (MemoryTypes.kindOf(component.typeId()).isEmpty()) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE, componentId + " is a "
                    + component.typeId() + ", not a ram or rom");
        }
        ComponentType type = source.registry().typeOf(component);
        Map<String, Object> resolved = type.resolveParameters(component.parameters());
        return new Described(componentId, MemoryContents.decode((String) resolved.get(MemoryTypes.CONTENTS)),
                ((Number) resolved.get(MemoryTypes.WIDTH)).intValue(), MemoryTypes.words(resolved),
                !((String) resolved.get(MemoryTypes.LINK)).isBlank(), source);
    }

    /** {@code count} words of {@code contents} from {@code firstAddress}, cut at the memory's last word. */
    public static MemoryView window(Described memory, MemoryContents contents, String sourceName, long firstAddress,
                                    int count) {
        if (count < 1 || count > MAXIMUM_COUNT) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "count is between 1 and "
                    + MAXIMUM_COUNT, Map.of("maximum", MAXIMUM_COUNT));
        }
        if (firstAddress < 0 || firstAddress >= memory.totalWords()) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "firstAddress " + firstAddress
                    + " is outside the memory: it has " + memory.totalWords() + " words (0.."
                    + (memory.totalWords() - 1) + ")");
        }
        long last = Math.min(memory.totalWords(), firstAddress + count);
        int digits = (memory.width() + 3) / 4;
        List<String> words = new ArrayList<>();
        for (long address = firstAddress; address < last; address++) {
            words.add(hex(contents.word(address) & mask(memory.width()), digits));
        }
        return new MemoryView(memory.source().id(), memory.source().version(), memory.component(), sourceName,
                firstAddress, words, memory.width(), memory.totalWords(), memory.linked(), contents.count());
    }

    private static long mask(int width) {
        return width >= Long.SIZE ? -1L : (1L << width) - 1;
    }

    private static String hex(long value, int digits) {
        StringBuilder text = new StringBuilder(Long.toHexString(value).toUpperCase());
        while (text.length() < digits) {
            text.insert(0, '0');
        }
        return text.toString();
    }

    private static ElementId idOf(String value) {
        try {
            return new ElementId(value);
        } catch (IllegalArgumentException invalid) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE, "There is no part " + value);
        }
    }
}
