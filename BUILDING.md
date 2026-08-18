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
| `versions/fabric-26.3-snapshot-6` | Fabric | 26.3 Snapshot 6 | `Facet-Fabric-<mod-version>-26.3-snapshot-6.jar` |
| `versions/fabric-26.3-snapshot-7` | Fabric | 26.3 Snapshot 7 | `Facet-Fabric-<mod-version>-26.3-snapshot-7.jar` |
| `versions/fabric-26.3-snapshot-8` | Fabric | 26.3 Snapshot 8 | `Facet-Fabric-<mod-version>-26.3-snapshot-8.jar` |
| `versions/fabric-26.3-snapshot-9` | Fabric | 26.3 Snapshot 9 | `Facet-Fabric-<mod-version>-26.3-snapshot-9.jar` |
| `versions/neoforge-26.1` | NeoForge | 26.1 | `Facet-NeoForge-<mod-version>-26.1.jar` |
| `versions/neoforge-26.1.2` | NeoForge | 26.1.2 | `Facet-NeoForge-<mod-version>-26.1.2.jar` |
| `versions/neoforge-26.2` | NeoForge | 26.2 | `Facet-NeoForge-<mod-version>-26.2.jar` |

`<mod-version>` comes from `mod_version` in `gradle.properties`.

## Build Commands

### All supported targets

The unqualified `build` task builds and tests all nine Fabric and NeoForge
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
./gradlew :versions:fabric-26.1:build :versions:fabric-26.2:build :versions:fabric-26.3-snapshot-6:build
./gradlew :versions:fabric-26.3-snapshot-7:build
./gradlew :versions:fabric-26.3-snapshot-8:build
./gradlew :versions:fabric-26.3-snapshot-9:build
```

### NeoForge only

```sh
./gradlew :versions:neoforge-26.1:build :versions:neoforge-26.1.2:build :versions:neoforge-26.2:build
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

## Release Publishing

Official releases use `.github/workflows/publish.yml`. From a version tag, it
validates `mod_version`, builds every target, stages only main JARs, creates
`SHA256SUMS.txt`, and publishes the configured destinations.

The formal release set covers Fabric 26.1, 26.2, and 26.3 Snapshot 8, plus
NeoForge 26.1, 26.1.2, and 26.2. Minecraft 26.3 Snapshot 6 and Snapshot 7 stay
buildable but are no longer part of the formal release set; each remains
available through its preview workflow.

Minecraft 26.3 Snapshot 8 joined the formal release set in v1.3.8 and keeps
its isolated preview workflow.

Minecraft 26.3 Snapshot 9 is a buildable adaptation target and is not yet part
of the formal release set.

Minecraft compatibility previews use `.github/workflows/publish-preview.yml`
and a tag such as `v1.2.3-mc26.3-snapshot-6-preview.1`. The preview workflow
builds only the exact Fabric snapshot target, creates a GitHub Prerelease, and
publishes the same JAR to Modrinth as a beta. It does not publish to CurseForge
or replace assets in the corresponding formal release.

Snapshot 7 uses the isolated `.github/workflows/publish-preview-snapshot-7.yml`
workflow and the tag pattern `v<version>-mc26.3-snapshot-7-preview.<number>`.

Snapshot 8 uses the isolated `.github/workflows/publish-preview-snapshot-8.yml`
workflow and the tag pattern `v<version>-mc26.3-snapshot-8-preview.<number>`.

Snapshot 9 uses the isolated `.github/workflows/publish-preview-snapshot-9.yml`
workflow and the tag pattern `v<version>-mc26.3-snapshot-9-preview.<number>`.

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
