# Spark 3 Paimon Example

This example uses Spark 3 with Apache Paimon.

Paimon Spark 3.5 currently supports Scala 2.12 only, so this example uses
`sparkPlatform.line = spark3` with the isolated `paimon` variant. The plugin
supplies Scala 2.12 Spark and Paimon constraints from the platform-owned bundle;
the example opts into the Spark SQL and Paimon APIs with versionless
`sparkPlatform` dependencies.

The Spark Platform plugin resolves Paimon's `at.yawk.lz4:lz4-java` capability
conflict automatically and applies the Java module options needed by Gradle
`run`.

```bash
cd ..
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark3-paimon:compileJava --no-configuration-cache
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark3-paimon:run --no-configuration-cache
```
