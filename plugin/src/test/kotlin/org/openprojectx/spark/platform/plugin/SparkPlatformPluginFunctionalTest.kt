package org.openprojectx.spark.platform.plugin

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SparkPlatformPluginFunctionalTest {
    private val spark3Version = "3.5.8"
    private val spark4Version = "4.0.1"
    private val clouderaSparkVersion = "3.3.2.3.3.7190.9-1"
    private val kafkaClientsVersion = "3.9.1"

    @field:TempDir
    lateinit var projectDir: File

    @Test
    fun `sparkPlatform constraints are implementation constraints for local builds`() {
        writeFixture()

        val result = gradleRunner("printSparkPlatform").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(result.output.contains("implementationExtends=sparkPlatform,sparkPlatformBom"))
        assertTrue(result.output.contains("compileOnlyExtends="))
        assertTrue(result.output.contains("dependencyCount=0"))
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql_2.12:$spark3Version"))
    }

    @Test
    fun `sparkPlatform constraints are compile only constraints for official builds`() {
        writeFixture()

        val result = gradleRunner("printSparkPlatform", "-PsparkPlatform.officialBuild=true").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(result.output.contains("implementationExtends="))
        assertTrue(result.output.contains("compileOnlyExtends=sparkPlatform,sparkPlatformBom"))
    }

    @Test
    fun `sparkPlatform constraints can target api and test scopes`() {
        writeFixture(
            """
            plugins.apply("java-library")

            sparkPlatform {
                line.set("spark4")
                variants.set(listOf("iceberg"))
                managedConfigurations.set(listOf("api", "testImplementation"))
            }

            dependencies {
                add("api", "org.apache.spark:spark-sql_2.13")
                add("testImplementation", "org.apache.iceberg:iceberg-spark-runtime-4.0_2.13")
            }
            """.trimIndent()
        )

        val result = gradleRunner("printSparkPlatform").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(result.output.contains("implementationExtends="))
        assertTrue(result.output.contains("apiExtends=sparkPlatform,sparkPlatformBom"))
        assertTrue(Regex("testImplementationExtends=.*sparkPlatform.*sparkPlatformBom").containsMatchIn(result.output))
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql_2.13:$spark4Version"))
        assertTrue(result.output.contains("constraint=org.apache.iceberg:iceberg-spark-runtime-4.0_2.13:1.10.0"))
    }

    @Test
    fun `users opt into platform dependencies without versions`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark4")
                variants.set(listOf("iceberg"))
            }

            dependencies {
                sparkPlatform("org.apache.spark:spark-sql_2.13")
                sparkPlatform("org.apache.iceberg:iceberg-spark-runtime-4.0_2.13")
            }
            """.trimIndent()
        )

        val result = gradleRunner("printSparkPlatform").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(result.output.contains("dependency=org.apache.spark:spark-sql_2.13:null"))
        assertTrue(result.output.contains("dependency=org.apache.iceberg:iceberg-spark-runtime-4.0_2.13:null"))
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql_2.13:$spark4Version"))
        assertTrue(result.output.contains("constraint=org.apache.iceberg:iceberg-spark-runtime-4.0_2.13:1.10.0"))
    }

    @Test
    fun `spark platform line selects a catalog bundle`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark4")
            }
            """.trimIndent()
        )

        val result = gradleRunner("printSparkPlatform").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(result.output.contains("dependencyCount=0"))
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql_2.13:$spark4Version"))
        assertTrue(result.output.contains("constraint=org.apache.hadoop:hadoop-client-api:3.4.2"))
    }

    @Test
    fun `spark platform variant uses Spark 3 Scala 2_12 constraints`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark3")
                variants.set(listOf("paimon"))
            }
            """.trimIndent()
        )

        val result = gradleRunner("printSparkPlatform").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(result.output.contains("dependencyCount=0"))
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql_2.12:$spark3Version"))
        assertTrue(result.output.contains("constraint=org.apache.paimon:paimon-spark-3.5_2.12:1.4.1"))
    }

    @Test
    fun `spark3 variants can contribute compatible Scala 2_12 constraints together`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark3")
                variants.set(listOf("iceberg", "paimon"))
            }
            """.trimIndent()
        )

        val result = gradleRunner("printSparkPlatform").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(result.output.contains("dependencyCount=0"))
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql_2.12:$spark3Version"))
        assertTrue(result.output.contains("constraint=org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.10.0"))
        assertTrue(result.output.contains("constraint=org.apache.paimon:paimon-spark-3.5_2.12:1.4.1"))
    }

    @Test
    fun `spark sql kafka is managed by the selected Spark line and Scala binary version`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark4")
            }
            """.trimIndent()
        )

        val result = gradleRunner("printSparkPlatform").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql_2.13:$spark4Version"))
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-avro_2.13:$spark4Version"))
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql-kafka-0-10_2.13:$spark4Version"))
        assertTrue(result.output.contains("constraint=org.apache.kafka:kafka-clients:$kafkaClientsVersion"))
    }

    @Test
    fun `addons contribute constraints without being modeled as variants`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark4")
                variants.set(listOf("iceberg"))
                addons.set(listOf("hadoopAws", "icebergAws"))
            }
            """.trimIndent()
        )

        val result = gradleRunner("printSparkPlatform").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(result.output.contains("constraint=org.apache.iceberg:iceberg-spark-runtime-4.0_2.13:1.10.0"))
        assertTrue(result.output.contains("constraint=org.apache.hadoop:hadoop-aws:3.4.2"))
        assertTrue(result.output.contains("constraint=org.apache.iceberg:iceberg-aws-bundle:1.10.0"))
    }

    @Test
    fun `cloudera line manages the Spark TCK dependency surface`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("cloudera")
                variants.set(listOf("iceberg"))
                addons.set(listOf("hadoopAws", "hadoopGcs", "icebergAws"))
                managedConfigurations.set(listOf("testImplementation"))
            }

            dependencies {
                add("testImplementation", "org.apache.spark:spark-sql_2.12")
                add("testImplementation", "org.apache.iceberg:iceberg-spark-runtime-3.3_2.12")
                add("testImplementation", "org.apache.hadoop:hadoop-aws")
                add("testImplementation", "org.apache.iceberg:iceberg-aws-bundle")
                add("testImplementation", "com.google.cloud.bigdataoss:gcs-connector")
            }
            """.trimIndent()
        )

        val result = gradleRunner("printSparkPlatform").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(Regex("testImplementationExtends=.*sparkPlatform.*sparkPlatformBom").containsMatchIn(result.output))
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql_2.12:$clouderaSparkVersion"))
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-hive_2.12:$clouderaSparkVersion"))
        assertTrue(result.output.contains("constraint=org.scala-lang:scala-library:2.12.15"))
        assertTrue(result.output.contains("constraint=org.slf4j:slf4j-api:1.7.36"))
        assertTrue(result.output.contains("constraint=org.apache.iceberg:iceberg-spark-runtime-3.3_2.12:1.8.1"))
        assertTrue(result.output.contains("constraint=org.apache.hadoop:hadoop-aws:3.1.1.7.1.9.14-2"))
        assertTrue(result.output.contains("constraint=org.apache.iceberg:iceberg-aws-bundle:1.8.1"))
        assertTrue(result.output.contains("constraint=com.google.cloud.bigdataoss:gcs-connector:4.0.4"))
    }

    @Test
    fun `spark3 scala 2_13 line uses only scala 2_13 Spark constraints`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark3-scala213")
                variants.set(listOf("iceberg"))
            }
            """.trimIndent()
        )

        val result = gradleRunner("printSparkPlatform").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(result.output.contains("dependencyCount=0"))
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql_2.13:$spark3Version"))
        assertTrue(result.output.contains("constraint=org.apache.iceberg:iceberg-spark-runtime-3.5_2.13:1.10.0"))
        assertTrue(result.output.contains("constraint=org.apache.hadoop:hadoop-client-api:3.4.2"))
        assertTrue(!result.output.contains("constraint=org.apache.spark:spark-sql_2.12:$spark3Version"))
    }

    @Test
    fun `plugin configures jib to use the platform base image`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark4")
                variants.set(listOf("iceberg"))
                addons.set(listOf("hadoopAws"))
                platformImage.set("registry.example.com/spark-platform")
            }
            """.trimIndent()
        )

        val result = gradleRunner("printJib").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printJib")?.outcome)
        assertTrue(result.output.contains("fromImage=docker://registry.example.com/spark-platform:spark4-iceberg-hadoopaws-1.2.3"))
        assertTrue(result.output.contains("jibJvmFlag=--add-opens=java.base/java.nio=ALL-UNNAMED"))
        assertTrue(result.output.contains("jibExtraClasspath=/opt/spark/jars/*"))
        assertTrue(result.output.contains("jibContainerizingMode=packaged"))
        assertTrue(result.output.contains("jibEntrypoint=/opt/entrypoint.sh"))
        assertTrue(result.output.contains("jibAppRoot=/opt/spark/app"))
        assertTrue(result.output.contains("jibEnv=SPARK_EXTRA_CLASSPATH=/opt/spark/app/resources:/opt/spark/app/classes:/opt/spark/app/libs/*:/opt/spark/jars/*"))
        assertTrue(result.output.contains("jibEnv=SPARK_SUBMIT_OPTS=--add-opens=java.base/java.lang=ALL-UNNAMED"))
    }

    @Test
    fun `plugin can use a curated profile tag for application images`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark4")
                profile.set("lakehouse")
                variants.set(listOf("iceberg", "openlineage"))
                addons.set(listOf("hadoopAws"))
                platformImage.set("registry.example.com/spark-platform")
            }
            """.trimIndent()
        )

        val result = gradleRunner("printJib").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printJib")?.outcome)
        assertTrue(result.output.contains("fromImage=docker://registry.example.com/spark-platform:spark4-lakehouse-1.2.3"))
    }

    @Test
    fun `official build configures jib to use the registry platform base image`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark4")
                variants.set(listOf("iceberg"))
                platformImage.set("registry.example.com/spark-platform")
            }
            """.trimIndent()
        )

        val result = gradleRunner("printJib", "-PsparkPlatform.officialBuild=true").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printJib")?.outcome)
        assertTrue(result.output.contains("fromImage=registry.example.com/spark-platform:spark4-iceberg-1.2.3"))
    }

    @Test
    fun `local platform image uses Docker daemon image even in official builds`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark4")
                variants.set(listOf("iceberg"))
                platformImage.set("registry.example.com/spark-platform")
                localPlatformImage.set(true)
            }
            """.trimIndent()
        )

        val result = gradleRunner("printJib", "-PsparkPlatform.officialBuild=true").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printJib")?.outcome)
        assertTrue(result.output.contains("fromImage=docker://registry.example.com/spark-platform:spark4-iceberg-1.2.3"))
    }

    @Test
    fun `plugin configures JavaExec with Spark module options`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark4")
                variants.set(listOf("iceberg"))
            }
            """.trimIndent()
        )

        val result = gradleRunner("printJavaExec").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printJavaExec")?.outcome)
        assertTrue(result.output.contains("javaExecJvmArg=--add-opens=java.base/java.nio=ALL-UNNAMED"))
    }

    private fun writeFixture(extraBuildScript: String = "") {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "fixture"
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                java
                id("org.openprojectx.spark.platform")
            }

            group = "org.example"
            version = "1.2.3"

            $extraBuildScript

            tasks.register("printSparkPlatform") {
                doLast {
                    listOf("implementation", "compileOnly", "api", "testImplementation", "testCompileOnly", "testRuntimeOnly")
                        .mapNotNull { name -> configurations.findByName(name)?.let { name to it } }
                        .forEach { (name, configuration) ->
                            val extends = configuration.extendsFrom.joinToString(",") { it.name }
                            println("${'$'}{name}Extends=${'$'}extends")
                        }
                    val sparkPlatformDependencies = configurations.getByName("sparkPlatform").dependencies
                    println("dependencyCount=${'$'}{sparkPlatformDependencies.size}")
                    sparkPlatformDependencies.forEach {
                        println("dependency=${'$'}{it.group}:${'$'}{it.name}:${'$'}{it.version}")
                    }
                    configurations.getByName("sparkPlatform").dependencyConstraints.forEach {
                        println("constraint=${'$'}{it.group}:${'$'}{it.name}:${'$'}{it.version}")
                    }
                }
            }

            tasks.register("printJib") {
                doLast {
                    val jib = project.extensions.getByName("jib")
                    val from = jib.javaClass.methods.first { it.name == "getFrom" && it.parameterCount == 0 }.invoke(jib)
                    val image = from.javaClass.methods.first { it.name == "getImage" && it.parameterCount == 0 }.invoke(from)
                    println("fromImage=${'$'}image")
                    val containerizingMode = jib.javaClass.methods.first { it.name == "getContainerizingMode" && it.parameterCount == 0 }.invoke(jib)
                    println("jibContainerizingMode=${'$'}containerizingMode")
                    val container = jib.javaClass.methods.first { it.name == "getContainer" && it.parameterCount == 0 }.invoke(jib)
                    val entrypoint = container.javaClass.methods.first { it.name == "getEntrypoint" && it.parameterCount == 0 }.invoke(container) as List<*>
                    entrypoint.forEach { println("jibEntrypoint=${'$'}it") }
                    val appRoot = container.javaClass.methods.first { it.name == "getAppRoot" && it.parameterCount == 0 }.invoke(container)
                    println("jibAppRoot=${'$'}appRoot")
                    val environment = container.javaClass.methods.first { it.name == "getEnvironment" && it.parameterCount == 0 }.invoke(container) as Map<*, *>
                    environment.entries.sortedBy { it.key.toString() }.forEach { (key, value) -> println("jibEnv=${'$'}key=${'$'}value") }
                    val jvmFlags = container.javaClass.methods.first { it.name == "getJvmFlags" && it.parameterCount == 0 }.invoke(container) as List<*>
                    jvmFlags.forEach { println("jibJvmFlag=${'$'}it") }
                    val extraClasspath = container.javaClass.methods.first { it.name == "getExtraClasspath" && it.parameterCount == 0 }.invoke(container) as List<*>
                    extraClasspath.forEach { println("jibExtraClasspath=${'$'}it") }
                }
            }

            tasks.register<JavaExec>("printJavaExec") {
                mainClass.set("org.example.DoesNotRun")
                doFirst {
                    jvmArgs.orEmpty().forEach { println("javaExecJvmArg=${'$'}it") }
                    throw org.gradle.api.tasks.StopExecutionException()
                }
            }
            """.trimIndent()
        )
    }

    private fun gradleRunner(vararg arguments: String): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*arguments, "--stacktrace")
            .withPluginClasspath()
    }
}
