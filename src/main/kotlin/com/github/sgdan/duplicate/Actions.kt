package com.github.sgdan.duplicate

import com.github.sgdan.duplicate.ActionType.*
import com.github.sgdan.webviewredux.Action
import com.github.sgdan.webviewredux.toHtml
import io.vavr.collection.Set
import io.vavr.kotlin.hashSet
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import mu.KotlinLogging
import java.io.File

private val log = KotlinLogging.logger {}

enum class ActionType {
    OPEN,
    CLEAR,
    FOLDER_CHOSEN,
    SELECT_SIZE,
    ADD_FILE,
    DEBUG,
    TOGGLE_SAFE,
    ADD_HASH,
    DELETE
}

fun update(action: Action, state: State) = when (action.to<ActionType>()) {
    CLEAR -> clear(state)
    OPEN -> open(state)
    FOLDER_CHOSEN -> action.call(state, State::addFolder)
    SELECT_SIZE -> action.call(state, State::selectSize)
    ADD_FILE -> action.call(state, State::addFile)
    ADD_HASH -> action.call(state, State::addHash)
    TOGGLE_SAFE -> state.copy(safeMode = !state.safeMode)
    DELETE -> action.call(state, State::delete)
    DEBUG -> {
        log.debug { "view result: ${toHtml(webview!!.engine.document)}" }
        state
    }
    else -> throw Exception("Unexpected action: $action")
}

/** Preserve only the current folder when clearing */
fun clear(state: State) = State(currentFolder = state.currentFolder)

fun open(state: State): State {
    chooseFolder(state)
    return state
}

fun State.delete(path: String): State {
    File(path).delete()
    return copy(deleted = deleted.add(path))
}

/**
 * When the user selects a group of files that are the same size, we need to
 * kick off a task to calculate md5 hash values which are used to determine
 * if the file contents are identical.
 */
fun State.selectSize(size: String): State {
    // selection might be null, in that case just clear the selection
    val selected = size.toLongOrNull() ?: return copy(selectedSize = null)

    // figure out which files need to be hashed
    val paths: Set<String> = sizeToPaths.getOrElse(selected, hashSet())
    val toHash: Set<String> = paths.removeAll(hashing).removeAll(hashed)
    launch {
        toHash.forEach { path ->
            val calculated = hash(path)
            redux?.perform(ADD_HASH, path, calculated)
        }
    }

    return copy(selectedSize = selected,
            hashing = hashing.addAll(toHash))
}

fun State.addFile(path: String, length: Long): State {
    val paths = sizeToPaths.getOrElse(length, hashSet()).add(path)
    return copy(sizeToPaths = sizeToPaths.put(length, paths),
            pathToSize = pathToSize.put(path, length)
    )
}

fun State.addHash(path: String, hash: String): State {
    val paths = hashToPaths.getOrElse(hash, hashSet()).add(path)
    val size = pathToSize.apply(path)
    val hashes = sizeToHashes.getOrElse(size, hashSet()).add(hash)
    return copy(hashToPaths = hashToPaths.put(hash, paths),
            hashing = hashing.remove(path),
            hashed = hashed.add(path),
            sizeToHashes = sizeToHashes.put(size, hashes))
}

/**
 * Add a folder to be scanned. Subfolders are scanned anyway so any
 * subfolders will be removed from the state. Kick off the background
 * scanning for the new folder
 */
fun State.addFolder(path: String): State {
    val paths = folders.add(path)
    val noSubs = paths.filter { p ->
        !(paths - p).any { p.startsWith(it) }
    }.toSet()

    // If noSubs doesn't contain this folder, no need to scan
    // because a parent must have already been scanned
    if (noSubs.contains(path)) async { scan(path) }

    return copy(folders = noSubs, currentFolder = path)
}

/**
 * Scan a folder and add all files by length to the state
 */
fun scan(folder: String) {
    val dir = File(folder)
    dir.walk().filter { it.isFile && it.length() > 0 }.forEach {
        redux?.perform(ADD_FILE, it.canonicalPath, it.length())
    }
}