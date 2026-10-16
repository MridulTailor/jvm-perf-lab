#!/usr/bin/env python3
"""
JIT Log Parser (scripts/parse_jit.py)
Parses HotSpot JIT diagnostic logs (-XX:+PrintCompilation and -XX:+PrintInlining)
to count compilations by tier, analyze inlining decisions, and compare
monomorphic vs megamorphic call sites.
Stdlib only.
"""

import sys
import os
import re
import json
import argparse

def parse_jit_log(log_path):
    if not os.path.exists(log_path):
        raise FileNotFoundError(f"JIT log file not found: {log_path}")

    tier_counts = {1: 0, 2: 0, 3: 0, 4: 0}
    osr_compilations = 0
    total_compilations = 0

    inlined_count = 0
    too_big_count = 0
    virtual_call_count = 0
    other_not_inlined_count = 0

    method_inlining = {}

    # PrintCompilation pattern
    # Example: "   130    5   %   4       lab.workload.InliningCase::computeSmallMethods @ 6 (32 bytes)"
    comp_re = re.compile(
        r"^\s*\d+\s+\d+\s+([%s!bn\s]*?)\s*([1-4])\s+(.+?)(?:\s+@\s+\d+)?\s+\((\d+)\s+bytes\)"
    )

    # PrintInlining pattern
    # Example: "     @ 18  lab.workload.InliningCase::computeLargePipeline (422 bytes)   hot method too big"
    inline_re = re.compile(
        r"@\s+\d+\s+([^\s(]+)\s+\((\d+)\s+bytes\)(?:\s+(.*))?"
    )

    with open(log_path, 'r', encoding='utf-8', errors='replace') as f:
        for line in f:
            # Check PrintCompilation
            m_comp = comp_re.match(line)
            if m_comp:
                attrs = m_comp.group(1)
                tier = int(m_comp.group(2))
                method = m_comp.group(3).strip()
                tier_counts[tier] = tier_counts.get(tier, 0) + 1
                total_compilations += 1
                if '%' in attrs:
                    osr_compilations += 1
                continue

            # Check PrintInlining
            m_inline = inline_re.search(line)
            if m_inline:
                target_method = m_inline.group(1).strip()
                byte_size = int(m_inline.group(2))
                reason = (m_inline.group(3) or "").strip()

                decision = "not_inlined"
                if "inline" in reason:
                    decision = "inlined"
                    inlined_count += 1
                elif "too big" in reason or "callee is too large" in reason:
                    decision = "too_big"
                    too_big_count += 1
                elif "virtual call" in reason or "megamorphic" in reason or "receiver not inlineable" in reason:
                    decision = "virtual_call"
                    virtual_call_count += 1
                else:
                    decision = "other_not_inlined"
                    other_not_inlined_count += 1

                # Keep track of decision per distinct method
                method_inlining.setdefault(target_method, []).append({
                    "byte_size": byte_size,
                    "decision": decision,
                    "reason": reason
                })

    return {
        "log_file": os.path.basename(log_path),
        "compilations": {
            "total": total_compilations,
            "c1_tier1": tier_counts[1],
            "c1_tier2": tier_counts[2],
            "c1_tier3": tier_counts[3],
            "c2_tier4": tier_counts[4],
            "osr_count": osr_compilations
        },
        "inlining_summary": {
            "total_decisions": inlined_count + too_big_count + virtual_call_count + other_not_inlined_count,
            "inlined": inlined_count,
            "too_big": too_big_count,
            "virtual_or_megamorphic": virtual_call_count,
            "other_not_inlined": other_not_inlined_count
        },
        "key_methods": {
            m: decisions for m, decisions in method_inlining.items()
            if any(k in m for k in ["InliningCase", "Operation", "OpAdd", "stage", "Pipeline"])
        }
    }

def main():
    parser = argparse.ArgumentParser(description="Parse HotSpot JIT compilation and inlining diagnostic logs")
    parser.add_argument("--file", required=True, help="Path to JIT diagnostic log file")
    parser.add_argument("--output-json", help="Path to write JSON output")

    args = parser.parse_args()
    data = parse_jit_log(args.file)

    if args.output_json:
        os.makedirs(os.path.dirname(args.output_json), exist_ok=True)
        with open(args.output_json, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)

    print(json.dumps(data, indent=2))

if __name__ == "__main__":
    main()
