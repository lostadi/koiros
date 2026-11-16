#!/usr/bin/env python3
"""Build twice in clean temporary stages and compare every APK byte."""
from __future__ import annotations

import copy
import json
from pathlib import Path
import subprocess
import sys

import build as builder


def main() -> int:
    options = builder.parser()
    options.description = __doc__
    options.set_defaults(out=builder.ROOT / "dist/reproducibility")
    args = options.parse_args()
    output = args.out.resolve()
    (output / "reproducibility.json").unlink(missing_ok=True)
    first_args, second_args = copy.copy(args), copy.copy(args)
    first_args.out, second_args.out = output / "first", output / "second"
    first = builder.build(first_args)
    second = builder.build(second_args)
    if first["sources"] != second["sources"] or first["toolchain"] != second["toolchain"]:
        raise RuntimeError("The source or toolchain changed between the two clean builds.")
    if first["artifacts"] != second["artifacts"]:
        raise RuntimeError("APK checksums differ between clean builds; reproducibility failed.")
    for name in first["artifacts"]:
        if (first_args.out / name).read_bytes() != (second_args.out / name).read_bytes():
            raise RuntimeError(f"APK bytes differ between clean builds: {name}")
    report = {"schema": "koiros.reproducibility/v1", "byte_identical": True,
              "comparison": "Two clean temporary build stages, same inputs and toolchain",
              "artifacts": first["artifacts"], "toolchain": first["toolchain"],
              "sources": first["sources"], "source_date_epoch": first["source_date_epoch"],
              "signature": first["signature"], "simulation_tests_passed_in_both_builds": True}
    (output / "reproducibility.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("PASS: two clean builds produced byte-identical APKs.")
    print(f"Report: {output / 'reproducibility.json'}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"Reproducibility check failed: {error}", file=sys.stderr)
        raise SystemExit(1)
