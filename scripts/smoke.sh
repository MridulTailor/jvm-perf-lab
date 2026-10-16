#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Resolve Java Home
if [ -d "${PROJECT_DIR}/.jdk/amazon-corretto-21.jdk/Contents/Home" ]; then
    JAVA_HOME="${PROJECT_DIR}/.jdk/amazon-corretto-21.jdk/Contents/Home"
elif [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || which java | sed 's/\/bin\/java//')"
fi

echo "================================================================"
echo " Starting jvm-perf-lab End-to-End Smoke Test (QUICK Mode)"
echo " Target JDK: ${JAVA_HOME}"
echo "================================================================"

START_TIME=$(date +%s)

# 1. Capture environment
echo "--- Step 1: Environment Capture ---"
"${PROJECT_DIR}/scripts/env_capture.sh"

# 2. Build & Test
echo "--- Step 2: Build & Unit Tests ---"
(cd "${PROJECT_DIR}" && JAVA_HOME="${JAVA_HOME}" mvn clean test)

# 3. JMH Microbenchmarks (QUICK)
echo "--- Step 3: JMH Benchmarks (QUICK) ---"
"${PROJECT_DIR}/scripts/run_jmh.sh" QUICK

# 4. GC Experiments (QUICK)
echo "--- Step 4: GC Experiments (QUICK) ---"
"${PROJECT_DIR}/scripts/run_gc_bench.sh" QUICK

# 5. Parse GC Logs
echo "--- Step 5: Parsing GC Logs ---"
python3 "${PROJECT_DIR}/scripts/parse_gc.py" \
    --dir "${PROJECT_DIR}/results/raw/gc" \
    --output-csv "${PROJECT_DIR}/results/processed/gc_summary.csv" \
    --output-json "${PROJECT_DIR}/results/processed/gc_summary.json"

# 6. JIT Diagnostics
echo "--- Step 6: JIT Diagnostics ---"
"${PROJECT_DIR}/scripts/run_jit_diagnostics.sh"

# 7. Warmup Curve (QUICK)
echo "--- Step 7: Warmup Curve (QUICK) ---"
"${PROJECT_DIR}/scripts/run_warmup.sh" QUICK

# 8. Profiling (QUICK)
echo "--- Step 8: Profiling (QUICK) ---"
"${PROJECT_DIR}/scripts/run_profile.sh" QUICK

# 9. Generate Reports
echo "--- Step 9: Report Generation ---"
python3 "${PROJECT_DIR}/scripts/report.py"

END_TIME=$(date +%s)
TOTAL_TIME=$(( END_TIME - START_TIME ))

echo "================================================================"
echo " Smoke test completed successfully in ${TOTAL_TIME} seconds (< 5 minutes)!"
echo " All raw logs, processed metrics, and doc summaries verified."
echo "================================================================"
