#!/usr/bin/env python3
"""
JVM Warmup Curve Plotter (scripts/plot_warmup.py)
Reads warmup_curve.csv and renders a high-clarity SVG chart (pure Python stdlib)
and optionally PNG if matplotlib is installed.
"""

import sys
import os
import csv
from pathlib import Path

def generate_svg_chart(data_by_config, output_svg_path):
    width = 800
    height = 500
    margin = {"top": 60, "right": 40, "bottom": 60, "left": 90}
    plot_width = width - margin["left"] - margin["right"]
    plot_height = height - margin["top"] - margin["bottom"]

    # Determine axis ranges
    all_iters = []
    all_thpts = []
    for cfg, rows in data_by_config.items():
        for r in rows:
            all_iters.append(r["iteration"])
            all_thpts.append(r["throughput_ops_sec"])

    if not all_iters or not all_thpts:
        print("Warning: No warmup data to plot.")
        return

    max_iter = max(all_iters)
    min_iter = min(all_iters)
    max_thpt = max(all_thpts) * 1.10  # 10% headroom

    def scale_x(it):
        if max_iter == min_iter:
            return margin["left"] + plot_width / 2.0
        return margin["left"] + ((it - min_iter) / (max_iter - min_iter)) * plot_width

    def scale_y(thpt):
        if max_thpt == 0:
            return margin["top"] + plot_height
        return margin["top"] + (1.0 - (thpt / max_thpt)) * plot_height

    colors = {
        "Tiered_Default": "#2563eb",  # Bright Blue
        "C1_Only": "#16a34a",         # Forest Green
        "Interpreter": "#dc2626"      # Crimson Red
    }

    svg_lines = []
    svg_lines.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}">')
    svg_lines.append('  <style>')
    svg_lines.append('    text { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }')
    svg_lines.append('    .axis { stroke: #94a3b8; stroke-width: 1.5; }')
    svg_lines.append('    .grid { stroke: #e2e8f0; stroke-width: 1; stroke-dasharray: 4,4; }')
    svg_lines.append('    .title { font-size: 18px; font-weight: 700; fill: #0f172a; text-anchor: middle; }')
    svg_lines.append('    .subtitle { font-size: 12px; fill: #64748b; text-anchor: middle; }')
    svg_lines.append('    .axis-label { font-size: 13px; font-weight: 600; fill: #334155; }')
    svg_lines.append('    .tick-label { font-size: 11px; fill: #64748b; }')
    svg_lines.append('    .legend-text { font-size: 12px; font-weight: 600; fill: #1e293b; }')
    svg_lines.append('  </style>')

    # Background
    svg_lines.append(f'  <rect width="{width}" height="{height}" fill="#ffffff"/>')

    # Title & Subtitle
    svg_lines.append(f'  <text x="{width / 2}" y="28" class="title">HotSpot JIT Warmup Curve: Compilation Tiers</text>')
    svg_lines.append(f'  <text x="{width / 2}" y="48" class="subtitle">Default Tiered (C1+C2) vs C1-Only (-XX:TieredStopAtLevel=1) vs Interpreter (-Xint)</text>')

    # Horizontal grid lines (5 steps)
    steps = 5
    for i in range(steps + 1):
        thpt_val = (max_thpt / steps) * i
        y = scale_y(thpt_val)
        svg_lines.append(f'  <line x1="{margin["left"]}" y1="{y:.1f}" x2="{width - margin["right"]}" y2="{y:.1f}" class="grid"/>')
        thpt_str = f"{thpt_val / 1e6:.2f}M" if thpt_val >= 1e6 else (f"{thpt_val / 1e3:.0f}k" if thpt_val >= 1e3 else f"{thpt_val:.0f}")
        svg_lines.append(f'  <text x="{margin["left"] - 10}" y="{y + 4:.1f}" class="tick-label" text-anchor="end">{thpt_str}</text>')

    # Vertical grid lines (every 5 iterations)
    for it in range(min_iter, max_iter + 1):
        if it % 5 == 0 or it == 1 or it == max_iter:
            x = scale_x(it)
            svg_lines.append(f'  <line x1="{x:.1f}" y1="{margin["top"]}" x2="{x:.1f}" y2="{height - margin["bottom"]}" class="grid"/>')
            svg_lines.append(f'  <text x="{x:.1f}" y="{height - margin["bottom"] + 18}" class="tick-label" text-anchor="middle">{it}</text>')

    # Axes
    svg_lines.append(f'  <line x1="{margin["left"]}" y1="{margin["top"]}" x2="{margin["left"]}" y2="{height - margin["bottom"]}" class="axis"/>')
    svg_lines.append(f'  <line x1="{margin["left"]}" y1="{height - margin["bottom"]}" x2="{width - margin["right"]}" y2="{height - margin["bottom"]}" class="axis"/>')

    # Axis Labels
    svg_lines.append(f'  <text x="{width / 2}" y="{height - 15}" class="axis-label" text-anchor="middle">Measurement Iteration (Sequential 200ms Windows)</text>')
    svg_lines.append(f'  <text x="20" y="{height / 2}" class="axis-label" text-anchor="middle" transform="rotate(-90 20 {height / 2})">Throughput (ops / sec)</text>')

    # Plot lines and circles for each configuration
    legend_items = []
    for cfg, color in [("Tiered_Default", "#2563eb"), ("C1_Only", "#16a34a"), ("Interpreter", "#dc2626")]:
        rows = data_by_config.get(cfg, [])
        if not rows:
            continue
        legend_items.append((cfg, color))
        points = []
        for r in rows:
            x = scale_x(r["iteration"])
            y = scale_y(r["throughput_ops_sec"])
            points.append(f"{x:.1f},{y:.1f}")

        # Polyline
        pts_str = " ".join(points)
        svg_lines.append(f'  <polyline points="{pts_str}" fill="none" stroke="{color}" stroke-width="2.5" stroke-linejoin="round"/>')

        # Data markers
        for r in rows:
            x = scale_x(r["iteration"])
            y = scale_y(r["throughput_ops_sec"])
            svg_lines.append(f'  <circle cx="{x:.1f}" cy="{y:.1f}" r="3.5" fill="{color}"/>')

    # Legend at top-right
    leg_x = width - margin["right"] - 220
    leg_y = margin["top"] + 15
    for idx, (label, color) in enumerate(legend_items):
        item_y = leg_y + idx * 22
        svg_lines.append(f'  <circle cx="{leg_x}" cy="{item_y}" r="5" fill="{color}"/>')
        svg_lines.append(f'  <text x="{leg_x + 12}" y="{item_y + 4}" class="legend-text">{label}</text>')

    svg_lines.append('</svg>')

    os.makedirs(os.path.dirname(output_svg_path), exist_ok=True)
    with open(output_svg_path, "w", encoding="utf-8") as f:
        f.write("\n".join(svg_lines))
    print(f"Generated vector SVG chart: {output_svg_path}")

