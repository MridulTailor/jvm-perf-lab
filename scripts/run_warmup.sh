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

RAW_DIR="${PROJECT_DIR}/results/raw/warmup"
PROCESSED_CSV="${PROJECT_DIR}/results/processed/warmup_curve.csv"
SVG_CHART="${PROJECT_DIR}/docs/warmup_curve.svg"

mkdir -p "${RAW_DIR}" "${PROJECT_DIR}/results/processed" "${PROJECT_DIR}/docs"

CP="${PROJECT_DIR}/target/classes"
if [ ! -f "${CP}/lab/harness/WarmupRunner.class" ]; then
    echo "Compiling harness..."
    (cd "${PROJECT_DIR}" && JAVA_HOME="${JAVA_HOME}" mvn compile -DskipTests)
fi

if [ "$MODE" = "QUICK" ]; then
    ITERS=5
    WINDOW_MS=100
    INTERP_ITERS=3
    echo "=== Running Warmup Curve experiment in QUICK mode ==="
else
    ITERS=25
    WINDOW_MS=200
    INTERP_ITERS=10
    echo "=== Running Warmup Curve experiment in FULL mode ==="
fi

echo "1. Running Default Tiered Compilation (C1 + C2)..."
"$JAVA_CMD" -cp "${CP}" lab.harness.WarmupRunner \
    --label "Tiered_Default" --iterations "${ITERS}" --window-ms "${WINDOW_MS}" > "${RAW_DIR}/tiered_default.out"

echo "2. Running C1-Only (-XX:TieredStopAtLevel=1)..."
"$JAVA_CMD" -XX:TieredStopAtLevel=1 -cp "${CP}" lab.harness.WarmupRunner \
    --label "C1_Only" --iterations "${ITERS}" --window-ms "${WINDOW_MS}" > "${RAW_DIR}/c1_only.out"

echo "3. Running Interpreter Baseline (-Xint)..."
"$JAVA_CMD" -Xint -cp "${CP}" lab.harness.WarmupRunner \
    --label "Interpreter" --iterations "${INTERP_ITERS}" --window-ms "${WINDOW_MS}" > "${RAW_DIR}/interpreter.out"

# Aggregate CSV
echo "config,iteration,elapsed_ms,ops,throughput_ops_sec" > "${PROCESSED_CSV}"
for f in "${RAW_DIR}/tiered_default.out" "${RAW_DIR}/c1_only.out" "${RAW_DIR}/interpreter.out"; do
    sed -n '/WARMUP_CSV_BEGIN/,/WARMUP_CSV_END/p' "$f" | grep -v "WARMUP_CSV" | grep -v "config,iteration" >> "${PROCESSED_CSV}"
done

echo "Warmup data consolidated into ${PROCESSED_CSV}"

# Generate Chart
python3 "${PROJECT_DIR}/scripts/plot_warmup.py" "${PROCESSED_CSV}" "${SVG_CHART}"
