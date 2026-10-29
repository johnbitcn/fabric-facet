# Building Facet

Run all commands from the repository root. Use the included Gradle wrapper;
a separate Gradle installation is not required.

For development priorities, older-version support, and selective ports, see
[Version Maintenance](docs/version-maintenance.md).

## Requirements

- A Java 25 toolchain available to Gradle
- Python 3.9 or newer for release-target validation (standard library only)
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
| `versions/fabric-26.4-snapshot-6` | Fabric | 26.4 Snapshot 6 | `Facet-Fabric-<mod-version>-26.4-snapshot-6.jar` |
| `versions/neoforge-26.1` | NeoForge | 26.1 | `Facet-NeoForge-<mod-version>-26.1.jar` |
| `versions/neoforge-26.1.2` | NeoForge | 26.1.2 | `Facet-NeoForge-<mod-version>-26.1.2.jar` |
| `versions/neoforge-26.2` | NeoForge | 26.2 | `Facet-NeoForge-<mod-version>-26.2.jar` |
| `versions/neoforge-26.3` | NeoForge | 26.3 | `Facet-NeoForge-<mod-version>-26.3.jar` |

`<mod-version>` comes from `mod_version` in `gradle.properties`.

## Build Commands

### Stable targets

The unqualified `build` task builds and tests the seven stable Fabric and
NeoForge modules. Snapshot modules are excluded unless explicitly enabled:

```sh
./gradlew build
```

Use a full clean build only to validate a cross-version change. For release
candidates, `scripts/release.sh` builds only the selected release targets:

```sh
./gradlew clean build
```

If the accepted candidate tree is unchanged, do not repeat the local clean
build; the tag workflow rebuilds it for the official release.

### Fabric only

```sh
./gradlew :versions:fabric-26.1:build :versions:fabric-26.2:build :versions:fabric-26.3:build
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

### NeoForge 26.3 startup on macOS

With NeoForge `26.3.0.95` / FML `12.0.12`, the development client was observed
waiting in the upstream SDL early-loading window before Facet initialization.
Adding `-XstartOnFirstThread` did not resolve it. Temporarily setting
`earlyWindowControl = false` in `versions/neoforge-26.3/run/config/fml.toml`
allowed resource loading and Facet model baking to finish. Restore that local
setting after diagnosis. The project does not disable the loading window in
published JARs. Default startup did not complete on this macOS setup.

## Fabric 26.4 Snapshot 6 Preview

Use Java 25 to run Gradle. If the default Java installation is newer, set
`JAVA_HOME` explicitly, for example on macOS:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew -PincludeSnapshots=true :versions:fabric-26.4-snapshot-6:build
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew -PincludeSnapshots=true :versions:fabric-26.4-snapshot-6:runClient
```

This target has its own rendering bridge. Build dependencies and minimum
runtime requirements are pinned separately in `gradle.properties`; updating
one does not automatically change the other.
Snapshot-specific classes override shared classes only in this target's
`sourceSets`. Brittle-ice logic and textures stay in this module; stable
modules do not compile or package them. Shared code is reused only where the
API remains compatible. Tests reuse the shared outline and Fabric 26.2
behavior tests, with Snapshot-specific overrides. Mod Menu is compile-only;
it is not installed by `runClient`.

Snapshot 6 uses `GuiPipeline.TEXTURED` for GUI texture drawing. Local validation
with Java 25 covered a clean build, 76 Java tests, 20 Python script tests, and
development-client resource initialization, including the baked-outline atlas.
The operator subsequently reported that in-world validation passed. Individual
feature-toggle and manual resource-reload cases were not separately itemized.
The client still reports upstream MoltenVK primitive-restart warnings on macOS.

The snapshot target is not part of the stable release workflow.
Snapshot previews use `.github/workflows/publish-preview-26.4-snapshot-6.yml`,
triggered by annotated tags named `v<mod-version>-mc26.4-snapshot-6-preview.<number>`.
The workflow publishes one main JAR and its SHA-256 manifest to a GitHub
prerelease, then publishes the same JAR to Modrinth as Alpha. Manual recovery
requires an existing preview tag and explicit destination selection. Both
GitHub and Modrinth read the notes from `release-notes/<preview-tag>.md`.
Do not use `scripts/release.sh` or a plain `v<mod-version>` tag for previews.

In-world acceptance must cover baked outlines, special block markers, water
and lava direction arrows, lava interior fog, holographic night vision, and
placement preview with improved transparency both enabled and disabled.

## Release Publishing

Official releases use `.github/workflows/publish.yml`. From a version tag, it
validates `mod_version` and reads `release-notes/<tag>.targets`. It builds,
stages, and publishes only the targets in that file, and creates a SHA-256
manifest for those main JARs. Missing, empty, unknown, duplicate, or Snapshot
target selections fail before building or publishing; there is no all-target
fallback.

The formal release set covers Fabric 26.1, 26.2, and 26.3, plus NeoForge
26.1, 26.1.2, 26.2, and 26.3. Minecraft 26.3 snapshots, prereleases, and
release candidates shared one rolling target during development; the 26.3
release replaced that target, and the pre-release targets have been removed.

Before creating a tag, commit `release-notes/<tag>.targets` with one affected
module name per line, without the `versions/` prefix. For example, if only
Fabric 26.2 changed, `release-notes/v<mod-version>.targets` contains:

```text
fabric-26.2
```

Select targets based on the actual source, resource, dependency, and build
changes since their last release. A shared change can affect several targets;
an isolated target change does not justify republishing other versions. A
mod-wide version bump alone does not make every target affected. Comment lines
starting with `#` are allowed. Use the independent preview workflow for
Snapshots. Tags created before target manifests were introduced cannot be
recovered through this new workflow without a manifest; do not move those tags.

`scripts/release.sh` prepares and verifies a release locally: it guards the
tree, reads `mod_version` and the same target manifest, cleans and builds only
those modules, mechanically enumerates their release JAR set, verifies embedded
versions, and stages a diagnostic
`release/SHA256SUMS.txt`. Run it without arguments for the candidate step,
then `scripts/release.sh --publish` after in-world acceptance to create the
annotated tag and push it. The CI build from the tag is the authoritative
final build; candidate hashes are diagnostic.

Use the manual inputs only to recover a failed destination on an existing tag;
leave destinations that already succeeded disabled.

Both release workflows sync the checked-out tag's English `README.md` to the
Modrinth project body when Modrinth publishing is enabled. Only the exact
`Official project pages: ...` line is removed; the README file and all other
Markdown remain unchanged. The sync runs before any publishing step and reads
the body back to verify it. Failures stop publishing. `MODRINTH_TOKEN` must have
`PROJECT_WRITE` scope and project edit permission, in addition to its version
publishing permissions. Validation-only runs with all destinations disabled do
not update the project body. Recovery of older tags that lack the sync scripts
skips the new steps and retains the original file publishing behavior. Run
`python3 scripts/test-modrinth-description.py` to test this without making
network requests.

For a description-only update, manually run **Sync Modrinth description**
(`sync-modrinth-description.yml`) from the desired branch. It uses the same
README transformation, token and read-back check, but does not build or publish
JARs, change versions, create tags or touch other platforms. It only runs on
manual dispatch, not on branch or tag pushes.

The former aggregate `publishModrinth` task has been removed to prevent
unintentional all-version uploads. Use the explicit-target release workflow.
