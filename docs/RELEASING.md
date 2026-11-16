# Building and releasing Koiros

The public repository is [lostadi/koiros](https://github.com/lostadi/koiros). Lee Ostadi publishes the official signed APKs. The source and repeatability checks can be run without access to his signing key.

## Reference toolchain

Use Linux with Python 3.12.8, Eclipse Temurin JDK 17.0.13+11, Android SDK Platform 35, and Android SDK Build Tools 35.0.0. Set `ANDROID_HOME` to your SDK directory and put the JDK's `java`, `javac`, and `keytool` on `PATH`.

Install the Android SDK packages with the official SDK manager:

```sh
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
```

The CI versions are recorded in [build.yml](../.github/workflows/build.yml). Build metadata records the tools and Android platform hashes actually used. SDK platform revisions can change within an API level: match the fingerprints in the published metadata when comparing an independently rebuilt APK.

The original Android/Termux build uses a different toolchain from the Linux reference workflow. Each toolchain is checked by building twice within that environment; an identical APK across those two environments is not promised. The release's metadata and reproducibility report identify which environment produced its files. See [Building](BUILDING.md) for the explicit alternative-toolchain options.

## Build and verify

From the repository root:

```sh
python3 tools/build.py --out dist
python3 tools/verify_reproducible.py --out dist/reproducibility
```

Every build runs all six model suites before packaging. To run only those suites, use `python3 tools/test.py`. The default build output is `Koiros-<version>-unsigned.apk` plus `build-metadata.json`. An unsigned APK is a verification artifact and must be signed before Android will install it.

The verifier makes two clean builds, compares their unsigned APK bytes, and writes `reproducibility.json`. Both outputs remain under `first/` and `second/` for inspection. The script fails if the APK hashes differ. CI performs these checks for pushes, pull requests, and manual runs, with read-only repository access and no signing credentials. It retains its unsigned artifacts for 30 days; permanent app downloads belong on the Releases page.

## Signing

Keep the official keystore and its password file outside the repository, source archives, and CI artifacts. Back them up privately: losing the signing key prevents compatible updates to installations signed with that key. The public signing certificate fingerprint is safe to publish.

To build with an existing key:

```sh
python3 tools/build.py --out dist \
  --keystore /private/path/koi-life.p12 \
  --password-file /private/path/keystore-password.txt \
  --key-alias koi-life

python3 tools/verify_reproducible.py --out dist/reproducibility \
  --keystore /private/path/koi-life.p12 \
  --password-file /private/path/keystore-password.txt \
  --key-alias koi-life
```

The signed result is `Koiros-<version>.apk`. With the same source, toolchain, signing key, and signing settings, the verifier checks that both signed APKs also match. Someone without the private release key can rebuild the unsigned app, but cannot reproduce Lee's signature. Publish the unsigned reference artifact and the signed installable APK with their checksums so these claims remain distinguishable.

If you maintain a separate fork, generate your own key once with `keytool` and keep it for subsequent releases. Do not distribute its private material. A differently signed build cannot update the official app in place; see [Compatibility](COMPATIBILITY.md).

## Publish with the GitHub CLI

Before tagging a release, increment `android:versionCode` in `app/AndroidManifest.xml` and set `android:versionName`. Preserve `org.ostadix.koilife` for compatibility with existing installations. Update the changelog, run the clean-build verifier, and confirm the signed app's installation and wallpaper setup on Android. Document the devices actually checked rather than treating model tests as device coverage.

Keep the source corresponding to each APK available under its release tag and include a source archive with the release. Preserve `LICENSE`, `LICENSES/`, `docs/CREDITS.md`, and the in-app notices in that distribution. Verify the archive contains no private keys, passwords, device settings, authentication files, or local diagnostic logs.

Use regular GitHub CLI commands from the repository checkout. Authenticate with `gh auth login` when needed, then publish the tag and files prepared for that version. For example:

```sh
gh auth status
git tag -a v1.8 -m 'Koiros 1.8'
git push origin main v1.8
gh run list --workflow build.yml --limit 5
gh release create v1.8 \
  dist/Koiros-1.8.apk \
  dist/Koiros-1.8-unsigned.apk \
  dist/Koiros-1.8-source.zip \
  dist/SHA256SUMS \
  dist/build-metadata.json \
  dist/reproducibility/reproducibility.json \
  --repo lostadi/koiros \
  --verify-tag \
  --title 'Koiros 1.8' \
  --notes-file docs/releases/v1.8.md
```

Review the finished release page and download its APK to check the published checksum. Do not replace a released APK with different bytes under the same version; issue a new version and retain the earlier source and verification records.

## Tool references

- [Android SDK manager](https://developer.android.com/tools/sdkmanager)
- [Android APK signing tool](https://developer.android.com/tools/apksigner)
- [GitHub CLI release creation](https://cli.github.com/manual/gh_release_create)
- [GitHub Actions checkout](https://github.com/actions/checkout), [Java setup](https://github.com/actions/setup-java), [Python setup](https://github.com/actions/setup-python), and [artifact uploads](https://github.com/actions/upload-artifact). The workflow pins their commits rather than moving version tags.
