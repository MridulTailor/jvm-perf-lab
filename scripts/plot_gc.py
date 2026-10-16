#!/usr/bin/env python3
"""
GC Pause Latency Comparison Plotter (scripts/plot_gc.py)
Generates vector SVG chart comparing pause latency (p50, p99, Max)
across all collectors on a log-scale.
Stdlib only.
"""

import sys
import os
import csv
import math
from pathlib import Path

def plot_gc_chart(csv_path, output_svg_path):
    if not os.path.exists(csv_path):
        print(f"Error: {csv_path} not found")
        return

    rows_2g = []
    with open(csv_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for r in reader:
            if r["heap"] == "2g":
                rows_2g.append(r)

    # Sort collectors logically: low-latency first or traditional first
    col_order = ["ZGC", "ZGC_Gen", "Shenandoah", "G1", "Parallel", "Serial"]
    rows_2g.sort(key=lambda x: col_order.index(x["collector"]) if x["collector"] in col_order else 99)

    width = 850
    height = 520
    margin = {"top": 60, "right": 50, "bottom": 80, "left": 90}
    plot_width = width - margin["left"] - margin["right"]
    plot_height = height - margin["top"] - margin["bottom"]

    # Log scale from 0.001 ms (1 microsecond) to 1000 ms (1 second)
    log_min = -3.0  # 10^-3 = 0.001 ms
    log_max = 3.0   # 10^3 = 1000 ms

    def scale_y(val_ms):
        v = max(val_ms, 0.001)
        log_v = math.log10(v)
        norm = (log_v - log_min) / (log_max - log_min)
        return margin["top"] + (1.0 - norm) * plot_height

    svg = []
    svg.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}">')
    svg.append('  <style>')
    svg.append('    text { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }')
    svg.append('    .axis { stroke: #64748b; stroke-width: 1.5; }')
    svg.append('    .grid { stroke: #e2e8f0; stroke-width: 1; stroke-dasharray: 4,4; }')
    svg.append('    .title { font-size: 18px; font-weight: 700; fill: #0f172a; text-anchor: middle; }')
    svg.append('    .subtitle { font-size: 12px; fill: #64748b; text-anchor: middle; }')
    svg.append('    .axis-label { font-size: 13px; font-weight: 600; fill: #334155; }')
    svg.append('    .tick { font-size: 11px; fill: #64748b; }')
    svg.append('    .bar-label { font-size: 11px; font-weight: 600; fill: #1e293b; text-anchor: middle; }')
    svg.append('    .legend-text { font-size: 12px; fill: #1e293b; }')
    svg.append('  </style>')

    # Background
    svg.append(f'  <rect width="{width}" height="{height}" fill="#ffffff"/>')

    # Title
    svg.append(f'  <text x="{width/2}" y="28" class="title">Garbage Collector Pause Latency Comparison (2GB Heap)</text>')
    svg.append(f'  <text x="{width/2}" y="48" class="subtitle">Log-Scale Pause Time Distribution (p50, p99, Max Pause in milliseconds)</text>')

    # Grid & Y ticks for decades 0.001, 0.01, 0.1, 1, 10, 100, 1000
    y_ticks = [
        (0.001, "0.001 ms (1µs)"),
        (0.01, "0.01 ms (10µs)"),
        (0.1, "0.1 ms (100µs)"),
        (1.0, "1 ms"),
        (10.0, "10 ms"),
        (100.0, "100 ms"),
        (1000.0, "1,000 ms (1s)")
    ]

    for val, label in y_ticks:
        y = scale_y(val)
        svg.append(f'  <line x1="{margin["left"]}" y1="{y:.1f}" x2="{width - margin["right"]}" y2="{y:.1f}" class="grid"/>')
        svg.append(f'  <text x="{margin["left"] - 10}" y="{y + 4:.1f}" class="tick" text-anchor="end">{label}</text>')

    # Draw Axes
    svg.append(f'  <line x1="{margin["left"]}" y1="{margin["top"]}" x2="{margin["left"]}" y2="{height - margin["bottom"]}" class="axis"/>')
    svg.append(f'  <line x1="{margin["left"]}" y1="{height - margin["bottom"]}" x2="{width - margin["right"]}" y2="{height - margin["bottom"]}" class="axis"/>')

    # Y-axis Label
    svg.append(f'  <text x="20" y="{height/2}" class="axis-label" text-anchor="middle" transform="rotate(-90 20 {height/2})">Pause Latency (log scale, ms)</text>')

    # Draw grouped bars for each collector
    num_cols = len(rows_2g)
    group_width = plot_width / max(num_cols, 1)
    bar_width = 22

    colors = {
        "p50": "#3b82f6",   # Blue
        "p99": "#f59e0b",   # Amber
        "max": "#ef4444"    # Red
    }

    base_y = height - margin["bottom"]

    for idx, r in enumerate(rows_2g):
        col_name = r["collector"]
        gx = margin["left"] + idx * group_width + (group_width - bar_width * 3 - 8) / 2

        p50 = float(r["p50_pause_ms"])
        p99 = float(r["p99_pause_ms"])
        max_p = float(r["max_pause_ms"])

        # p50 bar
        y50 = scale_y(p50)
        h50 = base_y - y50
        svg.append(f'  <rect x="{gx}" y="{y50:.1f}" width="{bar_width}" height="{h50:.1f}" fill="{colors["p50"]}" rx="2"/>')

        # p99 bar
        y99 = scale_y(p99)
        h99 = base_y - y99
        svg.append(f'  <rect x="{gx + bar_width + 4}" y="{y99:.1f}" width="{bar_width}" height="{h99:.1f}" fill="{colors["p99"]}" rx="2"/>')

        # max bar
        ymax = scale_y(max_p)
        hmax = base_y - ymax
        svg.append(f'  <rect x="{gx + (bar_width + 4) * 2}" y="{ymax:.1f}" width="{bar_width}" height="{hmax:.1f}" fill="{colors["max"]}" rx="2"/>')

        # Collector name label under group
        center_x = gx + (bar_width * 3 + 8) / 2
        svg.append(f'  <text x="{center_x:.1f}" y="{base_y + 22}" class="bar-label">{col_name}</text>')
        thpt_str = f"{float(r['throughput_ops_sec']):,.0f} ops/s"
        svg.append(f'  <text x="{center_x:.1f}" y="{base_y + 38}" class="tick" text-anchor="middle">{thpt_str}</text>')

    # Legend at top right
    leg_x = width - margin["right"] - 220
    leg_y = margin["top"] + 15
    for i, (k, label, col) in enumerate([("p50", "p50 Pause", colors["p50"]),
                                          ("p99", "p99 Pause", colors["p99"]),
                                          ("max", "Max Pause", colors["max"])]):
        ly = leg_y + i * 20
        svg.append(f'  <rect x="{leg_x}" y="{ly}" width="14" height="14" fill="{col}" rx="2"/>')
        svg.append(f'  <text x="{leg_x + 22}" y="{ly + 12}" class="legend-text">{label}</text>')

    svg.append('</svg>')

    os.makedirs(os.path.dirname(output_svg_path), exist_ok=True)
    with open(output_svg_path, 'w', encoding='utf-8') as f:
        f.write("\n".join(svg))
    print(f"Generated GC pause latency chart: {output_svg_path}")

def main():
    csv_file = Path("results/processed/gc_summary.csv")
    svg_file = Path("docs/gc_pauses.svg")
    if len(sys.argv) > 1:
        csv_file = Path(sys.argv[1])
    if len(sys.argv) > 2:
        svg_file = Path(sys.argv[2])
    plot_gc_chart(str(csv_file), str(svg_file))

if __name__ == "__main__":
    main()
