import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    java
    alias(libs.plugins.jib)
}

val platformLine = providers.gradleProperty("sparkPlatform.line").orElse("spark3")
val imageRepository = providers.gradleProperty("sparkPlatform.imageRepository")
    .orElse("ghcr.io/openprojectx/spark-platform")
val imageTag = providers.gradleProperty("sparkPlatform.imageTag")
    .orElse("${platformLine.get()}-$version")
val baseImageSuffix = providers.gradleProperty("sparkPlatform.baseImageSuffix")
    .orElse("-java17-python3-r-ubuntu")
val defaultImageVariantsByLine = mapOf(
    "spark3" to setOf("paimon", "openlineage"),
    "spark4" to setOf("iceberg", "hudi", "paimon", "openlineage")
)
val variants = providers.gradleProperty("sparkPlatform.variants")
    .map { it.split(",").map(String::trim).filter(String::isNotEmpty).map(String::lowercase).toSet() }
    .orElse(
        providers.provider {
            defaultImageVariantsByLine[platformLine.get().trim().lowercase()]
                ?: setOf("iceberg", "hudi", "paimon", "openlineage")
        }
    )
val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val scalaBinaryVersionPattern = Regex(""".*_(\d+\.\d+)$""")
val platformJars = configurations.create("platformJars") {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Variant jars layered on top of the selected Apache Spark image."
}

data class ModuleCoordinate(
    val group: String,
    val name: String
) {
    override fun toString(): String = "$group:$name"
}

data class CapabilityResolutionRule(
    val capability: ModuleCoordinate,
    val preferredProvider: ModuleCoordinate,
    val reason: String
)

val capabilityResolutionRules = listOf(
    CapabilityResolutionRule(
        capability = ModuleCoordinate("org.lz4", "lz4-java"),
        preferredProvider = ModuleCoordinate("at.yawk.lz4", "lz4-java"),
        reason = "Paimon's Spark bundle expects at.yawk.lz4:lz4-java when both LZ4 providers are present."
    )
)
val baseProvidedGroups = setOf(
    "com.google.code.findbugs",
    "org.apache.hadoop",
    "org.apache.spark",
    "org.lz4",
    "org.scala-lang",
    "org.scala-lang.modules",
    "org.slf4j",
    "org.xerial.snappy"
)
val jibImageTaskNames = setOf("jib", "jibDockerBuild", "jibBuildTar")

fun ModuleComponentIdentifier.matches(coordinate: ModuleCoordinate): Boolean {
    return group == coordinate.group && module == coordinate.name
}

configurations.configureEach {
    capabilityResolutionRules.forEach { rule ->
        resolutionStrategy.capabilitiesResolution.withCapability(rule.capability.toString()) {
            val provider = candidates.firstOrNull { candidate ->
                val id = candidate.id
                id is ModuleComponentIdentifier && id.matches(rule.preferredProvider)
            }

            if (provider != null) {
                select(provider)
                because(rule.reason)
            }
        }
    }
}

fun variantBundleName(line: String, variant: String): String {
    return "spark-platform-${line.trim().lowercase()}-variant-${variant.trim().lowercase()}"
}

fun managedBundleName(line: String): String {
    return "spark-platform-${line.trim().lowercase()}-managed"
}

fun sparkVersion(line: String): String {
    val versionName = line.trim().lowercase()
    return libsCatalog.findVersion(versionName)
        .orElseThrow {
            IllegalArgumentException(
                "Version catalog version '$versionName' is missing. Add it to gradle/libs.versions.toml."
            )
        }
        .requiredVersion
}

fun scalaBinaryVersions(bundleNames: Iterable<String>): Set<String> {
    return bundleNames.flatMap { bundleName ->
        libsCatalog.findBundle(bundleName)
            .orElseThrow {
                IllegalArgumentException(
                    "Version catalog bundle '$bundleName' is missing. Add it to gradle/libs.versions.toml."
                )
            }
            .get()
            .mapNotNull { dependency ->
                scalaBinaryVersionPattern.matchEntire(dependency.module.name)?.groupValues?.get(1)
            }
    }.toSet()
}

fun sparkBaseImage(line: String, selectedVariants: Set<String>): String {
    val normalizedLine = line.trim().lowercase()
    val variantBundleNames = selectedVariants.map { variantBundleName(normalizedLine, it) }
    val variantScalaVersions = scalaBinaryVersions(variantBundleNames)
    val scalaVersion = when (variantScalaVersions.size) {
        0 -> scalaBinaryVersions(listOf(managedBundleName(normalizedLine))).single()
        1 -> variantScalaVersions.single()
        else -> error(
            "Selected variants use multiple Scala binary versions: $variantScalaVersions. " +
                "Build separate platform images for incompatible variants."
        )
    }

    return "spark:${sparkVersion(normalizedLine)}-scala$scalaVersion${baseImageSuffix.get()}"
}

dependencies {
    add(platformJars.name, platform(project(":platform-bom")))

    variants.get().forEach { variant ->
        val bundleName = variantBundleName(platformLine.get(), variant)
        val bundle = libsCatalog.findBundle(bundleName)
            .orElseThrow {
                IllegalArgumentException(
                    "Version catalog bundle '$bundleName' is missing. Add it to gradle/libs.versions.toml."
                )
            }
            .get()

        bundle.forEach { dependency ->
            add(platformJars.name, dependency)
        }
    }
}

platformJars.exclude(group = "org.apache.spark")
platformJars.exclude(group = "org.apache.hadoop")
platformJars.exclude(group = "org.lz4")
configurations.named(platformJars.name).configure {
    baseProvidedGroups.forEach { group ->
        exclude(group = group)
    }
}

val syncPlatformJars by tasks.registering(Sync::class) {
    from(platformJars) {
        into("opt/spark/jars")
    }
    into(layout.buildDirectory.dir("jib"))
}

jib {
    from {
        image = sparkBaseImage(platformLine.get(), variants.get())
    }
    to {
        image = imageRepository.get()
        tags = setOf(imageTag.get())
    }
    extraDirectories {
        paths {
            path {
                setFrom(layout.buildDirectory.dir("jib").get().asFile)
                into = "/"
            }
        }
    }
    container {
        entrypoint = listOf("sh", "-c", "echo Spark Platform image: variant jars are in /opt/spark/jars")
    }
}

tasks.matching { it.name in jibImageTaskNames }.configureEach {
    dependsOn(syncPlatformJars)

    // Jib 3.5.x image tasks still read Gradle project/configuration state while executing.
    // Keep that incompatibility local to image builds so callers do not have to remember
    // a special command line flag just to avoid reusing a broken configuration-cache entry.
    notCompatibleWithConfigurationCache(
        "Jib image tasks access Gradle Project/runtimeClasspath state at execution time when configuration cache is reused."
    )
}
