package com.github.sgdan.duplicate

import io.vavr.collection.*
import io.vavr.collection.List
import io.vavr.collection.Map
import io.vavr.collection.Set
import io.vavr.kotlin.hashMap
import io.vavr.kotlin.hashSet
import io.vavr.kotlin.list
import kotlinx.coroutines.experimental.Job
import mu.KotlinLogging
import java.io.File as IOFile

private val log = KotlinLogging.logger {}

data class File(
        val path: String,
        val size: Long,
        val md5: String,
        val folder: String?)

data class State(
        val currentFolder: String = ".",
        val currentHash: String? = null,
        val safeMode: Boolean = true,
        val folders: Set<String> = hashSet(),
        val paths: Set<String> = hashSet(),

        // sets of same size files, largest first
        val sizeToPath: TreeMap<Long, Set<String>> = TreeMap.empty(reverseOrder()),

        val hashing: Set<String> = hashSet(),

        // hashed files
        val sizeToHash: TreeMap<Long, Set<String>> = TreeMap.empty(reverseOrder()),
        val hashToFile: Map<String, Set<File>> = hashMap(),
        val pathToFile: Map<String, File> = hashMap(),
        val deleted: Set<File> = hashSet(),

        // job to manage background tasks
        val job: Job = Job(),

        /*
         * To help unit testing, can addPath tasks here. However, they will be stripped out
         * and scheduled in the background before the update method finishes.
         */
        val tasks: List<() -> Unit> = list()
) {
    val groups: Stream<Set<File>> = sizeToHash.toStream().flatMap { it._2 }
            .map { hashToFile.get(it) }.flatMap { it }
            .filter { it.size() > 1 }
            .take(MAX_GROUPS) // limit the number of groups

    /**
     * Don't bother hashing anything smaller than this size, it will be ignored
     * because we're cutting off the number of groups displayed.
     */
    val minSize: Long = when {
        groups.size() < MAX_GROUPS -> 0
        else -> groups.last().first().size
    }

    val largestPaths: Stream<String> = sizeToPath.toStream()
            .filter { it._1 >= minSize } // ignore files smaller than our cutoff
            .map { it._2 }
            .filter { it.size() > 1 }
            .flatMap { it }

    val toHash: Stream<String> = largestPaths.filter {
        !hashing.contains(it) && !pathToFile.containsKey(it)
    }

    val currentGroup: Set<File> = hashToFile.getOrElse(currentHash, hashSet())

    val remaining: Int = currentGroup.removeAll(deleted).size()
}