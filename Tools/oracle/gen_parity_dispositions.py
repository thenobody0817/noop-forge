#!/usr/bin/env python3
"""Generate `platform_specific` parity dispositions for one-sided declarations.

WHY THIS EXISTS

The parity ledger is deliberately asymmetric: new one-sided debt BLOCKS, while a proven decrease is green.
That model assumes both platforms move together inside one change. The Android port of the StrandAnalytics
engine cluster does not: the Apple engines were written in a working fork with no Android tree at all, so
bringing them forward necessarily lands Swift-only first and twins them across follow-up changes.

`platform_specific` is the mechanism the ledger provides for exactly that, and the checked-in dispositions
already carry the precedent ("... (this PR is Swift-only)"). This tool emits one entry per current
one-sided finding, each naming the follow-up that will twin it, so the intent is reviewed rather than
silently baselined. When a twin lands, the disposition is deleted and the ledger reports the decrease.

WHAT IT DOES NOT DO

It does not touch the baseline. `parity_ledger.py --write-baseline` would accept the debt wholesale, which
is the opposite of reviewed: it would make 8,000 lines of pending work indistinguishable from debt nobody
intends to fix. Every entry here carries a rationale naming where it lands.

Usage:
    Tools/oracle/gen_parity_dispositions.py --rationale-template "..." [--dry-run]

Run it from the repository root. It rewrites Tools/parity_dispositions.json in place, preserving existing
entries and appending only identities that are not already present.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "Tools"))

import parity_ledger  # noqa: E402

DISPOSITIONS = ROOT / "Tools" / "parity_dispositions.json"


def current_one_sided() -> list[tuple[str, str]]:
    """Return (kind, platform-qualified identity) for every one-sided finding, de-duplicated.

    A finding's `identity` already carries its kind and platform as leading `|`-separated segments, so
    the authority identity a disposition must reference is the LAST TWO segments: `<platform>|<path>::<sig>`.
    """
    twin_map = json.loads((ROOT / "Tools" / "parity_twin_map.json").read_text())
    result = parity_ledger.scan(root=ROOT, twin_map=twin_map)
    out: dict[str, str] = {}
    for finding in result.findings:
        # Only the "new one-sided declaration" rules describe something a twin is meant to remove. A
        # test-only callsite or a stale-authority finding is a different problem and must not be papered
        # over here.
        if not finding.rule.startswith("add-unpaired-"):
            continue
        segments = finding.identity.split("|")
        authority = segments[-2] + "\0" + segments[-1]
        out[authority] = finding.rule
    return sorted((kind, identity) for identity, kind in out.items())


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--rationale-template",
        required=True,
        help="Rationale text; {file} is replaced with the declaring file's basename.",
    )
    parser.add_argument("--dry-run", action="store_true", help="Print what would be added; write nothing.")
    args = parser.parse_args()

    doc = json.loads(DISPOSITIONS.read_text())
    existing = {item["identity"] for item in doc["dispositions"]}

    added = 0
    for kind, identity in current_one_sided():
        if identity in existing:
            continue
        platform, _, rest = identity.partition("\0")
        path = rest.split("::", 1)[0]
        doc["dispositions"].append(
            {
                "type": "platform_specific",
                "kind": kind,
                "identity": identity,
                "identity_sha256": parity_ledger._canonical_sha256(identity),
                "platform": platform,
                "rationale": args.rationale_template.format(file=Path(path).name),
            }
        )
        added += 1

    if args.dry_run:
        print(f"would add {added} disposition(s) to {DISPOSITIONS.relative_to(ROOT)}")
        for item in doc["dispositions"][-added:] if added else []:
            print(f"  {item['kind']:<24} {item['identity']}")
        return 0

    DISPOSITIONS.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n")
    print(f"added {added} disposition(s) to {DISPOSITIONS.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())