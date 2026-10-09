package cl.almejo.vsim.core.simulation;

import java.util.List;

/** Ids of simulated components inside sub-circuits: {@code instance/child/grandchild} (spec §5.6). */
public final class InstancePath {

    public static final String SEPARATOR = "/";

    private InstancePath() {
    }

    public static String child(String parentId, String childId) {
        return parentId + SEPARATOR + childId;
    }

    /** The id of the document component that contains {@code id} (itself when it is not nested). */
    public static String topLevel(String id) {
        int index = id.indexOf(SEPARATOR);
        return index < 0 ? id : id.substring(0, index);
    }

    public static boolean isNested(String id) {
        return id.contains(SEPARATOR);
    }

    /** Number of instances above {@code id}: 0 for a document component. */
    public static int depth(String id) {
        return (int) id.chars().filter(character -> character == SEPARATOR.charAt(0)).count();
    }

    public static List<String> segments(String id) {
        return List.of(id.split(SEPARATOR));
    }
}
