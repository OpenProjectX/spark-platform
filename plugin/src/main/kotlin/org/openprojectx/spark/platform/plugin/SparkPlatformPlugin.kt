package org.openprojectx.spark.platform.plugin

import org.openprojectx.spark.platform.core.GreetingService
import org.gradle.api.Plugin
import org.gradle.api.Project

class SparkPlatformPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "sparkplatform",
            SparkPlatformExtension::class.java
        )
        extension.message.convention("Hello from Spark Platform")

        project.tasks.register("sayHello") { task ->
            task.group = "sparkplatform"
            task.description = "Prints the configured greeting."
            task.doLast {
                println(GreetingService().greeting(extension.message.get()))
            }
        }
    }
}
