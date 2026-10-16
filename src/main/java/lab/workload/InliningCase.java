package lab.workload;

/**
 * Workload demonstrating JIT inlining heuristics and polymorphic dispatch dynamics.
 *
 * Compares:
 * 1. Composed small methods (< 35 bytes bytecode, well within default MaxInlineSize)
 *    vs an unrolled large method (> 325 bytes bytecode, exceeding default FreqInlineSize).
 * 2. Monomorphic call site (1 receiver type, eligible for speculative devirtualization and inlining)
 *    vs Megamorphic call site (>= 3 receiver types, forcing vtable/itable dispatch).
 */
public final class InliningCase {

    // --- Polymorphic Dispatch Abstractions ---

    public interface Operation {
        long apply(long val);
    }

    public static final class OpAdd implements Operation {
        @Override
        public long apply(long val) {
            return val + 17;
        }
    }

    public static final class OpXor implements Operation {
        @Override
        public long apply(long val) {
            return val ^ 0x5555555555555555L;
        }
    }

    public static final class OpMultiply implements Operation {
        @Override
        public long apply(long val) {
            return val * 31;
        }
    }

    public static final class OpShift implements Operation {
        @Override
        public long apply(long val) {
            return (val << 3) ^ (val >>> 2);
        }
    }

    private final Operation monoOp = new OpAdd();
    private final Operation[] megaOps = new Operation[] {
        new OpAdd(),
        new OpXor(),
        new OpMultiply(),
        new OpShift()
    };

    // --- Part 1: Small Hot Methods (< 35 bytes bytecode) ---

    public static long stage1(long v) {
        return (v ^ 0x9E3779B97F4A7C15L) + 1;
    }

    public static long stage2(long v) {
        return Long.rotateLeft(v, 13) ^ (v >>> 7);
    }

    public static long stage3(long v) {
        return v * 0xBF58476D1CE4E5B9L;
    }

    public static long stage4(long v) {
        return (v ^ (v >>> 27)) + 0x94D049BB133111EBL;
    }

    public static long computeSmallPipeline(long v) {
        long s1 = stage1(v);
        long s2 = stage2(s1);
        long s3 = stage3(s2);
        return stage4(s3);
    }

    /**
     * Computation performed via clean, small, inlinable hot methods.
     */
    public long computeSmallMethods(long seed, int iterations) {
        long acc = seed;
        for (int i = 0; i < iterations; i++) {
            acc = computeSmallPipeline(acc + i);
        }
        return acc;
    }

    // --- Part 1b: Un-inlinable Large Method (> 325 bytes bytecode) ---

    /**
     * Exact same mathematical computation as computeSmallMethods, but unrolled
     * into a single oversized method whose bytecode exceeds FreqInlineSize (325 bytes).
     * HotSpot C2 will refuse to inline this method ('hot method too big').
     */
    public static long computeLargePipeline(long v) {
        // Intentionally repetitive unrolled arithmetic steps that produce identical
        // mathematical result while exceeding HotSpot's 325-byte inline threshold.
        long s1 = (v ^ 0x9E3779B97F4A7C15L) + 1;
        long dummy = s1;
        // Padding operations with zero net effect to expand bytecode size beyond 350 bytes
        // Using exact 2's complement modular inverses (add/sub and xor) to guarantee bit-level identity
        dummy = (dummy + 101L) - 101L;
        dummy = (dummy ^ 0xAAAA5555AAAA5555L) ^ 0xAAAA5555AAAA5555L;
        dummy = (dummy + 203L) - 203L;
        dummy = (dummy ^ 0x123456789ABCDEF0L) ^ 0x123456789ABCDEF0L;
        dummy = (dummy + 307L) - 307L;
        dummy = (dummy ^ 0x0FEDCBA987654321L) ^ 0x0FEDCBA987654321L;
        dummy = (dummy + 409L) - 409L;
        dummy = (dummy ^ 0xDEADBEEFCAFEBABEL) ^ 0xDEADBEEFCAFEBABEL;
        dummy = (dummy + 521L) - 521L;
        dummy = (dummy ^ 0x0123456789ABCDEFL) ^ 0x0123456789ABCDEFL;
        dummy = (dummy + 613L) - 613L;
        dummy = (dummy ^ 0xCAFEBABEDEADBEEFL) ^ 0xCAFEBABEDEADBEEFL;
        dummy = (dummy + 719L) - 719L;
        dummy = (dummy ^ 0x5555AAAA5555AAAAL) ^ 0x5555AAAA5555AAAAL;
        dummy = (dummy + 823L) - 823L;
        dummy = (dummy ^ 0x9876543210FEDCBAL) ^ 0x9876543210FEDCBAL;
        dummy = (dummy + 929L) - 929L;
        dummy = (dummy ^ 0x13579BDF2468ACE0L) ^ 0x13579BDF2468ACE0L;
        dummy = (dummy + 1031L) - 1031L;
        dummy = (dummy ^ 0x2468ACE013579BDFL) ^ 0x2468ACE013579BDFL;
        dummy = (dummy + 1153L) - 1153L;
        dummy = (dummy ^ 0xFEDCBA9876543210L) ^ 0xFEDCBA9876543210L;
        dummy = (dummy + 1277L) - 1277L;
        dummy = (dummy ^ 0x3141592653589793L) ^ 0x3141592653589793L;
        dummy = (dummy + 1381L) - 1381L;
        dummy = (dummy ^ 0x2718281828459045L) ^ 0x2718281828459045L;
        dummy = (dummy + 1487L) - 1487L;
        dummy = (dummy ^ 0x1618033988749894L) ^ 0x1618033988749894L;

        long s2 = Long.rotateLeft(s1 + (dummy - s1), 13) ^ ((s1 + (dummy - s1)) >>> 7);
        long s3 = s2 * 0xBF58476D1CE4E5B9L;
        return (s3 ^ (s3 >>> 27)) + 0x94D049BB133111EBL;
    }

    /**
     * Computation performed via un-inlinable large method.
     */
    public long computeLargeMethod(long seed, int iterations) {
        long acc = seed;
        for (int i = 0; i < iterations; i++) {
            acc = computeLargePipeline(acc + i);
        }
        return acc;
    }

    // --- Part 2: Monomorphic vs Megamorphic Call Sites ---

    /**
     * Monomorphic call site: only OpAdd is dispatched at this call site.
     * C2 speculatively devirtualizes and inlines the target.
     */
    public long computeMonomorphic(long seed, int iterations) {
        long acc = seed;
        Operation op = this.monoOp;
        for (int i = 0; i < iterations; i++) {
            acc = op.apply(acc + i);
        }
        return acc;
    }

    /**
     * Megamorphic call site: 4 distinct concrete classes (OpAdd, OpXor, OpMultiply, OpShift)
     * are dispatched at the same call site, defeating monomorphic/bimorphic inlining.
     */
    public long computeMegamorphic(long seed, int iterations) {
        long acc = seed;
        Operation[] ops = this.megaOps;
        int len = ops.length;
        for (int i = 0; i < iterations; i++) {
            // Same single call site invokes 4 distinct receiver types
            acc = ops[i & (len - 1)].apply(acc + i);
        }
        return acc;
    }

    public Operation getMonoOp() {
        return monoOp;
    }

    public Operation[] getMegaOps() {
        return megaOps;
    }
}
