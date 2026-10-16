package lab.workload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class EscapeAnalysisCaseTest {

    @Test
    @DisplayName("computeNoEscape and computeWithEscape produce strictly equivalent results")
    void testEquivalence() {
        EscapeAnalysisCase ea = new EscapeAnalysisCase();
        long seed = 987654321L;
        int iterations = 10_000;

        double resultNoEscape = ea.computeNoEscape(seed, iterations);
        double resultWithEscape = ea.computeWithEscape(seed, iterations);

        assertEquals(resultNoEscape, resultWithEscape, 1e-9,
                "Non-escaping and escaping computations must yield identical arithmetic output");
        assertNotNull(ea.escapeSink, "Escaping computation must publish sink reference");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 42L, 1337L, -999999L, 0xCAFEBABE})
    @DisplayName("Equivalence holds across diverse seeds")
    void testEquivalenceAcrossSeeds(long seed) {
        EscapeAnalysisCase ea = new EscapeAnalysisCase();
        int iterations = 1000;

        double res1 = ea.computeNoEscape(seed, iterations);
        double res2 = ea.computeWithEscape(seed, iterations);

        assertEquals(res1, res2, 1e-9);
    }

    @Test
    @DisplayName("Both methods are strictly deterministic")
    void testDeterminism() {
        EscapeAnalysisCase ea1 = new EscapeAnalysisCase();
        EscapeAnalysisCase ea2 = new EscapeAnalysisCase();

        long seed = 55555L;
        int iterations = 2500;

        assertEquals(ea1.computeNoEscape(seed, iterations), ea2.computeNoEscape(seed, iterations));
        assertEquals(ea1.computeWithEscape(seed, iterations), ea2.computeWithEscape(seed, iterations));
    }
}
