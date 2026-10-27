package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.read.MemoryReader;

import java.util.List;
import java.util.Map;

/** Reading the words of a RAM or ROM; writing them is the edit operation SetMemoryContents. */
final class MemoryTools {

    static final String READ_MEMORY = "vsim_read_memory";

    private MemoryTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return List.of(readMemory(api));
    }

    private static Tool readMemory(AutomationApi api) {
        return new Tool(READ_MEMORY, "Read the words of a RAM or ROM",
                "Up to " + MemoryReader.MAXIMUM_COUNT + " words from firstAddress, as hex of the memory's width. "
                        + "source initial: what the circuit holds (a linked memory: its copy); current: what the "
                        + "running simulation holds now, writes included. To change the initial words use vsim_edit "
                        + "SetMemoryContents; linking, importing and dumping files are for the user.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("component", Schema.nonEmptyString("The RAM or ROM"))
                        .property("source", Schema.enumeration("Default initial", List.of("initial", "current")))
                        .property("firstAddress", Schema.integerFrom("Default 0", 0))
                        .property("count", Schema.integer("Default 16", 1, MemoryReader.MAXIMUM_COUNT))
                        .required("document", "component").build(),
                Common.versionedResult("A window of the memory")
                        .property("component", Schema.string("The memory read"))
                        .property("source", Schema.string("initial or current"))
                        .property("firstAddress", Schema.integer("Address of the first word"))
                        .property("words", Schema.array("Hex words from firstAddress", Schema.string("")))
                        .property("width", Schema.integer("Bits per word"))
                        .property("totalWords", Schema.integer("Words the memory has"))
                        .property("linked", Schema.bool("Linked to a file: SetMemoryContents is refused"))
                        .property("nonZeroWords", Schema.integer("Non-zero words in the whole memory"))
                        .required("words", "width", "totalWords").build(),
                Tool.Hints.READS,
                List.of(Map.of("document", "circuit-1", "component", "c7", "firstAddress", 0, "count", 8)),
                arguments -> Tool.Output.of(api.readMemory(arguments.document(), arguments.string("component"),
                        "current".equals(arguments.string("source", "initial")),
                        arguments.has("firstAddress") ? arguments.longValue("firstAddress") : 0,
                        arguments.integer("count", 16))));
    }
}
