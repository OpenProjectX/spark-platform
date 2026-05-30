import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.Exec

plugins {
    java
    alias(libs.plugins.jib)
}

val platformLine: Provider<String> = providers.gradleProperty("sparkPlatform.line").orElse("spark3")
val imageRepository: Provider<String> = providers.gradleProperty("sparkPlatform.imageRepository")
    .orElse("ghcr.io/openprojectx/spark-platform")
data class BaseImageDefaults(
    val repository: String,
    val suffix: String
)

val baseImageDefaultsByLine = mapOf(
    "spark3" to BaseImageDefaults(
        repository = "ghcr.io/openprojectx/spark",
        suffix = "-java17-python3-r-ubuntu"
    ),
    "spark3-scala213" to BaseImageDefaults(
        repository = "ghcr.io/openprojectx/spark",
        suffix = "-java17-python3-r-ubuntu"
    ),
    "spark4" to BaseImageDefaults(
        repository = "ghcr.io/openprojectx/spark",
        suffix = "-java17-python3-r-ubuntu"
    )
)
val requestedBaseImageRepository = providers.gradleProperty("sparkPlatform.baseImageRepository")
val requestedBaseImageSuffix = providers.gradleProperty("sparkPlatform.baseImageSuffix")
val defaultImageVariantsByLine = mapOf(
    "spark3" to listOf("iceberg", "hudi", "paimon", "openlineage", "hadoopAws"),
    "spark3-scala213" to listOf("iceberg", "openlineage", "hadoopAws"),
    "spark4" to listOf("iceberg", "hudi", "paimon", "openlineage", "hadoopAws")
)
val isolatedCombinedImageVariantsByLine = emptyMap<String, Set<String>>()
val variants = providers.gradleProperty("sparkPlatform.variants")
    .map { it.split(",").map(::normalizeVariant).filter(String::isNotEmpty).distinct() }
    .orElse(
        providers.provider {
            defaultImageVariantsByLine[platformLine.get().trim().lowercase()]
                ?: listOf("iceberg", "hudi", "paimon", "openlineage")
        }
    )
val buildIndividualImages = providers.gradleProperty("sparkPlatform.buildIndividualImages")
    .map(String::toBoolean)
    .orElse(true)
val buildCombinedImages = providers.gradleProperty("sparkPlatform.buildCombinedImages")
    .map(String::toBoolean)
    .orElse(true)
val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val variantCamelBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])")
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
val baseProvidedTransitiveGroups = setOf(
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
    return "spark-platform-${line.trim().lowercase()}-variant-${normalizeVariant(variant)}"
}

fun managedBundleName(line: String): String {
    return "spark-platform-${line.trim().lowercase()}-managed"
}

fun normalizeVariant(variant: String): String {
    // Variant ids are lower camel case because image tags use '-' to join
    // variants. Accept dash/underscore/camel input and canonicalize it.
    val words = variant.trim()
        .split('-', '_')
        .flatMap { word -> word.split(variantCamelBoundary) }
        .map { it.lowercase() }
        .filter { it.isNotEmpty() }

    return if (words.size <= 1) {
        words.firstOrNull().orEmpty()
    } else {
        words.first() + words.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }
}

