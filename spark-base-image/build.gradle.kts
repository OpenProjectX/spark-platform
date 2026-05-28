import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Exec

plugins {
    java
    base
    alias(libs.plugins.jib)
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val imageRepository = providers.gradleProperty("sparkBaseImage.repository")
    .orElse("ghcr.io/openprojectx/spark")
val requestedLine = providers.gradleProperty("sparkBaseImage.line").orElse("spark3")
val requestedRuntimeBundle = providers.gradleProperty("sparkBaseImage.runtimeBundle")
val requestedBaseImage = providers.gradleProperty("sparkBaseImage.baseImage")
val jibImageTag = providers.gradleProperty("sparkBaseImage.imageTag")

fun catalogVersion(name: String): String {
    return libsCatalog.findVersion(name)
        .orElseThrow { IllegalArgumentException("Version catalog version '$name' is missing.") }
        .requiredVersion
}

fun catalogBundle(name: String): ExternalModuleDependencyBundle {
    return libsCatalog.findBundle(name)
        .orElseThrow { IllegalArgumentException("Version catalog bundle '$name' is missing.") }
        .get()
}

data class SparkLineSpec(
    val line: String,
    val name: String,
    val sparkVersion: String,
    val scalaBinaryVersion: String,
    val javaVersion: String,
    val archiveDistribution: String,
    val runtimeBundle: String
) {
    val layoutTag: String = "$sparkVersion-scala$scalaBinaryVersion-java$javaVersion-layout-ubuntu"
    val runtimeRootTag: String = "$sparkVersion-scala$scalaBinaryVersion-java$javaVersion-ubuntu"

    fun archiveBaseUrl(): String {
        val archiveFile = "spark-$sparkVersion-bin-$archiveDistribution.tgz"
        return "https://archive.apache.org/dist/spark/spark-$sparkVersion/$archiveFile"
    }
}

data class SparkImageType(
    val name: String,
    val tagFeatures: List<String> = emptyList(),
    val installPython3: Boolean = false,
    val installR: Boolean = false
)

data class SparkRuntimeImageSpec(
    val line: SparkLineSpec,
    val imageType: SparkImageType
) {
    val name: String = "${line.name}${imageType.name}"
    val isRootImage: Boolean = imageType.tagFeatures.isEmpty()
    val tag: String = buildList {
        add(line.sparkVersion)
        add("scala${line.scalaBinaryVersion}")
        add("java${line.javaVersion}")
        addAll(imageType.tagFeatures)
        add("ubuntu")
    }.joinToString("-")
}

val sparkLines = listOf(
    SparkLineSpec(
        line = "spark3",
        name = "Spark3Scala212Java17",
        sparkVersion = catalogVersion("spark3"),
        scalaBinaryVersion = "2.12",
        javaVersion = "17",
        archiveDistribution = "hadoop3",
        runtimeBundle = "spark-base-spark3-runtime"
    ),
    SparkLineSpec(
        line = "spark3-scala213",
        name = "Spark3Scala213Java17",
        sparkVersion = catalogVersion("spark3-scala213"),
        scalaBinaryVersion = "2.13",
        javaVersion = "17",
        archiveDistribution = "hadoop3-scala2.13",
        runtimeBundle = "spark-base-spark3-scala213-runtime"
    ),
    SparkLineSpec(
        line = "spark4",
        name = "Spark4Scala213Java17",
        sparkVersion = catalogVersion("spark4"),
        scalaBinaryVersion = "2.13",
        javaVersion = "17",
        archiveDistribution = "hadoop3",
        runtimeBundle = "spark-base-spark4-runtime"
    )
)
val sparkLinesByName = sparkLines.associateBy { it.line }

fun sparkLine(line: String): SparkLineSpec {
    val normalizedLine = line.trim().lowercase()
    return sparkLinesByName[normalizedLine]
        ?: error("Unsupported Spark base image line '$line'. Supported lines: ${sparkLinesByName.keys.sorted()}.")
}

val selectedLine = providers.provider { sparkLine(requestedLine.get()) }
val selectedRuntimeBundle = requestedRuntimeBundle.orElse(
    providers.provider { selectedLine.get().runtimeBundle }
)
val selectedBaseImage = requestedBaseImage.orElse(
    providers.provider { "${imageRepository.get()}:${selectedLine.get().layoutTag}" }
)
val selectedImageTag = jibImageTag.orElse(
    providers.provider { selectedLine.get().runtimeRootTag }
)

val sparkImageTypes = listOf(
    SparkImageType(name = "Scala"),
    SparkImageType(name = "Python3", tagFeatures = listOf("python3"), installPython3 = true),
    SparkImageType(name = "R", tagFeatures = listOf("r"), installR = true),
    SparkImageType(
        name = "Python3R",
        tagFeatures = listOf("python3", "r"),
        installPython3 = true,
        installR = true
    )
)

val runtimeImageSpecs = sparkLines.flatMap { line ->
    sparkImageTypes.map { imageType -> SparkRuntimeImageSpec(line, imageType) }
}

val runtimeJars = configurations.create("runtimeJars") {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Gradle-managed Spark runtime jars for clean Spark base images."
}

dependencies {
    add(runtimeJars.name, platform(project(":platform-bom")))
    catalogBundle(selectedRuntimeBundle.get()).forEach { dependency ->
        add(runtimeJars.name, dependency)
    }
}

val syncRuntimeJars by tasks.registering(Sync::class) {
    from(runtimeJars) {
        into("opt/spark/jars")
    }
    into(layout.buildDirectory.dir("jib"))
}

jib {
    from {
        image = selectedBaseImage.get()
    }
    to {
        image = "${imageRepository.get()}:${selectedImageTag.get()}"
    }
    extraDirectories {
        paths {
            path {
                setFrom(layout.buildDirectory.dir("jib").get().asFile)
                into = "/"
            }
        }
    }
    container {
        user = "spark"
        workingDirectory = "/opt/spark/work-dir"
        entrypoint = listOf("/opt/entrypoint.sh")
    }
}

tasks.matching { it.name in setOf("jib", "jibDockerBuild", "jibBuildTar") }.configureEach {
    dependsOn(syncRuntimeJars)
}

fun taskNameSuffix(value: String): String {
    return value
        .split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotBlank)
        .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
}

