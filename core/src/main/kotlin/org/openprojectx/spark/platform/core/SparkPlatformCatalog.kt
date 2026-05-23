package org.openprojectx.spark.platform.core

object SparkPlatformCatalog {
    const val DEFAULT_LINE = "spark3"
    val DEFAULT_VARIANTS = listOf("iceberg", "hudi", "paimon", "openlineage")

    fun normalizeLine(line: String): String = line.trim().lowercase().ifEmpty { DEFAULT_LINE }

    fun normalizeVariants(variants: Iterable<String>): List<String> {
        return variants
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun parseVariants(value: String): List<String> {
        return normalizeVariants(value.split(","))
    }

    fun managedBundle(line: String): String = "spark-platform-${normalizeLine(line)}-managed"

    fun variantBundle(line: String, variant: String): String {
        return "spark-platform-${normalizeLine(line)}-variant-${variant.trim().lowercase()}"
    }

    fun imageTag(line: String, platformVersion: String): String {
        return "${normalizeLine(line)}-${platformVersion.trim()}"
    }
}
