# Spark Platform Examples

This directory is a standalone multi-project Gradle build for runnable examples.
It uses the repository checkout as an included build, so examples exercise the
local plugin and platform BOM.

## Projects

| Project | Description |
| --- | --- |
| `:spark3-paimon` | Spark 3 + Paimon example using Scala 2.12 artifacts. |
| `:spark4-iceberg` | Spark 4 + Iceberg example using a local Iceberg Hadoop catalog. |

## Commands

Run from this directory:

```bash
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark4-iceberg:compileJava --no-configuration-cache
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark4-iceberg:run --no-configuration-cache
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark3-paimon:run --no-configuration-cache
env GRADLE_USER_HOME=/data/.gradle ../gradlew integration --no-configuration-cache
```

Use `integrationJvm` to run every example through Gradle's application plugin
and `integrationDocker` to build every example image and run it with Docker.
Each example also exposes `:example-name:integrationJvm` and
`:example-name:integrationDocker`.

The examples build uses Gradle Java toolchains for JDK 17. The example projects
select Spark Platform lines and variants, and the plugin supplies the
platform-owned Spark and variant runtime dependencies.
