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

Build stripped Spark layout images:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :spark-base-image:dockerBuildSparkBaseLayoutImages
```

Publish stripped Spark layout images:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :spark-base-image:dockerPushSparkBaseLayoutImages
```

Build Gradle-managed Spark runtime base images from already-published layout
images:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :spark-base-image:dockerBuildSparkBaseRuntimeImages
```

Publish Gradle-managed Spark runtime base images:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :spark-base-image:dockerPushSparkBaseRuntimeImages
```

The `Base Images` GitHub workflow is manual and publishes only layout images.
Layout images download and verify Apache Spark distributions, so trigger that
workflow deliberately for Spark distribution changes, base OS refreshes, or CVE
fixes. Runtime base images are assembled from Gradle-managed jars and are part
of the normal release workflow before platform images are published.

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

## Upgrading a Spark Line

1. Check the Apache release archive for the target Spark version and confirm the
   binary distributions that exist, especially `bin-hadoop3`,
   `bin-hadoop3-scala2.13`, and Hadoop-provided distributions.
   When a required Scala/Hadoop combination does not exist as an Apache binary
   distribution, model it as a separate Spark line and assemble its jars through
   Gradle/Jib instead of reusing a different Scala binary distribution.
2. Check `versions.json` in
   `https://github.com/apache/spark-docker.git` and Docker Hub tags for the
   matching official Spark image shape. Use it as a compatibility reference,
   not as the platform runtime source.
3. Keep the Scala binary version explicit for each line. Spark 3 defaults to
   Scala 2.12, `spark3-scala213` is a separate Scala 2.13 line, and Spark 4 is
   Scala 2.13.
4. Update only version catalog entries in `gradle/libs.versions.toml`; do not
   hard-code upgraded versions in plugin or platform-image source.
5. Re-check variant artifacts for the Spark line, for example Iceberg, Hudi,
   Paimon, and OpenLineage coordinates. Some variants encode both Spark and
   Scala versions in the artifact name.
6. Check Hadoop deliberately. Runtime base images must get Hadoop from the
   Gradle version catalog and BOM, not from jars bundled inside the Apache Spark
   binary distribution. The layout image strips distribution jars before the
   Gradle/Jib runtime image adds catalog-managed jars.
7. Update tests and docs that assert the Spark version or show concrete image
   tags.
8. Verify the plugin and image tag selection:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :plugin:test --no-configuration-cache
env GRADLE_USER_HOME=/data/.gradle ./gradlew :platform-image:jibDockerBuild --dry-run --no-configuration-cache \
  -PsparkPlatform.line=spark3 \
  -PsparkPlatform.variants=iceberg
```

When adding a Spark line, add a line spec to `spark-base-image/build.gradle.kts`
and a `spark-base-<line>-runtime` bundle in `gradle/libs.versions.toml`. The
layout image should preserve the Apache Spark filesystem contract for
Kubernetes and Spark Operator use, while the runtime image should assemble
`/opt/spark/jars` through Gradle/Jib from catalog-managed dependencies.

## Spark Base Image Model

Spark base images are intentionally split so heavy OS work and jar ownership do
not share the same layer:

- Layout image:
  downloads and verifies the immutable Apache Spark binary distribution,
  extracts the Spark filesystem layout, preserves scripts/configuration needed
  by Kubernetes and Spark Operator, and removes distribution jars from
  `/opt/spark/jars`.
- Feature layout images:
  start from the stripped layout image and install heavyweight OS features such
  as `python3`, `r`, or `python3-r`.
- Runtime image:
  starts from a published layout or feature layout image and adds only
  Gradle-resolved runtime jars from `gradle/libs.versions.toml` and
  `platform-bom`.

This keeps the heavy Spark distribution download and apt install layers out of
the normal release path, but still lets a release pick up Spark, Hadoop, Scala,
and related jar changes from the version catalog. The release task publishes
runtime base images first, then publishes `ghcr.io/openprojectx/spark-platform`
images on top of them.

Manual layout image publish:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :spark-base-image:dockerPushSparkBaseLayoutImages
```

This publishes both root layout tags such as
`ghcr.io/openprojectx/spark:3.5.8-scala2.13-java17-layout-ubuntu` and feature
layout tags such as
`ghcr.io/openprojectx/spark:3.5.8-scala2.13-java17-python3-r-layout-ubuntu`.
Run it when Spark distributions, base OS packages, Python, R, or CVE fixes need
to move.

Release-managed runtime image publish:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :spark-base-image:dockerPushSparkBaseRuntimeImages
```

Full local rebuild, including layout and runtime images:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :spark-base-image:dockerBuildSparkBaseImages \
  -PsparkBaseImage.localLayoutImages=true
```

Use `sparkBaseImage.localLayoutImages=true` only for local rebuilds. Release
builds intentionally use published layout images from `ghcr.io/openprojectx/spark`.

List jars in a runtime base image:

```bash
docker run --rm --entrypoint sh \
  ghcr.io/openprojectx/spark:3.5.8-scala2.13-java17-python3-r-ubuntu \
  -c 'find /opt/spark/jars -maxdepth 1 -type f -name "*.jar" -printf "%f\n" | sort'
```

Check that a layout image has no distribution jars:

```bash
docker run --rm --entrypoint sh \
  ghcr.io/openprojectx/spark:3.5.8-scala2.13-java17-python3-r-layout-ubuntu \
  -c 'find /opt/spark/jars -maxdepth 1 -type f -name "*.jar" -printf "%f\n" | sort'
```

The second command should print no jar names. If it prints Spark distribution
jars, the layout image is no longer clean and runtime ownership has leaked back
to the Apache binary distribution.

## Pull Request Checklist

- The change is covered by focused tests or the verification gap is documented.
- The example project still imports and runs with JDK 17.
- Public DSL changes are documented in `docs/user-reference.adoc`.
- New dependencies are managed through the version catalog.
