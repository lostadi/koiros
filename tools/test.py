#!/usr/bin/env python3
"""Compile and run the dependency-free Java simulation tests."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "org.ostadix.koilife"
MODELS = ("Companion", "CoupledChaos", "DotLattice", "FrameBudget", "KoiForms", "LifeField", "PageDrift",
          "PondDynamics", "TouchRipples", "WorldTint")


def java_tool(name: str) -> str:
    home = os.environ.get("JAVA_HOME")
    if home:
        candidate = Path(home) / "bin" / (name + (".exe" if os.name == "nt" else ""))
        if candidate.is_file():
            return str(candidate)
    found = shutil.which(name)
    if not found:
        raise RuntimeError(f"Missing {name}; install a JDK and set JAVA_HOME.")
    return found


def run_tests(root: Path, output: Path, javac: str | None = None,
              java: str | None = None) -> list[str]:
    javac, java = javac or java_tool("javac"), java or java_tool("java")
    output.mkdir(parents=True, exist_ok=True)
    package_path = PACKAGE.replace(".", "/")
    sources = [f"app/src/{package_path}/{name}.java" for name in MODELS]
    sources.extend(path.relative_to(root).as_posix()
                   for path in sorted((root / "tests").rglob("*Test.java")))
    tests = [f"{PACKAGE}.{path.stem}" for path in sorted((root / "tests").rglob("*Test.java"))]
    if not tests:
        raise RuntimeError("No simulation tests found.")
    subprocess.run([javac, "--release", "8", "-encoding", "UTF-8", "-d", str(output),
                    *sources], cwd=root, check=True, timeout=180)
    for name in tests:
        subprocess.run([java, "-cp", str(output), name], cwd=root, check=True, timeout=420)
    return tests


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=ROOT)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="koiros-tests-") as work:
        tests = run_tests(args.project.resolve(), Path(work) / "classes")
    print(f"Passed {len(tests)} simulation test suites.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"Tests failed: {error}", file=sys.stderr)
        raise SystemExit(1)
