package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.i18n.Messages;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The names the circuits of one window show on their main tabs: two circuits with the same name (two «4-bit adder»
 * files of different folders, or two untitled copies of one example) must be told apart, as editors do with their
 * files: a file gets its folder («4-bit adder — clase1»), going up more levels while two still look alike, and an
 * untitled circuit is numbered from the second one («4-bit adder 2»).
 */
public final class TabTitles {

    /** A circuit as the tab strip names it: its display name and its file, when it has one. */
    public record Named(String name, Optional<Path> file) {
    }

    private TabTitles() {
    }

    /** The titles of {@code circuits}, in the same order; a name no other circuit has is kept as it is. */
    public static List<String> distinct(List<Named> circuits) {
        Map<String, List<Integer>> byName = new HashMap<>();
        for (int index = 0; index < circuits.size(); index++) {
            byName.computeIfAbsent(circuits.get(index).name(), name -> new ArrayList<>()).add(index);
        }
        String[] titles = new String[circuits.size()];
        for (List<Integer> sharing : byName.values()) {
            if (sharing.size() == 1) {
                int index = sharing.getFirst();
                titles[index] = circuits.get(index).name();
                continue;
            }
            titleTheSharers(circuits, sharing, titles);
        }
        return List.of(titles);
    }

    private static void titleTheSharers(List<Named> circuits, List<Integer> sharing, String[] titles) {
        List<Integer> withFile = new ArrayList<>();
        for (int position = 0; position < sharing.size(); position++) {
            int index = sharing.get(position);
            if (circuits.get(index).file().isPresent()) {
                withFile.add(index);
            } else {
                titles[index] = position == 0 ? circuits.get(index).name()
                        : Messages.format("tab.title.number", circuits.get(index).name(), position + 1);
            }
        }
        for (int levels = 1; ; levels++) {
            Map<String, Integer> seen = new HashMap<>();
            for (int index : withFile) {
                titles[index] = Messages.format("tab.title.folder", circuits.get(index).name(),
                        folders(circuits.get(index).file().orElseThrow(), levels));
                seen.merge(titles[index], 1, Integer::sum);
            }
            boolean allDifferent = seen.values().stream().allMatch(count -> count == 1);
            int shown = levels;
            boolean nothingLeftToAdd = withFile.stream().allMatch(
                    index -> depthOf(circuits.get(index).file().orElseThrow()) <= shown);
            if (allDifferent || nothingLeftToAdd) {
                return;
            }
        }
    }

    /** The last {@code levels} folders of {@code file}'s path, «clase1» or «uno/src». */
    private static String folders(Path file, int levels) {
        List<String> names = new ArrayList<>();
        Path folder = file.getParent();
        while (folder != null && folder.getFileName() != null && names.size() < levels) {
            names.addFirst(folder.getFileName().toString());
            folder = folder.getParent();
        }
        return String.join("/", names);
    }

    private static int depthOf(Path file) {
        Path folder = file.getParent();
        return folder == null ? 0 : folder.getNameCount();
    }
}
