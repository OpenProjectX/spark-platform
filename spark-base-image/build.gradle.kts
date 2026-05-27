import org.gradle.api.tasks.Exec

plugins {
    base
}

val libsCatalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")
val spark3Version = libsCatalog.findVersion("spark3")
    .orElseThrow { IllegalArgumentException("Version catalog version 'spark3' is missing.") }
    .requiredVersion
val spark3GpgKey = "F28C9C925C188C35E345614DEDA00CE834F0FC5C"
val imageRepository = providers.gradleProperty("sparkBaseImage.repository")
    .orElse("ghcr.io/openprojectx/spark")

data class SparkDistributionSpec(
    val name: String,
    val sparkVersion: String,
    val scalaBinaryVersion: String,
    val javaVersion: String,
    val distribution: String,
    val tagFeaturePrefix: List<String> = emptyList()
) {
    val sparkArchiveFile: String = "spark-$sparkVersion-bin-$distribution.tgz"
    val sparkArchiveBaseUrl: String = "https://archive.apache.org/dist/spark/spark-$sparkVersion/$sparkArchiveFile"
}

data class SparkImageType(
    val name: String,
    val tagFeatures: List<String> = emptyList(),
    val installPython3: Boolean = false,
    val installR: Boolean = false
)

data class SparkBaseImageSpec(
    val distribution: SparkDistributionSpec,
    val imageType: SparkImageType
) {
    val name: String = "${distribution.name}${imageType.name}"
    val isRootImage: Boolean = imageType.tagFeatures.isEmpty()
    val tags: List<String> = listOf(
        buildList {
            add(distribution.sparkVersion)
            add("scala${distribution.scalaBinaryVersion}")
            add("java${distribution.javaVersion}")
            addAll(distribution.tagFeaturePrefix)
            addAll(imageType.tagFeatures)
            add("ubuntu")
        }.joinToString("-")
    )
}

val sparkDistributions = listOf(
    SparkDistributionSpec(
        name = "spark3Hadoop3Scala213Java17",
        sparkVersion = spark3Version,
        scalaBinaryVersion = "2.13",
        javaVersion = "17",
        distribution = "hadoop3-scala2.13"
    ),
    SparkDistributionSpec(
        name = "spark3HadoopProvidedScala212Java17",
        sparkVersion = spark3Version,
        scalaBinaryVersion = "2.12",
        javaVersion = "17",
        distribution = "without-hadoop",
        tagFeaturePrefix = listOf("hadoop-provided")
    )
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

val sparkBaseImageSpecs = sparkDistributions.flatMap { distribution ->
    sparkImageTypes.map { imageType -> SparkBaseImageSpec(distribution, imageType) }
}

fun taskNameSuffix(value: String): String {
    return value
        .split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotBlank)
        .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
}

val dockerBuildTaskNames = sparkBaseImageSpecs.map { spec ->
    val taskName = "dockerBuild${taskNameSuffix(spec.name)}"
    tasks.register<Exec>(taskName) {
        group = "docker"
        description = "Builds the Spark ${spec.distribution.sparkVersion} ${spec.distribution.distribution} ${spec.imageType.name} image."
        workingDir = projectDir
        if (!spec.isRootImage) {
            dependsOn("dockerBuild${taskNameSuffix("${spec.distribution.name}Scala")}")
        }

        val command = mutableListOf(
            "docker",
            "build",
        )
        if (spec.isRootImage) {
            command += listOf(
                "--build-arg",
                "SPARK_TGZ_URL=${spec.distribution.sparkArchiveBaseUrl}",
                "--build-arg",
                "SPARK_TGZ_ASC_URL=${spec.distribution.sparkArchiveBaseUrl}.asc",
                "--build-arg",
                "GPG_KEY=$spark3GpgKey",
                "--build-arg",
                "JAVA_IMAGE=eclipse-temurin:${spec.distribution.javaVersion}-jammy"
            )
        } else {
            val baseTag = SparkBaseImageSpec(
                distribution = spec.distribution,
                imageType = sparkImageTypes.first { it.tagFeatures.isEmpty() }
            ).tags.single()
            command += listOf(
                "-f",
                "Dockerfile.variant",
                "--build-arg",
                "BASE_IMAGE=${imageRepository.get()}:$baseTag",
                "--build-arg",
                "INSTALL_PYTHON3=${spec.imageType.installPython3}",
                "--build-arg",
                "INSTALL_R=${spec.imageType.installR}"
            )
        }
        spec.tags.forEach { tag ->
            command += listOf("-t", "${imageRepository.get()}:$tag")
        }
        command += "."

        commandLine(command)
    }
    taskName
}

dockerBuildTaskNames.zipWithNext().forEach { (previousTaskName, nextTaskName) ->
    tasks.named(nextTaskName).configure {
        mustRunAfter(previousTaskName)
    }
}

tasks.register("dockerBuildSparkBaseImages") {
    group = "docker"
    description = "Builds all Spark base images owned by this project."
    dependsOn(dockerBuildTaskNames)
}

val dockerPushTaskNames = sparkBaseImageSpecs.flatMap { spec ->
    spec.tags.map { tag ->
        val taskName = "dockerPush${taskNameSuffix(spec.name)}${taskNameSuffix(tag)}"
        tasks.register<Exec>(taskName) {
            group = "docker"
            description = "Pushes ${imageRepository.get()}:$tag."
            dependsOn("dockerBuild${taskNameSuffix(spec.name)}")
            commandLine("docker", "push", "${imageRepository.get()}:$tag")
        }
        taskName
    }
}

tasks.register("dockerPushSparkBaseImages") {
    group = "docker"
    description = "Builds and pushes all Spark base images owned by this project."
    dependsOn(dockerPushTaskNames)
}
