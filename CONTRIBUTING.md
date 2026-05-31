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

## Publish A Snapshot Plugin Locally

Use Maven Local when testing the plugin from a separate consumer project without
going through a remote snapshot repository.

Publish the current snapshot from this repository:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew publishToMavenLocal
```

This publishes the Gradle plugin marker, the plugin implementation jar, `core`,
and the platform BOM using the version in `gradle.properties`, for example
`0.1.38-SNAPSHOT`. The plugin jar includes the producer-owned
`gradle/libs.versions.toml`, so the consumer project does not copy or maintain
Spark, Hadoop, Iceberg, Hudi, Paimon, OpenLineage, or addon versions.

In an external test project, put `mavenLocal()` in plugin management so Gradle
can find the local plugin marker:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
    }
}
```

Then apply the local snapshot plugin version:

```kotlin
plugins {
    id("org.openprojectx.spark.platform") version "0.1.38-SNAPSHOT"
}
```

That version is only the Gradle plugin artifact version. Platform dependency
versions still come from the catalog packaged inside the plugin, and application
dependencies should remain versionless.

## Repository Conventions

- Keep dependency versions in `gradle/libs.versions.toml`.
- The Spark Platform plugin packages this catalog into the published plugin
  artifact. Consumer projects should not copy or maintain Spark Platform
  version catalogs; they only select platform coordinates and add versionless
  dependencies.
- Do not hard-code Spark, Hadoop, Iceberg, Hudi, Paimon, or OpenLineage versions
  in plugin source.
- Add Spark lines by adding catalog aliases and bundles such as
  `spark-platform-spark5-managed`, not by branching on specific version numbers.
- Use isolated variant-managed bundles when a runtime family cannot share the
  line-wide Scala binary version or dependency set.
- Put shared naming or normalization logic in `core`.
- Keep example projects under the standalone `examples/` Gradle build and exclude generated build output.

## Platform Concepts

Spark Platform uses four different selection concepts. Keep them separate when
changing code, docs, examples, or image tags.

### Line

A line is the broad Spark runtime compatibility lane. It answers:

- Which Spark major/minor/runtime family are we targeting?
- Which Scala binary version is part of that runtime?
- Which Hadoop client baseline belongs to that Spark runtime?
- Which base image family should application and platform images use?

Examples:

- `spark3`: Spark 3.5.x with Scala 2.12.
- `spark3-scala213`: Spark 3.5.x with Scala 2.13, modeled as a separate line
  because Scala binary compatibility changes artifact coordinates and base
  images.
- `spark4`: Spark 4.x with Scala 2.13.

Catalog ownership:

- `spark-platform-<line>-managed` contains baseline constraints every
  application on that line should inherit.
- `spark-base-<line>-runtime` contains the jars used to assemble the runtime
  Spark base image.

User-facing DSL:

```kotlin
sparkPlatform {
    line.set("spark4")
}
```

Do not branch in source code on concrete Spark versions such as `if spark4`.
If a future Spark 5 exists, add a `spark5` line and the matching catalog/image
config entries.

### Variant

A variant is an optional runtime family that is tied to Spark compatibility.
It answers:

- Which Spark-integrated table format, lineage integration, or runtime family is
  selected?
- Does this family encode the Spark or Scala binary version in artifact names?
- Does it need JVM module options, capability resolution, or an isolated BOM?
- Should it be visible in explicit image tags?

Examples:

- `iceberg`
- `hudi`
- `paimon`
- `openlineage`

Variants are compatibility-bearing. Iceberg/Hudi/Paimon/OpenLineage artifacts
often encode Spark and Scala in their module names. Paimon on Spark 3 is a good
example: it supports Scala 2.12 only, so the variant selection must respect the
line's Scala binary version and image compatibility.

Catalog ownership:

- `spark-platform-<line>-variant-<variant>` adds optional constraints for that
  variant on top of the line baseline.
- `spark-platform-<line>-variant-<variant>-managed` is only for an isolated
  variant BOM that replaces the normal line baseline for that variant.

User-facing DSL:

```kotlin
sparkPlatform {
    line.set("spark4")
    variants.set(listOf("iceberg"))
}

dependencies {
    sparkPlatform("org.apache.spark:spark-sql_2.13")
    sparkPlatform("org.apache.iceberg:iceberg-spark-runtime-4.0_2.13")
}
```

The user chooses which APIs they compile against, but does not choose versions.
The plugin adds strict platform-owned constraints from its packaged catalog.

### Addon

An addon is an optional platform-owned dependency pack that is not a Spark/Scala
compatibility dimension by itself. It answers:

- Which operational connector or supporting library pack should be present?
- Is this dependency useful across multiple variants?
- Can it be added without changing the Spark/Scala identity of the app?

Examples:

- `hadoopAws`
- `hadoopGcs`
- `icebergAws`
- JDBC driver packs, if added later.

Use addons for fundamental or operational dependencies that can combine with
many variants. For example, S3 support is not an Iceberg variant, Hudi variant,
or Paimon variant; it is a storage connector that several table formats may
use.

Catalog ownership:

- `spark-platform-<line>-addon-<addon>` adds optional constraints and image jars
  for that dependency pack.

