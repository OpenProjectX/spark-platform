package org.openprojectx.spark.platform.core

data class SparkPlatformCapabilityResolution(
    val capability: ModuleCoordinate,
    val preferredProvider: ModuleCoordinate,
    val reason: String
)

data class ModuleCoordinate(
    val group: String,
    val name: String
) {
    init {
        require(group.isNotBlank()) { "Module group must not be blank." }
        require(name.isNotBlank()) { "Module name must not be blank." }
    }

    override fun toString(): String = "$group:$name"
}

object SparkPlatformCapabilityResolutions {
    val BUILT_IN: List<SparkPlatformCapabilityResolution> = listOf(
        SparkPlatformCapabilityResolution(
            capability = ModuleCoordinate("org.lz4", "lz4-java"),
            preferredProvider = ModuleCoordinate("at.yawk.lz4", "lz4-java"),
            reason = "Paimon's Spark bundle expects at.yawk.lz4:lz4-java when both LZ4 providers are present."
        )
    )
}
