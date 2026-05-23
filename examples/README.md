# Spark Platform Examples

This directory is a standalone multi-project Gradle build for runnable examples.
It uses the repository checkout as an included build, so examples exercise the
local plugin and platform BOM.

## Projects

| Project | Description |
| --- | --- |
| `:spark4` | Spark 4 + Iceberg example using a local Iceberg Hadoop catalog. |

## Commands

Run from this directory:

```bash
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark4:compileJava --no-configuration-cache
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark4:run --no-configuration-cache
```

The examples build pins Gradle and Java execution to JDK 17 for IDE imports and
local runs.
