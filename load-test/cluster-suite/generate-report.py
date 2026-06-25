#!/usr/bin/env python3
"""Aggregate cluster-suite result.json files into one markdown report."""
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


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("load-test/reports")
    run_id = sys.argv[2] if len(sys.argv) > 2 else datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    profile = sys.argv[3] if len(sys.argv) > 3 else "quick"
    out_dir = root / f"cluster-run-{run_id}"
    out_dir.mkdir(parents=True, exist_ok=True)

    dirs = sorted(
        [p for p in root.glob("cluster-suite-*") if p.is_dir()],
        key=lambda p: p.stat().st_mtime,
    )

    rows = []
    summary = {"run_id": run_id, "profile": profile, "generated_at": datetime.now(timezone.utc).isoformat(), "tests": []}

    for d in dirs:
        name = d.name.replace("cluster-suite-", "", 1)
        result = load_json(d / "result.json") or {}
        status = result.get("status", "UNKNOWN")
        message = result.get("message", "")
        rows.append((name, status, message))
        summary["tests"].append({"id": name, "status": status, "message": message, "dir": str(d)})

    lines = [
        f"# Cluster Suite Report",
        "",
        f"- **Run ID:** `{run_id}`",
        f"- **Profile:** `{profile}`",
        f"- **Generated:** {summary['generated_at']}",
        "",
        "| Test | Status | Message |",
        "|------|--------|---------|",
    ]
    for name, status, message in rows:
        msg = (message or "").replace("|", "\\|").replace("\n", " ")
        lines.append(f"| `{name}` | **{status}** | {msg} |")

    lines.extend(["", "## Per-test artifacts", ""])
    for d in dirs[-15:]:
        lines.append(f"- `{d}`")

    md = "\n".join(lines) + "\n"
    (out_dir / "CLUSTER-SUITE-REPORT.md").write_text(md)
    (out_dir / "summary.json").write_text(json.dumps(summary, indent=2))
    print(f"Wrote {out_dir / 'CLUSTER-SUITE-REPORT.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
