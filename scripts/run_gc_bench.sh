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
SELECTED_GC="${2:-ALL}"

RAW_GC_DIR="${PROJECT_DIR}/results/raw/gc"
mkdir -p "${RAW_GC_DIR}"

# Build classes if needed
if [ ! -d "${PROJECT_DIR}/target/classes/lab/harness" ]; then
    echo "Compiling harness..."
    (cd "${PROJECT_DIR}" && JAVA_HOME="${JAVA_HOME}" mvn compile -DskipTests)
fi

CP="${PROJECT_DIR}/target/classes"

if [ "$MODE" = "QUICK" ]; then
    DURATION=3
    WARMUP=1
    REPETITIONS=1
    HEAPS=("512m" "2g")
    echo "=== Running GC experiments in QUICK mode (${DURATION}s measure, ${REPETITIONS} rep) ==="
else
    DURATION=60
    WARMUP=5
    REPETITIONS=3
    HEAPS=("512m" "2g")
    echo "=== Running GC experiments in FULL mode (${DURATION}s measure, ${REPETITIONS} reps) ==="
fi

get_gc_flags() {
    case "$1" in
        "Serial") echo "-XX:+UseSerialGC" ;;
        "Parallel") echo "-XX:+UseParallelGC" ;;
        "G1") echo "-XX:+UseG1GC" ;;
        "ZGC") echo "-XX:+UseZGC" ;;
        "ZGC_Gen") echo "-XX:+UseZGC -XX:+ZGenerational" ;;
        "Shenandoah") echo "-XX:+UseShenandoahGC" ;;
        *) echo "" ;;
    esac
}

COLLECTORS=("Serial" "Parallel" "G1" "ZGC" "ZGC_Gen" "Shenandoah")

for collector in "${COLLECTORS[@]}"; do
    if [ "$SELECTED_GC" != "ALL" ] && [ "$SELECTED_GC" != "$collector" ]; then
        continue
    fi

    flags="$(get_gc_flags "$collector")"
    # Verify collector support first
    if ! "$JAVA_CMD" $flags -version >/dev/null 2>&1; then
        echo "Skipping unsupported collector: $collector"
        continue
    fi

    for heap in "${HEAPS[@]}"; do
        for rep in $(seq 1 $REPETITIONS); do
            LOG_FILE="${RAW_GC_DIR}/${collector}_${heap}_rep${rep}.log"
            OUT_FILE="${RAW_GC_DIR}/${collector}_${heap}_rep${rep}.out"

            echo "[GC Experiment] Collector: ${collector} | Heap: ${heap} | Repetition: ${rep}/${REPETITIONS}"
            
            GC_LOG_OPT="-Xlog:gc*:file=${LOG_FILE}:time,uptime,pid:filecount=1,filesize=100M"
            HEAP_OPTS="-Xms${heap} -Xmx${heap}"

            # Run GcHarness in a separate, isolated JVM process
            "$JAVA_CMD" ${HEAP_OPTS} ${flags} "${GC_LOG_OPT}" \
                -cp "${CP}" lab.harness.GcHarness \
                --workload allocation \
                --duration "${DURATION}" \
                --warmup "${WARMUP}" \
                --threads 1 \
                --retention 0.05 \
                --cache-capacity 50000 \
                --seed "$(( 42 + rep ))" > "${OUT_FILE}" 2>&1

            echo "  Saved log: ${LOG_FILE}"
            echo "  Saved out: ${OUT_FILE}"
        done
    done
done

echo "GC benchmark execution completed. Logs located in ${RAW_GC_DIR}"
