package lab.jmh;

import lab.workload.InliningCase;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class InliningBenchmark {

    private InliningCase inliningCase;
    private long seed;

    @Setup(Level.Iteration)
    public void setup() {
        inliningCase = new InliningCase();
        seed = 1337L;
    }

    @Benchmark
    public void smallMethods(Blackhole bh) {
        bh.consume(inliningCase.computeSmallMethods(seed, 1000));
    }

    @Benchmark
    public void largeMethod(Blackhole bh) {
        bh.consume(inliningCase.computeLargeMethod(seed, 1000));
    }

    @Benchmark
    public void monomorphic(Blackhole bh) {
        bh.consume(inliningCase.computeMonomorphic(seed, 1000));
    }

    @Benchmark
    public void megamorphic(Blackhole bh) {
        bh.consume(inliningCase.computeMegamorphic(seed, 1000));
    }
}
