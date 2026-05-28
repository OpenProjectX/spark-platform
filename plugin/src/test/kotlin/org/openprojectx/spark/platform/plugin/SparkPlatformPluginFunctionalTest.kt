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
        assertTrue(!result.output.contains("constraint=org.apache.spark:spark-sql_2.12:$spark3Version"))
        assertTrue(!result.output.contains("constraint=org.apache.hadoop:hadoop-client-api"))
    }

    @Test
    fun `plugin configures jib to use the platform base image`() {
        writeFixture(
            """
            sparkPlatform {
                line.set("spark4")
                variants.set(listOf("iceberg"))
                platformImage.set("registry.example.com/spark-platform")
            }
            """.trimIndent()
        )

        val result = gradleRunner("printJib").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printJib")?.outcome)
        assertTrue(result.output.contains("fromImage=docker://registry.example.com/spark-platform:spark4-iceberg-1.2.3"))
        assertTrue(result.output.contains("jibJvmFlag=--add-opens=java.base/java.nio=ALL-UNNAMED"))
        assertTrue(result.output.contains("jibExtraClasspath=/opt/spark/jars/hadoop-client-api-3.4.2.jar"))
        assertTrue(result.output.contains("jibExtraClasspath=/opt/spark/jars/hadoop-client-runtime-3.4.2.jar"))
        assertTrue(result.output.contains("jibExtraClasspath=/opt/spark/jars/*"))
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
        val catalogFile = File(System.getProperty("user.dir"))
            .parentFile
            .resolve("gradle/libs.versions.toml")
            .absolutePath
            .replace(File.separatorChar, '/')
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        from(files("$catalogFile"))
                    }
                }
            }

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
                    val implementationExtends = configurations.getByName("implementation").extendsFrom.joinToString(",") { it.name }
                    val compileOnlyExtends = configurations.getByName("compileOnly").extendsFrom.joinToString(",") { it.name }
                    println("implementationExtends=${'$'}implementationExtends")
                    println("compileOnlyExtends=${'$'}compileOnlyExtends")
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
                    val container = jib.javaClass.methods.first { it.name == "getContainer" && it.parameterCount == 0 }.invoke(jib)
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
