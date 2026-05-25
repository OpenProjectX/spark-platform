import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

val platformLine = providers.gradleProperty("sparkPlatform.line").orElse("spark3")
val platformVariants = providers.gradleProperty("sparkPlatform.variants")
    .map { parseVariants(it) }
    .orElse(emptyList())
val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun normalizeLine(line: String): String = line.trim().lowercase().ifEmpty { "spark3" }

fun normalizeVariants(variants: Iterable<String>): List<String> {
    return variants
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()
}

fun parseVariants(value: String): List<String> = normalizeVariants(value.split(","))

fun managedBundleName(line: String): String = "spark-platform-${normalizeLine(line)}-managed"

fun variantBundleName(line: String, variant: String): String {
    return "spark-platform-${normalizeLine(line)}-variant-${variant.trim().lowercase()}"
}

fun variantManagedBundleName(line: String, variant: String): String {
    return "${variantBundleName(line, variant)}-managed"
}

fun MinimalExternalModuleDependency.requiredVersion(): String {
    val version = versionConstraint.requiredVersion
        .ifBlank { versionConstraint.preferredVersion }
        .ifBlank { versionConstraint.strictVersion }

    require(version.isNotBlank()) {
        "Catalog dependency '${module.group}:${module.name}' must declare a version."
    }

    return version
}

fun bundle(name: String): org.gradle.api.artifacts.ExternalModuleDependencyBundle = libsCatalog.findBundle(name)
    .orElseThrow {
        IllegalArgumentException(
            "Version catalog bundle '$name' is missing. Add it to gradle/libs.versions.toml."
        )
    }
    .get()

fun bundleOrNull(name: String): org.gradle.api.artifacts.ExternalModuleDependencyBundle? {
    return libsCatalog.findBundle(name).orElse(null)?.get()
}

fun managedBundles(
    line: String,
    requestedVariants: Iterable<String>
): List<org.gradle.api.artifacts.ExternalModuleDependencyBundle> = buildList {
    val variants = normalizeVariants(requestedVariants)

    if (variants.size == 1) {
        val variantManagedBundle = bundleOrNull(
            variantManagedBundleName(line, variants.single())
        )
        if (variantManagedBundle != null) {
            add(variantManagedBundle)
            return@buildList
        }
    }

    add(bundle(managedBundleName(line)))
    variants.forEach { variant ->
        add(
            bundleOrNull(variantManagedBundleName(line, variant))
                ?: bundle(variantBundleName(line, variant))
        )
    }
}

dependencies {
    constraints {
        managedBundles(platformLine.get(), platformVariants.get()).forEach { bundle ->
            bundle.forEach { dependency ->
                api("${dependency.module.group}:${dependency.module.name}") {
                    version {
                        strictly(dependency.requiredVersion())
                    }
                }
            }
        }
    }
}
