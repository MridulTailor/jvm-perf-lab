package lab.jmh;

import lab.workload.PointerChasing;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class PointerChasingBenchmark {

    private PointerChasing pointerChasing;

    @Setup(Level.Trial)
    public void setup() {
        // 100,000 nodes ~ exceeds L2/L3 cache to stress memory latency
        pointerChasing = new PointerChasing(42L, 100_000);
    }

    @Benchmark
    public void traverse(Blackhole bh) {
        bh.consume(pointerChasing.traverse(5_000));
    }
}