fun bundle(name: String): ExternalModuleDependencyBundle {
    return libsCatalog.findBundle(name)
        .orElseThrow {
            IllegalArgumentException(
                "Version catalog bundle '$name' is missing. Add it to gradle/libs.versions.toml."
            )
        }
        .get()
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

fun baseImageRepositoryFor(line: String): String {
    return requestedBaseImageRepository.orElse(
        providers.provider {
            baseImageDefaultsByLine[line.trim().lowercase()]?.repository ?: "spark"
        }
    ).get()
}

fun baseImageSuffixFor(line: String): String {
    return requestedBaseImageSuffix.orElse(
        providers.provider {
            baseImageDefaultsByLine[line.trim().lowercase()]?.suffix ?: "-java17-python3-r-ubuntu"
        }
    ).get()
}

fun scalaBinaryVersions(bundleNames: Iterable<String>): Set<String> {
    return bundleNames.flatMap { bundleName ->
        bundle(bundleName)
            .mapNotNull { dependency ->
                scalaBinaryVersionPattern.matchEntire(dependency.module.name)?.groupValues?.get(1)
            }
    }.toSet()
}

fun scalaBinaryVersion(line: String, variant: String): String {
    val versions = scalaBinaryVersions(listOf(variantBundleName(line, variant)))
    return when (versions.size) {
        1 -> versions.single()
        0 -> scalaBinaryVersions(listOf(managedBundleName(line))).single()
        else -> error("Variant '$variant' for line '$line' must resolve to exactly one Scala binary version, found $versions.")
    }
}

fun platformImageTag(line: String, selectedVariants: Iterable<String>): String {
    val normalizedLine = line.trim().lowercase()
    val variantPart = selectedVariants.joinToString("-") { it.trim().lowercase() }
        .ifBlank { "base" }
    return "$normalizedLine-$variantPart-$version"
}

fun sparkBaseImage(line: String, selectedVariants: Iterable<String>, failOnMultipleScalaVersions: Boolean = true): String {
    val normalizedLine = line.trim().lowercase()
    val variantBundleNames = selectedVariants.map { variantBundleName(normalizedLine, it) }
    val lineScalaVersion = scalaBinaryVersions(listOf(managedBundleName(normalizedLine))).single()
    val variantScalaVersions = scalaBinaryVersions(variantBundleNames)
    val scalaVersion = when (variantScalaVersions.size) {
        0 -> lineScalaVersion
        1 -> variantScalaVersions.single()
        else -> if (failOnMultipleScalaVersions) {
            error(
                "Selected variants use multiple Scala binary versions: $variantScalaVersions. " +
                    "Build separate platform images for incompatible variants."
            )
        } else {
            variantScalaVersions.first()
        }
    }

    return "${baseImageRepositoryFor(normalizedLine)}:${sparkVersion(normalizedLine)}-scala$scalaVersion${baseImageSuffixFor(normalizedLine)}"
}

tasks.register("printSparkBaseImage") {
    group = "help"
    description = "Prints the Spark base image resolved for the selected Spark Platform line and variants."
    doLast {
        println(sparkBaseImage(platformLine.get(), variants.get()))
    }
}

data class PlatformImageBuildSpec(
    val name: String,
    val variants: List<String>
)

fun buildSpecs(
    line: String,
    selectedVariants: List<String>,
    includeIndividualImages: Boolean,
    includeCombinedImages: Boolean
): List<PlatformImageBuildSpec> {
    require(includeIndividualImages || includeCombinedImages) {
        "At least one platform image build mode must be enabled. " +
            "Set sparkPlatform.buildIndividualImages=true or sparkPlatform.buildCombinedImages=true."
    }

    val isolatedVariants = isolatedCombinedImageVariantsByLine[line.trim().lowercase()].orEmpty()
    val individualSpecs = if (includeIndividualImages) {
        selectedVariants.map { variant ->
            PlatformImageBuildSpec(variant, listOf(variant))
        }
    } else {
        emptyList()
    }
    val combinedSpecs = if (includeCombinedImages) {
        selectedVariants
            .filterNot { variant -> variant in isolatedVariants }
            .groupBy { variant -> scalaBinaryVersion(line, variant) }
            .values
            .filter { compatibleVariants -> !includeIndividualImages || compatibleVariants.size > 1 }
            .map { compatibleVariants ->
                PlatformImageBuildSpec(compatibleVariants.joinToString("-"), compatibleVariants)
            }
    } else {
        emptyList()
    }

    return individualSpecs + combinedSpecs
}

fun taskNameSuffix(value: String): String {
    return value
        .split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotBlank)
        .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
}

fun addVariantJar(dependency: Any) {
    val addedDependency = dependencies.add(platformJars.name, dependency)
    if (addedDependency is ModuleDependency) {
        // These excludes apply only to transitive dependencies. The selected
        // variant artifact itself must stay in the image, even when it belongs
        // to a base-provided group such as org.apache.hadoop.
        baseProvidedTransitiveGroups.forEach { group ->
            addedDependency.exclude(mapOf("group" to group))
        }
    }
}

dependencies {
    add(platformJars.name, platform(project(":platform-bom")))

    variants.get().forEach { variant ->
        val bundleName = variantBundleName(platformLine.get(), variant)
        bundle(bundleName).forEach { dependency ->
            addVariantJar(dependency)
        }
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
        image = sparkBaseImage(platformLine.get(), variants.get(), failOnMultipleScalaVersions = false)
    }
    to {
        image = "${imageRepository.get()}:${providers.gradleProperty("sparkPlatform.imageTag").orElse(
            providers.provider { platformImageTag(platformLine.get(), variants.get()) }
        ).get()}"
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