User-facing DSL:

```kotlin
sparkPlatform {
    line.set("spark4")
    variants.set(listOf("iceberg"))
    addons.set(listOf("hadoopAws"))
}

dependencies {
    sparkPlatform("org.apache.hadoop:hadoop-aws")
}
```

Explicit image tags include addons, for example
`spark4-iceberg-hadoopaws-<version>`. Published release images should normally
prefer curated profile tags when many addons are included.

### Profile

A profile is a curated image recipe. It answers:

- Which variant/addon set do we want to publish as a short, stable image tag?
- Which combinations are officially supported and worth releasing?
- How do we avoid publishing every possible variant/addon permutation?

Examples:

- `lakehouse` might expand to variants such as `iceberg`, `hudi`, `paimon`,
  `openlineage` plus selected storage addons.
- `observability` could later expand to lineage and metrics-related runtime
  packs.

Profiles are defined in `gradle/spark-platform-image.toml`, not in plugin
source. They are primarily an image publishing and base-image selection concept.
Application builds should still set the `variants` and `addons` they compile
against so dependency constraints match their source code.

User-facing DSL for an application image that wants the curated base image tag:

```kotlin
sparkPlatform {
    line.set("spark4")
    profile.set("lakehouse")
    variants.set(listOf("iceberg"))
    addons.set(listOf("hadoopAws"))
}
```

The profile controls the default application base image tag, for example
`spark4-lakehouse-<platformVersion>`. The variants/addons still control
compile/runtime constraints and local JVM smoke runs.

### Scope And Packaging

`managed` means Gradle dependency constraints. It does not mean "package this
jar into the user application image." A managed bundle constrains versions for
coordinates that the user declares with `sparkPlatform(...)`, or for normal
Gradle configurations listed in `managedConfigurations`.

Packaging still follows the configuration where the dependency is declared. If
`implementation` is listed in `managedConfigurations`, then
`implementation("group:name")` can omit the version and still packages that
dependency like any other implementation dependency. That is appropriate for
application-owned libraries whose versions the platform intentionally manages.
It is not the default contract for Spark-owned runtime jars.

| Concept | Gradle scope | Runtime packaging scope |
| --- | --- | --- |
| `spark-platform-<line>-managed` | Strict constraints for the selected Spark line. No dependency is added unless the app declares a matching coordinate. | No direct packaging. Matching baseline jars are packaged by `spark-base-<line>-runtime` in the Spark base image. |
| `spark-base-<line>-runtime` | Not consumed by application builds. | Builds the clean Spark base image runtime jar set: Spark, Scala, Hadoop, Spark SQL Kafka, Spark Avro, Kubernetes/YARN/MLlib/REPL modules, and baseline transitives. |
| `spark-platform-<line>-variant-<variant>` | Strict constraints when the variant is selected. | Selected variant jars are layered by `platform-image` into `/opt/spark/jars`. |
| `spark-platform-<line>-addon-<addon>` | Strict constraints when the addon is selected. | Selected addon jars are layered by `platform-image` into `/opt/spark/jars`. |
| `profile` | Selects a curated platform image tag. It does not replace `variants` or `addons` for compile intent. | Chooses a curated platform image combination, such as `spark4-lakehouse-<version>`. |

Application image packaging follows the same boundary:

- Local JVM runs use platform dependencies on the runtime classpath so examples,
  tests, and IDE runs work without a preinstalled Spark distribution.
- Official application images treat platform-owned dependencies as provided by
  the image layers. The app image should contain user classes, resources, and
  application-owned libraries, not Spark/Hadoop/variant jars copied again.
- `sparkPlatformJavaExecRuntime` exists for smoke tests in official mode; it is
  a test/runtime convenience and does not change application image packaging.

Do not fix a production `ClassNotFoundException` for Spark/Hadoop/variant
classes by moving the dependency to `implementation`. That makes the app image
carry a second copy of platform runtime jars, even if the version is still
managed. Fix the selected platform contract instead: add the coordinate to the
right managed bundle, add line baseline jars to `spark-base-<line>-runtime`,
add optional content to the matching variant or addon bundle, and make sure the
app selects a platform image containing that bundle.

### Image Layers

There are three runtime image layers in the delivery model:

| Layer | Built by | Contents | Change cadence |
| --- | --- | --- | --- |
| Spark base image | `spark-base-image` | `/opt/spark` plus Gradle-managed Spark line jars from `spark-base-<line>-runtime`: Spark, Scala, Hadoop, Spark SQL Kafka, Spark Avro, Kubernetes/YARN/MLlib/REPL modules, and baseline transitives. | Spark/Hadoop/Scala line upgrades and CVE/runtime baseline fixes. |
| Platform image | `platform-image` | Starts from the Spark base image and adds selected variant/addon jars under `/opt/spark/jars`. | Platform release when supported variants/addons/profiles change. |
| Application image | User project with the plugin and Jib | Starts from the selected platform image and adds user classes, resources, and application-owned libraries. | Application release. |

`spark-base-image` also has an internal layout image. The layout image downloads
and verifies the Apache Spark distribution, installs Python/R where needed, and
strips `/opt/spark/jars` in the same Docker layer. Runtime base images then add
the Gradle-managed jar set with Jib. Layout images are build inputs, not the
runtime contract for user apps.

