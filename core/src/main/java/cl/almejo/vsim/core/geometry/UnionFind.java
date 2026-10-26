package cl.almejo.vsim.core.geometry;

import java.util.HashMap;
import java.util.Map;

/**
 * Union-find with path compression. {@link #union} makes the first argument's root the root of the merged set; the
 * netlist compiler depends on that choice (the order of its nets follows the roots).
 */
public final class UnionFind<T> {

    private final Map<T, T> parents = new HashMap<>();

    public void add(T item) {
        parents.putIfAbsent(item, item);
    }

    public T find(T item) {
        T root = item;
        while (!parents.get(root).equals(root)) {
            root = parents.get(root);
        }
        T current = item;
        while (!current.equals(root)) {
            T next = parents.get(current);
            parents.put(current, root);
            current = next;
        }
        return root;
    }

    public void union(T first, T second) {
        T firstRoot = find(first);
        T secondRoot = find(second);
        if (!firstRoot.equals(secondRoot)) {
            parents.put(secondRoot, firstRoot);
        }
    }
}