def main():
    csv_path = Path("results/processed/warmup_curve.csv")
    svg_path = Path("docs/warmup_curve.svg")

    if len(sys.argv) > 1:
        csv_path = Path(sys.argv[1])
    if len(sys.argv) > 2:
        svg_path = Path(sys.argv[2])

    if not csv_path.exists():
        print(f"Error: CSV file not found: {csv_path}")
        sys.exit(1)

    data_by_config = {}
    with open(csv_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            cfg = row["config"]
            data_by_config.setdefault(cfg, []).append({
                "iteration": int(row["iteration"]),
                "elapsed_ms": float(row["elapsed_ms"]),
                "ops": int(row["ops"]),
                "throughput_ops_sec": float(row["throughput_ops_sec"])
            })

    generate_svg_chart(data_by_config, str(svg_path))

    # Optional matplotlib PNG export if installed
    try:
        import matplotlib.pyplot as plt
        png_path = svg_path.with_suffix(".png")
        plt.figure(figsize=(9, 5), dpi=150)
        for cfg, color in [("Tiered_Default", "#2563eb"), ("C1_Only", "#16a34a"), ("Interpreter", "#dc2626")]:
            rows = data_by_config.get(cfg, [])
            if rows:
                x = [r["iteration"] for r in rows]
                y = [r["throughput_ops_sec"] for r in rows]
                plt.plot(x, y, marker='o', label=cfg, color=color, linewidth=2)
        plt.title("HotSpot JIT Warmup Curve: Compilation Tiers")
        plt.xlabel("Measurement Iteration (200ms Windows)")
        plt.ylabel("Throughput (ops/sec)")
        plt.grid(True, linestyle='--', alpha=0.5)
        plt.legend()
        plt.tight_layout()
        plt.savefig(str(png_path))
        print(f"Generated matplotlib PNG chart: {png_path}")
    except ImportError:
        pass

if __name__ == "__main__":
    main()
