#!/usr/bin/env python3
"""
GC Unified Log Parser (scripts/parse_gc.py)
Extracts GC pause metrics, overhead, peak heap, and throughput from OpenJDK 21 unified GC logs.
Stdlib only.
"""

import sys
import os
import re
import json
import math
import argparse
from pathlib import Path

def parse_size_mb(num_str, unit_char):
    val = float(num_str)
    unit = unit_char.upper()
    if unit == 'K':
        return val / 1024.0
    elif unit == 'M':
        return val
    elif unit == 'G':
        return val * 1024.0
    return val

def compute_percentiles(values):
    if not values:
        return {"p50": 0.0, "p95": 0.0, "p99": 0.0, "max": 0.0}
    s = sorted(values)
    n = len(s)
    
    def get_pct(p):
        k = (n - 1) * (p / 100.0)
        f = math.floor(k)
        c = math.ceil(k)
        if f == c:
            return s[int(k)]
        d0 = s[int(f)] * (c - k)
        d1 = s[int(c)] * (k - f)
        return d0 + d1

    return {
        "p50": round(get_pct(50), 4),
        "p95": round(get_pct(95), 4),
        "p99": round(get_pct(99), 4),
        "max": round(s[-1], 4)
    }

def parse_gc_log(log_path, out_path=None):
    if not os.path.exists(log_path):
        raise FileNotFoundError(f"GC log file not found: {log_path}")

    pauses = []
    peak_heap_mb = 0.0
    last_uptime_sec = 0.0

    pause_re = re.compile(r"Pause\s+.*?([0-9]+(?:\.[0-9]+)?)\s*ms")
    heap_re = re.compile(r"([0-9]+(?:\.[0-9]+)?)\s*([KkMmGg])(?:\([0-9.]+%\))?->([0-9]+(?:\.[0-9]+)?)\s*([KkMmGg])")
    uptime_re = re.compile(r"\[([0-9]+(?:\.[0-9]+)?)s\]")

    with open(log_path, 'r', encoding='utf-8', errors='replace') as f:
        for line in f:
            m_uptime = uptime_re.search(line)
            if m_uptime:
                try:
                    last_uptime_sec = max(last_uptime_sec, float(m_uptime.group(1)))
                except ValueError:
                    pass

            m_pause = pause_re.search(line)
            if m_pause:
                try:
                    p_val = float(m_pause.group(1))
                    pauses.append(p_val)
                except ValueError:
                    pass

            m_heap = heap_re.search(line)
            if m_heap:
                try:
                    before_mb = parse_size_mb(m_heap.group(1), m_heap.group(2))
                    after_mb = parse_size_mb(m_heap.group(3), m_heap.group(4))
                    peak_heap_mb = max(peak_heap_mb, before_mb, after_mb)
                except ValueError:
                    pass

    # Extract throughput and measured duration from companion .out file if present
    throughput_ops_sec = None
    harness_duration_sec = None
    if out_path and os.path.exists(out_path):
        with open(out_path, 'r', encoding='utf-8', errors='replace') as f:
            content = f.read()
            m_json = re.search(r"JSON_REPORT_BEGIN\s*(\{.*?\})\s*JSON_REPORT_END", content, re.DOTALL)
            if m_json:
                try:
                    data = json.loads(m_json.group(1))
                    throughput_ops_sec = data.get("throughput_ops_sec")
                    harness_duration_sec = data.get("duration_sec")
                except json.JSONDecodeError:
                    pass

    wall_duration_sec = harness_duration_sec if harness_duration_sec else last_uptime_sec
    total_pause_ms = sum(pauses)
    pcts = compute_percentiles(pauses)

    gc_overhead_pct = 0.0
    if wall_duration_sec and wall_duration_sec > 0:
        gc_overhead_pct = (total_pause_ms / (wall_duration_sec * 1000.0)) * 100.0

    return {
        "log_file": os.path.basename(log_path),
        "total_pause_ms": round(total_pause_ms, 4),
        "pause_count": len(pauses),
        "p50_pause_ms": pcts["p50"],
        "p95_pause_ms": pcts["p95"],
        "p99_pause_ms": pcts["p99"],
        "max_pause_ms": pcts["max"],
        "gc_overhead_pct": round(gc_overhead_pct, 4),
        "peak_heap_mb": round(peak_heap_mb, 2),
        "throughput_ops_sec": throughput_ops_sec,
        "wall_duration_sec": round(wall_duration_sec, 4) if wall_duration_sec else 0.0
    }

