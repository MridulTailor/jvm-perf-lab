package lab.workload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AllocationHeavyTest {

    @Test
    @DisplayName("Identical seeds produce identical allocation checksums")
    void testDeterminism() {
        AllocationHeavy w1 = new AllocationHeavy(42L, 0.10, 1000);
        AllocationHeavy w2 = new AllocationHeavy(42L, 0.10, 1000);

        for (int i = 0; i < 50; i++) {
            long cs1 = w1.step(100);
            long cs2 = w2.step(100);
            assertEquals(cs1, cs2, "Checksums at step " + i + " must match");
        }

        assertEquals(w1.computeRetainedChecksum(), w2.computeRetainedChecksum());
        assertEquals(w1.getRetainedCount(), w2.getRetainedCount());
    }

    @Test
    @DisplayName("Different seeds produce divergent checksums")
    void testSeedDivergence() {
        AllocationHeavy w1 = new AllocationHeavy(42L, 0.10, 1000);
        AllocationHeavy w2 = new AllocationHeavy(999L, 0.10, 1000);

        long cs1 = w1.step(200);
        long cs2 = w2.step(200);

        assertNotEquals(cs1, cs2);
    }

    @Test
    @DisplayName("Bounded cache respects maximum capacity and evicts properly")
    void testBoundedCache() {
        int capacity = 50;
        AllocationHeavy w = new AllocationHeavy(1234L, 1.0, capacity);

        // Step with 200 items, all requested to be retained
        w.step(200);

        assertEquals(capacity, w.getRetainedCount());
        assertEquals(capacity, w.getCacheCapacity());
    }

    @Test
    @DisplayName("Invalid parameters throw IllegalArgumentException")
    void testInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new AllocationHeavy(1L, -0.1, 100));
        assertThrows(IllegalArgumentException.class, () -> new AllocationHeavy(1L, 1.1, 100));
        assertThrows(IllegalArgumentException.class, () -> new AllocationHeavy(1L, 0.5, 0));
    }
}
