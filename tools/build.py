#!/usr/bin/env python3
"""Build Koiros with a JDK and Android SDK. No Gradle or network access is needed."""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

from test import java_tool, run_tests

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def command(args: list[str], cwd: Path, *, capture: bool = False) -> str:
    process = subprocess.run(args, cwd=cwd, check=True, timeout=600, text=True,
                             stdout=subprocess.PIPE if capture else None,
                             stderr=subprocess.STDOUT if capture else None)
    return process.stdout.strip() if capture else ""


def parser() -> argparse.ArgumentParser:
    options = argparse.ArgumentParser(description=__doc__)
    options.add_argument("--project", type=Path, default=ROOT)
    options.add_argument("--out", type=Path, default=ROOT / "dist")
    options.add_argument("--sdk-root", type=Path,
                         default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    options.add_argument("--compile-sdk", help="Explicitly override toolchain.lock.json.")
    options.add_argument("--build-tools", help="Explicitly override toolchain.lock.json.")
    options.add_argument("--android-jar", type=Path, help="Explicit alternate platform android.jar.")
    options.add_argument("--use-path-tools", action="store_true",
                         help="Use aapt2, d8 and apksigner on PATH (for example Termux).")
    options.add_argument("--keystore", type=Path, help="Sign with this existing private keystore.")
    options.add_argument("--password-file", type=Path, help="Keystore password file; never copied into artifacts.")
    options.add_argument("--key-password-file", type=Path, help="Optional separate private-key password file.")
    options.add_argument("--key-alias", default="koi-life")
    return options


def resolve_tool(name: str, sdk: Path | None, revision: str, use_path: bool) -> str:
    override = os.environ.get("KOIROS_" + name.upper())
    if override:
        found = shutil.which(override)
        if not found:
            raise RuntimeError(f"KOIROS_{name.upper()} does not identify an executable.")
        return found
    if not use_path and sdk:
        folder = sdk / "build-tools" / revision
        suffixes = (".exe", ".bat", "") if os.name == "nt" else ("",)
        for suffix in suffixes:
            candidate = folder / (name + suffix)
            if candidate.is_file():
                return str(candidate)
    if use_path:
        found = shutil.which(name)
        if found:
            return found
    raise RuntimeError(f"Missing {name}. Install Android SDK build-tools;{revision}, "
                       "set ANDROID_HOME, or explicitly select --use-path-tools.")


def source_hashes(root: Path) -> dict[str, str]:
    result = {}
    for folder in ("app", "tests", "tools"):
        for source in sorted((root / folder).rglob("*")):
            if "__pycache__" in source.parts:
                continue
            if source.is_symlink():
                raise RuntimeError(f"Build inputs must not be symlinks: {source.relative_to(root)}")
            if source.is_file():
                result[source.relative_to(root).as_posix()] = sha256(source)
    result["toolchain.lock.json"] = sha256(root / "toolchain.lock.json")
    return result


def tool_details(name: str, executable: str, version_args: list[str], root: Path) -> dict:
    executable_path = Path(executable).resolve()
    details = {"version": command([executable, *version_args], root, capture=True),
               "launcher_sha256": sha256(executable_path)}
    # SDK d8/apksigner are launcher scripts: fingerprint their implementation jars too.
    jars = [executable_path.parent / "lib" / (name + ".jar"),
            executable_path.parent.parent / "share" / "java" / (name + ".jar"),
            executable_path.parent.parent / "share" / "java" / name / (name + ".jar")]
    details["implementation_jars"] = {candidate.name: sha256(candidate)
                                       for candidate in jars if candidate.is_file()}
    return details


def package_apk(stage: Path, output: Path, epoch: int) -> None:
    timestamp = dt.datetime.fromtimestamp(epoch, dt.timezone.utc)
    if not 1980 <= timestamp.year <= 2107:
        raise RuntimeError("SOURCE_DATE_EPOCH must be within the ZIP date range 1980–2107.")
    zip_time = (timestamp.year, timestamp.month, timestamp.day,
                timestamp.hour, timestamp.minute, timestamp.second // 2 * 2)
    entries = {}
    with zipfile.ZipFile(stage / "work/resources.apk") as resources:
        for item in resources.infolist():
            if not item.is_dir():
                entries[item.filename] = resources.read(item)
    for dex in sorted((stage / "work/dex").glob("*.dex")):
        entries[dex.name] = dex.read_bytes()
    for source in sorted((stage / "app/assets").rglob("*")):
        if source.is_file():
            entries["assets/" + source.relative_to(stage / "app/assets").as_posix()] = source.read_bytes()
    # Storing every entry removes host zlib versions from the reproducibility boundary.
    # There are no bundled native libraries. Four-byte alignment is sufficient here.
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_STORED) as archive:
        for name, content in sorted(entries.items()):
            item = zipfile.ZipInfo(name, zip_time)
            item.create_system = 3
            item.external_attr = 0o100644 << 16
            item.compress_type = zipfile.ZIP_STORED
            offset = archive.fp.tell() + 30 + len(name.encode("utf-8"))
            padding = (-offset) % 4
            if padding:
                item.extra = struct.pack("<HH", 0xD935, padding) + bytes(padding)
            archive.writestr(item, content)


def verify_alignment(apk: Path) -> None:
    with zipfile.ZipFile(apk) as archive, apk.open("rb") as stream:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise RuntimeError("APK has duplicate entries.")
        if "classes.dex" not in names or "AndroidManifest.xml" not in names:
            raise RuntimeError("APK is missing its manifest or DEX code.")
        if archive.getinfo("resources.arsc").compress_type != zipfile.ZIP_STORED:
            raise RuntimeError("resources.arsc must be stored uncompressed.")
        for item in archive.infolist():
            if item.compress_type == zipfile.ZIP_STORED:
                stream.seek(item.header_offset)
                header = stream.read(30)
                name_length, extra_length = struct.unpack_from("<HH", header, 26)
                if (item.header_offset + 30 + name_length + extra_length) % 4:
                    raise RuntimeError(f"Unaligned APK entry: {item.filename}")


def build(args: argparse.Namespace) -> dict:
    root = args.project.resolve()
    output = args.out.resolve()
    lock = json.loads((root / "toolchain.lock.json").read_text(encoding="utf-8"))
    compile_sdk = args.compile_sdk or lock["compile_sdk"]
    revision = args.build_tools or lock["build_tools"]
    sdk = args.sdk_root.resolve() if args.sdk_root else None
    android_jar = args.android_jar.resolve() if args.android_jar else (
        sdk / "platforms" / ("android-" + compile_sdk) / "android.jar" if sdk else None)
    if not android_jar or not android_jar.is_file():
        raise RuntimeError(f"Missing android.jar. Install platforms;android-{compile_sdk} "
                           "and set ANDROID_HOME, or pass --android-jar.")
    if bool(args.keystore) != bool(args.password_file):
        raise RuntimeError("Signing requires both --keystore and --password-file.")
    if args.key_password_file and not args.keystore:
        raise RuntimeError("--key-password-file requires --keystore.")
    signing_paths = [p.resolve() for p in (args.keystore, args.password_file, args.key_password_file) if p]
    for path in signing_paths:
        if not path.is_file():
            raise RuntimeError("A required signing file does not exist.")
        # Signing secrets are never included as build inputs or staged with the app.
        for folder in (root / "app", root / "tests", root / "tools", output):
            if path == folder or folder in path.parents:
                raise RuntimeError("Keep private signing files outside app/tests/tools and the output folder.")
    executables = {name: resolve_tool(name, sdk, revision, args.use_path_tools)
                   for name in ("aapt2", "d8")}
    if args.keystore:
        executables["apksigner"] = resolve_tool("apksigner", sdk, revision, args.use_path_tools)
    javac, java = java_tool("javac"), java_tool("java")
    original_hashes = source_hashes(root)
    manifest = ET.parse(root / "app/AndroidManifest.xml").getroot()
    version = manifest.get(ANDROID + "versionName")
    if not version or not re.fullmatch(r"[A-Za-z0-9._+-]+", version):
        raise RuntimeError("Manifest versionName is not safe for an artifact filename.")
    uses_sdk = manifest.find("uses-sdk")
    if uses_sdk is None:
        raise RuntimeError("Manifest must declare uses-sdk.")
    min_sdk = int(uses_sdk.get(ANDROID + "minSdkVersion", "26"))
    target_sdk = int(uses_sdk.get(ANDROID + "targetSdkVersion", "34"))
    epoch = int(os.environ.get("SOURCE_DATE_EPOCH", lock["source_date_epoch"]))
    output.mkdir(parents=True, exist_ok=True)
    unsigned_name = f"Koiros-{version}-unsigned.apk"
    signed_name = f"Koiros-{version}.apk"
    unsigned_apk = output / unsigned_name
    signed_apk = output / signed_name
    toolchain = {"requested_compile_sdk": compile_sdk,
                 "requested_build_tools": revision,
                 "path_tools": args.use_path_tools,
                 "android_jar_sha256": sha256(android_jar),
                 "python_version": platform.python_version(),
                 "host_system": platform.system(), "host_machine": platform.machine(),
                 "java": tool_details("java", java, ["-version"], root),
                 "javac": tool_details("javac", javac, ["-version"], root)}
    for name, executable in executables.items():
        toolchain[name] = tool_details(name, executable,
                                      ["version"] if name in ("aapt2", "apksigner") else ["--version"], root)
    with tempfile.TemporaryDirectory(prefix="koiros-build-") as temporary:
        stage = Path(temporary)
        for folder in ("app", "tests"):
            shutil.copytree(root / folder, stage / folder)
        for folder in ("classes", "dex", "generated", "testclasses"):
            (stage / "work" / folder).mkdir(parents=True)
        print("Running simulation tests", flush=True)
        suites = run_tests(stage, stage / "work/testclasses", javac, java)
        print("Compiling resources", flush=True)
        command([executables["aapt2"], "compile", "--dir", "app/res", "-o", "work/compiled-res.zip"], stage)
        command([executables["aapt2"], "link", "-o", "work/resources.apk", "--manifest",
                 "app/AndroidManifest.xml", "-I", str(android_jar), "--java", "work/generated",
                 "--min-sdk-version", str(min_sdk), "--target-sdk-version", str(target_sdk),
                 "--auto-add-overlay", "-R", "work/compiled-res.zip"], stage)
        sources = sorted((stage / "app/src").rglob("*.java")) + sorted((stage / "work/generated").rglob("*.java"))
        # Direct argv avoids javac argument-file quoting problems in paths with spaces.
        command([javac, "--release", str(lock["java_release"]), "-encoding", "UTF-8", "-g:source,lines",
                 "-classpath", str(android_jar), "-d", "work/classes",
                 *(source.relative_to(stage).as_posix() for source in sources)], stage)
        classes = sorted((stage / "work/classes").rglob("*.class"))
        print("Compiling DEX", flush=True)
        command([executables["d8"], "--release", "--min-api", str(min_sdk), "--lib", str(android_jar),
                 "--output", "work/dex", *(source.relative_to(stage).as_posix() for source in classes)], stage)
        package_apk(stage, unsigned_apk, epoch)
    verify_alignment(unsigned_apk)
    artifacts = {unsigned_name: {"sha256": sha256(unsigned_apk), "bytes": unsigned_apk.stat().st_size}}
    signature = None
    if args.keystore:
        print("Signing with the supplied private key", flush=True)
        signing_command = [executables["apksigner"], "sign", "--ks", str(args.keystore.resolve()),
                           "--ks-key-alias", args.key_alias, "--ks-pass", "file:" + str(args.password_file.resolve()),
                           "--min-sdk-version", str(min_sdk), "--v1-signing-enabled", "false",
                           "--v2-signing-enabled", "true", "--v3-signing-enabled", "false",
                           "--v4-signing-enabled", "false", "--out", str(signed_apk)]
        if args.key_password_file:
            signing_command.extend(["--key-pass", "file:" + str(args.key_password_file.resolve())])
        signing_command.append(str(unsigned_apk))
        command(signing_command, root)
        report = command([executables["apksigner"], "verify", "--verbose", "--print-certs",
                          "--min-sdk-version", str(min_sdk), str(signed_apk)], root, capture=True)
        verify_alignment(signed_apk)
        match = re.search(r"certificate SHA-256 digest: ([0-9a-fA-F]+)", report)
        if not match:
            raise RuntimeError("apksigner verified the APK but did not provide a certificate digest.")
        signature = {"scheme": "v2", "certificate_sha256": match.group(1).lower(), "verified": True}
        artifacts[signed_name] = {"sha256": sha256(signed_apk), "bytes": signed_apk.stat().st_size}
    if source_hashes(root) != original_hashes:
        raise RuntimeError("Build inputs changed while the build was running; discard these outputs.")
    metadata = {"schema": "koiros.apk-build/v1", "package": manifest.get("package"),
                "version_name": version, "version_code": int(manifest.get(ANDROID + "versionCode")),
                "min_sdk": min_sdk, "target_sdk": target_sdk, "java_release": lock["java_release"],
                "source_date_epoch": epoch, "toolchain": toolchain,
                "simulation_tests": suites, "alignment_verified": True,
                "signature": signature, "artifacts": artifacts, "sources": original_hashes}
    (output / "build-metadata.json").write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (output / "SHA256SUMS").write_text("".join(f"{entry['sha256']}  {name}\n"
                                              for name, entry in sorted(artifacts.items())), encoding="utf-8")
    print(json.dumps({"output": str(output), "artifacts": artifacts}, indent=2), flush=True)
    return metadata


def main() -> int:
    build(parser().parse_args())
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError, ET.ParseError) as error:
        print(f"Build failed: {error}", file=sys.stderr)
        raise SystemExit(1)
