# Spark Platform

Spark Platform centralizes dependency and image management for Spark
applications built with Gradle. Application projects choose a Spark line and
runtime variants; the platform owns Spark, Hadoop, Scala, and variant runtime
artifacts so local, CI, and production builds resolve the same stack.

The project provides:

- a Gradle plugin: `org.openprojectx.spark.platform`
- a platform BOM: `org.openprojectx.spark.platform:platform-bom`
- a platform base-image module that packages selected runtime jars
- a version matrix for Spark 3, Spark 4, Hadoop, Iceberg, Hudi, Paimon, and OpenLineage

The plugin adds platform-owned dependencies from the selected line and variants.
Local builds get those dependencies on `implementation` so the app can run from
an IDE or Gradle. Official builds get them on `compileOnly` because the platform
image provides them at runtime. JVM smoke runs still receive a platform runtime
classpath in CI, so tests do not depend on whatever happened to be installed on
a developer machine. Application builds should manage only their own classes
and application-specific libraries.

## Quick Start

Apply the plugin and select the platform contract:

```kotlin
plugins {
    application
    java
    id("org.openprojectx.spark.platform")
}

sparkPlatform {
    line.set("spark4")
    variants.set(listOf("iceberg"))
    platformVersion.set("0.1.1-SNAPSHOT")
}
```

See the standalone `examples` Gradle build for runnable applications.

```bash
cd examples
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark4-iceberg:run --no-configuration-cache
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark3-paimon:run --no-configuration-cache
```

## Modules

| Module | Purpose |
| --- | --- |
| `core` | Shared catalog naming and normalization logic. |
| `plugin` | Gradle plugin implementation and TestKit coverage. |
| `platform-bom` | Java Platform BOM generated from the version catalog. |
| `platform-image` | Jib-based platform image with selected runtime jars. |
| `examples` | Standalone multi-project build for runnable examples. |

## Documentation

- User reference: `docs/user-reference.adoc`
- Contribution guide: `CONTRIBUTING.md`

## Development

Use the existing Gradle cache when working in this repository:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew test --no-configuration-cache
```

Run the Spark 4 example with:

```bash
cd examples
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark4-iceberg:run --no-configuration-cache
```

Run the Spark 3 Paimon example with:

```bash
cd examples
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark3-paimon:run --no-configuration-cache
```

Build the default Spark 4 platform images locally with:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :platform-image:jibDockerBuildPlatformImages \
  -PsparkPlatform.line=spark4
```

Platform images use Apache Spark base images such as
`spark:4.0.1-scala2.13-java17-python3-r-ubuntu` and layer only the selected
variant jars into `/opt/spark/jars`. Variant names are part of the generated
image tag, for example `spark4-iceberg-0.1.1-SNAPSHOT`.

The aggregate `jibDockerBuildPlatformImages` task builds each selected variant
individually, then builds one combined image for each Scala-compatible variant
group, after applying line-specific isolation rules such as Spark 3 Paimon.
Build one explicit variant set with:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :platform-image:jibDockerBuild \
  -PsparkPlatform.line=spark4 \
  -PsparkPlatform.variants=iceberg,hudi
```

`jibDockerBuild` writes to the local Docker daemon. Inspect a built image with:

```bash
docker inspect ghcr.io/openprojectx/spark-platform:spark4-iceberg-0.1.1-SNAPSHOT
docker run --rm --entrypoint sh ghcr.io/openprojectx/spark-platform:spark4-iceberg-0.1.1-SNAPSHOT \
  -c 'ls -1 /opt/spark/jars | sort'
```

The Jib image tasks are not compatible with Gradle configuration-cache reuse in
the current toolchain, so the build marks those tasks incompatible and Gradle
discards their configuration-cache entries. This does not disable Gradle's build
cache or Jib's image layer reuse.

Application `jibDockerBuild` tasks use the local Docker platform image as their
base image, even in CI. Registry publishing with `jib` keeps the registry base
image reference.

Application images also get the platform jar directory, `/opt/spark/jars/*`, on
the Jib runtime classpath. Spark and variant runtime jars remain owned by the
platform image rather than being redeclared or repackaged by application
projects.

For aggregate tasks such as `integration` or `release` that invoke
`jibDockerBuild` indirectly, set `sparkPlatform.localPlatformImage=true` in the
application build or pass `-PsparkPlatform.localPlatformImage=true`.