val dockerBuildLayoutTaskNames = sparkLines.map { line ->
    val taskName = "dockerBuild${taskNameSuffix(line.name)}Layout"
    tasks.register<Exec>(taskName) {
        group = "docker"
        description = "Builds the stripped Spark ${line.sparkVersion} ${line.scalaBinaryVersion} layout image."
        workingDir = projectDir

        val sparkArchiveBaseUrl = line.archiveBaseUrl()
        commandLine(
            "docker",
            "build",
            "--build-arg",
            "SPARK_TGZ_URL=$sparkArchiveBaseUrl",
            "--build-arg",
            "SPARK_TGZ_ASC_URL=$sparkArchiveBaseUrl.asc",
            "--build-arg",
            "JAVA_IMAGE=eclipse-temurin:${line.javaVersion}-jammy",
            "--build-arg",
            "STRIP_SPARK_JARS=true",
            "-t",
            "${imageRepository.get()}:${line.layoutTag}",
            "."
        )
    }
    taskName
}

val dockerBuildRuntimeTaskNames = runtimeImageSpecs.map { spec ->
    val taskName = "dockerBuild${taskNameSuffix(spec.name)}"
    tasks.register<Exec>(taskName) {
        group = "docker"
        description = "Builds the clean Spark ${spec.line.sparkVersion} ${spec.line.scalaBinaryVersion} ${spec.imageType.name} runtime image."
        workingDir = projectDir

        if (spec.isRootImage) {
            commandLine(
                rootProject.layout.projectDirectory.file("gradlew").asFile.absolutePath,
                ":spark-base-image:jibDockerBuild",
                "--no-configuration-cache",
                "-PsparkBaseImage.repository=${imageRepository.get()}",
                "-PsparkBaseImage.line=${spec.line.line}",
                "-PsparkBaseImage.runtimeBundle=${spec.line.runtimeBundle}",
                "-PsparkBaseImage.baseImage=${imageRepository.get()}:${spec.line.layoutTag}",
                "-PsparkBaseImage.imageTag=${spec.tag}"
            )
        } else {
            dependsOn("dockerBuild${taskNameSuffix("${spec.line.name}Scala")}")
            val baseTag = SparkRuntimeImageSpec(
                line = spec.line,
                imageType = sparkImageTypes.first { it.tagFeatures.isEmpty() }
            ).tag
            commandLine(
                "docker",
                "build",
                "-f",
                "Dockerfile.variant",
                "--build-arg",
                "BASE_IMAGE=${imageRepository.get()}:$baseTag",
                "--build-arg",
                "INSTALL_PYTHON3=${spec.imageType.installPython3}",
                "--build-arg",
                "INSTALL_R=${spec.imageType.installR}",
                "--build-arg",
                "RUNTIME_USER=spark",
                "-t",
                "${imageRepository.get()}:${spec.tag}",
                "."
            )
        }
    }
    taskName
}

