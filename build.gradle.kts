import net.researchgate.release.ReleaseExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.plugins.signing.Sign

plugins {
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("net.researchgate.release") version "3.1.0"
}

allprojects {
    group = "org.openprojectx.spark.platform"
}

subprojects {
    tasks.register<DependencyReportTask>("allDependencies") {}

    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension>("java") {
            withSourcesJar()
            withJavadocJar()
        }

        tasks.withType(Javadoc::class.java).configureEach {
            isFailOnError = false
        }

        extensions.configure<PublishingExtension>("publishing") {
            publications {
                if (project.name != "plugin" && project.name != "spark-base-image" && findByName("mavenJava") == null) {
                    create<MavenPublication>("mavenJava") {
                        from(components["java"])
                        artifactId = project.name
                    }
                }
            }

            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set(
                        when (project.name) {
                            "plugin" -> "Spark Platform"
                            "core" -> "Spark Platform Core"
                            else -> project.name
                        }
                    )
                    description.set("Spark Platform Gradle plugin")
                    url.set("https://github.com/OpenProjectX/spark-platform")

                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }

                    developers {
                        developer {
                            id.set("OpenProjectX")
                            name.set("OpenProjectX")
                            email.set("admin@openprojectx.org")
                        }
                    }

                    scm {
                        url.set("https://github.com/OpenProjectX/spark-platform")
                        connection.set("scm:git:https://github.com/OpenProjectX/spark-platform.git")
                        developerConnection.set("scm:git:ssh://git@github.com:OpenProjectX/spark-platform.git")
                    }
                }
            }
        }
    }

    extensions.configure<SigningExtension>("signing") {
        val keyFile = System.getenv("SIGNING_KEY_FILE")
        val keyPass = System.getenv("SIGNING_KEY_PASSWORD")

        if (!keyFile.isNullOrBlank()) {
            val keyText = file(keyFile).readText()
            useInMemoryPgpKeys(keyText, keyPass)

            val publishing = extensions.findByType(PublishingExtension::class.java)
            if (publishing != null) {
                sign(publishing.publications)
            }
        }
    }

    tasks.withType<PublishToMavenRepository>().configureEach {
        dependsOn(tasks.withType<Sign>())
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(System.getenv("OSSRH_USERNAME"))
            password.set(System.getenv("OSSRH_PASSWORD"))
        }
    }
}

tasks.register<Exec>("integrationTest") {
    group = "verification"
    description = "Runs examples through JVM and Docker integration paths."
    workingDir = layout.projectDirectory.dir("examples").asFile
    commandLine(
        layout.projectDirectory.file("gradlew").asFile.absolutePath,
        "integration",
        "--no-configuration-cache"
    )
}

configure<ReleaseExtension> {
    buildTasks.set(
        listOf(
            "integrationTest",
            ":spark-base-image:dockerPushSparkBaseRuntimeImages",
            ":platform-image:jibPublishAllPlatformImages",
            "publishToSonatype",
            "closeAndReleaseSonatypeStagingRepository",
        )
    )
    versionPropertyFile.set("gradle.properties")
    tagTemplate.set("\$name-\$version")
    preTagCommitMessage.set("[skip ci] Release ")
    tagCommitMessage.set("[skip ci] Create tag ")
    newVersionCommitMessage.set("[skip ci] Prepare next development version ")

    with(git) {
        requireBranch.set("master")
    }
}
