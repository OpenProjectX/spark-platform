package org.openprojectx.spark.platform.plugin

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class SparkPlatformExtension {
    abstract val officialBuild: Property<Boolean>
    abstract val localPlatformImage: Property<Boolean>
    abstract val line: Property<String>
    abstract val platformVersion: Property<String>
    abstract val platformImage: Property<String>
    abstract val imageTag: Property<String>
    abstract val variants: ListProperty<String>
}
