# Building Facet

Run all commands from the repository root. Use the included Gradle wrapper;
a separate Gradle installation is not required.

## Requirements

- A Java 25 toolchain available to Gradle
- Internet access for the first build so Gradle can resolve Minecraft, Fabric,
  NeoForge, and plugin dependencies
- On macOS, Linux, and other Unix-like systems, use `./gradlew`
- On Windows, use `gradlew.bat` in place of `./gradlew`

The project uses Gradle 9.5.0 and compiles all modules for Java 25. Verify the
Gradle runtime and detected Java installation with:

```sh
./gradlew --version
```

## Supported Targets

Modules follow the `versions/<loader>-<minecraft-version>` naming convention.

| Module | Loader | Minecraft | Main artifact |
| --- | --- | --- | --- |
| `versions/fabric-26.1` | Fabric | 26.1 | `Facet-Fabric-<mod-version>-26.1.jar` |
| `versions/fabric-26.2` | Fabric | 26.2 | `Facet-Fabric-<mod-version>-26.2.jar` |
| `versions/fabric-26.3` | Fabric | 26.3 | `Facet-Fabric-<mod-version>-26.3.jar` |
| `versions/fabric-26.4-snapshot-3` | Fabric | 26.4 Snapshot 3 | `Facet-Fabric-<mod-version>-26.4-snapshot-3.jar` |
| `versions/neoforge-26.1` | NeoForge | 26.1 | `Facet-NeoForge-<mod-version>-26.1.jar` |
| `versions/neoforge-26.1.2` | NeoForge | 26.1.2 | `Facet-NeoForge-<mod-version>-26.1.2.jar` |
| `versions/neoforge-26.2` | NeoForge | 26.2 | `Facet-NeoForge-<mod-version>-26.2.jar` |
| `versions/neoforge-26.3` | NeoForge | 26.3 | `Facet-NeoForge-<mod-version>-26.3.jar` |

`<mod-version>` comes from `mod_version` in `gradle.properties`.

## Build Commands

### All supported targets

The unqualified `build` task builds and tests all eight Fabric and NeoForge
modules:

```sh
./gradlew build
```

Use one clean build to create candidate artifacts for a release or validate a
cross-version change:

```sh
./gradlew clean build
```

If the accepted candidate tree is unchanged, do not repeat the local clean
build; the tag workflow rebuilds it for the official release.

### Fabric only

```sh
./gradlew :versions:fabric-26.1:build :versions:fabric-26.2:build :versions:fabric-26.3:build :versions:fabric-26.4-snapshot-3:build
```

### NeoForge only

```sh
./gradlew :versions:neoforge-26.1:build :versions:neoforge-26.1.2:build :versions:neoforge-26.2:build :versions:neoforge-26.3:build
```

### One target

Use the module path followed by `:build`, for example:

```sh
./gradlew :versions:fabric-26.2:build
```

## Build Outputs

Each module writes its artifacts to `versions/<module>/build/libs/` and
produces both a main mod JAR and a `-sources.jar`. For example:

```text
versions/fabric-26.2/build/libs/Facet-Fabric-<mod-version>-26.2.jar
versions/fabric-26.2/build/libs/Facet-Fabric-<mod-version>-26.2-sources.jar
```

Only the main JAR is installed in Minecraft or attached as a release asset.

## Development Client

Launch a client for one exact target with that module's `runClient` task:

```sh
./gradlew :versions:fabric-26.2:runClient
./gradlew :versions:neoforge-26.2:runClient
```

A successful build or main-menu startup does not replace in-world validation
for rendering, input, placement, or Loader-specific behavior.

## Fabric 26.4 Snapshot 3 Preview

Use Java 25 to run Gradle. If the default Java installation is newer, set
`JAVA_HOME` explicitly, for example on macOS:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :versions:fabric-26.4-snapshot-3:build
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :versions:fabric-26.4-snapshot-3:runClient
```

This target uses Fabric API `0.162.2+26.4`. It reuses the 26.3 rendering bridge
and the shared baked-outline implementation. Its tests include the shared
outline tests and the Fabric 26.2 behavior tests, run against Snapshot 3.
Mod Menu is compile-only; it is not installed by `runClient`.

The snapshot target is not part of the seven-target official release workflow.
Snapshot previews use `.github/workflows/publish-preview-26.4-snapshot-3.yml`,
triggered by annotated tags named `v<mod-version>-mc26.4-snapshot-3-preview.<number>`.
The workflow publishes one main JAR and its SHA-256 manifest to a GitHub
prerelease, then publishes the same JAR to Modrinth as Alpha. Manual recovery
requires an existing preview tag and explicit destination selection.
Do not use `scripts/release.sh` or a plain `v<mod-version>` tag for previews.

In-world acceptance must cover baked outlines, special block markers, water
and lava direction arrows, lava interior fog, holographic night vision, and
placement preview with improved transparency both enabled and disabled.

## Release Publishing

Official releases use `.github/workflows/publish.yml`. From a version tag, it
validates `mod_version`, builds every target, stages only main JARs, creates
`SHA256SUMS.txt`, and publishes the configured destinations.

The formal release set covers Fabric 26.1, 26.2, and 26.3, plus NeoForge
26.1, 26.1.2, 26.2, and 26.3. Minecraft 26.3 snapshots, prereleases, and
release candidates shared one rolling target during development; the 26.3
release replaced that target, and the pre-release targets have been removed.

Every version tag produces one complete release covering all targets. The
Minecraft 26.3 builds are published to both Modrinth and CurseForge.

`scripts/release.sh` prepares and verifies a release locally: it guards the
tree, reads `mod_version`, runs one clean build, mechanically enumerates the
release JAR set, verifies embedded versions, and stages a diagnostic
`release/SHA256SUMS.txt`. Run it without arguments for the candidate step,
then `scripts/release.sh --publish` after in-world acceptance to create the
annotated tag and push it. The CI build from the tag is the authoritative
final build; candidate hashes are diagnostic.

Use the manual inputs only to recover a failed destination on an existing tag;
leave destinations that already succeeded disabled.

The Gradle `publishModrinth` task is a legacy Fabric-only local path. It does
not publish NeoForge artifacts or create the GitHub checksum manifest, so it is
not the official release process.
