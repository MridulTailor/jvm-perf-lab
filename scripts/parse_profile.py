#!/usr/bin/env python3
"""
Profiler Top-Frames Extractor (scripts/parse_profile.py)
Parses async-profiler / collapsed / text profiles and extracts top 10 frames by self-time.
Outputs structured CSV and prints summary metrics.
Stdlib only.
"""

import sys
import os
import re
import csv
import argparse

def parse_profile_text(profile_path, output_csv=None, top_n=10):
    if not os.path.exists(profile_path):
        raise FileNotFoundError(f"Profile file not found: {profile_path}")

    # Async-profiler text output contains sections:
    # --- Execution profile ---
    # Total samples       : 313
    #
    # --- 790000000 ns (25.24%), 79 samples
    #   [ 0] lab.workload.PointerChasing.traverse
    #   [ 1] ...

    total_samples = 0
    frame_samples = {}
    frame_time_or_bytes = {}

    total_samples_re = re.compile(r"Total samples\s*:\s*(\d+)")
    sample_header_re = re.compile(r"---\s*(\d+)\s*(?:ns|bytes)?\s*\(([0-9.]+)%\),\s*(\d+)\s*samples")
    frame_line_re = re.compile(r"^\s*\[\s*0\]\s+(.+)$")  # Top frame (self time)

    current_weight = 0
    current_samples = 0

    with open(profile_path, 'r', encoding='utf-8', errors='replace') as f:
        for line in f:
            m_total = total_samples_re.search(line)
            if m_total:
                total_samples = int(m_total.group(1))
                continue

            m_hdr = sample_header_re.search(line)
            if m_hdr:
                current_weight = int(m_hdr.group(1))
                current_samples = int(m_hdr.group(3))
                continue

            m_top = frame_line_re.match(line)
            if m_top and current_samples > 0:
                frame_name = m_top.group(1).strip()
                frame_samples[frame_name] = frame_samples.get(frame_name, 0) + current_samples
                frame_time_or_bytes[frame_name] = frame_time_or_bytes.get(frame_name, 0) + current_weight
                current_samples = 0
                current_weight = 0

    if not total_samples:
        total_samples = sum(frame_samples.values())

    # Sort frames descending by samples
    sorted_frames = sorted(frame_samples.items(), key=lambda x: x[1], reverse=True)

    rows = []
    cum_pct = 0.0
    for idx, (frame, count) in enumerate(sorted_frames[:top_n], start=1):
        pct = (count / total_samples * 100.0) if total_samples > 0 else 0.0
        cum_pct += pct
        rows.append({
            "rank": idx,
            "frame": frame,
            "samples": count,
            "self_pct": round(pct, 2),
            "cumulative_pct": round(cum_pct, 2)
        })

    if output_csv:
        os.makedirs(os.path.dirname(output_csv), exist_ok=True)
        with open(output_csv, 'w', encoding='utf-8', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=["rank", "frame", "samples", "self_pct", "cumulative_pct"])
            writer.writeheader()
            writer.writerows(rows)
        print(f"Top {len(rows)} frames saved to {output_csv}")

    top_share = rows[-1]["cumulative_pct"] if rows else 0.0
    print(f"Top {len(rows)} frames account for {top_share:.2f}% of total self-time across {total_samples} samples.")
    return rows

def main():
    parser = argparse.ArgumentParser(description="Extract top frames by self-time from profiler output")
    parser.add_argument("--file", required=True, help="Profiler text output file")
    parser.add_argument("--output-csv", help="Path to write top-10 frames CSV")
    parser.add_argument("--top", type=int, default=10, help="Number of frames to extract (default: 10)")

    args = parser.parse_args()
    rows = parse_profile_text(args.file, args.output_csv, args.top)
    print("\nRank | Self% | Cum%  | Frame")
    print("-" * 75)
    for r in rows:
        print(f"{r['rank']:>4} | {r['self_pct']:>5.2f}% | {r['cumulative_pct']:>5.2f}% | {r['frame']}")

if __name__ == "__main__":
    main()
