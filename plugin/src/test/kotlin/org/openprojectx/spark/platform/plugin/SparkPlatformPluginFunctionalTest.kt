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
    fun `sayHello prints configured message`() {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("org.openprojectx.spark.platform")
            }

            sparkplatform {
                message.set("Hello from TestKit")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("sayHello")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":sayHello")?.outcome)
        assertTrue(result.output.contains("Hello from TestKit"))
    }
}
