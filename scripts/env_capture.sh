#!/usr/bin/env bash
set -euo pipefail

# Determine script and project directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Resolve Java Home
if [ -d "${PROJECT_DIR}/.jdk/amazon-corretto-21.jdk/Contents/Home" ]; then
    JAVA_HOME="${PROJECT_DIR}/.jdk/amazon-corretto-21.jdk/Contents/Home"
elif [ -z "${JAVA_HOME:-}" ]; then
    if [ -x "/usr/libexec/java_home" ]; then
        JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home)"
    else
        JAVA_BIN="$(which java)"
        JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
    fi
fi

JAVA_CMD="${JAVA_HOME}/bin/java"

if [ ! -x "$JAVA_CMD" ]; then
    echo "Error: java not executable at $JAVA_CMD" >&2
    exit 1
fi

OS_NAME="$(uname -s)"
OS_RELEASE="$(uname -r)"
ARCH="$(uname -m)"

if [ "$OS_NAME" = "Darwin" ]; then
    OS_PRETTY="macOS $(sw_vers -productVersion) ($(sw_vers -buildVersion))"
    CPU_BRAND="$(sysctl -n machdep.cpu.brand_string 2>/dev/null || echo "Apple Silicon")"
    CORES_PHYSICAL="$(sysctl -n hw.physicalcpu 2>/dev/null || echo "unknown")"
    CORES_LOGICAL="$(sysctl -n hw.logicalcpu 2>/dev/null || echo "unknown")"
    MEM_BYTES="$(sysctl -n hw.memsize 2>/dev/null || echo "0")"
    MEM_GB=$(( MEM_BYTES / 1024 / 1024 / 1024 ))
else
    OS_PRETTY="Linux $(uname -r)"
    CPU_BRAND="$(grep -m 1 'model name' /proc/cpuinfo | cut -d: -f2 | xargs || uname -m)"
    CORES_PHYSICAL="$(grep -c '^processor' /proc/cpuinfo 2>/dev/null || echo "unknown")"
    CORES_LOGICAL="$CORES_PHYSICAL"
    MEM_GB=$(( $(grep MemTotal /proc/meminfo | awk '{print $2}') / 1024 / 1024 ))
fi

# JDK Details
JAVA_VERSION_OUTPUT="$("$JAVA_CMD" -version 2>&1)"
JDK_VERSION="$("$JAVA_CMD" -XshowSettings:properties -version 2>&1 | grep "java.version =" | awk '{print $3}')"
JDK_VENDOR="$("$JAVA_CMD" -XshowSettings:properties -version 2>&1 | grep "java.vendor =" | awk '{print $3}')"
VM_NAME="$("$JAVA_CMD" -XshowSettings:properties -version 2>&1 | grep "java.vm.name =" | awk -F= '{print $2}' | xargs)"

# Check GC flags support
check_gc() {
    local flags="$1"
    if "$JAVA_CMD" $flags -version >/dev/null 2>&1; then
        echo "supported"
    else
        echo "unsupported on ${OS_NAME}/${ARCH}/${JDK_VERSION}"
    fi
}

GC_SERIAL="$(check_gc "-XX:+UseSerialGC")"
GC_PARALLEL="$(check_gc "-XX:+UseParallelGC")"
GC_G1="$(check_gc "-XX:+UseG1GC")"
GC_ZGC="$(check_gc "-XX:+UseZGC")"
GC_ZGC_GEN="$(check_gc "-XX:+UseZGC -XX:+ZGenerational")"
GC_SHENANDOAH="$(check_gc "-XX:+UseShenandoahGC")"

# Profiler tools check
ASPROF_BIN="$(which asprof 2>/dev/null || echo "")"
ASPROF_VERSION=""
if [ -n "$ASPROF_BIN" ]; then
    ASPROF_VERSION="$("$ASPROF_BIN" --version 2>&1 | head -n 1)"
fi

JFR_BIN="${JAVA_HOME}/bin/jfr"
JFR_AVAILABLE="false"
if [ -x "$JFR_BIN" ]; then
    JFR_AVAILABLE="true"
fi

TIMESTAMP="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

mkdir -p "${PROJECT_DIR}/results"

cat <<EOF > "${PROJECT_DIR}/results/env.json"
{
  "timestamp": "${TIMESTAMP}",
  "hardware": {
    "os": "${OS_NAME}",
    "os_version": "${OS_PRETTY}",
    "kernel": "${OS_RELEASE}",
    "arch": "${ARCH}",
    "cpu": "${CPU_BRAND}",
    "physical_cores": ${CORES_PHYSICAL},
    "logical_cores": ${CORES_LOGICAL},
    "total_memory_gb": ${MEM_GB}
  },
  "jdk": {
    "java_home": "${JAVA_HOME}",
    "version": "${JDK_VERSION}",
    "vendor": "${JDK_VENDOR}",
    "vm_name": "${VM_NAME}",
    "raw_version": $(echo "$JAVA_VERSION_OUTPUT" | python3 -c 'import sys, json; print(json.dumps(sys.stdin.read()))')
  },
  "collectors": {
    "SerialGC": "${GC_SERIAL}",
    "ParallelGC": "${GC_PARALLEL}",
    "G1GC": "${GC_G1}",
    "ZGC": "${GC_ZGC}",
    "ZGC_Generational": "${GC_ZGC_GEN}",
    "ShenandoahGC": "${GC_SHENANDOAH}"
  },
  "profiling": {
    "async_profiler_path": "${ASPROF_BIN}",
    "async_profiler_version": "${ASPROF_VERSION}",
    "jfr_cli_available": ${JFR_AVAILABLE}
  }
}
EOF

echo "Environment metadata captured to ${PROJECT_DIR}/results/env.json"
cat "${PROJECT_DIR}/results/env.json"
