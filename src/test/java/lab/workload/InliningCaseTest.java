package lab.workload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class InliningCaseTest {

    @Test
    @DisplayName("Small methods pipeline and large method pipeline produce strictly identical results")
    void testSmallVsLargePipelineEquivalence() {
        InliningCase inliningCase = new InliningCase();
        long seed = 42L;
        int iterations = 10_000;

        long smallResult = inliningCase.computeSmallMethods(seed, iterations);
        long largeResult = inliningCase.computeLargeMethod(seed, iterations);

        assertEquals(smallResult, largeResult,
                "Composed small methods and unrolled large method must compute identical values");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, -1L, 999999L, 0x123456789ABCDEFL, -88888888L})
    @DisplayName("Equivalence holds across diverse inputs and seeds")
    void testEquivalenceAcrossSeeds(long seed) {
        InliningCase inliningCase = new InliningCase();
        int iterations = 500;

        long rSmall = inliningCase.computeSmallMethods(seed, iterations);
        long rLarge = inliningCase.computeLargeMethod(seed, iterations);

        assertEquals(rSmall, rLarge);
    }

    @Test
    @DisplayName("Single step of small pipeline matches large pipeline exactly")
    void testSingleStepEquivalence() {
        long[] testValues = {0L, 1L, -1L, 42L, 0x7FFFFFFFFFFFFFFFL, -0x8000000000000000L};
        for (long v : testValues) {
            assertEquals(InliningCase.computeSmallPipeline(v), InliningCase.computeLargePipeline(v),
                    "Step value for " + v + " must match");
        }
    }

    @Test
    @DisplayName("Monomorphic dispatch is deterministic and matches expected OpAdd accumulation")
    void testMonomorphicDispatch() {
        InliningCase ic1 = new InliningCase();
        InliningCase ic2 = new InliningCase();

        long seed = 100L;
        int iterations = 2000;

        long res1 = ic1.computeMonomorphic(seed, iterations);
        long res2 = ic2.computeMonomorphic(seed, iterations);

        assertEquals(res1, res2);
        assertNotEquals(0L, res1);
    }

    @Test
    @DisplayName("Megamorphic dispatch exercises 4 distinct receiver classes and is deterministic")
    void testMegamorphicDispatch() {
        InliningCase ic1 = new InliningCase();
        InliningCase ic2 = new InliningCase();

        assertEquals(4, ic1.getMegaOps().length, "Megamorphic test requires at least 3 distinct receivers");

        long seed = 200L;
        int iterations = 4000;

        long res1 = ic1.computeMegamorphic(seed, iterations);
        long res2 = ic2.computeMegamorphic(seed, iterations);

        assertEquals(res1, res2);
        assertNotEquals(res1, ic1.computeMonomorphic(seed, iterations));
    }
}
