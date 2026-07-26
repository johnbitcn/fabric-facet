# Building Facet

Facet modules are named as `versions/<loader>-<minecraft-version>` and output
artifacts as `Facet-<Loader>-<mod-version>-<minecraft-version>.jar`.

| Module | Loader | Minecraft | Artifact prefix |
| --- | --- | --- | --- |
| `versions/fabric-26.1` | Fabric | 26.1 | `Facet-Fabric` |
| `versions/fabric-26.2` | Fabric | 26.2 | `Facet-Fabric` |
| `versions/fabric-26.3-snapshot-5` | Fabric | 26.3-snapshot-5 | `Facet-Fabric` |
| `versions/neoforge-26.1` | NeoForge | 26.1 | `Facet-NeoForge` |
| `versions/neoforge-26.2` | NeoForge | 26.2 | `Facet-NeoForge` |

Build all Fabric targets:

```sh
./gradlew build
```

Build the NeoForge target explicitly:

```sh
./gradlew :versions:neoforge-26.1:build :versions:neoforge-26.2:build
```

Build all supported targets:

```sh
./gradlew build :versions:neoforge-26.1:build :versions:neoforge-26.2:build
```

Fabric Modrinth publishing remains limited to the Fabric modules. NeoForge is
not included in `publishModrinth`.
