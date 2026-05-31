package org.openprojectx.spark.platform.plugin

import com.google.cloud.tools.jib.gradle.JibExtension
import com.google.cloud.tools.jib.gradle.JibPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.openprojectx.spark.platform.core.ModuleCoordinate
import org.openprojectx.spark.platform.core.SparkPlatformCapabilityResolutions
import org.openprojectx.spark.platform.core.SparkPlatformCatalog
import org.openprojectx.spark.platform.core.SparkPlatformJvmOptions

class SparkPlatformPlugin : Plugin<Project> {
    private val dependencyCatalog = SparkPlatformDependencyCatalog.loadDefault()

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "sparkPlatform",
            SparkPlatformExtension::class.java
        )

        extension.officialBuild.convention(project.provider { project.isOfficialBuild() })
        extension.localPlatformImage.convention(
            project.providers.gradleProperty("sparkPlatform.localPlatformImage")
                .map(String::toBoolean)
                .orElse(project.provider { project.isJibDockerBuild() })
        )
        extension.line.convention(project.providers.gradleProperty("sparkPlatform.line").orElse(SparkPlatformCatalog.DEFAULT_LINE))
        extension.profile.convention("")
        extension.platformVersion.convention(project.provider { project.version.toString() })
        extension.platformImage.convention("ghcr.io/openprojectx/spark-platform")
        extension.imageTag.convention(project.provider {
            val profile = extension.profile.get().trim()
            if (profile.isNotEmpty()) {
                SparkPlatformCatalog.profileImageTag(extension.line.get(), profile, extension.platformVersion.get())
            } else {
                SparkPlatformCatalog.imageTag(
                    extension.line.get(),
                    extension.variants.get(),
                    extension.addons.get(),
                    extension.platformVersion.get()
                )
            }
        })
        extension.variants.convention(emptyList())
        extension.addons.convention(emptyList())
        extension.managedConfigurations.convention(
            project.providers.gradleProperty("sparkPlatform.managedConfigurations")
                .map { value -> value.split(",").map(String::trim).filter(String::isNotEmpty).distinct() }
                .orElse(project.provider { defaultManagedConfigurations(extension) })
        )

        configureKnownCapabilityResolutions(project)

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

        project.configurations.create(JAVA_EXEC_RUNTIME_CONFIGURATION) {
            it.description = "Resolvable Spark Platform runtime dependencies for JavaExec smoke runs in official builds."
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
            it.extendsFrom(managed, bom)
        }

        project.plugins.withType(JavaPlugin::class.java) {
            wireManagedConfigurations(project, extension, managed, bom)
            configureJavaExec(project, extension)
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
            extension.managedConfigurations.get().forEach { targetConfiguration ->
                project.configurations.named(targetConfiguration).configure { it.extendsFrom(managed, bom) }
            }

            val selectedDependencies = dependencyCatalog.managedDependencies(
                extension.line.get(),
                extension.variants.get(),
                extension.addons.get()
            )
            selectedDependencies.forEach { dependency ->
                project.dependencies.constraints.add(
                    MANAGED_CONFIGURATION,
                    "${dependency.group}:${dependency.name}"
                ) {
                    it.version { version ->
                        version.strictly(dependency.version)
                    }
                }
            }
        }
    }

    private fun defaultManagedConfigurations(extension: SparkPlatformExtension): List<String> {
        return if (extension.officialBuild.get()) {
            listOf(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
        } else {
            listOf(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
        }
    }

    private fun configureKnownCapabilityResolutions(project: Project) {
        project.configurations.configureEach { configuration ->
            SparkPlatformCapabilityResolutions.BUILT_IN.forEach { rule ->
                configuration.resolutionStrategy.capabilitiesResolution.withCapability(rule.capability.toString()) { details ->
                    val provider = details.candidates.firstOrNull { candidate ->
                        val id = candidate.id
                        id is ModuleComponentIdentifier && id.matches(rule.preferredProvider)
                    }

                    if (provider != null) {
                        details.select(provider)
                        details.because(rule.reason)
                    }
                }
            }
        }
    }

    private fun ModuleComponentIdentifier.matches(coordinate: ModuleCoordinate): Boolean {
        return group == coordinate.group && module == coordinate.name
    }

    private fun configureJib(project: Project, extension: SparkPlatformExtension) {
        val jib = project.extensions.findByType(JibExtension::class.java) ?: return
        val image = platformBaseImageReference(project, extension)
        val toImage = "${project.group}/${project.name}:${project.version}".lowercase()
        val jvmFlags = managedJvmOptions(extension)
        val operatorAppDir = project.layout.buildDirectory.dir("spark-platform/operator-app")
        val operatorAppJar = project.tasks.register("sparkPlatformOperatorAppJar", Copy::class.java) { task ->
            task.description = "Stages the application jar at the Spark Operator local:// path."
            task.dependsOn(project.tasks.named(JavaPlugin.JAR_TASK_NAME))
            task.from(project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java).flatMap { it.archiveFile })
            task.rename { SPARK_OPERATOR_APP_JAR_NAME }
            task.into(operatorAppDir)
        }

        jib.from { it.setImage(image) }
        jib.to { it.setImage(toImage) }
        jib.setContainerizingMode("packaged")
        jib.extraDirectories {
            it.paths { paths ->
                paths.path { path ->
                    path.setFrom(operatorAppDir)
                    path.setInto(SPARK_OPERATOR_APP_DIR)
                }
            }
        }
        jib.container {
            it.setAppRoot(SPARK_OPERATOR_APP_DIR)
            it.setEntrypoint(listOf(SPARK_ENTRYPOINT))
            it.setJvmFlags((jvmFlags + it.jvmFlags).distinct())
            it.setExtraClasspath((listOf(PLATFORM_JARS_CLASSPATH) + it.extraClasspath).distinct())
            it.setEnvironment(operatorEnvironment(jvmFlags) + it.environment)
        }

        project.tasks.matching { it.name in JIB_IMAGE_TASKS }.configureEach {
            it.dependsOn(operatorAppJar)
        }
    }

    private fun configureJavaExec(project: Project, extension: SparkPlatformExtension) {
        project.afterEvaluate {
            project.tasks.withType(JavaExec::class.java).configureEach {
                it.jvmArgs(managedJvmOptions(extension))
                if (extension.officialBuild.get()) {
                    it.classpath(project.configurations.named(JAVA_EXEC_RUNTIME_CONFIGURATION))
                }
            }
        }
    }

    private fun platformBaseImageReference(project: Project, extension: SparkPlatformExtension): String {
        val image = "${extension.platformImage.get()}:${extension.imageTag.get()}"
        return if ((extension.officialBuild.get() && !extension.localPlatformImage.get()) || image.startsWith("docker://")) {
            image
        } else {
            "docker://$image"
        }
    }

    private fun Project.isJibDockerBuild(): Boolean {
        return gradle.startParameter.taskNames.any { taskName ->
            taskName == "jibDockerBuild" || taskName.endsWith(":jibDockerBuild")
        }
    }

    private fun managedJvmOptions(extension: SparkPlatformExtension): List<String> {
        return SparkPlatformJvmOptions.defaults(extension.line.get(), extension.variants.get())
    }

    private fun operatorEnvironment(jvmFlags: List<String>): Map<String, String> {
        return buildMap {
            put("SPARK_EXTRA_CLASSPATH", SPARK_OPERATOR_EXTRA_CLASSPATH)
            if (jvmFlags.isNotEmpty()) {
                put("SPARK_SUBMIT_OPTS", jvmFlags.joinToString(" "))
            }
            jvmFlags.forEachIndexed { index, option ->
                put("SPARK_JAVA_OPT_$index", option)
            }
        }
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

    companion object {
        const val MANAGED_CONFIGURATION = "sparkPlatform"
        const val BOM_CONFIGURATION = "sparkPlatformBom"
        const val JAVA_EXEC_RUNTIME_CONFIGURATION = "sparkPlatformJavaExecRuntime"
        const val PLATFORM_JARS_CLASSPATH = "/opt/spark/jars/*"
        const val SPARK_ENTRYPOINT = "/opt/entrypoint.sh"
        const val SPARK_OPERATOR_APP_DIR = "/opt/spark/app"
        const val SPARK_OPERATOR_APP_JAR_NAME = "app.jar"
        const val SPARK_OPERATOR_EXTRA_CLASSPATH =
            "$SPARK_OPERATOR_APP_DIR/resources:$SPARK_OPERATOR_APP_DIR/classes:$SPARK_OPERATOR_APP_DIR/libs/*:$PLATFORM_JARS_CLASSPATH"
        val JIB_IMAGE_TASKS = setOf("jib", "jibDockerBuild", "jibBuildTar")
    }
}