### Selection Rules

Use this decision table when adding a new dependency family:

| Question | Model it as |
| --- | --- |
| Does it define a Spark/Scala/Hadoop runtime compatibility lane? | New line |
| Does it depend on Spark integration artifacts or encode Spark/Scala in coordinates? | Variant |
| Is it a connector/support pack reusable across variants? | Addon |
| Is it a short name for a curated published image combination? | Profile |
| Is it required for every app on a Spark line? | Line-managed baseline |

Practical examples:

- Spark 3 Scala 2.13 is a line, not a variant, because Scala binary version
  changes core Spark artifact coordinates and base image identity.
- Iceberg is a variant because its runtime artifact is Spark-version and
  Scala-version specific.
- Spark SQL Kafka is line-managed baseline content because it is a Spark-owned
  module whose version follows the selected Spark line and whose jar should be
  present in the clean Spark runtime image.
- Spark Avro is line-managed baseline content for the same reason: its
  Spark-owned artifact uses the selected Scala binary suffix and follows the
  selected Spark line version.
- Hadoop AWS is an addon because S3 support is useful across many table-format
  variants and is not a Spark integration family by itself.
- Iceberg AWS is an addon because it supports Iceberg AWS catalogs/file IO but
  is not a Spark/Scala compatibility dimension.
- `lakehouse` is a profile because it is a curated release image name that
  expands to a chosen set of variants and addons.

## Dependency Catalog Configuration

`gradle/libs.versions.toml` is the source of truth for dependency versions,
module coordinates, and platform bundle names. It is a producer-owned platform
catalog:

- The plugin packages it into the published plugin jar and uses it to add strict
  constraints for application builds.
- `platform-image` uses it to resolve variant/addon jars for
  `ghcr.io/openprojectx/spark-platform`.
- `spark-base-image` uses `spark-base-<line>-runtime` bundles to assemble clean
  Spark runtime base image jars.
- Consumer projects should not copy this file. They only apply the plugin,
  select line/variants/addons/profile, and declare versionless
  `sparkPlatform(...)` dependencies for APIs they compile against.

The catalog has four relevant sections.

### `[versions]`

`[versions]` holds version numbers and version labels.

```toml
[versions]
spark3 = "3.5.8"
"spark3-scala213" = "3.5.8"
spark4 = "4.0.1"
hadoopSpark3 = "3.4.2"
hadoopSpark4 = "3.4.2"
iceberg = "1.10.0"
kafkaClients = "3.9.1"
```

Naming guidance:

- Use one Spark version key per line. If the same Spark version has different
  Scala binary lanes, keep distinct line keys such as `spark3` and
  `spark3-scala213`.
- Keep Hadoop version keys line-specific when they are part of the Spark
  runtime baseline, for example `hadoopSpark3` and `hadoopSpark4`.
- Use shared version keys only when the same upstream library version is
  intentionally used across lines.
- Do not encode versions in plugin source or build scripts when they can live
  here.

Implications:

- Changing a version here affects plugin constraints, platform image jars, and
  base runtime image jars for any bundles that reference that version key.
- Version upgrades should be reviewed as platform changes, not as local example
  or application changes.

### `[libraries]`

`[libraries]` maps catalog aliases to concrete Maven coordinates and version
keys.

```toml
[libraries]
spark4Sql = { module = "org.apache.spark:spark-sql_2.13", version.ref = "spark4" }
spark4Iceberg = { module = "org.apache.iceberg:iceberg-spark-runtime-4.0_2.13", version.ref = "iceberg" }
spark4Avro = { module = "org.apache.spark:spark-avro_2.13", version.ref = "spark4" }
spark4Kafka = { module = "org.apache.spark:spark-sql-kafka-0-10_2.13", version.ref = "spark4" }
sparkKafkaClients = { module = "org.apache.kafka:kafka-clients", version.ref = "kafkaClients" }
icebergAwsBundle = { module = "org.apache.iceberg:iceberg-aws-bundle", version.ref = "iceberg" }
spark4HadoopAws = { module = "org.apache.hadoop:hadoop-aws", version.ref = "hadoopSpark4" }
```

Naming guidance:

- Prefix Spark-line-specific aliases with the line, for example `spark3Sql`,
  `spark3Scala213Sql`, and `spark4Sql`.
- Use aliases that describe the platform concept and artifact, not the current
  version number.
- Keep Scala binary compatibility explicit in module coordinates. For example,
  `spark-sql_2.12` and `spark-sql_2.13` are different artifacts and should be
  represented by different aliases.
- For addons that are Scala-agnostic, still add line-specific aliases when the
  version should follow the line baseline, for example `spark3HadoopAws` and
  `spark4HadoopAws`.

Implications:

- A library alias alone does not make the dependency managed. It must be added
  to a platform bundle.
- Application users normally do not use these aliases. They write versionless
  Maven coordinates in `sparkPlatform(...)`; the plugin matches them with
  constraints derived from bundles.

### `[bundles]`

`[bundles]` is where platform concepts become dependency sets. This is the most
important section for Spark Platform behavior.

