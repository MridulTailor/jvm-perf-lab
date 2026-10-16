package lab.jmh;

import lab.workload.EscapeAnalysisCase;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class EscapeAnalysisBenchmark {

    private EscapeAnalysisCase eaCase;
    private long seed;

    @Setup(Level.Iteration)
    public void setup() {
        eaCase = new EscapeAnalysisCase();
        seed = 42L;
    }

    @Benchmark
    public void noEscape(Blackhole bh) {
        bh.consume(eaCase.computeNoEscape(seed, 1000));
    }

    @Benchmark
    public void withEscape(Blackhole bh) {
        bh.consume(eaCase.computeWithEscape(seed, 1000));
    }
}
