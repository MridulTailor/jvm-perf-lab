package lab.harness;

import lab.workload.InliningCase;

/**
 * Measures the warmup progression and compilation transition curve
 * under different JIT compilation tiers.
 *
 * Emits per-iteration throughput measurements (iteration index, elapsed ms, ops, ops/sec).
 */
public final class WarmupRunner {

    public static void main(String[] args) {
        int iterations = 25;
        int windowMs = 200;
        String label = "default";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--iterations" -> iterations = Integer.parseInt(args[++i]);
                case "--window-ms" -> windowMs = Integer.parseInt(args[++i]);
                case "--label" -> label = args[++i];
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
                }
            }
        }

        InliningCase inliningCase = new InliningCase();
        long seed = 123456789L;
        long sink = 0;

        System.out.println("WARMUP_CSV_BEGIN");
        System.out.println("config,iteration,elapsed_ms,ops,throughput_ops_sec");

        for (int iter = 1; iter <= iterations; iter++) {
            long windowNanos = windowMs * 1_000_000L;
            long start = System.nanoTime();
            long ops = 0;
            while ((System.nanoTime() - start) < windowNanos) {
                // Perform batch of workload operations
                sink ^= inliningCase.computeSmallMethods(seed + ops, 100);
                ops += 100;
            }
            long end = System.nanoTime();
            double elapsedMs = (end - start) / 1_000_000.0;
            double opsPerSec = ops / (elapsedMs / 1000.0);

            System.out.printf("%s,%d,%.2f,%d,%.2f%n", label, iter, elapsedMs, ops, opsPerSec);
        }

        System.out.println("WARMUP_CSV_END");
        if (sink == 42) {
            System.out.println("sink=" + sink);
        }
    }
}