def process_gc_directory(gc_dir, out_csv=None, out_json=None):
    gc_dir = Path(gc_dir)
    log_files = sorted(list(gc_dir.glob("*.log")))
    results = []

    for log_file in log_files:
        out_file = log_file.with_suffix(".out")
        metrics = parse_gc_log(str(log_file), str(out_file) if out_file.exists() else None)
        
        # Determine collector and heap from filename pattern: <collector>_<heap>_rep<rep>.log
        stem = log_file.stem
        parts = stem.split("_")
        if len(parts) >= 3:
            collector = parts[0]
            if parts[1] == "Gen":
                collector = "ZGC_Gen"
                heap = parts[2]
                rep = parts[3].replace("rep", "")
            else:
                heap = parts[1]
                rep = parts[2].replace("rep", "")
        else:
            collector = stem
            heap = "unknown"
            rep = "1"

        metrics["collector"] = collector
        metrics["heap"] = heap
        metrics["repetition"] = int(rep) if rep.isdigit() else 1
        results.append(metrics)

    # Group by collector and heap to compute means across repetitions
    grouped = {}
    for r in results:
        key = (r["collector"], r["heap"])
        grouped.setdefault(key, []).append(r)

    summary_rows = []
    for (col, hp), reps in sorted(grouped.items()):
        cnt = len(reps)
        avg_thpt = sum(x["throughput_ops_sec"] or 0 for x in reps) / cnt
        avg_overhead = sum(x["gc_overhead_pct"] for x in reps) / cnt
        avg_p50 = sum(x["p50_pause_ms"] for x in reps) / cnt
        avg_p95 = sum(x["p95_pause_ms"] for x in reps) / cnt
        avg_p99 = sum(x["p99_pause_ms"] for x in reps) / cnt
        max_pause = max(x["max_pause_ms"] for x in reps)
        avg_total_pause = sum(x["total_pause_ms"] for x in reps) / cnt
        avg_pause_cnt = sum(x["pause_count"] for x in reps) / cnt
        max_peak_heap = max(x["peak_heap_mb"] for x in reps)

        summary_rows.append({
            "collector": col,
            "heap": hp,
            "repetitions": cnt,
            "throughput_ops_sec": round(avg_thpt, 2),
            "gc_overhead_pct": round(avg_overhead, 4),
            "p50_pause_ms": round(avg_p50, 4),
            "p95_pause_ms": round(avg_p95, 4),
            "p99_pause_ms": round(avg_p99, 4),
            "max_pause_ms": round(max_pause, 4),
            "total_pause_ms": round(avg_total_pause, 4),
            "pause_count": round(avg_pause_cnt, 1),
            "peak_heap_mb": round(max_peak_heap, 2)
        })

    if out_json:
        os.makedirs(os.path.dirname(out_json), exist_ok=True)
        with open(out_json, "w", encoding="utf-8") as f:
            json.dump({"runs": results, "summary": summary_rows}, f, indent=2)

    if out_csv:
        os.makedirs(os.path.dirname(out_csv), exist_ok=True)
        with open(out_csv, "w", encoding="utf-8") as f:
            headers = [
                "collector", "heap", "repetitions", "throughput_ops_sec",
                "gc_overhead_pct", "p50_pause_ms", "p95_pause_ms", "p99_pause_ms",
                "max_pause_ms", "total_pause_ms", "pause_count", "peak_heap_mb"
            ]
            f.write(",".join(headers) + "\n")
            for row in summary_rows:
                f.write(",".join(str(row[h]) for h in headers) + "\n")

    return {"runs": results, "summary": summary_rows}

def main():
    parser = argparse.ArgumentParser(description="Parse unified GC logs and compute performance metrics")
    parser.add_argument("--file", help="Path to single GC log file to parse")
    parser.add_argument("--out", help="Path to companion harness output file")
    parser.add_argument("--dir", help="Directory of GC logs to parse in batch")
    parser.add_argument("--output-csv", help="Path to write processed summary CSV")
    parser.add_argument("--output-json", help="Path to write processed summary JSON")

    args = parser.parse_args()

    if args.file:
        res = parse_gc_log(args.file, args.out)
        print(json.dumps(res, indent=2))
    elif args.dir:
        res = process_gc_directory(args.dir, args.output_csv, args.output_json)
        print(f"Processed {len(res['runs'])} logs across {len(res['summary'])} collector/heap configurations.")
    else:
        parser.print_help()
        sys.exit(1)

if __name__ == "__main__":
    main()