Required bundle patterns:

```text
spark-platform-<line>-managed
spark-platform-<line>-variant-<variant>
spark-platform-<line>-variant-<variant>-managed
spark-platform-<line>-addon-<addon>
spark-base-<line>-runtime
```

Example:

```toml
[bundles]
spark-platform-spark4-managed = [
    "spark4Core",
    "spark4Sql",
    "spark4Hive",
    "spark4Avro",
    "spark4Kafka",
    "spark4HadoopClientApi",
    "spark4HadoopClientRuntime",
]
spark-platform-spark4-variant-iceberg = ["spark4Iceberg"]
spark-platform-spark4-addon-hadoopAws = ["spark4HadoopAws"]
spark-platform-spark4-addon-icebergAws = ["icebergAwsBundle"]
spark-base-spark4-runtime = [
    "spark4Core",
    "spark4Sql",
    "spark4Hive",
    "spark4Avro",
    "spark4Kafka",
    "spark4HiveThriftserver",
    "spark4Yarn",
    "spark4Kubernetes",
    "spark4Mllib",
    "spark4Graphx",
    "spark4Repl",
    "spark4HadoopClientApi",
    "spark4HadoopClientRuntime",
]
```

Bundle meanings:

- `spark-platform-<line>-managed`:
  baseline constraints for application builds on that line. Put only libraries
  every app on that line should inherit.
- `spark-platform-<line>-variant-<variant>`:
  optional compatibility-bearing runtime family, such as Iceberg, Hudi, Paimon,
  or OpenLineage.
- `spark-platform-<line>-variant-<variant>-managed`:
  isolated variant BOM. Use only when a variant cannot share the normal
  line-managed baseline.
- `spark-platform-<line>-addon-<addon>`:
  optional reusable support pack, such as Hadoop AWS, Hadoop GCS, Iceberg AWS,
  or future JDBC driver packs.
- `spark-base-<line>-runtime`:
  jars that make up the clean Spark runtime base image for the line. This is
  broader than application constraints because the base image needs Spark
  runtime modules such as YARN, Kubernetes, MLlib, GraphX, and REPL jars.

Implications:

- The plugin derives bundle names from `line`, `variants`, and `addons`.
  Adding a correctly named bundle is what makes a new line, variant, or addon
  discoverable without plugin source changes.
- The platform image build also derives bundle names. If
  `spark-platform-image.toml` lists `hadoopAws`, the catalog must contain
  `spark-platform-<line>-addon-hadoopAws` for that line.
- Keep baseline, variants, and addons separate. Do not put every optional
  connector into `spark-platform-<line>-managed`, or all users will inherit it.
- Do not put a Scala 2.12 artifact in a Scala 2.13 line bundle.

### `[plugins]`

`[plugins]` contains Gradle plugin coordinates used by this repository build,
for example Jib or Kotlin plugins.

```toml
[plugins]
jib = { id = "com.google.cloud.tools.jib", version.ref = "jib" }
```

This section is build tooling metadata. It is not part of Spark Platform runtime
selection and should not be used to model Spark variants or addons.

### How The Catalog Drives Builds

For an application build:

1. The user applies `org.openprojectx.spark.platform`.
2. The plugin reads the packaged catalog from its own jar.
3. `line`, `variants`, and `addons` determine bundle names.
4. The plugin adds strict constraints for dependencies in those bundles.
5. The user adds versionless `sparkPlatform(...)` dependencies for the APIs they
   actually compile against.

For a platform image build:

1. `spark-platform-image.toml` chooses the line, variants, addons, or profile.
2. The build maps selected variants/addons to catalog bundle names.
3. Gradle resolves the jars from the catalog-managed coordinates and versions.
4. The image build layers those jars into `/opt/spark/jars`.

For a Spark runtime base image build:

1. The selected line maps to `spark-base-<line>-runtime`.
2. Gradle resolves the full Spark runtime jar set from catalog-managed
   coordinates.
3. Jib adds those jars to a stripped Spark layout image.

When adding a dependency, update this file first. Update
`gradle/spark-platform-image.toml` only when that dependency should affect image
defaults, curated profiles, transitive excludes, or capability resolution.

### Forcing A Transitive Version

To force a transitive dependency version, model that transitive dependency as a
normal catalog alias and add it to the bundle that owns the runtime surface. Do
not hard-code a resolution strategy in source code for one module.

For Spark SQL Kafka, Spark pulls `org.apache.kafka:kafka-clients`
transitively. The platform pins it explicitly:

```toml
[versions]
kafkaClients = "3.9.1"

[libraries]
sparkKafkaClients = { module = "org.apache.kafka:kafka-clients", version.ref = "kafkaClients" }

[bundles]
spark-platform-spark4-managed = ["spark4Kafka", "sparkKafkaClients", "..."]
spark-base-spark4-runtime = ["spark4Kafka", "sparkKafkaClients", "..."]
```

Putting the alias in `spark-platform-<line>-managed` makes the plugin and BOM
publish a strict constraint for application builds. Putting the same alias in
`spark-base-<line>-runtime` makes the clean Spark base image resolve and package
that exact jar. Use the same pattern for any future transitive upgrade that is
owned by the platform runtime.

