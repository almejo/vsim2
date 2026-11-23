package cl.almejo.vsim.core.geometry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntUnionFindTest {

    @Test
    void everyItemStartsAloneAndAUnionKeepsTheFirstArgumentsRoot() {
        IntUnionFind sets = new IntUnionFind(5);
        assertThat(sets.find(3)).isEqualTo(3);

        sets.union(1, 2);
        sets.union(4, 1);

        assertThat(sets.find(2)).isEqualTo(4);
        assertThat(sets.find(1)).isEqualTo(4);
        assertThat(sets.find(0)).isZero();
        sets.union(4, 2);
        assertThat(sets.find(2)).as("a union inside one set changes nothing").isEqualTo(4);
    }
}
