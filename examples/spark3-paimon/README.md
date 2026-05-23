# Spark 3 Paimon Example

This example uses Spark 3 with Apache Paimon.

Paimon Spark 3.5 currently supports Scala 2.12 only, so this example uses
`sparkPlatform.line = spark3` with the isolated `paimon` variant and declares
the `_2.12` Spark/Paimon artifacts without versions.

The example also resolves Paimon's `at.yawk.lz4:lz4-java` capability conflict
locally and adds Spark 3's Java 17 module export for Gradle `run`.

```bash
cd ..
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark3-paimon:compileJava --no-configuration-cache
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark3-paimon:run --no-configuration-cache
```
