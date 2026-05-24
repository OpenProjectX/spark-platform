package org.openprojectx.spark.platform.core

object SparkPlatformJvmOptions {
    val SPARK_JAVA_MODULE_OPTIONS = listOf(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
        "--add-opens=java.base/sun.security.action=ALL-UNNAMED",
        "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED"
    )

    private val builtInRules = listOf(
        JvmOptionRule(
            options = SPARK_JAVA_MODULE_OPTIONS
        )
    )

    fun defaults(line: String, variants: Iterable<String>): List<String> {
        val normalizedLine = SparkPlatformCatalog.normalizeLine(line)
        val normalizedVariants = SparkPlatformCatalog.normalizeVariants(variants).toSet()
        return builtInRules
            .filter { it.matches(normalizedLine, normalizedVariants) }
            .flatMap { it.options }
            .distinct()
    }

    data class JvmOptionRule(
        val lines: Set<String> = emptySet(),
        val variants: Set<String> = emptySet(),
        val options: List<String>
    ) {
        fun matches(line: String, selectedVariants: Set<String>): Boolean {
            return (lines.isEmpty() || line in lines) &&
                (variants.isEmpty() || variants.any { it in selectedVariants })
        }
    }
}
