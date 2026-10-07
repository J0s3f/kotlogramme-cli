# Standalone single-file executables

GraalVM Native Image compiles this Kotlin/JVM client without requiring a JRE or JDK on the user's
machine. The image includes the host's Telegram library and generates native support libraries.
[Wrappe](https://github.com/Systemcluster/wrappe) 1.0.6 packs those files into one portable executable.
No installer is needed.

## Targets and release assets

CI uses **Liberica Native Image Kit 25** consistently for five targets:

| Platform | GitHub runner | Single-file release asset |
| --- | --- | --- |
| Linux x86_64 | `ubuntu-22.04` | `kotlogramme-<version>-linux-x86_64` |
| Linux ARM64 | `ubuntu-24.04-arm` | `kotlogramme-<version>-linux-aarch64` |
| Intel macOS | `macos-15-intel` | `kotlogramme-<version>-macos-x86_64` |
| Apple Silicon macOS | `macos-15` | `kotlogramme-<version>-macos-aarch64` |
| Windows x86_64 | `windows-2025` | `kotlogramme-<version>-windows-x86_64.exe` |

Releases contain exactly these five executables, `kotlogramme-all.jar`, and the Java distribution
zip with launch scripts. Native archives with separate libraries are not published. All five native
jobs must succeed and supply their single-file assets before the release is created.

The facade also supports Windows ARM64, but GraalVM/NIK has no native compiler for that platform.
The JVM distribution and an ARM64 Liberica JDK CI job cover it. NIK maintains Intel macOS support
where recent Oracle/GraalVM CE JDK 25 releases no longer supply an Intel macOS toolchain.

Linux x86_64 targets Ubuntu 22.04 (glibc 2.35); ARM64 targets Ubuntu 24.04 (glibc 2.39), required by
the facade's ARM64 library. The packer cannot lower that library's glibc requirement. macOS images
are built on macOS 15. Windows single-file payloads include the MSVC x64 redistributable runtime
from the C++ toolchain, alongside the generated support DLLs.

## Run

On Windows run the downloaded `.exe`. On Linux/macOS mark the downloaded file executable:

```bash
chmod +x kotlogramme-0.4.0-linux-x86_64
./kotlogramme-0.4.0-linux-x86_64 --help
```

Use the filename for your OS and architecture. The file can be renamed to `kotlogramme` (or
`kotlogramme.exe`) and put on PATH. Relative file arguments resolve against the caller's directory.

Wrappe extracts into a versioned directory under the system temporary directory and verifies cached
files before reuse. The CLI prints no packer banner. On Unix the runner replaces itself with the
native program, preserving terminal I/O and signals. Windows waits for the native process and
returns its exit code. Extracted files are cached rather than removed after every command, so
parallel CLI commands can reuse the native files. No permanent system installation is performed.

## Build

Use PowerShell 7, a GraalVM/NIK JDK 25 with Native Image, and native compiler prerequisites: MSVC C++
tools and Windows SDK on Windows, GCC/zlib development files on Linux, Xcode command line tools on
macOS. The normal JVM test loop remains independent of native compilation.

For the supplied Windows installation:

```powershell
.\scripts\build-native-windows.ps1
.\scripts\build-native-windows.ps1 -GraalVmHome 'C:\Tools\another-native-image-jdk-25'
```

The script defaults to `C:\Tools\graalvm-jdk-25.0.4+7.1`, runs `clean test nativeSingle`, and restores
the Java environment. On other native hosts set `JAVA_HOME` and `GRAALVM_HOME` to the Native Image JDK:

```bash
./gradlew --no-daemon -Pversion=0.4.0 clean test nativeSingle
```

On Windows use `gradlew.bat` and quote `'-Pversion=0.4.0'` in PowerShell. The single-file output lands
in `build/distributions/`. The original native executable and support libraries remain under
`build/native/nativeCompile/` for development. `nativeCompile` compiles, `nativeSmokeTest` checks
those files, and `nativeSingle` builds and checks the single-file executable. Native zip/tar tasks
remain local development options, but those archives are excluded from releases.

Windows, macOS and Linux x86_64 use Wrappe's pinned release binaries verified by SHA-256. Linux
ARM64 builds Wrappe 1.0.6 from source because its release does not bundle an ARM64 Linux runner:

```bash
cargo install wrappe --version 1.0.6 --locked --root /absolute/path/to/wrappe
export WRAPPE_BIN=/absolute/path/to/wrappe/bin/wrappe
./gradlew --no-daemon clean test nativeSingle
```

Rust is needed only to build the ARM64 packer; users do not need it installed. The project maintains
no custom extraction launcher. AppImage and MSI packaging are not part of this distribution.

## Verification and compatibility

The native smoke script copies only its input executable and any sidecars into a fresh temporary
directory, removes Java environment variables and Java from PATH, and checks help, a nonzero usage
error, version, Unicode config paths, offline `doctor`, and a scripted shell. The same checks run on
the raw native files and the packed executable. Dummy credentials are used; no Telegram query is
made. The packing tests ensure license text does not trigger wrapping and all support libraries,
including versioned shared libraries, are selected.

The base native matrix passed all nine jobs, including Windows ARM64 JVM coverage. Locally the
Windows single-file executable passes all seven smoke checks, and all 714 JVM tests pass. Hosted
single-file results are established by the packaging CI run on each platform. These checks do not
cover authenticated Telegram operations or a full interactive-console session.

The build derives serializer reflection registrations from the resolved facade jar, embeds only
its host Telegram library, preserves schema/version resources, and includes the Windows console
FFM metadata. Runtime loader initialization, `-march=compatibility`, and `--no-fallback` keep the
image portable without silently reintroducing Java.
