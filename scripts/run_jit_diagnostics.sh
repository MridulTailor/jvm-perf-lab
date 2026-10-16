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

RAW_JIT_DIR="${PROJECT_DIR}/results/raw/jit"
PROCESSED_DIR="${PROJECT_DIR}/results/processed"
mkdir -p "${RAW_JIT_DIR}" "${PROCESSED_DIR}"

CP="${PROJECT_DIR}/target/classes"

echo "Running JIT compilation & inlining diagnostics..."
JIT_LOG="${RAW_JIT_DIR}/inlining_jit.log"
JIT_JSON="${PROCESSED_DIR}/jit_summary.json"

"$JAVA_CMD" \
    -XX:+PrintCompilation \
    -XX:+UnlockDiagnosticVMOptions \
    -XX:+PrintInlining \
    -cp "${CP}" lab.harness.WarmupRunner --iterations 20 --window-ms 100 --label "jit_diag" > "${JIT_LOG}" 2>&1

echo "Parsing JIT diagnostics log..."
python3 "${PROJECT_DIR}/scripts/parse_jit.py" --file "${JIT_LOG}" --output-json "${JIT_JSON}"
echo "JIT diagnostics complete: ${JIT_JSON}"