### Example: Adding Spark SQL Kafka

`spark-sql-kafka-0-10` is modeled as line-managed baseline content, not as a
variant or addon. It is a Spark-owned module, its version follows the selected
Spark line, and the runtime image should provide the matching jar by default.
Its Kafka client transitive is pinned separately by `sparkKafkaClients`.

```toml
[libraries]
spark3Kafka = { module = "org.apache.spark:spark-sql-kafka-0-10_2.12", version.ref = "spark3" }
spark3Scala213Kafka = { module = "org.apache.spark:spark-sql-kafka-0-10_2.13", version.ref = "spark3-scala213" }
spark4Kafka = { module = "org.apache.spark:spark-sql-kafka-0-10_2.13", version.ref = "spark4" }
sparkKafkaClients = { module = "org.apache.kafka:kafka-clients", version.ref = "kafkaClients" }

[bundles]
spark-platform-spark3-managed = ["spark3Core", "spark3Sql", "spark3Hive", "spark3Kafka", "sparkKafkaClients", "..."]
spark-platform-spark3-scala213-managed = ["spark3Scala213Core", "spark3Scala213Sql", "spark3Scala213Hive", "spark3Scala213Kafka", "sparkKafkaClients", "..."]
spark-platform-spark4-managed = ["spark4Core", "spark4Sql", "spark4Hive", "spark4Kafka", "sparkKafkaClients", "..."]

spark-base-spark3-runtime = ["spark3Core", "spark3Sql", "spark3Hive", "spark3Kafka", "sparkKafkaClients", "..."]
spark-base-spark3-scala213-runtime = ["spark3Scala213Core", "spark3Scala213Sql", "spark3Scala213Hive", "spark3Scala213Kafka", "sparkKafkaClients", "..."]
spark-base-spark4-runtime = ["spark4Core", "spark4Sql", "spark4Hive", "spark4Kafka", "sparkKafkaClients", "..."]
```

Users still opt into the Kafka API explicitly and without versions:

```kotlin
sparkPlatform {
    line.set("spark4")
}

dependencies {
    sparkPlatform("org.apache.spark:spark-sql_2.13")
    sparkPlatform("org.apache.spark:spark-sql-kafka-0-10_2.13")
}
```

That application gets strict constraints for Spark SQL and Spark SQL Kafka from
the packaged platform catalog, while Spark base runtime images get the matching
Kafka jar from the `spark-base-<line>-runtime` bundle.

## Platform Image Configuration

`gradle/spark-platform-image.toml` is the data model for platform image
assembly and publishing. It does not define dependency versions; versions and
coordinates live in `gradle/libs.versions.toml`, which is packaged into the
plugin and used by platform image builds. The image config defines how those
catalog bundles are selected, combined, excluded, and tagged for Docker images.

The relationship is:

| Concept | Defined in | Used for |
| --- | --- | --- |
| Dependency versions and module coordinates | `gradle/libs.versions.toml` | Plugin constraints, platform image jar resolution, Spark base runtime jars |
| Line/variant/addon bundle names | `gradle/libs.versions.toml` | Mapping platform concepts to actual dependency coordinates |
| Base image repository/suffix | `gradle/spark-platform-image.toml` | Choosing the upstream Spark runtime base image for a line |
| Default variants/addons | `gradle/spark-platform-image.toml` | Aggregate image tasks when CLI properties omit selections |
| Profiles | `gradle/spark-platform-image.toml` | Curated release image tags and curated variant/addon combinations |
| Excludes and capability rules | `gradle/spark-platform-image.toml` | Resolution hygiene while building platform images |

### Naming Conventions

Line ids are lowercase image/runtime lanes, for example `spark3`,
`spark3-scala213`, and `spark4`. The same line id must appear consistently in:

- `spark-platform-<line>-managed` bundles.
- `spark-base-<line>-runtime` bundles.
- `[baseImages.<line>]`.
- `[defaultVariants]`, `[defaultAddons]`, `[defaultProfiles]`.
- `[profiles.<line>.<profile>]`.

Variant, addon, and profile ids use lower camel case in Gradle and TOML config,
for example `hadoopAws` and `hadoopGcs`. CLI inputs accept dash, underscore, or
camel case and normalize to the same id, so `hadoop-aws`, `hadoop_aws`, and
`hadoopAws` all address the `hadoopAws` addon. Image tag segments are rendered
lowercase and joined with `-`, so `hadoopAws` becomes `hadoopaws` in a tag.

Catalog bundle names must use these exact patterns:

```text
spark-platform-<line>-managed
spark-platform-<line>-variant-<variant>
spark-platform-<line>-variant-<variant>-managed
spark-platform-<line>-addon-<addon>
spark-base-<line>-runtime
```

Image config table names must use these exact patterns:

```toml
[baseImages.<line>]
[defaultVariants]
[defaultAddons]
[defaultProfiles]
[profiles.<line>.<profile>]
[isolatedVariants]
[resolution]
[[capabilityResolution]]
```

### `baseImages`

`[baseImages.<line>]` tells `platform-image` which Spark runtime image to use as
the parent for a platform image line.

