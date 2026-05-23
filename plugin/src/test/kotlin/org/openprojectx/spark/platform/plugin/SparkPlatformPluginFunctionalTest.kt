package org.openprojectx.spark.platform.plugin

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SparkPlatformPluginFunctionalTest {
    @field:TempDir
    lateinit var projectDir: File

    @Test
    fun `sparkPlatform dependencies are implementation dependencies for local builds`() {
        writeFixture()

        val result = gradleRunner("printSparkPlatform").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(result.output.contains("implementationExtends=sparkPlatform,sparkPlatformBom"))
        assertTrue(result.output.contains("compileOnlyExtends="))
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql_2.13:3.5.7"))
    }

    @Test
    fun `sparkPlatform dependencies are compile only dependencies for official builds`() {
        writeFixture()

        val result = gradleRunner("printSparkPlatform", "-PsparkPlatform.officialBuild=true").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printSparkPlatform")?.outcome)
        assertTrue(result.output.contains("implementationExtends="))
        assertTrue(result.output.contains("compileOnlyExtends=sparkPlatform,sparkPlatformBom"))
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
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql_2.13:4.0.1"))
        assertTrue(result.output.contains("constraint=org.apache.hadoop:hadoop-client-api:3.4.1"))
    }

    @Test
    fun `spark platform variant can select an isolated managed bundle`() {
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
        assertTrue(result.output.contains("constraint=org.apache.spark:spark-sql_2.12:3.5.7"))
        assertTrue(result.output.contains("constraint=org.apache.paimon:paimon-spark-3.5_2.12:1.4.1"))
    }

    @Test
    fun `plugin configures jib to use the platform base image`() {
        writeFixture(
            """
            sparkPlatform {
                platformImage.set("registry.example.com/spark-platform")
                imageTag.set("spark4-test")
            }
            """.trimIndent()
        )

        val result = gradleRunner("printJib").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printJib")?.outcome)
        assertTrue(result.output.contains("fromImage=registry.example.com/spark-platform:spark4-test"))
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

            dependencies {
                sparkPlatform("org.apache.spark:spark-sql_2.13")
            }

            tasks.register("printSparkPlatform") {
                doLast {
                    val implementationExtends = configurations.getByName("implementation").extendsFrom.joinToString(",") { it.name }
                    val compileOnlyExtends = configurations.getByName("compileOnly").extendsFrom.joinToString(",") { it.name }
                    println("implementationExtends=${'$'}implementationExtends")
                    println("compileOnlyExtends=${'$'}compileOnlyExtends")
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
