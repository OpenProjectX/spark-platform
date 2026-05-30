package buildsrc

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import java.io.File

data class BaseImageDefaults(
    val repository: String,
    val suffix: String
)

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

data class PlatformImageConfig(
    val baseImageDefaultsByLine: Map<String, BaseImageDefaults>,
    val defaultImageVariantsByLine: Map<String, List<String>>,
    val isolatedCombinedImageVariantsByLine: Map<String, Set<String>>,
    val baseProvidedTransitiveGroups: Set<String>,
    val capabilityResolutionRules: List<CapabilityResolutionRule>
)

fun loadPlatformImageConfig(
    file: File,
    normalizeVariant: (String) -> String
): PlatformImageConfig {
    require(file.isFile) {
        "Platform image config file '${file.path}' does not exist."
    }

    val config = Toml.parse(file.toPath())
    require(!config.hasErrors()) {
        "Platform image config file '${file.path}' has TOML parse errors:\n" +
            config.errors().joinToString("\n")
    }

    val baseImages = requireTable(config, "baseImages", file)
    val baseImageDefaultsByLine = baseImages.keySet().associate { line ->
        val normalizedLine = line.trim().lowercase()
        val lineConfig = requireTable(baseImages, line, file)
        normalizedLine to BaseImageDefaults(
            repository = requireString(lineConfig, "repository", file),
            suffix = requireString(lineConfig, "suffix", file)
        )
    }

    val defaultVariants = requireTable(config, "defaultVariants", file)
    val defaultImageVariantsByLine = defaultVariants.keySet()
        .associate { line ->
            line.trim().lowercase() to stringList(defaultVariants, line, file)
                .map(normalizeVariant)
                .filter(String::isNotEmpty)
                .distinct()
        }

    val isolatedVariants = config.getTable("isolatedVariants")
    val isolatedCombinedImageVariantsByLine = isolatedVariants?.keySet()
        ?.associate { line ->
            line.trim().lowercase() to stringList(isolatedVariants, line, file)
                .map(normalizeVariant)
                .filter(String::isNotEmpty)
                .toSet()
        }.orEmpty()

    val capabilityResolution = config.getArray("capabilityResolution")
    require(capabilityResolution == null || capabilityResolution.toList().all { it is TomlTable }) {
        "Platform image config property 'capabilityResolution' in '${file.path}' must be an array of tables."
    }
    val capabilityResolutionRules = capabilityResolution?.let { rules ->
        (0 until rules.size()).map { index ->
            val rule = rules.getTable(index)
            CapabilityResolutionRule(
                capability = parseCoordinate(
                    requireString(rule, "capability", file),
                    "capabilityResolution[$index].capability",
                    file
                ),
                preferredProvider = parseCoordinate(
                    requireString(rule, "preferredProvider", file),
                    "capabilityResolution[$index].preferredProvider",
                    file
                ),
                reason = requireString(rule, "reason", file)
            )
        }
    }.orEmpty()

    require(baseImageDefaultsByLine.isNotEmpty()) {
        "Platform image config file '${file.path}' must define at least one [baseImages.<line>] table."
    }
    require(defaultImageVariantsByLine.isNotEmpty()) {
        "Platform image config file '${file.path}' must define at least one [defaultVariants] entry."
    }

    return PlatformImageConfig(
        baseImageDefaultsByLine = baseImageDefaultsByLine,
        defaultImageVariantsByLine = defaultImageVariantsByLine,
        isolatedCombinedImageVariantsByLine = isolatedCombinedImageVariantsByLine,
        baseProvidedTransitiveGroups = config.getTable("resolution")
            ?.let { stringList(it, "baseProvidedTransitiveGroups", file).toSet() }
            .orEmpty(),
        capabilityResolutionRules = capabilityResolutionRules
    )
}

private fun requireTable(table: TomlTable, key: String, file: File): TomlTable {
    return table.getTable(key) ?: error("Platform image config file '${file.path}' is missing required table '$key'.")
}

private fun requireString(table: TomlTable, key: String, file: File): String {
    return table.getString(key)?.trim()?.takeIf(String::isNotEmpty)
        ?: error("Platform image config file '${file.path}' is missing required string '$key'.")
}

private fun stringList(table: TomlTable, key: String, file: File): List<String> {
    val array = table.getArray(key) ?: return emptyList()
    require(array.toList().all { it is String }) {
        "Platform image config property '$key' in '${file.path}' must be an array of strings."
    }
    return array.toStringList()
}

private fun TomlArray.toStringList(): List<String> {
    return (0 until size())
        .map { getString(it).trim() }
        .filter(String::isNotEmpty)
}

private fun parseCoordinate(value: String, propertyName: String, file: File): ModuleCoordinate {
    val parts = value.split(":")
    require(parts.size == 2 && parts.all { it.isNotBlank() }) {
        "Platform image config property '$propertyName' in '${file.path}' must use 'group:name', got '$value'."
    }
    return ModuleCoordinate(parts[0].trim(), parts[1].trim())
}
