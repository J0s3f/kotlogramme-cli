# A GraalVM native build of the client

Decision-shaped assessment of whether `kotlogramme-cli` can be built with GraalVM
`native-image` into a **single, self-contained Windows `.exe`** that runs with no JVM installed,
and what stands in the way.

> **Status of this document.** GraalVM is **not installed on the machine where this was written**,
> so no image was built. Everything about `native-image` *runtime behaviour* below is reasoning,
> clearly marked. Everything about what is *in the jars, the bytecode and the dependency graph* was
> executed and is factual. Read "Verified" and "Reasoned" at the bottom before trusting a claim.

## Verdict

**Viable with caveats — but only marginally worth it.**

- A **single** `.exe` is reachable, not a two-file download: the facade already extracts its bundled
  DLL from the classpath to a temp file and `System.load`s it, so embedding the DLL as an image
  resource preserves single-file delivery. No structural blocker there.
- JLine 4.4.6, Clikt 5.1.0 and Mordant 3.0.2 are all either reflection-free or ship their own
  `native-image` metadata. The interactive shell and the table rendering are *not* the problem.
- The real work — and the real risk — is **kotlinx.serialization**. The facade's
  `Transport.request`/`decode` resolve serializers at runtime from a `KType`, i.e. through
  `java.lang.reflect`, for every `@Serializable` payload it decodes (~500 `protocol` classes plus
  `raw`). That has to be registered, and a missing class fails at **runtime**, not at build time.
- Size is a wash: the mandatory `windows-x86_64/kotlogramme.dll` is 12.9 MB, and the fat jar is
  39.4 MB, so the exe will land in the same ballpark. The genuine win is **"no JDK 25 required"**
  plus faster startup — not bytes.

If the no-JVM requirement is firm for Windows users, do it; the recipe is below. If it is not,
the fat jar remains the better distribution.

## Environment check (what is actually available here)

