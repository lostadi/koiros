# Build Koiros

Koiros is a native Java Android app with no downloaded application dependencies.
The build uses Python's standard library, a JDK, and the Android SDK command-line
tools. It does not need root, Ostadix, a connected phone, or the original APKs.

## Reference toolchain

The reference Linux CI recipe is recorded in `toolchain.lock.json`:

- Temurin JDK **17.0.13+11**; Java source/bytecode release **8**.
- Python **3.12.8**.
- Android SDK platform **android-35** and Build Tools **35.0.0**.
- Fixed `SOURCE_DATE_EPOCH=315532800` (1980-01-01 UTC).

Install the [Android SDK command-line tools](https://developer.android.com/tools),
set `JAVA_HOME` and `ANDROID_HOME`, and install the two SDK packages:

```sh
sdkmanager "platforms;android-35" "build-tools;35.0.0"
python3 tools/test.py
python3 tools/build.py --out dist
```

The SDK installer may ask you to accept Google's SDK licenses. Once the SDK and
JDK are installed, the build makes no network requests. Linux is the reference
build host; explicit alternate toolchains are supported and recorded separately.
The APK itself runs on Android 8.0 / API 26 and newer, independent of the host used
to build it. Its target SDK is declared in `app/AndroidManifest.xml`.

The default output is `dist/Koiros-<version>-unsigned.apk`, `SHA256SUMS`, and
`build-metadata.json`. An unsigned APK is useful for comparing builds; Android
requires an APK signature before installation.

## Sign an installable APK

For the official release, Lee retains the existing private signing key so Koiros
can update the earlier Koi / Life installation without changing its package ID or
saved settings. The key and its password are deliberately absent from this repo.

For your own independent build, create your own RSA key once and keep it in a
private location outside the repository. This command prompts for its password:

```sh
keytool -genkeypair -alias koi-life -keyalg RSA -keysize 3072 \
  -sigalg SHA256withRSA -validity 10000 -storetype PKCS12 \
  -keystore /private/location/koiros.p12
```

Store that password in a private file readable only by you. Do not pass the
password itself on a command line or commit it to Git. Then build and sign:

```sh
python3 tools/build.py --out dist \
  --keystore /private/location/koiros.p12 \
  --password-file /private/location/keystore-password.txt \
  --key-alias koi-life
```

The build emits both the unsigned APK and `Koiros-<version>.apk`, verifies the
signed APK with `apksigner`, checks resource alignment, and records the public
signing certificate's SHA-256 digest. It uses APK signature scheme v2, supported
by every Android version this app supports. Signing uses the supplied files in
place; neither the key nor its password is copied into the build or APK.

Android only accepts an update with a compatible signing identity. A build signed
with your own key cannot update the official APK in place. To switch signers,
first write down any settings you wish to retain, then uninstall the old app
and install your independent build. Uninstalling removes the app's saved settings.

## Check reproducibility

Run two complete builds with fresh temporary build directories:

```sh
python3 tools/verify_reproducible.py --out dist/reproducibility
```

To compare signed APKs as well, add the same `--keystore`, `--password-file`, and
`--key-alias` arguments used above. The verifier checks byte-for-byte equality of
the unsigned APKs and, when signing was requested, the signed APKs. It also checks
that source hashes and toolchain fingerprints match between runs. Both runs
execute the simulation tests.

Outputs are retained under `dist/reproducibility/first/` and `second/`; a successful
comparison writes `dist/reproducibility/reproducibility.json`. A failed comparison
returns a nonzero exit status.

This is a precisely scoped claim: **the same source, toolchain, timestamp setting,
and signing key produce the same bytes when the check passes**. A different JDK,
SDK revision, Android resource compiler, DEX compiler, or signing key can produce
different APK bytes. The manifest version and the source snapshot are inputs too.
The SDK package names pin the requested API and Build Tools version; the metadata
also records the actual `android.jar` hash and tool fingerprints so an SDK package
revision is visible. A signature cannot be recreated without its private key.
Anyone can rebuild and compare the unsigned payload without that key.

Every ZIP entry has a fixed timestamp and permissions, sorted ordering, no
compression, and four-byte alignment. Storing all entries removes host compression
library differences. No native libraries are bundled. Source inputs are staged
under a fresh directory and compiled using relative paths. No build-time clock,
absolute source path, signing password, or private key is embedded in the APK.

## Android / Termux build variant

An Android device with a native JDK, Python, `aapt2`, `d8`, `apksigner`, and an Android
platform JAR can build the same source. Its toolchain may differ from the reference
Linux recipe, so its checksums and reproducibility report are recorded separately.

```sh
python3 tools/verify_reproducible.py --out dist/reproducibility \
  --use-path-tools --android-jar /path/to/android.jar --compile-sdk 35
```

Use the actual API identifier for the supplied platform JAR. This option is
explicit: the build never silently substitutes random tools from `PATH` for the
reference SDK. You may also override individual tool executables with
`KOIROS_AAPT2`, `KOIROS_D8`, and `KOIROS_APKSIGNER`. `JAVA_HOME` selects Java tools;
otherwise they are resolved from `PATH`. `--sdk-root`, `--compile-sdk`, and
`--build-tools` override the corresponding defaults.

## What the checks cover

Six dependency-free Java suites exercise Life updates and spectra, all ten forms,
coupled chaos, frame budgets, collective dynamics and encounter colors, and bounded
touch ripples. The build also verifies the packaged DEX, resource storage and
alignment, and optional APK signature. Host tests do not establish frame rate or
correct wallpaper-picker behavior on every Android device; those require device
testing.

Tool references: [AAPT2](https://developer.android.com/tools/aapt2),
[D8](https://developer.android.com/tools/d8), and
[apksigner](https://developer.android.com/tools/apksigner).
