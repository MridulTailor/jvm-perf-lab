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
JAVA_CMD="${JAVA_HOME}/bin/java"

MODE="${1:-FULL}"

RAW_PROFILE_DIR="${PROJECT_DIR}/results/raw/profile"
PROCESSED_DIR="${PROJECT_DIR}/results/processed"
mkdir -p "${RAW_PROFILE_DIR}" "${PROCESSED_DIR}"

CP="${PROJECT_DIR}/target/classes"
if [ ! -f "${CP}/lab/harness/GcHarness.class" ]; then
    echo "Compiling harness..."
    (cd "${PROJECT_DIR}" && JAVA_HOME="${JAVA_HOME}" mvn compile -DskipTests)
fi

if [ "$MODE" = "QUICK" ]; then
    DURATION=3
    WARMUP=1
    echo "=== Running Profiling in QUICK mode (${DURATION}s window) ==="
else
    DURATION=15
    WARMUP=3
    echo "=== Running Profiling in FULL mode (${DURATION}s window) ==="
fi

# Locate async-profiler dylib or check fallback
ASPROF_LIB=""
if [ -f "/opt/homebrew/lib/libasyncProfiler.dylib" ]; then
    ASPROF_LIB="/opt/homebrew/lib/libasyncProfiler.dylib"
elif [ -f "/opt/homebrew/Cellar/async-profiler/4.5/lib/libasyncProfiler.dylib" ]; then
    ASPROF_LIB="/opt/homebrew/Cellar/async-profiler/4.5/lib/libasyncProfiler.dylib"
fi

PROFILER_USED="async-profiler 4.5"

if [ -n "$ASPROF_LIB" ] && [ -f "$ASPROF_LIB" ]; then
    echo "Using async-profiler agent: ${ASPROF_LIB}"

    # 1. CPU profile on PointerChasing workload
    echo "1. Profiling CPU-bound workload (PointerChasing)..."
    CPU_FLAME="${RAW_PROFILE_DIR}/cpu_flame.html"
    CPU_TEXT="${RAW_PROFILE_DIR}/cpu_profile.txt"
    "$JAVA_CMD" -agentpath:"${ASPROF_LIB}=start,event=cpu,file=${CPU_FLAME}" \
        -cp "${CP}" lab.harness.GcHarness --workload pointer --duration "${DURATION}" --warmup "${WARMUP}" >/dev/null
    "$JAVA_CMD" -agentpath:"${ASPROF_LIB}=start,event=cpu,file=${CPU_TEXT}" \
        -cp "${CP}" lab.harness.GcHarness --workload pointer --duration "${DURATION}" --warmup "${WARMUP}" >/dev/null

    # 2. Alloc profile on AllocationHeavy workload
    echo "2. Profiling Allocation-heavy workload (AllocationHeavy)..."
    ALLOC_FLAME="${RAW_PROFILE_DIR}/alloc_flame.html"
    ALLOC_TEXT="${RAW_PROFILE_DIR}/alloc_profile.txt"
    "$JAVA_CMD" -agentpath:"${ASPROF_LIB}=start,event=alloc,file=${ALLOC_FLAME}" \
        -cp "${CP}" lab.harness.GcHarness --workload allocation --duration "${DURATION}" --warmup "${WARMUP}" >/dev/null
    "$JAVA_CMD" -agentpath:"${ASPROF_LIB}=start,event=alloc,file=${ALLOC_TEXT}" \
        -cp "${CP}" lab.harness.GcHarness --workload allocation --duration "${DURATION}" --warmup "${WARMUP}" >/dev/null

else
    echo "async-profiler dylib not found; falling back to JDK Flight Recorder (JFR)..."
    PROFILER_USED="JDK Flight Recorder (JFR)"

    CPU_JFR="${RAW_PROFILE_DIR}/cpu_profile.jfr"
    ALLOC_JFR="${RAW_PROFILE_DIR}/alloc_profile.jfr"
    CPU_TEXT="${RAW_PROFILE_DIR}/cpu_profile.txt"
    ALLOC_TEXT="${RAW_PROFILE_DIR}/alloc_profile.txt"

    "$JAVA_CMD" -XX:StartFlightRecording=filename="${CPU_JFR}",settings=profile \
        -cp "${CP}" lab.harness.GcHarness --workload pointer --duration "${DURATION}" --warmup "${WARMUP}" >/dev/null
    "${JAVA_HOME}/bin/jfr" print --events jdk.ExecutionSample "${CPU_JFR}" > "${CPU_TEXT}"

    "$JAVA_CMD" -XX:StartFlightRecording=filename="${ALLOC_JFR}",settings=profile \
        -cp "${CP}" lab.harness.GcHarness --workload allocation --duration "${DURATION}" --warmup "${WARMUP}" >/dev/null
    "${JAVA_HOME}/bin/jfr" print --events jdk.ObjectAllocationInNewTLAB,jdk.ObjectAllocationOutsideTLAB "${ALLOC_JFR}" > "${ALLOC_TEXT}"
fi

# Record profiler metadata
cat <<EOF > "${PROCESSED_DIR}/profiling_meta.json"
{
  "profiler_used": "${PROFILER_USED}",
  "mode": "${MODE}",
  "duration_sec": ${DURATION},
  "cpu_workload": "PointerChasing",
  "alloc_workload": "AllocationHeavy"
}
EOF

# Parse top 10 frames
echo "3. Extracting top 10 frames for CPU-bound profile..."
python3 "${PROJECT_DIR}/scripts/parse_profile.py" --file "${RAW_PROFILE_DIR}/cpu_profile.txt" \
    --output-csv "${PROCESSED_DIR}/profile_top10_cpu.csv"

echo "4. Extracting top 10 frames for Allocation-heavy profile..."
python3 "${PROJECT_DIR}/scripts/parse_profile.py" --file "${RAW_PROFILE_DIR}/alloc_profile.txt" \
    --output-csv "${PROCESSED_DIR}/profile_top10_alloc.csv"

echo "Profiling pipeline completed successfully."