```toml
[baseImages.spark4]
repository = "ghcr.io/openprojectx/spark"
suffix = "-java17-python3-r-ubuntu"
```

The generated base image reference is:

```text
<repository>:<line-version-and-scala-part><suffix>
```

The version and Scala part come from the selected Spark line and the
project-owned Spark base image model. The suffix is intentionally fixed here so
platform images consistently use the Java 17, Python 3, R, Ubuntu runtime shape.

Implications:

- A new Spark line needs a `[baseImages.<line>]` entry.
- The repository should normally be `ghcr.io/openprojectx/spark`, because
  platform images consume the base images this project publishes.
- Do not point a Scala 2.13 line at a Scala 2.12 base image. Model that as a
  separate line and publish the matching base image.

### `defaultVariants`

`[defaultVariants]` lists the variants selected by aggregate platform image
tasks when `-PsparkPlatform.variants` is not provided.

```toml
[defaultVariants]
spark4 = ["iceberg", "hudi", "paimon", "openlineage"]
```

Implications:

- This is not a user dependency default for application source code. It is a
  platform image build default.
- Each listed variant must have a matching
  `spark-platform-<line>-variant-<variant>` bundle, or a
  `spark-platform-<line>-variant-<variant>-managed` bundle, in
  `gradle/libs.versions.toml`.
- Keep this list to supported runtime families for the line. If a variant is
  Scala 2.12-only, do not list it for a Scala 2.13 line.

### `defaultAddons`

`[defaultAddons]` lists addon packs selected by aggregate platform image tasks
when `-PsparkPlatform.addons` is not provided.

```toml
[defaultAddons]
spark4 = ["hadoopAws"]
```

Implications:

- Addons are reusable operational packs, not Spark compatibility dimensions.
- Each listed addon must have a matching `spark-platform-<line>-addon-<addon>`
  bundle in `gradle/libs.versions.toml`.
- Defaults affect aggregate image builds. A user can still request explicit
  addons with `-PsparkPlatform.addons=hadoopAws,hadoopGcs,icebergAws` or
  `sparkPlatform { addons.set(...) }`.

### `defaultProfiles`

`[defaultProfiles]` lists curated profiles published by aggregate publish tasks
when no explicit profile is requested.

```toml
[defaultProfiles]
spark4 = ["lakehouse"]
```

Implications:

- This is the curated release surface. It keeps the number of published image
  tags controlled.
- Release workflows should normally publish profiles, not every individual
  variant/addon permutation.
- Each listed profile must have a matching `[profiles.<line>.<profile>]` table.

### `profiles`

`[profiles.<line>.<profile>]` expands a short profile name to a curated set of
variants and addons.

```toml
[profiles.spark4.lakehouse]
variants = ["iceberg", "hudi", "paimon", "openlineage"]
addons = ["hadoopAws", "hadoopGcs", "icebergAws"]
```

The profile affects image tags and image contents:

- Profile tag: `spark4-lakehouse-<version>`.
- Explicit variant/addon tag without profile:
  `spark4-iceberg-hudi-paimon-openlineage-hadoopaws-hadoopgcs-<version>`.

Application builds may set `profile` to choose a curated platform base image
tag, but they should still set the variants/addons their code compiles against.
The profile is not a replacement for dependency intent in the application DSL.

Implications:

- Profiles are where you decide which combinations are worth publishing.
- Profiles may include multiple variants and addons as long as they are
  compatible for the line.
- Avoid turning every dependency into a profile. A profile is a curated product
  surface, not a dependency category.

### `isolatedVariants`

`[isolatedVariants]` is optional. It lists variants that should not be folded
into a normal combined image for that line.

```toml
[isolatedVariants]
spark3 = ["someVariant"]
```

Use this when a variant cannot safely share the normal line-wide combination,
for example because it needs a different Scala binary version, a replacement
dependency family, or an isolated variant-managed BOM.

Implications:

- The aggregate image task can still build an isolated variant image.
- The combined image builder excludes isolated variants from the default
  compatible combined set.
- If a variant is truly a different Spark/Scala runtime lane, prefer a new line
  over overusing `isolatedVariants`.

### `resolution.baseProvidedTransitiveGroups`

`[resolution].baseProvidedTransitiveGroups` tells `platform-image` which
transitive dependency groups should be excluded when resolving variant/addon
jars for `/opt/spark/jars`.

```toml
[resolution]
baseProvidedTransitiveGroups = [
    "org.apache.spark",
    "org.apache.hadoop",
    "org.scala-lang",
]
```

Implications:

- These groups are expected to be provided by the Spark base runtime image or by
  line-managed baseline jars.
- Excluding them keeps variant/addon layers from reintroducing duplicate Spark,
  Hadoop, Scala, logging, compression, or other baseline jars.
- Do not add a group here just to hide a conflict. Add it only when the group is
  intentionally owned by the base runtime or line baseline.

### `capabilityResolution`

`[[capabilityResolution]]` adds data-driven Gradle capability-resolution rules
for platform image dependency resolution.

```toml
[[capabilityResolution]]
capability = "org.lz4:lz4-java"
preferredProvider = "at.yawk.lz4:lz4-java"
reason = "Paimon's Spark bundle expects at.yawk.lz4:lz4-java when both LZ4 providers are present."
```

