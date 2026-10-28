package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MemoryKind;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The «Memoria» parts (memory spec): {@link StandardComponentTypes#RAM} and {@link StandardComponentTypes#ROM}, with
 * their parameters. The contents live in the hidden text parameter {@link #CONTENTS} as {@link MemoryContents#encode()}
 * gives them, so undo, copy and paste, templates and files carry them like any other parameter; {@link #LINK} is the
 * linked file's path, shown only (the contents are its copy). Neither ever cuts words: contents beyond «Palabras» or
 * wider than «Ancho de palabra» stay in the parameter, the behaviour just does not address them.
 */
public final class MemoryTypes {

    /** «Palabras»: how many words, a power of two from 2 to 16 777 216 (1 … 24 address bits), as text. */
    public static final String WORDS = "words";
    /** «Ancho de palabra»: 1 … 64 bits. */
    public static final String WIDTH = StandardComponentTypes.WIDTH;
    /** «Conexión»: {@link #CONNECTION_BUS} or {@link #CONNECTION_PINS} (one pin per bit). */
    public static final String CONNECTION = "connection";
    public static final String CONNECTION_BUS = "bus";
    public static final String CONNECTION_PINS = "pins";
    /** «Datos» (RAM only): D and Q apart ({@link #DATA_SEPARATE}) or one bidirectional D ({@link #DATA_SHARED}). */
    public static final String DATA = "data";
    public static final String DATA_SEPARATE = "separate";
    public static final String DATA_SHARED = "shared";
    /** «Escritura» (RAM only): on the clock's falling edge ({@link #WRITE_CLOCK}) or while WE = 1 ({@link #WRITE_LEVEL}). */
    public static final String WRITE = "write";
    public static final String WRITE_CLOCK = "clock";
    public static final String WRITE_LEVEL = "level";
    /** «Mostrar contenido»: whether the part shows its content window; display only. */
    public static final String SHOW_CONTENTS = "show-contents";
    public static final String SHOW_CONTENTS_YES = "yes";
    public static final String SHOW_CONTENTS_NO = "no";
    /** The initial contents, hidden: {@link MemoryContents#encode()} text. */
    public static final String CONTENTS = "contents";
    /** The linked file's path ("" when not linked), hidden and display only. */
    public static final String LINK = "link";

    /** 24 address bits, like Logisim-evolution and Digital (memory spec §5). */
    public static final int MAXIMUM_ADDRESS_BITS = 24;
    public static final int MAXIMUM_WIDTH = Value.MAXIMUM_WIDTH;

    private static final ParameterSpec WORDS_PARAMETER = ParameterSpec.choice(WORDS, "256", wordChoices());
    private static final ParameterSpec WIDTH_PARAMETER = ParameterSpec.integer(WIDTH, 8, 1, MAXIMUM_WIDTH);
    private static final ParameterSpec CONNECTION_PARAMETER = ParameterSpec.choice(CONNECTION, CONNECTION_BUS,
            List.of(CONNECTION_BUS, CONNECTION_PINS));
    private static final ParameterSpec DATA_PARAMETER = ParameterSpec.choice(DATA, DATA_SEPARATE,
            List.of(DATA_SEPARATE, DATA_SHARED));
    private static final ParameterSpec WRITE_PARAMETER = ParameterSpec.choice(WRITE, WRITE_CLOCK,
            List.of(WRITE_CLOCK, WRITE_LEVEL));
    private static final ParameterSpec SHOW_CONTENTS_PARAMETER = ParameterSpec.choice(SHOW_CONTENTS,
            SHOW_CONTENTS_YES, List.of(SHOW_CONTENTS_YES, SHOW_CONTENTS_NO)).asDisplayOnly();
    private static final ParameterSpec CONTENTS_PARAMETER = ParameterSpec.text(CONTENTS, "",
            MemoryTypes::canonicalContents).asHidden();
    private static final ParameterSpec LINK_PARAMETER = ParameterSpec.text(LINK, "").asHidden().asDisplayOnly();

    /** The canonical text of the last few contents seen, so resolving a part's parameters (on every paint) stays cheap. */
    private static final int CANONICAL_CACHE_SIZE = 32;
    /** Longer texts (about 64 KB) go to a single slot: a few of them would hold megabytes for good. */
    private static final int CACHEABLE_LENGTH = 64 * 1024;
    private static volatile Map.Entry<String, String> lastLarge;
    private static final Map<String, String> CANONICAL_CACHE = new LinkedHashMap<>(CANONICAL_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CANONICAL_CACHE_SIZE;
        }
    };

    private MemoryTypes() {
    }

    static void register(ComponentTypeRegistry registry) {
        registry.register(memory(MemoryKind.RAM, List.of(WORDS_PARAMETER, WIDTH_PARAMETER, CONNECTION_PARAMETER,
                DATA_PARAMETER, WRITE_PARAMETER, SHOW_CONTENTS_PARAMETER, StandardComponentTypes.DELAY,
                CONTENTS_PARAMETER, LINK_PARAMETER)));
        registry.register(memory(MemoryKind.ROM, List.of(WORDS_PARAMETER, WIDTH_PARAMETER, CONNECTION_PARAMETER,
                SHOW_CONTENTS_PARAMETER, StandardComponentTypes.DELAY, CONTENTS_PARAMETER, LINK_PARAMETER)));
    }

    private static ComponentType memory(MemoryKind kind, List<ParameterSpec> parameters) {
        return ComponentType.parameterized(kind.id(), ComponentCategory.MEMORY, parameters,
                resolved -> MemoryPinout.of(kind, resolved).shape(),
                resolved -> new MemoryBehavior(MemoryPinout.of(kind, resolved), words(resolved),
                        (Integer) resolved.get("delay"), MemoryContents.decode((String) resolved.get(CONTENTS))));
    }

    /** The kind of memory {@code typeId} is, empty for every other type. */
    public static Optional<MemoryKind> kindOf(String typeId) {
        if (StandardComponentTypes.RAM.equals(typeId)) {
            return Optional.of(MemoryKind.RAM);
        }
        if (StandardComponentTypes.ROM.equals(typeId)) {
            return Optional.of(MemoryKind.ROM);
        }
        return Optional.empty();
    }

    /** «Palabras» of resolved parameters. */
    public static long words(Map<String, Object> resolved) {
        return Long.parseLong((String) resolved.get(WORDS));
    }

    /** The address width of resolved parameters: log2 of «Palabras». */
    public static int addressBits(Map<String, Object> resolved) {
        return Long.numberOfTrailingZeros(words(resolved));
    }

    /** «2», «4» … «16777216». */
    private static List<String> wordChoices() {
        List<String> choices = new ArrayList<>();
        for (int bits = 1; bits <= MAXIMUM_ADDRESS_BITS; bits++) {
            choices.add(String.valueOf(1L << bits));
        }
        return choices;
    }

    /** The normalizer of {@link #CONTENTS}: any readable contents text to its canonical form. */
    private static String canonicalContents(String text) {
        if (text.length() > CACHEABLE_LENGTH) {
            // one slot only: the same big text is resolved on every paint, but a few of them must not pile up
            Map.Entry<String, String> last = lastLarge;
            if (last != null && last.getKey().equals(text)) {
                return last.getValue();
            }
            String canonical = MemoryContents.decode(text).encode();
            lastLarge = Map.entry(text, canonical);
            return canonical;
        }
        synchronized (CANONICAL_CACHE) {
            String known = CANONICAL_CACHE.get(text);
            if (known != null) {
                return known;
            }
        }
        String canonical = MemoryContents.decode(text).encode();
        synchronized (CANONICAL_CACHE) {
            CANONICAL_CACHE.put(text, canonical);
        }
        return canonical;
    }
}
