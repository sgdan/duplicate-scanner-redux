package com.github.sgdan.duplicate

import com.github.sgdan.duplicate.ActionType.*
import com.github.sgdan.webviewredux.Action
import com.github.sgdan.webviewredux.toHtml
import io.vavr.collection.Stream
import io.vavr.control.Option
import io.vavr.kotlin.hashSet
import io.vavr.kotlin.list
import io.vavr.kotlin.option
import kotlinx.coroutines.experimental.launch
import mu.KotlinLogging
import java.io.File

private val log = KotlinLogging.logger {}

enum class ActionType {
    OPEN,
    CLEAR,
    FOLDER_CHOSEN,
    SELECT_DIR,
    SELECT_MD5,
    CLEAR_SELECT,
    ADD_FILE,
    DEBUG,
    TOGGLE_SAFE,
    ADD_HASH,
    DELETE
}

fun update(action: Action, state: State): State =
        act(action, state).queueHash().execute()

/**
 * Queue up files to be hashed
 */
fun State.queueHash(): State {
    val toHashNow = toHash.take(Runtime.getRuntime().availableProcessors() - 1 - hashing.size())
    val hashTasks: Stream<() -> Unit> = toHashNow.map { path -> { hashFile(path) } }
    return copy(hashing = hashing.addAll(toHashNow),
            tasks = tasks.appendAll(hashTasks))
}

fun act(action: Action, state: State): State = when (action.to<ActionType>()) {
    CLEAR -> action.call(state, State::clear)
    OPEN -> open(state)
    FOLDER_CHOSEN -> action.call(state, State::addFolder)
    SELECT_DIR -> state.select(null, action.get(0, String::class))
    SELECT_MD5 -> state.select(action.get(0, String::class), null)
    CLEAR_SELECT -> state.select(null, null)
    ADD_FILE -> action.call(state, State::addPath)
    ADD_HASH -> action.call(state, State::addHash)
    TOGGLE_SAFE -> state.copy(safeMode = !state.safeMode)
    DELETE -> action.call(state, State::delete)
    DEBUG -> {
        log.debug { "view result: ${toHtml(webview!!.engine.document)}" }
        state
    }
    else -> throw Exception("Unexpected action: $action")
}

/** Clear everything except the current folder */
fun State.clear(): State {
    job.cancel()
    return State(dir = dir)
}

fun open(state: State): State {
    chooseFolder(state)
    return state
}

fun State.delete(path: String): State {
    val file = pathToFile.apply(path)
    File(path).delete()
    return copy(deleted = deleted.add(file))
}

fun State.select(md5: String? = null, dir: String? = null): State = copy(
        currentFolder = dir,
        currentHash = md5)

fun State.addPath(path: String, size: Long): State {
    if (paths.contains(path)) return this

    // must be a new path
    val sameSize = sizeToPath.getOrElse(size, hashSet()).add(path)
    return copy(paths = paths.add(path),
            sizeToPath = sizeToPath.put(size, sameSize))
}

fun State.addHash(path: String, size: Long, md5: String, folder: String): State {
    if (pathToFile.containsKey(path)) throw Exception("$path has already been hashed")
    val file = File(path, size, md5, folder)
    val hashes = sizeToHash.getOrElse(size, hashSet()).add(md5)
    val files = hashToFile.getOrElse(md5, hashSet()).add(file)
    val siblings = dirToFile.getOrElse(folder, hashSet()).add(file)
    return copy(pathToFile = pathToFile.put(path, file),
            sizeToHash = sizeToHash.put(size, hashes),
            hashToFile = hashToFile.put(md5, files),
            hashing = hashing.remove(path),
            dirToFile = dirToFile.put(folder, siblings))
}

/**
 * Add a folder to be scanned. Subfolders are scanned anyway so remove them.
 * Kick off the background scanning for the new folder
 */
fun State.addFolder(path: String): State {
    val paths = folders.insert(0, path)
    val noSubs = paths.filter { p ->
        !(paths - p).any { p.startsWith(it) }
    }

    // If noSubs doesn't contain this folder, no need to scan
    // because a parent must have already been scanned
    val task: Option<() -> Unit> = noSubs.contains(path).option({ { scan(path) } })
    return copy(folders = noSubs,
            dir = path,
            tasks = tasks.appendAll(task))
}

/**
 * Scan a folder and addPath all files by length to the state
 */
fun scan(folder: String) {
    val dir = File(folder)
    dir.walk().filter { it.isFile && it.length() > 0 }.forEach {
        redux?.perform(ADD_FILE, it.canonicalPath, it.length())
    }
}

/**
 * Queue the actions to be executed in the background
 */
fun State.execute(): State {
    launch(parent = job) { tasks.forEach { it() } }
    return copy(tasks = list())
}