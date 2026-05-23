package org.openprojectx.spark.platform.plugin

import org.gradle.api.provider.Property

abstract class SparkPlatformExtension {
    abstract val message: Property<String>
}
