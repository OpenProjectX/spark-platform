# Contributing

This project is a Gradle plugin and platform build for Spark applications.
Changes should preserve the version-catalog-driven dependency model and keep the
example project runnable from an IDE.

## Requirements

- JDK 17
- Gradle wrapper from this repository
- Existing Gradle cache when available:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew --version
```

## Repository Conventions

- Keep dependency versions in `gradle/libs.versions.toml`.
- Do not hard-code Spark, Hadoop, Iceberg, Hudi, Paimon, or OpenLineage versions
  in plugin source.
- Add Spark lines by adding catalog aliases and bundles such as
  `spark-platform-spark5-managed`, not by branching on specific version numbers.
- Use isolated variant-managed bundles when a runtime family cannot share the
  line-wide Scala binary version or dependency set.
- Put shared naming or normalization logic in `core`.
- Keep example projects under the standalone `examples/` Gradle build and exclude generated build output.

## Useful Commands

Run all tests:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew test --no-configuration-cache
```

Run plugin functional tests:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :plugin:test --no-configuration-cache
```

Run the Spark 4 + Iceberg example:

```bash
cd examples
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark4-iceberg:run --no-configuration-cache
```

Run the Spark 3 + Paimon example:

```bash
cd examples
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark3-paimon:run --no-configuration-cache
```

Build a platform image:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :platform-image:jibDockerBuildPlatformImages \
  -PsparkPlatform.line=spark4
```

List jars in a built platform image:

```bash
docker run --rm --entrypoint ls \
  ghcr.io/openprojectx/spark-platform:spark4-iceberg-0.1.1-SNAPSHOT \
  /opt/spark/jars
```

Use `--entrypoint` because the Spark image entrypoint treats command arguments
as Spark launch arguments. For a stable sorted jar list:

```bash
docker run --rm --entrypoint sh \
  ghcr.io/openprojectx/spark-platform:spark4-iceberg-0.1.1-SNAPSHOT \
  -c 'find /opt/spark/jars -maxdepth 1 -type f -name "*.jar" -printf "%f\n" | sort'
```

## Adding a Managed Dependency

1. Add or update the version in `gradle/libs.versions.toml`.
2. Add a library alias for each supported Spark line.
3. Add the alias to the matching managed bundle.
4. Add a variant bundle if the dependency is optional image content.
5. Add a `spark-platform-<line>-variant-<variant>-managed` bundle only when the
   variant needs an isolated BOM.
6. Add or update plugin/platform-image tests when behavior changes.
7. Update `docs/user-reference.adoc`.

## Pull Request Checklist

- The change is covered by focused tests or the verification gap is documented.
- The example project still imports and runs with JDK 17.
- Public DSL changes are documented in `docs/user-reference.adoc`.
- New dependencies are managed through the version catalog.
