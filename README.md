# JVM Performance Lab (`jvm-perf-lab`)

An empirical, reproducible JVM performance engineering testbed focusing on HotSpot C2 compiler optimizations (Escape Analysis, Inlining, Devirtualization) and Garbage Collection dynamics (Serial, Parallel, G1, ZGC, Generational ZGC, and Shenandoah) running on Apple Silicon (AArch64) under Amazon Corretto 21 LTS.

## Objective
Provide verifiable, reproducible JVM performance measurements backed by raw diagnostic artifacts, JMH JSON logs, unified GC logs, and compilation logs without hand-typed or estimated figures.

## Project Structure
- `src/main/java/lab/`: Core deterministic workloads (`AllocationHeavy`, `PointerChasing`, `EscapeAnalysisCase`, `InliningCase`) and `GcHarness`.
- `src/jmh/java/lab/jmh/`: JMH benchmark suites measuring scalar replacement, inline budget thresholds, and dispatch polymorphism.
- `src/test/java/lab/`: JUnit 5 tests asserting determinism, behavioral equivalence, and log parser accuracy against fixtures.
- `scripts/`: Shell and Python scripts for environment capture, experiment execution, log parsing, and report generation.
- `results/`: Raw and processed benchmark artifacts, GC logs, and environment metadata.
- `docs/`: Auto-generated metric summaries (`RESULTS.md`, `RESUME_METRICS.md`) and in-depth performance analysis (`FINDINGS.md`).