val platformImageBuildSpecs = buildSpecs(
    platformLine.get(),
    variants.get(),
    buildIndividualImages.get(),
    buildCombinedImages.get()
)

fun registerPlatformImageTasks(
    jibTaskName: String,
    aggregateTaskName: String,
    action: String
): List<String> {
    val taskNames = platformImageBuildSpecs.map { spec ->
        val taskName = "$jibTaskName${taskNameSuffix(spec.name)}PlatformImage"
        tasks.register<Exec>(taskName) {
            group = "jib"
            description = "$action the ${spec.name} Spark Platform image."
            workingDir = rootDir
            commandLine(
                rootProject.layout.projectDirectory.file("gradlew").asFile.absolutePath,
                ":platform-image:$jibTaskName",
                "--no-configuration-cache",
                "-PsparkPlatform.line=${platformLine.get()}",
                "-PsparkPlatform.variants=${spec.variants.joinToString(",")}",
                "-PsparkPlatform.imageRepository=${imageRepository.get()}",
                "-PsparkPlatform.baseImageRepository=${baseImageRepositoryFor(platformLine.get())}",
                "-PsparkPlatform.imageTag=${platformImageTag(platformLine.get(), spec.variants)}",
                "-PsparkPlatform.baseImageSuffix=${baseImageSuffixFor(platformLine.get())}",
                "-PsparkPlatform.buildIndividualImages=${buildIndividualImages.get()}",
                "-PsparkPlatform.buildCombinedImages=${buildCombinedImages.get()}"
            )
        }
        taskName
    }

    taskNames.zipWithNext().forEach { (previousTaskName, nextTaskName) ->
        tasks.named(nextTaskName).configure {
            mustRunAfter(previousTaskName)
        }
    }

    tasks.register(aggregateTaskName) {
        group = "jib"
        description = "$action selected Spark Platform images."
        dependsOn(taskNames)
    }

    return taskNames
}

registerPlatformImageTasks(
    "jibDockerBuild",
    "jibDockerBuildPlatformImages",
    "Builds"
)

registerPlatformImageTasks(
    "jib",
    "jibPublishPlatformImages",
    "Publishes"
)

val jibPublishAllPlatformImageTaskNames = defaultImageVariantsByLine.keys.map { line ->
    val normalizedLine = line.trim().lowercase()
    val taskName = "jibPublish${taskNameSuffix(normalizedLine)}PlatformImages"
    tasks.register<Exec>(taskName) {
        group = "jib"
        description = "Publishes Spark Platform images for $normalizedLine."
        workingDir = rootDir
        commandLine(
            rootProject.layout.projectDirectory.file("gradlew").asFile.absolutePath,
            ":platform-image:jibPublishPlatformImages",
            "--no-configuration-cache",
            "-PsparkPlatform.line=$normalizedLine",
            "-PsparkPlatform.variants=${defaultImageVariantsByLine.getValue(normalizedLine).joinToString(",")}",
            "-PsparkPlatform.imageRepository=${imageRepository.get()}",
            "-PsparkPlatform.baseImageRepository=${baseImageRepositoryFor(normalizedLine)}",
            "-PsparkPlatform.baseImageSuffix=${baseImageSuffixFor(normalizedLine)}",
            "-PsparkPlatform.buildIndividualImages=false",
            "-PsparkPlatform.buildCombinedImages=true"
        )
    }
    taskName
}

jibPublishAllPlatformImageTaskNames.zipWithNext().forEach { (previousTaskName, nextTaskName) ->
    tasks.named(nextTaskName).configure {
        mustRunAfter(previousTaskName)
    }
}

tasks.register("jibPublishAllPlatformImages") {
    group = "jib"
    description = "Publishes Spark Platform images for every supported Spark line."
    dependsOn(jibPublishAllPlatformImageTaskNames)
}

tasks.matching { it.name in jibImageTaskNames }.configureEach {
    dependsOn(syncPlatformJars)
    doFirst {
        sparkBaseImage(platformLine.get(), variants.get())
    }

    // Jib 3.5.x image tasks still read Gradle project/configuration state while executing.
    // Keep that incompatibility local to image builds so callers do not have to remember
    // a special command line flag just to avoid reusing a broken configuration-cache entry.
    notCompatibleWithConfigurationCache(
        "Jib image tasks access Gradle Project/runtimeClasspath state at execution time when configuration cache is reused."
    )
}
