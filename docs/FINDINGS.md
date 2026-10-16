# Empirical Findings & Architectural Analysis (`FINDINGS.md`)

This document provides in-depth technical analysis of the performance measurements recorded in `results/`, examining HotSpot C2 compiler optimizations, virtual call dispatch heuristics, and garbage collection pauses on Apple Silicon (AArch64) under Amazon Corretto 21 LTS.

---

## 1. HotSpot C2 Escape Analysis & Scalar Replacement

### Empirical Observations
From `results/raw/jmh/ea_default.json` and `results/raw/jmh/ea_disabled.json`:
- **Default EA (`-XX:+DoEscapeAnalysis`)**: `774,147.54 ± 13,717.22 ops/s`, `0.009 B/op` allocation.
- **Disabled EA (`-XX:-DoEscapeAnalysis`)**: `259,108.09 ± 2,493.94 ops/s`, `48,000.027 B/op` allocation.
- **Forced Heap Escape (`withEscape`)**: `258,234.12 ± 4,701.41 ops/s`, `48,000.027 B/op` allocation.

### Technical Analysis
HotSpot C2's escape analysis algorithm (Bogan & Choi / Shibuya approach) classifies allocations into three states:
1. `NoEscape`: The object does not escape the allocating method or thread.
2. `ArgEscape`: The object escapes into a called method but does not survive past the caller.
3. `GlobalEscape`: The object is stored into a static field, instance field of a heap-reachable object, or returned to a global sink.

When `Point3D` is instantiated inside `computeNoEscape`, C2 classifies it as `NoEscape`. During the macro-expansion phase, C2 eliminates the `AllocateNode` completely and performs **scalar replacement**, assigning each field (`x`, `y`, `z`, `weight`) directly to CPU registers. As verified by JMH's `-prof gc`, heap allocation drops to effectively **0 B/op** (0.009 B/op residual measurement noise), yielding a **+198.8% throughput increase**.

### Verification of Allocation Cost
Notice that disabling Escape Analysis on non-escaping code (`noEscape` under `-XX:-DoEscapeAnalysis`: `259,108 ops/s`) results in throughput virtually identical to code that explicitly publishes the reference to a volatile field (`withEscape`: `258,234 ops/s`). Both allocate precisely **48,000.0 B/op** (1,000 `Point3D` objects × 48 bytes per object: 12-byte Mark/Klass word header + 4-byte padding + 4 × 8-byte `double` primitives under compressed OOPs). 

This proves empirically that the performance loss when escape analysis fails is driven by **heap allocation overhead and GC allocation pressure**, rather than the memory write to the field itself.

---

## 2. JIT Inlining Heuristics & Polymorphic Dispatch

### Inlining Budget Enforcement
From `results/raw/jmh/inlining_default.json`, `inlining_disabled.json`, and `inlining_thresholds.json`:
- **Composed Small Methods (`<35B`)**: `227,096.90 ops/s` (Default) vs `171,648.23 ops/s` (`-XX:-Inline`, **24.4% penalty**).
- **Unrolled Large Method (`>325B`)**: `197,757.19 ops/s` (Default) vs `227,089.75 ops/s` (`-XX:MaxInlineSize=100 -XX:FreqInlineSize=500`, **+14.8% gain**).

### Technical Analysis & JIT Diagnostic Proof
HotSpot C2 applies strict bytecode budgeting to prevent compiler bloat and code cache explosion:
- `MaxInlineSize` (default 35 bytes): Maximum bytecode size of an infrequently called method considered for inlining.
- `FreqInlineSize` (default 325 bytes): Maximum bytecode size of a hot method considered for inlining.

In `InliningCase.java`, `computeLargePipeline` was sized to 422 bytes of bytecode. Inspection of `results/raw/jit/inlining_jit.log` reveals:
```
@ 18 lab.workload.InliningCase::computeLargePipeline (422 bytes) hot method too big
```
Because 422 > 325, C2 refused inlining under default heuristics, forcing a real subroutine call on every iteration and restricting register allocation across call boundaries.

When `-XX:MaxInlineSize=100 -XX:FreqInlineSize=500` was supplied, the 422-byte method fell within the expanded 500-byte budget. C2 inlined the entire pipeline, and throughput increased from `197,757 ops/s` to `227,089 ops/s`, **completely matching the performance of the small-methods pipeline** (`227,252 ops/s`).

### Monomorphic vs Megamorphic Dispatch
- **Monomorphic Call Site**: `3,191,723.27 ± 18,256.81 ops/s`
- **Megamorphic Call Site (4 receiver classes)**: `306,428.56 ± 14,855.34 ops/s`
- **Degradation**: **10.4x throughput reduction** (90.4% penalty).

HotSpot utilizes inline caches (IC) at virtual/interface call sites (`invokeinterface`). When only a single receiver class (`OpAdd`) is observed, C2 speculatively devirtualizes the call site and inlines the target arithmetic directly into the loop. 

