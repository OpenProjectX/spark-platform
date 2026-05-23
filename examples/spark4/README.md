# Spark 4 Iceberg Example

This example applies the plugin from the repository checkout via `includeBuild("../..")`.
It uses `sparkPlatform.line = spark4` with the `iceberg` variant and declares
Spark/Iceberg dependencies without versions.

```bash
env GRADLE_USER_HOME=/data/.gradle ../../gradlew compileJava --no-configuration-cache
env GRADLE_USER_HOME=/data/.gradle ../../gradlew run --no-configuration-cache
```
