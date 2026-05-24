package org.openprojectx.spark.platform.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SparkPlatformJvmOptionsTest {
    @Test
    fun `provides Spark Java module options`() {
        val options = SparkPlatformJvmOptions.defaults("spark4", listOf("iceberg"))

        assertTrue(options.contains("--add-opens=java.base/java.nio=ALL-UNNAMED"))
        assertTrue(options.contains("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"))
    }
}
