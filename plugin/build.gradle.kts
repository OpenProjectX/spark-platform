import buildsrc.GenerateSparkPlatformConstants
import buildsrc.loadPlatformImageConfig
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val platformImageConfig = loadPlatformImageConfig(
    rootProject.layout.projectDirectory.file("gradle/spark-platform-image.toml").asFile
) { it.trim() }
val generateSparkPlatformConstants by tasks.registering(GenerateSparkPlatformConstants::class) {
    bundleAliases.set(libsCatalog.bundleAliases.sorted())
    lineIds.set(platformImageConfig.baseImageDefaultsByLine.keys.sorted())
    profileIds.set(
        platformImageConfig.profilesByLine.values
            .flatMap { it.keys }
            .distinct()
            .sorted()
    )
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/sparkPlatformConstants/java"))
}

sourceSets.main {
    java.srcDir(generateSparkPlatformConstants)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.jibGradlePlugin)
    implementation(libs.tomlj)
    testImplementation(libs.junitJupiter)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.processResources {
    from(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml")) {
        into("org/openprojectx/spark/platform/plugin")
        rename { "spark-platform.versions.toml" }
    }
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
