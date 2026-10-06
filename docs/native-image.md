# Standalone native packages

GraalVM Native Image compiles this Kotlin/JVM client into a native executable that requires no
installed JRE or JDK. The Telegram library for the build's OS and architecture is embedded and
extracted into the user's temporary directory when needed. Keep the generated AWT support libraries
beside the executable: these small native DLLs/shared libraries are part of the portable package,
not an installed Java runtime. A writable temporary directory is required.

## Targets and CI

CI uses **Liberica Native Image Kit 25** (a GraalVM-based distribution) consistently for five targets:

| Package platform | GitHub runner | Archive |
| --- | --- | --- |
| `linux-x86_64` | `ubuntu-22.04` | `.tar.gz` |
| `linux-aarch64` | `ubuntu-22.04-arm` | `.tar.gz` |
| `macos-x86_64` | `macos-15-intel` | `.tar.gz` |
| `macos-aarch64` | `macos-15` | `.tar.gz` |
| `windows-x86_64` | `windows-2025` | `.zip` |

The reusable `.github/workflows/native.yml` runs the offline JVM suite, builds the native client,
checks it with Java removed from its environment, and uploads the package on each runner. The CI
workflow calls it on pushes and pull requests. The release workflow calls it with the release
version, waits for all native builds, checks that all five archives are present, then attaches them
alongside the existing fat jar and JVM distribution. A native failure prevents a partial release.

The facade also supports **Windows ARM64**, but GraalVM and Liberica NIK currently have no Windows
ARM64 Native Image toolchain. CI verifies that platform using an ARM64 Liberica JDK and the real
bundled facade DLL. The JVM release remains available for it; there is no archive mislabelled as a
Windows ARM64 native build. NIK retains Intel macOS support after Oracle/GraalVM CE stopped shipping
it in newer JDK 25 releases.

Linux packages target the Ubuntu 22.04 runner's glibc environment and require compatible system
libraries; these are not musl/static Alpine builds. macOS packages are built on macOS 15. Windows
requires the Microsoft Visual C++ runtime used by the facade DLL (`VCRUNTIME140.dll`); the local
machine and hosted runner already supply it. None of these requirements involves installing Java.

## Local build

Use PowerShell 7, a GraalVM/NIK JDK 25 with Native Image, and the native compiler prerequisites:
MSVC C++ tools and Windows SDK on Windows; GCC and zlib development files on Linux; Xcode command
line tools on macOS. The regular JVM build remains available independently of Native Image.

For the supplied Windows installation:

```powershell
.\scripts\build-native-windows.ps1
# A different Oracle GraalVM or Liberica NIK installation:
.\scripts\build-native-windows.ps1 -GraalVmHome 'C:\Tools\another-native-image-jdk-25'
```

The script defaults to `C:\Tools\graalvm-jdk-25.0.4+7.1`, runs `clean test nativeDist`, and restores
the caller's Java environment. On any supported native build host, set `JAVA_HOME` and `GRAALVM_HOME`
to the native-image JDK, then run the wrapper:

```bash
./gradlew --no-daemon clean test nativeDist
```

On Windows use `gradlew.bat`. Outputs are:

- `build/native/nativeCompile/kotlogramme` (`kotlogramme.exe` on Windows), plus native support libraries.
- `build/distributions/kotlogramme-<version>-<platform>.zip` on Windows or `.tar.gz` on Linux/macOS.

Archives contain the executable, generated support libraries, project license and third-party
notices. Unpack the entire archive and run the executable there. Unix archives preserve executable
permissions. `nativeCompile` compiles; `nativeSmokeTest` also checks the native files; `nativeDist`
chooses the host's archive format. `nativeDistZip` and `nativeDistTar` are available explicitly.
Native compilation is opt-in: ordinary `test` requires no GraalVM or C++ compiler.

## Verification

`scripts/test-native.ps1` copies the executable and support libraries into a fresh temporary
directory, removes Java environment variables, and removes Java from PATH. It checks help, the
embedded version, configuration persistence in a Unicode directory, `doctor`, and a scripted shell.
Credentials are dummy values. `doctor` creates and closes the client without making a network query;
the test never signs in or sends/receives messages. Temporary config and session data are removed.

Locally verified on Windows x86_64 with Oracle GraalVM 25.0.4+7.1 and Liberica NIK 25.0.4.1-1:
`clean test nativeDist` succeeds,
all 714 JVM tests pass, and all six native smoke checks pass without Java on PATH. Actionlint validates
the CI, reusable native workflow and release workflow. Linux, macOS, ARM64 and hosted builds
still require execution on their CI runners; local Windows checks do not establish their results.
Authenticated Telegram operations and an interactive console are not covered by the native smoke
checks. No live Telegram tests belong in the default build loop.

## Compatibility configuration

The build derives reflection registrations for the facade's protocol/raw classes from the resolved
jar, because generic response decoding resolves Kotlin serializers reflectively. This avoids a
hand-maintained class list or tracing against a real Telegram account and tracks facade overrides.
The registrations cover that bounded model surface; future facade changes still need native checks.

The generated metadata embeds only the build host's Telegram library. Static app metadata includes
the version resource, raw schema and Windows console FFM downcalls; JLine and Mordant ship their own
metadata. Native-library loaders initialize at runtime. `-march=compatibility` targets broadly
compatible CPUs, and `--no-fallback` prevents silently producing a launcher that needs Java.

References: [NIK downloads and supported platforms](https://bell-sw.com/pages/downloads/native-image-kit/),
[Native Build Tools](https://graalvm.github.io/native-build-tools/latest/gradle-plugin),
[GraalVM metadata](https://www.graalvm.org/jdk25/reference-manual/native-image/metadata/),
[GitHub runner platforms](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).