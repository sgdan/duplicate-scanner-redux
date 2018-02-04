package com.github.sgdan.duplicate

import io.vavr.collection.Map
import io.vavr.collection.Set
import io.vavr.collection.TreeMap
import io.vavr.kotlin.hashMap
import io.vavr.kotlin.hashSet
import io.vavr.kotlin.linkedHashSet

data class State(
        val currentFolder: String = ".",
        val folders: Set<String> = linkedHashSet(),
        val selectedFolder: String? = null,
        val selectedSize: Long? = null,

        // sort from largest to smallest
        val sizeToPaths: Map<Long, Set<String>> = TreeMap.empty(reverseOrder()),
        val pathToSize: Map<String, Long> = hashMap(),

        // md5sum hash management
        val sizeToHashes: Map<Long, Set<String>> = hashMap(),
        val hashToPaths: Map<String, Set<String>> = hashMap(),
        val hashing: Set<String> = hashSet(), // waiting for md5sum to be calculated
        val hashed: Set<String> = hashSet(),

        val deleted: Set<String> = hashSet(), // underlying file was deleted

        // safe mode prevents accidental deletion of last file in group
        val safeMode: Boolean = true
) {
    fun selectedHashes(): Set<String> = sizeToHashes.getOrElse(selectedSize, hashSet())
}