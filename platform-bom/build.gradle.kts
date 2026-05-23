import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

val platformLine = providers.gradleProperty("sparkPlatform.line").orElse("spark3")
val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun managedBundleName(line: String): String = "spark-platform-${line.trim().lowercase()}-managed"

fun MinimalExternalModuleDependency.asConstraintNotation(): String {
    val version = versionConstraint.requiredVersion
        .ifBlank { versionConstraint.preferredVersion }
        .ifBlank { versionConstraint.strictVersion }

    require(version.isNotBlank()) {
        "Catalog dependency '${module.group}:${module.name}' must declare a version."
    }

    return "${module.group}:${module.name}:$version"
}

dependencies {
    constraints {
        val bundleName = managedBundleName(platformLine.get())
        val bundle = libsCatalog.findBundle(bundleName)
            .orElseThrow {
                IllegalArgumentException(
                    "Version catalog bundle '$bundleName' is missing. Add it to gradle/libs.versions.toml."
                )
            }
            .get()

        bundle.forEach { dependency ->
            api(dependency.asConstraintNotation())
        }
    }
}
