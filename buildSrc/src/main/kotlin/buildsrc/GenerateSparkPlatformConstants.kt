package buildsrc

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateSparkPlatformConstants : DefaultTask() {
    @get:Input
    abstract val bundleAliases: ListProperty<String>

    @get:Input
    abstract val lineIds: ListProperty<String>

    @get:Input
    abstract val profileIds: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val aliases = bundleAliases.get()
        val variants = familyIds(aliases, ".variant.")
        val addons = familyIds(aliases, ".addon.")
        require(lineIds.get().isNotEmpty()) { "Cannot generate SparkLine without configured platform lines." }
        require(variants.isNotEmpty()) { "Cannot generate SparkVariant without variant bundles." }
        require(addons.isNotEmpty()) { "Cannot generate SparkAddon without addon bundles." }
        val constantsByClass = linkedMapOf(
            "SparkLine" to lineIds.get().toSortedSet(),
            "SparkVariant" to variants,
            "SparkAddon" to addons,
            "SparkProfile" to profileIds.get().toSortedSet()
        )
        val packageDirectory = outputDirectory.dir(PACKAGE_NAME.replace('.', '/')).get().asFile
        packageDirectory.mkdirs()

        constantsByClass.forEach { (className, values) ->
            packageDirectory.resolve("$className.java").writeText(javaSource(className, values))
        }
    }

    private fun familyIds(aliases: Iterable<String>, marker: String): Set<String> {
        return aliases.asSequence()
            .filter { it.startsWith(BUNDLE_PREFIX) && it.contains(marker) }
            .map { it.substringAfter(marker).removeSuffix(MANAGED_SUFFIX) }
            .filter(String::isNotEmpty)
            .toSortedSet()
    }

    private fun javaSource(className: String, values: Set<String>): String {
        val constants = values.associateBy(::constantName)
        require(constants.size == values.size) {
            "Cannot generate $className because multiple ids map to the same Java constant: $values"
        }

        return buildString {
            appendLine("package $PACKAGE_NAME;")
            appendLine()
            appendLine("/** Platform ids generated from the Spark Platform configuration. */")
            appendLine("public final class $className {")
            appendLine("    private $className() {")
            appendLine("    }")
            if (constants.isNotEmpty()) {
                appendLine()
            }
            constants.forEach { (constant, value) ->
                appendLine("    public static final String $constant = \"${escapeJava(value)}\";")
            }
            appendLine("}")
        }
    }

    private fun constantName(value: String): String {
        val identifier = value
            .replace(CAMEL_BOUNDARY, "_")
            .replace(NON_IDENTIFIER, "_")
            .trim('_')
            .uppercase()
        require(identifier.isNotEmpty()) { "Cannot generate a Java constant for id '$value'." }
        return if (identifier.first().isDigit()) "_$identifier" else identifier
    }

    private fun escapeJava(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private companion object {
        const val PACKAGE_NAME = "org.openprojectx.spark.platform.plugin"
        const val BUNDLE_PREFIX = "spark.platform."
        const val MANAGED_SUFFIX = ".managed"
        val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
        val NON_IDENTIFIER = Regex("[^A-Za-z0-9]+")
    }
}
