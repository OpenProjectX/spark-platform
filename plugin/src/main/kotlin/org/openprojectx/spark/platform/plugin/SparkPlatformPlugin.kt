package org.openprojectx.spark.platform.plugin

import com.google.cloud.tools.jib.gradle.JibExtension
import com.google.cloud.tools.jib.gradle.JibPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPlugin
import org.openprojectx.spark.platform.core.SparkPlatformCatalog

class SparkPlatformPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "sparkPlatform",
            SparkPlatformExtension::class.java
        )

        extension.officialBuild.convention(project.provider { project.isOfficialBuild() })
        extension.line.convention(project.providers.gradleProperty("sparkPlatform.line").orElse(SparkPlatformCatalog.DEFAULT_LINE))
        extension.platformVersion.convention(project.provider { project.version.toString() })
        extension.platformImage.convention("ghcr.io/openprojectx/spark-platform")
        extension.imageTag.convention(project.provider {
            SparkPlatformCatalog.imageTag(extension.line.get(), extension.platformVersion.get())
        })
        extension.variants.convention(emptyList())

        val managed = project.configurations.create(MANAGED_CONFIGURATION) {
            it.description = "Spark Platform managed dependencies. Local builds use runtime scope; official builds use compile-only scope."
            it.isCanBeConsumed = false
            it.isCanBeResolved = false
        }

        val bom = project.configurations.create(BOM_CONFIGURATION) {
            it.description = "Spark Platform BOM dependency."
            it.isCanBeConsumed = false
            it.isCanBeResolved = false
        }

        project.plugins.withType(JavaPlugin::class.java) {
            wireManagedConfigurations(project, extension, managed, bom)
        }

        project.pluginManager.apply(JibPlugin::class.java)
        project.plugins.withId("com.google.cloud.tools.jib") {
            project.afterEvaluate {
                configureJib(project, extension)
            }
        }
    }

    private fun wireManagedConfigurations(
        project: Project,
        extension: SparkPlatformExtension,
        managed: Configuration,
        bom: Configuration
    ) {
        project.afterEvaluate {
            val targetConfiguration = if (extension.officialBuild.get()) {
                JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
            } else {
                JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME
            }

            project.configurations.named(targetConfiguration).configure { it.extendsFrom(managed, bom) }

            project.dependencies.add(
                BOM_CONFIGURATION,
                project.dependencies.platform(
                    "org.openprojectx.spark.platform:platform-bom:${extension.platformVersion.get()}"
                )
            )

            val catalog = project.versionCatalog()
            catalog.bundle(SparkPlatformCatalog.managedBundle(extension.line.get())).forEach { dependency ->
                project.dependencies.constraints.add(MANAGED_CONFIGURATION, dependency.asConstraintNotation())
            }
        }
    }

    private fun configureJib(project: Project, extension: SparkPlatformExtension) {
        val jib = project.extensions.findByType(JibExtension::class.java) ?: return
        val image = "${extension.platformImage.get()}:${extension.imageTag.get()}"
        val toImage = "${project.group}/${project.name}:${project.version}".lowercase()

        jib.from { it.setImage(image) }
        jib.to { it.setImage(toImage) }
    }

    private fun Project.isOfficialBuild(): Boolean {
        return providers.gradleProperty("sparkPlatform.officialBuild")
            .map(String::toBoolean)
            .orElse(
                providers.environmentVariable("CI").map { true }
                    .orElse(providers.environmentVariable("GITHUB_ACTIONS").map { true })
                    .orElse(providers.environmentVariable("JENKINS_HOME").map { true })
                    .orElse(false)
            )
            .get()
    }

    private fun Project.versionCatalog(): VersionCatalog {
        return extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    }

    private fun VersionCatalog.bundle(name: String): ExternalModuleDependencyBundle {
        return findBundle(name)
            .orElseThrow {
                IllegalArgumentException(
                    "Version catalog bundle '$name' is missing. Add it to gradle/libs.versions.toml."
                )
            }
            .get()
    }

    private fun MinimalExternalModuleDependency.asConstraintNotation(): String {
        val version = versionConstraint.requiredVersion
            .ifBlank { versionConstraint.preferredVersion }
            .ifBlank { versionConstraint.strictVersion }

        require(version.isNotBlank()) {
            "Catalog dependency '${module.group}:${module.name}' must declare a version."
        }

        return "${module.group}:${module.name}:$version"
    }

    companion object {
        const val MANAGED_CONFIGURATION = "sparkPlatform"
        const val BOM_CONFIGURATION = "sparkPlatformBom"
    }
}
