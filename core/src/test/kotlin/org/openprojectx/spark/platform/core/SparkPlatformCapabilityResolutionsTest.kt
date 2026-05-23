package org.openprojectx.spark.platform.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SparkPlatformCapabilityResolutionsTest {
    @Test
    fun `built in capability resolutions are data driven`() {
        val rules = SparkPlatformCapabilityResolutions.BUILT_IN

        assertTrue(rules.isNotEmpty())
        assertEquals(rules.map { it.capability }.toSet().size, rules.size)
        assertTrue(rules.all { it.reason.isNotBlank() })
    }

    @Test
    fun `paimon lz4 resolution is registered without a provider version`() {
        val rule = SparkPlatformCapabilityResolutions.BUILT_IN.single {
            it.capability == ModuleCoordinate("org.lz4", "lz4-java")
        }

        assertEquals(ModuleCoordinate("at.yawk.lz4", "lz4-java"), rule.preferredProvider)
        assertEquals("org.lz4:lz4-java", rule.capability.toString())
        assertEquals("at.yawk.lz4:lz4-java", rule.preferredProvider.toString())
    }
}
