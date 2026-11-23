package cl.almejo.vsim.core.geometry;

/**
 * Union-find with path compression over the numbers {@code 0..size-1}, in an array: for the hot loops that number
 * their items (the connectivity check's points, the layout's boxes); {@link UnionFind} is the general one.
 * {@link #union} makes the first argument's root the root of the merged set, like {@link UnionFind#union}.
 */
public final class IntUnionFind {

    private final int[] parents;

    /** {@code size} items, each in a set of its own. */
    public IntUnionFind(int size) {
        parents = new int[size];
        for (int item = 0; item < size; item++) {
            parents[item] = item;
        }
    }

    public int find(int item) {
        int root = item;
        while (parents[root] != root) {
            root = parents[root];
        }
        int current = item;
        while (parents[current] != root) {
            int next = parents[current];
            parents[current] = root;
            current = next;
        }
        return root;
    }

    public void union(int first, int second) {
        int firstRoot = find(first);
        int secondRoot = find(second);
        if (firstRoot != secondRoot) {
            parents[secondRoot] = firstRoot;
        }
    }
}
