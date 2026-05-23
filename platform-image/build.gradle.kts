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
val variants = providers.gradleProperty("sparkPlatform.variants")
    .map { it.split(",").map(String::trim).filter(String::isNotEmpty).map(String::lowercase).toSet() }
    .orElse(setOf("iceberg", "hudi", "paimon", "openlineage"))
val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

configurations.configureEach {
    // Spark brings org.lz4:lz4-java while Paimon's bundled dependencies bring
    // at.yawk.lz4:lz4-java. Both publish the same org.lz4:lz4-java capability,
    // so Gradle rejects the graph until this Paimon-compatible provider is selected.
    resolutionStrategy.capabilitiesResolution.withCapability("org.lz4:lz4-java") {
        val paimonCompatibleProvider = candidates.firstOrNull { candidate ->
            val id = candidate.id
            id is ModuleComponentIdentifier && id.group == "at.yawk.lz4" && id.module == "lz4-java"
        }

        if (paimonCompatibleProvider != null) {
            select(paimonCompatibleProvider)
            because("Paimon's Spark bundle expects at.yawk.lz4:lz4-java when both LZ4 providers are present.")
        }
    }
}

fun variantBundleName(line: String, variant: String): String {
    return "spark-platform-${line.trim().lowercase()}-variant-${variant.trim().lowercase()}"
}

dependencies {
    runtimeOnly(platform(project(":platform-bom")))

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
            runtimeOnly(dependency)
        }
    }
}

val syncPlatformJars by tasks.registering(Sync::class) {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("jib/opt/spark-platform/jars"))
}

jib {
    from {
        image = "eclipse-temurin:17-jre"
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
        entrypoint = listOf("sh", "-c", "echo Spark Platform image: jars are in /opt/spark-platform/jars")
        environment = mapOf("SPARK_EXTRA_CLASSPATH" to "/opt/spark-platform/jars/*")
    }
}

tasks.named("jib").configure {
    dependsOn(syncPlatformJars)
}

tasks.named("jibDockerBuild").configure {
    dependsOn(syncPlatformJars)
}
