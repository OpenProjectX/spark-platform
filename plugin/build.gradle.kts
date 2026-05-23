plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.junitJupiter)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junitPlatformLauncher)
}

gradlePlugin {
    plugins {
        create("sparkplatform") {
            id = "org.openprojectx.spark.platform"
            implementationClass = "org.openprojectx.spark.platform.plugin.SparkPlatformPlugin"
            displayName = "Spark Platform"
            description = "Spark Platform Gradle plugin"
        }
    }
}
