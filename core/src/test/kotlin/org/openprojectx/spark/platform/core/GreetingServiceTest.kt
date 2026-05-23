package org.openprojectx.spark.platform.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GreetingServiceTest {
    @Test
    fun `uses default greeting for blank messages`() {
        assertEquals("Hello from Spark Platform", GreetingService().greeting(" "))
    }
}
