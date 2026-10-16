package lab.harness;

import lab.workload.AllocationHeavy;
import lab.workload.PointerChasing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Standalone harness for GC comparison experiments.
 *
 * Runs a chosen workload for a fixed duration across configurable worker threads,
 * calculates steady-state throughput (ops/sec), and outputs a structured JSON report.
 */
public final class GcHarness {

    public static void main(String[] args) throws Exception {
        String workloadName = "allocation";
        int durationSec = 60;
        int warmupSec = 3;
        int threads = 1;
        long seed = 42L;
        double retentionFraction = 0.05;
        int cacheCapacity = 50_000;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--workload" -> workloadName = args[++i];
                case "--duration" -> durationSec = Integer.parseInt(args[++i]);
                case "--warmup" -> warmupSec = Integer.parseInt(args[++i]);
                case "--threads" -> threads = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--retention" -> retentionFraction = Double.parseDouble(args[++i]);
                case "--cache-capacity" -> cacheCapacity = Integer.parseInt(args[++i]);
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
                }
            }
        }

        System.out.printf("[GcHarness] Starting workload=%s, duration=%ds, warmup=%ds, threads=%d, retention=%.3f, heapMax=%dMB%n",
                workloadName, durationSec, warmupSec, threads, retentionFraction,
                Runtime.getRuntime().maxMemory() / (1024 * 1024));

        final AtomicBoolean warmRunning = new AtomicBoolean(true);
        final AtomicBoolean measureRunning = new AtomicBoolean(false);
        final LongAdder totalOps = new LongAdder();
        final LongAdder dummySink = new LongAdder();

        final String finalWorkload = workloadName;
        final double finalRetention = retentionFraction;
        final int finalCacheCap = cacheCapacity;
        final long baseSeed = seed;

        Thread[] workers = new Thread[threads];
        CountDownLatch startLatch = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            workers[t] = new Thread(() -> {
                long threadSeed = baseSeed + threadId * 10007L;
                AllocationHeavy alloc = "allocation".equalsIgnoreCase(finalWorkload)
                        ? new AllocationHeavy(threadSeed, finalRetention, finalCacheCap)
                        : null;
                PointerChasing ptr = "pointer".equalsIgnoreCase(finalWorkload)
                        ? new PointerChasing(threadSeed, 50_000)
                        : null;

                try {
                    startLatch.await();
                } catch (InterruptedException ignored) {}

                // Warmup phase
                while (warmRunning.get()) {
                    long cs = (alloc != null) ? alloc.step(100) : ptr.traverse(500);
                    dummySink.add(cs);
                }

                // Measurement phase
                while (measureRunning.get()) {
                    long cs = (alloc != null) ? alloc.step(100) : ptr.traverse(500);
                    dummySink.add(cs);
                    totalOps.increment();
                }
            }, "gc-worker-" + t);
            workers[t].start();
        }

        // Release threads for warmup
        startLatch.countDown();
        if (warmupSec > 0) {
            Thread.sleep(warmupSec * 1000L);
        }
        warmRunning.set(false);

        // Begin timed measurement
        measureRunning.set(true);
        long startNano = System.nanoTime();
        Thread.sleep(durationSec * 1000L);
        measureRunning.set(false);
        long endNano = System.nanoTime();

        for (Thread worker : workers) {
            worker.join();
        }

        double elapsedSec = (endNano - startNano) / 1_000_000_000.0;
        long totalCompleted = totalOps.sum();
        double opsPerSec = totalCompleted / elapsedSec;

        System.out.printf("[GcHarness] Finished: completed %d ops in %.3f s (%.2f ops/sec, sink=%d)%n",
                totalCompleted, elapsedSec, opsPerSec, dummySink.sum());

        // Structured JSON for deterministic parser consumption
        System.out.println("JSON_REPORT_BEGIN");
        System.out.printf("{\"workload\":\"%s\",\"duration_sec\":%.4f,\"threads\":%d,\"seed\":%d,\"retention_fraction\":%.4f,\"total_ops\":%d,\"throughput_ops_sec\":%.2f}%n",
                workloadName, elapsedSec, threads, seed, retentionFraction, totalCompleted, opsPerSec);
        System.out.println("JSON_REPORT_END");
    }
}
