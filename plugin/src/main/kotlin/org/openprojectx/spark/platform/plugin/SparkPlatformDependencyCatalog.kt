package org.openprojectx.spark.platform.plugin

import org.openprojectx.spark.platform.core.SparkPlatformCatalog
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

data class SparkPlatformDependency(
    val group: String,
    val name: String,
    val version: String
)

class SparkPlatformDependencyCatalog(
    private val bundles: Map<String, List<SparkPlatformDependency>>
) {
    fun managedDependencies(
        line: String,
        requestedVariants: Iterable<String>,
        requestedAddons: Iterable<String>
    ): List<SparkPlatformDependency> {
        val variants = SparkPlatformCatalog.normalizeVariants(requestedVariants)
        val addons = SparkPlatformCatalog.normalizeVariants(requestedAddons)

        val selectedBundles = if (variants.size == 1 && addons.isEmpty()) {
            val variantManagedBundle = bundleOrNull(
                SparkPlatformCatalog.variantManagedBundle(line, variants.single())
            )
            if (variantManagedBundle != null) {
                listOf(variantManagedBundle)
            } else {
                defaultBundles(line, variants, addons)
            }
        } else {
            defaultBundles(line, variants, addons)
        }

        return selectedBundles.flatten().distinct()
    }

    private fun defaultBundles(
        line: String,
        variants: Iterable<String>,
        addons: Iterable<String>
    ): List<List<SparkPlatformDependency>> {
        return buildList {
            add(bundle(SparkPlatformCatalog.managedBundle(line)))
            variants.forEach { variant ->
                add(
                    bundleOrNull(SparkPlatformCatalog.variantManagedBundle(line, variant))
                        ?: bundle(SparkPlatformCatalog.variantBundle(line, variant))
                )
            }
            addons.forEach { addon ->
                add(bundle(SparkPlatformCatalog.addonBundle(line, addon)))
            }
        }
    }

    private fun bundle(name: String): List<SparkPlatformDependency> {
        return bundleOrNull(name)
            ?: throw IllegalArgumentException(
                "Spark Platform catalog bundle '$name' is missing from the packaged platform catalog."
            )
    }

    private fun bundleOrNull(name: String): List<SparkPlatformDependency>? = bundles[name]

    companion object {
        private const val RESOURCE = "org/openprojectx/spark/platform/plugin/spark-platform.versions.toml"

        fun loadDefault(): SparkPlatformDependencyCatalog {
            val stream = SparkPlatformDependencyCatalog::class.java.classLoader.getResourceAsStream(RESOURCE)
                ?: error("Packaged Spark Platform catalog resource '$RESOURCE' was not found.")

            val catalog = stream.use { Toml.parse(it) }
            require(!catalog.hasErrors()) {
                "Packaged Spark Platform catalog has TOML parse errors:\n" +
                    catalog.errors().joinToString("\n")
            }

            val versions = catalog.getTable("versions")
                ?: error("Packaged Spark Platform catalog is missing [versions].")
            val libraries = catalog.getTable("libraries")
                ?: error("Packaged Spark Platform catalog is missing [libraries].")
            val bundleAliases = catalog.getTable("bundles")
                ?: error("Packaged Spark Platform catalog is missing [bundles].")

            val dependenciesByAlias = libraries.keySet().associateWith { alias ->
                parseDependency(alias, requireTable(libraries, alias), versions)
            }

            val bundles = bundleAliases.keySet().associateWith { bundleName ->
                stringList(bundleAliases, bundleName).map { alias ->
                    dependenciesByAlias[alias]
                        ?: error("Packaged Spark Platform catalog bundle '$bundleName' references unknown library '$alias'.")
                }
            }

            return SparkPlatformDependencyCatalog(bundles)
        }

        private fun parseDependency(
            alias: String,
            table: TomlTable,
            versions: TomlTable
        ): SparkPlatformDependency {
            val module = requireString(table, "module")
            val parts = module.split(":")
            require(parts.size == 2 && parts.all { it.isNotBlank() }) {
                "Packaged Spark Platform catalog library '$alias' must use module = 'group:name', got '$module'."
            }

            val versionRefTable = table.getTable("version")
            val version = versionRefTable?.getString("ref")?.let { versionRef ->
                    versions.getString(versionRef)
                        ?: error("Packaged Spark Platform catalog library '$alias' references unknown version '$versionRef'.")
                }
                ?: table.getString("version")
                ?: error("Packaged Spark Platform catalog library '$alias' must declare version or version.ref.")

            return SparkPlatformDependency(
                group = parts[0].trim(),
                name = parts[1].trim(),
                version = version.trim()
            )
        }

        private fun requireTable(table: TomlTable, key: String): TomlTable {
            return table.getTable(key)
                ?: error("Packaged Spark Platform catalog is missing required table '$key'.")
        }

        private fun requireString(table: TomlTable, key: String): String {
            return table.getString(key)?.trim()?.takeIf(String::isNotEmpty)
                ?: error("Packaged Spark Platform catalog is missing required string '$key'.")
        }

        private fun stringList(table: TomlTable, key: String): List<String> {
            val array = table.getArray(key)
                ?: error("Packaged Spark Platform catalog is missing required string array '$key'.")
            require(array.toList().all { it is String }) {
                "Packaged Spark Platform catalog property '$key' must be an array of strings."
            }
            return array.toStringList()
        }

        private fun TomlArray.toStringList(): List<String> {
            return (0 until size())
                .map { getString(it).trim() }
                .filter(String::isNotEmpty)
        }
    }
}
