# Spark 4 SQL Example

This example is part of the multi-project build in `examples`.
The examples build applies the plugin from the repository checkout via `includeBuild("..")`.
It selects `sparkPlatform.line = spark4`. The plugin supplies Spark, Scala, and
Hadoop constraints from the platform-owned bundles; the example opts into the
Spark SQL API with a versionless `sparkPlatform` dependency.

```bash
cd ..
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark4-sql:compileJava --no-configuration-cache
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark4-sql:run --no-configuration-cache
```
