# Spark 4 Iceberg Example

This example is part of the multi-project build in `examples`.
The examples build applies the plugin from the repository checkout via `includeBuild("..")`.
It uses `sparkPlatform.line = spark4` with the `iceberg` variant and declares
Spark/Iceberg dependencies without versions.

```bash
cd ..
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark4-iceberg:compileJava --no-configuration-cache
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark4-iceberg:run --no-configuration-cache
```
