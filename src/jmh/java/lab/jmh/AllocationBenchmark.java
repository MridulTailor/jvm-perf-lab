package lab.jmh;

import lab.workload.AllocationHeavy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class AllocationBenchmark {

    private AllocationHeavy allocationHeavy;

    @Setup(Level.Iteration)
    public void setup() {
        // 5% retained in 20,000 entry bounded cache
        allocationHeavy = new AllocationHeavy(42L, 0.05, 20_000);
    }

    @Benchmark
    public void allocate(Blackhole bh) {
        bh.consume(allocationHeavy.step(500));
    }
}