| Check | Result |
| --- | --- |
| `java -version` | `openjdk 25.0.4.1 2026-08-18 LTS`, **Temurin** (HotSpot), `25.0.4.1+1-LTS` |
| `$env:JAVA_HOME` | `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\` |
| `native-image --version` | not found (no such command) |
| `$env:JAVA_HOME\bin\native-image*` | absent |
| `gu --version` | `gu` resolves to PowerShell's `Get-Unique` alias, not the GraalVM Updater |
| GraalVM in `C:\Program Files`, `~\.jdks`, Gradle caches | none (JDKs present are Temurin/Azul/Liberica HotSpot only) |

So the toolchain is JDK 25 **HotSpot**, and `native-image` is simply not installed. There was no
`native-image` invocation to run and no build attempt to report as a result. The `shadowJar` build
*was* run offline and succeeds (see Size), which is what makes the bytecode inspections below
possible.

## 1. JNI native library loading

**What the facade does (verified, `kotlogramme` 0.5.0 bytecode).**
`org.kotlogramme.NativeLibraryLoader.ensureLoaded()` is, in order:

1. read the `kotlogramme.native.path` system property; if non-blank,
   `System.load(Path.of(prop).toAbsolutePath().toString())` and return;
2. otherwise pick a platform from `os.name`/`os.arch`, build the resource path
   `native/<platform>/<file>`, and call `NativeLibraryLoader::class.java.classLoader.getResourceAsStream(path)`;
3. if that stream is `null` → `System.loadLibrary("kotlogramme")`;
4. otherwise copy the stream to a fresh `Files.createTempDirectory("kotlogramme-native-")` file,
   `deleteOnExit()` it, and `System.load(absPath)`.

**Crucially, the loader itself uses no reflection** — only `getResourceAsStream`, `Files.copy` and
`System.load`/`loadLibrary`. That is all supported in a native image.

**Answer: embed the DLL as a resource; the existing logic keeps it single-file.**
Register `native/windows-x86_64/kotlogramme.dll` in the image's `resource-config.json`. At runtime
step 2 finds the stream, step 4 writes it to `%TEMP%` and loads it. There is **no need** for the
`kotlogramme.native.path` side file, and therefore **no two-file reality**. `kotlogramme.native.path`
stays useful as an override (e.g. pointing at a system DLL) but is not part of the minimal path.

**The JNI boundary is small (verified).** `org.kotlogramme.TelegramClient$Native` declares exactly
seven `native` methods:

```text
create(int, String, String) -> String
close(long) -> String
isAuthorized(long) -> String
signInBot(long, String, String) -> String
sendMessage(long, String, String) -> String
request(long, String, String) -> String
invokeRaw(long, byte[], int) -> byte[]
```

Only `long`, `int`, `String` and `byte[]` cross the boundary. `Transport$request$1` calls
`TelegramClient$Native.request(...)` and decodes the returned **JSON string**, so the native side is
a self-contained JSON/FFI endpoint. Nothing suggests Java objects or upcalls travel back into the
image, so no `jni-configuration` is needed for *our* library beyond `-H:+JNI` (on by default). This
is reasoning about the DLL's internals, not a measured result.

One runtime flag carries over: the build already asks for `--enable-native-access=ALL-UNNAMED`
(`applicationDefaultJvmArgs` in `build.gradle.kts`). A native executable does not read that list, so
the same option must be baked into the image or passed at launch.

**Doctor is unaffected functionally.** `FacadeNativeLibraryProbe.kt` prints
`TelegramClient::class.java.protectionDomain?.codeSource?.location`. In a native image there is no
code source, so that line will print `null`; `doctor` still reports the library as loaded.

## 2. Reflection and serialization

This is where the work is. There are three separate serialization paths; two are clean and one is
not.

**(a) The client's own config store — reflection-free (verified).** `JsonConfigStore` calls
`json.encodeToString(ConfigFile.from(config))` and `json.decodeFromString(text)`, but the compiled
bytecode in the built jar shows the Kotlin serialization plugin already resolved both reified calls
**statically** to `ConfigFile.Companion.serializer()`:

```text
getstatic   ConfigFile.Companion
invokevirtual ConfigFile$Companion.serializer()KSerializer
invokevirtual Json.encodeToString(SerializationStrategy, Object)
...
invokevirtual Json.decodeFromString(DeserializationStrategy, String)
```

No `SerializersKt.serializer(KType)` appears. So `ConfigFile`, `CredentialsFile` and the
`OutputFormat` enum need **no** reflect-config. `kotlin-reflect` is not on the classpath either
(only `kotlin-stdlib`) — `KClass` use is the stdlib stub.

**(b) The facade's raw schema — reflection-free (verified).** `org.kotlogramme.raw.RawTelegramApi`
loads `/raw/telegram-layer-229.json` with `Class.getResourceAsStream` and decodes it with the
**explicit** `RawSchema.Companion.serializer()`. It only needs `raw/telegram-layer-229.json` in the
image. Nothing reflective.

**(c) The facade's generic response decode — the blocker (verified in bytecode).**
`org.kotlogramme.Transport.request` and `Transport.decode` are `inline fun <reified …>` helpers.
The emitted `Transport$request$1` class contains the compiler's reified markers
(`Intrinsics.reifiedOperationMarker`, `MagicApiIntrinsics.voidMagicApiCall("kotlinx.serialization.serializer.withModule")`),
and the real, inlined call sites run:

```text
Json.getSerializersModule()
SerializersKt.serializer(SerializersModule, KType)   // typeOf<Result>()
Json.decodeFromString(DeserializationStrategy, String)
```

`kotlinx-serialization-core-jvm 1.11.0` then resolves the serializer **reflectively**:
`SerializersKt__SerializersJvmKt` walks `java.lang.reflect.ParameterizedType`/`Class`, and
`kotlinx.serialization.internal.PlatformKt.constructSerializerForGivenTypeArgs` uses
`java.lang.reflect` (`Class.getDeclared…`, annotation checks) to find the companion `serializer()`.
That library **ships no `META-INF/native-image` metadata at all** (verified: its jar has none).

Consequence: every `@Serializable` type that reaches `Transport.request` — the ~517
`org.kotlogramme.protocol.*` classes and the `org.kotlogramme.raw.*` types, plus their list/enum
wrappers — must be reachable for reflection. Miss one and the image **builds**, then dies at runtime
with `SerializationException` / "serializer not found" the first time that command decodes a
response. That is the single most important item in this report.

**(d) `OperationCatalog` — reflective but unused by the CLI (verified).** The only class in all 846
facade classes that references `java.lang.reflect` is `org.kotlogramme.OperationCatalog`. Its static
initializer takes `Class.getDeclaredMethods()` over 15 bridge types
(`TelegramClient`, `AccountBridge`, `AuthBridge`, `MessagesBridge`, `ChatsBridge`, `ContactsBridge`,
`DialogsBridge`, `UpdatesBridge`, `MediaBridge`, `FilesBridge`, `InlineBridge`, `ActionsBridge`,
`MarkupBridge`, `StickersBridge`, `RawBridge`) and collects `Method.getAnnotation(Operation::class.java).name`.
A binary scan shows the class references **only itself** — nothing in the facade or the CLI calls
`OperationCatalog.getNames()`. It can therefore be left out of the image entirely. It would need a
`reflect-config` entry (the 15 types, `allDeclaredMethods`, annotation queries) only if the client
ever starts using it.

**No `Class.forName`, no `ObjectInputStream`** anywhere in the facade (verified by scanning all
846 class files).

## 3. JLine and JNA

The dependency is `org.jline:jline:4.4.6`, a 1.7 MB **omnibus jar with no transitive dependencies**
(verified via the Gradle `runtimeClasspath` report). It bundles `jline-terminal`,
`jline-terminal-ffm`, `jline-terminal-jni`, `jline-native`, `jansi-core`, and — importantly —
**native-image metadata and the platform native libraries**:

```text
META-INF/native-image/org.jline/jline-terminal/{native-image.properties,reflection-config.json,resource-config.json}
META-INF/native-image/org.jline/jline-native/{native-image.properties,reflection-config.json,resource-config.json,jni-config.json}
META-INF/native-image/org.jline/jline-terminal-jni/{native-image.properties,reflection-config.json,resource-config.json}
META-INF/native-image/org.jline/jline-terminal-ffm/reachability-metadata.json       # includes FFM downcall descriptors
META-INF/jline/providers/{dumb,exec,ffm,jni}
org/jline/nativ/Windows/x86_64/jlinenative.dll                                      # 127 KB
```

JLine's own header says the `META-INF/services/org.jline.terminal.spi.TerminalProvider` file is for
`jlink` only; runtime selection uses `META-INF/jline/providers/<name>`. The registered providers are
**ffm, jni, exec and dumb** — JNA is *not* one of them in JLine 4. (This contradicts the comment in
`build.gradle.kts` and the README that "JLine reaches its native terminal support through JNA"; for
JLine 4.4.6 that comment is stale.) `jansi-core` classes are bundled but no `jansi` provider is
registered, and the app never touches `org.jline.jansi.AnsiConsole`, so JNA is not in the terminal
path.

So: the shell is expected to survive, most likely on the FFM provider (it ships native-image
reachability metadata with `foreign` downcall descriptors) or the JNI provider (it ships `jni-config`
for `org.jline.nativ.CLibrary` and includes `org/jline/nativ/.*` as resources), with `dumb` as the
floor. **A `--no-shell` build is not needed for JLine's sake.** Whether the selected provider really
works on a Windows console inside the image is unverified.

## 4. Clikt and Mordant

**Clikt is reflection-free (verified).** Neither `clikt-jvm-5.1.0.jar` nor `clikt-core-jvm-5.1.0.jar`
contains a single `java/lang/reflect` reference. No metadata is needed.

**Mordant 3.0.2 is native-image-aware (verified).** The `mordant-jvm` omnibus pulls **three**
terminal backends, each registered as a `TerminalInterfaceProvider` service and each shipping
`META-INF/native-image` metadata:

- `mordant-jvm-jna` → `TerminalInterfaceProviderJna`, brings `net.java.dev.jna:jna:5.14.0`;
- `mordant-jvm-ffm` → `TerminalInterfaceProviderFfm`;
- `mordant-jvm-graal-ffi` → `TerminalInterfaceProviderNativeImage` (+ Windows/macOS/Linux variants),
  the backend Mordant ships specifically for GraalVM.

The one thing to watch is JNA: `jna-5.14.0.jar` ships **no** native-image metadata and bundles its
own `jnidispatch.dll`. If the image ends up loading the JNA backend, it needs extra configuration.
The graal-ffi backend exists precisely to avoid that, so the recommendation is to **exclude
`mordant-jvm-jna`** (and optionally `mordant-jvm-ffm`) and keep `mordant-jvm-graal-ffi`. Which
backend the ServiceLoader picks in an image is unverified.

## 5. Size, startup and the distribution story

Measured here (the fat jar was built offline with `./gradlew --offline shadowJar`):

| Artifact | Size |
| --- | --- |
| `build/libs/kotlogramme-all.jar` | **39.4 MB** |
| `native/windows-x86_64/kotlogramme.dll` inside the `kotlogramme` jar | **12.9 MB** (13,228 KB) |
| `raw/telegram-layer-229.json` | 833 KB |
| `org/jline/nativ/Windows/x86_64/jlinenative.dll` | 127 KB |
| `kotlogramme` jar total (contains all six platform libraries) | 32.5 MB |

A Windows-only exe must embed the 12.9 MB DLL no matter what. native-image compresses embedded
resources and drops reachable-code it can prove unused, so the exe is plausibly in the same 30–50 MB
band as the jar — **no meaningful size win** (estimated, not measured). Startup should improve from
JVM+Kotlin class loading to native-image's tens of milliseconds; unverified. The app pays a one-time
cost extracting the DLL to `%TEMP%` on first use.

The distribution story today (`release.yml`, tag-driven) publishes `kotlogramme-all.jar` plus
`distZip`, and `ci.yml` verifies the jar carries all six `native/…` entries. The cost of the jar is
that users need **JDK 25** — an unusual requirement for a CLI. That is the genuine argument for a
native exe: *no JVM*, not fewer bytes. The counter-argument is that a native build is Windows-only
unless you build per-OS on three runners, so the release would carry the universal fat jar **and**
an optional Windows exe: two stories to maintain.

## Blockers, and where they live

| # | Blocker | Class / file that causes it | Severity |
| --- | --- | --- | --- |
| 1 | Runtime serializer lookup for every decoded payload | facade `org.kotlogramme.Transport.request`/`decode` + `kotlinx-serialization-core` `SerializersKt`/`PlatformKt` | **High** |
| 2 | DLL resource must be included | `org.kotlogramme.NativeLibraryLoader` (resource `native/windows-x86_64/kotlogramme.dll`) | Low (config) |
| 3 | Raw schema resource must be included | `org.kotlogramme.raw.RawTelegramApi` (`/raw/telegram-layer-229.json`) | Low (config) |
| 4 | FFM/JNI native access | JLine `jline-terminal-ffm`/`jline-native`; facade `TelegramClient$Native` | Low (metadata ships; runtime flag) |
| 5 | JNA backend has no metadata | `mordant-jvm-jna` → `net.java.dev.jna:jna:5.14.0` | Low–medium (use graal-ffi) |
| 6 | `OperationCatalog` reflection | `org.kotlogramme.OperationCatalog` | None (unused by the CLI) |
| 7 | `protectionDomain` in `doctor` | `adapter/telegram/NativeLibraryProbe.kt` | None (prints `null`) |

## A minimal `native-image` invocation

Not run here. It assumes a fat jar on the classpath and a `native-config/` directory produced by
the tracing agent:

```bat
native-image ^
  --no-fallback ^
  -H:+UnlockExperimentalVMOptions ^
  -H:ResourceConfigurationFiles=native-config/resource-config.json ^
  -H:ReflectionConfigurationFiles=native-config/reflect-config.json ^
  -H:JNIConfigurationFiles=native-config/jni-config.json ^
  --enable-native-access=ALL-UNNAMED ^
  -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 ^
  -cp build\libs\kotlogramme-all.jar ^
  -o kotlogramme.exe ^
  org.kotlogramme.cli.MainKt
