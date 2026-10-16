package lab.workload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PointerChasingTest {

    @Test
    @DisplayName("Identical seeds produce identical pointer chasing traversals")
    void testDeterminism() {
        PointerChasing pc1 = new PointerChasing(1337L, 5000);
        PointerChasing pc2 = new PointerChasing(1337L, 5000);

        for (int i = 0; i < 20; i++) {
            long cs1 = pc1.traverse(500);
            long cs2 = pc2.traverse(500);
            assertEquals(cs1, cs2, "Traversal checksum at step " + i + " must match");
        }

        assertNotNull(pc1.getCurrent());
        assertEquals(pc1.getCurrent().id, pc2.getCurrent().id);
    }

    @Test
    @DisplayName("Different seeds produce divergent traversal paths")
    void testSeedDivergence() {
        PointerChasing pc1 = new PointerChasing(111L, 2000);
        PointerChasing pc2 = new PointerChasing(222L, 2000);

        long cs1 = pc1.traverse(1000);
        long cs2 = pc2.traverse(1000);

        assertNotEquals(cs1, cs2);
    }

    @Test
    @DisplayName("Topology construction creates full valid graph without dead ends")
    void testValidTopology() {
        int nodeCount = 500;
        PointerChasing pc = new PointerChasing(777L, nodeCount);

        assertEquals(nodeCount, pc.getNodeCount());
        // Traverse several cycles to verify no NullPointerExceptions
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 10; i++) {
                pc.traverse(nodeCount * 2);
            }
        });
    }

    @Test
    @DisplayName("Invalid node counts throw IllegalArgumentException")
    void testInvalidNodeCount() {
        assertThrows(IllegalArgumentException.class, () -> new PointerChasing(1L, 0));
        assertThrows(IllegalArgumentException.class, () -> new PointerChasing(1L, 1));
    }
}
