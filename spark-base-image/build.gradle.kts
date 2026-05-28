import org.gradle.api.tasks.Exec

plugins {
    java
    base
    alias(libs.plugins.jib)
}

val libsCatalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")
val spark3Version = libsCatalog.findVersion("spark3")
    .orElseThrow { IllegalArgumentException("Version catalog version 'spark3' is missing.") }
    .requiredVersion
val spark3Scala213Version = libsCatalog.findVersion("spark3-scala213")
    .orElseThrow { IllegalArgumentException("Version catalog version 'spark3-scala213' is missing.") }
    .requiredVersion
val spark3GpgKey = "F28C9C925C188C35E345614DEDA00CE834F0FC5C"
val imageRepository = providers.gradleProperty("sparkBaseImage.repository")
    .orElse("ghcr.io/openprojectx/spark")
val jibImageTag = providers.gradleProperty("sparkBaseImage.imageTag")
    .orElse("$spark3Scala213Version-scala2.13-java17-hadoop-provided-ubuntu")
val hadoopProvidedJars = configurations.create("hadoopProvidedJars") {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Spark 3.5 Scala 2.13 runtime jars without Hadoop client jars."
}

sealed interface SparkImageSource {
    data class ArchiveDistribution(val distribution: String) : SparkImageSource {
        fun archiveBaseUrl(sparkVersion: String): String {
            val archiveFile = "spark-$sparkVersion-bin-$distribution.tgz"
            return "https://archive.apache.org/dist/spark/spark-$sparkVersion/$archiveFile"
        }
    }

    data object GradleManagedJars : SparkImageSource
}

data class SparkDistributionSpec(
    val name: String,
    val sparkVersion: String,
    val scalaBinaryVersion: String,
    val javaVersion: String,
    val source: SparkImageSource,
    val tagFeaturePrefix: List<String> = emptyList()
)

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
        sparkVersion = spark3Scala213Version,
        scalaBinaryVersion = "2.13",
        javaVersion = "17",
        source = SparkImageSource.ArchiveDistribution("hadoop3-scala2.13")
    ),
    SparkDistributionSpec(
        name = "spark3HadoopProvidedScala213Java17",
        sparkVersion = spark3Scala213Version,
        scalaBinaryVersion = "2.13",
        javaVersion = "17",
        source = SparkImageSource.GradleManagedJars,
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
val gradleManagedDistributionNames = sparkDistributions
    .filter { it.source is SparkImageSource.GradleManagedJars }
    .map { it.name }
    .toSet()

dependencies {
    add(hadoopProvidedJars.name, libs.spark3Scala213Core)
    add(hadoopProvidedJars.name, libs.spark3Scala213Sql)
    add(hadoopProvidedJars.name, libs.spark3Scala213Hive)
    add(hadoopProvidedJars.name, libs.spark3Scala213HiveThriftserver)
    add(hadoopProvidedJars.name, libs.spark3Scala213Yarn)
    add(hadoopProvidedJars.name, libs.spark3Scala213Kubernetes)
    add(hadoopProvidedJars.name, libs.spark3Scala213Mllib)
    add(hadoopProvidedJars.name, libs.spark3Scala213Graphx)
    add(hadoopProvidedJars.name, libs.spark3Scala213Repl)
}

hadoopProvidedJars.exclude(group = "org.apache.hadoop")

val prepareHadoopProvidedImageLayout by tasks.registering {
    val workDirMarker = layout.buildDirectory.file("generated/hadoop-provided-image-layout/.keep")
    outputs.file(workDirMarker)
    doLast {
        workDirMarker.get().asFile.apply {
            parentFile.mkdirs()
            writeText("")
        }
    }
}

val syncHadoopProvidedJars by tasks.registering(Sync::class) {
    dependsOn(prepareHadoopProvidedImageLayout)
    from(hadoopProvidedJars) {
        into("opt/spark/jars")
    }
    from(prepareHadoopProvidedImageLayout.map { it.outputs.files.singleFile }) {
        into("opt/spark/work-dir")
    }
    into(layout.buildDirectory.dir("jib"))
}

jib {
    from {
        image = "eclipse-temurin:17-jammy"
    }
    to {
        image = "${imageRepository.get()}:${jibImageTag.get()}"
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
        user = "root"
        workingDirectory = "/opt/spark/work-dir"
        entrypoint = listOf("sh", "-c", "echo Spark Hadoop-provided image: Gradle-managed jars are in /opt/spark/jars")
    }
}

tasks.matching { it.name in setOf("jib", "jibDockerBuild", "jibBuildTar") }.configureEach {
    dependsOn(syncHadoopProvidedJars)
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
        description = "Builds the Spark ${spec.distribution.sparkVersion} ${spec.distribution.name} ${spec.imageType.name} image."
        workingDir = projectDir
        if (!spec.isRootImage) {
            dependsOn("dockerBuild${taskNameSuffix("${spec.distribution.name}Scala")}")
        }

        if (spec.distribution.source is SparkImageSource.GradleManagedJars && spec.isRootImage) {
            commandLine(
                rootProject.layout.projectDirectory.file("gradlew").asFile.absolutePath,
                ":spark-base-image:jibDockerBuild",
                "--no-configuration-cache",
                "-PsparkBaseImage.repository=${imageRepository.get()}",
                "-PsparkBaseImage.imageTag=${spec.tags.single()}"
            )
        } else {
            val command = mutableListOf(
                "docker",
                "build",
            )
            if (spec.isRootImage) {
                val archiveSource = spec.distribution.source as SparkImageSource.ArchiveDistribution
                val sparkArchiveBaseUrl = archiveSource.archiveBaseUrl(spec.distribution.sparkVersion)
                command += listOf(
                    "--build-arg",
                    "SPARK_TGZ_URL=$sparkArchiveBaseUrl",
                    "--build-arg",
                    "SPARK_TGZ_ASC_URL=$sparkArchiveBaseUrl.asc",
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
                    "INSTALL_R=${spec.imageType.installR}",
                    "--build-arg",
                    "RUNTIME_USER=${if (spec.distribution.name in gradleManagedDistributionNames) "root" else "spark"}"
                )
            }
            spec.tags.forEach { tag ->
                command += listOf("-t", "${imageRepository.get()}:$tag")
            }
            command += "."

            commandLine(command)
        }
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
