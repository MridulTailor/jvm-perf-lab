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

RAW_DIR="${PROJECT_DIR}/results/raw/jmh"
mkdir -p "${RAW_DIR}"

# Build benchmarks jar if needed
if [ ! -f "${PROJECT_DIR}/target/benchmarks.jar" ]; then
    echo "Building benchmarks.jar..."
    (cd "${PROJECT_DIR}" && JAVA_HOME="${JAVA_HOME}" mvn clean package -DskipTests)
fi

if [ "$MODE" = "QUICK" ]; then
    echo "=== Running JMH in QUICK mode (development / smoke) ==="
    JMH_OPTS="-f 1 -wi 1 -i 2 -r 500ms -w 500ms"
else
    echo "=== Running JMH in FULL mode (reproducible production results) ==="
    JMH_OPTS="-f 3 -wi 5 -i 10 -r 1s -w 1s"
fi

echo "1. Running Escape Analysis experiments (Default vs -XX:-DoEscapeAnalysis)..."
"$JAVA_CMD" -jar "${PROJECT_DIR}/target/benchmarks.jar" "EscapeAnalysisBenchmark.*" \
    ${JMH_OPTS} -prof gc -rf json -rff "${RAW_DIR}/ea_default.json"

"$JAVA_CMD" -jar "${PROJECT_DIR}/target/benchmarks.jar" "EscapeAnalysisBenchmark.*" \
    ${JMH_OPTS} -jvmArgs "-XX:-DoEscapeAnalysis" -prof gc -rf json -rff "${RAW_DIR}/ea_disabled.json"

echo "2. Running Inlining & Polymorphism experiments..."
"$JAVA_CMD" -jar "${PROJECT_DIR}/target/benchmarks.jar" "InliningBenchmark.*" \
    ${JMH_OPTS} -prof gc -rf json -rff "${RAW_DIR}/inlining_default.json"

"$JAVA_CMD" -jar "${PROJECT_DIR}/target/benchmarks.jar" "InliningBenchmark.*" \
    ${JMH_OPTS} -jvmArgs "-XX:-Inline" -prof gc -rf json -rff "${RAW_DIR}/inlining_disabled.json"

"$JAVA_CMD" -jar "${PROJECT_DIR}/target/benchmarks.jar" "InliningBenchmark.*" \
    ${JMH_OPTS} -jvmArgs "-XX:MaxInlineSize=100 -XX:FreqInlineSize=500" -prof gc -rf json -rff "${RAW_DIR}/inlining_thresholds.json"

echo "3. Running Core Workload benchmarks (PointerChasing and AllocationHeavy)..."
"$JAVA_CMD" -jar "${PROJECT_DIR}/target/benchmarks.jar" "PointerChasingBenchmark.*|AllocationBenchmark.*" \
    ${JMH_OPTS} -prof gc -rf json -rff "${RAW_DIR}/workloads.json"

echo "JMH microbenchmark suite completed successfully. Results in ${RAW_DIR}"