```

- `resource-config.json` must include at least:
  `native/windows-x86_64/kotlogramme.dll`, `raw/telegram-layer-229.json`, `META-INF/services/.*`,
  `META-INF/jline/providers/.*` and `org/jline/nativ/Windows/x86_64/.*`.
  The older shortcut is
  `-H:IncludeResources="native/windows-x86_64/kotlogramme.dll|raw/telegram-layer-229.json"`.
- `reflect-config.json` is the real work. Generate it with the tracing agent over **representative
  live traffic**, not the fake-backed test suite:
  `java -agentlib:native-image-agent=config-output-dir=native-config -jar build\libs\kotlogramme-all.jar …`
  then merge in the 15 bridge types from blocker #6 if `OperationCatalog` is ever used. Because the
  agent only sees the types you exercise, an "all `protocol`/`raw` types" registration script is the
  safer alternative for blocker #1.
- The app already relies on `stdout.encoding`/`stderr.encoding=UTF-8` for Windows console output;
  the `-D` build-time properties above are an attempt to preserve that, but whether they become
  image defaults is unverified.
- A Gradle-native alternative exists (`org.graalvm.buildtools.native`), but wiring it in would
  restructure the build and is out of scope for this task.

## Honest costs

- **Build time / CI**: minutes per image, on a **Windows** runner with GraalVM for JDK 25.
  `native-image` does not cross-compile this for you.
- **The shell**: expected to work because JLine ships the metadata; the provider it actually selects
  on a Windows console is unverified.
- **Two-file reality**: **not** required — the DLL can be embedded and is extracted at runtime.
- **Size**: no win (12.9 MB DLL + code ≈ the 39.4 MB jar).
- **Runtime risk**: blocker #1 fails at run time, per command. The offline tests use fakes
  (`CliFixture`), so the tracing agent would not see the real payload decoders; covering them needs
  a live account, which is the hardest surface to exercise and the reason "it built" is not "it
  works".

## Verified vs reasoned

**Verified by execution here** (bytecode `javap`, `jar tf`, binary scans, Gradle dependency report,
offline build):

- No GraalVM / `native-image` / `gu` on this machine; `JAVA_HOME` is Temurin 25 HotSpot.
- `NativeLibraryLoader`'s algorithm and its reflection-free use of `getResourceAsStream` + `System.load`.
- `TelegramClient$Native`'s seven native methods and their `long/int/String/byte[]` signatures.
- `RawTelegramApi` loads `/raw/telegram-layer-229.json` and decodes with the explicit
  `RawSchema.Companion.serializer()`.
- `Transport.request`/`decode` use the reified `SerializersKt.serializer(SerializersModule, KType)`
  path; kotlinx-serialization-core 1.11.0 uses `java.lang.reflect` and ships no metadata.
- `JsonConfigStore`'s calls compile to static `ConfigFile.Companion.serializer()`.
- `OperationCatalog` is the only reflective facade class, and only it references itself.
- JLine 4.4.6 omnibus contents, native-image metadata, provider list (ffm/jni/exec/dumb) and native DLLs.
- Clikt has zero reflection references; Mordant ships jna/ffm/graal-ffi backends with metadata;
  JNA 5.14.0 ships none.
- `shadowJar` builds offline: 39.4 MB; DLL 12.9 MB; raw schema 833 KB.

**Reasoned, not verified** (no GraalVM available):

- That `resource-config` + `getResourceAsStream` + `System.load` works end to end in an image.
- That tracing-agent / registered reflect-config makes the facade's serializer lookup succeed, and
  that the registered set is complete.
- Which JLine terminal provider is selected on Windows in an image, and that the shell behaves.
- Which Mordant `TerminalInterfaceProvider` is selected, and whether the JNA backend must be excluded.
- The actual exe size and startup time, and whether the `-D*.encoding` defaults carry over.
