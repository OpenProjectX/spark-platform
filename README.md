# Spark Platform

Spark Platform centralizes dependency and image management for Spark
applications built with Gradle.

The project provides:

- a Gradle plugin: `org.openprojectx.spark.platform`
- a platform BOM: `org.openprojectx.spark.platform:platform-bom`
- a platform base-image module that packages selected runtime jars
- a version matrix for Spark 3, Spark 4, Hadoop, Iceberg, Hudi, Paimon, and OpenLineage

The plugin lets application builds declare Spark-platform dependencies without
versions. Local builds get those dependencies on `implementation` so the app can
run from an IDE or Gradle. Official builds get them on `compileOnly` because the
platform image is expected to provide them at runtime.

## Quick Start

Apply the plugin and declare managed dependencies on the `sparkPlatform`
configuration:

```kotlin
plugins {
    application
    java
    id("org.openprojectx.spark.platform")
}

sparkPlatform {
    line.set("spark4")
    platformVersion.set("0.1.0-SNAPSHOT")
}

dependencies {
    sparkPlatform("org.apache.spark:spark-sql_2.13")
    sparkPlatform("org.apache.iceberg:iceberg-spark-runtime-4.0_2.13")
}
```

See `examples/spark4` for a runnable Spark 4 + Iceberg application.

```bash
cd examples/spark4
env GRADLE_USER_HOME=/data/.gradle ../../gradlew run --no-configuration-cache
```

## Modules

| Module | Purpose |
| --- | --- |
| `core` | Shared catalog naming and normalization logic. |
| `plugin` | Gradle plugin implementation and TestKit coverage. |
| `platform-bom` | Java Platform BOM generated from the version catalog. |
| `platform-image` | Jib-based platform image with selected runtime jars. |
| `examples/spark4` | Example Spark 4 + Iceberg application. |

## Documentation

- User reference: `docs/user-reference.adoc`
- Contribution guide: `CONTRIBUTING.md`

## Development

Use the existing Gradle cache when working in this repository:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew test --no-configuration-cache
```

Build the Spark 4 platform image locally with:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :platform-image:jibDockerBuild \
  -PsparkPlatform.line=spark4 \
  -PsparkPlatform.variants=iceberg
```

