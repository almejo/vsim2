package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.core.component.standard.BitGroups;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The summary under «Configuración avanzada…» in Propiedades (spec §2.3): «2 pines de 4 bits» for equal pins («2
 * pines de 7 bits (el último de 1)» when the width is not a multiple of the bits per pin); for
 * custom groups «Personalizado, 3 pines:», one line per pin («1: 7-11,25-31 (12 bits)», shown with the pin's colour)
 * and the unused bits («Sin usar: 0-6,12-14»).
 *
 * @param heading   the first line
 * @param pinLines  one line per pin, in pin order; none for equal pins
 * @param unused    the unused bits' line, when some bit is unused
 */
record GroupsSummary(String heading, List<String> pinLines, Optional<String> unused) {

    GroupsSummary {
        pinLines = List.copyOf(pinLines);
    }

    static GroupsSummary of(BitGroups groups) {
        int size = groups.uniformSize();
        if (size > 0) {
            int remainder = groups.lastPinRemainder();
            String heading = remainder > 0
                    ? Messages.format("groups.summary.uniformWithLastPin", groups.pinCount(), size, remainder)
                    : Messages.format("groups.summary.uniform", groups.pinCount(), size);
            return new GroupsSummary(heading, List.of(), Optional.empty());
        }
        List<String> pinLines = IntStream.range(0, groups.pinCount())
                .mapToObj(pin -> Messages.format("groups.summary.pin", pin + 1, groups.label(pin),
                        groups.widthOf(pin)))
                .toList();
        List<Integer> unusedBits = groups.unusedBits();
        Optional<String> unused = unusedBits.isEmpty() ? Optional.empty()
                : Optional.of(Messages.format("groups.summary.unused", BitGroups.label(unusedBits)));
        return new GroupsSummary(Messages.format("groups.summary.custom", groups.pinCount()), pinLines, unused);
    }
}
