package lab.workload;

/**
 * Workload contrasting scalar-replaceable non-escaping allocations
 * against escaping allocations that force heap allocation.
 *
 * Under C2 with -XX:+DoEscapeAnalysis, the non-escaping variant eliminates
 * object allocation entirely via scalar replacement into CPU registers.
 * When escape analysis is disabled (-XX:-DoEscapeAnalysis) or when the object
 * escapes to an instance field, real heap allocations are incurred.
 *
 * Both methods perform identical mathematical transformations and produce
 * identical return values for identical inputs.
 */
public final class EscapeAnalysisCase {

    public static final class Point3D {
        private final double x;
        private final double y;
        private final double z;
        private final double weight;

        public Point3D(double x, double y, double z, double weight) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.weight = weight;
        }

        public double magnitudeSquared() {
            return (x * x + y * y + z * z) * weight;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public double getWeight() { return weight; }
    }

    // Public non-final sink field to force heap escape
    public volatile Point3D escapeSink;

    /**
     * Non-escaping version: Point3D never escapes the method scope.
     * C2 replaces Point3D with scalar registers (0 B/op heap allocation).
     *
     * @param seed input seed
     * @param iterations iteration count
     * @return accumulated mathematical result
     */
    public double computeNoEscape(long seed, int iterations) {
        double acc = 0.0;
        long state = seed;
        for (int i = 0; i < iterations; i++) {
            state = state * 2862933555777941757L + 3037000493L;
            double x = (state & 0xFFFF) * 0.001;
            double y = ((state >>> 16) & 0xFFFF) * 0.001;
            double z = ((state >>> 32) & 0xFFFF) * 0.001;
            double w = ((state >>> 48) & 0xFFFF) * 0.0001 + 0.1;

            Point3D pt = new Point3D(x, y, z, w);
            acc += pt.magnitudeSquared();
        }
        return acc;
    }

    /**
     * Escaping version: Point3D is written to volatile instance field escapeSink,
     * forcing HotSpot C2 to allocate on the heap (GlobalEscape).
     *
     * @param seed input seed
     * @param iterations iteration count
     * @return accumulated mathematical result, strictly identical to computeNoEscape
     */
    public double computeWithEscape(long seed, int iterations) {
        double acc = 0.0;
        long state = seed;
        for (int i = 0; i < iterations; i++) {
            state = state * 2862933555777941757L + 3037000493L;
            double x = (state & 0xFFFF) * 0.001;
            double y = ((state >>> 16) & 0xFFFF) * 0.001;
            double z = ((state >>> 32) & 0xFFFF) * 0.001;
            double w = ((state >>> 48) & 0xFFFF) * 0.0001 + 0.1;

            Point3D pt = new Point3D(x, y, z, w);
            this.escapeSink = pt; // Forces escape to heap
            acc += pt.magnitudeSquared();
        }
        return acc;
    }
}
