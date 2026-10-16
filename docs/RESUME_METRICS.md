# Key Verified Performance Metrics & Empirical Claims (`RESUME_METRICS.md`)

**Target Environment**: Apple Silicon Apple M1 Pro (arm64), macOS 15.3.2 (24D81), Amazon Corretto 21.0.12.1 (OpenJDK 64-Bit Server VM) | Measured: 2026-10-16

All numbers below are extracted automatically from raw benchmark outputs. Statistical significance is strictly asserted by verifying non-overlapping confidence intervals (score ± error).

## 1. Validated Performance Statements (Supported by Data)

- **Escape Analysis & Scalar Replacement**: Enabling HotSpot C2 Escape Analysis (`-XX:+DoEscapeAnalysis`) increased throughput by **198.8%** (774,147.54 ± 13,717.22 vs 259,108.09 ± 2,493.94 ops/s; 3 forks) and eliminated **48,000.0 B/op** of heap allocation (dropping allocation to 0.009 B/op). [Source: `results/raw/jmh/ea_default.json`, `results/raw/jmh/ea_disabled.json`]
- **Monomorphic Devirtualization**: Monomorphic call site inlining delivered **10.4x higher throughput** than a 4-receiver megamorphic call site (3,191,723.27 ± 18,256.81 vs 306,428.56 ± 14,855.34 ops/s; 3 forks), demonstrating the severe penalty of speculative inlining failure at polymorphic call sites. [Source: `results/raw/jmh/inlining_default.json`]
- **JIT Inlining Optimization**: Disabling method inlining via `-XX:-Inline` resulted in a **24.4% throughput penalty** on composed arithmetic pipelines (227,096.90 ± 2,254.94 down to 171,648.23 ± 6,342.76 ops/s; 3 forks). [Source: `results/raw/jmh/inlining_default.json`, `results/raw/jmh/inlining_disabled.json`]
- **ZGC vs G1 Pause Latency (2GB Heap)**: ZGC sustained a p99 pause time of **0.019 ms** compared to G1's **4.875 ms** (a **252.6x reduction** in tail pause latency) under equivalent allocation pressure (3 repetitions). [Source: `results/processed/gc_summary.csv`]
- **Workload Profiling & CPU Bottleneck Analysis**: Async-profiler CPU sampling confirmed that `lab.workload.PointerChasing.traverse` alone accounted for **96.6% of total CPU self-time** in memory-locality benchmarks. [Source: `results/processed/profile_top10_cpu.csv`]
- **HotSpot Compilation Diagnostics**: Automated JIT diagnostic parsing verified **19 C2 Tier-4 compilations** and detected exact inline rejections (`121 callee is too large`), demonstrating compiler budget enforcement. [Source: `results/processed/jit_summary.json`]

## 2. Claims NOT Supported by Data (Statistically Insignificant or Unsupported)
The following candidate statements were evaluated but are rejected or labeled statistically insignificant:

- Parallel GC did not exhibit throughput advantage over G1 at 2GB heap (76,214.7 vs 77,344.4 ops/s).

