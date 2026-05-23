package org.openprojectx.spark.platform.core

class GreetingService {
    fun greeting(message: String): String = message.trim().ifEmpty { "Hello from Spark Platform" }
}
