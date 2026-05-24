import org.gradle.api.tasks.Exec
import org.gradle.jvm.toolchain.JavaToolchainService

allprojects {
    group = "org.openprojectx.spark.platform.examples"
    version = "0.1.1-SNAPSHOT"
}

val integrationJvm = tasks.register("integrationJvm") {
    group = "verification"
    description = "Runs every example as a JVM application."
}

val integrationDocker = tasks.register("integrationDocker") {
    group = "verification"
    description = "Builds every example image and runs it with Docker."
}

tasks.register("integration") {
    group = "verification"
    description = "Runs every example through JVM and Docker integration paths."
    dependsOn(integrationJvm, integrationDocker)
}

fun taskSuffix(value: String): String =
    value
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .joinToString("") { segment ->
            segment.replaceFirstChar { it.uppercaseChar() }
        }

fun normalizedImagePart(value: String): String =
    value.trim().lowercase()

fun localPlatformImageTag(line: String, variants: List<String>, platformVersion: String): String {
    val variantPart = variants.joinToString("-") { normalizedImagePart(it) }.ifBlank { "base" }
    return "${normalizedImagePart(line)}-$variantPart-${platformVersion.trim()}"
}

fun applicationImage(project: Project): String =
    "${project.group}/${project.name}:${project.version}".lowercase()

fun extensionGetter(extension: Any, propertyName: String): Any {
    val getterName = "get${propertyName.replaceFirstChar { it.uppercaseChar() }}"
    val getter = extension.javaClass.methods.single {
        it.name == getterName && it.parameterCount == 0
    }
    return getter.invoke(extension)
}

fun extensionStringProperty(extension: Any, propertyName: String): String {
    val property = extensionGetter(extension, propertyName)
    val value = property.javaClass.methods.single {
        it.name == "get" && it.parameterCount == 0
    }.invoke(property)
    return value.toString()
}

fun extensionBooleanProperty(extension: Any, propertyName: String): Boolean {
    val property = extensionGetter(extension, propertyName)
    val value = property.javaClass.methods.single {
        it.name == "get" && it.parameterCount == 0
    }.invoke(property)
    return value as Boolean
}

fun extensionListProperty(extension: Any, propertyName: String): List<String> {
    val property = extensionGetter(extension, propertyName)
    val value = property.javaClass.methods.single {
        it.name == "get" && it.parameterCount == 0
    }.invoke(property)
    return (value as Iterable<*>).map { it.toString() }
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension>("java") {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }

        val toolchains = extensions.getByType<JavaToolchainService>()
        tasks.withType<JavaExec>().configureEach {
            javaLauncher.set(
                toolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(17))
                }
            )
        }
    }

    plugins.withId("application") {
        val runJvmIntegration = tasks.register("integrationJvm") {
            group = "verification"
            description = "Runs ${project.path} as a JVM application."
            dependsOn(tasks.named("run"))
        }

        rootProject.tasks.named("integrationJvm") {
            dependsOn(runJvmIntegration)
        }
    }

    plugins.withId("com.google.cloud.tools.jib") {
        afterEvaluate {
            val sparkPlatform = extensions.findByName("sparkPlatform") ?: return@afterEvaluate
            if (extensionBooleanProperty(sparkPlatform, "officialBuild")) {
                return@afterEvaluate
            }

            val line = extensionStringProperty(sparkPlatform, "line")
            val variants = extensionListProperty(sparkPlatform, "variants")
            val platformVersion = extensionStringProperty(sparkPlatform, "platformVersion")
            val platformImage = extensionStringProperty(sparkPlatform, "platformImage")
            val imageTag = localPlatformImageTag(line, variants, platformVersion)
            val buildTaskName = "build${taskSuffix(path)}PlatformImage"

            val buildPlatformImage = tasks.register<Exec>(buildTaskName) {
                group = "jib"
                description = "Builds the local Spark platform base image used by ${project.path}."
                workingDir = rootProject.layout.projectDirectory.dir("..").asFile
                commandLine(
                    rootProject.layout.projectDirectory.file("../gradlew").asFile.absolutePath,
                    ":platform-image:jibDockerBuild",
                    "--no-configuration-cache",
                    "-PsparkPlatform.line=${normalizedImagePart(line)}",
                    "-PsparkPlatform.variants=${variants.joinToString(",") { normalizedImagePart(it) }}",
                    "-PsparkPlatform.imageRepository=$platformImage",
                    "-PsparkPlatform.imageTag=$imageTag",
                )
            }

            tasks.matching { it.name == "jibDockerBuild" || it.name == "jibBuildTar" }
                .configureEach {
                    dependsOn(buildPlatformImage)
                }

            val runDockerIntegration = tasks.register<Exec>("integrationDocker") {
                group = "verification"
                description = "Builds the ${project.path} Docker image and runs it."
                dependsOn(tasks.named("jibDockerBuild"))
                commandLine("docker", "run", "--rm", applicationImage(project))
            }

            rootProject.tasks.named("integrationDocker") {
                dependsOn(runDockerIntegration)
            }
        }
    }
}
