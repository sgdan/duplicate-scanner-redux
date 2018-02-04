package com.github.sgdan.duplicate

import mu.KotlinLogging
import org.junit.Assert
import org.junit.Test
import java.io.File

private val log = KotlinLogging.logger {}

class HashTest : Assert() {
    @Test
    fun smallFile() {
        val path = "src/test/resources/to-be-hashed.png"
        val file = File(path)
        val hashed = hash(file.absolutePath)
        assertEquals("f1fe09d6f9e225e89cbcdcfe0f8626e1", hashed)
    }
}