Implications:

- Use this for generic resolution rules that may apply to more than one future
  variant/addon.
- Keep the reason specific enough that future maintainers know why the provider
  was selected.
- If the same capability rule is needed for application dependency resolution,
  model it in `core` as well so plugin users get the same behavior outside image
  builds.

### How The Tables Work Together

When building a platform image for a line:

1. `baseImages.<line>` selects the Spark runtime base image.
2. `defaultVariants.<line>` and `defaultAddons.<line>` provide defaults unless
   CLI properties specify variants/addons.
3. If `sparkPlatform.profile` is set, `profiles.<line>.<profile>` expands to
   the curated variants/addons and the output tag uses the profile name.
4. The build maps every selected variant/addon to catalog bundles in
   `gradle/libs.versions.toml`.
5. `resolution.baseProvidedTransitiveGroups` prevents base-owned groups from
   being duplicated in variant/addon layers.
6. `capabilityResolution` selects preferred providers when dependencies expose
   overlapping Gradle capabilities.
7. The resulting jars are layered into `/opt/spark/jars` on top of the selected
   base image.

When adding a new optional dependency family:

1. Decide whether it is a variant or addon using the selection rules above.
2. Add version and library aliases in `gradle/libs.versions.toml`.
3. Add the matching `spark-platform-<line>-variant-<name>` or
   `spark-platform-<line>-addon-<name>` bundle.
4. Add it to `defaultVariants` or `defaultAddons` only if aggregate images
   should include it by default.
5. Add it to one or more `[profiles.<line>.<profile>]` tables if it should be
   part of a curated release tag.
6. Add `baseProvidedTransitiveGroups` or `capabilityResolution` entries only if
   resolution requires them.

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

Build and run the Spark 4 + Iceberg example app image:

```bash
cd examples
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark4-iceberg:jibDockerBuild --no-configuration-cache

docker run --rm \
  -e SPARK_DRIVER_BIND_ADDRESS=0.0.0.0 \
  org.openprojectx.spark.platform.examples/spark4-iceberg:0.1.1-snapshot \
  driver \
  --master 'local[*]' \
  --conf spark.driver.host=127.0.0.1 \
  --class org.openprojectx.spark.platform.examples.spark4.Spark4IcebergExample \
  local:///opt/spark/app/app.jar
```

The `ghcr.io/openprojectx/spark-platform:<tag>` images are platform/runtime
base images. They contain Spark and platform-managed jars under
`/opt/spark/jars`, but they do not run an example application by default. The
example application images are built from the standalone `examples/` Gradle
build and use the lowercased Gradle group, project name, and version:
`org.openprojectx.spark.platform.examples/<example-project>:<version>`.
Application images keep Spark's official `/opt/entrypoint.sh` contract and
stage the user jar at `local:///opt/spark/app/app.jar`, which is the path to use
from Spark Operator `SparkApplication.spec.jars`.

Run the Spark 3 + Paimon example:

```bash
cd examples
env GRADLE_USER_HOME=/data/.gradle ../gradlew :spark3-paimon:run --no-configuration-cache
```

Run every example as both a JVM app and a Docker app image:

```bash
cd examples
env GRADLE_USER_HOME=/data/.gradle ../gradlew integration --no-configuration-cache
```

The `integrationDocker` task builds each example app image with
`jibDockerBuild` and then runs `docker run --rm` against that app image. The
repository root `integrationTest` task delegates to this examples integration
task, so release verification covers example app image execution as well as the
plain JVM `run` path.

Build a platform image:

```bash
env GRADLE_USER_HOME=/data/.gradle ./gradlew :platform-image:jibDockerBuildPlatformImages \
  -PsparkPlatform.line=spark4 \
  -PsparkPlatform.buildIndividualImages=false \
  -PsparkPlatform.buildCombinedImages=true
```

Use `sparkPlatform.buildIndividualImages=false` for curated profile tags. Leave
it enabled only when you deliberately need one tag per variant for debugging or
publishing a single optional runtime family.

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

List locally built snapshot image tags for one repository:

```bash
IMAGE_REPO=ghcr.io/openprojectx/spark-platform
docker image ls "$IMAGE_REPO" \
  --format '{{.Repository}}:{{.Tag}} {{.CreatedAt}}' \
  | grep -- '-SNAPSHOT '
```

Delete old local snapshot image tags while keeping the latest snapshot version
for that repository:

```bash
IMAGE_REPO=ghcr.io/openprojectx/spark-platform
LATEST_SNAPSHOT_VERSION="$(
  docker image ls "$IMAGE_REPO" --format '{{.Tag}}' \
    | grep -E -- '[0-9]+(\.[0-9]+)*-SNAPSHOT$' \
    | sed -E 's/.*-([0-9]+(\.[0-9]+)*-SNAPSHOT)$/\1/' \
    | sort -Vu \
    | tail -n 1
)"

docker image ls "$IMAGE_REPO" --format '{{.Repository}}:{{.Tag}}' \
  | grep -E -- '[0-9]+(\.[0-9]+)*-SNAPSHOT$' \
  | grep -v -- "-${LATEST_SNAPSHOT_VERSION}$" \
  | xargs -r docker image rm
```