When 4 distinct classes (`OpAdd`, `OpXor`, `OpMultiply`, `OpShift`) cycle through the call site, the inline cache transitions past the bimorphic threshold into **megamorphic state**. C2 cannot generate inline type checks for megamorphic sites and instead emits an indirect table lookup (`itable` stub) on every invocation, defeating hardware branch predictors and compiler vectorization.

---

## 3. Garbage Collection Comparative Dynamics

### Summary Matrix (from `results/processed/gc_summary.csv`)
| Collector | Heap | Throughput (ops/s) | GC Overhead (%) | p50 Pause (ms) | p99 Pause (ms) | Max Pause (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **ZGC** | 2g | **77,820.1** | **0.004%** | **0.005** | **0.019** | **0.030** |
| **ZGC_Gen** | 2g | 77,166.9 | 0.005% | 0.007 | **0.014** | **0.021** |
| **Shenandoah**| 2g | 77,416.7 | 0.048% | 0.042 | 0.192 | 0.295 |
| **G1** | 2g | 77,344.4 | 0.906% | 3.122 | 4.875 | 5.627 |
| **Parallel** | 2g | 76,214.7 | 2.320% | 4.685 | 6.839 | 15.695 |
| **Serial** | 2g | 73,088.1 | 6.301% | 10.951 | 17.138 | 234.876 |

### Ultra-Low Latency: ZGC & Generational ZGC
ZGC and Generational ZGC achieved sub-20-microsecond tail pause latencies (`p99 = 0.014 - 0.019 ms`).
Total stop-the-world time across 60 seconds of sustained allocation under ZGC was only **2.19 ms** (a negligible **0.004% overhead**), representing a **252.6x reduction in p99 pause time** compared to G1 (4.875 ms).

### Hypothesis & Plausible Explanation: Parallel GC vs Concurrent Collectors
> [!NOTE]
> *Surprising Result (Hypothesis)*: Conventional JVM literature often asserts that Parallel GC achieves higher raw throughput than concurrent collectors on batch allocation tasks due to the lack of concurrent read/write barrier overhead. However, in our measurements on Apple Silicon M1 Pro, Parallel GC achieved lower throughput (`76,214 ops/s` at 2G, `71,841 ops/s` at 512M) than ZGC (`77,820 ops/s` at 2G, `77,594 ops/s` at 512M) and G1 (`77,344 ops/s` at 2G).

**Plausible Explanation (Hypothesis)**:
1. *Old Generation Retention Pressure*: `AllocationHeavy` retains 5% of allocations in a bounded circular buffer (50,000 entries), promoting objects to the old generation. Under Parallel GC, young collections copy survivors, and tenured space accumulates garbage, eventually triggering Full GC events with multi-millisecond stop-the-world pauses (15.7ms max at 2G, 46.8ms max at 512M), resulting in 2.32% to 7.28% mutator pause overhead.
2. *Multicore Hardware Headroom*: The Apple M1 Pro features 8 hardware execution cores. Because the benchmarking harness was configured with 1 mutator thread, the remaining hardware cores were available for ZGC and Shenandoah concurrent GC threads. The concurrent collectors offloaded marking and compaction onto auxiliary cores without contending with the mutator thread.
3. *Tight Heap Sensitivity*: At a tight 512MB heap, Parallel GC pause overhead expanded to 7.28% (4.37s in pauses) and Serial to 12.88% (7.73s in pauses), whereas ZGC maintained 0.017% overhead and 77,594 ops/sec.

---

## 4. JIT Warmup Dynamics

Inspection of `results/processed/warmup_curve.csv` and `docs/warmup_curve.svg`:
- **Interpreter Baseline (`-Xint`)**: Flat throughput at `~8.6M ops/s` with zero compilation overhead.
- **C1-Only (`-XX:TieredStopAtLevel=1`)**: Rapid convergence to steady-state (`~26.0M ops/s`) within the first 100ms window, with no profile-guided optimization.
- **Tiered Default (C1 + C2)**: Starts around `23.3M ops/s` as C1 Tier-3 gathers invocation/backedge profiling counters, and ramps to steady state within 3 iterations (~600ms).

---

## 5. Execution Profiling Analysis

From `results/processed/profile_top10_cpu.csv` and `profile_top10_alloc.csv`:
- **CPU Profiling (`PointerChasing`)**: `lab.workload.PointerChasing.traverse` accounts for **96.59% of self-time**. The randomized Sattolo permutation successfully defeats L1/L2 hardware prefetching, keeping CPU cycles localized in memory-load stalls within the traversal method.
- **Allocation Profiling (`AllocationHeavy`)**: `byte[]` accounts for **89.89%** of allocations and `ShortLivedPayload` for **10.10%** (100.0% combined). The profiler accurately captured the payload object and array churn.