(dockerBuildLayoutTaskNames + dockerBuildRuntimeTaskNames).zipWithNext().forEach { (previousTaskName, nextTaskName) ->
    tasks.named(nextTaskName).configure {
        mustRunAfter(previousTaskName)
    }
}

tasks.register("dockerBuildSparkBaseImages") {
    group = "docker"
    description = "Builds all clean Spark base images owned by this project."
    dependsOn(dockerBuildLayoutTaskNames + dockerBuildRuntimeTaskNames)
}

tasks.register("dockerBuildSparkBaseLayoutImages") {
    group = "docker"
    description = "Builds stripped Spark distribution layout images."
    dependsOn(dockerBuildLayoutTaskNames)
}

tasks.register("dockerBuildSparkBaseRuntimeImages") {
    group = "docker"
    description = "Builds Gradle-managed Spark runtime base images from published layout images."
    dependsOn(dockerBuildRuntimeTaskNames)
}

val dockerPushLayoutTaskNames = sparkLines.map { line ->
    val taskName = "dockerPush${taskNameSuffix(line.name)}Layout"
    tasks.register<Exec>(taskName) {
        group = "docker"
        description = "Pushes ${imageRepository.get()}:${line.layoutTag}."
        dependsOn("dockerBuild${taskNameSuffix(line.name)}Layout")
        commandLine("docker", "push", "${imageRepository.get()}:${line.layoutTag}")
    }
    taskName
}

val dockerPushRuntimeTaskNames = runtimeImageSpecs.map { spec ->
    val taskName = "dockerPush${taskNameSuffix(spec.name)}"
    tasks.register<Exec>(taskName) {
        group = "docker"
        description = "Pushes ${imageRepository.get()}:${spec.tag}."
        dependsOn("dockerBuild${taskNameSuffix(spec.name)}")
        commandLine("docker", "push", "${imageRepository.get()}:${spec.tag}")
    }
    taskName
}

tasks.register("dockerPushSparkBaseImages") {
    group = "docker"
    description = "Builds and pushes all clean Spark base images owned by this project."
    dependsOn(dockerPushLayoutTaskNames + dockerPushRuntimeTaskNames)
}

tasks.register("dockerPushSparkBaseLayoutImages") {
    group = "docker"
    description = "Builds and pushes stripped Spark distribution layout images."
    dependsOn(dockerPushLayoutTaskNames)
}

tasks.register("dockerPushSparkBaseRuntimeImages") {
    group = "docker"
    description = "Builds and pushes Gradle-managed Spark runtime base images from published layout images."
    dependsOn(dockerPushRuntimeTaskNames)
}
