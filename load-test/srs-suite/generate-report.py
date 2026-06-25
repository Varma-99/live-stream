#!/usr/bin/env python3
"""Aggregate SRS suite report dirs into one markdown + JSON summary."""
from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path


def load_json(path: Path):
    try:
        return json.loads(path.read_text())
    except Exception:
        return None


def k6_metrics(summary_path: Path) -> dict:
    data = load_json(summary_path)
    if not data:
        return {}
    m = data.get("metrics", {})
    out = {}
    for key in (
        "http_req_failed",
        "http_req_duration",
        "join_ok",
        "qos_stats_ok",
        "whep_handshake_ok",
        "whep_handshake_duration",
    ):
        if key in m:
            metric = m[key]
            out[key] = {
                "avg": metric.get("values", {}).get("avg"),
                "p95": metric.get("values", {}).get("p(95)"),
                "rate": metric.get("values", {}).get("rate"),
            }
    return out


def srs_stream_count(path: Path) -> int | None:
    data = load_json(path)
    if not data:
        return None
    return len(data.get("streams", []))


def ingest_kbps(qos_path: Path) -> int | None:
    data = load_json(qos_path)
    if not data:
        return None
    ing = data.get("ingest") or {}
    return ing.get("currentIngestKbps")


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("load-test/reports")
    run_id = sys.argv[2] if len(sys.argv) > 2 else datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    mode = sys.argv[3] if len(sys.argv) > 3 else "phase5"
    is_cluster = mode == "cluster"
    prefix = "srs-cluster-suite" if is_cluster else "srs-suite"
    out_name = f"srs-cluster-run-{run_id}" if is_cluster else f"srs-phase5-run-{run_id}"
    out_dir = root / out_name
    out_dir.mkdir(parents=True, exist_ok=True)

    dirs = sorted(
        [p for p in root.glob(f"{prefix}-*") if p.is_dir()],
        key=lambda p: p.stat().st_mtime,
    )

    sections = []
    summary = {
        "backend": "srs-cluster" if is_cluster else "srs",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "run_id": run_id,
        "tests": [],
    }

    for d in dirs:
        name = d.name.replace("srs-suite-", "", 1)
        entry = {"id": name, "dir": str(d)}

        meta = load_json(d / "suite-meta.json")
        if meta:
            entry["meta"] = meta

        for pattern, key in [
            ("k6-viewers-summary.json", "k6_viewers"),
            ("k6-viewers-600-summary.json", "k6_viewers_600"),
            ("k6-whep-summary.json", "k6_whep"),
            ("k6-whep-before-failover.json", "k6_whep_before_failover"),
            ("k6-whep-after-failover.json", "k6_whep_after_failover"),
            ("failover-summary.json", "failover_summary"),
            ("ttff-summary.json", "ttff_summary"),
            ("pull-efficiency-summary.json", "pull_efficiency_summary"),
        ]:
            p = d / pattern
            if p.exists():
                if key.endswith("_summary"):
                    data = load_json(p)
                    if data:
                        entry[key] = data
                else:
                    entry[key] = k6_metrics(p)

        for qos_file in sorted(d.glob("qos-*.json")):
            kbps = ingest_kbps(qos_file)
            if kbps is not None:
                entry.setdefault("ingest_kbps", {})[qos_file.stem] = kbps

        for srs_file in sorted(d.glob("srs-streams-*.json")):
            cnt = srs_stream_count(srs_file)
            if cnt is not None:
                entry.setdefault("srs_active_streams", {})[srs_file.stem] = cnt

        log_hints = []
        for log in d.glob("*.log"):
            if log.stat().st_size > 0:
                log_hints.append(log.name)
        if log_hints:
            entry["logs"] = log_hints

        summary["tests"].append(entry)
        sections.append(f"### {name}\n")
        if entry.get("k6_viewers"):
            kv = entry["k6_viewers"]
            sections.append(f"- k6 viewers: join_ok rate={kv.get('join_ok', {}).get('rate')}, p95={kv.get('http_req_duration', {}).get('p95')}ms\n")
        if entry.get("k6_whep"):
            kw = entry["k6_whep"]
            sections.append(f"- k6 WHEP: ok rate={kw.get('whep_handshake_ok', {}).get('rate')}, p95={kw.get('whep_handshake_duration', {}).get('p95')}ms\n")
        if entry.get("ingest_kbps"):
            sections.append(f"- ingest kbps samples: {entry['ingest_kbps']}\n")
        if entry.get("failover_summary"):
            fs = entry["failover_summary"]
            sections.append(f"- failover: overall_pass={fs.get('overall_pass')}, before={fs.get('whep_ok_rate_before')}, after={fs.get('whep_ok_rate_after')}\n")
        if entry.get("ttff_summary"):
            ts = entry["ttff_summary"]
            sections.append(f"- TTFF cold first WHEP: {ts.get('cold_first_whep_sec')}s, pass={ts.get('pass_cold_whep')}\n")
        if entry.get("pull_efficiency_summary"):
            ps = entry["pull_efficiency_summary"]
            sections.append(
                f"- pull efficiency: pass={ps.get('overall_pass')}, "
                f"abr pulls min/max={ps.get('abr_pull_clients_min')}/{ps.get('abr_pull_clients_max')}\n"
            )
        if entry.get("srs_active_streams"):
            sections.append(f"- SRS active streams: {entry['srs_active_streams']}\n")
        sections.append(f"- artifacts: `{d}`\n\n")

    json_name = "SRS-CLUSTER-SUMMARY.json" if is_cluster else "SRS-PHASE5-SUMMARY.json"
    md_name = "SRS-CLUSTER-REPORT.md" if is_cluster else "SRS-PHASE5-REPORT.md"
    json_path = out_dir / json_name
    md_path = out_dir / md_name

    json_path.write_text(json.dumps(summary, indent=2) + "\n")

    title = "SRS Edge Cluster — Test Report" if is_cluster else "SRS Phase 5 — Load Test Report"
    md = [
        f"# {title}",
        "",
        f"Generated: {summary['generated_at']}",
        f"Run id: `{run_id}`",
        "",
        "## Test runs included",
        "",
        f"Found **{len(dirs)}** report directories under `{root}`.",
        "",
        "| Test | Directory |",
        "|------|-----------|",
    ]
    for t in summary["tests"]:
        md.append(f"| {t['id']} | `{t['dir']}` |")

    md.extend(["", "## Details", ""])
    md.extend(sections)

    md.extend([
        "## How to re-run",
        "",
        "```bash",
    ])
    if is_cluster:
        md.extend([
            "colima start --port-forwarder=grpc",
            "SRS_CLUSTER_MODE=edge ./scripts/start-srs.sh",
            "./mvnw server config/config-dev-cluster.yml",
            "./load-test/srs-suite/run-all-cluster.sh",
            "```",
        ])
    else:
        md.extend([
            "./scripts/start-srs.sh",
            "./mvnw server config/config-dev.yml",
            "# Start ONE broadcast on broadcast.html",
            "./load-test/srs-suite/run-all.sh",
            "```",
            "",
            "## MediaMTX comparison",
            "",
            "Run the same suite later with `MEDIA_BACKEND=mediamtx` (when you are ready).",
        ])
    md.extend(["", ""])

    md_path.write_text("\n".join(md))
    print(f"Wrote {json_path}")
    print(f"Wrote {md_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
