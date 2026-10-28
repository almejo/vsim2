package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.memory.MemoryKind;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.EnumMap;
import java.util.Map;

/**
 * What a RAM or ROM is called everywhere the user sees it (its title bar on the canvas, the memory map's title, the
 * content editor's title and Volcar's file name): its label, or with no label its kind numbered per kind in placement
 * order, «RAM1», «RAM2», «ROM1» (memory spec §3; display only, never stored). A labelled memory still takes its
 * number, so naming one never renumbers the others.
 */
public final class MemoryNames {

    private MemoryNames() {
    }

    /** The name of {@code memory}, one of the components of {@code document}. */
    public static String displayName(CircuitDocument document, Component memory) {
        Numbering numbering = new Numbering();
        for (Component component : document.components()) {
            String name = numbering.nameOf(component);
            if (component.id().equals(memory.id())) {
                return name;
            }
        }
        return numbering.nameOf(memory);
    }

    /**
     * The names of the memories of a document walked in its order (what the canvas does when it paints every part):
     * each call counts the component when it is a memory and answers its name.
     */
    public static final class Numbering {

        private final Map<MemoryKind, Integer> placedByKind = new EnumMap<>(MemoryKind.class);

        /** The name of {@code component}, the next one of the walk; a component that is not a memory: its label. */
        public String nameOf(Component component) {
            return MemoryTypes.kindOf(component.typeId())
                    .map(kind -> named(component, placedByKind.merge(kind, 1, Integer::sum)))
                    .orElse(component.label());
        }

        private static String named(Component memory, int ordinal) {
            return memory.label().isBlank() ? Messages.componentName(memory.typeId()) + ordinal : memory.label();
        }
    }
}
