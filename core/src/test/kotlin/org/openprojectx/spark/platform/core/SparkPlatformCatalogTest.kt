package org.openprojectx.spark.platform.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SparkPlatformCatalogTest {
    @Test
    fun `derives bundle names from platform line and variants`() {
        assertEquals("spark-platform-spark5-managed", SparkPlatformCatalog.managedBundle(" Spark5 "))
        assertEquals("spark-platform-spark5-variant-iceberg", SparkPlatformCatalog.variantBundle("spark5", "Iceberg"))
    }

    @Test
    fun `parses variant csv values`() {
        assertEquals(listOf("iceberg", "hudi"), SparkPlatformCatalog.parseVariants("iceberg, hudi,iceberg"))
    }
}
