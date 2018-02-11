package com.github.sgdan.duplicate

import io.vavr.kotlin.*
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import mu.KotlinLogging
import org.junit.Assert
import org.junit.Test
import java.nio.file.Paths

private val log = KotlinLogging.logger {}


class StateTest : Assert() {

    val initial = State().addPath("/one", 111)
            .addPath("/two", 222)
            .addPath("/another/two", 222)
            .addPath("/path/three", 333)
            .addPath("/another/three", 333)

    @Test
    fun testQueueHash() {
        val q = initial.queueHash()
        assertTrue(q.tasks.size() > 0)
    }

    @Test
    fun testAddHash() {
        val largest = initial.largestPaths.toList()
        log.debug { "largest: $largest" }

        // should only contain files where there is another file of matching size
        assertEquals(4, largest.size())

        // the two files of size 333 should come first
        assertTrue(list(0, 1).contains(largest.indexOf("/path/three")))
        assertTrue(list(0, 1).contains(largest.indexOf("/another/three")))

        // then the two of size 222
        assertTrue(list(2, 3).contains(largest.indexOf("/two")))
        assertTrue(list(2, 3).contains(largest.indexOf("/another/two")))

        // shouldn't contain the file of size 111
        assertFalse(largest.contains("/one"))


        val added = initial.addHash("/path/three", 333, "33333333", "/path")
                .addHash("/another/two", 222, "22222", "/another")
        assertEquals(list("/another/three", "/two"), added.toHash.toList())

        val all = added.addHash("/two", 222, "22222", null)
                .addHash("/another/three", 111, "111", null)
        assertTrue(all.toHash.isEmpty)
    }

    @Test
    fun testClear() {
        // launch a child job that doesn't finish
        val job = launch(initial.job) {
            while (true) {
                println("working...")
                delay(1000)
            }
        }
        println("launched $job!")
        assertTrue(job.isActive)
        val clear = initial.clear()
        assertEquals(0, clear.paths.size())
        assertEquals(".", clear.currentFolder)
        assertTrue(job.isCancelled)
    }

    @Test
    fun testAddFolder() {
        val parent = Paths.get("src/test/resources/to-scan").toFile().canonicalPath
        val sub1 = "$parent/sub1"
        val sub2 = "$parent/sub2"

        // addPath both child folders
        val added1 = initial.addFolder(sub1)
        assertEquals(hashSet(sub1), added1.folders)

        val added2 = added1.addFolder(sub2)
        assertEquals(hashSet(sub1, sub2), added2.folders)

        // addPath parent, child folders should be removed
        val addedParent = added2.addFolder(parent)
        assertEquals(hashSet(parent), addedParent.folders)

        // 3 tasks should have been added
        assertEquals(3, addedParent.tasks.size())
    }
}