The cleanup is local to the Docker daemon. It removes only tags for
`IMAGE_REPO` that end in a `*-SNAPSHOT` version and leaves the latest snapshot
version in place across all profiles, variants, and addons.

## Adding a Managed Dependency

1. Add or update the version in `gradle/libs.versions.toml`.
2. Add a library alias for each supported Spark line.
3. Add the alias to the matching managed bundle.
4. Add a variant or addon bundle if the dependency is optional image content.
5. Add a `spark-platform-<line>-variant-<variant>-managed` bundle only when the
   variant needs an isolated BOM.
6. Add or update plugin/platform-image tests when behavior changes.
7. Update `docs/user-reference.adoc`.

### Extension Pattern

The default extension point is the version catalog. Do not add `if spark3`,
`if spark4`, or connector-specific branches in plugin source when a dependency
can be represented as a catalog alias and bundle.

Choose the smallest ownership level that matches the library:

- Line-managed baseline:
  use `spark-platform-<line>-managed` and `spark-base-<line>-runtime` only for
  libraries that every application on that Spark line must receive, such as
  Spark core modules and the Hadoop client baseline.
- Optional variant:
  use `spark-platform-<line>-variant-<variant>` for optional runtime families
  such as `iceberg`, `hudi`, `paimon`, or `openlineage`. Variants are
  compatibility-bearing: they may encode Spark or Scala versions, affect JVM
  options, or determine the base image shape. Variants are part of explicit
  image tags.
- Optional addon:
  use `spark-platform-<line>-addon-<addon>` for platform-owned dependency packs
  that are not Spark/Scala compatibility dimensions, such as `hadoopAws`,
  `hadoopGcs`, or JDBC drivers. Addons are included by curated profiles or by
  `sparkPlatform.addons`, but curated release tags use the profile name instead
  of listing every addon.
- Isolated variant BOM:
  use `spark-platform-<line>-variant-<variant>-managed` only when that variant
  cannot safely combine with the normal line-managed bundle, for example a
  Scala-binary mismatch or a dependency family that must replace the line
  baseline as a unit.

For a storage connector such as Hadoop AWS or GCS, the normal pattern is:

```toml
[versions]
hadoopAwsSpark3 = "3.4.2"
hadoopAwsSpark4 = "3.4.2"
gcsConnector = "hadoop3-2.x.x"

[libraries]
spark3HadoopAws = { module = "org.apache.hadoop:hadoop-aws", version.ref = "hadoopAwsSpark3" }
spark4HadoopAws = { module = "org.apache.hadoop:hadoop-aws", version.ref = "hadoopAwsSpark4" }
spark3GcsConnector = { module = "com.google.cloud.bigdataoss:gcs-connector", version.ref = "gcsConnector" }
spark4GcsConnector = { module = "com.google.cloud.bigdataoss:gcs-connector", version.ref = "gcsConnector" }

[bundles]
spark-platform-spark3-addon-hadoopAws = ["spark3HadoopAws"]
spark-platform-spark4-addon-hadoopAws = ["spark4HadoopAws"]
spark-platform-spark3-addon-hadoopGcs = ["spark3GcsConnector"]
spark-platform-spark4-addon-hadoopGcs = ["spark4GcsConnector"]
```

With those bundle names in place, no plugin code is needed. Users select the
addon:

```kotlin
sparkPlatform {
    line.set("spark4")
    addons.set(listOf("hadoopAws"))
}

dependencies {
    implementation(sparkPlatform("org.apache.hadoop:hadoop-aws"))
}
```

The application controls which connector classes it actually uses; Spark
Platform owns the versions, constraints, and platform image contents for the
selected addon. If the connector also needs JVM module options, capability
resolution, exclusions, or image-entrypoint behavior, add that logic in `core`
as a data-driven rule and cover it with focused tests.

Use lower camel case for multi-word variant and addon ids in Gradle bundle names
because image tags use `-` to separate tag parts. For example, use `hadoopAws`
for the catalog id; the rendered image tag segment is lowercased as
`hadoopaws`. CLI aliases such as `hadoop-aws` and `hadoop_aws` are normalized
to `hadoopAws`.

Platform image behavior is data, not build-script code. Add or remove curated
line profiles, base-image defaults, isolated variants, transitive excludes, and
capability-resolution rules in `gradle/spark-platform-image.toml`. Use
`-PsparkPlatform.imageConfig=<path>` when testing an alternate image config
file.

For a new library family, the normal path should be config-only:

1. Add versions, libraries, and `spark-platform-<line>-variant-<variant>` or
   `spark-platform-<line>-addon-<addon>`
   bundles in `gradle/libs.versions.toml`.
2. Add the variant or addon to the curated line profiles in
   `gradle/spark-platform-image.toml`.
3. Add transitive-exclude groups or capability-resolution rules in the same
   image config when resolution needs them.

Change `platform-image/build.gradle.kts` only when the image build needs a new
kind of behavior, not when it needs another dependency, variant, profile, base
image, exclusion, or capability rule.

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
